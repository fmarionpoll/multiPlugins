package plugins.fmp.multitools.series;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.ClassRule;
import org.junit.Test;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;
import plugins.fmp.multitools.tools.chart.builders.KymoSpotChartSupport;

public class SpotLineCameraSamplingTest {
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
            new plugins.fmp.multitools.test.InMemoryImageRule();

    private Experiment experiment() {
        Experiment exp = new Experiment() { @Override public void loadFileIntervalsFromSeqCamData() { } };
        exp.getSeqCamData().setImagesList(Arrays.asList("frame0.jpg", "frame1.jpg", "frame2.jpg", "frame3.jpg"));
        exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(new long[] { 0, 20000, 45000, 60000 });
        exp.setKymoFirst_ms(40000);
        exp.setKymoLast_ms(60000);
        exp.setKymoBin_ms(60000);
        return exp;
    }

    @Test public void bothAnalyzersIgnoreKymographClippingAndSubsampling() {
        Experiment exp = experiment();
        assertEquals(Arrays.asList(0, 1, 2, 3), AnalyzeSpotLineDeficits.binFrames(exp));
        assertEquals(Arrays.asList(0, 1, 2, 3), AnalyzeSpotRims.binFrames(exp));
    }

    @Test public void samplingMarkerSurvivesSavingLoadingAndCopying() throws Exception {
        String directory = Files.createTempDirectory("camera-line-persistence").toString();
        Spots spots = new Spots();
        Spot spot = new Spot();
        spot.getProperties().setName("spotA");
        for (EnumResults type : new EnumResults[] { EnumResults.KYMO_CROSS_RATIO, EnumResults.KYMO_RIM_RATIO }) {
            SpotMeasure measure = spot.getMeasurements(type);
            measure.setValues(new double[] { 1, 0.8, 0.6, 0.4 });
            measure.setCameraSampled(true);
        }
        spots.getSpotList().add(spot);
        assertTrue(spots.getPersistence().saveKymoMeasures(spots, directory));
        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        Spot restored = loaded.findSpotByName("spotA");
        assertNotNull(restored);
        for (EnumResults type : new EnumResults[] { EnumResults.KYMO_CROSS_RATIO, EnumResults.KYMO_RIM_RATIO }) {
            assertTrue(restored.getMeasurements(type).isCameraSampled());
            assertArrayEquals(spot.getMeasurements(type).getValues(), restored.getMeasurements(type).getValues(), 0);
            SpotMeasure copy = new SpotMeasure("copy");
            copy.copyMeasures(restored.getMeasurements(type));
            assertTrue(copy.isCameraSampled());
        }
    }

    @Test public void nativeCameraTimesAndExportGridStaySeparateFromKymographGrid() {
        Experiment exp = experiment();
        Spot spot = new Spot();
        exp.getSpots().getSpotList().add(spot);
        ResultsOptions options = new ResultsOptions();
        options.buildExcelStepMs = 30000;
        for (EnumResults type : new EnumResults[] { EnumResults.KYMO_CROSS_RATIO, EnumResults.KYMO_RIM_RATIO }) {
            SpotMeasure measure = spot.getMeasurements(type);
            measure.setValues(new double[] { 1, 0.8, 0.6, 0.4 });
            measure.setCameraSampled(true);
            options.resultType = type;
            SpotExcelTimeline.SpotExcelGrid grid = SpotExcelTimeline.buildForSpotExport(exp, options);
            assertArrayEquals(new long[] { 0, 20000, 45000, 60000 }, grid.getFrameElapsedMsRelative());
            assertEquals(3, grid.getNBins());
            assertEquals(0.72, measure.getValuesResampledToExcelGrid(grid).get(1), 1e-9);
            measure.setCameraSampled(false);
            grid = SpotExcelTimeline.buildForSpotExport(exp, options);
            assertArrayEquals(new long[] { 40000, 100000, 160000, 220000 }, grid.getFrameElapsedMsRelative());
            assertEquals(40000, grid.getClipStartMs());
        }
    }

    @Test(timeout = 10000) public void chartsAndAggregateChartsUseCameraTimestamps() {
        Experiment exp = experiment();
        Cage cage = new Cage();
        cage.getProperties().setCageID(1);
        exp.getCages().getCageList().add(cage);
        Spot spot = new Spot();
        spot.getProperties().setCageID(1);
        spot.getCrossLineSignalToBaselineRatio().setValues(new double[] { 1, 0.8, 0.6, 0.4 });
        spot.getCrossLineSignalToBaselineRatio().setCameraSampled(true);
        exp.getSpots().getSpotList().add(spot);
        ResultsOptions options = new ResultsOptions();
        for (EnumResults type : new EnumResults[] { EnumResults.KYMO_CROSS_RATIO, EnumResults.AGG_KYMO_CROSS_LOSS }) {
            options.resultType = type;
            org.jfree.data.xy.XYSeriesCollection data = KymoSpotChartSupport.buildCageDataset(exp, cage,
                    exp.getSpots(), options);
            assertEquals(1, data.getSeriesCount());
            assertEquals(4, data.getSeries(0).getItemCount());
            assertEquals(0.75, data.getSeries(0).getX(2).doubleValue(), 1e-9);
            assertEquals(1, data.getSeries(0).getX(3).doubleValue(), 1e-9);
        }
        assertEquals(2, KymoSpotChartSupport.cameraFrameForChartMinute(exp, EnumResults.KYMO_CROSS_RATIO, 0.75));
    }
}
