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
import plugins.fmp.multitools.experiment.timebase.TimestepResolutionContext;
import plugins.fmp.multitools.experiment.timebase.TimestepResolver;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.chart.builders.CapillaryChartSeriesKeys;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.ExportTimePolicy;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.enums.ExportLayoutMode;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;

/**
 * Normalized CSV export for multiCAFE capillary levels and gulps.
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
		boolean wantCageLr = wantsCageLr(options, mode);
		boolean wantGulpEvents = mode == Mode.GULPS && (options.amplitudeGulps || options.nbGulps);

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

		try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(csvFolder, denseCols)) {
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
				exportOneExperiment(exp, options, charSeries, mode, csv, denseCols, wantCageLr, wantGulpEvents);
				iSeries++;
				progress.incPosition();
			}
			Logger.info("CsvNormalizedExport: done stamp=" + csv.getStamp() + " folder=" + csv.getFolder());
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
			CsvNormalizedExportSupport csv, List<String> denseCols, boolean wantCageLr, boolean wantGulpEvents)
			throws IOException {
		exp.ensureFrameTimeScale();
		exp.dispatchCapillariesToCages();

		int stepMs = resolveStepMs(exp, options);
		long nativeMedian = nativeMedianMs(exp);
		ExportTimePolicy.Relation relation = ExportTimePolicy.relation(stepMs, nativeMedian);
		CageCapillarySeriesBuilder builder = new CageCapillarySeriesBuilder();

		if (wantCageLr) {
			exportCageLr(exp, options, charSeries, mode, csv, builder, stepMs, relation);
		}

		if (!denseCols.isEmpty() || wantGulpEvents) {
			exportCapillaryMeasures(exp, options, charSeries, mode, csv, denseCols, wantGulpEvents, builder, stepMs,
					relation);
		}
	}

	private static void exportCapillaryMeasures(Experiment exp, ResultsOptions options, String charSeries, Mode mode,
			CsvNormalizedExportSupport csv, List<String> denseCols, boolean wantGulpEvents,
			CageCapillarySeriesBuilder builder, int stepMs, ExportTimePolicy.Relation relation) throws IOException {

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
				XYSeriesCollection ds = buildDataset(exp, cage, options, rt, stepMs, builder);
				if (ds != null) {
					datasets.put(rt, ds);
				}
			}
			if (wantGulpEvents && eventType != null && !datasets.containsKey(eventType)) {
				XYSeriesCollection ds = buildDataset(exp, cage, options, eventType, stepMs, builder);
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
						mergeSeries(byTime, series, e.getValue(), e.getKey(), stepMs, relation);
					}
					for (Map.Entry<Long, Map<String, Double>> row : byTime.entrySet()) {
						csv.writeMeasureCapRow(expKey, cageId, capId, row.getKey() / 60000.0, row.getValue());
					}
				}

				if (wantGulpEvents && eventType != null) {
					XYSeriesCollection ds = datasets.get(eventType);
					if (ds == null) {
						continue;
					}
					XYSeries series = findSeriesForCapillary(ds, exp, cage, cap, eventType);
					writeGulpEvents(csv, expKey, cageId, capId, series, eventType, stepMs, relation);
				}
			}
		}
	}

	private static void exportCageLr(Experiment exp, ResultsOptions options, String charSeries, Mode mode,
			CsvNormalizedExportSupport csv, CageCapillarySeriesBuilder builder, int stepMs,
			ExportTimePolicy.Relation relation) throws IOException {
		EnumResults lrType = mode == Mode.LEVELS ? EnumResults.TOPLEVEL_LR : EnumResults.SUMGULPS_LR;
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

			XYSeriesCollection dataset = buildDataset(exp, cage, options, lrType, stepMs, builder);
			if (dataset == null) {
				continue;
			}
			XYSeries seriesSum = findSeriesByKey(dataset, cage.getCageID() + "_Sum");
			XYSeries seriesPi = findSeriesByKey(dataset, cage.getCageID() + "_PI");
			if (seriesSum == null || seriesSum.getItemCount() == 0) {
				continue;
			}
			int n = seriesSum.getItemCount();
			long[] timesMs = new long[n];
			double[] sumValues = new double[n];
			double[] piValues = new double[n];
			for (int i = 0; i < n; i++) {
				timesMs[i] = Math.round(seriesSum.getX(i).doubleValue() * 60000.0);
				sumValues[i] = seriesSum.getY(i).doubleValue();
				piValues[i] = Double.NaN;
				if (seriesPi != null) {
					int idx = seriesPi.indexOf(seriesSum.getX(i));
					if (idx >= 0) {
						piValues[i] = seriesPi.getY(idx).doubleValue();
					}
				}
			}
			if (relation == ExportTimePolicy.Relation.COARSER) {
				double[] sumRegrouped = lrType == EnumResults.SUMGULPS_LR
						? ExportTimePolicy.regroupSum(timesMs, sumValues, stepMs)
						: ExportTimePolicy.regroupHoldLast(timesMs, sumValues, stepMs);
				double[] piRegrouped = ExportTimePolicy.regroupHoldLast(timesMs, piValues, stepMs);
				long t0 = timesMs[0];
				int nBins = Math.max(sumRegrouped.length, piRegrouped.length);
				long[] centers = ExportTimePolicy.binCentersMs(t0, nBins, stepMs);
				for (int i = 0; i < nBins; i++) {
					double sum = i < sumRegrouped.length ? sumRegrouped[i] : Double.NaN;
					double pi = i < piRegrouped.length ? piRegrouped[i] : Double.NaN;
					if (Double.isNaN(sum) && Double.isNaN(pi)) {
						continue;
					}
					csv.writeMeasureCageRow(expKey, cageId, centers[i] / 60000.0, sum, pi);
				}
			} else {
				for (int i = 0; i < n; i++) {
					if (Double.isNaN(sumValues[i]) && Double.isNaN(piValues[i])) {
						continue;
					}
					csv.writeMeasureCageRow(expKey, cageId, timesMs[i] / 60000.0, sumValues[i], piValues[i]);
				}
			}
		}
	}

	private static XYSeriesCollection buildDataset(Experiment exp, Cage cage, ResultsOptions base, EnumResults resultType,
			int stepMs, CageCapillarySeriesBuilder builder) {
		ResultsOptions ro = new ResultsOptions();
		ro.buildExcelStepMs = stepMs;
		ro.relativeToMaximum = false;
		ro.subtractT0 = false;
		ro.correctEvaporation = (resultType == EnumResults.TOPLEVEL);
		ro.resultType = resultType;
		ro.exportLayoutMode = ExportLayoutMode.NORMALIZED;
		exp.getCages().prepareComputations(exp, ro);
		return builder.build(exp, cage, ro);
	}

	private static void mergeSeries(Map<Long, Map<String, Double>> byTime, XYSeries series, String colName,
			EnumResults resultType, int stepMs, ExportTimePolicy.Relation relation) {
		if (series == null || series.getItemCount() == 0) {
			return;
		}
		int n = series.getItemCount();
		long[] timesMs = new long[n];
		double[] values = new double[n];
		for (int i = 0; i < n; i++) {
			timesMs[i] = Math.round(series.getX(i).doubleValue() * 60000.0);
			values[i] = series.getY(i).doubleValue();
		}
		if (relation == ExportTimePolicy.Relation.COARSER) {
			double[] regrouped;
			if (resultType == EnumResults.NBGULPS) {
				regrouped = ExportTimePolicy.regroupPresence(timesMs, values, stepMs);
			} else if (resultType == EnumResults.AMPLITUDEGULPS || resultType == EnumResults.SUMGULPS) {
				regrouped = ExportTimePolicy.regroupSum(timesMs, values, stepMs);
			} else {
				regrouped = ExportTimePolicy.regroupHoldLast(timesMs, values, stepMs);
			}
			long[] centers = ExportTimePolicy.binCentersMs(timesMs[0], regrouped.length, stepMs);
			for (int i = 0; i < regrouped.length; i++) {
				putValue(byTime, centers[i], colName, regrouped[i]);
			}
		} else {
			for (int i = 0; i < n; i++) {
				putValue(byTime, timesMs[i], colName, values[i]);
			}
		}
	}

	private static void putValue(Map<Long, Map<String, Double>> byTime, long tMs, String col, double v) {
		if (Double.isNaN(v)) {
			return;
		}
		byTime.computeIfAbsent(tMs, k -> new LinkedHashMap<>()).put(col, v);
	}

	private static void writeGulpEvents(CsvNormalizedExportSupport csv, String expKey, int cageId, String capId,
			XYSeries series, EnumResults eventType, int stepMs, ExportTimePolicy.Relation relation) throws IOException {
		if (series == null || series.getItemCount() == 0) {
			return;
		}
		int n = series.getItemCount();
		long[] timesMs = new long[n];
		double[] values = new double[n];
		for (int i = 0; i < n; i++) {
			timesMs[i] = Math.round(series.getX(i).doubleValue() * 60000.0);
			values[i] = series.getY(i).doubleValue();
		}
		long[] outTimes;
		double[] outValues;
		if (relation == ExportTimePolicy.Relation.COARSER) {
			double[] regrouped = eventType == EnumResults.NBGULPS
					? ExportTimePolicy.regroupPresence(timesMs, values, stepMs)
					: ExportTimePolicy.regroupSum(timesMs, values, stepMs);
			outTimes = ExportTimePolicy.binCentersMs(timesMs[0], regrouped.length, stepMs);
			outValues = regrouped;
		} else {
			outTimes = timesMs;
			outValues = values;
		}
		for (int i = 0; i < outValues.length; i++) {
			double v = outValues[i];
			if (Double.isNaN(v) || v == 0.0) {
				continue;
			}
			csv.writeGulpEvent(expKey, cageId, capId, outTimes[i] / 60000.0, v);
		}
	}

	private static List<String> denseMeasureColumns(ResultsOptions options, Mode mode) {
		List<String> cols = new ArrayList<>();
		if (mode == Mode.LEVELS) {
			if (options.topLevel) {
				cols.add(colName(EnumResults.TOPRAW));
				cols.add(colName(EnumResults.TOPLEVEL));
			}
			if (options.bottomLevel) {
				cols.add(colName(EnumResults.BOTTOMLEVEL));
			}
			if (options.derivative) {
				cols.add(colName(EnumResults.DERIVEDVALUES));
			}
		} else {
			if (options.derivative) {
				cols.add(colName(EnumResults.DERIVEDVALUES));
			}
			if (options.sumGulps) {
				cols.add(colName(EnumResults.SUMGULPS));
			}
		}
		return cols;
	}

	private static Map<EnumResults, String> denseResultTypes(ResultsOptions options, Mode mode) {
		Map<EnumResults, String> map = new LinkedHashMap<>();
		if (mode == Mode.LEVELS) {
			if (options.topLevel) {
				map.put(EnumResults.TOPRAW, colName(EnumResults.TOPRAW));
				map.put(EnumResults.TOPLEVEL, colName(EnumResults.TOPLEVEL));
			}
			if (options.bottomLevel) {
				map.put(EnumResults.BOTTOMLEVEL, colName(EnumResults.BOTTOMLEVEL));
			}
			if (options.derivative) {
				map.put(EnumResults.DERIVEDVALUES, colName(EnumResults.DERIVEDVALUES));
			}
		} else {
			if (options.derivative) {
				map.put(EnumResults.DERIVEDVALUES, colName(EnumResults.DERIVEDVALUES));
			}
			if (options.sumGulps) {
				map.put(EnumResults.SUMGULPS, colName(EnumResults.SUMGULPS));
			}
		}
		return map;
	}

	private static boolean wantsCageLr(ResultsOptions options, Mode mode) {
		if (mode == Mode.LEVELS) {
			return options.lrPI;
		}
		return options.lrPI && options.sumGulps;
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

	private static int resolveStepMs(Experiment exp, ResultsOptions options) {
		long ms = TimestepResolver
				.resolve(exp, options.buildExcelStepMs, TimestepResolutionContext.FOR_EXCEL_EXPORT).getStepMs();
		return Math.max(1, (int) Math.min(ms, Integer.MAX_VALUE));
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
