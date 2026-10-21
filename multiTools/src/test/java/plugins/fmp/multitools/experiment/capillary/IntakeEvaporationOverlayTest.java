package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.Test;

public class IntakeEvaporationOverlayTest {
    @Test public void anchoredOnceAndOffsetByExactFractionalAmplitudes() {
        double[] amplitudes = {0, 2.5, 0, 0, 1.25, 0, 0};
        // Later raw levels deliberately diverge: they must not reset the model.
        double[] raw = {19, 30, 31, 32, 45, 46, 47};
        double[] model = {5, 5.2, 5.5, 5.7, 6.1, 6.4, 6.8};
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(amplitudes, raw, model);
        assertEquals(1, paths.size());
        List<Point2D> points = paths.get(0);
        assertEquals(9, points.size()); // seven times + two vertical steps
        point(points.get(0), .5, 19);
        point(points.get(1), 1.5, 19.2);
        point(points.get(2), 1.5, 21.7);
        point(points.get(5), 4.5, 22.6);
        point(points.get(6), 4.5, 23.85);
        point(points.get(8), 6.5, 24.55);
    }

    @Test public void noIntakesShowsTheWholeEvaporationModelFromT0() {
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(new double[4],
                new double[] {10, 20, 30, 40}, new double[] {3, 4, 4.5, 4.75});
        assertEquals(1, paths.size());
        point(paths.get(0).get(0), .5, 10);
        point(paths.get(0).get(1), 1.5, 11);
        point(paths.get(0).get(2), 2.5, 11.5);
        point(paths.get(0).get(3), 3.5, 11.75);
    }

    @Test public void missingModelBreaksDrawingButPreservesAnchorAndAccumulatedIntake() {
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(new double[] {0, 0, 2, 0, 0},
                new double[] {10, 15, 20, 30, 40}, new double[] {0, .2, Double.NaN, .6, .8});
        assertEquals(2, paths.size());
        assertEquals(2, paths.get(0).size());
        point(paths.get(1).get(0), 3.5, 12.6);
        point(paths.get(1).get(1), 4.5, 12.8);
    }

    @Test public void finalIntakeStillMakesItsFullVerticalStep() {
        List<Point2D> points = IntakeEvaporationOverlay.paths(new double[] {0, 0, .4},
                new double[] {20, 21, 25}, new double[] {0, .1, .2}).get(0);
        point(points.get(2), 2.5, 20.2);
        point(points.get(3), 2.5, 20.6);
    }

    @Test public void missingInitialAnchorDoesNotInventAReplacementFromLaterRaw() {
        assertTrue(IntakeEvaporationOverlay.paths(new double[3], new double[] {Double.NaN, 10, 11}, new double[3]).isEmpty());
        assertTrue(IntakeEvaporationOverlay.paths(new double[3], new double[] {10, 11, 12}, new double[] {Double.NaN, 0, 1}).isEmpty());
        assertTrue(IntakeEvaporationOverlay.paths(new double[0], new double[0], new double[0]).isEmpty());
    }

    @Test public void laterRawGapsDoNotMoveOrBreakThePrediction() {
        List<Point2D> points = IntakeEvaporationOverlay.paths(new double[] {0, 0, 1},
                new double[] {10, Double.NaN, Double.NaN}, new double[] {0, .2, .4}).get(0);
        point(points.get(1), 1.5, 10.2);
        point(points.get(3), 2.5, 11.4);
    }

    private static void point(Point2D point, double x, double y) {
        assertEquals(x, point.getX(), 1e-10);
        assertEquals(y, point.getY(), 1e-10);
    }

    @Test public void compensatedStepsSubtractOnlyTheirOwnIntervalEvaporation() {
        double[] amplitudes = {0, 2.5, 0, 0, 1.25, 0, 0};
        double[] original = amplitudes.clone();
        double[] raw = {19, 30, 31, 32, 45, 46, 47};
        double[] model = {5, 5.2, 5.5, 5.7, 6.1, 6.4, 6.8};
        List<Point2D> net = IntakeEvaporationOverlay.paths(amplitudes, raw, model, true).get(0);
        List<Point2D> total = IntakeEvaporationOverlay.paths(amplitudes, raw, model, false).get(0);
        point(net.get(0), .5, 19);
        point(net.get(2), 1.5, 21.5); // step 2.5 - 0.2 = 2.3
        point(net.get(6), 4.5, 23.25); // step 1.25 - 0.4 = 0.85
        point(net.get(8), 6.5, 23.95);
        assertEquals(.6, total.get(8).getY() - net.get(8).getY(), 1e-10);
        assertArrayEquals(original, amplitudes, 0);
    }

    @Test public void compensationCannotCreateANegativeFeedingStep() {
        List<Point2D> points = IntakeEvaporationOverlay.paths(new double[] {0, .1, 0},
                new double[] {10, 11, 12}, new double[] {0, .2, .4}, true).get(0);
        assertEquals(3, points.size());
        point(points.get(1), 1.5, 10.2);
        point(points.get(2), 2.5, 10.4);
    }

    @Test public void compensationLeavesPureEvaporationUnchanged() {
        double[] raw = {10, 12, 13};
        double[] model = {0, .2, .4};
        List<Point2D> net = IntakeEvaporationOverlay.paths(new double[3], raw, model, true).get(0);
        point(net.get(2), 2.5, 10.4);
    }

    @Test public void missingIntervalReferenceDoesNotInventANetIntake() {
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(new double[] {0, 0, 0, 2, 0},
                new double[] {10, 11, 12, 13, 14}, new double[] {0, .2, Double.NaN, .6, .8}, true);
        assertEquals(1, paths.size());
        assertEquals(2, paths.get(0).size());
    }
}
