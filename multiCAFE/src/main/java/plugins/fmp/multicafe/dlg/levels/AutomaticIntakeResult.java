package plugins.fmp.multicafe.dlg.levels;

import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.VerticalCorrelation;

/** Amplitudes and fitted geometry for one capillary at one threshold. */
public final class AutomaticIntakeResult {
	final CorrelationGulpPreview preview;
	final double[] amplitudes, bottoms;
	final String options;

	public AutomaticIntakeResult(CorrelationGulpPreview p, boolean episodes, boolean local) {
		this(p, episodes, local, p.noise.automatic);
	}

	public AutomaticIntakeResult(CorrelationGulpPreview p, boolean episodes, boolean local, double threshold) {
		preview = p;
		if (!Double.isFinite(threshold) || threshold < 0)
			throw new IllegalArgumentException("Detection threshold unavailable");
		if (episodes) {
			IntakeEpisodes.Detection detection = IntakeEpisodes.detect(p.residualRate, p.minutes, p.motion.reliable,
					p.controlRates, p.noise.automatic, threshold, p.correctedLevels, p::supportsIntakeStep);
			amplitudes = detection.net.clone();
			bottoms = detection.position.clone();
			for (int i = 0; i < bottoms.length; i++) {
				bottoms[i] += p.evaporation[i];
				if (i > 0 && amplitudes[i] > 0)
					amplitudes[i] += p.evaporation[i] - p.evaporation[i - 1];
			}
		} else {
			bottoms = null;
			boolean[] chosen = VerticalCorrelation.candidates(p.residualRate, p.motion.reliable, threshold);
			amplitudes = new double[chosen.length];
			for (int i = 0; i < chosen.length; i++)
				if (chosen[i])
					amplitudes[i] = Math.max(0, p.motion.shift[i]);
		}
		options = IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + episodes + ",local=" + local
				+ ",scale=" + (local ? p.localScale.factor : 1);
	}

	public void install(Capillary cap) {
		cap.getCorrelationGulps().set(amplitudes, 0, 0, preview.noise.automatic, false);
		cap.getCorrelationGulps().setBottomPositions(bottoms);
		cap.getCorrelationGulps().setMethodOptions(options);
	}
}
