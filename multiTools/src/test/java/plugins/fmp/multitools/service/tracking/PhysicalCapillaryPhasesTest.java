package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import java.nio.file.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPhaseGeometryPersistence;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class PhysicalCapillaryPhasesTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    private Capillary cap(String name, double x) {
        ROI2DLine green = new ROI2DLine(new Line2D.Double(x, 0, x, 100));
        green.setName(name);
        Capillary cap = new Capillary(green);
        cap.getPhaseGeometry().initialize(0, green.getLine(), new Line2D.Double(x, 10, x, 90), 3);
        return cap;
    }

    @Test public void proposalsAndDeletionUseActualBluePhasesAndPersist() throws Exception {
        Capillaries caps = new Capillaries();
        Capillary a = cap("line0L", 10), b = cap("line0R", 30);
        caps.getList().add(a); caps.getList().add(b);
        a.getPhaseGeometry().putBlue(80, new Line2D.Double(14, 10, 14, 90), 4);
        assertEquals(java.util.Arrays.asList(0L, 80L), new java.util.ArrayList<>(PhysicalCapillaryPhases.starts(caps)));
        assertEquals(2, PhysicalCapillaryPhases.addFromPrevious(caps, 25));
        assertEquals(0, PhysicalCapillaryPhases.addFromPrevious(caps, 25));
        assertEquals(10, a.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        assertEquals(3, a.getPhaseGeometry().getWidthAt(25), 0);
        a.getPhaseGeometry().putBlue(25, new Line2D.Double(12, 10, 12, 90), 5);
        caps.getTrackingTimeline().addManual(25, "phase metadata");
        assertEquals(12, a.getPhaseGeometry().getBlueAt(79).getX1(), 0);
        assertEquals(0, PhysicalCapillaryPhases.remove(caps, 0));
        assertEquals(2, PhysicalCapillaryPhases.remove(caps, 25));
        assertNull(caps.getTrackingTimeline().get(25));
        assertEquals(10, a.getPhaseGeometry().getBlueAt(79).getX1(), 0);
        assertEquals(3, a.getPhaseGeometry().getWidthAt(79), 0);
        assertEquals(14, a.getPhaseGeometry().getBlueAt(80).getX1(), 0);
        assertEquals(4, a.getPhaseGeometry().getWidthAt(80), 0);
        assertEquals(0, PhysicalCapillaryPhases.remove(caps, 25));
        String directory = Files.createTempDirectory("physical-phases-").toString();
        assertTrue(CapillaryPhaseGeometryPersistence.save(caps, directory));
        a.getPhaseGeometry().clear(); b.getPhaseGeometry().clear();
        assertTrue(CapillaryPhaseGeometryPersistence.load(caps, directory));
        assertNull(a.getPhaseGeometry().getBlueStartingAt(25));
        assertNotNull(a.getPhaseGeometry().getBlueStartingAt(0));
        assertEquals(14, a.getPhaseGeometry().getBlueAt(80).getX1(), 0);
    }

    @Test public void proposalsPreserveExistingPositionsAndSkipCapillariesWithoutPhysicalData() {
        Capillaries caps = new Capillaries();
        Capillary existing = cap("line0L", 10);
        existing.getPhaseGeometry().putBlue(25, new Line2D.Double(20, 10, 20, 90));
        caps.getList().add(existing);
        caps.getList().add(new Capillary());
        assertEquals(0, PhysicalCapillaryPhases.addFromPrevious(caps, 25));
        assertEquals(20, existing.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        assertFalse(caps.getList().get(1).getPhaseGeometry().isInitialized());
    }

    @Test public void legacyReferenceAppearsAndIsPreservedWhenProposalCreatesFirstLaterPhase() {
        Capillaries caps = new Capillaries();
        Capillary cap = cap("line0L", 10);
        cap.getPhaseGeometry().clear();
        cap.getProperties().setMeasuredEndpoints(new java.awt.geom.Point2D.Double(12, 10),
                new java.awt.geom.Point2D.Double(12, 90));
        caps.getList().add(cap);
        assertTrue(PhysicalCapillaryPhases.starts(caps).contains(0L));
        assertEquals(1, PhysicalCapillaryPhases.addFromPrevious(caps, 25));
        assertEquals(12, cap.getPhaseGeometry().getBlueAt(0).getX1(), 0);
        assertEquals(12, cap.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        assertEquals(0, PhysicalCapillaryPhases.remove(caps, 0));
    }
}
