/* Headless layout and task-scope checks; uses the existing synthetic experiment fixture. */
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;

public class IntakeTabsSmoke {
    static Object get(Object object, String name) { return CorrelationIntakeBatchSmoke.get(object, name); }
    static void check(boolean ok, String message) { CorrelationIntakeBatchSmoke.check(ok, message); }
    static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && ((JButton) child).getText().equals(text)) return (JButton) child;
            if (child instanceof Container) {
                JButton found = button((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    static void checkButtons(Container root, CorrelationIntakePanel panel) {
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JButton || child instanceof JCheckBox || child instanceof JComboBox) {
                java.awt.Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), panel);
                check(bounds.x >= 0 && bounds.x + bounds.width <= panel.getWidth(), "Clipped control: " + child);
            }
            if (child instanceof Container) checkButtons((Container) child, panel);
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Experiment experiment = CorrelationIntakeBatchSmoke.fixture(new int[1]);
        CorrelationIntakePanel[] holder = { null };
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CorrelationIntakePanel(experiment, experiment.getCapillaries().getList().get(0));
            CorrelationIntakeBrowserSmoke.call(holder[0], "resumeSession");
        });
        CorrelationIntakeBrowserSmoke.preview(holder[0]);
        SwingUtilities.invokeAndWait(() -> {
            CorrelationIntakePanel panel = holder[0];
            try {
                JTabbedPane tabs = (JTabbedPane) get(panel, "tasks");
                check(tabs.getTabCount() == 3, "Expected three tasks");
                check(tabs.getTitleAt(0).equals("Review & edit") && tabs.getTitleAt(1).equals("Experiment")
                        && tabs.getTitleAt(2).equals("Series"), "Task names changed");
                JComboBox<?> scope = (JComboBox<?>) get(panel, "saveScope");
                check(((JButton) get(panel, "save")).getText().equals("Save capillary intake"), "Single-capillary save is ambiguous");
                scope.setSelectedIndex(1);
                check(((JButton) get(panel, "save")).getText().equals("Save experiment intake..."), "Experiment save scope is ambiguous");
                check(((JButton) get(panel, "copyToGulps")).getText().equals("Replace experiment gulps..."), "Experiment replacement scope is ambiguous");
                JCheckBox edit = (JCheckBox) get(panel, "addGulp");
                edit.setSelected(true);
                for (int width : new int[] {1180, 920}) {
                    for (int tab = 0; tab < 3; tab++) {
                        tabs.setSelectedIndex(tab);
                        check(edit.isEnabled() == (tab == 0), "Intake editing is available outside Review & edit");
                        check(((JSpinner) get(panel, "threshold")).isEnabled() == (tab == 0), "Threshold dragging is available in another task");
                        panel.setSize(width, width == 920 ? 700 : 880);
                        CorrelationIntakeBatchSmoke.layout(panel);
                        checkButtons(tabs.getParent(), panel);
                        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D graphics = image.createGraphics();
                        panel.printAll(graphics); graphics.dispose();
                        ImageIO.write(image, "png", new File("multiCAFE/target/intake-tab-" + tab + "-" + width + ".png"));
                    }
                }
                tabs.setSelectedIndex(0);
                check(edit.isSelected(), "Switching tasks lost the editing preference");
                JButton settings = button(panel, "Detection & display settings");
                settings.doClick();
                CorrelationIntakeBatchSmoke.layout(panel);
                checkButtons(tabs.getParent(), panel);
                ((JComboBox<?>) get(panel, "experimentMode")).setSelectedIndex(1);
                tabs.setSelectedIndex(1);
                CorrelationIntakeBatchSmoke.layout(panel);
                checkButtons(tabs.getParent(), panel);
            } catch (Exception error) { throw new RuntimeException(error); }
            finally { panel.close(); }
        });
        System.out.println("PASS: three tasks; editing limited to review; retained editing mode; controls fit at 1180 and 920 pixels; expanded settings and edited-experiment controls.");
        System.exit(0);
    }
}
