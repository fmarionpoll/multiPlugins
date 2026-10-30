package plugins.fmp.multitools.series;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** Correlate and validate an entire experiment, then persist before advancing. */
public class RefineMeniscus extends BuildSeries {
    private final int radius;
    private final double correction;
    private final boolean expand;
    private final MeniscusRefinement.Method method;
    private volatile boolean stopping;
    private int savedExperiments;

    public RefineMeniscus(int radius, double correction, boolean expand) {
        this(radius, correction, expand, MeniscusRefinement.Method.ADJACENT);
    }

    public RefineMeniscus(int radius, double correction, boolean expand, MeniscusRefinement.Method method) {
        this.radius = radius;
        this.correction = correction;
        this.expand = expand;
        this.method = java.util.Objects.requireNonNull(method, "Correlation method");
    }

    /** Finish any save already in progress; never leave half an experiment validated. */
    public void requestStop() {
        stopping = true;
    }

    public int getSavedExperiments() {
        return savedExperiments;
    }

    @Override
    void analyzeExperiment(Experiment exp) {
        try {
            if (stopping) {
                stopFlag = true;
                return;
            }
            firePropertyChange("refinement_experiment", null, exp);
            load(exp);
            Map<Capillary, MeniscusRefinement.Proposal> proposals = new LinkedHashMap<>();
            for (Capillary cap : exp.getCapillaries().getList()) {
                if (stopping) {
                    stopFlag = true;
                    return;
                }
                Level2D raw = cap.getTopRaw().polylineLevel;
                if (raw == null || raw.npoints < 2)
                    throw new IllegalStateException("Detect topraw first for " + cap.getKymographName());
                BufferedImage image = nativeImage(exp, cap, raw.npoints);
                proposals.put(cap, MeniscusRefinement.propose(raw, image, radius, correction, expand, null, method));
            }
            if (proposals.isEmpty())
                throw new IllegalStateException("No capillaries to refine");
            if (stopping) {
                stopFlag = true;
                return;
            }
            MeniscusRefinement.Validation validation = MeniscusRefinement.validateAll(proposals);
            try {
                save(exp);
            } catch (RuntimeException error) {
                MeniscusRefinement.undo(validation);
                throw error;
            }
            savedExperiments++;
            // No ROI-to-model pull here: the newly validated model is authoritative.
            exp.closeSequences();
            if (stopping)
                stopFlag = true;
        } catch (RuntimeException error) {
            throw new IllegalStateException("Refinement stopped at " + exp + ": " + error.getMessage(), error);
        }
    }

    private BufferedImage nativeImage(Experiment exp, Capillary cap, int width) {
        List<String> files = exp.getSeqKymos().getImagesList();
        // A combined Icy sequence can pad frames to a common width and its frame
        // order need not match a previously saved capillary index. Like level
        // detection, correlate the original file, matched by kymograph name.
        if (files != null && !files.isEmpty()) {
            for (String path : files) {
                String name = new File(path).getName().replaceFirst("[.][^.]+$", "");
                if (name.equals(cap.getKymographName()))
                    return MeniscusRefinement.image(new SequenceLoaderService().imageIORead(path), cap, width);
            }
            throw new IllegalStateException("No native kymograph file found for " + cap.getKymographName());
        }
        // Newly built, in-memory experiments may not have file paths yet.
        return MeniscusRefinement.image(exp.getSeqKymos().getSequence(), cap, width);
    }

    protected void load(Experiment exp) {
        // Preserve already validated in-memory topraw in the open experiment.
        boolean loaded = exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
                && exp.getSeqKymos().getSequence().getSizeT() > 0;
        if (!loaded) {
            exp.xmlLoad_MCExperiment();
            if (!exp.load_capillaries_description_and_measures() || !exp.loadKymographs())
                throw new IllegalStateException("Could not load capillary measurements and kymographs");
        }
    }

    protected void save(Experiment exp) {
        String directory = exp.getResultsDirectory();
        if (directory == null
                || !exp.getCapillaries().getPersistence().saveDescriptions(exp.getCapillaries(), directory)
                || !exp.getCapillaries().getPersistence().saveMeasures(exp.getCapillaries(), directory,
                        exp.getNominalIntervalSec()))
            throw new IllegalStateException("Could not save refined capillary measurements");
    }
}
