package plugins.fmp.multitools.experiment.ui.host;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.GenerationMode;

/**
 * Host contract for {@link plugins.fmp.multitools.experiment.ui.IntervalsPanel}.
 * Encapsulates plugin-specific experiment reload / UI refresh after interval
 * edits and access to view-option defaults.
 */
public interface IntervalsHost extends DialogHost {

	int getDefaultNominalIntervalSec();

	void setDefaultNominalIntervalSec(int sec);

	void saveViewOptions();

	/**
	 * Called after Apply has updated {@link Experiment} fields and closed the
	 * viewer. Implementations reload image lists and refresh plugin UI as
	 * appropriate (multiCAFE vs multiSPOTS96 differ).
	 */
	void onAfterIntervalsApply(Experiment exp);

	/**
	 * Called when the user changes the first-image index spinner (not when
	 * loading from experiment). multiCAFE updates the camera viewer; multiSPOTS96
	 * leaves this as a no-op.
	 */
	default void onFirstImageIndexChanged(Experiment exp) {
	}

	/**
	 * Adjusts the generation mode before building the summary label text.
	 * multiSPOTS96 maps {@link GenerationMode#UNKNOWN} to
	 * {@link GenerationMode#DIRECT_FROM_STACK}; multiCAFE returns the mode
	 * unchanged.
	 */
	default GenerationMode coerceGenerationMode(GenerationMode gm) {
		return gm;
	}
}
