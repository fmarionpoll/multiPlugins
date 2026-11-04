package plugins.fmp.multicafe.dlg.levels;
import java.util.Map;
import javax.swing.SwingWorker;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
/** Event-thread job ownership. Completion callbacks must check ownership before publishing results. */
final class IntakeJobs {
    SwingWorker<CorrelationGulpPreview, Void> preview;
    SwingWorker<Map<Capillary, AutomaticIntakeResult>, String> batch;
    SwingWorker<Integer, String> series;
    SwingWorker<Void, Void> export;
    SwingWorker<Void, String> edited;
    SwingWorker<Boolean, Void> save;
    boolean busy() { return batch != null || series != null || export != null || edited != null || save != null; }
    void cancelPreview() {
        if (preview != null) preview.cancel(true);
        preview = null;
    }
}
