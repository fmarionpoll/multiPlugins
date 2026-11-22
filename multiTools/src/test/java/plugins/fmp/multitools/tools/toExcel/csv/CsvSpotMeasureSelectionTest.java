package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.ArrayList;
import org.junit.Test;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvSpotMeasureSelectionTest {
	@Test
	public void aggregateSelectionSplitsFileFamiliesAndCopiesIndependently() {
		ResultsOptions options = new ResultsOptions();
		options.spotAggregateMeasures = new ArrayList<>(Arrays.asList(EnumResults.AGG_KYMO_CROSS_LOSS, EnumResults.AGG_KYMO_RIM_LOSS, EnumResults.AGG_KYMO_HEIGHT_LOSS));
		ResultsOptions copy = new ResultsOptions();
		copy.copy(options);
		options.spotAggregateMeasures.clear();
		assertEquals(Arrays.asList(EnumResults.AGG_KYMO_CROSS_LOSS,
				EnumResults.AGG_KYMO_RIM_LOSS), Arrays.asList(CsvNormalizedSpotExport.selectedAggregateTypes(copy, false)));
		assertEquals(Arrays.asList(EnumResults.AGG_KYMO_HEIGHT_LOSS),
				Arrays.asList(CsvNormalizedSpotExport.selectedAggregateTypes(copy, true)));
		assertEquals(0, CsvNormalizedSpotExport.selectedAggregateTypes(options, false).length);
		assertEquals(0, CsvNormalizedSpotExport.selectedAggregateTypes(options, true).length);
	}
	@Test
	public void selectedLineMeasuresReachCsvColumnsAndSurviveOptionsCopy() {
		ResultsOptions options = new ResultsOptions();
		options.sum = false;
		assertEquals(0, CsvNormalizedSpotExport.primaryTypes(options).length);
		options.spotCrossRatio = true;
		assertEquals(Arrays.asList(EnumResults.KYMO_CROSS_RATIO, EnumResults.FLY_PIXEL_COUNT),
				Arrays.asList(CsvNormalizedSpotExport.primaryTypes(options)));
		options.spotRimRatio = true;
		options.spotKymoGreenHeight = true;
		ResultsOptions copy = new ResultsOptions();
		copy.copy(options);
		assertEquals(Arrays.asList(EnumResults.KYMO_GREEN_HEIGHT, EnumResults.KYMO_CROSS_RATIO,
				EnumResults.KYMO_RIM_RATIO, EnumResults.FLY_PIXEL_COUNT),
				Arrays.asList(CsvNormalizedSpotExport.primaryTypes(copy)));
		assertTrue(plugins.fmp.multitools.tools.toExcel.XLSExportSpots.hasAnySpotMeasureSelected(copy));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_CROSS_RATIO));
		assertFalse(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_RIM_RATIO));
		assertTrue(CsvNormalizedSpotExport.isKymoSeries(EnumResults.KYMO_GREEN_HEIGHT));
		options.spotCrossRatio = false;
		options.spotRimRatio = false;
		options.spotKymoGreenHeight = false;
		copy.copy(options);
		assertFalse(plugins.fmp.multitools.tools.toExcel.XLSExportSpots.hasAnySpotMeasureSelected(copy));
		assertEquals(0, CsvNormalizedSpotExport.primaryTypes(copy).length);
	}

}
