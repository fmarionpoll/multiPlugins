package plugins.fmp.multicafe.dlg.browse;

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

/**
 * Bulk metadata scan after directory multi-select (Search): batched scan, combo
 * registration, descriptor index preload and descriptor file worker.
 */
final class CafeMetadataScanCoordinator {

	private final LoadSaveExperiment host;

	CafeMetadataScanCoordinator(LoadSaveExperiment host) {
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
		long startTime = System.nanoTime();

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
				long endTime = System.nanoTime();
				SwingUtilities.invokeLater(() -> {
					finishMetadataScanAndUpdateUI(progressFrame, startTime, endTime);
				});
			}
		};

		worker.execute();
	}

	private void processMetadataOnly(ProgressFrame progressFrame) {
		host.lastMetadataScanFailed = false;
		final String subDir = host.parent0.expListComboLazy.expListBinSubDirectory;

		try {
			ExperimentMetadataScanSupport.scanExperimentPaths(host.selectedNames, subDir, progressFrame,
					ExperimentMetadataScanSupport.DEFAULT_BATCH_SIZE,
					ExperimentMetadataScanSupport.DEFAULT_PROGRESS_INTERVAL, host.processingCount,
					(fileName, sd, fileIndex) -> processSingleFileMetadataOnly(fileName, sd, fileIndex));

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			host.lastMetadataScanFailed = true;
			Logger.error("Error processing experiment metadata: " + e.getMessage(), e);
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage("Error: " + e.getMessage());
			});
		}
	}

	private void processSingleFileMetadataOnly(String fileName, String subDir, int fileIndex) {
		try {
			ExperimentDirectories expDirectories = new ExperimentDirectories();

			if (expDirectories.getDirectoriesFromExptPath(subDir, fileName)) {
				String camDataImagesDirectory = expDirectories.getCameraImagesDirectory();
				String resultsDirectory = expDirectories.getResultsDirectory();
				ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
				host.experimentMetadataList.add(metadata);
			}

		} catch (Exception e) {
			Logger.warn("Failed to process metadata for file [" + fileIndex + "] " + fileName + ": " + e.getMessage(),
					e);
		}
	}

	private void finishMetadataScanAndUpdateUI(ProgressFrame progressFrame, long startTime, long endTime) {
		try {
			if (host.lastMetadataScanFailed) {
				progressFrame.close();
				host.selectedNames.clear();
				host.isProcessing = false;
				host.updateBrowseInterface();
				return;
			}
			if (host.experimentMetadataList.isEmpty()) {
				progressFrame.close();
				host.selectedNames.clear();
				host.isProcessing = false;
				Logger.debug("LoadExperiment: processSelectedFilesMetadataOnly took " + (endTime - startTime) / 1e6
						+ " ms (no new experiments)");
				host.updateBrowseInterface();
				return;
			}

			progressFrame.setMessage("Adding experiments to list...");
			progressFrame.setPosition(0);

			List<LazyExperiment> lazyExperiments = new ArrayList<>();
			for (ExperimentMetadata metadata : host.experimentMetadataList) {
				lazyExperiments.add(new LazyExperiment(metadata));
			}

			host.parent0.expListComboLazy.addLazyExperimentsBulk(lazyExperiments);
			host.parent0.paneExperiment.tabInfos.initCombos();

			host.parent0.descriptorIndex.preloadFromCombo(host.parent0.expListComboLazy, new Runnable() {
				@Override
				public void run() {
					host.parent0.paneExperiment.tabInfos.initCombos();
					host.parent0.paneExperiment.tabFilter.initCombos();
					runDescriptorsFileWorker(progressFrame, startTime, endTime);
				}
			}, progressFrame);

		} catch (Exception e) {
			Logger.warn("Error adding metadata to UI: " + e.getMessage(), e);
			progressFrame.close();
			host.selectedNames.clear();
			host.isProcessing = false;
			host.updateBrowseInterface();
		}
	}

	private void runDescriptorsFileWorker(ProgressFrame progressFrame, long scanStartTime, long scanEndTime) {
		final int n = host.parent0.expListComboLazy.getItemCount();
		new SwingWorker<Void, Integer>() {
			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < n; i++) {
					Experiment exp = host.parent0.expListComboLazy.getItemAtNoLoad(i);
					if (exp == null) {
						continue;
					}
					String path = DescriptorsIO.getDescriptorsFullName(exp.getResultsDirectory());
					if (!new java.io.File(path).exists()) {
						DescriptorsIO.buildFromExperiment(exp);
					}
					publish(i);
				}
				return null;
			}

			@Override
			protected void process(java.util.List<Integer> chunks) {
				if (progressFrame == null || chunks.isEmpty()) {
					return;
				}
				int i = chunks.get(chunks.size() - 1);
				progressFrame.setMessage(String.format("Building descriptor cache %d / %d", i + 1, n));
				progressFrame.setPosition((double) (i + 1) / Math.max(1, n));
			}

			@Override
			protected void done() {
				progressFrame.close();
				host.selectedNames.clear();
				host.isProcessing = false;
				long totalEnd = System.nanoTime();
				Logger.debug(
						"LoadExperiment: processSelectedFilesMetadataOnly total " + (totalEnd - scanStartTime) / 1e6
								+ " ms (scan phase " + (scanEndTime - scanStartTime) / 1e6 + " ms)");
				host.updateBrowseInterface();
			}
		}.execute();
	}
}
