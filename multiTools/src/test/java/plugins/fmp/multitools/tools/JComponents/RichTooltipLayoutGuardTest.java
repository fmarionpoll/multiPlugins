package plugins.fmp.multitools.tools.JComponents;

import static org.junit.Assert.*;
import java.awt.LayoutManager;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.Test;
import org.pushingpixels.flamingo.api.common.RichTooltip;
import org.pushingpixels.flamingo.internal.ui.common.BasicRichTooltipPanelUI;
import org.pushingpixels.flamingo.internal.ui.common.JRichTooltipPanel;

public class RichTooltipLayoutGuardTest {
    @Test public void narrowPopupValidationDefersWrappingAndNormalLayoutStillWorks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object previous = UIManager.get("RichTooltipPanelUI");
            try {
                UIManager.put("RichTooltipPanelUI", BasicRichTooltipPanelUI.class.getName());
                JRichTooltipPanel panel = new JRichTooltipPanel(new RichTooltip("12345678901234567890123", "Gap review"));
                RichTooltipLayoutGuard.protect(panel);
                for (int width : new int[] {0, 1, 2, 8, 16, 32}) {
                    panel.setSize(width, 300); panel.doLayout();
                }
                panel.setSize(panel.getPreferredSize()); panel.doLayout();
                assertTrue(panel.getComponentCount() > 0);
                assertTrue(panel.getComponent(0).getWidth() > 0);
            } finally {
                if (previous == null) UIManager.getDefaults().remove("RichTooltipPanelUI");
                else UIManager.put("RichTooltipPanelUI", previous);
            }
        });
    }

    @Test public void listenerIsScopedAndDoesNotReplaceOtherComponentLayouts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object previous = UIManager.get("RichTooltipPanelUI");
            try {
                UIManager.put("RichTooltipPanelUI", BasicRichTooltipPanelUI.class.getName());
                JPanel parent = new JPanel(); LayoutManager parentLayout = parent.getLayout();
                JRichTooltipPanel tip = new JRichTooltipPanel(new RichTooltip("Tooltip", "Description"));
                LayoutManager original = tip.getLayout();
                RichTooltipLayoutGuard guard = new RichTooltipLayoutGuard();
                parent.add(tip);
                assertNotSame(original, tip.getLayout());
                assertSame(parentLayout, parent.getLayout());
                guard.close(); guard.close();
                JRichTooltipPanel later = new JRichTooltipPanel(new RichTooltip("Tooltip", "Description"));
                LayoutManager laterLayout = later.getLayout(); parent.add(later);
                assertSame(laterLayout, later.getLayout());
            } finally {
                if (previous == null) UIManager.getDefaults().remove("RichTooltipPanelUI");
                else UIManager.put("RichTooltipPanelUI", previous);
            }
        });
    }
}
