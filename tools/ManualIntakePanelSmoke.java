/* Synthetic, headless panel integration; no experimental files are written. */
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import javax.swing.*;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;

public class ManualIntakePanelSmoke {
    static Object invoke(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(target);
    }
    static void check(boolean ok, String message) { CorrelationIntakeBatchSmoke.check(ok, message); }
    static void display(CorrelationIntakePanel panel, Capillary cap, CorrelationGulpPreview preview) throws Exception {
        Method m = panel.getClass().getDeclaredMethod("display", Capillary.class, CorrelationGulpPreview.class);
        m.setAccessible(true); m.invoke(panel, cap, preview);
    }
    
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        int[] saves = {0};
        Experiment exp = CorrelationIntakeBatchSmoke.fixture(saves);
        Capillary cap = exp.getCapillaries().getList().get(0);
        cap.getTopRaw().polylineLevel.ypoints[30] = Double.NaN;
        CorrelationGulpPreview preview = CorrelationGulpPreview.computeAccepted(exp, cap, false);
        SwingUtilities.invokeAndWait(() -> {
            CorrelationIntakePanel panel = new CorrelationIntakePanel(exp, cap);
            CorrelationIntakePanel reopened = null;
            try {
                JSpinner amplitude = new JSpinner(new SpinnerNumberModel(2.5, 0., Double.MAX_VALUE, .1));
                JSpinner position = new JSpinner(new SpinnerNumberModel(40., -Double.MAX_VALUE, Double.MAX_VALUE, .1));
                Method fieldsMethod = CorrelationIntakePanel.class.getDeclaredMethod("manualGulpFields",
                        int.class, int.class, JSpinner.class, JSpinner.class);
                fieldsMethod.setAccessible(true);
                JPanel fields = (JPanel) fieldsMethod.invoke(null, 29, 30, amplitude, position);
                JOptionPane popup = new JOptionPane(fields, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
                java.awt.Dimension popupSize = popup.getPreferredSize();
                check(popupSize.width < 800 && popupSize.height < 400,
                        "Manual gulp popup spans screens: " + popupSize);
                ((JSpinner.NumberEditor) amplitude.getEditor()).getTextField().setValue(3.75);
                amplitude.commitEdit();
                check(((Number) amplitude.getValue()).doubleValue() == 3.75, "Compact field lost decimal editing");
                display(panel, cap, preview);
                ManualIntakeEdits edits = (ManualIntakeEdits) invoke(panel, "manualEdits");
                edits.replace(29, 30, 4.5, 40);
                invoke(panel, "refresh");
                check(((boolean[]) invoke(panel, "candidates"))[30], "Rejected bin cannot be manually selected");
                check(((double[]) invoke(panel, "candidateBottoms"))[30] == 40, "Manual bottom lost");
                ((JCheckBox) CorrelationIntakeBatchSmoke.get(panel, "persistentEpisodes")).doClick();
                check(((double[]) invoke(panel, "candidateAmplitudes"))[30] == 4.5, "Single-bin mode lost manual intake");
                edits.replace(20, 20, 0, 30);
                java.nio.file.Path comparison = java.nio.file.Paths.get("multiCAFE/target/manual-intake-comparison.csv");
                Method exportCsv = panel.getClass().getDeclaredMethod("writeCsv", java.nio.file.Path.class);
                exportCsv.setAccessible(true); exportCsv.invoke(panel, comparison);
                java.util.List<String> comparisonRows = java.nio.file.Files.readAllLines(comparison);
                String added = comparisonRows.stream().filter(rowText -> rowText.startsWith("30;")).findFirst().get();
                String removed = comparisonRows.stream().filter(rowText -> rowText.startsWith("20;")).findFirst().get();
                check(added.endsWith(";true;added"), "CSV did not label manual addition");
                check(removed.endsWith(";true;removed"), "CSV did not label removed automatic intake");
                String[] removedFields = removed.split(";");
                check(Double.parseDouble(removedFields[removedFields.length - 5]) > 0,
                        "CSV lost fully automatic intake before deletion");
                check(Double.parseDouble(removedFields[removedFields.length - 3]) == 0,
                        "CSV lost edited zero after deletion");
                invoke(panel, "store");
                check(saves[0] == 1 && cap.getCorrelationGulps().bottomAt(30, 0) == 40, "Save did not retain geometry");
                StringBuffer row = new StringBuffer(); cap.getCorrelationGulps().csvExportToRow(row, ";");
                CapillaryCorrelationGulps restored = new CapillaryCorrelationGulps();
                restored.csvImportFromRow(row.toString().split(";"), 0, true);
                check(restored.getMethodOptions().contains("manual_intakes="), "CSV lost manual metadata");
                check(restored.bottomAt(30, 0) == 40, "CSV lost manual bottom");
                cap.copyCorrelationGulpsToGulps();
                check(cap.getGulps().getHeightSeries().ypoints[30] == 4.5, "Copy lost manual amplitude");
                reopened = new CorrelationIntakePanel(exp, cap);
                display(reopened, cap, preview);
                check(((double[]) invoke(reopened, "candidateAmplitudes"))[30] == 4.5, "Reopen lost override");
                check((Boolean) invoke(reopened, "isStoredCurrent"), "Reopened manual geometry marked unsaved");
                @SuppressWarnings("unchecked")
                java.util.Map<Capillary, ManualIntakeEdits> allEdits = (java.util.Map<Capillary, ManualIntakeEdits>)
                        CorrelationIntakeBatchSmoke.get(panel, "manualGulps");
                Capillary other = exp.getCapillaries().getList().get(1);
                ManualIntakeEdits otherEdits = new ManualIntakeEdits();
                otherEdits.replace(25, 25, 2, 30); allEdits.put(other, otherEdits);
                Class<?> reviewClass = Class.forName(CorrelationIntakePanel.class.getName() + "$ReviewExport");
                java.lang.reflect.Constructor<?> constructor = reviewClass.getDeclaredConstructor(CorrelationIntakePanel.class);
                constructor.setAccessible(true); Object reviewExport = constructor.newInstance(panel);
                Method archiveWrite = reviewClass.getDeclaredMethod("write", java.nio.file.Path.class);
                archiveWrite.setAccessible(true);
                java.nio.file.Path archive = java.nio.file.Paths.get("multiCAFE/target/manual-intake-experiment-review.zip");
                archiveWrite.invoke(reviewExport, archive);
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
                    check(zip.size() == 7, "Experiment export omitted capillaries or images");
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                            zip.getInputStream(zip.getEntry("correlation-" + other.getLast2ofCapillaryName() + ".csv")),
                            java.nio.charset.StandardCharsets.UTF_8));
                    String otherRow = reader.lines().filter(line -> line.startsWith("25;")).findFirst().get();
                    check(otherRow.endsWith(";true;added"), "Experiment export lost another capillary's unsaved addition");
                }
                check(saves[0] == 1, "Review export saved experiment measures");
                check(CorrelationIntakeBatchSmoke.get(panel, "computedCapillary") == cap,
                        "Review export changed selected capillary");
                panel.setSize(1180, 950); CorrelationIntakeBatchSmoke.layout(panel);
                BufferedImage screenshot = new BufferedImage(1180, 950, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics = screenshot.createGraphics(); panel.paint(graphics); graphics.dispose();
                javax.imageio.ImageIO.write(screenshot, "png", new java.io.File("multiCAFE/target/manual-intake-panel.png"));
                ((JComboBox<?>) CorrelationIntakeBatchSmoke.get(panel, "runMode")).setSelectedIndex(1);
                check(!((JCheckBox) CorrelationIntakeBatchSmoke.get(panel, "addGulp")).isEnabled(), "Batch mode permits manual editing");
                ((JComboBox<?>) CorrelationIntakeBatchSmoke.get(panel, "runMode")).setSelectedIndex(0);
                ((JButton) CorrelationIntakeBatchSmoke.get(panel, "resetManualGulps")).doClick();
                check(!((boolean[]) invoke(panel, "candidates"))[30], "Reset kept rejected manual bin");
                JCheckBox addGulp = (JCheckBox) CorrelationIntakeBatchSmoke.get(panel, "addGulp");
                addGulp.setSelected(true);
                ((JComboBox<?>) CorrelationIntakeBatchSmoke.get(panel, "capillaries")).setSelectedItem(other);
                check(addGulp.isSelected() && !addGulp.isEnabled(), "Capillary loading cleared editing mode");
                display(panel, other, CorrelationGulpPreview.computeAccepted(exp, other, false));
                check(addGulp.isSelected() && addGulp.isEnabled(), "New capillary did not retain editing mode");
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { panel.close(); if (reopened != null) reopened.close(); }
        });
        System.out.println("Manual intake panel smoke passed: rejected bins, modes, CSV, copy, reopen, rendering and reset.");
        System.exit(0); // Icy starts background threads even in a headless fixture.
    }
}
