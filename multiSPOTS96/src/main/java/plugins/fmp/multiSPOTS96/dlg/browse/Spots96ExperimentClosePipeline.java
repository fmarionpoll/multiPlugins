package plugins.fmp.multiSPOTS96.dlg.browse;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;

final class Spots96ExperimentClosePipeline {

	Spots96ExperimentClosePipeline() {
	}

	void closeViewsForCurrentExperiment(Experiment exp) {
		if (exp != null) {
			if (exp.isLoading()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - loading still in progress: "
						+ exp.toString());
				return;
			}

			if (exp.isSaving()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - save operation already in progress: "
						+ exp.toString());
				return;
			}

			exp.setSaving(true);

			try {
				if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
					exp.cleanPreviousDetectedFliesROIs();
				}

				if (exp.getSeqCamData() != null) {
					exp.saveExperimentDescriptors();

					exp.getCages().transferROIsFromSequence(exp.getSeqCamData());
					exp.getCages().getPersistence().saveCages(exp.getCages(), exp.getResultsDirectory(), exp);
					String binDir = exp.getKymosBinFullDirectory();
					if (binDir != null) {
						exp.getCages().getPersistence().saveMeasures(exp.getCages(), binDir);
					}

					exp.save_spots_description_and_measures();

					if (exp.getSeqCamData() != null) {
						DescriptorsIO.buildFromExperiment(exp);
					}
				}
				exp.closeSequences();
			} catch (Exception e) {
				Logger.error("Error in closeViewsForCurrentExperiment: " + e.getMessage(), e);
			} finally {
				exp.setSaving(false);
			}
		}
	}
}
