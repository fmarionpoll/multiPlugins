package plugins.fmp.multicafe.dlg.levels;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.ManualIntakeEdits;
/** Fully calculated result. Construction can run in the background; install only mutates the model. */
final class PreparedIntake {
    final AutomaticIntakeResult detected;
    final double[] amplitudes, bottoms;
    final String options;
    PreparedIntake(AutomaticIntakeResult detected, ManualIntakeEdits edits) {
        this.detected = detected;
        amplitudes = detected.amplitudes.clone();
        bottoms = detected.bottoms.clone();
        edits.apply(amplitudes, bottoms);
        int scale = detected.options.indexOf(",scale=");
        options = detected.options.substring(0, scale) + edits.options() + detected.options.substring(scale);
    }
    void install(Capillary cap) {
        CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
        stored.set(amplitudes, 0, 0, detected.threshold, detected.manualThreshold);
        stored.setBottomPositions(bottoms);
        stored.setMethodOptions(options);
    }
}
