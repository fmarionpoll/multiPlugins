package plugins.fmp.multitools.series;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.system.SystemUtil;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CapillaryTracker;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.service.tracking.CapillaryFrameRegistration;
import plugins.fmp.multitools.service.tracking.CapillaryFrameRegistration.Result;
import plugins.fmp.multitools.service.tracking.DirectionalCapillaryTracker;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.TrackedRoisByFrame;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DLine;

/**
 * Anchor-based capillary tracking. Each frame is compared with the selected run
 * start, so sub-pixel gradual motion is not lost by repeated adjacent-frame
 * estimates. Uses parallel per-capillary phase correlation within each frame.
 * When tStart > tEnd, runs backward: seed at tStart, fills from tStart-1 down to tEnd
 * (e.g. after a jump at t, user corrects at t+4 and runs backwards from t+4 to t).
 */
public class TrackCapillariesAlongTime {

	private final CapillaryTracker tracker = new CapillaryTracker();
	private final SequenceLoaderService loadSvc = new SequenceLoaderService();
	private final CapillaryFrameRegistration frameRegistration = new CapillaryFrameRegistration();
	private final DirectionalCapillaryTracker directionalTracker = new DirectionalCapillaryTracker();

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

		TrackedRoisByFrame table = new TrackedRoisByFrame(t0, t1, caps.size());
		for (int i : indices) {
			Capillary cap = caps.get(i);
			AlongT at0 = cap.getAlongTAtT(t0);
			if (at0 == null || at0.getRoi() == null)
				continue;
			ROI2D roi0 = (ROI2D) at0.getRoi().getCopy();
			table.setRoiAt(i, t0, roi0);
		}

		int nThreads = Math.min(indices.size(), Math.max(1, SystemUtil.getNumberOfCPUs()));
		ExecutorService exec = Executors.newFixedThreadPool(nThreads);
		try {
			CompletableFuture<IcyBufferedImage> loadReference = CompletableFuture
					.supplyAsync(() -> loadImage(seqCamData, t0), exec);
			CompletableFuture<IcyBufferedImage> loadCurr = CompletableFuture
					.supplyAsync(() -> loadImage(seqCamData, t0 + 1), exec);
			IcyBufferedImage imgReference = loadReference.join();
			if (imgReference == null) {
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
					prevRoi[i] = table.getRoiAtNoCopy(i, t - 1);

				final IcyBufferedImage fp = imgReference;
				final IcyBufferedImage fc = imgCurr;
				final long frameT = t;
				List<CompletableFuture<Void>> tasks = new ArrayList<>();
				for (int i : indices) {
					ROI2D roiReference = table.getRoiAtNoCopy(i, t0);
					if (roiReference == null)
						continue;
					final int capIndex = i;
					tasks.add(CompletableFuture.runAsync(() -> {
						ROI2D roiNew = tracker.trackOneFrame(roiReference, fp, fc);
						if (roiNew != null)
							table.setRoiAt(capIndex, (int) frameT, roiNew);
					}, exec));
				}
				CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

				ROI2D[] currentRoi = new ROI2D[caps.size()];
				for (int i : indices)
					currentRoi[i] = table.getRoiAtNoCopy(i, frameT);
				List<Integer> outlierIndices = findOutlierDisplacements(indices, prevRoi, currentRoi, outlierMadFactor, outlierMinPx);
				if (!outlierIndices.isEmpty())
					Logger.debug("Local motion outliers at T=" + t + ": " + outlierIndices
							+ " (handled automatically by robust frame registration)");
				applyFrameRegistration(caps, indices, table, t0, t,
						estimateCageMotions(exp, imgReference, imgCurr),
						estimateDirectionalLines(caps, indices, t0, imgReference, imgCurr));

				int frameDone = t - t0;
				progress.updateProgress("Frame " + t + "/" + t1, frameDone, nFrames);
			}
			List<Map<Long, ROI2D>> maps = table.toTrackedMaps();
			for (int i : indices) {
				if (!maps.get(i).isEmpty())
					capillaries.injectTrackedRoisForCapillary(i, t0, t1, maps.get(i));
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

		TrackedRoisByFrame table = new TrackedRoisByFrame(tTarget, tSeed, caps.size());
		for (int i : indices) {
			Capillary cap = caps.get(i);
			AlongT atSeed = cap.getAlongTAtT(tSeed);
			if (atSeed == null || atSeed.getRoi() == null)
				continue;
			ROI2D roiSeed = (ROI2D) atSeed.getRoi().getCopy();
			table.setRoiAt(i, tSeed, roiSeed);
		}

		int nThreads = Math.min(indices.size(), Math.max(1, SystemUtil.getNumberOfCPUs()));
		ExecutorService exec = Executors.newFixedThreadPool(nThreads);
		try {
			IcyBufferedImage imgReference = loadImage(seqCamData, tSeed);
			if (imgReference == null) {
				progress.failed("Cannot load image at t=" + tSeed);
				return;
			}
			for (int t = tSeed - 1; t >= tTarget; t--) {
				if (progress.isCancelled())
					break;
				IcyBufferedImage imgCurr = loadImage(seqCamData, t);
				if (imgCurr == null)
					continue;
				final IcyBufferedImage fp = imgReference;
				final IcyBufferedImage fc = imgCurr;
				final long frameT = t;
				List<CompletableFuture<Void>> tasks = new ArrayList<>();
				for (int i : indices) {
					ROI2D roiReference = table.getRoiAtNoCopy(i, tSeed);
					if (roiReference == null)
						continue;
					final int capIndex = i;
					tasks.add(CompletableFuture.runAsync(() -> {
						ROI2D roiAtT = tracker.trackOneFrame(roiReference, fp, fc);
						if (roiAtT != null)
							table.setRoiAt(capIndex, (int) frameT, roiAtT);
					}, exec));
				}
				CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
				applyFrameRegistration(caps, indices, table, tSeed, t,
						estimateCageMotions(exp, imgReference, imgCurr),
						estimateDirectionalLines(caps, indices, tSeed, imgReference, imgCurr));
				int frameDone = tSeed - 1 - t;
				progress.updateProgress("Backward " + t + ".." + tSeed, frameDone, nFrames);
			}
			List<Map<Long, ROI2D>> maps = table.toTrackedMaps();
			for (int i : indices) {
				if (!maps.get(i).isEmpty())
					capillaries.injectTrackedRoisForCapillary(i, tTarget, tSeed, maps.get(i));
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

	/** Regularizes local translations with one robust transform of the rigid frame. */
	private void applyFrameRegistration(List<Capillary> caps, List<Integer> indices, TrackedRoisByFrame table,
			int sourceT, int targetT, Map<Integer, CageEndpointMotion> cageMotions,
			List<Line2D> directionalLines) {
		List<Line2D> source = new ArrayList<Line2D>(caps.size());
		List<Line2D> locallyTracked = new ArrayList<Line2D>(caps.size());
		for (int i = 0; i < caps.size(); i++) {
			source.add(null);
			locallyTracked.add(null);
		}
		for (int i : indices) {
			ROI2D roiBefore = table.getRoiAtNoCopy(i, sourceT);
			ROI2D roiLocal = table.getRoiAtNoCopy(i, targetT);
			if (!(roiBefore instanceof ROI2DLine) || !(roiLocal instanceof ROI2DLine))
				continue;
			Capillary cap = caps.get(i);
			Line2D physical = cap.getPhaseGeometry().getBlueAt(sourceT);
			if (physical == null)
				physical = ((ROI2DLine) roiBefore).getLine();
			Line2D directional = directionalLines.get(i);
			if (directional != null) {
				source.set(i, physical);
				locallyTracked.set(i, directional);
				continue;
			}
			Point2D beforeCenter = ROI2DUtilities.getRoiCentroid(roiBefore);
			Point2D localCenter = ROI2DUtilities.getRoiCentroid(roiLocal);
			if (beforeCenter == null || localCenter == null)
				continue;
			double dx = localCenter.getX() - beforeCenter.getX();
			double dy = localCenter.getY() - beforeCenter.getY();
			CageEndpointMotion cageMotion = cageMotions.get(cap.getCageID());
			if (cageMotion != null) {
				dx = cageMotion.upper.getX();
				dy = cageMotion.upper.getY();
			}
			source.set(i, physical);
			double dx2 = cageMotion == null ? dx : cageMotion.lower.getX();
			double dy2 = cageMotion == null ? dy : cageMotion.lower.getY();
			locallyTracked.set(i, new Line2D.Double(physical.getX1() + dx, physical.getY1() + dy,
					physical.getX2() + dx2, physical.getY2() + dy2));
		}
		try {
			Result result = frameRegistration.fit(source, locallyTracked);
			for (int i : indices) {
				Line2D registered = result.getRegisteredLines().get(i);
				ROI2D previous = table.getRoiAtNoCopy(i, sourceT);
				if (registered == null || previous == null)
					continue;
				Capillary cap = caps.get(i);
				Line2D green = registered;
				if (cap.getPhaseGeometry().isInitialized()) {
					cap.getPhaseGeometry().putBlue(targetT, registered);
					green = cap.getPhaseGeometry().greenForBlue(registered);
				}
				ROI2DLine roi = new ROI2DLine(green);
				roi.setName(previous.getName());
				roi.setColor(previous.getColor());
				roi.setStroke(previous.getStroke());
				roi.setReadOnly(previous.isReadOnly());
				table.setRoiAt(i, targetT, roi);
			}
			Logger.debug("Frame registration T=" + targetT + " model="
					+ result.getFit().getTransform().getModel() + " rms=" + result.getFit().getRms() + " landmarks="
					+ result.getLandmarkCount());
		} catch (IllegalArgumentException ex) {
			Logger.warn("Frame registration skipped at T=" + targetT + ": " + ex.getMessage());
		}
	}

	private List<Line2D> estimateDirectionalLines(List<Capillary> caps, List<Integer> indices, int sourceT,
			IcyBufferedImage reference, IcyBufferedImage current) {
		List<Line2D> lines = new ArrayList<Line2D>(caps.size());
		for (int i = 0; i < caps.size(); i++) lines.add(null);
		if (reference == null || current == null || reference.getWidth() != current.getWidth()
				|| reference.getHeight() != current.getHeight())
			return lines;
		double[] ref = Array1DUtil.arrayToDoubleArray(reference.getDataXY(0), reference.isSignedDataType());
		double[] cur = Array1DUtil.arrayToDoubleArray(current.getDataXY(0), current.isSignedDataType());
		for (int i : indices) {
			Line2D physical = caps.get(i).getPhaseGeometry().getBlueAt(sourceT);
			if (physical != null)
				lines.set(i, directionalTracker.track(physical, ref, cur, reference.getWidth(), reference.getHeight()));
		}
		return lines;
	}

	/** Motion of each broader cage patch; less ambiguous than a narrow shaft. */
	private Map<Integer, CageEndpointMotion> estimateCageMotions(Experiment exp, IcyBufferedImage reference,
			IcyBufferedImage current) {
		Map<Integer, CageEndpointMotion> motions = new HashMap<Integer, CageEndpointMotion>();
		if (exp == null || exp.getCages() == null || exp.getCages().cagesList == null)
			return motions;
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null || cage.getRoi() == null)
				continue;
			Rectangle b = cage.getRoi().getBounds();
			if (b.width < 20 || b.height < 20)
				continue;
			double inset = Math.min(12, b.width / 5.0);
			ROI2DLine upper = new ROI2DLine(new Line2D.Double(b.x + inset, b.y + 3,
					b.x + b.width - inset, b.y + 3));
			ROI2DLine lower = new ROI2DLine(new Line2D.Double(b.x + inset, b.y + b.height - 4,
					b.x + b.width - inset, b.y + b.height - 4));
			Point2D du = motionOf(upper, reference, current);
			Point2D dl = motionOf(lower, reference, current);
			if (du != null && dl != null)
				motions.put(cage.getCageID(), new CageEndpointMotion(du, dl));
		}
		return motions;
	}

	private Point2D motionOf(ROI2D roi, IcyBufferedImage reference, IcyBufferedImage current) {
		ROI2D moved = tracker.trackOneFrame(roi, reference, current, 14);
		Point2D p0 = ROI2DUtilities.getRoiCentroid(roi);
		Point2D p1 = ROI2DUtilities.getRoiCentroid(moved);
		return p0 == null || p1 == null ? null
				: new Point2D.Double(p1.getX() - p0.getX(), p1.getY() - p0.getY());
	}

	private static final class CageEndpointMotion {
		final Point2D upper;
		final Point2D lower;
		CageEndpointMotion(Point2D upper, Point2D lower) {
			this.upper = upper;
			this.lower = lower;
		}
	}

	private IcyBufferedImage loadImage(SequenceCamData seqCamData, int t) {
		String path = seqCamData.getFileNameFromImageList(t);
		return path != null ? loadSvc.imageIORead(path) : null;
	}
}
