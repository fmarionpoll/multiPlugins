package plugins.fmp.multicafe.dlg.levels;

import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.VerticalCorrelation;

/** Detector output before event overrides, at an automatic or explicitly chosen threshold. */
public final class AutomaticIntakeResult {
	final CorrelationGulpPreview preview;
	final double[] amplitudes, bottoms;
	final String options;
	final double threshold;
	final boolean manualThreshold;

	public AutomaticIntakeResult(CorrelationGulpPreview p, boolean episodes, boolean local) {
		this(p, new IntakeDetectionSettings(episodes, local), p.noise.automatic, false, null);
	}

	public AutomaticIntakeResult(CorrelationGulpPreview p, boolean episodes, boolean local, double threshold) {
		this(p, new IntakeDetectionSettings(episodes, local), threshold, true, null);
	}

	AutomaticIntakeResult(CorrelationGulpPreview p, IntakeDetectionSettings settings, double threshold,
			boolean manualThreshold, IntakeEpisodes.Detection cachedDetection) {
		preview = p;
		this.threshold = threshold;
		this.manualThreshold = manualThreshold;
		boolean episodes = settings.episodes, local = settings.local;
		if (!Double.isFinite(threshold) || threshold < 0)
			throw new IllegalArgumentException("Detection threshold unavailable");
		if (episodes) {
			IntakeEpisodes.Detection detection = cachedDetection != null ? cachedDetection
					: IntakeEpisodes.detect(p.residualRate, p.minutes, p.motion.reliable,
					p.controlRates, p.noise.automatic, threshold, p.correctedLevels, p::supportsIntakeStep);
			amplitudes = detection.net.clone();
			bottoms = detection.position.clone();
			for (int i = 0; i < bottoms.length; i++) {
				bottoms[i] += p.evaporation[i];
				if (i > 0 && amplitudes[i] > 0)
					amplitudes[i] += p.evaporation[i] - p.evaporation[i - 1];
			}
		} else {
			bottoms = p.raw.clone();
			boolean[] chosen = VerticalCorrelation.candidates(p.residualRate, p.motion.reliable, threshold);
			amplitudes = new double[chosen.length];
			for (int i = 0; i < chosen.length; i++)
				if (chosen[i])
					amplitudes[i] = Math.max(0, p.motion.shift[i]);
		}
		options = settings.options() + ",scale=" + (local ? p.localScale.factor : 1);
	}

	public void install(Capillary cap) {
		cap.getIntakes().set(amplitudes, 0, 0, threshold, manualThreshold);
		cap.getIntakes().setBottomPositions(bottoms);
		cap.getIntakes().setMethodOptions(options);
	}
}
