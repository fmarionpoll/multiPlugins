package plugins.fmp.multicafe.dlg.browse;

import java.io.File;

import icy.gui.frame.progress.ProgressFrame;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.Logger;

final class CafeExperimentOpenPipeline {

	private final LoadSaveExperiment owner;
	private final ExperimentLoadLifecycle lifecycle;

	CafeExperimentOpenPipeline(LoadSaveExperiment owner) {
		this.owner = owner;
		this.lifecycle = owner.loadLifecycle;
	}

	boolean openSelectedExperiment(Experiment exp) {
		if (exp == null)
			return false;

		final long startTime = System.nanoTime();
		int expIndex = owner.parent0.expListComboLazy.getSelectedIndex();

		Logger.debug("LoadSaveExperiment:openSelectedExperiment() START - exp="
				+ (exp != null ? exp.getResultsDirectory() : "null") + ", isLazy=" + (exp instanceof LazyExperiment)
				+ ", capillaries.count="
				+ (exp != null && exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : "N/A"));

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

			loadCapillariesData(exp, progressFrame);

			String selectedBinDir = selectBinDirectory(exp);
			if (selectedBinDir != null) {
				exp.setBinSubDirectory(selectedBinDir);
				owner.parent0.expListComboLazy.expListBinSubDirectory = selectedBinDir;
			}

			loadKymographsAndMeasures(exp, selectedBinDir, progressFrame);

			if (!lifecycle.validateStillSelected(exp, expIndex, progressFrame,
					() -> (Experiment) owner.parent0.expListComboLazy.getSelectedItem())) {
				return lifecycle.abortLoad(exp, expIndex, progressFrame,
						"different experiment selected before cage load");
			}

			prepareCageMeasuresFile(exp);

			progressFrame.setMessage("Load cage measures...");
			boolean cagesLoaded = exp.load_cages_description_and_measures();

			if (cagesLoaded && exp.getCapillaries() != null && exp.getCapillaries().getList().size() > 0)
				exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());

			if (!cagesLoaded) {
				Logger.warn("Failed to load cages for experiment [" + expIndex + "]");
			}

			if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
				exp.initTmsForFlyPositions(exp.getCamImageFirst_ms());
			}

			exp.updateROIsAt(0);
			owner.parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);

			displayGraphsIfEnabled(exp);

			progressFrame.setMessage("Load data: update dialogs");

			owner.parent0.paneExperiment.updateDialogs(exp);
			owner.parent0.paneKymos.updateDialogs(exp);
			owner.parent0.paneCapillaries.updateDialogs(exp);

			owner.parent0.paneExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);

			long endTime = System.nanoTime();
			logCageLoadCompletion(exp, expIndex, startTime, endTime);

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
			Logger.warn("LoadExperiment: openSelecteExperiment [" + expIndex + "] failed, took "
					+ (endTime - startTime) / 1e6 + " ms");
			return false;
		}
	}

	private int countCagesWithFlyPositions(Experiment exp) {
		int count = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private void logCageLoadCompletion(Experiment exp, int expIndex, long startTime, long endTime) {
		int cageCount = exp.getCages().getCageList().size();
		int cagesWithFlyPositions = countCagesWithFlyPositions(exp);
		int totalFlyPositions = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				totalFlyPositions += cage.flyPositions.flyPositionList.size();
			}
		}
		int nFrames = 0;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getImageLoader() != null) {
			nFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		}
		Logger.debug("LoadExperiment: openSelectedExperiment [" + expIndex + "] load completed, total time: "
				+ (endTime - startTime) / 1e6 + " ms, cages: " + cageCount + ", with fly positions: "
				+ cagesWithFlyPositions + ", total fly positions: " + totalFlyPositions + ", frames: " + nFrames);
	}

	private boolean loadExperimentImages(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load image");
		exp.getSeqCamData().loadImages();

		ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
		int actualImageCount = imgLoader.getImagesCount();
		int loadedNFrames = imgLoader.getNTotalFrames();
		if (actualImageCount > 0 && loadedNFrames > 0 && actualImageCount != loadedNFrames) {
			long frameFirst = imgLoader.getAbsoluteIndexFirstImage();
			long nImages = actualImageCount + frameFirst;
			imgLoader.setFixedNumberOfImages(nImages);
			imgLoader.setNTotalFrames(actualImageCount);
		}

		if (owner.parent0.expListComboLazy.getSelectedItem() != exp) {
			return lifecycle.abortLoad(exp, expIndex, progressFrame,
					"different experiment selected after loading images");
		}

		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null) {
			Logger.warn("LoadSaveExperiment: Sequence is null after loadImages()");
			return lifecycle.abortLoad(exp, expIndex, progressFrame, "sequence is null after loading images");
		}

		owner.parent0.paneExperiment.updateViewerForSequenceCam(exp);

		owner.parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);

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

	private void loadCapillariesData(Experiment exp, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load capillaries");
		exp.loadCamDataCapillaries();
	}

	private void loadKymographsAndMeasures(Experiment exp, String selectedBinDir, ProgressFrame progressFrame) {
		if (exp.getCapillaries() == null || exp.getCapillaries().getList().size() == 0) {
			return;
		}

		progressFrame.setMessage("Load kymographs");
		if (selectedBinDir != null) {
			owner.parent0.paneKymos.tabLoadSave.loadDefaultKymos(exp);
		}

		progressFrame.setMessage("Load capillary measures");
		if (selectedBinDir != null && exp.getBinSubDirectory() != null) {
			String binFullDir = exp.getKymosBinFullDirectory();
			if (binFullDir != null) {
				exp.load_capillaries_description_and_measures();

				if (exp.getSeqKymos() != null && exp.getCapillaries() != null
						&& exp.getCapillaries().getList().size() > 0) {
					owner.parent0.paneKymos.tabIntervals.transferCapillaryNamesToComboBox(exp);
					owner.parent0.paneKymos.tabIntervals.displayUpdateOnSwingThread();
				}
			}
		}

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().addListener(owner);
		}
	}

	private void displayGraphsIfEnabled(Experiment exp) {
		if (owner.parent0.paneExperiment.tabOptions.graphsCheckBox.isSelected()) {
			owner.parent0.paneLevels.tabGraphs.displayChartPanels(exp);
			owner.parent0.paneCages.tabGraphics.refreshIfDisplayed(exp);
		}
	}

	private void prepareCageMeasuresFile(Experiment exp) {
	}

	private String selectBinDirectory(Experiment exp) {
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null)
			return null;
		File resultsDirFile = new File(resultsDir);
		if (!resultsDirFile.exists() || !resultsDirFile.isDirectory())
			return null;

		boolean isFirstExperiment = (owner.parent0.expListComboLazy.getSelectedIndex() == 0);
		boolean isSingleExperiment = (owner.parent0.expListComboLazy.getItemCount() == 1);
		String previousBinDir = owner.parent0.expListComboLazy.expListBinSubDirectory;

		plugins.fmp.multitools.experiment.BinDirectoryResolver.Context ctx = //
				new plugins.fmp.multitools.experiment.BinDirectoryResolver.Context();
		ctx.resultsDirectory = resultsDir;
		ctx.detectedIntervalMs = exp.getCamImageBin_ms() > 0 ? exp.getCamImageBin_ms() : exp.getKymoBin_ms();
		ctx.nominalIntervalSec = exp.getNominalIntervalSec();
		ctx.previouslySelected = previousBinDir;
		ctx.allowPrompt = (isSingleExperiment || isFirstExperiment);
		ctx.useSessionRemembered = true;
		ctx.parentForDialog = owner;

		return plugins.fmp.multitools.experiment.BinDirectoryResolver.resolve(ctx);
	}
}
