/* Headless browser-session checks; uses CorrelationIntakeBatchSmoke fixtures. */
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;

public class CorrelationIntakeBrowserSmoke {
    static void checkBatchFailureFollow() throws Exception {
        Experiment a=CorrelationIntakeBatchSmoke.fixture(new int[1]),b=CorrelationIntakeBatchSmoke.fixture(new int[1]);
        java.nio.file.Path output=java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get("multiCAFE/target"),"intake-failed-navigation-");
        a.setResultsDirectory(java.nio.file.Files.createDirectories(output.resolve("a")).toAbsolutePath().toString());
        b.setResultsDirectory(java.nio.file.Files.createDirectories(output.resolve("b")).toAbsolutePath().toString());
        b.getCapillaries().getList().get(1).getTopRaw().polylineLevel.npoints=4;
        JComboBox<Experiment> browser=new JComboBox<>(new Experiment[]{a,b});
        Object[] root={null},originalPanel={null};
        SwingUtilities.invokeAndWait(()-> {
            try {
                Class<?> type=Class.forName("plugins.fmp.multicafe.dlg.levels.CorrelationIntakeBrowser");
                Constructor<?> constructor=type.getDeclaredConstructor(JComboBox.class,Capillary.class,Consumer.class);
                constructor.setAccessible(true);root[0]=constructor.newInstance(browser,null,null);
                originalPanel[0]=get(root[0],"active");
                ((JComboBox<?>)get(originalPanel[0],"runMode")).setSelectedIndex(2);
                ((JComboBox<?>)get(originalPanel[0],"seriesRange")).setSelectedIndex(1);
                ((JButton)get(originalPanel[0],"runSeries")).doClick();
            } catch(Exception error){throw new RuntimeException(error);}
        });
        try {
            CorrelationIntakeBatchSmoke.waitSeries((plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel)originalPanel[0]);
            drain();drain();
            SwingUtilities.invokeAndWait(()-> {
                Object failedPanel=get(root[0],"active");
                check(browser.getSelectedIndex()==1 && browser.isEnabled(),"Failed experiment was not selected/unlocked");
                check(failedPanel!=originalPanel[0] && get(failedPanel,"experiment")==b,"Dialog did not follow failing experiment");
                check(((JComboBox<?>)get(failedPanel,"runMode")).getSelectedIndex()==2,"Failure status hidden in another mode");
                check(((JLabel)get(failedPanel,"seriesStatus")).getText().contains("Failed at Experiment 1/2:"),"Failure details lost when panel switched");
                check((Integer)get(originalPanel[0],"seriesSaved")==1,"Navigation changed completed saves");
            });
        } finally {SwingUtilities.invokeAndWait(()->call(root[0],"close"));}
    }
    static Object get(Object o, String name) { return CorrelationIntakeBatchSmoke.get(o, name); }
    static void check(boolean value, String message) { CorrelationIntakeBatchSmoke.check(value, message); }
    static void call(Object o, String name) {
        try { CorrelationIntakeBatchSmoke.call(o, name); } catch (Exception error) { throw new RuntimeException(error); }
    }
    static void drain() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
    static void preview(Object panel) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            boolean[] ready = { false };
            SwingUtilities.invokeAndWait(() -> ready[0] = get(panel, "preview") != null && get(panel, "worker") == null);
            if (ready[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Selected experiment did not become ready");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Experiment a = CorrelationIntakeBatchSmoke.fixture(new int[1]);
        Experiment b = CorrelationIntakeBatchSmoke.fixture(new int[1]);
        JComboBox<Experiment> browser = new JComboBox<>(new Experiment[] { a, b });
        int initialListeners = browser.getItemListeners().length;
        Object[] wrapper = { null };
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> type = Class.forName("plugins.fmp.multicafe.dlg.levels.CorrelationIntakeBrowser");
                Constructor<?> constructor = type.getDeclaredConstructor(JComboBox.class, Capillary.class, Consumer.class);
                constructor.setAccessible(true);
                wrapper[0] = constructor.newInstance(browser, a.getCapillaries().getList().get(0), null);
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        Object root = wrapper[0];
        try {
            Object panelA = get(root, "active"); preview(panelA);
            SwingUtilities.invokeAndWait(() -> ((JSpinner)get(panelA, "threshold")).setValue(2.5));
            SwingUtilities.invokeAndWait(() -> browser.setSelectedIndex(1)); drain();
            Object panelB = get(root, "active"); preview(panelB);
            SwingUtilities.invokeAndWait(() -> {
                check(get(root, "selected") == b, "Next experiment not selected");
                check(panelA != panelB && !(Boolean)get(root, "closed"), "Browser closed or reused wrong experiment session");
                check((Boolean)get(panelA, "suspended") && !((Timer)get(panelA, "sourceTimer")).isRunning(), "Old session still polling");
                check(get(panelA, "worker") == null && get(panelA, "tooltipLayoutGuard") == null, "Old worker/listener remained active");
                check(get(panelB, "tooltipLayoutGuard") != null, "New session listener not installed");
                ((JSpinner)get(panelB, "threshold")).setValue(1.5);
                browser.setSelectedIndex(0);
            }); drain(); preview(panelA);
            SwingUtilities.invokeAndWait(() -> {
                check(get(root, "active") == panelA, "Previous experiment did not reuse session");
                check(((Number)((JSpinner)get(panelA, "threshold")).getValue()).doubleValue() == 2.5, "Lost first experiment's manual threshold");
                browser.setSelectedIndex(1); browser.setSelectedIndex(0); browser.setSelectedIndex(1);
            }); drain(); preview(panelB);
            SwingUtilities.invokeAndWait(() -> {
                check(get(root, "active") == panelB, "Rapid selection used stale experiment");
                check(((Number)((JSpinner)get(panelB, "threshold")).getValue()).doubleValue() == 1.5, "Threshold crossed experiments");
                browser.setSelectedIndex(-1);
            }); drain();
            SwingUtilities.invokeAndWait(() -> {
                check(get(root, "active") == null && !(Boolean)get(root, "closed"), "Empty selection closed browser");
                // Simulate descriptor loading replacing the capillary objects.
                Experiment reloaded = CorrelationIntakeBatchSmoke.fixture(new int[1]);
                a.getCapillaries().getList().clear();
                a.getCapillaries().getList().addAll(reloaded.getCapillaries().getList());
                browser.setSelectedIndex(0);
            }); drain(); preview(panelA);
            SwingUtilities.invokeAndWait(() -> {
                check(((JComboBox<?>)get(panelA, "capillaries")).getSelectedItem() == a.getCapillaries().getList().get(0), "Reload retained old capillary objects");
                check(((Number)((JSpinner)get(panelA, "threshold")).getValue()).doubleValue() == 2.5, "Reload lost manual threshold");
                check(((Map<?, ?>)get(root, "sessions")).size() == 2, "Selection created duplicate sessions");
            });
            Experiment delayed = CorrelationIntakeBatchSmoke.fixture(new int[1]);
            plugins.fmp.multitools.experiment.sequence.SequenceKymos loaded = delayed.getSeqKymos();
            delayed.setSeqKymos(new plugins.fmp.multitools.experiment.sequence.SequenceKymos());
            SwingUtilities.invokeAndWait(() -> { browser.addItem(delayed); browser.setSelectedItem(delayed); }); drain();
            Object waitingPanel = get(root, "active");
            SwingUtilities.invokeAndWait(() -> {
                check((Boolean)get(waitingPanel, "waitingForSources"), "Missing images did not enter waiting state");
                check(get(waitingPanel, "preview") == null, "Missing images displayed another experiment's preview");
                delayed.setSeqKymos(loaded);
            });
            preview(waitingPanel);
            SwingUtilities.invokeAndWait(() -> check(!(Boolean)get(waitingPanel, "waitingForSources"), "Loading images did not ready the panel"));
        } finally { SwingUtilities.invokeAndWait(() -> call(root, "close")); }
        SwingUtilities.invokeAndWait(() -> {
            check(browser.getItemListeners().length == initialListeners, "Browser listener leaked on close");
            check(((Map<?, ?>)get(root, "sessions")).isEmpty(), "Closing retained sessions");
        });
        checkBatchFailureFollow();
        System.out.println("PASS: next/previous/direct/rapid/empty experiment selection; per-experiment thresholds; descriptor reload; delayed image loading; timer/worker/tooltip/browser-listener cleanup; automatic failed-experiment selection and visible batch error.");
        System.exit(0);
    }
}
