package plugins.fmp.multicafe.dlg.levels;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryIntakes;
import plugins.fmp.multitools.service.ManualIntakeEdits;
import plugins.fmp.multitools.tools.polyline.Level2D;
/** Shared validation and installation policy. Call on the event thread before saving. */
final class IntakePersistence {
    enum Destination { SAVE_INTAKE, REPLACE_GULPS_AND_SAVE }

    /** Preserve the stored event list while moving its geometry to the corrected trace. */
    static CapillaryIntakes rebaseStoredIntake(CapillaryIntakes stored, double[] oldRaw,
            double toprawShift, double manualShift) {
        CapillaryIntakes rebased = new CapillaryIntakes();
        rebased.copy(stored);
        if (!stored.hasMeasures())
            return rebased;
        Level2D heights = stored.getEvents().getHeightSeries();
        String options = stored.getMethodOptions();
        ManualIntakeEdits edits = ManualIntakeEdits.fromOptions(options, heights.npoints);
        double automaticShift = toprawShift - edits.automaticOffset();
        double[] bottoms = new double[heights.npoints];
        Arrays.fill(bottoms, Double.NaN);
        for (int i = 1; i < bottoms.length; i++)
            if (heights.ypoints[i] > 0) {
                double fallback = i < oldRaw.length ? oldRaw[i] : Double.NaN;
                bottoms[i] = stored.bottomAt(i, fallback) + (edits.covers(i) ? manualShift : automaticShift);
            }
        edits.rebaseToTopraw(manualShift);
        rebased.setBottomPositions(bottoms);
        rebased.setMethodOptions(edits.replaceOptionsIn(options));
        return rebased;
    }

    static boolean install(Experiment exp, List<Capillary> expected,
            Map<Capillary, PreparedIntake> results, Destination destination) {
        List<Capillary> current = exp.getCapillaries().getList();
        if (current.size() != expected.size() || !current.containsAll(expected)) return false;
        for (Map.Entry<Capillary, PreparedIntake> entry : results.entrySet())
            if (!current.contains(entry.getKey()) || !entry.getValue().detected.preview.matchesSources(exp)) return false;
        for (Map.Entry<Capillary, PreparedIntake> entry : results.entrySet()) entry.getValue().install(entry.getKey());
        if (destination == Destination.REPLACE_GULPS_AND_SAVE)
            for (Capillary cap : results.keySet())
                if (!cap.copyIntakesToGulps())
                    throw new IllegalStateException("Could not replace gulps for " + cap.getLast2ofCapillaryName());
        return true;
    }
}
