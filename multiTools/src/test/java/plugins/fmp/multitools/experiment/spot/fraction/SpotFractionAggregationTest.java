package plugins.fmp.multitools.experiment.spot.fraction;

import static org.junit.Assert.*;
import static plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculatorTest.*;
import java.util.*;
import org.junit.Test;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.*;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

public class SpotFractionAggregationTest {
    private List<Group> groups(SpotFractionPlan plan, Spot... spots) {
        List<Input> inputs = inputs(spots); List<Row> rows = SpotFractionCalculator.calculate(inputs, KEY, plan);
        return SpotFractionAggregation.build(inputs, rows, KEY, plan);
    }
    private SpotFractionPlan noFill() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.settings.put(KEY, new Settings(GapPolicy.NONE, GapPolicy.NONE,
                TailPolicy.KEEP_MISSING, 600, Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE)); return plan;
    }
    @Test public void totalsAndMeansDoNotDropMissingMembersOrClipNegativeConsumption() {
        Group group = groups(noFill(), spot("A", 1, false, new double[] {100, 900, 50, 120}, 0, 1, 0, 0),
                spot("B", 1, false, new double[] {100, 100, 50, 100})).get(0);
        assertEquals(2, group.expectedSpots); assertArrayEquals(new int[] {2, 1, 2, 2}, group.availableSpots());
        assertArrayEquals(new double[] {0, Double.NaN, 1, -.2}, group.values(Measure.SUM_CONSUMED), 1e-12);
        assertArrayEquals(new double[] {0, Double.NaN, .5, -.1}, group.values(Measure.MEAN_CONSUMED), 1e-12);
    }
    @Test public void absentPrimaryMeasurementsKeepTheExpectedCohortFixed() {
        Spot missing = new Spot(); missing.getProperties().setName("missing"); missing.getProperties().setCageID(1);
        Group group = groups(new SpotFractionPlan(), spot("A", 1, false, new double[] {100, 50}), missing).get(0);
        assertEquals(2, group.expectedSpots); assertArrayEquals(new int[] {1, 1}, group.availableSpots());
        assertTrue(Double.isNaN(group.values(Measure.SUM_CONSUMED)[1]));
    }
    @Test public void provisionalReferencesDoNotBecomeCompleteTotals() {
        Group group = groups(new SpotFractionPlan(), spot("A", 1, false, new double[] {100, 50}),
                spot("B", 1, false, new double[] {900, 80}, 1, 0)).get(0);
        assertArrayEquals(new int[] {1, 1}, group.availableSpots());
        assertTrue(Double.isNaN(group.values(Measure.SUM_REMAINING)[1]));
    }
    @Test public void failedDepositsAreExplicitlyExcludedRatherThanCountedAsConsumed() {
        SpotFractionPlan plan = new SpotFractionPlan(); plan.references.put(KEY.forSpot("B"), new Reference(ReferenceKind.FAILED_DEPOSIT,
                Double.NaN, 0, "Failed deposit", true, "", Collections.emptyList()));
        Group group = groups(plan, spot("A", 1, false, new double[] {100, 50}), spot("B", 1, false, new double[] {0, 0})).get(0);
        assertEquals(1, group.expectedSpots); assertArrayEquals(new double[] {0, .5}, group.values(Measure.SUM_CONSUMED), 0);
    }
    @Test public void filledContributionsAreMarkedOnTheAggregate() {
        Group group = groups(new SpotFractionPlan(), spot("A", 1, false, new double[] {100, 900, 50}, 0, 1, 0),
                spot("B", 1, false, new double[] {100, 100, 100})).get(0);
        assertEquals(.25, group.values(Measure.SUM_CONSUMED)[1], 1e-12);
        assertArrayEquals(new boolean[] {false, true, false}, group.estimatedSamples());
    }
    @Test public void nativeTimeAlignmentDoesNotResampleOrBridgeMissingSamples() {
        Spot a = spot("A", 1, false, new double[] {100, 75, 50}), b = spot("B", 1, false, new double[] {100, 50});
        SpotMethodResult original = b.getMethodMeasurements().get(KEY.method);
        b.getMethodMeasurements().put(new SpotMethodResult(KEY.method, new double[] {0, 2}, new double[] {0, 0}, "fraction",
                original.getParameters(), original.getQuantities().values()));
        Group group = groups(new SpotFractionPlan(), a, b).get(0);
        assertArrayEquals(new double[] {0, 1, 2}, group.timesSeconds(), 0);
        assertArrayEquals(new double[] {0, Double.NaN, 1}, group.values(Measure.SUM_CONSUMED), 1e-12);
    }
    @Test public void cagesAndConditionsAreNotPooledTogether() {
        Spot a = spot("A", 1, false, new double[] {100, 50}), b = spot("B", 1, false, new double[] {100, 50}), c = spot("C", 2, false, new double[] {100, 50});
        a.getProperties().setStimulus("sugar"); b.getProperties().setStimulus("water"); c.getProperties().setStimulus("sugar");
        List<Group> groups = groups(new SpotFractionPlan(), a, b, c); assertEquals(3, groups.size());
        for (Group group : groups) assertEquals(1, group.expectedSpots);
    }
}
