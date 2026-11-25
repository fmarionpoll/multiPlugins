package plugins.fmp.multitools.experiment.spot.measurement;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.EnumResults.PersistenceDomain;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.BaselinePolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;

public class SpotMeasurementModelTest {
    private static final int OCCLUDED = SpotSampleFlags.OCCLUDED;

    static SpotMethodResult result(SpotMeasurementMethod method, double[] times, double[] values, int[] flags) {
        double[] fly = new double[values.length];
        Arrays.fill(fly, Double.NaN);
        return new SpotMethodResult(method, times, fly, "covered_pixels", Collections.emptyMap(),
                Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", values, flags)));
    }

    static SpotMethodResult result(double[] times, double[] values, int[] flags) {
        return result(SpotMeasurementMethod.FILTER, times, values, flags);
    }

    static SpotProcessingSettings settings(GapPolicy policy, double maxSeconds, double tolerance,
            int medianRadius, BaselinePolicy baseline) {
        return new SpotProcessingSettings(policy, maxSeconds, tolerance, medianRadius, baseline,
                Double.NaN, Double.NaN, "", false);
    }

    @Test
    public void acquisitionDataAndMetadataAreDefensivelyCopied() {
        double[] times = { 0, 10 };
        double[] values = { 100, 200 };
        double[] fly = { 0, 8 };
        int[] flags = { 0, OCCLUDED };
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("threshold", "20");
        SpotMeasuredQuantity quantity = new SpotMeasuredQuantity("signal", "intensity", values, flags);
        SpotMethodResult result = new SpotMethodResult(SpotMeasurementMethod.COLOR, times, fly, "covered_pixels",
                parameters, Collections.singletonList(quantity));
        times[0] = 999;
        values[0] = 999;
        fly[0] = 999;
        flags[0] = OCCLUDED;
        parameters.put("threshold", "999");
        result.getTimesSeconds()[0] = 999;
        result.getFlyEvidence()[0] = 999;
        quantity.getRawValues()[0] = 999;
        quantity.getAcquisitionFlags()[0] = OCCLUDED;
        assertArrayEquals(new double[] { 0, 10 }, result.getTimesSeconds(), 0);
        assertArrayEquals(new double[] { 0, 8 }, result.getFlyEvidence(), 0);
        assertArrayEquals(new double[] { 100, 200 }, quantity.getRawValues(), 0);
        assertArrayEquals(new int[] { 0, OCCLUDED }, quantity.getAcquisitionFlags());
        assertEquals("20", result.getParameters().get("threshold"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void quantityMapCannotBeChanged() {
        result(new double[] { 0 }, new double[] { 10 }, new int[] { 0 }).getQuantities().clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnalignedFlyEvidence() {
        new SpotMethodResult(SpotMeasurementMethod.COLOR, new double[] { 0, 1 }, new double[] { 0 },
                "pixels", Collections.emptyMap(), Collections.singletonList(
                        new SpotMeasuredQuantity("signal", "intensity", new double[] { 1, 2 }, new int[2])));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateTimesRatherThanInventingATimeline() {
        result(new double[] { 1, 1 }, new double[] { 1, 2 }, new int[2]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsQuantitiesOnDifferentTimelines() {
        new SpotMethodResult(SpotMeasurementMethod.FILTER, new double[] { 0, 1 }, new double[] { 0, 0 },
                "pixels", Collections.emptyMap(), Collections.singletonList(
                        new SpotMeasuredQuantity("signal", "intensity", new double[] { 1 }, new int[1])));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateQuantityIds() {
        SpotMeasuredQuantity q = new SpotMeasuredQuantity("signal", "intensity", new double[] { 1 }, new int[1]);
        new SpotMethodResult(SpotMeasurementMethod.FILTER, new double[] { 0 }, new double[] { 0 },
                "pixels", Collections.emptyMap(), Arrays.asList(q, q));
    }

    @Test
    public void builderKeepsUnrecordedSamplesUnknownAndSnapshotsIndependent() {
        SpotMethodRecording recording = new SpotMethodRecording(SpotMeasurementMethod.X_LINES,
                new double[] { 0, 1, 2 }, "occupancy_fraction")
                        .parameter("insect_threshold", "20")
                        .quantity("signal", "intensity").quantity("area", "pixels");
        recording.recordFlyEvidence(1, 0.75);
        recording.record("signal", 1, 23, OCCLUDED);
        recording.record("area", 1, 7, OCCLUDED);
        SpotMethodResult first = recording.build();
        recording.record("signal", 1, 999, 0);
        assertArrayEquals(new double[] { Double.NaN, 23, Double.NaN }, first.getQuantity("signal").getRawValues(), 0);
        assertArrayEquals(new double[] { Double.NaN, 0.75, Double.NaN }, first.getFlyEvidence(), 0);
        assertEquals(SpotSampleFlags.DETECTION_FAILED, first.getQuantity("area").getEffectiveFlags()[0]);
        assertEquals("20", first.getParameters().get("insect_threshold"));
    }

    @Test
    public void manualRejectionsAreSeparateAndReversible() {
        SpotMeasuredQuantity original = new SpotMeasuredQuantity("signal", "intensity",
                new double[] { 100, 900 }, new int[] { 0, OCCLUDED });
        SpotMeasuredQuantity edited = original.withManualRejection(1, true);
        assertArrayEquals(new double[] { 100, 900 }, edited.getRawValues(), 0);
        assertEquals(OCCLUDED, edited.getAcquisitionFlags()[1]);
        assertEquals(OCCLUDED | SpotSampleFlags.MANUALLY_REJECTED, edited.getEffectiveFlags()[1]);
        assertEquals(OCCLUDED, edited.withManualRejection(1, false).getEffectiveFlags()[1]);
        assertFalse(original.getManualRejections()[1]);
    }

    @Test
    public void cacheTracksSettingsAndInvalidatesOnlyReplacedMethod() {
        SpotMeasurements measurements = new SpotMeasurements();
        measurements.put(result(new double[] { 0, 1, 2 }, new double[] { 100, 999, 80 }, new int[] { 0, OCCLUDED, 0 }));
        measurements.put(result(SpotMeasurementMethod.RIM_LINES, new double[] { 0 }, new double[] { 7 }, new int[1]));
        SpotDerivedSeries observations = measurements.derive(SpotMeasurementMethod.FILTER, "signal",
                SpotProcessingSettings.OBSERVATIONS);
        SpotDerivedSeries rim = measurements.derive(SpotMeasurementMethod.RIM_LINES, "signal",
                SpotProcessingSettings.OBSERVATIONS);
        assertSame(observations, measurements.derive(SpotMeasurementMethod.FILTER, "signal",
                SpotProcessingSettings.OBSERVATIONS));
        SpotProcessingSettings linear = settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.NONE);
        SpotDerivedSeries filled = measurements.derive(SpotMeasurementMethod.FILTER, "signal", linear);
        assertNotSame(observations, filled);
        assertSame(filled, measurements.derive(SpotMeasurementMethod.FILTER, "signal",
                settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.NONE)));
        SpotMethodResult old = measurements.get(SpotMeasurementMethod.FILTER);
        measurements.put(old.withQuantity(old.getQuantity("signal").withManualRejection(2, true)));
        assertNotSame(filled, measurements.derive(SpotMeasurementMethod.FILTER, "signal", linear));
        assertSame(rim, measurements.derive(SpotMeasurementMethod.RIM_LINES, "signal", SpotProcessingSettings.OBSERVATIONS));
        measurements.remove(SpotMeasurementMethod.FILTER);
        assertNull(measurements.get(SpotMeasurementMethod.FILTER));
    }

    @Test
    public void flyDetectorsAndQuantitiesRemainIndependentAcrossMethods() {
        SpotMeasurements measurements = new SpotMeasurements();
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values()) {
            SpotMethodRecording recording = new SpotMethodRecording(method, new double[] { 0 }, "pixels")
                    .quantity("signal", "intensity");
            recording.recordFlyEvidence(0, method.ordinal());
            recording.record("signal", 0, 100, method == SpotMeasurementMethod.COLOR ? OCCLUDED : 0);
            measurements.put(recording.build());
        }
        assertTrue(Double.isNaN(measurements.derive(SpotMeasurementMethod.COLOR, "signal",
                SpotProcessingSettings.OBSERVATIONS).getValues()[0]));
        assertEquals(100, measurements.derive(SpotMeasurementMethod.RIM_LINES, "signal",
                SpotProcessingSettings.OBSERVATIONS).getValues()[0], 0);
        assertEquals(SpotMeasurementMethod.RIM_LINES.ordinal(),
                measurements.get(SpotMeasurementMethod.RIM_LINES).getFlyEvidence()[0], 0);
    }

    @Test
    public void spotCopyPreservesPrimaryDataWithoutSharingMutableRegistry() {
        Spot source = new Spot();
        SpotMethodResult result = result(new double[] { 0 }, new double[] { 100 }, new int[1]);
        source.getMethodMeasurements().put(result);
        Spot copy = new Spot(source, true);
        assertSame(result, copy.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        copy.getMethodMeasurements().clear();
        assertSame(result, source.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        assertTrue(new Spot(source, false).getMethodMeasurements().snapshot().isEmpty());
        copy.copyFrom(source, true);
        for (EnumResults legacy : EnumResults.values()) {
            if (legacy.isPersistedIn(PersistenceDomain.SPOT) && legacy != EnumResults.FLY_PIXEL_COUNT) {
                source.getMeasurements(legacy).setValues(new double[] { 100 });
            }
        }
        source.normalizeMeasures();
        assertArrayEquals(new double[] { 100 }, copy.getMethodMeasurements().get(SpotMeasurementMethod.FILTER)
                .getQuantity("signal").getRawValues(), 0);
    }
}
