package plugins.fmp.multitools.experiment.spot.measurement;

import static org.junit.Assert.*;
import static plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementModelTest.result;
import static plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementModelTest.settings;

import org.junit.Test;

import plugins.fmp.multitools.experiment.spot.measurement.SpotDerivedSeries.GapKind;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.BaselinePolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;

public class SpotSeriesProcessorTest {
    private static final int OCCLUDED = SpotSampleFlags.OCCLUDED;

    private SpotDerivedSeries process(double[] times, double[] values, int[] flags, SpotProcessingSettings settings) {
        return SpotSeriesProcessor.process(result(times, values, flags), "signal", settings);
    }

    @Test
    public void defaultMasksContaminatedValuesWithoutChangingRawMeasurements() {
        SpotMethodResult raw = result(new double[] { 0, 10, 20 }, new double[] { 100, 900, 80 },
                new int[] { 0, OCCLUDED, 0 });
        SpotDerivedSeries derived = SpotSeriesProcessor.process(raw, "signal", SpotProcessingSettings.OBSERVATIONS);
        assertArrayEquals(new double[] { 100, Double.NaN, 80 }, derived.getValues(), 0);
        assertArrayEquals(new double[] { 100, 900, 80 }, raw.getQuantity("signal").getRawValues(), 0);
        assertArrayEquals(new boolean[] { false, false, false }, derived.getEstimatedSamples());
        derived.getValues()[0] = 999;
        derived.getObservationFlags()[0] = OCCLUDED;
        derived.getEstimatedSamples()[0] = true;
        assertEquals(100, derived.getValues()[0], 0);
        assertEquals(0, derived.getObservationFlags()[0]);
        assertFalse(derived.getEstimatedSamples()[0]);
    }

    @Test
    public void interpolationUsesElapsedTimeAndPreservesRejectionFlags() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 9, 10 }, new double[] { 100, 999, 999, 80 },
                new int[] { 0, OCCLUDED, OCCLUDED, 0 }, settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.NONE));
        assertArrayEquals(new double[] { 100, 98, 82, 80 }, derived.getValues(), 0.000001);
        assertArrayEquals(new boolean[] { false, true, true, false }, derived.getEstimatedSamples());
        assertArrayEquals(new int[] { 0, OCCLUDED, OCCLUDED, 0 }, derived.getObservationFlags());
    }

    @Test
    public void holdPreviousIsExplicitAndDoesNotMoveTheRightObservation() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2 }, new double[] { 100, 999, 80 },
                new int[] { 0, OCCLUDED, 0 }, settings(GapPolicy.HOLD_PREVIOUS, 2, 0, 0, BaselinePolicy.NONE));
        assertArrayEquals(new double[] { 100, 100, 80 }, derived.getValues(), 0);
    }

    @Test
    public void durationLimitUsesBracketTimes() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 10 }, new double[] { 100, 999, 80 },
                new int[] { 0, OCCLUDED, 0 }, settings(GapPolicy.LINEAR, 9, 0, 0, BaselinePolicy.NONE));
        assertTrue(Double.isNaN(derived.getValues()[1]));
    }

    @Test
    public void neitherLeadingNorTrailingGapsAreExtrapolated() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2, 3, 4 },
                new double[] { 999, 100, 999, 80, 999 }, new int[] { OCCLUDED, 0, OCCLUDED, 0, OCCLUDED },
                settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.FIRST_VALID));
        assertArrayEquals(new double[] { Double.NaN, 1, 0.9, 0.8, Double.NaN }, derived.getValues(), 0.000001);
        assertEquals(GapKind.LEADING, derived.getGaps().get(0).kind);
        assertEquals(GapKind.INTERNAL, derived.getGaps().get(1).kind);
        assertEquals(GapKind.TRAILING, derived.getGaps().get(2).kind);
        assertEquals(1, derived.baselineTimeSeconds, 0);
        assertEquals(100, derived.baselineValue, 0);
        assertTrue(derived.reviewRequired);
    }

    @Test
    public void missingImagesManualRejectionsAndMixedCausesAreNotFilledAsFlyVisits() {
        for (int flag : new int[] { SpotSampleFlags.MISSING_IMAGE, SpotSampleFlags.DETECTION_FAILED,
                SpotSampleFlags.INVALID_BACKGROUND, SpotSampleFlags.QUALITY_UNKNOWN,
                SpotSampleFlags.MANUALLY_REJECTED, OCCLUDED | SpotSampleFlags.MISSING_IMAGE }) {
            SpotDerivedSeries derived = process(new double[] { 0, 1, 2 }, new double[] { 100, 999, 100 },
                    new int[] { 0, flag, 0 }, settings(GapPolicy.LINEAR, 10, 1, 0, BaselinePolicy.NONE));
            assertTrue(Double.isNaN(derived.getValues()[1]));
            assertFalse(derived.getGaps().get(0).connectableForDisplay);
        }
    }

    @Test
    public void equalEndpointsCanBeConnectedForDisplayWithoutFillingData() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2 }, new double[] { 100, 900, 100.5 },
                new int[] { 0, OCCLUDED, 0 }, settings(GapPolicy.NONE, 10, 1, 0, BaselinePolicy.NONE));
        assertTrue(derived.getGaps().get(0).connectableForDisplay);
        assertTrue(Double.isNaN(derived.getValues()[1]));
        assertFalse(derived.getEstimatedSamples()[1]);
        assertEquals(1, derived.getGaps().get(0).firstMissing);
        assertEquals(1, derived.getGaps().get(0).lastMissing);
    }

    @Test
    public void baselineComesFromFirstValidObservationNotGlobalMaximumOrMedian() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2 }, new double[] { 100, 120, 80 }, new int[3],
                settings(GapPolicy.NONE, 0, 0, 1, BaselinePolicy.FIRST_VALID));
        assertEquals(100, derived.baselineValue, 0);
        assertEquals(0, derived.baselineTimeSeconds, 0);
        assertFalse(derived.reviewRequired);
        assertEquals("first_valid_observation", derived.baselineSource);
        assertTrue(derived.smoothed);
    }

    @Test
    public void invalidBaselineDoesNotProduceInfiniteNormalizedValues() {
        SpotDerivedSeries derived = process(new double[] { 0, 1 }, new double[] { 0, 10 }, new int[2],
                settings(GapPolicy.NONE, 0, 0, 0, BaselinePolicy.FIRST_VALID));
        assertArrayEquals(new double[] { Double.NaN, Double.NaN }, derived.getValues(), 0);
        assertTrue(derived.reviewRequired);
    }

    @Test
    public void explicitReviewedBaselineResolvesDelayedReferenceWithoutInventingLeadingValues() {
        SpotProcessingSettings settings = new SpotProcessingSettings(GapPolicy.LINEAR, 10, 0, 0,
                BaselinePolicy.EXPLICIT, 100, -1, "pre-exposure image", true);
        SpotDerivedSeries derived = process(new double[] { 0, 1 }, new double[] { 999, 80 },
                new int[] { OCCLUDED, 0 }, settings);
        assertArrayEquals(new double[] { Double.NaN, 0.8 }, derived.getValues(), 0);
        assertEquals(-1, derived.baselineTimeSeconds, 0);
        assertEquals("pre-exposure image", derived.baselineSource);
        assertFalse(derived.reviewRequired);
    }

    @Test
    public void unreviewedExplicitBaselineRequiresAttention() {
        SpotProcessingSettings settings = new SpotProcessingSettings(GapPolicy.NONE, 0, 0, 0,
                BaselinePolicy.EXPLICIT, 100, 0, "provisional", false);
        assertTrue(process(new double[] { 0 }, new double[] { 100 }, new int[1], settings).reviewRequired);
    }

    @Test
    public void medianCannotBridgeAnUnfilledGap() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2, 3, 4 },
                new double[] { 100, 100, 999, 20, 20 }, new int[] { 0, 0, OCCLUDED, 0, 0 },
                settings(GapPolicy.NONE, 0, 0, 100, BaselinePolicy.NONE));
        assertArrayEquals(new double[] { 100, 100, Double.NaN, 20, 20 }, derived.getValues(), 0);
    }

    @Test
    public void unflaggedNaNsBecomeDetectionFailuresAndStayUnfilled() {
        SpotDerivedSeries derived = process(new double[] { 0, 1, 2 }, new double[] { 100, Double.NaN, 80 }, new int[3],
                settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.NONE));
        assertTrue(Double.isNaN(derived.getValues()[1]));
        assertEquals(SpotSampleFlags.DETECTION_FAILED, derived.getObservationFlags()[1]);
    }

    @Test
    public void entirelyOccludedRecordingRequiresReviewAndHasNoInventedBaseline() {
        SpotDerivedSeries derived = process(new double[] { 0, 1 }, new double[] { 999, 999 },
                new int[] { OCCLUDED, OCCLUDED }, settings(GapPolicy.LINEAR, 10, 0, 0, BaselinePolicy.FIRST_VALID));
        assertArrayEquals(new double[] { Double.NaN, Double.NaN }, derived.getValues(), 0);
        assertTrue(Double.isNaN(derived.baselineValue));
        assertTrue(derived.reviewRequired);
        assertEquals(GapKind.ENTIRE_RECORDING, derived.getGaps().get(0).kind);
    }

    @Test
    public void emptyRecordingHasNoGapsOrInventedValues() {
        SpotDerivedSeries derived = process(new double[0], new double[0], new int[0], SpotProcessingSettings.OBSERVATIONS);
        assertEquals(0, derived.getValues().length);
        assertTrue(derived.getGaps().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void explicitBaselineRequiresProvenance() {
        new SpotProcessingSettings(GapPolicy.NONE, 0, 0, 0, BaselinePolicy.EXPLICIT, 100, 0, "", true);
    }
}
