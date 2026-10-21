package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.List;
import org.junit.Test;

public class IntakeEvaporationOverlayTest {
    @Test public void projectionStartsAtIntakeBottomAndResetsAtNextIntake() {
        boolean[] intake = {false, true, false, false, true, false, false};
        double[] raw = {19, 20, 20, 21, 24, 24, 25};
        double[] model = {0, .2, .5, .7, 1.1, 1.4, 1.8};
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(intake, raw, model);
        assertEquals(2, paths.size());
        List<Point2D> first = paths.get(0), second = paths.get(1);
        assertEquals(1.5, first.get(0).getX(), 1e-10);
        assertEquals(20, first.get(0).getY(), 1e-10);
        assertEquals(4.5, first.get(3).getX(), 1e-10);
        assertEquals(20.9, first.get(3).getY(), 1e-10);
        assertEquals(4.5, second.get(0).getX(), 1e-10);
        assertEquals(24, second.get(0).getY(), 1e-10);
        assertEquals(24.7, second.get(2).getY(), 1e-10);
    }

    @Test public void actualModelCurvatureIsKeptInsteadOfAssumingConstantSlope() {
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(new boolean[] {true, false, false, true},
                new double[] {10, 11, 12, 15}, new double[] {0, 1, 1.5, 1.75});
        assertEquals(1, paths.size());
        assertEquals(11, paths.get(0).get(1).getY(), 1e-10);
        assertEquals(11.5, paths.get(0).get(2).getY(), 1e-10);
        assertEquals(11.75, paths.get(0).get(3).getY(), 1e-10);
    }

    @Test public void missingModelBreaksTheProjectionUntilAnotherIntake() {
        List<List<Point2D>> paths = IntakeEvaporationOverlay.paths(new boolean[] {true, false, false, false, true, false},
                new double[] {10, 10, 11, 11, 15, 15}, new double[] {0, .2, Double.NaN, .6, .8, 1});
        assertEquals(2, paths.size());
        assertEquals(2, paths.get(0).size());
        assertEquals(4.5, paths.get(1).get(0).getX(), 1e-10);
    }

    @Test public void noIntakesOrOnlyFinalIntakeDoesNotInventABaseline() {
        assertTrue(IntakeEvaporationOverlay.paths(new boolean[3], new double[3], new double[3]).isEmpty());
        assertTrue(IntakeEvaporationOverlay.paths(new boolean[] {false, false, true}, new double[3], new double[3]).isEmpty());
    }
}
