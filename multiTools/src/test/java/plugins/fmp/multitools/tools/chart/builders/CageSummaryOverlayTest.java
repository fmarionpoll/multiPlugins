package plugins.fmp.multitools.tools.chart.builders;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.ClassRule;
import org.junit.Test;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.plot.CageChartPlotFactory;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CageSummaryOverlayTest {
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule memory =
            new plugins.fmp.multitools.test.InMemoryImageRule();

    private ResultsOptions options() {
        ResultsOptions options = new ResultsOptions();
        options.resultType = EnumResults.AGG_KYMO_HEIGHT_LOSS;
        options.cageSummaryMeasures = Arrays.asList(EnumResults.AGG_KYMO_HEIGHT_LOSS,
                EnumResults.AGG_X_LINE_CONSO, EnumResults.AGG_RIM_LINE_CONSO);
        return options;
    }

    private Cage cage(Experiment exp, int id, double remaining) {
        Cage cage = new Cage(); cage.getProperties().setCageID(id);
        exp.getCages().cagesList.add(cage);
        Spot spot = new Spot();
        spot.getProperties().setCageID(id);
        spot.getProperties().setName("spot_" + id);
        spot.getProperties().setStimulus("sucrose");
        spot.getProperties().setConcentration("70 mM");
        spot.getMeasurements(EnumResults.KYMO_GREEN_HEIGHT_RATIO).setValues(new double[] {1, remaining, remaining});
        spot.getMeasurements(EnumResults.X_LINE_RATIO).setValues(new double[] {1, .8, .7});
        spot.getMeasurements(EnumResults.RIM_LINE_RATIO).setValues(new double[] {1, .9});
        exp.getSpots().getSpotList().add(spot);
        return cage;
    }

    @Test public void eachCageContainsAllMeasuresWithIndependentLengthsAndCaches() {
        Experiment exp = new Experiment(); exp.setKymoBin_ms(60000);
        Cage zero = cage(exp, 0, .5), one = cage(exp, 1, .2);
        CageSummaryOverlay builder = new CageSummaryOverlay(new CageSpotSeriesBuilder());
        ResultsOptions options = options();
        XYSeriesCollection first = builder.build(exp, zero, options);
        XYSeriesCollection second = builder.build(exp, one, options);
        assertEquals(3, first.getSeriesCount());
        assertEquals(.5, first.getYValue(0, 1), 1e-12);
        assertEquals(.8, second.getYValue(0, 1), 1e-12);
        assertEquals(.2, first.getYValue(1, 1), 1e-12);
        assertEquals(.1, first.getYValue(2, 1), 1e-12);
        assertEquals(2, first.getItemCount(2));
        assertEquals(3, first.getItemCount(0));
        assertEquals(EnumResults.AGG_X_LINE_CONSO, CageSummaryOverlay.measure(first.getSeries(1)));
        assertTrue(CageSummaryOverlay.label(first.getSeries(1)).contains("sucrose"));
        assertEquals(3, options.cageSummaryMeasures.size());
        assertEquals(EnumResults.AGG_KYMO_HEIGHT_LOSS, options.resultType);
        XYPlot plot = CageChartPlotFactory.buildXYPlot(first, new NumberAxis(), new NumberAxis());
        assertEquals(1, plot.getRangeAxisCount());
        assertEquals("Consumed spot equivalents", plot.getRangeAxis().getLabel());
        assertNotEquals(plot.getRenderer().getSeriesStroke(0), plot.getRenderer().getSeriesStroke(1));
    }

    @Test public void mixedUnitsGetSeparateAxesAndNullSamplesSurvive() {
        Experiment exp = new Experiment(); Cage cage = cage(exp, 0, .5);
        CageSeriesBuilder source = (experiment, value, request) -> {
            XYSeriesCollection data = new XYSeriesCollection();
            XYSeries row = new XYSeries("test", false);
            row.add(0, 1); row.add(2.5, (Number) null); row.add(3, 2);
            data.addSeries(row);
            return data;
        };
        ResultsOptions options = options();
        options.cageSummaryMeasures = Arrays.asList(EnumResults.AGG_KYMO_HEIGHT_LOSS, EnumResults.KYMO_CAGE_MEAN_FRACT);
        XYSeriesCollection data = new CageSummaryOverlay(source).build(exp, cage, options);
        assertNull(data.getY(1, 1));
        assertEquals(2.5, data.getXValue(1, 1), 0);
        NumberAxis primary = new NumberAxis(); primary.setRange(0, 1000);
        XYPlot plot = CageChartPlotFactory.buildXYPlot(data, new NumberAxis(), primary);
        assertEquals(2, plot.getRangeAxisCount());
        assertEquals(1, plot.getDataset(1).getSeriesCount());
        assertEquals(EnumResults.KYMO_CAGE_MEAN_FRACT.toUnit(), plot.getRangeAxis(1).getLabel());
        assertTrue(plot.getRangeAxis().isAutoRange());
    }

    @Test public void singleMeasureUsesUnchangedDatasetAndOptionsCopyOwnsItsSelection() {
        ResultsOptions options = options();
        ResultsOptions copied = new ResultsOptions(); copied.copy(options);
        copied.cageSummaryMeasures.clear();
        assertEquals(3, options.cageSummaryMeasures.size());
        options.cageSummaryMeasures = Collections.singletonList(options.resultType);
        XYSeriesCollection original = new XYSeriesCollection();
        assertSame(original, new CageSummaryOverlay((e, c, o) -> original).build(null, null, options));
    }
}
