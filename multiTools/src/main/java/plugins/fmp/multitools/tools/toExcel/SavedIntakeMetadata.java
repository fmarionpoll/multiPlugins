package plugins.fmp.multitools.tools.toExcel;

import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;

/** Shared provenance for the separately saved intake; method_options includes manual edits. */
public final class SavedIntakeMetadata {
    private SavedIntakeMetadata() {}
    public static final String[] HEADERS = { "experiment_id", "cage_id", "capillary_id", "window_radius",
        "max_shift_px", "threshold_px_per_min", "manual_threshold", "method_options" };
    public static String[] row(String experiment, int cage, String id, Capillary cap) {
        CapillaryCorrelationGulps intake = cap.getCorrelationGulps();
        return new String[] { experiment, Integer.toString(cage), id, Integer.toString(intake.getWindowRadius()),
            Double.toString(intake.getMaxShiftPx()), Double.toString(intake.getThresholdPxPerMin()),
            Boolean.toString(intake.isManualThreshold()), intake.getMethodOptions() };
    }
}
