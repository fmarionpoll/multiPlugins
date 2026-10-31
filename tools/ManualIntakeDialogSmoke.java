/* Exercise the real modal OK path using isolated synthetic experiment data. */
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import javax.swing.*;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;

public class ManualIntakeDialogSmoke {
    static Component named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container) {
                Component found = named((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
    static void drag(Component image, int x, int from, int to) {
        long now = System.currentTimeMillis();
        image.dispatchEvent(new java.awt.event.MouseEvent(image, java.awt.event.MouseEvent.MOUSE_PRESSED,
                now, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, from, 1, false, java.awt.event.MouseEvent.BUTTON1));
        image.dispatchEvent(new java.awt.event.MouseEvent(image, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                now, java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, to, 0, false, java.awt.event.MouseEvent.NOBUTTON));
        image.dispatchEvent(new java.awt.event.MouseEvent(image, java.awt.event.MouseEvent.MOUSE_RELEASED,
                now, 0, x, to, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }
    static JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) {
                JButton found = button((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Experiment exp = CorrelationIntakeBatchSmoke.fixture(new int[1]);
        Capillary cap = exp.getCapillaries().getList().get(0);
        CorrelationGulpPreview preview = CorrelationGulpPreview.computeAccepted(exp, cap, false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                CorrelationIntakePanel panel = new CorrelationIntakePanel(exp, cap);
                Timer accept = new Timer(300, event -> {
                    for (Window window : Window.getWindows())
                        if (window instanceof JDialog && window.isShowing()
                                && ((JDialog) window).getTitle().startsWith("Manual gulp")) {
                            JButton ok = button((Container) window, "OK");
                            if (ok != null) {
                                ((Timer) event.getSource()).stop();
                                try {
                                    CorrelationIntakeBatchSmoke.check(window.getWidth() < 850 && window.getHeight() < 650,
                                            "Zoom enlarged the dialog beyond a compact window");
                                    JPanel image = (JPanel) named((Container) window, "manualIntakeZoom");
                                    JSpinner amount = (JSpinner) CorrelationIntakeBatchSmoke.get(image, "amplitude");
                                    JSpinner bottom = (JSpinner) CorrelationIntakeBatchSmoke.get(image, "position");
                                    amount.setValue(2.); bottom.setValue(40.);
                                    Method xMethod = image.getClass().getDeclaredMethod("lineX"); xMethod.setAccessible(true);
                                    Method yMethod = image.getClass().getDeclaredMethod("y", double.class); yMethod.setAccessible(true);
                                    int x = (Integer) xMethod.invoke(image);
                                    int top = (Integer) yMethod.invoke(image, 40 - 2 - (preview.evaporation[30] - preview.evaporation[29]));
                                    drag(image, x, top, top - 12);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(((Number) amount.getValue()).doubleValue() - 3) < .05,
                                            "Dragging upper blue endpoint did not update intake");
                                    int lower = (Integer) yMethod.invoke(image, 40.);
                                    drag(image, x, lower, lower + 24);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(((Number) amount.getValue()).doubleValue() - 5) < .05
                                            && ((Number) bottom.getValue()).doubleValue() == 42,
                                            "Dragging lower blue endpoint did not preserve upper position");
                                    ((JComboBox<?>) named((Container) window, "manualIntakeZoomScale")).setSelectedItem(24);
                                    CorrelationIntakeBatchSmoke.check((Integer) CorrelationIntakeBatchSmoke.get(image, "scale") == 24,
                                            "Zoom selection did not change image scale");
                                    window.validate();
                                    Method reveal = image.getClass().getDeclaredMethod("revealLine"); reveal.setAccessible(true); reveal.invoke(image);
                                    java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(window.getWidth(), window.getHeight(),
                                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                                    Graphics2D graphics = shot.createGraphics(); window.paint(graphics); graphics.dispose();
                                    javax.imageio.ImageIO.write(shot, "png", new java.io.File("multiCAFE/target/manual-intake-zoom-dialog.png"));
                                    ok.doClick();
                                } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
                            }
                        }
                });
                try {
                    ManualIntakePanelSmoke.display(panel, cap, preview);
                    ((JCheckBox) CorrelationIntakeBatchSmoke.get(panel, "addGulp")).setSelected(true);
                    Method edit = panel.getClass().getDeclaredMethod("editGulp", int.class, int.class);
                    edit.setAccessible(true);
                    accept.start(); edit.invoke(panel, 30, 30);
                    Path file = Paths.get("multiCAFE/target/manual-intake-dialog.csv");
                    Method export = panel.getClass().getDeclaredMethod("writeCsv", Path.class);
                    export.setAccessible(true); export.invoke(panel, file);
                    String row = Files.readAllLines(file).stream().filter(line -> line.startsWith("30;")).findFirst().get();
                    CorrelationIntakeBatchSmoke.check(row.endsWith(";true;added"), "Dialog OK lost manual addition: " + row);
                    String[] columns = row.split(";");
                    CorrelationIntakeBatchSmoke.check(Math.abs(Double.parseDouble(columns[columns.length - 3]) - 5) < .05,
                            "Export did not retain dragged intake size");
                } catch (Exception error) { throw new RuntimeException(error); }
                finally { accept.stop(); panel.close(); }
            });
            System.out.println("Modal intake dialog OK-to-CSV test passed.");
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
