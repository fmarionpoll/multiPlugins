/* Headless synthetic integration checks. Compile/run with the multiCAFE dependency
 * classpath and multiCAFE/target/classes plus multiTools/target/classes first. */
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class CorrelationIntakeBatchSmoke {
    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static Object get(Object o, String name) {
        try { return field(o, name); } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(o);
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void waitBatch(CorrelationIntakePanel p) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            boolean[] done = { false };
            SwingUtilities.invokeAndWait(() -> done[0] = get(p, "batchWorker") == null);
            if (done[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Batch timed out");
    }
    static void layout(Container c) { c.doLayout(); for (java.awt.Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }
    static Experiment fixture(int[] saves) {
        Experiment exp = new Experiment() {
            @Override public boolean save_capillaries_description_and_measures() { saves[0]++; return true; }
        };
        long[] times = new long[50];
        for (int i = 0; i < times.length; i++) times[i] = i * 60000L;
        exp.setFrameTimeScale(FrameTimeScale.fromArrays(0, times, null, 0, times.length));
        Sequence seq = new Sequence();
        for (int c = 0; c < 3; c++) {
            plugins.kernel.roi.roi2d.ROI2DLine roi = new plugins.kernel.roi.roi2d.ROI2DLine(0, 0, 0, 100);
            roi.setName("line" + c + "L");
            Capillary cap = new Capillary(roi);
            cap.setKymographIndex(c); cap.setNFlies(c == 0 ? 1 : 0);
            cap.setVolume(5); cap.setPixels(100);
            double[] x = new double[50], y = new double[50];
            for (int i = 0; i < y.length; i++) { x[i] = i; y[i] = 20 + .1 * i + .02 * Math.sin(i + c) + (c == 0 && i >= 20 ? 5 : 0); }
            cap.getTopRaw().polylineLevel = new Level2D(x, y, y.length);
            exp.getCapillaries().getList().add(cap);
            seq.setImage(c, 0, IcyBufferedImage.createFrom(new BufferedImage(50, 100, BufferedImage.TYPE_INT_RGB)));
        }
        SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(seq); exp.setSeqKymos(kymos);
        return exp;
    }
    static void checkSeries() throws Exception {
        java.nio.file.Path output = java.nio.file.Files.createTempDirectory(Paths.get("multiCAFE/target"), "intake-series-");
        for (int scope = 0; scope < 7; scope++) {
            Experiment[] experiments = new Experiment[3];
            for (int i = 0; i < 3; i++) {
                experiments[i] = fixture(new int[1]);
                java.nio.file.Path dir = output.resolve("scope" + scope + "-exp" + i);
                java.nio.file.Files.createDirectories(dir);
                experiments[i].setResultsDirectory(dir.toAbsolutePath().toString());
            }
            CorrelationIntakePanel[] holder = { null };
            JComboBox<Experiment> browser = new JComboBox<>(experiments);
            browser.setSelectedIndex(1);
            int range = scope;
            int failingIndex=scope==3?1:scope==4?2:scope==5?0:-1;
            if (failingIndex>=0) experiments[failingIndex].getCapillaries().getList().get(1).getTopRaw().polylineLevel.npoints = 4;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    holder[0] = new CorrelationIntakePanel(experiments[1], experiments[1].getCapillaries().getList().get(0));
                    Method follow = holder[0].getClass().getDeclaredMethod("followExperimentBrowser", JComboBox.class);
                    follow.setAccessible(true); follow.invoke(holder[0], browser);
                    ((JComboBox<?>)get(holder[0], "runMode")).setSelectedIndex(2);
                    int chosenRange=range==4?1:Math.min(range,2);
                    ((JComboBox<?>)get(holder[0], "seriesRange")).setSelectedIndex(chosenRange);
                    ((JButton)get(holder[0], "runSeries")).doClick();
                    int first=chosenRange==2?0:1,last=chosenRange==0?1:2;
                    check(((JLabel)get(holder[0], "seriesStatus")).getText().contains("indices "+first+"-"+last+" / 3"),
                            "Initial progress does not use absolute browser indices");
                    check(!browser.isEnabled(), "Browser not locked during series");
                    check(!((JComboBox<?>)get(holder[0], "runMode")).isEnabled(), "Mode editable during series");
                    if(range==6) ((JButton)get(holder[0],"stopSeries")).doClick();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            CorrelationIntakePanel p = holder[0];
            try {
                waitSeries(p);
                SwingUtilities.invokeAndWait(() -> {
                    int selected=range==4?2:range>=5?0:1;
                    check(browser.isEnabled() && browser.getSelectedIndex() == selected, "Wrong experiment selected after batch");
                    int expected = range==0 || range==3 || range==4 ? 1 : range==1 ? 2 : range==2 ? 3 : 0;
                    check((Integer)get(p, "seriesSaved") == expected, "Wrong saved count: " + ((JLabel)get(p, "seriesStatus")).getText());
                    String status=((JLabel)get(p,"seriesStatus")).getText();
                    if(failingIndex>=0) check(status.contains("Failed at Experiment "+failingIndex+"/3:"),"Failure index is relative: "+status);
                    if(range==6) check(status.contains("Stopped at Experiment 0/3:"),"Stop lost absolute experiment: "+status);
                    for (int i = 0; i < 3; i++) {
                        boolean processed = range==0 ? i==1 : range==1 ? i>=1 : range==2 ? true : range==3 ? i==0 : range==4 ? i==1 : false;
                        check(java.nio.file.Files.exists(Paths.get(experiments[i].getResultsDirectory(), "CapillariesMeasures.csv")) == processed, "Wrong experiment range saved");
                        for (Capillary cap : experiments[i].getCapillaries().getList()) {
                            check(cap.getCorrelationGulps().hasMeasures() == processed, "Wrong range detected");
                            if (processed) check(Arrays.equals(cap.getGulps().getHeightSeries().ypoints,
                                    cap.getCorrelationGulps().getEvents().getHeightSeries().ypoints), "Series failed to copy gulps");
                        }
                        check(experiments[i].getSeqKymos().getSequence() != null, "Closed preexisting images");
                    }
                });
                if (range == 2) {
                    long previewDeadline = System.currentTimeMillis() + 30000;
                    while (System.currentTimeMillis() < previewDeadline) {
                        boolean[] ready = { false };
                        SwingUtilities.invokeAndWait(() -> ready[0] = get(p, "preview") != null);
                        if (ready[0]) break;
                        Thread.sleep(20);
                    }
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            p.setSize(1180, 880); layout(p);
                            BufferedImage image = new BufferedImage(1180, 880, BufferedImage.TYPE_INT_RGB);
                            java.awt.Graphics2D g = image.createGraphics(); p.printAll(g); g.dispose();
                            ImageIO.write(image, "png", Paths.get("multiCAFE/target/intake-series-preview.png").toFile());
                        } catch (Exception e) { throw new RuntimeException(e); }
                    });
                }
            } finally { SwingUtilities.invokeAndWait(p::close); }
        }
        Experiment failed = fixture(new int[1]);
        java.nio.file.Path failedDir = output.resolve("failed"); java.nio.file.Files.createDirectories(failedDir);
        failed.setResultsDirectory(failedDir.toAbsolutePath().toString());
        failed.getCapillaries().getList().get(1).getTopRaw().polylineLevel.npoints = 4;
        plugins.fmp.multicafe.dlg.levels.IntakeSeriesProcessor processor = new plugins.fmp.multicafe.dlg.levels.IntakeSeriesProcessor();
        Experiment diskSource = fixture(new int[1]);
        java.nio.file.Path diskDir = output.resolve("cold-load").toAbsolutePath();
        java.nio.file.Path binDir = diskDir.resolve("bin_60"); java.nio.file.Files.createDirectories(binDir);
        diskSource.setResultsDirectory(diskDir.toString()); diskSource.setBinDirectory(binDir.toString());
        for (int i = 0; i < 3; i++) {
            Capillary cap = diskSource.getCapillaries().getList().get(i);
            cap.setKymographName("line" + i + "1"); cap.setCageID(i);
            ImageIO.write(new BufferedImage(50, 100, BufferedImage.TYPE_INT_RGB), "tiff", binDir.resolve(cap.getKymographName() + ".tif").toFile());
            plugins.fmp.multitools.experiment.cage.Cage cage = new plugins.fmp.multitools.experiment.cage.Cage();
            cage.getProperties().setCageID(i); cage.setCageNFlies(i == 0 ? 1 : 0);
            cage.addCapillaryIfUniqueBulkFilteredOnCageID(diskSource.getCapillaries().getList());
            diskSource.getCages().addCageIfUnique(cage);
        }
        check(diskSource.getFrameTimeScale().save(diskDir.toString()), "Fixture timestamps did not save");
        check(diskSource.save_cages_description_and_measures(), "Fixture cages did not save");
        processor.process(diskSource, true, false, () -> false, message -> {});
        Experiment cold = new Experiment();
        cold.setResultsDirectory(diskDir.toString()); cold.setBinDirectory(binDir.toString());
        check(processor.process(cold, true, false, () -> false, message -> {}), "Disk-loaded experiment did not process");
        check(cold.getCapillaries().getList().size() == 3, "Disk load lost capillaries");
        check(cold.getCapillaries().getList().stream().filter(c -> c.getNFlies() == 0).count() == 2, "Disk load lost no-fly assignments");
        check(cold.getSeqKymos().getSequence() == null || cold.getSeqKymos().getSequence().getSizeT() == 0, "Batch-loaded images were not released");
        try {
            processor.process(failed, true, false, () -> false, message -> {});
            throw new AssertionError("Invalid experiment accepted");
        } catch (IllegalStateException expected) {
            check(!java.nio.file.Files.exists(failedDir.resolve("CapillariesMeasures.csv")), "Failed detection wrote files");
            for (Capillary c : failed.getCapillaries().getList()) check(!c.getCorrelationGulps().hasMeasures(), "Failed detection installed partial results");
        }
        Experiment stopped = fixture(new int[1]);
        boolean[] stop = { false };
        check(!processor.process(stopped, true, false, () -> stop[0], message -> stop[0] = true), "Stop ignored");
        for (Capillary c : stopped.getCapillaries().getList()) check(!c.getCorrelationGulps().hasMeasures(), "Stopped series installed results");
        boolean[] savingStop = { false };
        plugins.fmp.multicafe.dlg.levels.IntakeSeriesProcessor finishSave = new plugins.fmp.multicafe.dlg.levels.IntakeSeriesProcessor() {
            @Override protected void save(Experiment e) { savingStop[0] = true; }
        };
        check(finishSave.process(fixture(new int[1]), true, false, () -> savingStop[0], message -> {}), "Stop during save lost completed experiment");
        java.nio.file.Path notDirectory = output.resolve("not-a-directory");
        java.nio.file.Files.write(notDirectory, new byte[] { 1 });
        Experiment unsavable = fixture(new int[1]); unsavable.setResultsDirectory(notDirectory.toAbsolutePath().toString());
        try {
            processor.process(unsavable, true, false, () -> false, message -> {});
            throw new AssertionError("Save failure was not reported");
        } catch (IllegalStateException expected) { check(expected.getMessage().contains("save"), "Wrong save failure"); }
        System.out.println("PASS: three experiment ranges; actual CSV persistence; cold loading of measures/TIFFs/controls; image cleanup; gulp conversion; browser locking/restoration; later failure stops series; stop before/during save; save failure.");
    }
    static void waitSeries(CorrelationIntakePanel p) throws Exception {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            boolean[] done = { false };
            SwingUtilities.invokeAndWait(() -> done[0] = get(p, "seriesWorker") == null);
            if (done[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Series timed out");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        int[] saves = { 0 };
        Experiment exp = fixture(saves);
        CorrelationIntakePanel[] holder = { null };
        SwingUtilities.invokeAndWait(() -> holder[0] = new CorrelationIntakePanel(exp, exp.getCapillaries().getList().get(0)));
        CorrelationIntakePanel p = holder[0];
        try {
            for (boolean episodes : new boolean[] { true, false }) {
                SwingUtilities.invokeAndWait(() -> {
                    ((JCheckBox)get(p, "persistentEpisodes")).setSelected(episodes);
                    ((JComboBox<?>)get(p, "runMode")).setSelectedIndex(1);
                    ((JButton)get(p, "detectAll")).doClick();
                });
                waitBatch(p);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        Map<?, ?> results = (Map<?, ?>)field(p, "batchResults");
                        check(results.size() == 3, "Missing batch capillaries: " + ((JLabel)field(p, "batchStatus")).getText());
                        check(!((JSpinner)field(p, "threshold")).isEnabled(), "Automatic threshold editable");
                        check(((JLabel)field(p, "detectorVersion")).getText().contains(episodes ? "persistent episodes" : "single-bin (persistence off)"),
                                "Detector label does not identify the active rule");
                        Method amplitudes = p.getClass().getDeclaredMethod("candidateAmplitudes"); amplitudes.setAccessible(true);
                        Method bottoms = p.getClass().getDeclaredMethod("candidateBottoms"); bottoms.setAccessible(true);
                        for (Capillary cap : exp.getCapillaries().getList()) {
                            ((JComboBox<?>)field(p, "capillaries")).setSelectedItem(cap);
                            Object result = results.get(cap);
                            check(Arrays.equals((double[])field(result, "amplitudes"), (double[])amplitudes.invoke(p)), "Batch/manual amplitude mismatch");
                            if (episodes) check(Arrays.equals((double[])field(result, "bottoms"), (double[])bottoms.invoke(p)), "Geometry mismatch");
                            check(!cap.getCorrelationGulps().hasMeasures(), "Detection saved results prematurely");
                        }
                        if (args.length > 0 && episodes) {
                            p.setSize(1180, 880); layout(p);
                            BufferedImage image = new BufferedImage(1180, 880, BufferedImage.TYPE_INT_RGB);
                            java.awt.Graphics2D g = image.createGraphics(); p.printAll(g); g.dispose();
                            ImageIO.write(image, "png", Paths.get(args[0]).toFile());
                        }
                        Capillary cap = exp.getCapillaries().getList().get(0);
                        if(episodes) cap.getTopRaw().polylineLevel.xpoints[1] += .25;
                        else cap.getTopRaw().polylineLevel.ypoints[1] += 1;
                        call(p, "saveAll");
                        check(((Map<?, ?>)field(p, "batchResults")).isEmpty(), "Stale batch accepted");
                        check(!cap.getCorrelationGulps().hasMeasures(), "Stale batch installed");
                        check(saves[0] == 0, "Stale batch wrote files");
                        if(episodes) cap.getTopRaw().polylineLevel.xpoints[1] -= .25;
                        else cap.getTopRaw().polylineLevel.ypoints[1] -= 1;
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            SwingUtilities.invokeAndWait(() -> ((JButton)get(p, "detectAll")).doClick());
            waitBatch(p);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    double[][] raw = exp.getCapillaries().getList().stream().map(c -> c.getTopRaw().polylineLevel.ypoints.clone()).toArray(double[][]::new);
                    call(p, "saveAll");
                    check(saves[0] == 1, "Save all did not persist once");
                    for (int i = 0; i < 3; i++) {
                        Capillary c = exp.getCapillaries().getList().get(i);
                        check(c.getCorrelationGulps().hasMeasures(), "Save all missed capillary");
                        check(!c.getCorrelationGulps().isManualThreshold(), "Batch used manual threshold");
                        check(Arrays.equals(raw[i], c.getTopRaw().polylineLevel.ypoints), "Batch changed topraw");
                    }
                    Method persist = p.getClass().getDeclaredMethod("persistBatch", boolean.class); persist.setAccessible(true);
                    persist.invoke(p, true);
                    check(saves[0] == 2, "Copy all did not save once");
                    for (Capillary c : exp.getCapillaries().getList()) {
                        Level2D ordinary = c.getGulps().getHeightSeries();
                        Level2D detected = c.getCorrelationGulps().getEvents().getHeightSeries();
                        check(ordinary.npoints == detected.npoints && Arrays.equals(ordinary.ypoints, detected.ypoints), "Ordinary gulps differ from detected intake");
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            Capillary invalid = exp.getCapillaries().getList().get(1);
            invalid.getTopRaw().polylineLevel.npoints = 4;
            SwingUtilities.invokeAndWait(() -> ((JButton)get(p, "detectAll")).doClick());
            waitBatch(p);
            SwingUtilities.invokeAndWait(() -> {
                check(((Map<?, ?>)get(p, "batchResults")).isEmpty(), "Failed batch kept partial results");
                check(!((JButton)get(p, "saveAll")).isEnabled(), "Failed batch enabled save");
            });
            invalid.getTopRaw().polylineLevel.npoints = 50;
            SwingUtilities.invokeAndWait(() -> {
                ((JButton)get(p, "detectAll")).doClick();
                ((JButton)get(p, "cancelAll")).doClick();
                check(get(p, "batchWorker") == null, "Cancellation did not release worker");
                check(((Map<?, ?>)get(p, "batchResults")).isEmpty(), "Cancelled batch retained results");
                ((JComboBox<?>)get(p, "runMode")).setSelectedIndex(0);
                try { call(p, "analyzeSelected"); } catch (Exception e) { throw new RuntimeException(e); }
            });
            long deadline = System.currentTimeMillis() + 30000;
            while (System.currentTimeMillis() < deadline) {
                boolean[] ready = { false };
                SwingUtilities.invokeAndWait(() -> ready[0] = get(p, "preview") != null);
                if (ready[0]) break;
                Thread.sleep(20);
            }
            SwingUtilities.invokeAndWait(() -> check(((JSpinner)get(p, "threshold")).isEnabled(), "Manual mode no longer editable"));
            System.out.println("PASS: all capillaries; episode/single-bin parity; preview-only detection; stale-save rejection; save-all persistence; unchanged topraw; failed batch; cancellation; manual mode.");
        } finally { SwingUtilities.invokeAndWait(p::close); }
        checkSeries();
        System.exit(0);
    }
}
