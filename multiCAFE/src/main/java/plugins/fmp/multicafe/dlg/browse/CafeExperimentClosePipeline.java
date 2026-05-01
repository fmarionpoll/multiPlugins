package plugins.fmp.multicafe.dlg.browse;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;

final class CafeExperimentClosePipeline {

	CafeExperimentClosePipeline() {
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

				exp.saveExperimentDescriptors();

				if (exp.getSeqCamData() != null) {
					int capCountBeforeSave = exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : 0;
					Logger.debug(
							"LoadSaveExperiment:closeViewsForCurrentExperiment() About to save capillaries - count="
									+ capCountBeforeSave + ", exp=" + exp.getResultsDirectory());
					exp.save_capillaries_description_and_measures();

					exp.getCages().transferROIsFromSequence(exp.getSeqCamData());

					exp.getCages().getPersistence().saveCages(exp.getCages(), exp.getResultsDirectory(), exp);

					String binDir = exp.getKymosBinFullDirectory();
					if (binDir != null) {
						exp.getCages().getPersistence().saveMeasures(exp.getCages(), binDir);
					}

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
