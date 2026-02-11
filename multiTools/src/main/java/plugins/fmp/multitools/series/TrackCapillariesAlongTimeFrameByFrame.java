package plugins.fmp.multitools.series;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.system.SystemUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CapillaryTracker;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.ROI2D.AlongT;

/**
 * Frame-by-frame capillary tracking. Loads 2 images at a time (t-1 and t), tracks
 * all capillaries for that frame pair, then advances. Uses futures for parallel
 * image loading and parallel per-capillary phase correlation within each frame.
 */
public class TrackCapillariesAlongTimeFrameByFrame {

	private final CapillaryTracker tracker = new CapillaryTracker();
	private final SequenceLoaderService loadSvc = new SequenceLoaderService();

	public void run(Experiment exp, int tStart, int tEnd, ProgressReporter progress) {
		Capillaries capillaries = exp.getCapillaries();
		List<Capillary> caps = capillaries.getList();
		if (caps.isEmpty()) {
			progress.completed();
			return;
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null) {
			progress.failed("No sequence data");
			return;
		}

		int t0 = Math.min(tStart, tEnd);
		int t1 = Math.max(tStart, tEnd);

		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < caps.size(); i++) {
			if (caps.get(i).getKymographBuild())
				indices.add(i);
		}
		if (indices.isEmpty()) {
			progress.updateMessage("No capillaries with kymograph enabled");
			progress.completed();
			return;
		}

		int nFrames = t1 - t0;
		if (nFrames < 1) {
			progress.completed();
			return;
		}

		@SuppressWarnings("unchecked")
		Map<Long, ROI2D>[] results = new Map[caps.size()];
		ROI2D[] currentRoi = new ROI2D[caps.size()];

		for (int i : indices) {
			Capillary cap = caps.get(i);
			AlongT at0 = cap.getAlongTAtT(t0);
			if (at0 == null || at0.getRoi() == null)
				continue;
			ROI2D roi0 = (ROI2D) at0.getRoi().getCopy();
			results[i] = new LinkedHashMap<>();
			results[i].put((long) t0, roi0);
			currentRoi[i] = roi0;
		}

		int nThreads = Math.min(indices.size(), Math.max(1, SystemUtil.getNumberOfCPUs()));
		ExecutorService exec = Executors.newFixedThreadPool(nThreads);

		try {
			CompletableFuture<IcyBufferedImage> loadPrev = CompletableFuture
					.supplyAsync(() -> loadImage(seqCamData, t0), exec);
			CompletableFuture<IcyBufferedImage> loadCurr = CompletableFuture
					.supplyAsync(() -> loadImage(seqCamData, t0 + 1), exec);
			IcyBufferedImage imgPrev = loadPrev.join();
			if (imgPrev == null) {
				progress.failed("Cannot load image at t=" + t0);
				return;
			}

			for (int t = t0 + 1; t <= t1; t++) {
				if (progress.isCancelled())
					break;

				IcyBufferedImage imgCurr = (t == t0 + 1) ? loadCurr.join() : loadImage(seqCamData, t);
				if (imgCurr == null)
					continue;

				final IcyBufferedImage fp = imgPrev;
				final IcyBufferedImage fc = imgCurr;
				final long frameT = t;

				List<CompletableFuture<Void>> tasks = new ArrayList<>();
				for (int i : indices) {
					if (currentRoi[i] == null)
						continue;
					final int capIndex = i;
					ROI2D roiPrev = currentRoi[i];
					tasks.add(CompletableFuture.runAsync(() -> {
						ROI2D roiNew = tracker.trackOneFrame(roiPrev, fp, fc);
						if (roiNew != null) {
							results[capIndex].put(frameT, roiNew);
							currentRoi[capIndex] = roiNew;
						}
					}, exec));
				}
				CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

				imgPrev = imgCurr;
				int frameDone = t - t0;
				progress.updateProgress("Frame " + t + "/" + t1, frameDone, nFrames);
			}

			for (int i : indices) {
				if (results[i] != null && !results[i].isEmpty())
					capillaries.injectTrackedRoisForCapillary(i, t0, t1, results[i]);
			}
		} catch (Exception ex) {
			progress.failed(ex.getMessage());
		} finally {
			exec.shutdown();
		}

		if (progress.isCancelled())
			progress.failed("Cancelled");
		else
			progress.completed();
	}

	private IcyBufferedImage loadImage(SequenceCamData seqCamData, int t) {
		String path = seqCamData.getFileNameFromImageList(t);
		return path != null ? loadSvc.imageIORead(path) : null;
	}
}
