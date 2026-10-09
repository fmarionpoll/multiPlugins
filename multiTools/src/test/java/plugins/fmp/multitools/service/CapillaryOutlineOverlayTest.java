package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import org.junit.BeforeClass;
import org.junit.Test;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasuredTipsOverlay;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class CapillaryOutlineOverlayTest {
    @BeforeClass
    public static void initializeIcy() {
        icy.preferences.IcyPreferences.init();
    }

    @Test
    public void outlineFollowsEditedBlueLineAndNeverBecomesACapillaryRoi() throws Exception {
        Capillary cap = new Capillary();
        cap.setKymographName("line01");
        Line2D green = new Line2D.Double(40, 0, 40, 150);
        cap.setRoi(new ROI2DLine(green));
        cap.getPhaseGeometry().initialize(0, green, new Line2D.Double(40, 20, 40, 120), 8.);
        cap.getPhaseGeometry().putBlue(50, new Line2D.Double(45, 20, 45, 120), Double.NaN);
        Capillaries caps = new Capillaries();
        caps.addCapillary(cap);
        try (SequenceCamData sequence = new SequenceCamData()) {
            CapillaryMeasuredTipsOverlay.setOutlinesVisible(sequence, true);
            assertEquals(1, CapillaryMeasuredTipsOverlay.transferTipsToSequence(caps, sequence, 0));
            ROI2DLine blue = (ROI2DLine) sequence.findROIsMatchingNamePattern(
                    CapillaryMeasuredTipsOverlay.ROI_PREFIX).get(0);
            ROI2DPolygon outline = (ROI2DPolygon) sequence.findROIsMatchingNamePattern(
                    CapillaryMeasuredTipsOverlay.OUTLINE_PREFIX).get(0);
            assertTrue(outline.isReadOnly());
            assertEquals(8., outline.getBounds2D().getWidth(), 1e-9);
            assertTrue(sequence.findROIsMatchingNamePattern("line").isEmpty());
            javax.swing.SwingUtilities.invokeAndWait(() -> blue.setLine(new Line2D.Double(50, 25, 50, 125)));
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertEquals(46., outline.getBounds2D().getX(), 1e-9);
            assertEquals(25., outline.getBounds2D().getY(), 1e-9);
            assertEquals(1, CapillaryMeasuredTipsOverlay.transferTipsFromSequence(caps, sequence, 0));
            assertEquals(8., cap.getPhaseGeometry().getWidthAt(0), 0.);
            CapillaryMeasuredTipsOverlay.transferTipsToSequence(caps, sequence, 60);
            assertTrue(sequence.findROIsMatchingNamePattern(CapillaryMeasuredTipsOverlay.OUTLINE_PREFIX).isEmpty());
            CapillaryMeasuredTipsOverlay.setOutlinesVisible(sequence, false);
            CapillaryMeasuredTipsOverlay.transferTipsToSequence(caps, sequence, 0);
            assertTrue(sequence.findROIsMatchingNamePattern(CapillaryMeasuredTipsOverlay.OUTLINE_PREFIX).isEmpty());
            CapillaryMeasuredTipsOverlay.removeTipsFromSequence(sequence);
            assertTrue(sequence.getSequence().getROI2Ds().isEmpty());
        }
    }
}
