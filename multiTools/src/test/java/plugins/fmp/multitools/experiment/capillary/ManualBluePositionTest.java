package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import org.junit.BeforeClass;
import org.junit.Test;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class ManualBluePositionTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test public void addingFirstPhasePreservesLegacyImageZeroEndpoints() {
        ROI2DLine green = new ROI2DLine(new Line2D.Double(10, 0, 10, 100));
        green.setName("line0L");
        Capillary cap = new Capillary(green);
        cap.getProperties().setMeasuredEndpoints(new java.awt.geom.Point2D.Double(12, 10),
                new java.awt.geom.Point2D.Double(12, 90));
        Capillaries caps = new Capillaries();
        caps.getList().add(cap);
        SequenceCamData data = new SequenceCamData();
        data.setSequence(new Sequence());
        ROI2DLine edited = new ROI2DLine(new Line2D.Double(20, 10, 20, 90));
        edited.setName(CapillaryMeasuredTipsOverlay.ROI_PREFIX + CapillaryMeasuredTipsOverlay.tipSuffix(cap, -1));
        data.getSequence().addROI(edited);
        assertEquals(1, CapillaryMeasuredTipsOverlay.editBluePositionFromSequence(caps, data, 25, true));
        assertNotNull(cap.getPhaseGeometry().getBlueStartingAt(0));
        assertEquals(12, cap.getPhaseGeometry().getBlueAt(0).getX1(), 0);
        assertEquals(12, cap.getPhaseGeometry().getBlueAt(24).getX1(), 0);
        assertEquals(20, cap.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        CapillaryMeasuredTipsOverlay.transferTipsToSequence(caps, data, 0);
        ROI2DLine shown = (ROI2DLine) data.findROIsMatchingNamePattern(CapillaryMeasuredTipsOverlay.ROI_PREFIX).get(0);
        assertEquals(12, shown.getLine().getX1(), 0);
    }

    @Test public void manualPositionsPreserveEarlierAndLaterPhasesAndIgnoreGreenBoundaries() {
        ROI2DLine green = new ROI2DLine(new Line2D.Double(10, 0, 10, 100));
        green.setName("line0L");
        Capillary cap = new Capillary(green);
        cap.getPhaseGeometry().initialize(0, green.getLine(), new Line2D.Double(10, 10, 10, 90));
        cap.getPhaseGeometry().putBlue(80, new Line2D.Double(30, 10, 30, 90));
        cap.setAlongTList(java.util.Arrays.asList(new AlongT(0, green), new AlongT(40, green)));
        Capillaries caps = new Capillaries();
        caps.getList().add(cap);
        SequenceCamData data = new SequenceCamData();
        data.setSequence(new Sequence());
        ROI2DLine edited = new ROI2DLine(new Line2D.Double(20, 10, 20, 90));
        edited.setName(CapillaryMeasuredTipsOverlay.ROI_PREFIX + CapillaryMeasuredTipsOverlay.tipSuffix(cap, -1));
        data.getSequence().addROI(edited);

        assertEquals(1, CapillaryMeasuredTipsOverlay.editBluePositionFromSequence(caps, data, 25, true));
        assertEquals(10, cap.getPhaseGeometry().getBlueAt(24).getX1(), 0);
        assertEquals(20, cap.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        edited.setLine(new Line2D.Double(22, 10, 22, 90));
        assertEquals(1, CapillaryMeasuredTipsOverlay.editBluePositionFromSequence(caps, data, 50, false));
        assertEquals(22, cap.getPhaseGeometry().getBlueAt(25).getX1(), 0);
        assertNull(cap.getPhaseGeometry().getBlueStartingAt(40));
        assertNull(cap.getPhaseGeometry().getBlueStartingAt(50));
        assertEquals(30, cap.getPhaseGeometry().getBlueAt(80).getX1(), 0);
        assertEquals(2, cap.getAlongTList().size());
    }
}
