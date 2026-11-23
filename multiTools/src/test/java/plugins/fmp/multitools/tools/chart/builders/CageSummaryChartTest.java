package plugins.fmp.multitools.tools.chart.builders;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotAggregateSeries;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CageSummaryChartTest {
	@org.junit.ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
			new plugins.fmp.multitools.test.InMemoryImageRule();
	@Test public void allLineSummariesUseTheWholeCageWithAndWithoutCachedAggregates() {
		Experiment exp = new Experiment();
		exp.setCamImages_ms(new long[] { 0, 60000 });
		exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(new long[] { 0, 60000 });
		exp.setKymoFirst_ms(0); exp.setKymoLast_ms(60000); exp.setKymoBin_ms(60000);
		Cage cage = new Cage(); cage.getProperties().setCageID(4);
		exp.getCages().cagesList.add(cage);
		Spot first = addSpot(exp, 4, "spot4_a");
		Spot second = addSpot(exp, 4, "spot4_b");
		for (EnumResults result : Family.LINES.measures(true)) {
			EnumResults source = SpotChartMeasures.sourceOf(result);
			first.getMeasurements(source).setValues(new double[] { 1, 0.4 });
			second.getMeasurements(source).setValues(new double[] { 1, 0.8 });
			ResultsOptions options = new ResultsOptions(); options.resultType = result;
			cage.getSpotAggregates().clear();
			boolean sum = result == EnumResults.AGG_KYMO_HEIGHT_LOSS || result == EnumResults.AGG_X_LINE_CONSO
					|| result == EnumResults.AGG_RIM_LINE_CONSO;
			XYSeriesCollection nativeData = KymoSpotChartSupport.buildCageDataset(exp, cage, exp.getSpots(), options);
			assertEquals(result.name(), 1, nativeData.getSeriesCount());
			assertEquals(sum ? 0.8 : 0.6, nativeData.getYValue(0, 1), 1e-9);
			exp.getCages().prepareSpotAggregates(exp, options);
			XYSeriesCollection cached = KymoSpotChartSupport.buildCageDataset(exp, cage, exp.getSpots(), options);
			assertEquals(nativeData.getYValue(0, 1), cached.getYValue(0, 1), 1e-9);
		}
	}

	@Test public void medianSummaryShowsOnlyItsReferenceOnThePrimaryGreyAxis() {
		Experiment exp = new Experiment();
		exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(new long[] { 0, 60000 });
		Cage cage = new Cage(); cage.getProperties().setCageID(2);
		exp.getCages().cagesList.add(cage);
		addSpot(exp, 2, "spot2_a");
		CageSpotStimulusAggregation.AggregateSeries aggregate = new CageSpotStimulusAggregation.AggregateSeries(
				new CageSpotStimulusAggregation.StimulusConcKey("sugar", "1"),
				new java.util.ArrayList<>(Arrays.asList(0.0, 0.5)), 1, new int[] { 1, 1 });
		cage.getSpotAggregates().setEntries(Arrays.asList(CageSpotAggregateSeries.fromNativeAggregate(aggregate)));
		cage.getSpotAggregates().setMedianRefSeries(CageSpotAggregateSeries.fromMedianRef(
				new java.util.ArrayList<>(Arrays.asList(10.0, 20.0)), 1));
		ResultsOptions options = new ResultsOptions(); options.resultType = EnumResults.CAGE_GREY_MEDIAN;
		XYSeriesCollection dataset = new CageSpotSeriesBuilder().build(exp, cage, options);
		assertEquals(1, dataset.getSeriesCount());
		assertEquals(20.0, dataset.getYValue(0, 1), 1e-9);
		XYPlot plot = CageChartPlotFactory.buildXYPlot(dataset, new NumberAxis("Time (min)"), new NumberAxis("grey value"));
		assertEquals(1, plot.getRangeAxisCount());
		assertEquals("grey value", plot.getRangeAxis().getLabel());
		assertEquals(1, plot.getDataset().getSeriesCount());
	}

	@Test public void primaryCageCurvesUseTheSameBaselineReferenceAsSpotOverlays() {
		Experiment exp = new Experiment();
		exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(new long[] { 0, 60000, 180000 });
		Cage cage = new Cage(); cage.getProperties().setCageID(1);
		exp.getCages().cagesList.add(cage);
		Spot spot = addSpot(exp, 1, "spot1_a");
		spot.getMeasurements(EnumResults.FILTER_GREY).setValues(new double[] { 10, 20, 100 });
		ResultsOptions options = new ResultsOptions(); options.resultType = EnumResults.FILTER_GREY;
		options.relativeToMaximum = true; options.spotBaselineWindowMinutes = 1;
		XYSeriesCollection dataset = new CageSpotSeriesBuilder().build(exp, cage, options);
		assertEquals(0.5, dataset.getYValue(0, 0), 1e-9);
		assertEquals(5.0, dataset.getYValue(0, 2), 1e-9);
	}

	private static Spot addSpot(Experiment exp, int cageId, String name) {
		Spot spot = new Spot(); spot.getProperties().setCageID(cageId); spot.getProperties().setName(name);
		spot.getProperties().setStimulus("sugar"); spot.getProperties().setConcentration("1");
		exp.getSpots().getSpotList().add(spot); return spot;
	}
}
