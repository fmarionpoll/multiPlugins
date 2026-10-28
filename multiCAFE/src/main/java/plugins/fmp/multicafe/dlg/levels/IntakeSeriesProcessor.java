package plugins.fmp.multicafe.dlg.levels;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;

/** Prepares every capillary before replacing any gulps; saves one experiment at a time. */
public class IntakeSeriesProcessor {
    public boolean process(Experiment exp, boolean episodes, boolean local,
            BooleanSupplier stopping, Consumer<String> progress) throws Exception {
        boolean hadImages = hasImages(exp);
        try {
            if (stopping.getAsBoolean()) return false;
            load(exp);
            List<Capillary> targets = new ArrayList<>(exp.getCapillaries().getList());
            if (targets.isEmpty()) throw new IllegalStateException("No capillaries to detect");
            Map<Capillary, CorrelationIntakePanel.AutomaticResult> results = new IdentityHashMap<>();
            for (Capillary cap : targets) {
                if (stopping.getAsBoolean()) return false;
                progress.accept("Capillary " + (results.size() + 1) + "/" + targets.size() + ": " + cap.getLast2ofCapillaryName());
                try {
                    results.put(cap, new CorrelationIntakePanel.AutomaticResult(
                            CorrelationGulpPreview.computeAccepted(exp, cap, local), episodes, local));
                } catch (RuntimeException error) {
                    throw new IllegalStateException(cap.getLast2ofCapillaryName() + ": " + error.getMessage(), error);
                }
            }
            if (stopping.getAsBoolean()) return false;
            // Source checks and model replacement share one EDT operation, preventing
            // an editor event between validation and installation of the results.
            boolean[] installed = { false };
            Runnable commit = () -> {
                if (stopping.getAsBoolean()) return;
                List<Capillary> current = exp.getCapillaries().getList();
                if (current.size() != targets.size() || !current.containsAll(targets)
                        || results.values().stream().anyMatch(r -> !r.preview.matchesSources(exp)))
                    throw new IllegalStateException("Source levels changed during detection");
                for (Capillary cap : targets) {
                    results.get(cap).install(cap);
                    if (!cap.copyCorrelationGulpsToGulps())
                        throw new IllegalStateException("Could not copy intake to gulps for " + cap.getLast2ofCapillaryName());
                }
                installed[0] = true;
            };
            if (SwingUtilities.isEventDispatchThread()) commit.run();
            else SwingUtilities.invokeAndWait(commit);
            if (!installed[0]) return false;
            progress.accept("Saving gulp measures...");
            // Once installed, always finish this save, even if Stop is requested.
            save(exp);
            return true;
        } finally {
            // Only release images this run loaded; leave open experiment viewers intact.
            if (!hadImages && exp.getSeqKymos() != null) exp.getSeqKymos().closeSequence();
        }
    }

    protected void load(Experiment exp) {
        if (exp instanceof LazyExperiment) ((LazyExperiment) exp).loadIfNeeded();
        boolean hasLevels = exp.getCapillaries().getList().stream().anyMatch(cap ->
                cap.getTopRaw() != null && cap.getTopRaw().polylineLevel != null && cap.getTopRaw().polylineLevel.npoints > 0);
        if (!hasLevels) {
            exp.xmlLoad_MCExperiment();
            if (!exp.load_capillaries_description_and_measures())
                throw new IllegalStateException("Could not load capillary measurements");
            if (!exp.load_cages_description_and_measures())
                throw new IllegalStateException("Could not load cages and no-fly control assignments");
            exp.getCages().transferNFliesFromCagesToCapillaries(exp.getCapillaries().getList());
        }
        exp.ensureFrameTimeScale();
        if (!hasImages(exp) && !exp.loadKymographs())
            throw new IllegalStateException("Could not load kymographs");
    }

    protected void save(Experiment exp) {
        String directory = exp.getResultsDirectory();
        if (directory == null
                || !exp.getCapillaries().getPersistence().saveDescriptions(exp.getCapillaries(), directory)
                || !exp.getCapillaries().getPersistence().saveMeasures(exp.getCapillaries(), directory, exp.getNominalIntervalSec()))
            throw new IllegalStateException("Could not save gulp measures");
    }

    private static boolean hasImages(Experiment exp) {
        return exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
                && exp.getSeqKymos().getSequence().getSizeT() > 0;
    }
}
