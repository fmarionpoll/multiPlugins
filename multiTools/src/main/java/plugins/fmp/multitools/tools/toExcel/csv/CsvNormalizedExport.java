package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * Capillary ({@code measure_cap_*}) files include topraw / toplevel / bottomlevel
 * / sumgulps when data exist, plus derivative if selected. Cage
 * ({@code measure_cage_*}) files are wide: {@code sum}, {@code pi},
 * {@code sum_gulps}, {@code pi_gulps}. Levels-tab checkboxes do not gate the
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

		long nativeMedian = nativeMedianMs(exp);
		int nativeStepMs = (int) Math.max(1L, nativeMedian > 0 ? nativeMedian : binStepMs);
		// Bin grid follows dialog analysis interval when enabled; no auto COARSER/NATIVE gate.
		boolean writeBin = options.forceCsvBinGrid;
		CageCapillarySeriesBuilder builder = new CageCapillarySeriesBuilder();

		exportCageWide(exp, options, charSeries, csv, builder, nativeStepMs, binStepMs, writeBin, wantCageLevels,
				wantCageGulps);

		if (!denseCols.isEmpty() || wantGulpEvents) {
			exportCapillaryMeasures(exp, options, charSeries, mode, csv, denseCols, wantGulpEvents, builder,
					nativeStepMs, binStepMs, writeBin);
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
					for (Map.Entry<Long, Map<String, Double>> row : byTime.entrySet()) {
						csv.writeMeasureCapRowRaw(expKey, cageId, capId, row.getKey() / 60000.0, row.getValue());
					}
					if (writeBin && !byTime.isEmpty()) {
						writeMeasureCapBinned(csv, expKey, cageId, capId, byTime, denseCols, binStepMs);
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
			Map<Long, Map<String, Double>> byTime, List<String> denseCols, long binStepMs) throws IOException {
		int n = byTime.size();
		long[] timesMs = new long[n];
		double[][] cols = new double[denseCols.size()][n];
		int i = 0;
		for (Map.Entry<Long, Map<String, Double>> e : byTime.entrySet()) {
			timesMs[i] = e.getKey();
			Map<String, Double> vals = e.getValue();
			for (int c = 0; c < denseCols.size(); c++) {
				Double v = vals.get(denseCols.get(c));
				cols[c][i] = v != null ? v : Double.NaN;
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
			Map<String, Double> row = new LinkedHashMap<>();
			boolean any = false;
			for (int c = 0; c < denseCols.size(); c++) {
				double v = binned[c][b];
				if (!Double.isNaN(v)) {
					row.put(denseCols.get(c), v);
					any = true;
				}
			}
			if (any) {
				csv.writeMeasureCapRowBin(expKey, cageId, capId, starts[b] / 60000.0, row);
			}
		}
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

			XYSeries levelSum = null;
			XYSeries levelPi = null;
			XYSeries gulpSum = null;
			XYSeries gulpPi = null;
			if (wantLevels) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, EnumResults.TOPLEVEL_LR, nativeStepMs,
						builder);
				levelSum = findSeriesByKey(ds, cageId + "_Sum");
				levelPi = findSeriesByKey(ds, cageId + "_PI");
			}
			if (wantGulps) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, EnumResults.SUMGULPS_LR, nativeStepMs,
						builder);
				gulpSum = findSeriesByKey(ds, cageId + "_Sum");
				gulpPi = findSeriesByKey(ds, cageId + "_PI");
			}

			Map<Long, double[]> byTime = new TreeMap<>();
			mergeCagePair(byTime, levelSum, levelPi, 0, 1);
			mergeCagePair(byTime, gulpSum, gulpPi, 2, 3);
			if (byTime.isEmpty()) {
				continue;
			}

			for (Map.Entry<Long, double[]> e : byTime.entrySet()) {
				double[] v = e.getValue();
				if (allNaN(v)) {
					continue;
				}
				csv.writeMeasureCageRowRaw(expKey, cageId, e.getKey() / 60000.0, v[0], v[1], v[2], v[3]);
			}

			if (writeBin) {
				writeMeasureCageBinned(csv, expKey, cageId, byTime, binStepMs);
			}
		}
	}

	private static void mergeCagePair(Map<Long, double[]> byTime, XYSeries seriesSum, XYSeries seriesPi, int sumIdx,
			int piIdx) {
		if (seriesSum == null || seriesSum.getItemCount() == 0) {
			return;
		}
		for (int i = 0; i < seriesSum.getItemCount(); i++) {
			long tMs = Math.round(seriesSum.getX(i).doubleValue() * 60000.0);
			double[] row = byTime.computeIfAbsent(tMs, k -> new double[] { Double.NaN, Double.NaN, Double.NaN,
					Double.NaN });
			row[sumIdx] = seriesSum.getY(i).doubleValue();
			if (seriesPi != null) {
				int idx = seriesPi.indexOf(seriesSum.getX(i));
				if (idx >= 0) {
					row[piIdx] = seriesPi.getY(idx).doubleValue();
				}
			}
		}
	}

	private static void writeMeasureCageBinned(CsvNormalizedExportSupport csv, String expKey, int cageId,
			Map<Long, double[]> byTime, long binStepMs) throws IOException {
		int n = byTime.size();
		long[] timesMs = new long[n];
		double[][] cols = new double[4][n];
		int i = 0;
		for (Map.Entry<Long, double[]> e : byTime.entrySet()) {
			timesMs[i] = e.getKey();
			double[] v = e.getValue();
			for (int c = 0; c < 4; c++) {
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
			double sum = binned[0][b];
			double pi = binned[1][b];
			double sumGulps = binned[2][b];
			double piGulps = binned[3][b];
			if (Double.isNaN(sum) && Double.isNaN(pi) && Double.isNaN(sumGulps) && Double.isNaN(piGulps)) {
				continue;
			}
			csv.writeMeasureCageRowBin(expKey, cageId, starts[b] / 60000.0, sum, pi, sumGulps, piGulps);
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
		ro.correctEvaporation = (resultType == EnumResults.TOPLEVEL || resultType == EnumResults.TOPLEVEL_LR);
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
		byTime.computeIfAbsent(tMs, k -> new LinkedHashMap<>()).put(col, v);
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
			cols.add(colName(EnumResults.TOPRAW));
			cols.add(colName(EnumResults.TOPLEVEL));
			cols.add(colName(EnumResults.BOTTOMLEVEL));
			cols.add(colName(EnumResults.SUMGULPS));
			if (options.derivative) {
				cols.add(colName(EnumResults.DERIVEDVALUES));
			}
		}
		return cols;
	}

	private static Map<EnumResults, String> denseResultTypes(ResultsOptions options, Mode mode) {
		Map<EnumResults, String> map = new LinkedHashMap<>();
		if (mode == Mode.LEVELS) {
			map.put(EnumResults.TOPRAW, colName(EnumResults.TOPRAW));
			map.put(EnumResults.TOPLEVEL, colName(EnumResults.TOPLEVEL));
			map.put(EnumResults.BOTTOMLEVEL, colName(EnumResults.BOTTOMLEVEL));
			map.put(EnumResults.SUMGULPS, colName(EnumResults.SUMGULPS));
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
		boolean isLRType = resultType == EnumResults.TOPLEVEL_LR || resultType == EnumResults.TOPLEVELDELTA_LR
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
