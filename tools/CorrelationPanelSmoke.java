/* Read-only integration smoke test using a real experiment; renders the actual Swing panel.
 * Compile with the Maven dependency classpath and run with -Djava.awt.headless=true.
 * Arguments: experiment directory, output directory. Never saves experiment objects.
 */
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.dlg.levels.CorrelationGulpPanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CorrelationPanelSmoke {
    /** Isolate test preferences from the host application's Windows registry. */
    public static final class MemoryPreferencesFactory implements java.util.prefs.PreferencesFactory {
        private final java.util.prefs.Preferences root = new MemoryPreferences(null, "");
        public java.util.prefs.Preferences userRoot() { return root; }
        public java.util.prefs.Preferences systemRoot() { return root; }
    }
    public static final class MemoryPreferences extends java.util.prefs.AbstractPreferences {
        private final Map<String, String> values = new LinkedHashMap<>();
        MemoryPreferences(java.util.prefs.AbstractPreferences parent, String name) { super(parent, name); }
        protected void putSpi(String key, String value) { values.put(key,value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        protected String[] childrenNamesSpi() { return new String[0]; }
        protected java.util.prefs.AbstractPreferences childSpi(String name) { return new MemoryPreferences(this,name); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void render(CorrelationGulpPanel panel, Path path) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1160, 820); layout(panel);
            BufferedImage image = new BufferedImage(1160, 820, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            try { ImageIO.write(image, "png", path.toFile()); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static void compute(CorrelationGulpPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { ((JButton) field(panel, "compute")).doClick(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        ((SwingWorker<?, ?>) field(panel, "worker")).get();
        for (int i = 0; i < 100; i++) {
            final boolean[] done = {false};
            SwingUtilities.invokeAndWait(() -> { try { done[0] = field(panel, "preview") != null; } catch (Exception e) { throw new RuntimeException(e); } });
            if (done[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Panel did not publish its preview");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Path root = Paths.get(args[0], "results"), output = Paths.get(args[1]);
        Files.createDirectories(output);
        Experiment exp = new Experiment();
        exp.setResultsDirectory(root.toString());
        exp.setFrameTimeScale(FrameTimeScale.load(root.toString()));
        Map<String, Capillary> caps = new LinkedHashMap<>();
        boolean section = false;
        Sequence sequence = new Sequence();
        for (String line : Files.readAllLines(root.resolve("CapillariesDescription.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;CAPILLARIES;");
            else if (section && !line.startsWith("cap_prefix")) {
                String[] row = line.split(";");
                ROI2DLine roi = new ROI2DLine(); roi.setName("line" + row[0]);
                Capillary cap = new Capillary(roi);
                cap.setKymographIndex(Integer.parseInt(row[1])); cap.setNFlies(Integer.parseInt(row[5]));
                cap.setVolume(Double.parseDouble(row[6])); cap.setPixels(Integer.parseInt(row[7]));
                cap.setKymographName(row[2]); caps.put(row[0], cap);
                BufferedImage image = ImageIO.read(root.resolve("bin_60").resolve(row[3]).toFile());
                sequence.setImage(cap.getKymographIndex(), 0, IcyBufferedImage.createFrom(image));
                exp.getCapillaries().getList().add(cap);
            }
        }
        section = false;
        for (String line : Files.readAllLines(root.resolve("CapillariesMeasures.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;TOPRAW;");
            else if (section && !line.startsWith("#") && !line.isEmpty()) {
                String[] row = line.split(";"); int n = Integer.parseInt(row[2]);
                double[] x = new double[n], y = new double[n];
                for (int i = 0; i < n; i++) { x[i] = i; y[i] = Double.parseDouble(row[i + 3]); }
                caps.get(row[0]).getTopRaw().polylineLevel = new Level2D(x, y, n);
            }
        }
        SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(sequence); exp.setSeqKymos(kymos);
        System.out.println("Loaded real experiment images and measures in memory.");
        double[] before = caps.get("1L").getTopRaw().polylineLevel.ypoints.clone();
        final CorrelationGulpPanel[] ref = new CorrelationGulpPanel[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = new CorrelationGulpPanel(exp, 2));
        CorrelationGulpPanel panel = ref[0]; compute(panel);
        CorrelationGulpPreview result = (CorrelationGulpPreview) field(panel, "preview");
        if (Math.abs(result.motion.shift[57] - 1.4) > .051) throw new AssertionError("Icy image adapter changed channel interpretation");
        render(panel, output.resolve("panel_1L_automatic.png"));
        JSpinner threshold = (JSpinner) field(panel, "threshold");
        double initial = ((Number) threshold.getValue()).doubleValue();
        Component plot = (Component) field(panel, "plot");
        Method y = plot.getClass().getDeclaredMethod("y", double.class); y.setAccessible(true);
        int initialY = (Integer) y.invoke(plot, initial);
        SwingUtilities.invokeAndWait(() -> {
            long now = System.currentTimeMillis();
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_PRESSED, now, 0, 400, initialY, 1, false));
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_DRAGGED, now+1, 0, 400, initialY-20, 0, false));
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_RELEASED, now+2, 0, 400, initialY-20, 1, false));
        });
        if (((Number) threshold.getValue()).doubleValue() <= initial) throw new AssertionError("Threshold drag failed");
        Method export = panel.getClass().getDeclaredMethod("writeCsv", Path.class); export.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { try { export.invoke(panel, output.resolve("panel_preview.csv")); } catch (Exception e) { throw new RuntimeException(e); } });
        String csv = new String(Files.readAllBytes(output.resolve("panel_preview.csv")), StandardCharsets.UTF_8);
        if (!csv.contains("manual=true") || csv.split("\n").length != 145) throw new AssertionError("CSV preview incomplete");
        SwingUtilities.invokeAndWait(() -> threshold.setValue(100.));
        Method candidates = panel.getClass().getDeclaredMethod("candidates"); candidates.setAccessible(true);
        for (boolean b : (boolean[]) candidates.invoke(panel)) if (b) throw new AssertionError("Candidate preview did not update with threshold");
        SwingUtilities.invokeAndWait(() -> { try { ((JButton) field(panel,"automatic")).doClick(); } catch (Exception e) { throw new RuntimeException(e); } });
        if (Math.abs(((Number) threshold.getValue()).doubleValue()-initial)>1e-10) throw new AssertionError("Automatic reset failed");
        SwingUtilities.invokeAndWait(() -> { try { ((JComboBox<?>) field(panel,"capillaries")).setSelectedItem(caps.get("7L")); } catch (Exception e) { throw new RuntimeException(e); } });
        compute(panel); render(panel, output.resolve("panel_7L_automatic.png"));
        if (!java.util.Arrays.equals(before, caps.get("1L").getTopRaw().polylineLevel.ypoints)) throw new AssertionError("Original levels modified");
        System.out.println("PASS: real 1L/7L preview, image adapter, draggable threshold, automatic reset, CSV export, and unchanged raw levels.");
        sequence.close();
        System.exit(0);
    }
}
