package plugins.fmp.multitools.experiment.ui;

import java.util.function.Supplier;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.tools.Logger;

/**
 * Shared experiment load coordination: selection checks, loading flags, lazy/XML
 * metadata load — used by browse dialogs before plugin-specific image/dialog steps.
 */
public final class ExperimentLoadLifecycle {

	public volatile Experiment currentlyLoadingExperiment;
	public volatile int currentlyLoadingIndex = -1;

	public void initializeLoad(Experiment exp, int expIndex) {
		exp.setLoading(true);
		currentlyLoadingExperiment = exp;
		currentlyLoadingIndex = expIndex;
	}

	public boolean validateStillSelected(Experiment exp, int expIndex, ProgressFrame progressFrame,
			Supplier<Experiment> getSelectedExperiment) {
		if (getSelectedExperiment.get() != exp) {
			Logger.info("Skipping load for experiment [" + expIndex + "] - no longer selected");
			return false;
		}
		if (exp.isSaving()) {
			Logger.warn("Cannot load experiment [" + expIndex + "] - save operation in progress: " + exp.toString());
			progressFrame.close();
			return false;
		}
		return true;
	}

	public boolean abortLoad(Experiment exp, int expIndex, ProgressFrame progressFrame, String reason) {
		Logger.info("Aborting load for experiment [" + expIndex + "] - " + reason);
		exp.setLoading(false);
		if (currentlyLoadingExperiment == exp) {
			currentlyLoadingExperiment = null;
			currentlyLoadingIndex = -1;
		}
		progressFrame.close();
		return false;
	}

	public boolean loadExperimentMetadata(Experiment exp, int expIndex, ProgressFrame progressFrame,
			Supplier<Experiment> getSelectedExperiment) {
		if (exp instanceof LazyExperiment) {
			progressFrame.setMessage("Loading experiment metadata...");
			((LazyExperiment) exp).loadIfNeeded();
		} else {
			exp.xmlLoad_MCExperiment();
		}
		if (getSelectedExperiment.get() != exp) {
			return abortLoad(exp, expIndex, progressFrame, "different experiment selected after lazy load");
		}
		return true;
	}

	public void clearLoadingIfCurrent(Experiment exp) {
		exp.setLoading(false);
		if (currentlyLoadingExperiment == exp) {
			currentlyLoadingExperiment = null;
			currentlyLoadingIndex = -1;
		}
	}
}
