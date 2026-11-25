package plugins.fmp.multitools.tools.chart;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts.Measure;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

public class SpotAcquisitionChartsTest {
    @Test public void collectionsOfferOnlyNativeMeasurementsAndMethodSpecificFlyEvidence() {
        Set<SpotMeasurementMethod> flies = EnumSet.noneOf(SpotMeasurementMethod.class);
        for (Family family : Family.values()) for (Measure measure : SpotAcquisitionCharts.measures(family)) {
            if (measure.flyOnly) flies.add(measure.method);
            else {
                assertFalse(measure.quantity.contains("ratio")); assertFalse(measure.quantity.contains("clean"));
                assertFalse(measure.quantity.contains("smooth")); assertFalse(measure.quantity.contains("delta"));
            }
        }
        assertEquals(EnumSet.allOf(SpotMeasurementMethod.class), flies);
    }
    private void primary(Spot spot, SpotMeasurementMethod method, String quantity, double[] flies) {
        spot.getMethodMeasurements().put(new SpotMethodResult(method, new double[] {0, 90, 400}, flies,
                "covered_pixels", Collections.emptyMap(), Collections.singletonList(new SpotMeasuredQuantity(quantity,
                        "intensity", new double[] {100, 999, 50}, new int[] {0, SpotSampleFlags.OCCLUDED, 0}))));
    }
    @Test public void rawUnitsAndNativeTimestampsSurviveWhileOcclusionsStayMissing() {
        Spot spot = new Spot(); spot.getProperties().setName("A");
        primary(spot, SpotMeasurementMethod.FILTER, "thresholded_signal", new double[] {0, 7, Double.NaN});
        Measure measure = SpotAcquisitionCharts.measures(Family.FILTER).get(0);
        XYSeriesCollection data = SpotAcquisitionCharts.data(Collections.singletonList(spot), measure, false);
        assertEquals(100, data.getYValue(0, 0), 0); assertNull(data.getY(0, 1)); assertEquals(50, data.getYValue(0, 2), 0);
        assertEquals(1.5, data.getXValue(0, 1), 0); assertEquals(400.0 / 60, data.getXValue(0, 2), 0);
        XYSeriesCollection flies = SpotAcquisitionCharts.data(Collections.singletonList(spot), measure, true);
        assertEquals(7, flies.getYValue(0, 1), 0); assertNull(flies.getY(0, 2));
    }
    @Test public void colorAndFilterFlyEvidenceNeverShareTheLegacySeries() {
        Spot spot = new Spot(); spot.getProperties().setName("A"); spot.getFlyCoveredPixelCount().setIsPresent(new int[] {999, 999, 999});
        primary(spot, SpotMeasurementMethod.FILTER, "thresholded_signal", new double[] {0, 7, 0});
        primary(spot, SpotMeasurementMethod.COLOR, "grey_signal", new double[] {0, 3, 0});
        assertEquals(7, SpotAcquisitionCharts.data(Collections.singletonList(spot), SpotAcquisitionCharts.measures(Family.FILTER).get(0), true).getYValue(0, 1), 0);
        assertEquals(3, SpotAcquisitionCharts.data(Collections.singletonList(spot), SpotAcquisitionCharts.measures(Family.COLORS).get(0), true).getYValue(0, 1), 0);
    }
    @Test public void processedLegacyValuesCannotMasqueradeAsRawAcquisition() {
        Spot spot = new Spot(); spot.getRoiNormalizedGreySignal().setValues(new double[] {1, .5});
        assertEquals(0, SpotAcquisitionCharts.data(Collections.singletonList(spot), SpotAcquisitionCharts.measures(Family.FILTER).get(0), false).getSeriesCount());
    }
}
