package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvNormalizedSpotExportTest {

	@Test
	public void cameraAndKymoColumnsStayOnSeparateClocks() {
		assertEquals("area_sum", CsvNormalizedSpotExport.columnName(EnumResults.AREA_SUM));
		assertEquals("kymo_fract", CsvNormalizedSpotExport.columnName(EnumResults.KYMO_FRACT));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AREA_FLYPRESENT));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AREA_SUMCLEAN));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_GREEN_HEIGHT_RATIO));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_GREENHEIGHT_CONSO));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.AGG_SUMCLEAN));
	}

	@Test
	public void spotMeasureTypesIncludeFlyPresenceWithTheSelectedBoxes() {
		ResultsOptions options = new ResultsOptions();
		options.spotAreas = true;
		options.sum = true;
		options.spotKymoFract = true;
		EnumResults[] types = plugins.fmp.multitools.tools.toExcel.XLSExportSpots.spotMeasureTypes(options);
		assertEquals(EnumResults.AREA_SUM, types[0]);
		assertEquals(EnumResults.KYMO_FRACT, types[1]);
		assertEquals(EnumResults.AREA_FLYPRESENT, types[2]);
	}

	@Test
	public void kymoRawTimesUseTheKymographBin() {
		long[] times = CsvNormalizedSpotExport.rawTimes(null, new ResultsOptions(), EnumResults.KYMO_FRACT, 3, null);
		assertEquals(0L, times[0]);
		assertEquals(60000L, times[1]);
		assertEquals(120000L, times[2]);
	}

	@Test
	public void spotFilesUseExperimentCageAndSpotKeys() throws Exception {
		Path folder = Files.createTempDirectory("spot-csv");
		try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, Arrays.asList("area_sum"),
				Arrays.asList("kymo_fract"), Arrays.asList("agg_sumclean"), false, 60000L, true)) {
			csv.ensureIdspot("expA", 2, "r0c1", "spot", 0, 1, 0.5, 12, "quinine", "10mM", "green", 1, "0");
			Map<String, Double> row = new LinkedHashMap<>();
			row.put("area_sum", 3.5);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 1.0, row);
			csv.writeSpotRow(false, true, "expA", 2, "r0c1", 0.0, row);
			Map<String, Double> empty = new LinkedHashMap<>();
			empty.put("area_sum", Double.NaN);
			csv.writeSpotRow(false, false, "expA", 2, "r0c1", 2.0, empty);
		}

		String idspot = read(folder.resolve("idspot.csv"));
		assertTrue(idspot.contains("experiment_id,cage_id,spot_id"));
		assertTrue(idspot.contains("expA,2,r0c1,spot,0,1,0.5,12,quinine,10mM,green,1,0"));

		String raw = read(folder.resolve("measure_spot_raw.csv"));
		assertTrue(raw.startsWith("experiment_id,cage_id,spot_id,time_min,area_sum"));
		assertTrue(raw.contains("expA,2,r0c1,1.0,3.5"));
		assertFalse(raw.contains(",2.0,"));

		String bin = read(folder.resolve("measure_spot_bin60.csv"));
		assertTrue(bin.contains("expA,2,r0c1,0.0,3.5"));
		assertFalse(Files.exists(folder.resolve("measure_spot_kymo_raw.csv")));
	}

	private static String read(Path file) throws Exception {
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}

	@Test
	public void groupIdJoinsStimulusAndConcentration() {
		assertEquals("quinine__10mM", CsvNormalizedSpotExport.buildGroupId("quinine", "10mM"));
	}
}
