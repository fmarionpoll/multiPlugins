package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Test;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvNormalizedSpotExportTest {

	@ClassRule
	public static final InMemoryImageRule imageMemory = new InMemoryImageRule();

	@Test
	public void nativeKymoExportComputesSumsAndUsesOnlyExactlyTwoConfiguredCategories() throws Exception {
		Path folder = Files.createTempDirectory("spot-group-export");
		Experiment exp = new Experiment();
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
		assertTrue(sums.contains(",2,A__1,0.0,,0.5,2.0"));
		assertTrue(sums.contains(",2,A__1,1.0,,0.25,1.0"));
		String pi = read(folder.resolve("measure_spotgroup_kymo_pi_raw.csv"));
		assertTrue(pi.contains(",2,A__1,B__1,0.0,,agg_greenheight_conso,0.5,0.25,2,1,0.3333333333333333,0.0"));
		assertTrue(pi.contains(",2,A__1,B__1,1.0,,agg_greenheight_conso,0.25,0.25,1,1,0.0,0.0"));
		assertFalse(pi.contains(",3,A__1,B__1"));
	}

	@Test
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

	@Test
	public void preferenceUsesTotalOrPerContributorAndLeavesMissingResultsBlank() throws Exception {
		assertEquals(1.0 / 3.0,
				CsvNormalizedSpotExportSupport.preferenceIndex(0.4, 0.2, 2, 1, false), 1e-9);
		assertEquals(0.0, CsvNormalizedSpotExportSupport.preferenceIndex(0.4, 0.2, 2, 1, true), 1e-9);
		assertNull(CsvNormalizedSpotExportSupport.preferenceIndex(0.4, Double.NaN, 2, 0, true));
		assertNull(CsvNormalizedSpotExportSupport.preferenceIndex(0.0, 0.0, 2, 1, false));
		Path folder = Files.createTempDirectory("spot-preference");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Collections.emptyList(),
				Collections.emptyList(), Collections.emptyList(), false, 60000, false)) {
			csv.writePreference("expA", 2, "A__1", "B__1", 0.0, 0, "agg_sumclean", 0.4, 0.2, 2, 1);
			csv.writePreference("expA", 2, "A__1", "B__1", 1.0, 1, "agg_sumclean", 0.4, Double.NaN, 2, 0);
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

	@Test
	public void aggregateRenamesPreserveExportIdentifiers() {
		assertAggregateIdentifier(EnumResults.AGG_FILTER_DEPLETION, "AGG_SUMCLEAN");
		assertAggregateIdentifier(EnumResults.AGG_FILTER_V5_DEPLETION, "AGG_SUMCLEAN_V5");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_DEPLETION, "AGG_SUMCLEAN_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_FILTER_V5_PIXEL_SUM, "AGG_AREA_COUNT_V5");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_PIXEL_SUM, "AGG_AREA_COUNT_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_KYMO_HEIGHT_LOSS, "AGG_GREENHEIGHT_CONSO");
		assertEquals("agg_x_line_conso", CsvNormalizedSpotExport.columnName(EnumResults.AGG_KYMO_CROSS_LOSS));
		assertEquals("x_line_ratio", CsvNormalizedSpotExport.columnName(EnumResults.KYMO_CROSS_RATIO));
		assertEquals("rim_line_ratio", CsvNormalizedSpotExport.columnName(EnumResults.KYMO_RIM_RATIO));
		assertEquals("AGG_LINE_CONSO", EnumResults.AGG_KYMO_CROSS_LOSS.toPersistenceKey());
		assertEquals("KYMO_LINE_RATIO", EnumResults.KYMO_CROSS_RATIO.toPersistenceKey());
		assertEquals(EnumResults.AGG_KYMO_CROSS_LOSS, EnumResults.findByPersistenceKey("AGG_LINE_CONSO"));
		assertEquals(EnumResults.KYMO_CROSS_RATIO, EnumResults.findByPersistenceKey("KYMO_LINE_RATIO"));
		assertEquals(EnumResults.AGG_KYMO_CROSS_LOSS, EnumResults.findByText("AGG_LINE_CONSO"));
		assertEquals(EnumResults.KYMO_CROSS_RATIO, EnumResults.findByText("KYMO_LINE_RATIO"));
		assertAggregateIdentifier(EnumResults.AGG_KYMO_RIM_LOSS, "AGG_RIM");
		assertAggregateIdentifier(EnumResults.CAGE_GREY_MEDIAN, "AGG_MEDIANREF");
	}

	private static void assertAggregateIdentifier(EnumResults result, String identifier) {
		assertEquals(identifier, result.toPersistenceKey());
		assertEquals(identifier.toLowerCase(java.util.Locale.ROOT), CsvNormalizedSpotExport.columnName(result));
		assertEquals(result, EnumResults.findByPersistenceKey(identifier));
		assertFalse(result.isPersistedIn(EnumResults.PersistenceDomain.SPOT));
	}

	@Test
	public void cameraAndKymoColumnsStayOnSeparateClocks() {
		assertEquals("area_sum", CsvNormalizedSpotExport.columnName(EnumResults.FILTER_GREY));
		assertEquals("kymo_fract", CsvNormalizedSpotExport.columnName(EnumResults.KYMO_ROW_FRACTION));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.FLY_PIXEL_COUNT));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.FILTER_GREY_SMOOTHED));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_GREEN_HEIGHT_RATIO));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_KYMO_HEIGHT_LOSS));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_FILTER_DEPLETION));
	}

	@Test
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

	@Test
	public void kymoRawTimesUseTheKymographBin() {
		long[] times = CsvNormalizedSpotExport.rawTimes(null, new ResultsOptions(), EnumResults.KYMO_ROW_FRACTION, 3, null);
		assertEquals(0L, times[0]);
		assertEquals(60000L, times[1]);
		assertEquals(120000L, times[2]);
	}

	@Test
	public void kymoFrameIndexesAccountForClippingAndSubsampling() {
		long[] cameraTimes = { 0L, 1000L, 3000L, 7000L, 8000L, 11000L };
		CsvNormalizedSpotExport.KymoFrameIndexes frames =
				new CsvNormalizedSpotExport.KymoFrameIndexes(cameraTimes, 2000L, 10000L, 2);
		assertEquals(Integer.valueOf(2), frames.at(0, 2));
		assertEquals(Integer.valueOf(4), frames.at(1, 2));
		assertEquals(Integer.valueOf(3), frames.at(1, 3));
		assertNull(frames.at(0, 1));
	}

	@Test
	public void spotFilesUseExperimentCageAndSpotKeys() throws Exception {
		Path folder = Files.createTempDirectory("spot-csv");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Arrays.asList("area_sum"),
				Arrays.asList("kymo_fract"), Arrays.asList("agg_sumclean"), false, 60000L, true)) {
			csv.ensureIdspot("expA", 2, "r0c1", "spot", 0, 1, 0.5, 12, "quinine", "10mM", "green", 1, "0");
			Map<String, Double> row = new LinkedHashMap<>();
			row.put("area_sum", 3.5);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 1.0, 4, row);
			csv.writeSpotRow(false, true, "expA", 2, "r0c1", 0.0, 0, row);
			csv.writeSpotRow(false, true, "expA", 2, "r0c1", 2.0, null, row);
			Map<String, Double> empty = new LinkedHashMap<>();
			empty.put("area_sum", Double.NaN);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 2.0, 5, empty);
			Map<String, Double> group = Collections.singletonMap("agg_sumclean", 3.5);
			csv.writeGroupRow(false, "expA", 2, "quinine__10mM", 1.0, 4, group);
			csv.writeGroupRow(true, "expA", 2, "quinine__10mM", 2.0, null, group);
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

	@Test
	public void groupIdJoinsStimulusAndConcentration() {
		assertEquals("quinine__10mM", CsvNormalizedSpotExport.buildGroupId("quinine", "10mM"));
	}
}
