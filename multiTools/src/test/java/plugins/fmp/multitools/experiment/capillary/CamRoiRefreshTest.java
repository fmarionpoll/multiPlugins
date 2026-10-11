package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CamRoiRefreshTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test public void refreshUsesIndependentPhasesAndPreservesOtherRois() {
        SequenceCamData data = new SequenceCamData();
        data.setSequence(new Sequence());
        Experiment experiment = new Experiment(data);
        ROI2DLine green = line(10, "line0L");
        ROI2DLine laterGreen = line(20, "line0L");
        Capillary cap = new Capillary(green);
        cap.setAlongTList(Arrays.asList(new AlongT(0, green), new AlongT(40, laterGreen)));
        cap.getPhaseGeometry().initialize(0, green.getLine(), line(12, "").getLine());
        cap.getPhaseGeometry().putBlue(25, line(15, "").getLine());
        experiment.getCapillaries().getList().add(cap);
        ROI2DLine unrelated = line(90, "cage0");
        data.getSequence().addROI(unrelated);

        experiment.updateROIsAt(0);
        assertTrue(data.findROIsMatchingNamePattern("caplength_").isEmpty());
        CapillaryMeasuredTipsOverlay.transferTipsToSequence(experiment.getCapillaries(), data, 0);
        experiment.updateROIsAt(30);
        assertEquals(10, displayedX(data, "line"), 0);
        assertEquals(15, displayedX(data, "caplength_"), 0);
        experiment.updateROIsAt(45);
        assertEquals(20, displayedX(data, "line"), 0);
        assertEquals(15, displayedX(data, "caplength_"), 0);
        experiment.updateROIsAt(0);
        assertEquals(12, displayedX(data, "caplength_"), 0);
        assertTrue(data.getSequence().getROI2Ds().contains(unrelated));
        assertEquals(3, data.getSequence().getROI2Ds().size());
        ROI2DLine displayed = (ROI2DLine) data.findROIsMatchingNamePattern("caplength_").get(0);
        displayed.setLine(line(99, "").getLine());
        assertEquals(12, cap.getPhaseGeometry().getBlueAt(0).getX1(), 0);
    }

    private static double displayedX(SequenceCamData data, String pattern) {
        assertEquals(1, data.findROIsMatchingNamePattern(pattern).size());
        return ((ROI2DLine) data.findROIsMatchingNamePattern(pattern).get(0)).getLine().getX1();
    }

    private static ROI2DLine line(double x, String name) {
        ROI2DLine roi = new ROI2DLine(new Line2D.Double(x, 0, x, 100));
        roi.setName(name);
        return roi;
    }
}
