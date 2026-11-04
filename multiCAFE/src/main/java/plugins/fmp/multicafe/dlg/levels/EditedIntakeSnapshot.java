package plugins.fmp.multicafe.dlg.levels;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.ManualIntakeEdits;

/**
 * Settings captured on the event thread. Previews are prepared in the
 * background, then installed together.
 */
public final class EditedIntakeSnapshot {
	final Experiment exp;
	final List<Capillary> caps;
	final boolean episodes, local;
	final Map<Capillary, Double> chosenThresholds;
	final Map<Capillary, Boolean> manualThreshold;
	final Map<Capillary, String> edits;
	final Map<Capillary, CorrelationGulpPreview> previews;

	EditedIntakeSnapshot(Experiment exp, List<Capillary> caps, boolean episodes, boolean local,
			Map<Capillary, Double> chosenThresholds, Map<Capillary, Boolean> manualThreshold,
			Map<Capillary, String> edits, Map<Capillary, CorrelationGulpPreview> previews) {
		this.exp = exp;
		this.caps = caps;
		this.episodes = episodes;
		this.local = local;
		this.chosenThresholds = chosenThresholds;
		this.manualThreshold = manualThreshold;
		this.edits = edits;
		this.previews = new IdentityHashMap<>(previews);
	}

	void prepare(Consumer<String> progress) {
		int done = 0;
		for (Capillary cap : caps) {
			progress.accept("Preparing " + (++done) + "/" + caps.size() + ": " + cap.getLast2ofCapillaryName());
			CorrelationGulpPreview preview = previews.get(cap);
			if (preview == null || preview.localEvaporation != local || !preview.matchesSources(exp))
				preview = CorrelationGulpPreview.computeAccepted(exp, cap, local);
			previews.put(cap, preview);
		}
	}

	boolean install(boolean copy) {
		List<Capillary> current = exp.getCapillaries().getList();
		if (current.size() != caps.size() || !current.containsAll(caps))
			return false;
		for (CorrelationGulpPreview preview : previews.values())
			if (preview == null || !preview.matchesSources(exp))
				return false;
		for (Capillary cap : caps) {
			CorrelationGulpPreview preview = previews.get(cap);
			boolean manual = Boolean.TRUE.equals(manualThreshold.get(cap));
			double threshold = manual ? chosenThresholds.get(cap)
					: Double.isFinite(preview.noise.automatic) ? preview.noise.automatic : 1;
			AutomaticIntakeResult detected = new AutomaticIntakeResult(preview, episodes, local, threshold);
			double[] amplitudes = detected.amplitudes.clone();
			double[] bottoms = detected.bottoms == null ? preview.raw.clone() : detected.bottoms.clone();
			ManualIntakeEdits manualEdits = ManualIntakeEdits.fromOptions(edits.get(cap), preview.raw.length);
			manualEdits.apply(amplitudes, bottoms);
			CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
			stored.set(amplitudes, 0, 0, threshold, manual);
			stored.setBottomPositions(bottoms);
			stored.setMethodOptions(IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + episodes
					+ ",local=" + local + manualEdits.options() + ",scale=" + (local ? preview.localScale.factor : 1));
		}
		if (copy)
			for (Capillary cap : caps)
				if (!cap.copyCorrelationGulpsToGulps())
					throw new IllegalStateException(
							"Could not copy intake to gulps for " + cap.getLast2ofCapillaryName());
		return true;
	}
}
