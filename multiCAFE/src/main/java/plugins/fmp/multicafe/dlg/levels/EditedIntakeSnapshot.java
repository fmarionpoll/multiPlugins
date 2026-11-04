package plugins.fmp.multicafe.dlg.levels;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.ManualIntakeEdits;

/**
 * Settings captured on the event thread. Previews, detection and overrides are prepared in the
 * background. Installation only validates sources and updates the model.
 */
public final class EditedIntakeSnapshot {
	final Experiment exp;
	final List<Capillary> caps;
	final boolean episodes, local;
	final Map<Capillary, Double> chosenThresholds;
	final Map<Capillary, Boolean> manualThreshold;
	final Map<Capillary, String> edits;
	final Map<Capillary, CorrelationGulpPreview> previews;
	final Map<Capillary, PreparedIntake> results = new IdentityHashMap<>();

	EditedIntakeSnapshot(Experiment exp, List<Capillary> caps, boolean episodes, boolean local,
			Map<Capillary, Double> chosenThresholds, Map<Capillary, Boolean> manualThreshold,
			Map<Capillary, String> edits, Map<Capillary, CorrelationGulpPreview> previews) {
		this.exp = exp;
		this.caps = new java.util.ArrayList<>(caps);
		this.episodes = episodes;
		this.local = local;
		this.chosenThresholds = new IdentityHashMap<>(chosenThresholds);
		this.manualThreshold = new IdentityHashMap<>(manualThreshold);
		this.edits = new IdentityHashMap<>(edits);
		this.previews = new IdentityHashMap<>(previews);
	}

	void prepare(Consumer<String> progress) {
		int done = 0;
		for (Capillary cap : caps) {
			if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
			progress.accept("Preparing " + (++done) + "/" + caps.size() + ": " + cap.getLast2ofCapillaryName());
			CorrelationGulpPreview preview = previews.get(cap);
			if (preview == null || preview.localEvaporation != local || !preview.matchesSources(exp))
				preview = CorrelationGulpPreview.computeAccepted(exp, cap, local);
			previews.put(cap, preview);
			boolean manual = Boolean.TRUE.equals(manualThreshold.get(cap));
			double threshold = manual ? chosenThresholds.get(cap)
					: Double.isFinite(preview.noise.automatic) ? preview.noise.automatic : 1;
			AutomaticIntakeResult detected = new AutomaticIntakeResult(preview, new IntakeDetectionSettings(episodes, local), threshold, manual, null);
			results.put(cap, new PreparedIntake(detected,
					ManualIntakeEdits.fromOptions(edits.getOrDefault(cap, ""), preview.raw.length)));
		}
	}

	boolean install(boolean copy) {
		return install(copy ? IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE : IntakePersistence.Destination.SAVE_INTAKE);
	}

	boolean install(IntakePersistence.Destination destination) {
		if (results.size() != caps.size()) throw new IllegalStateException("Prepare intake before installation");
		return IntakePersistence.install(exp, caps, results, destination);
	}
}
