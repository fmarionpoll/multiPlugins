package plugins.fmp.multicafe.dlg.levels;
import java.util.List;
import java.util.Map;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
/** Shared validation and installation policy. Call on the event thread before saving. */
final class IntakePersistence {
    enum Destination { SAVE_INTAKE, REPLACE_GULPS_AND_SAVE }
    static boolean install(Experiment exp, List<Capillary> expected,
            Map<Capillary, PreparedIntake> results, Destination destination) {
        List<Capillary> current = exp.getCapillaries().getList();
        if (current.size() != expected.size() || !current.containsAll(expected)) return false;
        for (Map.Entry<Capillary, PreparedIntake> entry : results.entrySet())
            if (!current.contains(entry.getKey()) || !entry.getValue().detected.preview.matchesSources(exp)) return false;
        for (Map.Entry<Capillary, PreparedIntake> entry : results.entrySet()) entry.getValue().install(entry.getKey());
        if (destination == Destination.REPLACE_GULPS_AND_SAVE)
            for (Capillary cap : results.keySet())
                if (!cap.copyCorrelationGulpsToGulps())
                    throw new IllegalStateException("Could not replace gulps for " + cap.getLast2ofCapillaryName());
        return true;
    }
}
