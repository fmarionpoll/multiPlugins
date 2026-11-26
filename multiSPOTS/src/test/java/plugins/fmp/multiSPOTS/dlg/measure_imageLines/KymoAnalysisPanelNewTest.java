package plugins.fmp.multiSPOTS.dlg.measure_imageLines;

import static org.junit.Assert.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class KymoAnalysisPanelNewTest {
    @Test public void measurementDialogContainsOnlyAcquisitionControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KymoAnalysisPanel panel = new KymoAnalysisPanel(null);
            List<String> buttons = new ArrayList<>();
            collectButtons(panel, buttons);
            assertTrue(buttons.contains("Measure raw"));
            assertTrue(buttons.contains("ALL series (current to last)"));
            assertFalse(buttons.contains("Analyze new"));
            assertFalse(buttons.contains("Old/new comparison"));
            assertFalse(buttons.contains("Lift occlusions (row-wise)"));
            assertEquals(ImageTransformEnums.RGB_DIFFS, panel.readParams().metricTransform);
            assertTrue(panel.readParams().insectMetricGateEnabled);
            assertFalse(panel.readParams().rowwiseOcclusionFill);
            assertFalse(panel.readParams().previewLiftedBands);
        });
    }
    private static void collectButtons(Container container, List<String> buttons) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton) buttons.add(((AbstractButton) child).getText());
            if (child instanceof Container) collectButtons((Container) child, buttons);
        }
    }
}
