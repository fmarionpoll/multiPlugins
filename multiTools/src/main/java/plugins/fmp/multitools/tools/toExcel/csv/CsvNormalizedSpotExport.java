package plugins.fmp.multitools.tools.toExcel.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.poi.ss.util.CellReference;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotAggregateSeries;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.ColorUtils;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.service.CageKymographTimeline;
import plugins.fmp.multitools.service.CageKymographTimeline.Clock;
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
 * wide Excel export. Native CSV values retain their stored scale.
 * The frame index is zero-based. A resampled row uses the index of a native
 * measurement at the same time when that index can be determined.
 * Kymograph {@code time_min} follows {@link CageKymographTimeline}: the column-time
 * file, otherwise source-frame acquisition times, otherwise the nominal bin.
 * Kymograph files name that choice in {@code time_basis}. A column-time file that
 * does not match the series is not replaced with a synthetic clock.
 */
public final class CsvNormalizedSpotExport {

	public enum Mode {
		SPOTS, SPOTS_WITH_GROUPS, AGGREGATE, AGGREGATE_KYMO, AGGREGATE_ALL
	}

	private CsvNormalizedSpotExport() {
	}

	public static String columnName(EnumResults resultType) {
		return resultType == null ? "" : resultType.toString().toLowerCase(Locale.ROOT);
	}

	/** Kymograph strip series and kymograph aggregates. Fly presence stays on the camera clock. */
	public static boolean isKymoSeries(EnumResults resultType) {
		if (plugins.fmp.multitools.experiment.spot.SpotLineSampling.isLineMeasure(resultType)) return false;
		if (resultType == null || resultType == EnumResults.FLY_PIXEL_COUNT) {
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
		return groupIdPart(stimulus) + "__" + groupIdPart(concentration);
	}

	private static String groupIdPart(String value) {
		return value == null ? "" : value.replace("%", "%25").replace("_", "%5F");
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

		boolean primary = useMode == Mode.SPOTS || useMode == Mode.SPOTS_WITH_GROUPS;
		boolean withGroups = useMode == Mode.SPOTS_WITH_GROUPS;
		boolean allGroups = withGroups || useMode == Mode.AGGREGATE_ALL;
		List<String> cameraCols = new ArrayList<>();
		List<String> kymoCols = new ArrayList<>();
		if (primary) {
			splitColumns(primaryTypes(options), cameraCols, kymoCols);
		}
		boolean groupKymo = useMode == Mode.AGGREGATE_KYMO;
		EnumResults[] cameraGroups = allGroups ? allCameraAggregateTypes()
				: primary ? new EnumResults[0] : aggregateTypes(options, useMode);
		EnumResults[] kymoGroups = allGroups ? aggregateTypes(options, Mode.AGGREGATE_KYMO) : new EnumResults[0];
		if (!primary && options.spotAggregateMeasures != null) {
			cameraGroups = selectedAggregateTypes(options, false);
			kymoGroups = selectedAggregateTypes(options, true);
			groupKymo = false;
			allGroups = true;
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
						aggregateColumns(cameraGroups), groupKymo, binStepMs, writeBin);
				CsvNormalizedSpotExportSupport kymoGroupWriter = allGroups ? new CsvNormalizedSpotExportSupport(
						csvFolder, Collections.emptyList(), Collections.emptyList(), aggregateColumns(kymoGroups),
						true, binStepMs, writeBin) : null) {
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
				if (primary) {
					exportSpots(exp, options, charSeries, descriptors, spots, cameraCols, kymoCols, writeBin);
				}
				if (cameraGroups.length > 0) {
					exportAggregates(exp, options, charSeries, descriptors, spots, spots, cameraGroups, writeBin);
				}
				if (kymoGroupWriter != null) {
					exportAggregates(exp, options, charSeries, descriptors, kymoGroupWriter, spots, kymoGroups, writeBin);
				}
				if (exp.getSpots() != null) {
					String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
					for (SpotDetectionProvenance.Family family : SpotDetectionProvenance.Family.values()) {
						if (exp.getSpots().getSpotList().stream().anyMatch(family::hasData)) {
							descriptors.ensureDescriptors(exp, charSeries, null, null);
							spots.writeDetectionSettings(expKey, family.id, SpotDetectionProvenance.load(exp, family));
						}
					}
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
			return new EnumResults[] { EnumResults.AGG_KYMO_HEIGHT_LOSS };
		}
		EnumResults rt = options.resultType != null ? options.resultType : EnumResults.AGG_FILTER_DEPLETION;
		return new EnumResults[] { rt };
	}

	private static EnumResults[] allCameraAggregateTypes() {
		return new EnumResults[] { EnumResults.AGG_FILTER_DEPLETION, EnumResults.AGG_COLOR_DEPLETION, EnumResults.AGG_COLOR_PIXEL_SUM,
				EnumResults.AGG_X_LINE_CONSO, EnumResults.AGG_RIM_LINE_CONSO };
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
		EnumResults[] types = primaryTypes(options);
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
		KymoFrameIndexes kymoFrames = new KymoFrameIndexes(cameraTimes, exp.getKymoFirst_ms(),
				exp.getKymoLast_ms(), exp.getKymoSubsampleFactor());

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
				writeIdspot(csv, expKey, cage, cageId, spot, spotId);
				if (!cameraCols.isEmpty()) {
					writeSpotFamily(exp, options, csv, expKey, cage, cageId, spot, spotId, cameraTypes, false,
							writeBin, cameraTimes, kymoFrames);
				}
				if (!kymoCols.isEmpty()) {
					writeSpotFamily(exp, options, csv, expKey, cage, cageId, spot, spotId, kymoTypes, true,
							writeBin, cameraTimes, kymoFrames);
				}
			}
		}
	}

	static void writeSpotFamily(Experiment exp, ResultsOptions options, CsvNormalizedSpotExportSupport csv,
			String expKey, Cage cage, int cageId, Spot spot, String spotId, List<EnumResults> types, boolean kymo,
			boolean writeBin, long[] cameraTimes, KymoFrameIndexes kymoFrames) throws IOException {
		Map<Long, NativeRow> raw = new TreeMap<>();
		Map<Long, Map<String, Double>> binned = new TreeMap<>();
		Map<Long, String> binnedBasis = new TreeMap<>();
		Spots allSpots = exp.getSpots();
		for (EnumResults rt : types) {
			String col = columnName(rt);
			SpotMeasure measure = spot.getMeasurements(rt);
			Clock clock = null;
			if (measure != null && measure.getCount() > 0) {
				double scale = allSpots.getScalingFactorToPhysicalUnits(rt);
				int count = measure.getCount();
				clock = sampleClock(exp, rt, count, cameraTimes);
				boolean rejected = clock != null && clock.elapsedMs == null;
				int n = rejected ? count : Math.min(count, clock == null ? 0 : clock.elapsedMs.length);
				if (!rejected && clock == null) {
					long[] times = rawTimes(exp, options, rt, count, cameraTimes);
					n = Math.min(count, times.length);
					for (int i = 0; i < n; i++) {
						double value = measure.getValueAt(i) * scale;
						if (!inWindow(times[i], options) || !Double.isFinite(value)) {
							continue;
						}
						raw.computeIfAbsent(times[i], k -> new NativeRow()).put(Integer.valueOf(i), times[i], null, col,
								value);
					}
				} else {
					for (int i = 0; i < n; i++) {
						double value = measure.getValueAt(i) * scale;
						Long timeMs = rejected ? null : clock.elapsedMs[i];
						if (timeMs != null && (!inWindow(timeMs, options) || !Double.isFinite(value))) {
							continue;
						}
						if (timeMs == null && !Double.isFinite(value)) {
							continue;
						}
						Integer frameIndex = usesKymoGrid(exp, rt) ? kymoFrames.at(i, count) : Integer.valueOf(i);
						long key = timeMs != null ? timeMs : Long.MIN_VALUE + i;
						String basis = clock == null ? null : clock.basis.exportId;
						raw.computeIfAbsent(key, k -> new NativeRow()).put(frameIndex, timeMs, basis, col, value);
					}
				}
			}
			if (writeBin && (clock == null || clock.elapsedMs != null)) {
				double[] excel = excelSpotValues(exp, options, cage, spot, rt);
				SpotExcelTimeline.SpotExcelGrid grid = gridFor(exp, options, rt);
				if (excel != null && grid.getNBins() > 0 && grid.getFrameElapsedMsRelative().length > 0) {
					int nBins = Math.min(excel.length, grid.getNBins());
					String basis = clock == null ? null : clock.basis.exportId;
					for (int k = 0; k < nBins; k++) {
						if (!Double.isFinite(excel[k])) {
							continue;
						}
						long tMs = grid.getHeaderElapsedMs(k);
						binned.computeIfAbsent(tMs, key -> new LinkedHashMap<>()).put(col, excel[k]);
						binnedBasis.putIfAbsent(tMs, basis);
					}
				}
			}
		}
		for (Map.Entry<Long, NativeRow> row : raw.entrySet()) {
			csv.writeSpotRow(kymo, false, expKey, cageId, spotId, minutesOf(row.getValue().timeMs),
					row.getValue().frameIndex, row.getValue().timeBasis, row.getValue().values);
		}
		if (writeBin) {
			for (Map.Entry<Long, Map<String, Double>> row : binned.entrySet()) {
				csv.writeSpotRow(kymo, true, expKey, cageId, spotId, minutesOf(row.getKey()),
						frameIndexAt(raw, row.getKey()), binnedBasis.get(row.getKey()), row.getValue());
			}
		}
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

	static void exportAggregates(Experiment exp, ResultsOptions options, String charSeries,
			CsvNormalizedExportSupport descriptors, CsvNormalizedSpotExportSupport csv, CsvNormalizedSpotExportSupport groupIds,
			EnumResults[] types, boolean writeBin)
			throws IOException {
		if (exp.getCages() == null || exp.getSpots() == null) {
			return;
		}
		exp.ensureFrameTimeScale();
		Spots allSpots = exp.getSpots();
		String expKey = NormalizedExportSupport.buildExpKey(exp, charSeries);
		long[] cameraTimes = cameraFrameTimes(exp, options);
		KymoFrameIndexes kymoFrames = new KymoFrameIndexes(cameraTimes, exp.getKymoFirst_ms(),
				exp.getKymoLast_ms(), exp.getKymoSubsampleFactor());
		EnumResults saved = options.resultType;
		boolean savedGrouping = options.spotAggregateByStimulusConc;
		Map<Integer, Map<String, GroupRows>> groups = new LinkedHashMap<>();
		try {
			for (EnumResults rt : types) {
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
					Map<String, GroupRows> cageGroups = groups.computeIfAbsent(cage.getProperties().getCageID(),
							key -> configuredGroups(cage, allSpots));
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
						GroupRows acc = cageGroups.get(groupId);
						if (acc == null) {
							continue;
						}
						Clock clock = sampleClock(exp, rt, s.getMeasure().getCount(), cameraTimes);
						boolean rejected = clock != null && clock.elapsedMs == null;
						long[] times = clock == null
								? rawTimes(exp, options, rt, s.getMeasure().getCount(), cameraTimes)
								: clock.elapsedMs;
						mergeNativeGroup(acc.raw, col, s, times, clock == null ? null : clock.basis.exportId, options,
								scale, usesKymoGrid(exp, rt) ? kymoFrames : null);
						if (writeBin && grid != null && !rejected && grid.getNBins() > 0
								&& grid.getFrameElapsedMsRelative().length > 0) {
							mergeBinned(acc.binned, acc.binBasis, col,
									s.getMeasure().getValuesResampledToExcelGrid(grid), grid, scale,
									clock == null ? null : clock.basis.exportId);
						}
					}
				}
			}
		} finally {
			options.resultType = saved;
			options.spotAggregateByStimulusConc = savedGrouping;
		}

		for (Map<String, GroupRows> cageGroups : groups.values()) {
			for (GroupRows acc : cageGroups.values()) {
				groupIds.ensureIdspotGroup(expKey, acc.cageId, acc.groupId, acc.stimulus, acc.concentration, acc.nSpots,
						acc.volumeCount > 0 ? acc.totalVolume / acc.volumeCount : Double.NaN);
				for (Map.Entry<Long, NativeRow> row : acc.raw.entrySet()) {
					csv.writeGroupRow(false, expKey, acc.cageId, acc.groupId, minutesOf(row.getValue().timeMs),
							row.getValue().frameIndex, row.getValue().timeBasis, row.getValue().values);
				}
				if (writeBin) {
					for (Map.Entry<Long, Map<String, Double>> row : acc.binned.entrySet()) {
						csv.writeGroupRow(true, expKey, acc.cageId, acc.groupId, minutesOf(row.getKey()),
								frameIndexAt(acc.raw, row.getKey()), acc.binBasis.get(row.getKey()), row.getValue());
					}
				}
			}
			if (cageGroups.size() == 2) {
				List<GroupRows> pair = new ArrayList<>(cageGroups.values());
				pair.sort(Comparator.comparing((GroupRows group) -> group.stimulus).thenComparing(group -> group.concentration));
				exportPreference(csv, expKey, pair.get(0), pair.get(1), types);
			}
		}
	}

	private static Map<String, GroupRows> configuredGroups(Cage cage, Spots allSpots) {
		Map<String, GroupRows> groups = new TreeMap<>();
		for (Spot spot : cage.getSpotList(allSpots)) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			SpotProperties props = spot.getProperties();
			StimulusConcKey key = new StimulusConcKey(props.getStimulus(), props.getConcentration());
			String groupId = buildGroupId(key.stimulus, key.concentration);
			GroupRows group = groups.computeIfAbsent(groupId, id -> new GroupRows(cage.getProperties().getCageID(),
					id, key.stimulus, key.concentration));
			group.nSpots++;
			if (Double.isFinite(props.getSpotVolume())) {
				group.totalVolume += props.getSpotVolume();
				group.volumeCount++;
			}
		}
		return groups;
	}

	private static void exportPreference(CsvNormalizedSpotExportSupport csv, String expKey, GroupRows a, GroupRows b,
			EnumResults[] types) throws IOException {
		TreeSet<Long> times = new TreeSet<>(a.raw.keySet());
		times.addAll(b.raw.keySet());
		for (long time : times) {
			NativeRow rowA = a.raw.get(time);
			NativeRow rowB = b.raw.get(time);
			Integer frame = rowA != null ? rowA.frameIndex : rowB.frameIndex;
			for (EnumResults type : types) {
				if (type == EnumResults.AGG_COLOR_PIXEL_SUM) {
					continue;
				}
				String col = columnName(type);
				double sumA = rowA == null ? Double.NaN : rowA.values.getOrDefault(col, Double.NaN);
				double sumB = rowB == null ? Double.NaN : rowB.values.getOrDefault(col, Double.NaN);
				int countA = rowA == null ? 0 : rowA.values.getOrDefault(col + "_n_valid", 0.0).intValue();
				int countB = rowB == null ? 0 : rowB.values.getOrDefault(col + "_n_valid", 0.0).intValue();
				if ((rowA != null && rowA.values.containsKey(col + "_n_valid"))
						|| (rowB != null && rowB.values.containsKey(col + "_n_valid"))) {
					Long timeMs = rowA != null && rowA.timeMs != null ? rowA.timeMs
							: rowB != null ? rowB.timeMs : null;
					String basis = rowA != null && rowA.timeBasis != null ? rowA.timeBasis
							: rowB != null ? rowB.timeBasis : null;
					csv.writePreference(expKey, a.cageId, a.groupId, b.groupId, minutesOf(timeMs), frame, basis, col,
							sumA, sumB, countA, countB);
				}
			}
		}
	}

	static EnumResults[] primaryTypes(ResultsOptions options) {
		return XLSExportSpots.spotMeasureTypes(options);
	}

	static EnumResults[] selectedAggregateTypes(ResultsOptions options, boolean kymo) {
		return options.spotAggregateMeasures.stream().filter(type -> isKymoSeries(type) == kymo)
				.toArray(EnumResults[]::new);
	}

	private static List<String> aggregateColumns(EnumResults[] types) {
		List<String> columns = new ArrayList<>();
		for (EnumResults type : types) {
			String name = columnName(type);
			columns.add(name);
			columns.add(name + "_n_valid");
		}
		return columns;
	}

	private static void mergeNativeGroup(Map<Long, NativeRow> byTime, String col, CageSpotAggregateSeries series,
			long[] times, String timeBasis, ResultsOptions options, double scale, KymoFrameIndexes kymoFrames) {
		SpotMeasure measure = series.getMeasure();
		if (measure == null) {
			return;
		}
		int count = measure.getCount();
		boolean rejected = times == null;
		if (!rejected && times.length == 0) {
			return;
		}
		int n = rejected ? count : Math.min(count, times.length);
		String basis = timeBasis;
		for (int i = 0; i < n; i++) {
			Long timeMs = rejected ? null : times[i];
			if (timeMs != null && !inWindow(timeMs, options)) {
				continue;
			}
			Integer frameIndex = kymoFrames == null ? Integer.valueOf(i) : kymoFrames.at(i, count);
			long key = timeMs != null ? timeMs : Long.MIN_VALUE + i;
			NativeRow row = byTime.computeIfAbsent(key, k -> new NativeRow());
			double v = measure.getValueAt(i);
			if (Double.isFinite(v)) {
				row.put(frameIndex, timeMs, basis, col, v * scale);
			}
			row.put(frameIndex, timeMs, basis, col + "_n_valid", series.getValidCount(i));
		}
	}

	private static Integer frameIndexAt(Map<Long, NativeRow> byTime, long timeMs) {
		NativeRow row = byTime.get(timeMs);
		return row == null ? null : row.frameIndex;
	}

	private static void mergeBinned(Map<Long, Map<String, Double>> byTime, Map<Long, String> basisByTime, String col,
			List<Double> values, SpotExcelTimeline.SpotExcelGrid grid, double scale, String timeBasis) {
		if (values == null || grid == null) {
			return;
		}
		int n = Math.min(values.size(), grid.getNBins());
		for (int k = 0; k < n; k++) {
			Double v = values.get(k);
			if (v == null || !Double.isFinite(v)) {
				continue;
			}
			long tMs = grid.getHeaderElapsedMs(k);
			byTime.computeIfAbsent(tMs, key -> new LinkedHashMap<>()).put(col, v * scale);
			basisByTime.putIfAbsent(tMs, timeBasis);
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

	static long[] rawTimes(Experiment exp, ResultsOptions options, EnumResults resultType, int n, long[] cameraTimes) {
		if (n < 1) {
			return new long[0];
		}
		if (usesKymoGrid(exp, resultType)) {
			Clock clock = CageKymographTimeline.resolve(exp, n, cameraTimes);
			return clock.elapsedMs != null ? clock.elapsedMs : new long[0];
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

	/** Kymograph column clock, or null when this measure stays on the camera timeline. */
	private static Clock sampleClock(Experiment exp, EnumResults resultType, int n, long[] cameraTimes) {
		if (!usesKymoGrid(exp, resultType)) {
			return null;
		}
		return CageKymographTimeline.resolve(exp, n, cameraTimes);
	}

	private static Double minutesOf(Long timeMs) {
		return timeMs == null ? null : timeMs / 60000.0;
	}

	private static boolean usesKymoGrid(Experiment exp, EnumResults type) {
		if (plugins.fmp.multitools.experiment.spot.SpotLineSampling.isLineMeasure(type))
			return !plugins.fmp.multitools.experiment.spot.SpotLineSampling.usesCameraFrames(exp, type);
		return isKymoSeries(type);
	}

	private static long[] cameraFrameTimes(Experiment exp, ResultsOptions options) {
		if (exp == null || options == null) {
			return new long[0];
		}
		EnumResults saved = options.resultType;
		options.resultType = EnumResults.FILTER_GREY;
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

	/** Recover the source camera frames for a kymograph built from a clipped stack. */
	static final class KymoFrameIndexes {
		private final long[] cameraTimes;
		private final long startMs;
		private final long endMs;
		private final int factor;

		KymoFrameIndexes(long[] cameraTimes, long startMs, long endMs, int factor) {
			this.cameraTimes = cameraTimes != null ? cameraTimes : new long[0];
			this.startMs = startMs;
			this.endMs = endMs;
			this.factor = factor;
		}

		Integer at(int column, int width) {
			int[] frames = CageKymographTimeline.sourceFrames(width, cameraTimes, startMs, endMs, factor);
			if (frames == null || column < 0 || column >= frames.length) {
				return null;
			}
			return frames[column];
		}
	}

	private static final class NativeRow {
		final Map<String, Double> values = new LinkedHashMap<>();
		Integer frameIndex;
		Long timeMs;
		String timeBasis;

		void put(Integer index, Long timeMs, String timeBasis, String column, double value) {
			if (values.isEmpty()) {
				frameIndex = index;
				this.timeMs = timeMs;
				this.timeBasis = timeBasis;
			} else {
				if (!Objects.equals(frameIndex, index)) {
					frameIndex = null;
				}
				if (!Objects.equals(this.timeMs, timeMs)) {
					this.timeMs = null;
				}
				if (!Objects.equals(this.timeBasis, timeBasis)) {
					this.timeBasis = null;
				}
			}
			values.put(column, value);
		}
	}

	private static final class GroupRows {
		final int cageId;
		final String groupId;
		final String stimulus;
		final String concentration;
		int nSpots;
		double totalVolume;
		int volumeCount;
		final Map<Long, NativeRow> raw = new TreeMap<>();
		final Map<Long, Map<String, Double>> binned = new TreeMap<>();
		final Map<Long, String> binBasis = new TreeMap<>();

		GroupRows(int cageId, String groupId, String stimulus, String concentration) {
			this.cageId = cageId;
			this.groupId = groupId;
			this.stimulus = stimulus;
			this.concentration = concentration;
		}
	}
}
