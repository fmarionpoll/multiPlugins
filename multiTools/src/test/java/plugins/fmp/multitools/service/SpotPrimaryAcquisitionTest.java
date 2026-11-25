package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.ClassRule;
import org.junit.Test;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class SpotPrimaryAcquisitionTest {
    @ClassRule public static final InMemoryImageRule memory = new InMemoryImageRule();

    @Test
    public void filterAndColorCaptureKeepIndependentFlyEvidenceAndPreGateValues() {
        Experiment exp = new Experiment() {
            @Override public double[] getMeasureTimeMinutes() { return new double[] { 0, 0.1, 0.5 }; }
        };
        Spot spot = new Spot();
        BuildSeriesOptions options = new BuildSeriesOptions();
        SpotCameraMeasurementCapture filter = new SpotCameraMeasurementCapture(exp, Collections.singletonList(spot), 3,
                SpotMeasurementMethod.FILTER, options, "test-filter");
        filter.record(spot, 0, 7, true, 999, 10);
        filter.missingImage(1);
        filter.publish();
        SpotMethodResult original = spot.getMethodMeasurements().get(SpotMeasurementMethod.FILTER);
        SpotCameraMeasurementCapture color = new SpotCameraMeasurementCapture(exp, Collections.singletonList(spot), 3,
                SpotMeasurementMethod.COLOR, options, "test-color");
        color.record(spot, 0, 0, false, 40, 5);
        color.publish();
        assertSame(original, spot.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        assertEquals(7, original.getFlyEvidence()[0], 0);
        assertEquals(0, spot.getMethodMeasurements().get(SpotMeasurementMethod.COLOR).getFlyEvidence()[0], 0);
        assertArrayEquals(new double[] { 0, 6, 30 }, original.getTimesSeconds(), 0.000001);
        assertEquals(999, original.getQuantity("thresholded_signal").getRawValues()[0], 0);
        assertEquals(SpotSampleFlags.OCCLUDED, original.getQuantity("thresholded_signal").getEffectiveFlags()[0]);
        assertEquals(SpotSampleFlags.MISSING_IMAGE, original.getQuantity("thresholded_signal").getEffectiveFlags()[1]);
        assertTrue(Double.isNaN(original.getQuantity("thresholded_signal").getRawValues()[2]));
    }

    @Test
    public void verticalCaptureUsesOriginalPixelsAndPreservesFlyContaminationAsRawData() {
        IcyBufferedImage image = new IcyBufferedImage(3, 4, 3, DataType.UBYTE);
        for (int c = 0; c < 3; c++) {
            byte[] pixels = new byte[12];
            Arrays.fill(pixels, (byte) (c == 0 ? 100 : 0));
            image.setDataXY(c, pixels);
        }
        ((byte[]) image.getDataXY(2))[1] = (byte) 250; // Fly on middle column, independent of spot metric.
        image.dataChanged();
        CageKymoAnalyzer.Params params = new CageKymoAnalyzer.Params(ImageTransformEnums.R_RGB, 50, 0, 1,
                false, 10, true, false, true, ImageTransformEnums.B_RGB, 200, true);
        SpotMethodResult primary = SpotVerticalMeasurementCapture.capture(image,
                new CageKymographSpotBands(new Spot(), 0, 4, false), params, new double[] { 0, 1, 9 });
        assertArrayEquals(new double[] { 1, 1, 1 }, primary.getQuantity("row_fraction").getRawValues(), 0);
        assertArrayEquals(new double[] { 0, 0.25, 0 }, primary.getFlyEvidence(), 0);
        assertArrayEquals(new double[] { 1, Double.NaN, 1 }, SpotSeriesProcessor.process(primary, "row_fraction",
                SpotProcessingSettings.OBSERVATIONS).getValues(), 0);
        assertArrayEquals(new double[] { 4, 4, 4 }, primary.getQuantity("green_height").getRawValues(), 0);
    }
}
