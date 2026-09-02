package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPhaseGeometryPersistence;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CapillaryLengthApplyTest {
    @org.junit.BeforeClass public static void initializeIcy() {
        icy.preferences.IcyPreferences.init();
    }
    private Capillary capillary() {
        Capillary cap = new Capillary();
        cap.setKymographName("line01");
        cap.setRoi(new ROI2DLine(new Line2D.Double(10, 0, 10, 130)));
        return cap;
    }

    private CapillaryLengthResult result(Capillary cap, boolean selected) {
        CapillaryLengthResult result = new CapillaryLengthResult();
        CapillaryLengthResult.Measure m = new CapillaryLengthResult.Measure(cap, "line01", 100);
        m.setDetectedEndpoints(new Point2D.Double(10, 15), new Point2D.Double(10, 115));
        m.setDetectedPixels(100);
        m.setSelected(selected);
        result.addMeasure(m);
        return result;
    }

    @Test public void replacesOldBlueAndPersistsWithoutChangingLaterPhase() throws Exception {
        Capillary cap = capillary();
        cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 130),
                new Line2D.Double(10, 5, 10, 125));
        cap.getPhaseGeometry().putBlue(50, new Line2D.Double(20, 20, 20, 120));
        assertEquals(1, CapillaryLengthDetector.apply(result(cap, true), 0));
        assertEquals(15, cap.getPhaseGeometry().getBlueAt(0).getY1(), 0);
        assertEquals(20, cap.getPhaseGeometry().getBlueAt(50).getY1(), 0);
        assertEquals(15, cap.getProperties().getMeasuredStart().getY(), 0);
        Capillaries source = new Capillaries(); source.addCapillary(cap);
        Path directory = Files.createTempDirectory("capillary-redetection-");
        assertTrue(CapillaryPhaseGeometryPersistence.save(source, directory.toString()));
        Capillary restored = capillary();
        Capillaries loaded = new Capillaries(); loaded.addCapillary(restored);
        assertTrue(CapillaryPhaseGeometryPersistence.load(loaded, directory.toString()));
        assertEquals(15, restored.getPhaseGeometry().getBlueAt(0).getY1(), 0);
        assertEquals(20, restored.getPhaseGeometry().getBlueAt(50).getY1(), 0);
    }

    @Test public void initializesBlueAndLeavesUnselectedMeasurementsUntouched() {
        Capillary cap = capillary();
        assertEquals(0, CapillaryLengthDetector.apply(result(cap, false)));
        assertFalse(cap.getPhaseGeometry().isInitialized());
        assertEquals(1, CapillaryLengthDetector.apply(result(cap, true)));
        assertTrue(cap.getPhaseGeometry().isInitialized());
        assertEquals(15, cap.getPhaseGeometry().getBlueAt(0).getY1(), 0);
    }

    @Test public void laterFrameUpdatesOnlyItsContainingPhase() {
        Capillary cap = capillary();
        cap.getAlongTList();
        cap.addAlongTAtStartIfMissing(50);
        cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 130),
                new Line2D.Double(10, 5, 10, 125));
        cap.getProperties().setMeasuredEndpoints(new Point2D.Double(10, 5), new Point2D.Double(10, 125));
        assertEquals(1, CapillaryLengthDetector.apply(result(cap, true), 60));
        assertEquals(5, cap.getPhaseGeometry().getBlueAt(0).getY1(), 0);
        assertEquals(15, cap.getPhaseGeometry().getBlueStartingAt(50).getY1(), 0);
        assertEquals(5, cap.getProperties().getMeasuredStart().getY(), 0);
    }
}
