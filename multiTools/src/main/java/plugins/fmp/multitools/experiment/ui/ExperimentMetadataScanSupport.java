package plugins.fmp.multitools.experiment.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;

/**
 * Shared batched directory scan loop used when bulk-adding experiments from a
 * directory listing (progress UI + throttle).
 */
public final class ExperimentMetadataScanSupport {

	public static final int DEFAULT_BATCH_SIZE = 20;
	public static final int DEFAULT_PROGRESS_INTERVAL = 10;

	private ExperimentMetadataScanSupport() {
	}

	@FunctionalInterface
	public interface FileScanConsumer {
		void accept(String fileName, String subDir, int fileIndex);
	}

	public static void scanExperimentPaths(List<String> selectedNames, String subDir, ProgressFrame progressFrame,
			int batchSize, int progressInterval, AtomicInteger processingCount, FileScanConsumer perFile)
			throws InterruptedException {
		final int totalFiles = selectedNames.size();
		for (int i = 0; i < totalFiles; i += batchSize) {
			int endIndex = Math.min(i + batchSize, totalFiles);

			final int currentBatch = i;
			final int currentEndIndex = endIndex;
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage(String.format("Scanning experiments %d-%d of %d", currentBatch + 1,
						currentEndIndex, totalFiles));
				progressFrame.setPosition((double) currentBatch / totalFiles);
			});

			for (int j = i; j < endIndex; j++) {
				perFile.accept(selectedNames.get(j), subDir, j);
				processingCount.incrementAndGet();

				if (j % progressInterval == 0) {
					final int currentProgress = j;
					SwingUtilities.invokeLater(() -> {
						progressFrame.setMessage(String.format("Found %d experiments...", currentProgress + 1));
					});
				}

				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}
}
