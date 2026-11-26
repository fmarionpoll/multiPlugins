package plugins.fmp.multitools.experiment.spot.fraction;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.*;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

public class SpotFractionCalculatorTest {
    private static Settings referenceSettings(ReferenceEstimation estimation, double start, double end) {
        return new Settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, 600,
                Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE, estimation, start, end);
    }
    @Test public void maximumReferenceUsesOnlyValidObservationsAndCanReplaceInitialEstimation() {
        SpotFractionPlan plan = new SpotFractionPlan();
        Spot source = spot("A", 1, false, new double[] {80, 999, 100, 50}, 0, SpotSampleFlags.OCCLUDED, 0, 0);
        assertEquals(80, calculate(plan, source).get(0).reference.value, 0);
        plan.settings.put(KEY, referenceSettings(ReferenceEstimation.OBSERVED_MAXIMUM, 0, 3));
        Row row = calculate(plan, source).get(0);
        assertEquals(100, row.reference.value, 0);
        assertEquals(ReferenceKind.OBSERVED_MAXIMUM, row.reference.kind);
        assertEquals(.5, row.remaining()[3], 0);
        assertArrayEquals(new double[] {80, 999, 100, 50}, source.getMethodMeasurements().get(KEY.method).getQuantity("signal").getRawValues(), 0);
    }
    @Test public void baselineMedianPrecedesGapFillingAndChangesOnlyWhenItsRecipeChanges() {
        SpotFractionPlan plan = new SpotFractionPlan();
        plan.settings.put(KEY, referenceSettings(ReferenceEstimation.BASELINE_MEDIAN, 0, 3));
        Spot source = spot("A", 1, false, new double[] {100, 999, 120, 100, 50}, 0, SpotSampleFlags.OCCLUDED, 0, 0, 0);
        Row row = calculate(plan, source).get(0);
        assertEquals(100, row.reference.value, 0);
        assertEquals(ReferenceKind.BASELINE_MEDIAN, row.reference.kind);
        assertEquals(-.2, row.consumed()[2], 1e-12);
        assertEquals(.5, row.consumed()[4], 0);
        assertTrue(row.fractions.getEstimatedSamples()[1]);
        plan.settings.put(KEY, referenceSettings(ReferenceEstimation.BASELINE_MEDIAN, 0, 2));
        row = calculate(plan, source).get(0);
        assertNull(row.reference); // Two valid samples; the filled gap must not count toward the baseline.
        assertFalse(row.issues.isEmpty());
    }
    @Test public void changedDataInvalidatesBaselineReferencesAndExplicitReferencesSurviveRecipeChanges() {
        SpotFractionPlan plan = new SpotFractionPlan();
        plan.settings.put(KEY, referenceSettings(ReferenceEstimation.BASELINE_MEDIAN, 0, 2));
        Row row = calculate(plan, spot("A", 1, false, new double[] {100, 100, 100, 50})).get(0);
        row = calculate(plan, spot("A", 1, false, new double[] {200, 200, 200, 100})).get(0);
        assertEquals(100, row.reference.value, 0);
        assertFalse(row.issues.isEmpty());
        plan.references.put(row.key, SpotFractionCalculator.reference(row.input, KEY.quantity, ReferenceKind.EXPLICIT,
                250, 0, "Approved deposit reference", true, Collections.emptyList(), Collections.singletonMap("A", row.input)));
        plan.settings.put(KEY, referenceSettings(ReferenceEstimation.OBSERVED_MAXIMUM, 0, 2));
        row = calculate(plan, spot("A", 1, false, new double[] {200, 200, 200, 100})).get(0);
        assertEquals(ReferenceKind.EXPLICIT, row.reference.kind);
        assertEquals(250, row.reference.value, 0);
    }
    public static final Key KEY = new Key(SpotMeasurementMethod.FILTER, "signal", "");
    public static Spot spot(String name, int cage, boolean consumed, double[] values, int... flags) {
        Spot spot = new Spot(); spot.getProperties().setName(name); spot.getProperties().setCageID(cage);
        spot.setConsumedBeforeRecording(consumed);
        double[] times = new double[values.length], fly = new double[values.length];
        for (int i = 0; i < times.length; i++) { times[i] = i; fly[i] = flags.length > 0 && flags[i] != 0 ? 1 : 0; }
        spot.getMethodMeasurements().put(new SpotMethodResult(KEY.method, times, fly, "fraction", Collections.emptyMap(),
                Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", values, flags.length == 0 ? new int[values.length] : flags))));
        return spot;
    }
    public static List<Input> inputs(Spot... spots) { List<Input> inputs = new ArrayList<>(); for (Spot spot : spots) inputs.add(new Input(spot, KEY.method)); return inputs; }
    private static List<Row> calculate(SpotFractionPlan plan, Spot... spots) { return SpotFractionCalculator.calculate(inputs(spots), KEY, plan); }
    private static Settings settings(GapPolicy quiet, GapPolicy changing, TailPolicy tail, Illumination illumination) {
        return new Settings(quiet, changing, tail, 600, Tolerance.PERCENT_REFERENCE, 8, 0, illumination);
    }
    @Test public void fixedInitialReferencePreservesNegativeConsumptionAndRawData() {
        SpotFractionPlan plan = new SpotFractionPlan(); Spot spot = spot("A", 1, false, new double[] {100, 120, 50});
        Row row = calculate(plan, spot).get(0);
        assertArrayEquals(new double[] {0, -.2, .5}, row.consumed(), 1e-12);
        assertEquals(100, row.reference.value, 0); assertEquals(ReferenceKind.INITIAL, row.reference.kind); assertTrue(plan.review.isEmpty());
        assertArrayEquals(new double[] {100, 120, 50}, spot.getMethodMeasurements().get(KEY.method).getQuantity("signal").getRawValues(), 0);
    }
    @Test public void delayedReferenceRemainsProvisionalUntilExplicitlyApproved() {
        SpotFractionPlan plan = new SpotFractionPlan(); Spot spot = spot("A", 1, false, new double[] {900, 80, 40}, SpotSampleFlags.OCCLUDED, 0, 0);
        Row row = calculate(plan, spot).get(0);
        assertEquals(ReferenceKind.DELAYED, row.reference.kind); assertFalse(row.reference.approved);
        assertEquals(1, row.reference.timeSeconds, 0); assertFalse(row.issues.isEmpty()); assertTrue(Double.isNaN(row.remaining()[0]));
        Map<String, Input> all = Collections.singletonMap("A", row.input);
        plan.references.put(row.key, SpotFractionCalculator.reference(row.input, KEY.quantity, ReferenceKind.DELAYED, 80, 1,
                "Delayed reference accepted by user", true, Collections.emptyList(), all));
        assertTrue(calculate(plan, spot).get(0).issues.isEmpty());
    }
    @Test public void quietAndChangedGapsUseDifferentPoliciesAndKeepEstimateMarkers() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, settings(GapPolicy.HOLD_PREVIOUS, GapPolicy.NONE, TailPolicy.KEEP_MISSING, Illumination.NONE));
        Spot spot = spot("A", 1, false, new double[] {100, 999, 96, 999, 50}, 0, SpotSampleFlags.OCCLUDED, 0, SpotSampleFlags.OCCLUDED, 0);
        Row row = calculate(plan, spot).get(0);
        assertArrayEquals(new double[] {1, 1, .96, Double.NaN, .5}, row.remaining(), 1e-12);
        assertArrayEquals(new boolean[] {false, true, false, false, false}, row.fractions.getEstimatedSamples());
        assertEquals(1, row.markedChanges);
    }
    @Test public void tailIsMissingByDefaultAndAnExplicitHoldIsMarkedEstimated() {
        SpotFractionPlan plan = new SpotFractionPlan(); Spot spot = spot("A", 1, false, new double[] {100, 80, 900}, 0, 0, SpotSampleFlags.OCCLUDED);
        assertTrue(Double.isNaN(calculate(plan, spot).get(0).remaining()[2]));
        plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.HOLD_PREVIOUS, Illumination.NONE));
        Row row = calculate(plan, spot).get(0); assertEquals(.8, row.remaining()[2], 0); assertTrue(row.fractions.getEstimatedSamples()[2]);
        plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.REVIEW, Illumination.NONE));
        assertFalse(calculate(plan, spot).get(0).issues.isEmpty());
    }
    @Test public void flaggedPreconsumedSpotBorrowsOnlyComparableCageReferences() {
        SpotFractionPlan plan = new SpotFractionPlan();
        List<Row> rows = calculate(plan, spot("empty", 1, true, new double[] {0, 0}), spot("A", 1, false, new double[] {100, 100}),
                spot("B", 1, false, new double[] {120, 120}), spot("other cage", 2, false, new double[] {9999, 9999}));
        Row empty = rows.get(0); assertEquals(110, empty.reference.value, 0); assertEquals(ReferenceKind.PRECONSUMED, empty.reference.kind);
        assertEquals(Arrays.asList("A", "B"), empty.reference.donors); assertTrue(empty.issues.isEmpty());
        assertArrayEquals(new double[] {1, 1}, empty.consumed(), 0);
    }
    @Test public void unexplainedInitialAbsenceIsNotReinterpretedAsALaterDepositBaseline() {
        Row row = calculate(new SpotFractionPlan(), spot("A", 1, false, new double[] {0, 30, 20})).get(0);
        assertNull(row.reference); assertFalse(row.issues.isEmpty()); assertTrue(Double.isNaN(row.remaining()[2]));
    }
    @Test public void failedDepositIsExcludedWithoutCreatingConsumption() {
        SpotFractionPlan plan = new SpotFractionPlan();
        plan.references.put(KEY.forSpot("A"), new Reference(ReferenceKind.FAILED_DEPOSIT, Double.NaN, 0, "Failed deposit", true, "", Collections.emptyList()));
        Row row = calculate(plan, spot("A", 1, false, new double[] {0, 0})).get(0);
        assertTrue(row.excluded); assertTrue(row.issues.isEmpty()); assertTrue(Double.isNaN(row.consumed()[0]));
    }
    @Test public void changedAcquisitionDoesNotSilentlyReplaceAnEstablishedReference() {
        SpotFractionPlan plan = new SpotFractionPlan(); calculate(plan, spot("A", 1, false, new double[] {100, 50}));
        Row row = calculate(plan, spot("A", 1, false, new double[] {200, 100})).get(0);
        assertEquals(100, row.reference.value, 0); assertTrue(row.issues.get(0).contains("changed"));
    }
    @Test public void donorChangesInvalidateBorrowedReferences() {
        SpotFractionPlan plan = new SpotFractionPlan();
        calculate(plan, spot("empty", 1, true, new double[] {0, 0}), spot("A", 1, false, new double[] {100, 100}));
        Row row = calculate(plan, spot("empty", 1, true, new double[] {0, 0}), spot("A", 1, false, new double[] {200, 200})).get(0);
        assertEquals(100, row.reference.value, 0); assertFalse(row.issues.isEmpty());
    }
    @Test public void cageDriftPreservesUpToTwoTrueChangesPerInterval() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, Illumination.CAGE));
        List<Row> rows = calculate(plan, spot("A", 1, false, new double[] {100, 60, 75}), spot("B", 1, false, new double[] {100, 60, 75}),
                spot("C", 1, false, new double[] {100, 120, 75}), spot("D", 1, false, new double[] {100, 120, 75}),
                spot("E", 1, false, new double[] {100, 120, 150}));
        assertArrayEquals(new double[] {1, 1.2, 1.5}, rows.get(4).factors(), 1e-12);
        assertArrayEquals(new double[] {0, 0, 0}, rows.get(4).consumed(), 1e-12);
        assertArrayEquals(new double[] {0, .5, .5}, rows.get(0).consumed(), 1e-12);
        assertArrayEquals(new double[] {0, 0, .5}, rows.get(2).consumed(), 1e-12);
        assertEquals(5, rows.get(0).support()[1]); assertTrue(plan.review.isEmpty());
    }
    @Test public void insufficientDriftSupportIsNotSilentlyCarriedForward() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, Illumination.CAGE));
        List<Row> rows = calculate(plan, spot("A", 1, false, new double[] {100, 120}), spot("B", 1, false, new double[] {100, 120}));
        assertTrue(Double.isNaN(rows.get(0).factors()[1])); assertFalse(rows.get(0).issues.isEmpty()); assertTrue(Double.isNaN(rows.get(0).remaining()[1]));
    }
    @Test public void spotOverrideDoesNotChangeOtherSpotsOrExperimentDefaults() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, Illumination.NONE));
        plan.settings.put(KEY.forSpot("A"), settings(GapPolicy.NONE, GapPolicy.NONE, TailPolicy.KEEP_MISSING, Illumination.NONE));
        List<Row> rows = calculate(plan, spot("A", 1, false, new double[] {100, 900, 100}, 0, 1, 0), spot("B", 1, false, new double[] {100, 900, 100}, 0, 1, 0));
        assertTrue(Double.isNaN(rows.get(0).remaining()[1])); assertEquals(1, rows.get(1).remaining()[1], 0);
    }
    @Test public void missingImageIsNotFilledAsAnOcclusion() {
        Row row = calculate(new SpotFractionPlan(), spot("A", 1, false, new double[] {100, 900, 100}, 0, SpotSampleFlags.MISSING_IMAGE, 0)).get(0);
        assertTrue(Double.isNaN(row.remaining()[1]));
    }
    @Test public void emptyFirstClearViewDoesNotAllowLaterPoopToEstablishTheReference() {
        Row row = calculate(new SpotFractionPlan(), spot("A", 1, false, new double[] {900, 0, 30}, 1, 0, 0)).get(0);
        assertNull(row.reference); assertFalse(row.issues.isEmpty());
    }
    @Test public void experimentDriftRequiresAMajorityAfterAllowingTwoChangesPerCage() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING, Illumination.EXPERIMENT));
        List<Spot> spots = new ArrayList<>();
        for (int cage = 1; cage <= 2; cage++) for (int i = 0; i < 5; i++) spots.add(spot("c" + cage + "s" + i, cage, false, new double[] {100, i < 2 ? 60 : 120}));
        List<Row> rows = calculate(plan, spots.toArray(new Spot[0]));
        assertEquals(1.2, rows.get(4).factors()[1], 1e-12); assertEquals(10, rows.get(4).support()[1]);
        assertEquals(.5, rows.get(0).consumed()[1], 1e-12); assertTrue(rows.get(4).issues.isEmpty());
    }
    @Test public void automaticBorrowingDoesNotMixStimuliOrUnknownCages() {
        Spot empty = spot("empty", 1, true, new double[] {0, 0}), donor = spot("donor", 1, false, new double[] {100, 100});
        empty.getProperties().setStimulus("water"); donor.getProperties().setStimulus("sugar");
        Row row = calculate(new SpotFractionPlan(), empty, donor).get(0);
        assertNull(row.reference); assertFalse(row.issues.isEmpty());
        assertNull(calculate(new SpotFractionPlan(), spot("empty", -1, true, new double[] {0, 0}),
                spot("donor", -1, false, new double[] {100, 100})).get(0).reference);
    }
    @Test public void noiseEstimationWithoutEnoughValidPairsRequiresReview() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, new Settings(GapPolicy.LINEAR, GapPolicy.LINEAR,
                TailPolicy.KEEP_MISSING, 600, Tolerance.ROBUST_NOISE, 0, 0, Illumination.NONE));
        Row row = calculate(plan, spot("A", 1, false, new double[] {100, 90})).get(0);
        assertTrue(row.issues.get(0).contains("noise"));
    }
    @Test public void changedSpotMetadataAlsoRequiresReferenceReview() {
        SpotFractionPlan plan = new SpotFractionPlan(); calculate(plan, spot("A", 1, false, new double[] {100, 90}));
        assertFalse(calculate(plan, spot("A", 2, false, new double[] {100, 90})).get(0).issues.isEmpty());
    }
}
