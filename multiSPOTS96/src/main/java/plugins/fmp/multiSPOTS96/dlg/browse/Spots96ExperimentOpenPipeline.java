package plugins.fmp.multiSPOTS96.dlg.browse;

import java.util.List;

import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.persistence.MigrationTool;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.tools.Logger;

final class Spots96ExperimentOpenPipeline {

	private final LoadSaveExperiment owner;
	private final ExperimentLoadLifecycle lifecycle;

	Spots96ExperimentOpenPipeline(LoadSaveExperiment owner) {
		this.owner = owner;
		this.lifecycle = owner.loadLifecycle;
	}

	boolean openSelectedExperiment(Experiment exp) {
		final long startTime = System.nanoTime();
		int expIndex = owner.parent0.expListComboLazy.getSelectedIndex();

		ProgressFrame progressFrame = new ProgressFrame("Load Experiment Data");
		lifecycle.initializeLoad(exp, expIndex);

		try {
			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame, "different experiment selected");
			}

			if (!lifecycle.loadExperimentMetadata(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return false;
			}

			if (!loadExperimentImages(exp, expIndex, progressFrame)) {
				return false;
			}

			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame,
						"different experiment selected before cage load");
			}

			progressFrame.setMessage("Loading cages and spots...");
			exp.load_cages_description_and_measures();
			exp.load_spots_description_and_measures();

			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame,
						"different experiment selected during cage/spot load");
			}

			owner.parent0.dlgExperiment.updateViewerForSequenceCam(exp);

			owner.parent0.dlgMeasure.tabCharts.displayChartPanels(exp);

			progressFrame.setMessage("Load data: update dialogs");

			owner.parent0.dlgExperiment.updateDialogs(exp);
			owner.parent0.dlgSpots.updateDialogs(exp);

			owner.parent0.dlgExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);

			long endTime = System.nanoTime();
			logCageLoadCompletion(exp, expIndex, startTime, endTime);

			migrateLegacyExperimentInBackground(exp);

			exp.setLoading(false);
			lifecycle.clearLoadingIfCurrent(exp);

			progressFrame.close();
			return true;
		} catch (Exception e) {
			Logger.error("Error opening experiment [" + expIndex + "]: "
					+ (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
			Logger.error("Exception details [" + expIndex + "]: " + e.toString(), e);
			progressFrame.close();

			exp.setLoading(false);
			lifecycle.clearLoadingIfCurrent(exp);

			long endTime = System.nanoTime();
			System.out.println("LoadExperiment: openSelectedExperiment [" + expIndex + "] failed, took "
					+ (endTime - startTime) / 1e6 + " ms");
			return false;
		}
	}

	private void logCageLoadCompletion(Experiment exp, int expIndex, long startTime, long endTime) {
		int cageCount = exp.getCages() != null ? exp.getCages().getCageList().size() : 0;
		System.out.println("LoadExperiment: openSelectedExperiment [" + expIndex + "] load completed, total time: "
				+ (endTime - startTime) / 1e6 + " ms, cages: " + cageCount);
	}

	private boolean loadExperimentImages(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load image");
		List<String> imagesList = ExperimentDirectories.getImagesListFromPathV2(exp.getSeqCamData().getImagesDirectory(),
				"jpg");
		exp.getSeqCamData().loadImageList(imagesList);

		if (owner.parent0.expListComboLazy.getSelectedItem() != exp) {
			return lifecycle.abortLoad(exp, expIndex, progressFrame,
					"different experiment selected after loading images");
		}

		owner.parent0.dlgExperiment.updateViewerForSequenceCam(exp);

		if (owner.parent0.expListComboLazy.getSelectedItem() != exp) {
			return lifecycle.abortLoad(exp, expIndex, progressFrame, "different experiment selected during load");
		}

		if (exp.getSeqCamData() == null) {
			Logger.error("LoadSaveExperiments:openSelectedExperiment() [" + expIndex
					+ "] Error: no jpg files found for this experiment\n");
			progressFrame.close();
			exp.setLoading(false);
			lifecycle.clearLoadingIfCurrent(exp);
			return false;
		}

		if (exp.getSeqCamData().getSequence() != null) {
			exp.getSeqCamData().getSequence().addListener(owner);
		}

		return true;
	}

	private void migrateLegacyExperimentInBackground(Experiment exp) {
		if (!exp.isLegacyExperimentFormat())
			return;
		final String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null)
			return;

		final ProgressFrame migFrame = new ProgressFrame("Upgrade legacy experiment");
		migFrame.setMessage("Upgrading experiment to new CSV format...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				try {
					MigrationTool tool = new MigrationTool();
					boolean migrated = tool.migrateExperiment(exp, resultsDir);
					if (migrated) {
						exp.saveExperimentDescriptors();
						exp.setLegacyExperimentFormat(false);
					}
				} catch (Exception e) {
					Logger.warn("LoadSaveExperiment: Legacy migration failed: " + e.getMessage());
				}
				return null;
			}

			@Override
			protected void done() {
				migFrame.close();
			}
		};

		worker.execute();
	}
}
