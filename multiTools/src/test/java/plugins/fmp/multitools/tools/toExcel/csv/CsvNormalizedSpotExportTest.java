package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Test;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.CageKymographTimeline;
import plugins.fmp.multitools.service.CageKymographTimeline.Basis;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvNormalizedSpotExportTest {

	@ClassRule
	public static final InMemoryImageRule imageMemory = new InMemoryImageRule();

	@Test(timeout = 10000)
	public void nativeKymoExportComputesSumsAndUsesOnlyExactlyTwoConfiguredCategories() throws Exception {
		Path folder = Files.createTempDirectory("spot-group-export");
		Experiment exp = new Experiment() {
			@Override public String getKymosBinFullDirectory() { return folder.toString(); }
		};
		exp.setResultsDirectory(folder.toString());
		exp.setKymoBin_ms(60000);
		addCage(exp, 2);
		addCage(exp, 3);
		addSpot(exp, 2, "A", 0.75, Double.NaN);
		addSpot(exp, 2, "A", 0.75, 0.75);
		addSpot(exp, 2, "B", 0.75, 0.75);
		addSpot(exp, 3, "A", 0.75);
		addSpot(exp, 3, "B", 0.75);
		addSpot(exp, 3, "C");
		EnumResults measure = EnumResults.AGG_KYMO_HEIGHT_LOSS;
		String column = CsvNormalizedSpotExport.columnName(measure);
		try (CsvNormalizedExportSupport descriptors = new CsvNormalizedExportSupport(folder,
				Collections.emptyList(), 60000, false);
				CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder,
						Collections.emptyList(), Collections.emptyList(), Arrays.asList(column, column + "_n_valid"),
						true, 60000, false)) {
			CsvNormalizedSpotExport.exportAggregates(exp, new ResultsOptions(), "A", descriptors, csv, csv,
					new EnumResults[] { measure }, false);
		}
		String groups = read(folder.resolve("idspotgroup.csv"));
		assertTrue(groups.contains(",2,A__1,A,1,2,"));
		assertTrue(groups.contains(",3,C__1,C,1,1,"));
		String sums = read(folder.resolve("measure_spotgroup_kymo_raw.csv"));
		assertTrue(sums.contains(",2,A__1,0.0,,nominal_bin,0.5,2.0"));
		assertTrue(sums.contains(",2,A__1,1.0,,nominal_bin,0.25,1.0"));
		String pi = read(folder.resolve("measure_spotgroup_kymo_pi_raw.csv"));
		assertTrue(pi.contains(",2,A__1,B__1,0.0,,nominal_bin,agg_greenheight_conso,0.5,0.25,2,1,0.3333333333333333,0.0"));
		assertTrue(pi.contains(",2,A__1,B__1,1.0,,nominal_bin,agg_greenheight_conso,0.25,0.25,1,1,0.0,0.0"));
		assertFalse(pi.contains(",3,A__1,B__1"));
	}

	@Test(timeout = 10000)
	public void savedDetectionSettingsRetainIdentityAndParameters() throws Exception {
		Experiment exp = new Experiment();
		exp.setResultsDirectory(Files.createTempDirectory("spot-detection").toString());
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.spotThreshold = 42;
		SpotDetectionProvenance.recordCamera(exp, SpotDetectionProvenance.Family.FILTER, "test", options);
		java.util.Properties filter = SpotDetectionProvenance.load(exp, SpotDetectionProvenance.Family.FILTER);
		assertEquals("42", filter.getProperty("parameter.spot_threshold"));
		assertEquals("test", filter.getProperty("method"));
		assertTrue(filter.containsKey("run_id"));
		assertEquals(filter.getProperty("run_id"),
				SpotDetectionProvenance.load(exp, SpotDetectionProvenance.Family.FLY_MASK).getProperty("run_id"));
		assertTrue(SpotDetectionProvenance.load(exp, SpotDetectionProvenance.Family.COLOR).isEmpty());
	}

	private static void addCage(Experiment exp, int cageId) {
		Cage cage = new Cage();
		cage.getProperties().setCageID(cageId);
		exp.getCages().getCageList().add(cage);
	}

	private static void addSpot(Experiment exp, int cageId, String compound, double... ratios) {
		Spot spot = new Spot();
		spot.getProperties().setCageID(cageId);
		spot.getProperties().setStimulus(compound);
		spot.getProperties().setConcentration("1");
		spot.getGreenMaskHeightToPeakRatio().setValues(ratios);
		exp.getSpots().addSpot(spot);
	}

	@Test(timeout = 10000)
	public void preferenceUsesTotalOrPerContributorAndLeavesMissingResultsBlank() throws Exception {
		assertEquals(1.0 / 3.0,
				CsvNormalizedSpotExportSupport.preferenceIndex(0.4, 0.2, 2, 1, false), 1e-9);
		assertEquals(0.0, CsvNormalizedSpotExportSupport.preferenceIndex(0.4, 0.2, 2, 1, true), 1e-9);
		assertNull(CsvNormalizedSpotExportSupport.preferenceIndex(0.4, Double.NaN, 2, 0, true));
		assertNull(CsvNormalizedSpotExportSupport.preferenceIndex(0.0, 0.0, 2, 1, false));
		Path folder = Files.createTempDirectory("spot-preference");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), false, 60000, false)) {
			csv.writePreference("expA", 2, "A__1", "B__1", 0.0, 0, null, "agg_sumclean", 0.4, 0.2, 2, 1);
			csv.writePreference("expA", 2, "A__1", "B__1", 1.0, 1, null, "agg_sumclean", 0.4, Double.NaN, 2, 0);
			java.util.Properties settings = new java.util.Properties();
			settings.setProperty("run_id", "run1");
			settings.setProperty("parameter.bin_ms", "60000");
			csv.writeDetectionSettings("expA", "image_filter", settings);
			csv.writeDetectionSettings("expA", "kymograph_strip", new java.util.Properties());
		}
		String preferences = read(folder.resolve("measure_spotgroup_pi_raw.csv"));
		assertTrue(preferences.contains("expA,2,A__1,B__1,0.0,0,agg_sumclean,0.4,0.2,2,1,"));
		assertTrue(preferences.contains("expA,2,A__1,B__1,1.0,1,agg_sumclean,0.4,,2,0,,"));
		assertTrue(read(folder.resolve("detection_runs.csv")).contains("expA,kymograph_strip,,,,unavailable"));
		assertTrue(read(folder.resolve("detection_parameters.csv")).contains("expA,run1,bin_ms,60000,ms"));
	}

	@Test(timeout = 10000)
	public void aggregateRenamesPreserveExportIdentifiers() {
		assertAggregateIdentifier(EnumResults.AGG_FILTER_DEPLETION, "AGG_SUMCLEAN");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_DEPLETION, "AGG_SUMCLEAN_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_PIXEL_SUM, "AGG_AREA_COUNT_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_KYMO_HEIGHT_LOSS, "AGG_GREENHEIGHT_CONSO");
		assertEquals("agg_x_line_conso", CsvNormalizedSpotExport.columnName(EnumResults.AGG_X_LINE_CONSO));
		assertEquals("x_line_ratio", CsvNormalizedSpotExport.columnName(EnumResults.X_LINE_RATIO));
		assertEquals("rim_line_ratio", CsvNormalizedSpotExport.columnName(EnumResults.RIM_LINE_RATIO));
		assertEquals("AGG_LINE_CONSO", EnumResults.AGG_X_LINE_CONSO.toPersistenceKey());
		assertEquals("KYMO_LINE_RATIO", EnumResults.X_LINE_RATIO.toPersistenceKey());
		assertEquals(EnumResults.AGG_X_LINE_CONSO, EnumResults.findByPersistenceKey("AGG_LINE_CONSO"));
		assertEquals(EnumResults.X_LINE_RATIO, EnumResults.findByPersistenceKey("KYMO_LINE_RATIO"));
		assertEquals(EnumResults.AGG_X_LINE_CONSO, EnumResults.findByText("AGG_LINE_CONSO"));
		assertEquals(EnumResults.X_LINE_RATIO, EnumResults.findByText("KYMO_LINE_RATIO"));
		assertAggregateIdentifier(EnumResults.AGG_RIM_LINE_CONSO, "AGG_RIM");
		assertAggregateIdentifier(EnumResults.CAGE_GREY_MEDIAN, "AGG_MEDIANREF");
	}

	private static void assertAggregateIdentifier(EnumResults result, String identifier) {
		assertEquals(identifier, result.toPersistenceKey());
		assertEquals(identifier.toLowerCase(java.util.Locale.ROOT), CsvNormalizedSpotExport.columnName(result));
		assertEquals(result, EnumResults.findByPersistenceKey(identifier));
		assertFalse(result.isPersistedIn(EnumResults.PersistenceDomain.SPOT));
	}

	@Test(timeout = 10000)
	public void cameraAndKymoColumnsStayOnSeparateClocks() {
		assertEquals("area_sum", CsvNormalizedSpotExport.columnName(EnumResults.FILTER_GREY));
		assertEquals("kymo_fract", CsvNormalizedSpotExport.columnName(EnumResults.KYMO_ROW_FRACTION));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.FLY_PIXEL_COUNT));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.FILTER_GREY_SMOOTHED));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_GREEN_HEIGHT_RATIO));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_KYMO_HEIGHT_LOSS));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_FILTER_DEPLETION));
	}

	@Test(timeout = 10000)
	public void spotMeasureTypesIncludeFlyPresenceWithTheSelectedBoxes() {
		ResultsOptions options = new ResultsOptions();
		options.spotAreas = true;
		options.sum = true;
		options.spotKymoFract = true;
		EnumResults[] types = plugins.fmp.multitools.tools.toExcel.XLSExportSpots.spotMeasureTypes(options);
		assertEquals(EnumResults.FILTER_GREY, types[0]);
		assertEquals(EnumResults.KYMO_ROW_FRACTION, types[1]);
		assertEquals(EnumResults.FLY_PIXEL_COUNT, types[2]);
	}

	@Test(timeout = 10000)
	public void kymoRawTimesUseTheKymographBin() {
		long[] times = CsvNormalizedSpotExport.rawTimes(null, new ResultsOptions(), EnumResults.KYMO_ROW_FRACTION, 3, null);
		assertEquals(0L, times[0]);
		assertEquals(60000L, times[1]);
		assertEquals(120000L, times[2]);
		assertEquals(plugins.fmp.multitools.service.CageKymographTimeline.Basis.NOMINAL_BIN,
				plugins.fmp.multitools.service.CageKymographTimeline.resolve(null, 3, null).basis);
	}

	@Test(timeout = 10000)
	public void kymoFrameIndexesAccountForClippingAndSubsampling() {
		long[] cameraTimes = { 0L, 1000L, 3000L, 7000L, 8000L, 11000L };
		CsvNormalizedSpotExport.KymoFrameIndexes frames =
				new CsvNormalizedSpotExport.KymoFrameIndexes(cameraTimes, 2000L, 10000L, 2);
		assertEquals(Integer.valueOf(2), frames.at(0, 2));
		assertEquals(Integer.valueOf(4), frames.at(1, 2));
		assertEquals(Integer.valueOf(3), frames.at(1, 3));
		assertNull(frames.at(0, 1));
	}

	@Test(timeout = 10000)
	public void spotFilesUseExperimentCageAndSpotKeys() throws Exception {
		Path folder = Files.createTempDirectory("spot-csv");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Arrays.asList("area_sum"),
				Arrays.asList("kymo_fract"), Arrays.asList("agg_sumclean"), false, 60000L, true)) {
			csv.ensureIdspot("expA", 2, "r0c1", "spot", 0, 1, 0.5, 12, "quinine", "10mM", "green", 1, "0");
			Map<String, Double> row = new LinkedHashMap<>();
			row.put("area_sum", 3.5);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 1.0, 4, null, row);
			csv.writeSpotRow(false, true, "expA", 2, "r0c1", 0.0, 0, null, row);
			csv.writeSpotRow(false, true, "expA", 2, "r0c1", 2.0, null, null, row);
			Map<String, Double> empty = new LinkedHashMap<>();
			empty.put("area_sum", Double.NaN);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 2.0, 5, null, empty);
			Map<String, Double> group = Collections.singletonMap("agg_sumclean", 3.5);
			csv.writeGroupRow(false, "expA", 2, "quinine__10mM", 1.0, 4, null, group);
			csv.writeGroupRow(true, "expA", 2, "quinine__10mM", 2.0, null, null, group);
		}

		String idspot = read(folder.resolve("idspot.csv"));
		assertTrue(idspot.contains("experiment_id,cage_id,spot_id"));
		assertTrue(idspot.contains("expA,2,r0c1,spot,0,1,0.5,12,quinine,10mM,green,1,0"));

		String raw = read(folder.resolve("measure_spot_raw.csv"));
		assertTrue(raw.startsWith("experiment_id,cage_id,spot_id,time_min,frame_index,area_sum"));
		assertTrue(raw.contains("expA,2,r0c1,1.0,4,3.5"));
		assertFalse(raw.contains(",2.0,"));

		String bin = read(folder.resolve("measure_spot_bin60.csv"));
		assertTrue(bin.contains("expA,2,r0c1,0.0,0,3.5"));
		assertTrue(bin.contains("expA,2,r0c1,2.0,,3.5"));
		assertFalse(Files.exists(folder.resolve("measure_spot_kymo_raw.csv")));
		assertTrue(read(folder.resolve("measure_spotgroup_raw.csv")).contains("expA,2,quinine__10mM,1.0,4,3.5"));
		assertTrue(read(folder.resolve("measure_spotgroup_bin60.csv")).contains("expA,2,quinine__10mM,2.0,,3.5"));
	}

	private static String read(Path file) throws Exception {
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test(timeout = 10000)
	public void matchingColumnsUseCameraTimesAndKeepFrameIndexes() throws Exception {
		Experiment exp = new Experiment();
		exp.setKymoBin_ms(60000);
		exp.setKymoSubsampleFactor(3);
		long[] camera = new long[369];
		for (int i = 0; i < camera.length; i++) {
			camera[i] = Math.round(i * 7642000.0 / 368);
		}
		long[] times = CsvNormalizedSpotExport.rawTimes(exp, new ResultsOptions(), EnumResults.KYMO_GREEN_HEIGHT, 369,
				camera);
		assertEquals(7642000L, times[368]);
		assertEquals(Basis.CAMERA_FRAME, CageKymographTimeline.resolve(exp, 369, camera).basis);
		CsvNormalizedSpotExport.KymoFrameIndexes frames = new CsvNormalizedSpotExport.KymoFrameIndexes(camera, 0, 0, 3);
		assertEquals(Integer.valueOf(0), frames.at(0, 369));
		assertEquals(Integer.valueOf(368), frames.at(368, 369));

		Spot spot = new Spot();
		spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT).setValues(new double[] { 5, 4, 3 });
		long[] shortCamera = { 0L, 21000L, 43000L };
		Path folder = Files.createTempDirectory("greenheight-camera");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Collections.emptyList(),
				Arrays.asList("kymo_green_height"), Collections.emptyList(), false, 60000, false)) {
			CsvNormalizedSpotExport.writeSpotFamily(exp, new ResultsOptions(), csv, "camA2_A", new Cage(), 1, spot, "s",
					Arrays.asList(EnumResults.KYMO_GREEN_HEIGHT), true, false, shortCamera,
					new CsvNormalizedSpotExport.KymoFrameIndexes(shortCamera, 0, 0, 3));
		}
		List<String> lines = Files.readAllLines(folder.resolve("measure_spot_kymo_raw.csv"));
		assertEquals("experiment_id,cage_id,spot_id,time_min,frame_index,time_basis,kymo_green_height", lines.get(0));
		assertTrue(lines.get(1).contains("camA2_A,1,s,0.0,0,camera_frame,5.0"));
		assertTrue(lines.get(2).contains("camA2_A,1,s,0.35,1,camera_frame,4.0"));
		assertFalse(lines.get(2).contains(",1.0,1,"));
	}

	@Test(timeout = 10000)
	public void columnFileOutranksCameraAndNominalClocks() throws Exception {
		Path directory = Files.createTempDirectory("kymo-columns");
		CageKymographTimeline.write(directory.toString(), new long[] { 0, 21000, 43000, 64000, 86000 },
				Arrays.asList(0, 2, 4));
		Experiment exp = new Experiment() {
			@Override
			public String getKymosBinFullDirectory() {
				return directory.toString();
			}
		};
		exp.setKymoBin_ms(60000);
		long[] camera = { 0L, 21000L, 43000L, 64000L, 86000L };
		CageKymographTimeline.Clock clock = CageKymographTimeline.resolve(exp, 3, camera);
		assertEquals(Basis.COLUMN_FILE, clock.basis);
		assertArrayEquals(new long[] { 0L, 43000L, 86000L }, clock.elapsedMs);
	}

	@Test(timeout = 10000)
	public void mismatchedColumnFileIsNotReplacedWithSyntheticTimes() throws Exception {
		Path directory = Files.createTempDirectory("kymo-columns-bad");
		CageKymographTimeline.write(directory.toString(), new long[] { 0, 21000 }, Arrays.asList(0, 1));
		Experiment exp = new Experiment() {
			@Override
			public String getKymosBinFullDirectory() {
				return directory.toString();
			}
		};
		exp.setKymoBin_ms(60000);
		long[] camera = { 0L, 21000L, 42000L, 64000L };
		CageKymographTimeline.Clock clock = CageKymographTimeline.resolve(exp, 4, camera);
		assertEquals(Basis.COLUMN_FILE_REJECTED, clock.basis);
		assertNull(clock.elapsedMs);
		assertEquals(0, CsvNormalizedSpotExport.rawTimes(exp, new ResultsOptions(), EnumResults.KYMO_GREEN_HEIGHT, 4,
				camera).length);

		Spot spot = new Spot();
		spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT).setValues(new double[] { 1, 2, 3, 4 });
		Path folder = Files.createTempDirectory("greenheight-rejected");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Collections.emptyList(),
				Arrays.asList("kymo_green_height"), Collections.emptyList(), false, 600000, true)) {
			CsvNormalizedSpotExport.writeSpotFamily(exp, new ResultsOptions(), csv, "camA2_A", new Cage(), 1, spot, "s",
					Arrays.asList(EnumResults.KYMO_GREEN_HEIGHT), true, true, camera,
					new CsvNormalizedSpotExport.KymoFrameIndexes(camera, 0, 0, 1));
		}
		String raw = read(folder.resolve("measure_spot_kymo_raw.csv"));
		assertTrue(raw.contains("camA2_A,1,s,,0,column_file_rejected,1.0"));
		assertTrue(raw.contains("camA2_A,1,s,,3,column_file_rejected,4.0"));
		assertFalse(raw.contains("nominal_bin"));
		assertFalse(raw.contains(",1.0,1,"));
		assertFalse(Files.exists(folder.resolve("measure_spot_kymo_bin600.csv")));
	}

	@Test(timeout = 10000)
	public void subsampledColumnsUseTheSourceFrameTimestamp() {
		Experiment exp = new Experiment();
		exp.setKymoBin_ms(60000);
		exp.setKymoSubsampleFactor(2);
		long[] camera = { 0L, 21000L, 42000L, 63000L, 84000L, 105000L };
		CageKymographTimeline.Clock clock = CageKymographTimeline.resolve(exp, 3, camera);
		assertEquals(Basis.CAMERA_FRAME, clock.basis);
		assertArrayEquals(new long[] { 0L, 42000L, 84000L }, clock.elapsedMs);
		CageKymographTimeline.Clock nominal = CageKymographTimeline.resolve(exp, 2, camera);
		assertEquals(Basis.NOMINAL_BIN, nominal.basis);
		assertArrayEquals(new long[] { 0L, 60000L }, nominal.elapsedMs);
	}

	@Test(timeout = 10000)
	public void sixHundredSecondBinsFollowTheAcquisitionSpan() {
		Experiment exp = new Experiment() {
			@Override
			public void loadFileIntervalsFromSeqCamData() {
			}
		};
		List<String> names = new ArrayList<>();
		long[] camera = new long[369];
		for (int i = 0; i < camera.length; i++) {
			names.add("frame" + i + ".jpg");
			camera[i] = Math.round(i * 7642000.0 / 368);
		}
		exp.getSeqCamData().setImagesList(names);
		exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(camera);
		exp.setCamImages_ms(camera);
		exp.setKymoBin_ms(60000);
		Spot spot = new Spot();
		spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT).setValues(new double[369]);
		exp.getSpots().getSpotList().add(spot);
		ResultsOptions options = new ResultsOptions();
		options.resultType = EnumResults.KYMO_GREEN_HEIGHT;
		options.buildExcelStepMs = 600000;
		SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, options);
		assertEquals(7642000L, grid.getFrameElapsedMsRelative()[368]);
		assertEquals(13, grid.getNBins());
		assertEquals(12L * 600000L, grid.getHeaderElapsedMs(12));
	}

	@Test(timeout = 10000)
	public void groupIdJoinsStimulusAndConcentration() {
		assertEquals("quinine__10mM", CsvNormalizedSpotExport.buildGroupId("quinine", "10mM"));
	}
}
