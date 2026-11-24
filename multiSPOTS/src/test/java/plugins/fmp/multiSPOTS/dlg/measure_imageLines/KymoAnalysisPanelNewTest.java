package plugins.fmp.multiSPOTS.dlg.measure_imageLines;

import static org.junit.Assert.*;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class KymoAnalysisPanelNewTest {
    @Test public void stableEntryPointKeepsTheOldWorkflowAlongsideTheTrial() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KymoAnalysisPanel panel = new KymoAnalysisPanel(null);
            assertTrue(panel instanceof KymoAnalysisPanel_old);
            assertTrue(panel instanceof KymoAnalysisPanel_new);
            List<String> buttons = new ArrayList<>();
            collectButtons(panel, buttons);
            assertTrue(buttons.contains("Analyze"));
            assertTrue(buttons.contains("Analyze new (current)"));
            assertTrue(buttons.contains("Old/new comparison"));
            assertEquals(ImageTransformEnums.RGB_DIFFS, panel.readParams().metricTransform);
            assertTrue(panel.readParams().insectMetricGateEnabled);
            assertTrue(panel.readParams().rowwiseOcclusionFill);
        });
    }

    @Test public void comparisonDoesNotDrawAcrossMissingDataOrClampNegativeLoss() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        KymoAnalysisPanel_new.addTrace(dataset, "new", new double[] {0, 1, 2},
                new double[] {1.2, Double.NaN, .5}, true);
        assertEquals(-.2, dataset.getYValue(0, 0), 1e-12);
        assertNull(dataset.getY(0, 1));
        assertEquals(.5, dataset.getYValue(0, 2), 0);
    }

    @Test public void subtotalBreaksOnlyAtChangesOfContributingSpots() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        KymoAnalysisPanel_new.addObservedTrace(dataset, new double[] {0, 1, 2, 3},
                new double[] {.4, .2, .3, Double.NaN}, new boolean[] {false, true, false, true});
        assertEquals(6, dataset.getItemCount(0));
        assertEquals(.4, dataset.getYValue(0, 0), 0);
        assertNull(dataset.getY(0, 1));
        assertEquals(.2, dataset.getYValue(0, 2), 0);
        assertEquals(.3, dataset.getYValue(0, 3), 0);
        assertNull(dataset.getY(0, 4));
        assertNull(dataset.getY(0, 5));
    }

    private static void collectButtons(Container container, List<String> buttons) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton) buttons.add(((JButton) child).getText());
            if (child instanceof Container) collectButtons((Container) child, buttons);
        }
    }
}
