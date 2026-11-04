package plugins.fmp.multicafe.dlg.levels;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;
/** One experiment's retained editing state; owned by the Swing event thread. */
final class IntakeSession {
    static final class CapillaryState { Double threshold; ManualIntakeEdits edits; }
    private final Map<Capillary, CapillaryState> states = new IdentityHashMap<>();
    final Map<Capillary, CorrelationGulpPreview> previews = new IdentityHashMap<>();
    private CapillaryState state(Capillary cap) { return states.computeIfAbsent(cap, key -> new CapillaryState()); }
    Double threshold(Capillary cap) { return state(cap).threshold; }
    void threshold(Capillary cap, Double value) { state(cap).threshold = value; }
    boolean hasEdits(Capillary cap) { return state(cap).edits != null; }
    ManualIntakeEdits edits(Capillary cap) {
        CapillaryState state = state(cap);
        if (state.edits == null) state.edits = new ManualIntakeEdits();
        return state.edits;
    }
    void edits(Capillary cap, ManualIntakeEdits edits) { state(cap).edits = edits; }
    void forEachThreshold(BiConsumer<Capillary, Double> action) {
        states.forEach((cap, state) -> { if (state.threshold != null) action.accept(cap, state.threshold); });
    }
    void clearEdits() { states.values().forEach(state -> state.edits = null); }
    void clearThresholds() { states.values().forEach(state -> state.threshold = null); }
}
