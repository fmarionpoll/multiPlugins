package plugins.fmp.multiSPOTS96.dlg.browse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multitools.experiment.ui.ExperimentMetadataScanSupport;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;

final class Spots96MetadataScanCoordinator {

	private final LoadSaveExperiment host;

	Spots96MetadataScanCoordinator(LoadSaveExperiment host) {
		this.host = host;
	}

	void onPropertyChangeSelectClosed() {
		if (host.selectedNames.size() < 1) {
			return;
		}
		if (host.isProcessing) {
			Logger.warn("File processing already in progress, ignoring new request");
			return;
		}
		processSelectedFilesMetadataOnly();
	}

	private void processSelectedFilesMetadataOnly() {
		host.isProcessing = true;
		host.processingCount.set(0);
		host.experimentMetadataList.clear();

		ProgressFrame progressFrame = new ProgressFrame("Processing Experiment Metadata");
		progressFrame.setMessage("Scanning " + host.selectedNames.size() + " experiment directories...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				processMetadataOnly(progressFrame);
				return null;
			}

			@Override
			protected void done() {
				host.isProcessing = false;
				progressFrame.close();
				SwingUtilities.invokeLater(() -> {
					host.updateBrowseInterface();
				});
			}
		};

		worker.execute();
	}

	private void processMetadataOnly(ProgressFrame progressFrame) {
		final String subDir = host.parent0.expListComboLazy.expListBinSubDirectory;

		try {
			ExperimentMetadataScanSupport.scanExperimentPaths(host.selectedNames, subDir, progressFrame,
					ExperimentMetadataScanSupport.DEFAULT_BATCH_SIZE,
					ExperimentMetadataScanSupport.DEFAULT_PROGRESS_INTERVAL, host.processingCount,
					(fileName, sd, fileIndex) -> processSingleFileMetadataOnly(fileName, sd));

			SwingUtilities.invokeLater(() -> {
				addMetadataToUI();
			});

			host.selectedNames.clear();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			Logger.error("Error processing experiment metadata: " + e.getMessage());
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage("Error: " + e.getMessage());
			});
		}
	}

	private void processSingleFileMetadataOnly(String fileName, String subDir) {
		try {
			ExperimentDirectories expDirectories = new ExperimentDirectories();

			if (expDirectories.getDirectoriesFromExptPath(subDir, fileName)) {
				String camDataImagesDirectory = expDirectories.getCameraImagesDirectory();
				String resultsDirectory = expDirectories.getResultsDirectory();
				ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
				host.experimentMetadataList.add(metadata);
			}

		} catch (Exception e) {
			Logger.warn("Failed to process metadata for file " + fileName + ": " + e.getMessage());
		}
	}

	private void addMetadataToUI() {
		try {
			List<LazyExperiment> lazyExperiments = new ArrayList<>();
			for (ExperimentMetadata metadata : host.experimentMetadataList) {
				LazyExperiment lazyExp = new LazyExperiment(metadata);
				lazyExperiments.add(lazyExp);
			}

			host.parent0.expListComboLazy.addLazyExperimentsBulk(lazyExperiments);
			host.parent0.dlgExperiment.tabInfos.initCombos();

			host.parent0.descriptorIndex.preloadFromCombo(host.parent0.expListComboLazy, new Runnable() {
				@Override
				public void run() {
					host.parent0.dlgExperiment.tabInfos.initCombos();
					host.parent0.dlgExperiment.tabFilter.initCombos();
				}
			});

			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					for (int i = 0; i < host.parent0.expListComboLazy.getItemCount(); i++) {
						Experiment exp = host.parent0.expListComboLazy.getItemAtNoLoad(i);
						String path = DescriptorsIO.getDescriptorsFullName(exp.getResultsDirectory());
						File f = new File(path);
						if (!f.exists()) {
							DescriptorsIO.buildFromExperiment(exp);
						}
					}
					return null;
				}
			}.execute();

		} catch (Exception e) {
			Logger.warn("Error adding metadata to UI: " + e.getMessage());
		}
	}
}
