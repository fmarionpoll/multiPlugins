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

import org.junit.Test;

import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvNormalizedSpotExportTest {

	@Test
	public void aggregateRenamesPreserveExportIdentifiers() {
		assertAggregateIdentifier(EnumResults.AGG_FILTER_DEPLETION, "AGG_SUMCLEAN");
		assertAggregateIdentifier(EnumResults.AGG_FILTER_V5_DEPLETION, "AGG_SUMCLEAN_V5");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_DEPLETION, "AGG_SUMCLEAN_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_FILTER_V5_PIXEL_SUM, "AGG_AREA_COUNT_V5");
		assertAggregateIdentifier(EnumResults.AGG_COLOR_PIXEL_SUM, "AGG_AREA_COUNT_COLOR");
		assertAggregateIdentifier(EnumResults.AGG_KYMO_HEIGHT_LOSS, "AGG_GREENHEIGHT_CONSO");
		assertAggregateIdentifier(EnumResults.AGG_KYMO_CROSS_LOSS, "AGG_LINE_CONSO");
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
