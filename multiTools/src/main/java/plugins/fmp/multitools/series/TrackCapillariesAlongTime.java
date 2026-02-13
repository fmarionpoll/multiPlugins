package plugins.fmp.multitools.series;

import java.awt.geom.Point2D;
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
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;

/**
 * Frame-by-frame capillary tracking. Loads 2 images at a time (t-1 and t), tracks
 * all capillaries for that frame pair, then advances. Uses futures for parallel
 * image loading and parallel per-capillary phase correlation within each frame.
 * When tStart > tEnd, runs backward: seed at tStart, fills from tStart-1 down to tEnd
 * (e.g. after a jump at t, user corrects at t+4 and runs backwards from t+4 to t).
 */
public class TrackCapillariesAlongTime {

	private final CapillaryTracker tracker = new CapillaryTracker();
	private final SequenceLoaderService loadSvc = new SequenceLoaderService();

	public static final double DEFAULT_OUTLIER_MAD_FACTOR = 2.5;
	public static final double DEFAULT_OUTLIER_MIN_PX = 5.0;

	public void run(Experiment exp, int tStart, int tEnd, ProgressReporter progress) {
		run(exp, tStart, tEnd, progress, DEFAULT_OUTLIER_MAD_FACTOR, DEFAULT_OUTLIER_MIN_PX);
	}

	public void run(Experiment exp, int tStart, int tEnd, ProgressReporter progress, double outlierMadFactor, double outlierMinPx) {
		boolean backward = tStart > tEnd;
		int t0 = Math.min(tStart, tEnd);
		int t1 = Math.max(tStart, tEnd);
		double mad = (outlierMadFactor > 0 && !Double.isNaN(outlierMadFactor)) ? outlierMadFactor : DEFAULT_OUTLIER_MAD_FACTOR;
		double minPx = (outlierMinPx >= 0) ? outlierMinPx : DEFAULT_OUTLIER_MIN_PX;
		if (backward)
			runBackward(exp, t1, t0, progress);
		else
			runForward(exp, t0, t1, progress, mad, minPx);
	}

	private void runForward(Experiment exp, int t0, int t1, ProgressReporter progress, double outlierMadFactor, double outlierMinPx) {
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

		List<Integer> indices = indicesWithKymo(caps);
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
				ROI2D[] prevRoi = new ROI2D[caps.size()];
				for (int i : indices)
					prevRoi[i] = currentRoi[i];

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

				List<Integer> outlierIndices = findOutlierDisplacements(indices, prevRoi, currentRoi, outlierMadFactor, outlierMinPx);
				if (!outlierIndices.isEmpty()) {
					int choice = progress.reportOutliers(t, outlierIndices, caps);
					if (choice == ProgressReporter.STOP_TRACKING) {
						for (int i : indices)
							if (results[i] != null)
								results[i].remove(frameT);
						for (int i : indices)
							if (results[i] != null && !results[i].isEmpty())
								capillaries.injectTrackedRoisForCapillary(i, t0, t - 1, results[i]);
						progress.failed("Stopped at frame " + t + ": unusual movement in " + outlierIndices.size() + " capillary/capillaries.");
						exec.shutdown();
						return;
					}
					if (choice == ProgressReporter.SKIP_OUTLIERS_THIS_FRAME) {
						for (int i : outlierIndices) {
							results[i].remove(frameT);
							currentRoi[i] = prevRoi[i];
						}
					}
				}

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

	private void runBackward(Experiment exp, int tSeed, int tTarget, ProgressReporter progress) {
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
		List<Integer> indices = indicesWithKymo(caps);
		if (indices.isEmpty()) {
			progress.updateMessage("No capillaries with kymograph enabled");
			progress.completed();
			return;
		}
		int nFrames = tSeed - tTarget;
		if (nFrames < 1) {
			progress.completed();
			return;
		}

		@SuppressWarnings("unchecked")
		Map<Long, ROI2D>[] results = new Map[caps.size()];
		ROI2D[] currentRoi = new ROI2D[caps.size()];
		for (int i : indices) {
			Capillary cap = caps.get(i);
			AlongT atSeed = cap.getAlongTAtT(tSeed);
			if (atSeed == null || atSeed.getRoi() == null)
				continue;
			ROI2D roiSeed = (ROI2D) atSeed.getRoi().getCopy();
			results[i] = new LinkedHashMap<>();
			results[i].put((long) tSeed, roiSeed);
			currentRoi[i] = roiSeed;
		}

		int nThreads = Math.min(indices.size(), Math.max(1, SystemUtil.getNumberOfCPUs()));
		ExecutorService exec = Executors.newFixedThreadPool(nThreads);
		try {
			for (int t = tSeed - 1; t >= tTarget; t--) {
				if (progress.isCancelled())
					break;
				IcyBufferedImage imgNext = loadImage(seqCamData, t + 1);
				IcyBufferedImage imgCurr = loadImage(seqCamData, t);
				if (imgNext == null || imgCurr == null)
					continue;
				final IcyBufferedImage fp = imgNext;
				final IcyBufferedImage fc = imgCurr;
				final long frameT = t;
				List<CompletableFuture<Void>> tasks = new ArrayList<>();
				for (int i : indices) {
					if (currentRoi[i] == null)
						continue;
					final int capIndex = i;
					ROI2D roiNext = currentRoi[i];
					tasks.add(CompletableFuture.runAsync(() -> {
						ROI2D roiAtT = tracker.trackOneFrame(roiNext, fp, fc);
						if (roiAtT != null) {
							results[capIndex].put(frameT, roiAtT);
							currentRoi[capIndex] = roiAtT;
						}
					}, exec));
				}
				CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
				int frameDone = tSeed - 1 - t;
				progress.updateProgress("Backward " + t + ".." + tSeed, frameDone, nFrames);
			}
			for (int i : indices) {
				if (results[i] != null && !results[i].isEmpty())
					capillaries.injectTrackedRoisForCapillary(i, tTarget, tSeed, results[i]);
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

	/**
	 * Flags capillaries whose displacement magnitude is an outlier vs the rest (median + madFactor*MAD).
	 * Requires at least 3 capillaries; returns empty list if too few or no clear outliers.
	 * Higher madFactor = less stringent (fewer capillaries flagged).
	 */
	private List<Integer> findOutlierDisplacements(List<Integer> indices, ROI2D[] prevRoi, ROI2D[] currentRoi,
			double madFactor, double minPxWhenMadZero) {
		if (indices.size() < 3)
			return new ArrayList<>();
		List<Integer> indexByPos = new ArrayList<>(indices.size());
		List<Double> mags = new ArrayList<>(indices.size());
		for (int i : indices) {
			if (prevRoi[i] == null || currentRoi[i] == null)
				continue;
			Point2D cPrev = ROI2DUtilities.getRoiCentroid(prevRoi[i]);
			Point2D cCurr = ROI2DUtilities.getRoiCentroid(currentRoi[i]);
			if (cPrev == null || cCurr == null)
				continue;
			double dx = cCurr.getX() - cPrev.getX();
			double dy = cCurr.getY() - cPrev.getY();
			indexByPos.add(i);
			mags.add(Math.hypot(dx, dy));
		}
		int n = mags.size();
		if (n < 3)
			return new ArrayList<>();
		double[] a = new double[n];
		for (int j = 0; j < n; j++)
			a[j] = mags.get(j);
		double median = medianOf(a, n);
		for (int j = 0; j < n; j++)
			a[j] = Math.abs(mags.get(j) - median);
		double mad = medianOf(a, n);
		double threshold = median + madFactor * (mad > 0 ? mad : minPxWhenMadZero);
		List<Integer> outliers = new ArrayList<>();
		for (int j = 0; j < n; j++)
			if (mags.get(j) > threshold)
				outliers.add(indexByPos.get(j));
		return outliers;
	}

	private static double medianOf(double[] a, int len) {
		if (len <= 0)
			return 0;
		double[] b = new double[len];
		System.arraycopy(a, 0, b, 0, len);
		java.util.Arrays.sort(b);
		int mid = len / 2;
		return (len % 2 == 1) ? b[mid] : (b[mid - 1] + b[mid]) / 2;
	}

	private List<Integer> indicesWithKymo(List<Capillary> caps) {
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < caps.size(); i++) {
			if (caps.get(i).getKymographBuild())
				indices.add(i);
		}
		return indices;
	}

	private IcyBufferedImage loadImage(SequenceCamData seqCamData, int t) {
		String path = seqCamData.getFileNameFromImageList(t);
		return path != null ? loadSvc.imageIORead(path) : null;
	}
}
