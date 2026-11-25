package plugins.fmp.multitools.series;

import static org.junit.Assert.*;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.Test;
import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class BuildSeriesProgressTest {
    @org.junit.ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
            new plugins.fmp.multitools.test.InMemoryImageRule();
    private static final class RecordingProgress extends ProgressFrame {
        final List<String> messages = new ArrayList<>();
        boolean closed;
        RecordingProgress() { super("test"); }
        @Override public void setMessage(String value) { messages.add(value); super.setMessage(value); }
        @Override public void close() { closed = true; super.close(); }
    }
    private static final class Lazy extends LazyExperiment {
        boolean prepared, preparedOnEdt;
        Lazy(int number) { super(new ExperimentMetadata("C:/experiments/exp" + number + "/images",
                "C:/experiments/exp" + number + "/results", "C:/experiments/exp" + number + "/results/bin60")); }
        @Override public void loadIfNeeded() { prepared = true; preparedOnEdt = SwingUtilities.isEventDispatchThread(); }
    }
    private static final class Worker extends BuildSeries {
        final RecordingProgress reporting = new RecordingProgress();
        final List<Experiment> processed = new ArrayList<>();
        boolean fail, stopAfterFirst;
        @Override protected ProgressFrame createSeriesProgress() { return reporting; }
        @Override void analyzeExperiment(Experiment exp) {
            assertTrue(((Lazy) exp).prepared); processed.add(exp);
            MeasurementProgress.phase("Measuring images"); MeasurementProgress.images(4, 10);
            if (fail) throw new IllegalStateException("test failure");
            MeasurementProgress.phase("Saving measurements");
            if (stopAfterFirst) stopFlag = true;
        }
    }
    private List<Lazy> setup(Worker worker) throws Exception {
        List<Lazy> experiments = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            JComboBoxExperimentLazy combo = new JComboBoxExperimentLazy(); combo.setSuppressLazyLoad(true);
            for (int i = 0; i < 4; i++) { Lazy exp = new Lazy(i); experiments.add(exp); combo.addItem(exp); }
            combo.setSelectedIndex(0); combo.index0 = 1; combo.index1 = 2; worker.options.expList = combo;
        });
        return experiments;
    }
    @Test(timeout = 15000) public void selectedRangeShowsRelativeCountsAndFrameProgressWithoutBlockingEdt() throws Exception {
        Worker worker = new Worker(); List<Lazy> experiments = setup(worker);
        assertEquals(2, worker.doInBackground().intValue());
        assertEquals(Arrays.asList(experiments.get(1), experiments.get(2)), worker.processed);
        assertFalse(experiments.get(1).preparedOnEdt); assertFalse(experiments.get(2).preparedOnEdt);
        assertFalse(experiments.get(0).prepared); assertFalse(experiments.get(3).prepared);
        assertEquals(2, worker.reporting.getLength(), 0); assertEquals(2, worker.reporting.getPosition(), 0);
        assertEquals(100, worker.getProgress()); assertTrue(worker.reporting.closed); assertFalse(worker.threadRunning);
        assertTrue(worker.reporting.messages.stream().anyMatch(s -> s.contains("Experiment 1/2 (list 2/4)") && s.contains("Image 4/10")));
        assertTrue(worker.reporting.messages.stream().anyMatch(s -> s.contains("Experiment 2/2 (list 3/4)") && s.contains("Saving measurements")));
        SwingUtilities.invokeAndWait(() -> assertEquals(0, worker.options.expList.getSelectedIndex()));
    }
    @Test(timeout = 15000) public void failureClosesProgressAndRemovesReportingContext() throws Exception {
        Worker worker = new Worker(); setup(worker); worker.fail = true;
        try { worker.doInBackground(); fail("Expected test failure"); } catch (IllegalStateException expected) { }
        assertTrue(worker.reporting.closed); assertFalse(worker.threadRunning); assertFalse(BuildSeries.isBackgroundRunActive());
        int before = worker.reporting.messages.size(); MeasurementProgress.images(9, 10);
        assertEquals(before, worker.reporting.messages.size());
        SwingUtilities.invokeAndWait(() -> assertEquals(0, worker.options.expList.getSelectedIndex()));
    }
    @Test(timeout = 15000) public void stoppingDoesNotShowAnUnfinishedBatchAsComplete() throws Exception {
        Worker worker = new Worker(); setup(worker); worker.stopAfterFirst = true;
        assertEquals(1, worker.doInBackground().intValue());
        assertEquals(1, worker.reporting.getPosition(), 0); assertEquals(2, worker.reporting.getLength(), 0);
        assertEquals(50, worker.getProgress()); assertTrue(worker.reporting.closed);
    }
    @Test public void capturedContextCanReportFromParallelThreadsAndNestedContextsRestore() throws Exception {
        List<Integer> updates = Collections.synchronizedList(new ArrayList<>());
        MeasurementProgress.Listener outer = (p, u, done, total) -> updates.add(done);
        try (MeasurementProgress.Scope scope = MeasurementProgress.install(outer)) {
            MeasurementProgress.Listener captured = MeasurementProgress.current();
            Thread child = new Thread(() -> { MeasurementProgress.images(99, 100); captured.update(null, "Image", 1, 100); });
            child.start(); child.join();
            try (MeasurementProgress.Scope inner = MeasurementProgress.install((p, u, done, total) -> { })) {
                MeasurementProgress.images(98, 100);
            }
            MeasurementProgress.images(2, 100);
        }
        MeasurementProgress.images(97, 100); assertEquals(Arrays.asList(1, 2), updates);
    }
}
