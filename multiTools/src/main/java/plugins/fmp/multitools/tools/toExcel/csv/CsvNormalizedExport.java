package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.util.CellReference;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageCapillariesComputation;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CapillaryChartSeriesKeys;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.enums.ExportLayoutMode;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

/**
 * Normalized CSV export for multiCAFE capillary levels and gulps.
 * <p>
 * Capillary ({@code measure_cap_*}) files include consumption_raw_uL /
 * consumption_corrected_uL / bottom_level_uL / consumption_from_gulps_uL /
 * gulp_amplitude_uL (0 when no gulp at that time), plus derivative if selected.
 * Cage ({@code measure_cage_*}) files are wide: {@code sum}/{@code pi}
 * (toplevel), {@code sum_topraw}/{@code pi_topraw},
 * {@code sum_gulps}/{@code pi_gulps}, {@code sum00}/{@code pi00} (toplevel00),
 * {@code t00_suitable}. Levels-tab checkboxes do not gate the
 * standard columns; optional extras (derivative, gulp events) still follow
 * options.
 */
public final class CsvNormalizedExport {

	public enum Mode {
		LEVELS, GULPS
	}

	private CsvNormalizedExport() {
	}

	public static void exportToFolder(Path csvFolder, ResultsOptions options, Mode mode) throws ExcelExportException {
		if (csvFolder == null) {
			throw new ExcelExportException("CSV folder path is null", "csv_export", "folder");
		}
		if (options == null || options.expList == null) {
			throw new ExcelExportException("Export options incomplete", "csv_export", "options");
		}
		options.exportLayoutMode = ExportLayoutMode.NORMALIZED;

		List<String> denseCols = denseMeasureColumns(options, mode);
		boolean wantGulpEvents = mode == Mode.GULPS && (options.amplitudeGulps || options.nbGulps);
		boolean wantCageLevels = mode == Mode.LEVELS;
		boolean wantCageGulps = true;
		long binStepMs = options.buildExcelStepMs > 0 ? options.buildExcelStepMs : 60000L;

		Logger.info("CsvNormalizedExport: start -> " + csvFolder);

		JComboBoxExperimentLazy expList = options.expList;
		try {
			int[] ix = expList.getExportExperimentIndexBounds(options);
			expList.loadExperimentMeasuresForExportRange(true, options.onlyalive, ix[0], ix[1]);
			expList.chainExperimentsUsingKymoIndexes(options.collateSeries);
			expList.setFirstImageForAllExperiments(options.collateSeries);
		} catch (Exception e) {
			throw new ExcelExportException("Failed to prepare experiments for CSV export", "csv_export", "prepare", e);
		}

		ProgressFrame progress = new ProgressFrame("CSV export");
		int nbexpts = expList.getItemCount();
		int[] bx = expList.getExportExperimentIndexBounds(options);
		int progressLen = (bx[1] >= bx[0]) ? (bx[1] - bx[0] + 1) : nbexpts;

		try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(csvFolder, denseCols, binStepMs, true)) {
			progress.setLength(Math.max(1, progressLen));
			int iSeries = 0;
			for (int index = options.experimentIndexFirst; index <= options.experimentIndexLast; index++) {
				Experiment exp = expList.getItemAt(index);
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}
				exp.loadExperimentDescriptors();
				ensureBinDirectory(exp, expList, options);
				exp.load_spots_description_and_measures();
				exp.load_capillaries_description_and_measures();
				exp.loadCagesMeasures(false);
				if (shouldSkipChained(exp, options)) {
					continue;
				}

				progress.setMessage("CSV export experiment " + (index + 1) + " of " + nbexpts);
				String charSeries = CellReference.convertNumToColString(iSeries);
				exportOneExperiment(exp, options, charSeries, mode, csv, denseCols, wantCageLevels, wantCageGulps,
						wantGulpEvents, binStepMs);
				iSeries++;
				progress.incPosition();
			}
			Logger.info("CsvNormalizedExport: done folder=" + csv.getFolder()
					+ (csv.isWriteBinFiles() ? (" bin=" + csv.getBinDescriptor()) : ""));
		} catch (IOException e) {
			throw new ExcelExportException("CSV write failed", "csv_export", csvFolder.toString(), e);
		} catch (Exception e) {
			throw new ExcelExportException("Unexpected CSV export error", "csv_export", csvFolder.toString(), e);
		} finally {
			progress.close();
		}

		if (mode == Mode.GULPS && options.markovChain) {
			Logger.warn("CsvNormalizedExport: Markov chain is not included in normalized CSV (use wide Excel).");
		}
	}

	private static void exportOneExperiment(Experiment exp, ResultsOptions options, String charSeries, Mode mode,
			CsvNormalizedExportSupport csv, List<String> denseCols, boolean wantCageLevels, boolean wantCageGulps,
			boolean wantGulpEvents, long binStepMs) throws IOException {
		exp.ensureFrameTimeScale();
		exp.dispatchCapillariesToCages();
		exp.getCages().computeT00References(exp);

		long nativeMedian = nativeMedianMs(exp);
		int nativeStepMs = (int) Math.max(1L, nativeMedian > 0 ? nativeMedian : binStepMs);
		// Raw series stay on kymo/camera-frame indices; do not resample onto Cam_sample_s.
		long kymoBinMs = exp.getKymoBin_ms();
		int rawStepMs = (int) Math.max(1L, kymoBinMs > 0 ? kymoBinMs : nativeStepMs);
		// Bin grid follows dialog analysis interval when enabled; no auto COARSER/NATIVE gate.
		boolean writeBin = options.forceCsvBinGrid;
		CageCapillarySeriesBuilder builder = new CageCapillarySeriesBuilder();

		exportCageWide(exp, options, charSeries, csv, builder, rawStepMs, binStepMs, writeBin, wantCageLevels,
				wantCageGulps);

		if (!denseCols.isEmpty() || wantGulpEvents) {
			exportCapillaryMeasures(exp, options, charSeries, mode, csv, denseCols, wantGulpEvents, builder,
					rawStepMs, binStepMs, writeBin);
		}
	}

	private static void exportCapillaryMeasures(Experiment exp, ResultsOptions options, String charSeries, Mode mode,
			CsvNormalizedExportSupport csv, List<String> denseCols, boolean wantGulpEvents,
			CageCapillarySeriesBuilder builder, int nativeStepMs, long binStepMs, boolean writeBin)
			throws IOException {

		Map<EnumResults, String> denseTypes = denseResultTypes(options, mode);
		EnumResults eventType = gulpEventResultType(options, mode);

		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null) {
				continue;
			}
			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries == null || capillaries.isEmpty()) {
				continue;
			}

			Map<EnumResults, XYSeriesCollection> datasets = new LinkedHashMap<>();
			for (EnumResults rt : denseTypes.keySet()) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, rt, nativeStepMs, builder);
				if (ds != null) {
					datasets.put(rt, ds);
				}
			}
			if (wantGulpEvents && eventType != null && !datasets.containsKey(eventType)) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, eventType, nativeStepMs, builder);
				if (ds != null) {
					datasets.put(eventType, ds);
				}
			}

			for (Capillary cap : capillaries) {
				csv.ensureDescriptors(exp, charSeries, cage, cap);
				String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
				int cageId = cage.getProperties().getCageID();
				String capId = NormalizedExportSupport.buildCapId(cap);

				if (!denseCols.isEmpty()) {
					Map<Long, Map<String, Double>> byTime = new TreeMap<>();
					for (Map.Entry<EnumResults, String> e : denseTypes.entrySet()) {
						XYSeriesCollection ds = datasets.get(e.getKey());
						if (ds == null) {
							continue;
						}
						XYSeries series = findSeriesForCapillary(ds, exp, cage, cap, e.getKey());
						mergeSeriesNative(byTime, series, e.getValue());
					}
					byTime.entrySet().removeIf(row -> !hasRawMeasureValues(row.getValue()));
					boolean t00Suitable = exp.isT00Suitable();
					for (Map.Entry<Long, Map<String, Double>> row : byTime.entrySet()) {
						csv.writeMeasureCapRowRaw(expKey, cageId, capId, row.getKey() / 60000.0, row.getValue(),
								t00Suitable);
					}
					if (writeBin && !byTime.isEmpty()) {
						writeMeasureCapBinned(csv, expKey, cageId, capId, byTime, denseCols, binStepMs, t00Suitable);
					}
				}

				if (wantGulpEvents && eventType != null) {
					XYSeriesCollection ds = datasets.get(eventType);
					if (ds == null) {
						continue;
					}
					XYSeries series = findSeriesForCapillary(ds, exp, cage, cap, eventType);
					writeGulpEventsNative(csv, expKey, cageId, capId, series);
				}
			}
		}
	}

	private static void writeMeasureCapBinned(CsvNormalizedExportSupport csv, String expKey, int cageId, String capId,
			Map<Long, Map<String, Double>> byTime, List<String> denseCols, long binStepMs, boolean t00Suitable)
			throws IOException {
		int n = byTime.size();
		long[] timesMs = new long[n];
		double[][] cols = new double[denseCols.size()][n];
		int i = 0;
		for (Map.Entry<Long, Map<String, Double>> e : byTime.entrySet()) {
			timesMs[i] = e.getKey();
			Map<String, Double> vals = e.getValue();
			for (int c = 0; c < denseCols.size(); c++) {
				String col = denseCols.get(c);
				if (CsvNormalizedExportSupport.COL_T00_SUITABLE.equals(col)) {
					cols[c][i] = Double.NaN;
					continue;
				}
				Double v = vals.get(col);
				cols[c][i] = v != null ? v : Double.NaN;
			}
			i++;
		}
		double[][] binned = CsvTimeWeightedResample.resampleColumns(timesMs, cols, binStepMs);
		if (binned.length == 0 || binned[0].length == 0) {
			return;
		}
		int gulpCol = denseCols.indexOf(CsvNormalizedExportSupport.COL_GULP_AMPLITUDE);
		int cumulCol = denseCols.indexOf(CsvNormalizedExportSupport.COL_CONSUMPTION_FROM_GULPS_UL);
		if (gulpCol >= 0 && cumulCol >= 0 && cumulCol < binned.length) {
			binned[gulpCol] = amplitudeFromCumulativeDiffs(binned[cumulCol]);
		}
		int nBins = binned[0].length;
		long[] starts = CsvTimeWeightedResample.binStartsMs(nBins, binStepMs);
		for (int b = 0; b < nBins; b++) {
			Map<String, Double> row = new LinkedHashMap<>();
			boolean any = false;
			for (int c = 0; c < denseCols.size(); c++) {
				String col = denseCols.get(c);
				if (CsvNormalizedExportSupport.COL_T00_SUITABLE.equals(col)) {
					continue;
				}
				double v = binned[c][b];
				if (CsvNormalizedExportSupport.COL_GULP_AMPLITUDE.equals(col)) {
					double g = Double.isNaN(v) ? 0.0 : v;
					row.put(col, g);
					if (g != 0.0) {
						any = true;
					}
					continue;
				}
				if (!Double.isNaN(v)) {
					row.put(col, v);
					any = true;
				}
			}
			if (any) {
				csv.writeMeasureCapRowBin(expKey, cageId, capId, starts[b] / 60000.0, row, t00Suitable);
			}
		}
	}

	private static double[] amplitudeFromCumulativeDiffs(double[] cumulative) {
		int n = cumulative == null ? 0 : cumulative.length;
		double[] out = new double[n];
		if (n == 0) {
			return out;
		}
		out[0] = 0.0;
		for (int i = 1; i < n; i++) {
			double cur = cumulative[i];
			double prev = cumulative[i - 1];
			if (Double.isNaN(cur) || Double.isNaN(prev)) {
				out[i] = 0.0;
			} else {
				out[i] = cur - prev;
			}
		}
		return out;
	}

	private static void exportCageWide(Experiment exp, ResultsOptions options, String charSeries,
			CsvNormalizedExportSupport csv, CageCapillarySeriesBuilder builder, int nativeStepMs, long binStepMs,
			boolean writeBin, boolean wantLevels, boolean wantGulps) throws IOException {
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null) {
				continue;
			}
			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries == null || capillaries.isEmpty()) {
				continue;
			}
			csv.ensureDescriptors(exp, charSeries, cage, capillaries.get(0));
			String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
			int cageId = cage.getProperties().getCageID();
			boolean t00Suitable = exp.isT00Suitable();

			XYSeries levelSum = null;
			XYSeries levelPi = null;
			XYSeries toprawSum = null;
			XYSeries toprawPi = null;
			XYSeries gulpSum = null;
			XYSeries gulpPi = null;
			XYSeries level00Sum = null;
			XYSeries level00Pi = null;
			if (wantLevels) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, EnumResults.TOPLEVEL_LR, nativeStepMs,
						builder);
				levelSum = findSeriesByKey(ds, cageId + "_Sum");
				levelPi = findSeriesByKey(ds, cageId + "_PI");
				XYSeriesCollection rawDs = buildDataset(exp, cage, options, EnumResults.TOPRAW_LR, nativeStepMs,
						builder);
				toprawSum = findSeriesByKey(rawDs, cageId + "_Sum");
				toprawPi = findSeriesByKey(rawDs, cageId + "_PI");
				XYSeriesCollection ds00 = buildDataset(exp, cage, options, EnumResults.TOPLEVEL00_LR, nativeStepMs,
						builder);
				level00Sum = findSeriesByKey(ds00, cageId + "_Sum");
				level00Pi = findSeriesByKey(ds00, cageId + "_PI");
			}
			if (wantGulps) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, EnumResults.SUMGULPS_LR, nativeStepMs,
						builder);
				gulpSum = findSeriesByKey(ds, cageId + "_Sum");
				gulpPi = findSeriesByKey(ds, cageId + "_PI");
			}

			Map<Long, double[]> byTime = new TreeMap<>();
			mergeCagePair(byTime, levelSum, levelPi, 0, 1, true);
			mergeCagePair(byTime, toprawSum, toprawPi, 2, 3, true);
			mergeCagePair(byTime, gulpSum, gulpPi, 4, 5, !wantLevels);
			mergeCagePair(byTime, level00Sum, level00Pi, 6, 7, true);
			if (byTime.isEmpty()) {
				continue;
			}

			for (Map.Entry<Long, double[]> e : byTime.entrySet()) {
				double[] v = e.getValue();
				if (allNaN(v)) {
					continue;
				}
				csv.writeMeasureCageRowRaw(expKey, cageId, e.getKey() / 60000.0, v[0], v[1], v[2], v[3], v[4], v[5],
						v[6], v[7], t00Suitable);
			}

			if (writeBin) {
				Map<Long, double[]> byTimeSides = new TreeMap<>();
				if (wantLevels) {
					mergeCageSidePair(byTimeSides, exp, cage, options, EnumResults.TOPLEVEL, nativeStepMs, builder,
							cageId, 0, 1, true);
					mergeCageSidePair(byTimeSides, exp, cage, options, EnumResults.TOPRAW, nativeStepMs, builder,
							cageId, 2, 3, true);
					mergeCageSidePair(byTimeSides, exp, cage, options, EnumResults.TOPLEVEL00, nativeStepMs, builder,
							cageId, 6, 7, true);
				}
				if (wantGulps) {
					mergeCageSidePair(byTimeSides, exp, cage, options, EnumResults.SUMGULPS, nativeStepMs, builder,
							cageId, 4, 5, !wantLevels);
				}
				if (!byTimeSides.isEmpty()) {
					writeMeasureCageBinned(csv, expKey, cageId, byTimeSides, binStepMs, t00Suitable);
				}
			}
		}
	}

	private static void mergeCageSidePair(Map<Long, double[]> byTime, Experiment exp, Cage cage,
			ResultsOptions options, EnumResults baseType, int stepMs, CageCapillarySeriesBuilder builder, int cageId,
			int lIdx, int rIdx, boolean createKeys) {
		XYSeriesCollection parts = buildCapillaryParts(exp, cage, options, baseType, stepMs, builder);
		XYSeriesCollection sides = CageCapillarySeriesBuilder.buildSideTotalSeries(cage, parts);
		mergeCageSides(byTime, findSeriesByKey(sides, cageId + "_L"), findSeriesByKey(sides, cageId + "_R"), lIdx,
				rIdx, createKeys);
	}

	private static XYSeriesCollection buildCapillaryParts(Experiment exp, Cage cage, ResultsOptions base,
			EnumResults baseType, int stepMs, CageCapillarySeriesBuilder builder) {
		ResultsOptions ro = new ResultsOptions();
		ro.buildExcelStepMs = stepMs;
		ro.relativeToMaximum = false;
		ro.subtractT0 = false;
		ro.correctEvaporation = (baseType == EnumResults.TOPLEVEL || baseType == EnumResults.TOPLEVEL00);
		ro.resultType = baseType;
		ro.exportLayoutMode = ExportLayoutMode.NORMALIZED;
		ro.lrPIThreshold = base != null ? base.lrPIThreshold : 0.0;
		exp.getCages().prepareComputations(exp, ro);
		return builder.build(exp, cage, ro);
	}

	private static void mergeCageSides(Map<Long, double[]> byTime, XYSeries seriesL, XYSeries seriesR, int lIdx,
			int rIdx, boolean createKeys) {
		if (seriesL == null || seriesR == null || seriesL.getItemCount() == 0) {
			return;
		}
		for (int i = 0; i < seriesL.getItemCount(); i++) {
			long tMs = Math.round(seriesL.getX(i).doubleValue() * 60000.0);
			int idxR = seriesR.indexOf(seriesL.getX(i));
			if (idxR < 0) {
				continue;
			}
			double[] row = byTime.get(tMs);
			if (row == null) {
				if (!createKeys) {
					continue;
				}
				row = newNaNRow(8);
				byTime.put(tMs, row);
			}
			row[lIdx] = seriesL.getY(i).doubleValue();
			row[rIdx] = seriesR.getY(idxR).doubleValue();
		}
	}

	private static void mergeCagePair(Map<Long, double[]> byTime, XYSeries seriesSum, XYSeries seriesPi, int sumIdx,
			int piIdx, boolean createKeys) {
		if (seriesSum == null || seriesSum.getItemCount() == 0) {
			return;
		}
		for (int i = 0; i < seriesSum.getItemCount(); i++) {
			long tMs = Math.round(seriesSum.getX(i).doubleValue() * 60000.0);
			double[] row = byTime.get(tMs);
			if (row == null) {
				if (!createKeys) {
					continue;
				}
				row = newNaNRow(8);
				byTime.put(tMs, row);
			}
			row[sumIdx] = seriesSum.getY(i).doubleValue();
			if (seriesPi != null) {
				int idx = seriesPi.indexOf(seriesSum.getX(i));
				if (idx >= 0) {
					row[piIdx] = seriesPi.getY(idx).doubleValue();
				}
			}
		}
	}

	private static double[] newNaNRow(int n) {
		double[] row = new double[n];
		Arrays.fill(row, Double.NaN);
		return row;
	}

	private static void writeMeasureCageBinned(CsvNormalizedExportSupport csv, String expKey, int cageId,
			Map<Long, double[]> byTimeSides, long binStepMs, boolean t00Suitable) throws IOException {
		int n = byTimeSides.size();
		long[] timesMs = new long[n];
		double[][] cols = new double[8][n];
		int i = 0;
		for (Map.Entry<Long, double[]> e : byTimeSides.entrySet()) {
			timesMs[i] = e.getKey();
			double[] v = e.getValue();
			for (int c = 0; c < 8; c++) {
				cols[c][i] = v[c];
			}
			i++;
		}
		double[][] binned = CsvTimeWeightedResample.resampleColumns(timesMs, cols, binStepMs);
		if (binned.length == 0 || binned[0].length == 0) {
			return;
		}
		int nBins = binned[0].length;
		long[] starts = CsvTimeWeightedResample.binStartsMs(nBins, binStepMs);
		for (int b = 0; b < nBins; b++) {
			double valL = binned[0][b];
			double valR = binned[1][b];
			double sum = CageCapillariesComputation.computeSumFromSides(valL, valR);
			double pi = CageCapillariesComputation.computePiFromSides(valL, valR);

			valL = binned[2][b];
			valR = binned[3][b];
			double sumTopraw = CageCapillariesComputation.computeSumFromSides(valL, valR);
			double piTopraw = CageCapillariesComputation.computePiFromSides(valL, valR);

			valL = binned[4][b];
			valR = binned[5][b];
			double sumGulps = CageCapillariesComputation.computeSumFromSides(valL, valR);
			double piGulps = CageCapillariesComputation.computePiFromSides(valL, valR);

			valL = binned[6][b];
			valR = binned[7][b];
			double sum00 = CageCapillariesComputation.computeSumFromSides(valL, valR);
			double pi00 = CageCapillariesComputation.computePiFromSides(valL, valR);

			if (Double.isNaN(sum) && Double.isNaN(pi) && Double.isNaN(sumTopraw) && Double.isNaN(piTopraw)
					&& Double.isNaN(sumGulps) && Double.isNaN(piGulps) && Double.isNaN(sum00) && Double.isNaN(pi00)) {
				continue;
			}
			csv.writeMeasureCageRowBin(expKey, cageId, starts[b] / 60000.0, sum, pi, sumTopraw, piTopraw, sumGulps,
					piGulps, sum00, pi00, t00Suitable);
		}
	}

	private static boolean allNaN(double[] v) {
		for (double d : v) {
			if (!Double.isNaN(d)) {
				return false;
			}
		}
		return true;
	}

	private static XYSeriesCollection buildDataset(Experiment exp, Cage cage, ResultsOptions base, EnumResults resultType,
			int stepMs, CageCapillarySeriesBuilder builder) {
		ResultsOptions ro = new ResultsOptions();
		ro.buildExcelStepMs = stepMs;
		ro.relativeToMaximum = false;
		ro.subtractT0 = false;
		ro.correctEvaporation = (resultType == EnumResults.TOPLEVEL || resultType == EnumResults.TOPLEVEL_LR
				|| resultType == EnumResults.TOPLEVEL00 || resultType == EnumResults.TOPLEVEL00_LR
				|| resultType == EnumResults.TOPLEVEL_SUM00 || resultType == EnumResults.TOPLEVEL_PI00
				|| resultType == EnumResults.TOPLEVEL_SUM_AND_00 || resultType == EnumResults.TOPLEVEL_PI_AND_00);
		ro.resultType = resultType;
		ro.exportLayoutMode = ExportLayoutMode.NORMALIZED;
		ro.lrPIThreshold = base != null ? base.lrPIThreshold : 0.0;
		exp.getCages().prepareComputations(exp, ro);
		return builder.build(exp, cage, ro);
	}

	private static void mergeSeriesNative(Map<Long, Map<String, Double>> byTime, XYSeries series, String colName) {
		if (series == null || series.getItemCount() == 0) {
			return;
		}
		for (int i = 0; i < series.getItemCount(); i++) {
			long tMs = Math.round(series.getX(i).doubleValue() * 60000.0);
			putValue(byTime, tMs, colName, series.getY(i).doubleValue());
		}
	}

	private static void putValue(Map<Long, Map<String, Double>> byTime, long tMs, String col, double v) {
		if (Double.isNaN(v)) {
			return;
		}
		if (CsvNormalizedExportSupport.COL_GULP_AMPLITUDE.equals(col) && v == 0.0) {
			return;
		}
		boolean gulpCol = CsvNormalizedExportSupport.COL_CONSUMPTION_FROM_GULPS_UL.equals(col)
				|| CsvNormalizedExportSupport.COL_GULP_AMPLITUDE.equals(col);
		if (gulpCol && !byTime.containsKey(tMs)) {
			return;
		}
		byTime.computeIfAbsent(tMs, k -> new LinkedHashMap<>()).put(col, v);
	}

	private static boolean hasRawMeasureValues(Map<String, Double> values) {
		if (values == null || values.isEmpty()) {
			return false;
		}
		for (Map.Entry<String, Double> e : values.entrySet()) {
			Double v = e.getValue();
			if (v == null || Double.isNaN(v)) {
				continue;
			}
			if (CsvNormalizedExportSupport.COL_GULP_AMPLITUDE.equals(e.getKey()) && v == 0.0) {
				continue;
			}
			if (CsvNormalizedExportSupport.COL_CONSUMPTION_FROM_GULPS_UL.equals(e.getKey())) {
				continue;
			}
			if (CsvNormalizedExportSupport.COL_T00_SUITABLE.equals(e.getKey())) {
				continue;
			}
			return true;
		}
		return false;
	}

	private static void writeGulpEventsNative(CsvNormalizedExportSupport csv, String expKey, int cageId, String capId,
			XYSeries series) throws IOException {
		if (series == null || series.getItemCount() == 0) {
			return;
		}
		for (int i = 0; i < series.getItemCount(); i++) {
			double v = series.getY(i).doubleValue();
			if (Double.isNaN(v) || v == 0.0) {
				continue;
			}
			csv.writeGulpEvent(expKey, cageId, capId, series.getX(i).doubleValue(), v);
		}
	}

	private static List<String> denseMeasureColumns(ResultsOptions options, Mode mode) {
		List<String> cols = new ArrayList<>();
		if (mode == Mode.LEVELS) {
			// Capillary columns (not gated by Levels-tab checkboxes).
			cols.add(CsvNormalizedExportSupport.COL_CONSUMPTION_RAW_UL);
			cols.add(CsvNormalizedExportSupport.COL_CONSUMPTION_CORRECTED_UL);
			cols.add(CsvNormalizedExportSupport.COL_CONSUMPTION_RAW00_UL);
			cols.add(CsvNormalizedExportSupport.COL_CONSUMPTION_CORRECTED00_UL);
			cols.add(CsvNormalizedExportSupport.COL_BOTTOM_LEVEL_UL);
			cols.add(CsvNormalizedExportSupport.COL_CONSUMPTION_FROM_GULPS_UL);
			cols.add(CsvNormalizedExportSupport.COL_GULP_AMPLITUDE);
			cols.add(CsvNormalizedExportSupport.COL_T00_SUITABLE);
			if (options.derivative) {
				cols.add(colName(EnumResults.DERIVEDVALUES));
			}
		}
		return cols;
	}

	private static Map<EnumResults, String> denseResultTypes(ResultsOptions options, Mode mode) {
		Map<EnumResults, String> map = new LinkedHashMap<>();
		if (mode == Mode.LEVELS) {
			map.put(EnumResults.TOPRAW, CsvNormalizedExportSupport.COL_CONSUMPTION_RAW_UL);
			map.put(EnumResults.TOPLEVEL, CsvNormalizedExportSupport.COL_CONSUMPTION_CORRECTED_UL);
			map.put(EnumResults.TOPRAW00, CsvNormalizedExportSupport.COL_CONSUMPTION_RAW00_UL);
			map.put(EnumResults.TOPLEVEL00, CsvNormalizedExportSupport.COL_CONSUMPTION_CORRECTED00_UL);
			map.put(EnumResults.BOTTOMLEVEL, CsvNormalizedExportSupport.COL_BOTTOM_LEVEL_UL);
			map.put(EnumResults.SUMGULPS, CsvNormalizedExportSupport.COL_CONSUMPTION_FROM_GULPS_UL);
			map.put(EnumResults.AMPLITUDEGULPS, CsvNormalizedExportSupport.COL_GULP_AMPLITUDE);
			if (options.derivative) {
				map.put(EnumResults.DERIVEDVALUES, colName(EnumResults.DERIVEDVALUES));
			}
		}
		return map;
	}

	private static EnumResults gulpEventResultType(ResultsOptions options, Mode mode) {
		if (mode != Mode.GULPS) {
			return null;
		}
		if (options.amplitudeGulps) {
			return EnumResults.AMPLITUDEGULPS;
		}
		if (options.nbGulps) {
			return EnumResults.NBGULPS;
		}
		return null;
	}

	private static String colName(EnumResults r) {
		return r.toString().toLowerCase();
	}

	private static long nativeMedianMs(Experiment exp) {
		if (exp.getFrameTimeScale() != null && !exp.getFrameTimeScale().isEmpty()) {
			long m = exp.getFrameTimeScale().medianDeltaMs();
			if (m > 0) {
				return m;
			}
		}
		long cam = exp.getCamImageBin_ms();
		return cam > 0 ? cam : exp.getKymoBin_ms();
	}

	private static void ensureBinDirectory(Experiment exp, JComboBoxExperimentLazy expList, ResultsOptions options) {
		String preferred = expList != null ? expList.expListBinSubDirectory : null;
		exp.resolveActiveBinForMeasuresLoad(preferred, false, false);
	}

	private static boolean shouldSkipChained(Experiment exp, ResultsOptions options) {
		if (options != null && options.experimentIndexFirst == options.experimentIndexLast) {
			return false;
		}
		return exp.chainToPreviousExperiment != null;
	}

	private static XYSeries findSeriesForCapillary(XYSeriesCollection dataset, Experiment exp, Cage cage, Capillary cap,
			EnumResults resultType) {
		if (dataset == null || cage == null || cap == null) {
			return null;
		}
		boolean isLRType = resultType == EnumResults.TOPLEVEL_LR || resultType == EnumResults.TOPLEVEL00_LR
				|| resultType == EnumResults.TOPRAW_LR || resultType == EnumResults.TOPLEVELDELTA_LR
				|| resultType == EnumResults.SUMGULPS_LR;
		if (isLRType) {
			return null;
		}
		return findSeriesByKey(dataset, CapillaryChartSeriesKeys.key(cap));
	}

	private static XYSeries findSeriesByKey(XYSeriesCollection dataset, String key) {
		if (dataset == null || key == null) {
			return null;
		}
		for (int i = 0; i < dataset.getSeriesCount(); i++) {
			XYSeries series = dataset.getSeries(i);
			if (key.equals(series.getKey())) {
				return series;
			}
		}
		return null;
	}
}
