package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.util.CellReference;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotAggregateSeries;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.ColorUtils;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport;
import plugins.fmp.multitools.tools.toExcel.XLSExportSpots;
import plugins.fmp.multitools.tools.toExcel.enums.ExportLayoutMode;
import plugins.fmp.multitools.tools.toExcel.exceptions.ExcelExportException;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;

/**
 * Normalized CSV export for multiSPOTS spot measures and cage-level aggregates.
 * <p>
 * Camera-timed series go to {@code measure_spot_raw.csv}. Kymograph series go to
 * {@code measure_spot_kymo_raw.csv}. When {@link ResultsOptions#forceCsvBinGrid}
 * is set, matching {@code *_binN} files hold the same resampled values as the
 * wide Excel export. When the Spots checkbox is on, each series is divided by its
 * maximum: the raw file uses the native maximum, the bin file uses the
 * resampled maximum, matching Excel. Fly presence is left unchanged.
 */
public final class CsvNormalizedSpotExport {

	public enum Mode {
		SPOTS, AGGREGATE, AGGREGATE_KYMO
	}

	private CsvNormalizedSpotExport() {
	}

	public static String columnName(EnumResults resultType) {
		return resultType == null ? "" : resultType.toString().toLowerCase(Locale.ROOT);
	}

	/** Kymograph strip series and kymograph aggregates. Fly presence stays on the camera clock. */
	public static boolean isKymoSeries(EnumResults resultType) {
		if (resultType == null || resultType == EnumResults.AREA_FLYPRESENT) {
			return false;
		}
		return resultType.isPersistedKymographSpotMeasure() || EnumResults.isKymographMeasure(resultType);
	}

	public static String buildSpotId(Spot spot) {
		if (spot == null || spot.getProperties() == null) {
			return "";
		}
		SpotProperties p = spot.getProperties();
		if (p.getSpotUniqueID() != null) {
			return Integer.toString(p.getSpotUniqueID().getId());
		}
		if (p.getCageRow() >= 0 && p.getCageColumn() >= 0) {
			return "r" + p.getCageRow() + "c" + p.getCageColumn();
		}
		String name = p.getName();
		if (name != null && !name.trim().isEmpty()) {
			return name.trim();
		}
		return "i" + p.getSpotArrayIndex();
	}

	public static String buildGroupId(String stimulus, String concentration) {
		String stim = stimulus != null ? stimulus : "";
		String conc = concentration != null ? concentration : "";
		return stim + "__" + conc;
	}

	public static void exportToFolder(Path csvFolder, ResultsOptions options, Mode mode) throws ExcelExportException {
		if (csvFolder == null) {
			throw new ExcelExportException("CSV folder path is null", "csv_export", "folder");
		}
		if (options == null || options.expList == null) {
			throw new ExcelExportException("Export options incomplete", "csv_export", "options");
		}
		options.exportLayoutMode = ExportLayoutMode.NORMALIZED;
		Mode useMode = mode != null ? mode : Mode.SPOTS;

		List<String> cameraCols = new ArrayList<>();
		List<String> kymoCols = new ArrayList<>();
		List<String> groupCols = new ArrayList<>();
		boolean groupKymo = useMode == Mode.AGGREGATE_KYMO;
		if (useMode == Mode.SPOTS) {
			if (!XLSExportSpots.hasAnySpotMeasureSelected(options)) {
				Logger.warn("CsvNormalizedSpotExport: no spot measures selected");
				return;
			}
			splitColumns(XLSExportSpots.spotMeasureTypes(options), cameraCols, kymoCols);
		} else {
			for (EnumResults rt : aggregateTypes(options, useMode)) {
				groupCols.add(columnName(rt));
			}
		}

		long binStepMs = options.buildExcelStepMs > 0 ? options.buildExcelStepMs : 60000L;
		boolean writeBin = options.forceCsvBinGrid;
		Logger.info("CsvNormalizedSpotExport: start -> " + csvFolder + " mode=" + useMode);

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

		try (CsvNormalizedExportSupport descriptors = new CsvNormalizedExportSupport(csvFolder, Collections.emptyList(),
				binStepMs, false);
				CsvNormalizedSpotExportSupport spots = new CsvNormalizedSpotExportSupport(csvFolder, cameraCols, kymoCols,
						groupCols, groupKymo, binStepMs, writeBin)) {
			progress.setLength(Math.max(1, progressLen));
			int iSeries = 0;
			for (int index = options.experimentIndexFirst; index <= options.experimentIndexLast; index++) {
				Experiment exp = expList.getItemAt(index);
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}
				exp.loadExperimentDescriptors();
				ensureBinDirectory(exp, expList);
				exp.load_spots_description_and_measures();
				exp.loadCagesMeasures(false);
				if (shouldSkipChained(exp, options)) {
					continue;
				}
				progress.setMessage("CSV export experiment " + (index + 1) + " of " + nbexpts);
				String charSeries = CellReference.convertNumToColString(iSeries);
				if (useMode == Mode.SPOTS) {
					exportSpots(exp, options, charSeries, descriptors, spots, cameraCols, kymoCols, writeBin);
				} else {
					exportAggregates(exp, options, charSeries, descriptors, spots, useMode, writeBin);
				}
				iSeries++;
				progress.incPosition();
			}
			Logger.info("CsvNormalizedSpotExport: done folder=" + csvFolder);
		} catch (IOException e) {
			throw new ExcelExportException("CSV write failed", "csv_export", csvFolder.toString(), e);
		} catch (Exception e) {
			throw new ExcelExportException("Unexpected CSV export error", "csv_export", csvFolder.toString(), e);
		} finally {
			progress.close();
		}
	}

	private static void splitColumns(EnumResults[] types, List<String> cameraCols, List<String> kymoCols) {
		for (EnumResults rt : types) {
			if (isKymoSeries(rt)) {
				kymoCols.add(columnName(rt));
			} else {
				cameraCols.add(columnName(rt));
			}
		}
	}

	private static EnumResults[] aggregateTypes(ResultsOptions options, Mode mode) {
		if (mode == Mode.AGGREGATE_KYMO) {
			return new EnumResults[] { EnumResults.AGG_GREENHEIGHT_CONSO, EnumResults.AGG_LINE_CONSO };
		}
		EnumResults rt = options.resultType != null ? options.resultType : EnumResults.AGG_SUMCLEAN;
		return new EnumResults[] { rt };
	}

	private static void exportSpots(Experiment exp, ResultsOptions options, String charSeries,
			CsvNormalizedExportSupport descriptors, CsvNormalizedSpotExportSupport csv, List<String> cameraCols,
			List<String> kymoCols, boolean writeBin) throws IOException {
		if (exp.getCages() == null || exp.getSpots() == null) {
			return;
		}
		exp.ensureFrameTimeScale();
		Spots allSpots = exp.getSpots();
		String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
		EnumResults[] types = XLSExportSpots.spotMeasureTypes(options);
		List<EnumResults> cameraTypes = new ArrayList<>();
		List<EnumResults> kymoTypes = new ArrayList<>();
		for (EnumResults rt : types) {
			if (isKymoSeries(rt)) {
				kymoTypes.add(rt);
			} else {
				cameraTypes.add(rt);
			}
		}
		long[] cameraTimes = cameraFrameTimes(exp, options);

		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null || cage.getSpotList(allSpots).isEmpty()) {
				continue;
			}
			cage.updateSpotsStimulus_i(allSpots);
			descriptors.ensureDescriptors(exp, charSeries, cage, null);
			int cageId = cage.getProperties().getCageID();
			for (Spot spot : cage.getSpotList(allSpots)) {
				if (spot == null || spot.getProperties() == null) {
					continue;
				}
				String spotId = buildSpotId(spot);
				boolean wrote = false;
				if (!cameraCols.isEmpty()) {
					wrote |= writeSpotFamily(exp, options, csv, expKey, cage, cageId, spot, spotId, cameraTypes, false,
							writeBin, cameraTimes);
				}
				if (!kymoCols.isEmpty()) {
					wrote |= writeSpotFamily(exp, options, csv, expKey, cage, cageId, spot, spotId, kymoTypes, true,
							writeBin, cameraTimes);
				}
				if (wrote) {
					writeIdspot(csv, expKey, cage, cageId, spot, spotId);
				}
			}
		}
	}

	private static boolean writeSpotFamily(Experiment exp, ResultsOptions options, CsvNormalizedSpotExportSupport csv,
			String expKey, Cage cage, int cageId, Spot spot, String spotId, List<EnumResults> types, boolean kymo,
			boolean writeBin, long[] cameraTimes) throws IOException {
		Map<Long, Map<String, Double>> raw = new TreeMap<>();
		Map<Long, Map<String, Double>> binned = new TreeMap<>();
		Spots allSpots = exp.getSpots();
		for (EnumResults rt : types) {
			String col = columnName(rt);
			SpotMeasure measure = spot.getMeasurements(rt);
			if (measure != null && measure.getCount() > 0) {
				double scale = allSpots.getScalingFactorToPhysicalUnits(rt);
				boolean relative = options.relativeToMaximum && rt != EnumResults.AREA_FLYPRESENT;
				double[] values = preparedNative(measure, relative, scale);
				long[] times = rawTimes(exp, options, rt, values.length, cameraTimes);
				int n = Math.min(values.length, times.length);
				for (int i = 0; i < n; i++) {
					if (!inWindow(times[i], options) || !Double.isFinite(values[i])) {
						continue;
					}
					raw.computeIfAbsent(times[i], k -> new LinkedHashMap<>()).put(col, values[i]);
				}
			}
			if (writeBin) {
				double[] excel = excelSpotValues(exp, options, cage, spot, rt);
				SpotExcelTimeline.SpotExcelGrid grid = gridFor(exp, options, rt);
				if (excel != null) {
					int nBins = Math.min(excel.length, grid.getNBins());
					for (int k = 0; k < nBins; k++) {
						if (!Double.isFinite(excel[k])) {
							continue;
						}
						long tMs = grid.getHeaderElapsedMs(k);
						binned.computeIfAbsent(tMs, key -> new LinkedHashMap<>()).put(col, excel[k]);
					}
				}
			}
		}
		boolean wrote = !raw.isEmpty();
		for (Map.Entry<Long, Map<String, Double>> row : raw.entrySet()) {
			csv.writeSpotRow(kymo, false, expKey, cageId, spotId, row.getKey() / 60000.0, row.getValue());
		}
		if (writeBin) {
			for (Map.Entry<Long, Map<String, Double>> row : binned.entrySet()) {
				csv.writeSpotRow(kymo, true, expKey, cageId, spotId, row.getKey() / 60000.0, row.getValue());
			}
		}
		return wrote || (writeBin && !binned.isEmpty());
	}

	private static void writeIdspot(CsvNormalizedSpotExportSupport csv, String expKey, Cage cage, int cageId, Spot spot,
			String spotId) throws IOException {
		SpotProperties p = spot.getProperties();
		String color = ColorUtils.getFriendlyColorName(spotColor(spot));
		csv.ensureIdspot(expKey, cageId, spotId, p.getName(), p.getCageRow(), p.getCageColumn(), p.getSpotVolume(),
				p.getSpotNPixels(), p.getStimulus(), p.getConcentration(), color, cage.getProperties().getCageNFlies(),
				p.getStimulusI());
	}

	private static java.awt.Color spotColor(Spot spot) {
		if (spot.getRoi() != null && spot.getRoi().getColor() != null) {
			return spot.getRoi().getColor();
		}
		return spot.getProperties() != null ? spot.getProperties().getColor() : null;
	}

	private static void exportAggregates(Experiment exp, ResultsOptions options, String charSeries,
			CsvNormalizedExportSupport descriptors, CsvNormalizedSpotExportSupport csv, Mode mode, boolean writeBin)
			throws IOException {
		if (exp.getCages() == null || exp.getSpots() == null) {
			return;
		}
		exp.ensureFrameTimeScale();
		Spots allSpots = exp.getSpots();
		String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
		long[] cameraTimes = cameraFrameTimes(exp, options);
		EnumResults saved = options.resultType;
		Map<String, GroupRows> groups = new LinkedHashMap<>();
		try {
			for (EnumResults rt : aggregateTypes(options, mode)) {
				options.resultType = rt;
				options.spotAggregateByStimulusConc = true;
				exp.getCages().prepareSpotAggregates(exp, options);
				String col = columnName(rt);
				for (Cage cage : exp.getCages().getCageList()) {
					if (cage == null) {
						continue;
					}
					if (options.onlyalive && cage.getProperties().getCageNFlies() < 1) {
						continue;
					}
					if (cage.getSpotList(allSpots).isEmpty()) {
						continue;
					}
					List<CageSpotAggregateSeries> series = cage.getSpotAggregates().getEntries();
					if (series == null) {
						continue;
					}
					descriptors.ensureDescriptors(exp, charSeries, cage, null);
					int cageId = cage.getProperties().getCageID();
					SpotExcelTimeline.SpotExcelGrid grid = writeBin ? gridFor(exp, options, rt) : null;
					double scale = allSpots.getScalingFactorToPhysicalUnits(rt);
					for (CageSpotAggregateSeries s : series) {
						if (s == null || s.getMeasure() == null || s.getKey() == null) {
							continue;
						}
						String groupId = buildGroupId(s.getKey().stimulus, s.getKey().concentration);
						String mapKey = cageId + "|" + groupId;
						GroupRows acc = groups.get(mapKey);
						if (acc == null) {
							acc = new GroupRows(cageId, groupId, s.getKey().stimulus, s.getKey().concentration,
									s.getNSpotsExposed(), meanSpotVolume(cage, s, allSpots));
							groups.put(mapKey, acc);
						}
						mergeNative(acc.raw, col, s.getMeasure(),
								rawTimes(exp, options, rt, s.getMeasure().getCount(), cameraTimes), options, scale);
						if (writeBin && grid != null) {
							mergeBinned(acc.binned, col, s.getMeasure().getValuesResampledToExcelGrid(grid), grid,
									scale);
						}
					}
				}
			}
		} finally {
			options.resultType = saved;
		}

		for (GroupRows acc : groups.values()) {
			csv.ensureIdspotGroup(expKey, acc.cageId, acc.groupId, acc.stimulus, acc.concentration, acc.nSpots,
					acc.volume);
			for (Map.Entry<Long, Map<String, Double>> row : acc.raw.entrySet()) {
				csv.writeGroupRow(false, expKey, acc.cageId, acc.groupId, row.getKey() / 60000.0, row.getValue());
			}
			if (writeBin) {
				for (Map.Entry<Long, Map<String, Double>> row : acc.binned.entrySet()) {
					csv.writeGroupRow(true, expKey, acc.cageId, acc.groupId, row.getKey() / 60000.0, row.getValue());
				}
			}
		}
	}

	private static void mergeNative(Map<Long, Map<String, Double>> byTime, String col, SpotMeasure measure, long[] times,
			ResultsOptions options, double scale) {
		if (measure == null || times == null) {
			return;
		}
		int n = Math.min(measure.getCount(), times.length);
		for (int i = 0; i < n; i++) {
			double v = measure.getValueAt(i);
			if (!Double.isFinite(v) || !inWindow(times[i], options)) {
				continue;
			}
			byTime.computeIfAbsent(times[i], k -> new LinkedHashMap<>()).put(col, v * scale);
		}
	}

	private static void mergeBinned(Map<Long, Map<String, Double>> byTime, String col, List<Double> values,
			SpotExcelTimeline.SpotExcelGrid grid, double scale) {
		if (values == null || grid == null) {
			return;
		}
		int n = Math.min(values.size(), grid.getNBins());
		for (int k = 0; k < n; k++) {
			Double v = values.get(k);
			if (v == null || !Double.isFinite(v)) {
				continue;
			}
			byTime.computeIfAbsent(grid.getHeaderElapsedMs(k), key -> new LinkedHashMap<>()).put(col, v * scale);
		}
	}

	private static double[] excelSpotValues(Experiment exp, ResultsOptions options, Cage cage, Spot spot,
			EnumResults resultType) {
		EnumResults saved = options.resultType;
		options.resultType = resultType;
		try {
			SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, options);
			Results results = new Results(cage.getProperties(), spot.getProperties(), 1);
			results.getDataFromSpot(spot, grid, options);
			if (results.getDataValues() == null || results.getDataValues().isEmpty()) {
				return null;
			}
			int nOut = results.getDataValues().size();
			results.initValuesOutArray(nOut, Double.NaN);
			double scale = exp.getSpots().getScalingFactorToPhysicalUnits(resultType);
			results.transferDataValuesToValuesOut(scale, resultType);
			return results.getValuesOut();
		} finally {
			options.resultType = saved;
		}
	}

	private static SpotExcelTimeline.SpotExcelGrid gridFor(Experiment exp, ResultsOptions options, EnumResults resultType) {
		EnumResults saved = options.resultType;
		options.resultType = resultType;
		try {
			return SpotExcelTimeline.buildForSpotExport(exp, options);
		} finally {
			options.resultType = saved;
		}
	}

	private static double[] preparedNative(SpotMeasure measure, boolean relativeToMaximum, double scale) {
		int n = measure.getCount();
		double[] values = new double[n];
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			values[i] = measure.getValueAt(i);
			if (Double.isFinite(values[i]) && values[i] > max) {
				max = values[i];
			}
		}
		boolean norm = relativeToMaximum && Double.isFinite(max) && max != 0.0;
		for (int i = 0; i < n; i++) {
			if (!Double.isFinite(values[i])) {
				values[i] = Double.NaN;
				continue;
			}
			if (norm) {
				values[i] = values[i] / max;
			}
			values[i] *= scale;
		}
		return values;
	}

	static long[] rawTimes(Experiment exp, ResultsOptions options, EnumResults resultType, int n, long[] cameraTimes) {
		if (n < 1) {
			return new long[0];
		}
		if (isKymoSeries(resultType)) {
			long delta = exp != null ? exp.getKymoBin_ms() : 0L;
			if (delta <= 0L) {
				delta = 60000L;
			}
			long[] times = new long[n];
			for (int i = 0; i < n; i++) {
				times[i] = (long) i * delta;
			}
			return times;
		}
		long[] frames = cameraTimes != null ? cameraTimes : new long[0];
		if (frames.length >= n) {
			long[] times = new long[n];
			System.arraycopy(frames, 0, times, 0, n);
			return times;
		}
		long step = options != null && options.buildExcelStepMs > 0 ? options.buildExcelStepMs : 60000L;
		long[] times = new long[n];
		for (int i = 0; i < n; i++) {
			times[i] = (long) i * step;
		}
		return times;
	}

	private static long[] cameraFrameTimes(Experiment exp, ResultsOptions options) {
		if (exp == null || options == null) {
			return new long[0];
		}
		EnumResults saved = options.resultType;
		options.resultType = EnumResults.AREA_SUM;
		try {
			return SpotExcelTimeline.buildForSpotExport(exp, options).getFrameElapsedMsRelative();
		} finally {
			options.resultType = saved;
		}
	}

	private static boolean inWindow(long tMs, ResultsOptions options) {
		if (options == null || !options.fixedIntervals) {
			return true;
		}
		return tMs >= options.startAll_Ms && tMs <= options.endAll_Ms;
	}

	private static double meanSpotVolume(Cage cage, CageSpotAggregateSeries series, Spots allSpots) {
		if (cage == null || series == null || series.getKey() == null || allSpots == null) {
			return Double.NaN;
		}
		double sum = 0.0;
		int n = 0;
		for (Spot spot : cage.getSpotList(allSpots)) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			SpotProperties p = spot.getProperties();
			boolean sameGroup = series.getKey().stimulus.equals(p.getStimulus())
					&& series.getKey().concentration.equals(p.getConcentration());
			if (!sameGroup || !Double.isFinite(p.getSpotVolume())) {
				continue;
			}
			sum += p.getSpotVolume();
			n++;
		}
		return n > 0 ? sum / n : Double.NaN;
	}

	private static void ensureBinDirectory(Experiment exp, JComboBoxExperimentLazy expList) {
		if (exp == null) {
			return;
		}
		String preferred = expList != null ? expList.expListBinSubDirectory : null;
		exp.resolveActiveBinForMeasuresLoad(preferred, false, false);
	}

	private static boolean shouldSkipChained(Experiment exp, ResultsOptions options) {
		if (options != null && options.experimentIndexFirst == options.experimentIndexLast) {
			return false;
		}
		return exp.chainToPreviousExperiment != null;
	}

	private static final class GroupRows {
		final int cageId;
		final String groupId;
		final String stimulus;
		final String concentration;
		final int nSpots;
		final double volume;
		final Map<Long, Map<String, Double>> raw = new TreeMap<>();
		final Map<Long, Map<String, Double>> binned = new TreeMap<>();

		GroupRows(int cageId, String groupId, String stimulus, String concentration, int nSpots, double volume) {
			this.cageId = cageId;
			this.groupId = groupId;
			this.stimulus = stimulus;
			this.concentration = concentration;
			this.nSpots = nSpots;
			this.volume = volume;
		}
	}
}
