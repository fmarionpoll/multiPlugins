package plugins.fmp.multiSPOTS.dlg.browse;

import java.io.File;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.BinDirectoryResolver;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.KymographKind;
import plugins.fmp.multitools.experiment.persistence.MigrationTool;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.tools.Logger;

final class ExperimentOpenPipeline {

	private final LoadSaveExperiment owner;
	private final ExperimentLoadLifecycle lifecycle;

	ExperimentOpenPipeline(LoadSaveExperiment owner) {
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

			// Must run before load_cages(): ensureBinDirectoryForLoading() (inside cage load) uses
			// camImageBin_ms from file timestamps — same ordering issue as multiCAFE's pipeline
			// before bin resolution.
			progressFrame.setMessage("Reading camera timestamps...");
			exp.getFileIntervalsFromSeqCamData();

			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame,
						"different experiment selected before cage load");
			}

			progressFrame.setMessage("Loading cages...");
			exp.load_cages_description_and_measures();

			progressFrame.setMessage("Selecting analysis bin...");
			String selectedBinDir = selectBinDirectory(exp);
			if (selectedBinDir != null) {
				exp.setBinSubDirectory(selectedBinDir);
				owner.parent0.expListComboLazy.expListBinSubDirectory = selectedBinDir;
			} else if (owner.parent0.expListComboLazy.expListBinSubDirectory != null) {
				exp.setBinSubDirectory(owner.parent0.expListComboLazy.expListBinSubDirectory);
			}

			progressFrame.setMessage("Resolving cage kymograph bin on disk...");
			if (exp.adoptBinSubdirectoryContainingCageKymographTiffs()) {
				String adopted = exp.getBinSubDirectory();
				if (adopted != null) {
					owner.parent0.expListComboLazy.expListBinSubDirectory = adopted;
				}
			}

			progressFrame.setMessage("Loading spots...");
			exp.load_spots_description_and_measures();

			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame,
						"different experiment selected during cage/spot load");
			}

			owner.parent0.dlgExperiment.updateViewerForSequenceCam(exp);

			displayGraphsIfEnabled(exp);

			if (owner.parent0.viewOptions.isAutoLoadKymographs()) {
				progressFrame.setMessage("Load cage kymographs...");
				String kymoBin = exp.getKymosBinFullDirectory();
				if (kymoBin != null) {
					exp.setKymographKind(KymographKind.CAGE_STACKED_TIFF);
					// Drop any stale seqKymos from a previously selected experiment (closeSequences()
					// closes pixels but does not clear the field).
					exp.releaseKymographSequence();
					if (exp.isCageKymographDiskRewriteInProgress()) {
						Logger.debug(
								"ExperimentOpenPipeline: skip cage kymograph load (disk rewrite in progress)");
					} else if (!exp.loadCageSpotKymographs()) {
						Logger.warn("ExperimentOpenPipeline: loadCageSpotKymographs returned false for bin "
								+ kymoBin + " (no kymocage_*.tif* or load error — see Experiment logs)");
					} else if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
						exp.getSeqKymos().getSequence().addListener(owner);
						SwingUtilities.invokeLater(() -> owner.parent0.openCageKymographViewer(exp));
					}
				} else {
					Logger.warn("ExperimentOpenPipeline: skip cage kymographs (kymographs bin path is null)");
				}
			}

			progressFrame.setMessage("Load data: update dialogs");

			owner.parent0.dlgExperiment.updateDialogs(exp);
			owner.parent0.dlgSpots.updateDialogs(exp);
			owner.parent0.dlgKymos.updateDialogs(exp);

			owner.parent0.dlgExperiment.infosPanel.transferPreviousExperimentInfosToDialog(exp, exp);

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

	private void displayGraphsIfEnabled(Experiment exp) {
		if (owner.parent0.viewOptions.isAutoGraphSpotMeasures()
				&& owner.parent0.dlgMeasure != null && owner.parent0.dlgMeasure.chartsPanel != null) {
			owner.parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
		}
		if (owner.parent0.viewOptions.isAutoGraphKymoMeasures()
				&& owner.parent0.dlgKymos != null && owner.parent0.dlgKymos.tabKymoGraph != null) {
			owner.parent0.dlgKymos.tabKymoGraph.displayChartsOnExperimentOpen();
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

	private String selectBinDirectory(Experiment exp) {
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null) {
			return null;
		}
		File resultsDirFile = new File(resultsDir);
		if (!resultsDirFile.exists() || !resultsDirFile.isDirectory()) {
			return null;
		}

		String previousBinDir = owner.parent0.expListComboLazy.expListBinSubDirectory;

		BinDirectoryResolver.Context ctx = new BinDirectoryResolver.Context();
		ctx.resultsDirectory = resultsDir;
		ctx.detectedIntervalMs = exp.getCamImageBin_ms() > 0 ? exp.getCamImageBin_ms() : exp.getKymoBin_ms();
		ctx.nominalIntervalSec = exp.getNominalIntervalSec();
		ctx.previouslySelected = previousBinDir;
		ctx.allowPrompt = false;
		ctx.allowCleanup = true;
		ctx.useSessionRemembered = true;
		ctx.parentForDialog = owner;

		return BinDirectoryResolver.resolveBinSubdirectory(ctx);
	}
}
