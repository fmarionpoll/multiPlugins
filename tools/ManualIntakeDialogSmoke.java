/* Exercise the real modal OK path using isolated synthetic experiment data. */
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import javax.swing.*;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;

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
        boolean blue = args.length > 0 && "blue".equalsIgnoreCase(args[0]);
        Color expectedColor = blue ? plugins.fmp.multitools.experiment.capillary.CapillaryIntakes.ROI_COLOR : Color.YELLOW;
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Experiment exp = CorrelationIntakeBatchSmoke.fixture(new int[1]);
        Capillary cap = exp.getCapillaries().getList().get(0);
        double[] levels = cap.getTopRaw().polylineLevel.ypoints;
        levels[29] = levels[28] + 1;
        levels[30] = levels[29] + 2;
        levels[31] = levels[30] + 3;
        levels[32] = levels[31] + 1.5;
        long[] times = new long[cap.getTopRaw().polylineLevel.npoints];
        for (int i = 0; i < times.length; i++) times[i] = i * 120000L;
        exp.setFrameTimeScale(plugins.fmp.multitools.experiment.timebase.FrameTimeScale.fromArrays(0, times, null, 0, times.length));
        CorrelationGulpPreview preview = CorrelationGulpPreview.computeAccepted(exp, cap, false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                CorrelationIntakePanel panel = new CorrelationIntakePanel(exp, cap);
                JComboBox<?> intakeColor = (JComboBox<?>) CorrelationIntakeBatchSmoke.get(panel, "intakeColor");
                if (blue) intakeColor.setSelectedIndex(1);
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
                                    JSpinner context = (JSpinner) named((Container) window, "manualIntakeContextMinutes");
                                    context.setValue(0.);
                                    CorrelationIntakeBatchSmoke.check((Integer) CorrelationIntakeBatchSmoke.get(image, "left") == 29
                                            && (Integer) CorrelationIntakeBatchSmoke.get(image, "right") == 31,
                                            "Zero context must retain the complete selected interval");
                                    context.setValue(6.);
                                    CorrelationIntakeBatchSmoke.check((Integer) CorrelationIntakeBatchSmoke.get(image, "left") == 26
                                            && (Integer) CorrelationIntakeBatchSmoke.get(image, "right") == 34,
                                            "Context used frame counts instead of actual minutes");
                                    context.setValue(22.);
                                    double[] neighbours = (double[]) CorrelationIntakeBatchSmoke.get(image, "otherAmplitudes");
                                    CorrelationIntakeBatchSmoke.check(neighbours[20] > 0 && neighbours[27] == 2,
                                            "Zoom omitted automatic or manually added neighbouring intake");
                                    image.setSize(image.getPreferredSize());
                                    java.awt.image.BufferedImage painted = new java.awt.image.BufferedImage(image.getWidth(), image.getHeight(),
                                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                                    Graphics2D paintedGraphics = painted.createGraphics(); image.paint(paintedGraphics); paintedGraphics.dispose();
                                    Method columnX = image.getClass().getDeclaredMethod("columnX", int.class); columnX.setAccessible(true);
                                    Method neighbourY = image.getClass().getDeclaredMethod("y", double.class); neighbourY.setAccessible(true);
                                    int neighbourX = (Integer) columnX.invoke(image, 27), neighbourRow = (Integer) neighbourY.invoke(image, 39.);
                                    CorrelationIntakeBatchSmoke.check(painted.getRGB(neighbourX, neighbourRow) == expectedColor.getRGB(),
                                            "Neighbouring intake line did not use the selected color");
                                    int handleX = (Integer) columnX.invoke(image, 30);
                                    int handleY = (Integer) neighbourY.invoke(image, ((Number) bottom.getValue()).doubleValue());
                                    CorrelationIntakeBatchSmoke.check(painted.getRGB(handleX, handleY) == expectedColor.getRGB(),
                                            "Active intake handle did not use the selected color");
                                    amount.setValue(2.); bottom.setValue(40.);
                                    Method xMethod = image.getClass().getDeclaredMethod("lineX"); xMethod.setAccessible(true);
                                    Method topXMethod = image.getClass().getDeclaredMethod("topX"); topXMethod.setAccessible(true);
                                    Method yMethod = image.getClass().getDeclaredMethod("y", double.class); yMethod.setAccessible(true);
                                    int x = (Integer) xMethod.invoke(image);
                                    int top = (Integer) yMethod.invoke(image, 40 - 2 - (preview.evaporation[30] - preview.evaporation[29]));
                                    drag(image, (Integer) topXMethod.invoke(image), top, top - 12);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(((Number) amount.getValue()).doubleValue() - 3) < .05,
                                            "Dragging upper endpoint did not update intake");
                                    int lower = (Integer) yMethod.invoke(image, 40.);
                                    drag(image, x, lower, lower + 24);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(((Number) amount.getValue()).doubleValue() - 5) < .05
                                            && ((Number) bottom.getValue()).doubleValue() == 42,
                                            "Dragging lower endpoint did not preserve upper position");
                                    JSpinner from = (JSpinner) named((Container) window, "manualIntakeFirstBin");
                                    JSpinner to = (JSpinner) named((Container) window, "manualIntakeLastBin");
                                    double originalAmount = ((Number) amount.getValue()).doubleValue();
                                    from.setValue(29); to.setValue(32);
                                    CorrelationIntakeBatchSmoke.check(((Number) amount.getValue()).doubleValue() == originalAmount
                                            && ((Number) bottom.getValue()).doubleValue() == 42,
                                            "Expanding the range changed the original bin's intake or position");
                                    from.setValue(35);
                                    CorrelationIntakeBatchSmoke.check(((Number) to.getValue()).intValue() == 35,
                                            "First bin beyond last did not maintain a valid interval");
                                    to.setValue(30);
                                    CorrelationIntakeBatchSmoke.check(((Number) from.getValue()).intValue() == 30,
                                            "Last bin before first did not maintain a valid interval");
                                    from.setValue(29); to.setValue(32);
                                    CorrelationIntakeBatchSmoke.check((Integer) CorrelationIntakeBatchSmoke.get(image, "first") == 29
                                            && (Integer) CorrelationIntakeBatchSmoke.get(image, "last") == 32,
                                            "Selected bins did not update the preview interval");
                                    JLabel selectedTime = (JLabel) named((Container) window, "manualIntakeSelectedTime");
                                    CorrelationIntakeBatchSmoke.check(selectedTime.getText().contains("56.00 - 64.00"),
                                            "Selected time did not use the edited interval timestamps");
                                    double[] perBin = (double[]) CorrelationIntakeBatchSmoke.get(image, "measuredAmplitudes");
                                    CorrelationIntakeBatchSmoke.check(perBin[29] == 1 && perBin[30] == 2
                                            && perBin[31] == 3 && perBin[32] == 1.5,
                                            "Range selection did not independently measure all four bins");
                                    JSpinner editBin = (JSpinner) named((Container) window, "manualIntakeEditBin");
                                    editBin.setValue(31);
                                    amount.setValue(4.);
                                    CorrelationIntakeBatchSmoke.check(perBin[29] == 1 && perBin[30] == 2 && perBin[32] == 1.5,
                                            "Editing one bin changed another selected bin");
                                    double evap31 = preview.evaporation[31] - preview.evaporation[30];
                                    double keptBottom = ((Number) bottom.getValue()).doubleValue();
                                    editBin.setValue(30);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(perBin[31] - (4 + evap31)) < 1e-6,
                                            "Switching bins discarded a committed intake");
                                    editBin.setValue(31);
                                    JSpinner.NumberEditor amountEditor = (JSpinner.NumberEditor) amount.getEditor();
                                    amountEditor.getTextField().setText(amountEditor.getFormat().format(6.5));
                                    JSpinner.NumberEditor bottomEditor = (JSpinner.NumberEditor) bottom.getEditor();
                                    bottomEditor.getTextField().setText(bottomEditor.getFormat().format(40));
                                    editBin.setValue(32);
                                    double[] editedBottoms = (double[]) CorrelationIntakeBatchSmoke.get(image, "measuredBottoms");
                                    CorrelationIntakeBatchSmoke.check(Math.abs(perBin[31] - (6.5 + evap31)) < 1e-6
                                            && Math.abs(editedBottoms[31] - 40) < 1e-6,
                                            "Switching bins discarded the interval being edited");
                                    CorrelationIntakeBatchSmoke.check(perBin[32] == 1.5,
                                            "Switching bins changed the destination interval");
                                    editBin.setValue(31);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(((Number) amount.getValue()).doubleValue() - 6.5) < 1e-6
                                            && Math.abs(((Number) bottom.getValue()).doubleValue() - 40) < 1e-6,
                                            "Returning to a bin did not restore its intake");
                                    JButton saveBin = (JButton) named((Container) window, "manualIntakeSaveBin");
                                    JButton deleteBin = (JButton) named((Container) window, "manualIntakeDeleteBin");
                                    amountEditor.getTextField().setText(amountEditor.getFormat().format(7));
                                    saveBin.doClick();
                                    editBin.setValue(30);
                                    CorrelationIntakeBatchSmoke.check(Math.abs(perBin[31] - (7 + evap31)) < 1e-6 && perBin[30] == 2,
                                            "Save did not keep only the bin being edited");
                                    editBin.setValue(31);
                                    deleteBin.doClick();
                                    CorrelationIntakeBatchSmoke.check(perBin[31] == 0 && ((Number) amount.getValue()).doubleValue() == 0
                                            && perBin[30] == 2 && perBin[32] == 1.5,
                                            "Delete did not clear only the current bin");
                                    amount.setValue(4.);
                                    bottom.setValue(keptBottom);
                                    CorrelationIntakeBatchSmoke.check(topXMethod.invoke(image).equals(xMethod.invoke(image)),
                                            "Intake preview is not a vertical bar");
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
                    ((ManualIntakeEdits) ManualIntakePanelSmoke.invoke(panel, "manualEdits")).replace(27, 27, 2, 40);
                    ((ManualIntakeEdits) ManualIntakePanelSmoke.invoke(panel, "manualEdits")).replace(30, 30, 1.5, 40);
                    ((JCheckBox) CorrelationIntakeBatchSmoke.get(panel, "addGulp")).setSelected(true);
                    Method edit = panel.getClass().getDeclaredMethod("editGulp", int.class, int.class);
                    edit.setAccessible(true);
                    accept.start(); edit.invoke(panel, 30, 30);
                    CorrelationIntakeBatchSmoke.check((Double) CorrelationIntakeBatchSmoke.get(panel, "manualGulpContextMinutes") == 22,
                            "Dialog did not remember the selected context for the next event");
                    Path file = Paths.get("multiCAFE/target/manual-intake-dialog.csv");
                    Method export = panel.getClass().getDeclaredMethod("writeCsv", Path.class);
                    export.setAccessible(true); export.invoke(panel, file);
                    String row = Files.readAllLines(file).stream().filter(line -> line.startsWith("31;")).findFirst().get();
                    String[] columns = row.split(";");
                    CorrelationIntakeBatchSmoke.check(columns[columns.length - 2].equals("true")
                            && Math.abs(Double.parseDouble(columns[columns.length - 3]) - 4) < 1e-9,
                            "Export did not retain the individually edited intake bin");
                    double[] result = (double[]) ManualIntakePanelSmoke.invoke(panel, "candidateAmplitudes");
                    CorrelationIntakeBatchSmoke.check(result[29] == 1 && result[30] == 2 && result[32] == 1.5 && result[27] == 2,
                            "Saving a range lost per-bin measurements, double counted or altered a neighbour");
                } catch (Exception error) { throw new RuntimeException(error); }
                finally { accept.stop(); panel.close(); }
            });
            System.out.println("Modal intake dialog OK-to-CSV test passed.");
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
}
