package plugins.fmp.multitools.series;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DArea;

public class DetectFlyTools {
	public List<BooleanMask2D> cageMaskList = new ArrayList<BooleanMask2D>();
	public Rectangle rectangleAllCages = null;
	public BuildSeriesOptions options = null;
	public Cages cages = null;
	private Experiment experiment = null;

	/** Fraction of a blob that must differ from the comparison image for the blob to be kept. */
	static final double MIN_BACKGROUND_DIFF_FRACTION = 0.2;

	private int[] currentSamples;
	private int[] comparisonSamples;
	private int sampleWidth;
	private int sampleOriginX;
	private int sampleOriginY;
	private int sampleDelta;
	private boolean sampleTrackWhite;

	private static final class ScoredMask {
		final BooleanMask2D mask;
		final int area;

		ScoredMask(BooleanMask2D mask, int area) {
			this.mask = mask;
			this.area = area;
		}
	}

	// -----------------------------------------------------

	/**
	 * Valid blobs in the cage ROI, sorted by descending pixel count. Count is capped when
	 * {@link BuildSeriesOptions#blimitMaxBlobsPerCage} is true.
	 */
	List<BooleanMask2D> findBlobMasksForCage(ROI2DArea roiAll, BooleanMask2D cageMask, Cage cage, int t)
			throws InterruptedException {
		if (cageMask == null)
			return Collections.emptyList();

		ROI2DArea roi = new ROI2DArea(roiAll.getBooleanMask(true).getIntersection(cageMask));

		List<Point2D> prevCenters = Collections.emptyList();
		if (options.bjitter && t > 0 && cage != null)
			prevCenters = getPreviousFlyCenters(cage, t - 1);

		List<BooleanMask2D> accepted = new ArrayList<>();
		BooleanMask2D roiBooleanMask = roi.getBooleanMask(true);
		for (BooleanMask2D mask : roiBooleanMask.getComponents()) {
			int len = scoreComponent(mask, prevCenters, cage);
			if (len > 0)
				accepted.add(mask);
		}
		accepted = mergeSplitFlyBodies(accepted, options);

		List<ScoredMask> scored = new ArrayList<>();
		for (BooleanMask2D mask : accepted)
			scored.add(new ScoredMask(mask, mask.getNumberOfPoints()));

		scored.sort(Comparator.comparingInt((ScoredMask s) -> s.area).reversed());

		int maxKeep = scored.size();
		if (options.blimitMaxBlobsPerCage) {
			int cap = Math.max(1, options.nFliesPresent);
			maxKeep = Math.min(cap, scored.size());
		}

		List<BooleanMask2D> out = new ArrayList<>(maxKeep);
		for (int i = 0; i < maxKeep; i++)
			out.add(scored.get(i).mask);
		return out;
	}

	private int scoreComponent(BooleanMask2D mask, List<Point2D> prevCenters, Cage cage) throws InterruptedException {
		java.awt.Point[] pts = mask.getPoints();
		int len = pts.length;
		if (options.blimitLow && len < options.limitLow)
			return 0;
		if (options.blimitUp && len > options.limitUp)
			return 0;

		if (options.bexcludeSpotBlobs && blobCenterInSpotRegion(cage, mask))
			return 0;

		double ratio = computeOrientedBoundingBoxAspectRatio(pts);
		if (options.blimitRatio && options.limitRatio > 0 && ratio > (double) options.limitRatio)
			return 0;

		if (len > 0 && options.bjitter && !prevCenters.isEmpty() && options.jitter >= 0) {
			Rectangle2D ob = mask.getOptimizedBounds();
			Point2D cur = new Point2D.Double(ob.getCenterX(), ob.getCenterY());
			double dmin = Double.MAX_VALUE;
			for (Point2D p : prevCenters)
				dmin = Math.min(dmin, cur.distance(p));
			if (dmin > options.jitter)
				return 0;
		}

		if (currentSamples != null && comparisonSamples != null
				&& !passesBackgroundComparison(pts, currentSamples, comparisonSamples, sampleWidth, sampleOriginX,
						sampleOriginY, sampleDelta, sampleTrackWhite))
			return 0;

		return len;
	}

	/**
	 * Samples of the filtered frame and of the comparison background. A later blob test keeps the
	 * whole component when enough pixels differ; it does not erase the pixels that match.
	 *
	 * @return false when the two images do not share a size
	 */
	public boolean setBackgroundComparison(IcyBufferedImage current, IcyBufferedImage comparison, int delta,
			boolean trackWhite) {
		if (current == null || comparison == null || current.getSizeX() != comparison.getSizeX()
				|| current.getSizeY() != comparison.getSizeY() || current.getSizeX() <= 0 || current.getSizeY() <= 0) {
			clearBackgroundComparison();
			return false;
		}
		int channel = options != null ? options.videoChannel : 0;
		sampleWidth = current.getSizeX();
		java.awt.Rectangle bounds = current.getBounds();
		sampleOriginX = bounds == null ? 0 : bounds.x;
		sampleOriginY = bounds == null ? 0 : bounds.y;
		sampleDelta = Math.max(0, delta);
		sampleTrackWhite = trackWhite;
		currentSamples = channelSamples(current, trackWhite, channel);
		comparisonSamples = channelSamples(comparison, trackWhite, channel);
		return currentSamples != null && comparisonSamples != null;
	}

	public void clearBackgroundComparison() {
		currentSamples = null;
		comparisonSamples = null;
	}

	/**
	 * True when at least {@link #MIN_BACKGROUND_DIFF_FRACTION} of the pixels are darker (or brighter,
	 * when {@code trackWhite}) than the comparison image by more than {@code delta}.
	 */
	static boolean passesBackgroundComparison(java.awt.Point[] pts, int[] current, int[] comparison, int width,
			int originX, int originY, int delta, boolean trackWhite) {
		if (current == null || comparison == null)
			return true;
		if (pts == null || pts.length == 0)
			return false;
		int changed = 0;
		int len = pts.length;
		for (int i = 0; i < len; i++) {
			int x = pts[i].x - originX;
			int y = pts[i].y - originY;
			if (x < 0 || y < 0 || x >= width)
				continue;
			int idx = x + y * width;
			if (idx < 0 || idx >= current.length || idx >= comparison.length)
				continue;
			int diff = trackWhite ? current[idx] - comparison[idx] : comparison[idx] - current[idx];
			if (diff > delta)
				changed++;
		}
		return changed >= MIN_BACKGROUND_DIFF_FRACTION * len;
	}

	private static int[] channelSamples(IcyBufferedImage img, boolean trackWhite, int videoChannel) {
		int n = img.getSizeX() * img.getSizeY();
		if (n <= 0 || img.getSizeC() <= 0)
			return null;
		int[] out = new int[n];
		if (trackWhite && img.getSizeC() >= 3) {
			byte[] red = img.getDataXYAsByte(0);
			byte[] green = img.getDataXYAsByte(1);
			byte[] blue = img.getDataXYAsByte(2);
			int len = Math.min(n, Math.min(red.length, Math.min(green.length, blue.length)));
			for (int i = 0; i < len; i++)
				out[i] = ((red[i] & 0xFF) + (green[i] & 0xFF) + (blue[i] & 0xFF)) / 3;
			return out;
		}
		int channel = videoChannel;
		if (channel < 0 || channel >= img.getSizeC())
			channel = 0;
		byte[] values = img.getDataXYAsByte(channel);
		int len = Math.min(n, values.length);
		for (int i = 0; i < len; i++)
			out[i] = values[i] & 0xFF;
		return out;
	}

	private boolean blobCenterInSpotRegion(Cage cage, BooleanMask2D mask) {
		if (experiment == null || experiment.getSpots() == null || cage == null || mask == null)
			return false;
		Rectangle2D bounds = mask.getOptimizedBounds();
		if (bounds == null)
			return false;
		double cx = bounds.getCenterX();
		double cy = bounds.getCenterY();
		for (Spot spot : cage.getSpotList(experiment.getSpots())) {
			if (spot == null || spot.getRoi() == null)
				continue;
			if (spot.getRoi().contains(cx, cy))
				return true;
		}
		return false;
	}

	/**
	 * Returns an orientation-invariant aspect ratio estimate for a blob, based on a
	 * PCA-derived orientation and an oriented bounding box in that frame.
	 * <p>
	 * ratio = max(extentU, extentV) / min(extentU, extentV)
	 * </p>
	 */
	private static double computeOrientedBoundingBoxAspectRatio(java.awt.Point[] pts) {
		if (pts == null || pts.length < 2) {
			return 1.0;
		}
		final int n = pts.length;

		// Compute mean
		double mx = 0.0;
		double my = 0.0;
		for (int i = 0; i < n; i++) {
			mx += pts[i].x;
			my += pts[i].y;
		}
		mx /= n;
		my /= n;

		// Compute covariance matrix entries
		double cxx = 0.0;
		double cxy = 0.0;
		double cyy = 0.0;
		for (int i = 0; i < n; i++) {
			double dx = pts[i].x - mx;
			double dy = pts[i].y - my;
			cxx += dx * dx;
			cxy += dx * dy;
			cyy += dy * dy;
		}

		// Principal axis orientation (angle of the largest eigenvector)
		// theta = 0.5 * atan2(2*cxy, cxx - cyy)
		double theta = 0.5 * Math.atan2(2.0 * cxy, cxx - cyy);
		double cos = Math.cos(theta);
		double sin = Math.sin(theta);

		// Project points onto rotated axes and compute extents.
		double minU = Double.POSITIVE_INFINITY;
		double maxU = Double.NEGATIVE_INFINITY;
		double minV = Double.POSITIVE_INFINITY;
		double maxV = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < n; i++) {
			double dx = pts[i].x - mx;
			double dy = pts[i].y - my;
			double u = dx * cos + dy * sin;
			double v = -dx * sin + dy * cos;

			if (u < minU)
				minU = u;
			if (u > maxU)
				maxU = u;
			if (v < minV)
				minV = v;
			if (v > maxV)
				maxV = v;
		}

		double extentU = maxU - minU;
		double extentV = maxV - minV;
		double minExtent = Math.min(extentU, extentV);
		double maxExtent = Math.max(extentU, extentV);
		if (!(minExtent > 0.0)) {
			return Double.POSITIVE_INFINITY;
		}
		return maxExtent / minExtent;
	}

	private static List<Point2D> getPreviousFlyCenters(Cage cage, int tPrev) {
		List<Point2D> out = new ArrayList<>();
		if (cage == null || cage.flyPositions == null || tPrev < 0)
			return out;
		for (FlyPosition fp : cage.flyPositions.flyPositionList) {
			if (fp.flyIndexT != tPrev)
				continue;
			Rectangle2D r = fp.rectPosition;
			if (r == null || Double.isNaN(r.getX()) || r.getWidth() <= 0 || r.getHeight() <= 0)
				continue;
			out.add(new Point2D.Double(r.getCenterX(), r.getCenterY()));
		}
		return out;
	}

	/**
	 * Union of per-cage blobs after the same rules as {@link #findFlies}; does not write positions.
	 */
	public BooleanMask2D unionFilteredFlyBlobs(IcyBufferedImage negativeImage, int t) throws InterruptedException {
		if (options == null || cages == null || negativeImage == null)
			return null;
		ROI2DArea binarizedImageRoi = binarizeImage(negativeImage, options.threshold);
		java.awt.Rectangle ib = negativeImage.getBounds();
		int w = ib.width;
		int h = ib.height;
		if (w <= 0 || h <= 0)
			return null;
		boolean[] acc = new boolean[w * h];
		for (Cage cage : cages.cagesList) {
			if (options.detectCage != -1 && cage.getProperties().getCageID() != options.detectCage)
				continue;
			for (BooleanMask2D m : findBlobMasksForCage(binarizedImageRoi, cage.cageMask2D, cage, t)) {
				for (java.awt.Point p : m.getPoints()) {
					int x = p.x - ib.x;
					int y = p.y - ib.y;
					if (x >= 0 && x < w && y >= 0 && y < h)
						acc[x + y * w] = true;
				}
			}
		}
		return new BooleanMask2D(ib, acc);
	}

	public ROI2DArea binarizeImage(IcyBufferedImage img, int threshold) {
		if (img == null)
			return null;
		boolean[] mask = new boolean[img.getSizeX() * img.getSizeY()];
		if (options.btrackWhite) {
			byte[] arrayRed = img.getDataXYAsByte(0);
			byte[] arrayGreen = img.getDataXYAsByte(1);
			byte[] arrayBlue = img.getDataXYAsByte(2);
			for (int i = 0; i < arrayRed.length; i++) {
				float r = (arrayRed[i] & 0xFF);
				float g = (arrayGreen[i] & 0xFF);
				float b = (arrayBlue[i] & 0xFF);
				float intensity = (r + g + b) / 3f;
				mask[i] = (intensity) > threshold;
			}
		} else {
			byte[] arrayChan = img.getDataXYAsByte(options.videoChannel);
			for (int i = 0; i < arrayChan.length; i++)
				mask[i] = (((int) arrayChan[i]) & 0xFF) < threshold;
		}
		if (options.bmorphClose) {
			mask = morphClose(mask, img.getSizeX(), img.getSizeY(), options.morphCloseRadius);
		}
		BooleanMask2D bmask = new BooleanMask2D(img.getBounds(), mask);
		return new ROI2DArea(bmask);
	}

	/**
	 * Morphological close: dilate then erode with a 3×3 structuring element, {@code radius} times.
	 * Bridges thin gaps that split one fly into two components.
	 */
	static boolean[] morphClose(boolean[] mask, int width, int height, int radius) {
		if (mask == null || width <= 0 || height <= 0 || mask.length < width * height) {
			return mask;
		}
		int r = radius;
		if (r < 1) {
			r = 1;
		} else if (r > 5) {
			r = 5;
		}
		boolean[] work = mask;
		for (int i = 0; i < r; i++) {
			work = dilate3x3(work, width, height);
		}
		for (int i = 0; i < r; i++) {
			work = erode3x3(work, width, height);
		}
		return work;
	}

	private static boolean[] dilate3x3(boolean[] mask, int w, int h) {
		boolean[] out = new boolean[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (!mask[x + y * w]) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					int ny = y + dy;
					if (ny < 0 || ny >= h) {
						continue;
					}
					for (int dx = -1; dx <= 1; dx++) {
						int nx = x + dx;
						if (nx >= 0 && nx < w) {
							out[nx + ny * w] = true;
						}
					}
				}
			}
		}
		return out;
	}

	private static boolean[] erode3x3(boolean[] mask, int w, int h) {
		boolean[] out = new boolean[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				boolean keep = true;
				outer: for (int dy = -1; dy <= 1; dy++) {
					int ny = y + dy;
					if (ny < 0 || ny >= h) {
						keep = false;
						break;
					}
					for (int dx = -1; dx <= 1; dx++) {
						int nx = x + dx;
						if (nx < 0 || nx >= w || !mask[nx + ny * w]) {
							keep = false;
							break outer;
						}
					}
				}
				out[x + y * w] = keep;
			}
		}
		return out;
	}

	public List<Rectangle2D> findFlies(IcyBufferedImage workimage, int t, int illumPhase) throws InterruptedException {
		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectFlies");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>(cages.cagesList.size());
		futures.clear();

		final ROI2DArea binarizedImageRoi = binarizeImage(workimage, options.threshold);
		final List<Rectangle2D> listRectangles = Collections.synchronizedList(new ArrayList<Rectangle2D>());

		for (Cage cage : cages.cagesList) {
			if (options.detectCage != -1 && cage.getProperties().getCageID() != options.detectCage)
				continue;

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						saveMasksForCage(binarizedImageRoi, cage, t, illumPhase, listRectangles);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}));
		}

		waitDetectCompletion(processor, futures, null);
		processor.shutdown();
		return listRectangles;
	}

	private void saveMasksForCage(ROI2DArea binarizedImageRoi, Cage cage, int t, int illumPhase,
			List<Rectangle2D> listRectangles) throws InterruptedException {
		List<BooleanMask2D> masks = findBlobMasksForCage(binarizedImageRoi, cage.cageMask2D, cage, t);
		if (masks.isEmpty()) {
			cage.flyPositions.addPositionWithoutRoiArea(t, 0, null, illumPhase);
			return;
		}
		cage.flyPositions.nflies = Math.max(cage.flyPositions.nflies, masks.size());
		for (int flyId = 0; flyId < masks.size(); flyId++) {
			BooleanMask2D m = masks.get(flyId);
			Rectangle2D rect = m.getOptimizedBounds();
			cage.flyPositions.addPositionWithoutRoiArea(t, flyId, rect, illumPhase);
			if (rect != null)
				listRectangles.add(rect);
		}
	}

	public ROI2DArea binarizeInvertedImage(IcyBufferedImage img, int threshold) {
		if (img == null)
			return null;
		boolean[] mask = new boolean[img.getSizeX() * img.getSizeY()];
		if (options.btrackWhite) {
			byte[] arrayRed = img.getDataXYAsByte(0);
			byte[] arrayGreen = img.getDataXYAsByte(1);
			byte[] arrayBlue = img.getDataXYAsByte(2);
			for (int i = 0; i < arrayRed.length; i++) {
				float r = (arrayRed[i] & 0xFF);
				float g = (arrayGreen[i] & 0xFF);
				float b = (arrayBlue[i] & 0xFF);
				float intensity = (r + g + b) / 3f;
				mask[i] = (intensity < threshold);
			}
		} else {
			byte[] arrayChan = img.getDataXYAsByte(options.videoChannel);
			for (int i = 0; i < arrayChan.length; i++)
				mask[i] = (((int) arrayChan[i]) & 0xFF) > threshold;
		}
		if (options.bmorphClose) {
			mask = morphClose(mask, img.getSizeX(), img.getSizeY(), options.morphCloseRadius);
		}
		BooleanMask2D bmask = new BooleanMask2D(img.getBounds(), mask);
		return new ROI2DArea(bmask);
	}

	public void initParametersForDetection(Experiment exp, BuildSeriesOptions options) {
		this.options = options;
		this.experiment = exp;
		exp.getCages().detect_nframes = (int) (((exp.getCages().detectLast_Ms - exp.getCages().detectFirst_Ms)
				/ exp.getCages().detectBin_Ms) + 1);
		exp.getCages().clearAllMeasures(options.detectCage);
		cages = exp.getCages();
		cages.computeBooleanMasksForCages();
		rectangleAllCages = null;
		for (Cage cage : cages.cagesList) {
			if (options.detectCage != -1 && cage.getProperties().getCageID() != options.detectCage)
				continue;
			Rectangle rect = cage.getRoi().getBounds();
			if (rectangleAllCages == null)
				rectangleAllCages = new Rectangle(rect);
			else
				rectangleAllCages.add(rect);
		}
	}

	protected void waitDetectCompletion(Processor processor, ArrayList<Future<?>> futuresArray,
			ProgressFrame progressBar) {
		int frame = 1;
		int nframes = futuresArray.size();

		while (!futuresArray.isEmpty()) {
			final Future<?> f = futuresArray.get(futuresArray.size() - 1);
			if (progressBar != null)
				progressBar.setMessage("Analyze frame: " + (frame) + "//" + nframes);
			try {
				f.get();
			} catch (ExecutionException e) {
				System.out
						.println("FlyDetectTools:waitDetectCompletion - frame:" + frame + " Execution exception: " + e);
			} catch (InterruptedException e) {
				Logger.warn("FlyDetectTools:waitDetectCompletion - Interrupted exception: " + e);
			}
			futuresArray.remove(f);
			frame++;
		}
	}

	/**
	 * Joins blobs that are one fly cut by a dark patch in the subtracted background.
	 * Where the previous frame is already as dark as the fly, the body cancels and
	 * connected components return two pieces. Pieces are joined only when they sit
	 * on one body axis: a thin crack, or two short fragments that together match one
	 * fly. Side-by-side flies and two full flies in a row are left apart.
	 */
	static List<BooleanMask2D> mergeSplitFlyBodies(List<BooleanMask2D> blobs, BuildSeriesOptions options)
			throws InterruptedException {
		if (blobs == null || blobs.size() < 2 || options == null)
			return blobs;
		List<BooleanMask2D> current = new ArrayList<>(blobs);
		boolean merged = true;
		while (merged) {
			merged = false;
			int bestI = -1;
			int bestJ = -1;
			double bestGap = Double.MAX_VALUE;
			for (int i = 0; i < current.size(); i++) {
				BlobGeom gi = BlobGeom.of(current.get(i));
				for (int j = i + 1; j < current.size(); j++) {
					BlobGeom gj = BlobGeom.of(current.get(j));
					double gap = axisGap(gi, gj);
					if (!canJoinSplitFly(gi, gj, gap, options) || gap >= bestGap)
						continue;
					bestGap = gap;
					bestI = i;
					bestJ = j;
				}
			}
			if (bestI >= 0) {
				BooleanMask2D joined = bridgeMasks(current.get(bestI), current.get(bestJ));
				current.remove(bestJ);
				current.remove(bestI);
				current.add(joined);
				merged = true;
			}
		}
		return current;
	}

	private static boolean canJoinSplitFly(BlobGeom a, BlobGeom b, double gap, BuildSeriesOptions options) {
		if (a.area < 2 || b.area < 2 || a.major < 1 || b.major < 1)
			return false;
		double thickness = Math.max(1.0, Math.min(a.minor, b.minor));
		if (gap < -thickness)
			return false;

		double shorter = Math.min(a.major, b.major);
		double maxGap = Math.max(8.0, Math.min(shorter, 3.0 * thickness));
		if (gap > maxGap)
			return false;

		boolean aRound = a.major < 1.35 * Math.max(a.minor, 1.0);
		boolean bRound = b.major < 1.35 * Math.max(b.minor, 1.0);
		double dx = b.cx - a.cx;
		double dy = b.cy - a.cy;
		double dist = Math.hypot(dx, dy);
		if (dist >= 1.0) {
			double link = Math.atan2(dy, dx);
			if (!aRound && !bRound && undirectedAngleDiff(a.axis, b.axis) > Math.toRadians(28))
				return false;
			if (!(aRound && bRound) && undirectedAngleDiff(link, aRound ? b.axis : a.axis) > Math.toRadians(32))
				return false;
			if (aRound && bRound && gap > 4)
				return false;
		}

		double span = a.major / 2.0 + dist + b.major / 2.0;
		double width = Math.max(1.0, Math.max(a.minor, b.minor));
		double ratio = span / width;
		double maxRatio = 6.0;
		if (options.blimitRatio && options.limitRatio > 0)
			maxRatio = Math.max(options.limitRatio * 1.5, options.limitRatio + 1.0);
		if (ratio > maxRatio)
			return false;
		if (options.blimitUp && options.limitUp > 0 && (a.area + b.area) > options.limitUp)
			return false;

		double aspectA = a.major / Math.max(a.minor, 1.0);
		double aspectB = b.major / Math.max(b.minor, 1.0);
		boolean thinCrack = gap <= Math.max(3.0, 0.5 * thickness);
		boolean stubs = aspectA < 2.8 && aspectB < 2.8 && ratio >= Math.max(aspectA, aspectB) + 0.35;
		return thinCrack || stubs;
	}

	private static double axisGap(BlobGeom a, BlobGeom b) {
		double dist = Math.hypot(b.cx - a.cx, b.cy - a.cy);
		return dist - (a.major + 1.0) / 2.0 - (b.major + 1.0) / 2.0;
	}

	private static double undirectedAngleDiff(double a, double b) {
		double d = Math.abs(a - b) % Math.PI;
		if (d > Math.PI / 2.0)
			d = Math.PI - d;
		return d;
	}

	private static BooleanMask2D bridgeMasks(BooleanMask2D a, BooleanMask2D b) throws InterruptedException {
		BooleanMask2D union = a.getUnion(b);
		int gap = chebyshevGap(a, b);
		int radius = Math.max(1, (gap + 1) / 2);
		if (radius > 8)
			radius = 8;
		Rectangle bounds = union.bounds;
		int pad = radius;
		int w = bounds.width + 2 * pad;
		int h = bounds.height + 2 * pad;
		boolean[] padded = new boolean[w * h];
		for (int y = 0; y < bounds.height; y++) {
			int row = y * bounds.width;
			for (int x = 0; x < bounds.width; x++) {
				if (union.mask[row + x])
					padded[(x + pad) + (y + pad) * w] = true;
			}
		}
		boolean[] closed = morphClose(padded, w, h, radius);
		BooleanMask2D result = new BooleanMask2D(new Rectangle(bounds.x - pad, bounds.y - pad, w, h), closed);
		result.optimizeBounds();
		return result;
	}

	private static int chebyshevGap(BooleanMask2D a, BooleanMask2D b) throws InterruptedException {
		java.awt.Point[] pa = a.getPoints();
		java.awt.Point[] pb = b.getPoints();
		int min = Integer.MAX_VALUE;
		for (int i = 0; i < pa.length; i++) {
			for (int j = 0; j < pb.length; j++) {
				int d = Math.max(Math.abs(pa[i].x - pb[j].x), Math.abs(pa[i].y - pb[j].y));
				if (d < min)
					min = d;
				if (min <= 1)
					return min;
			}
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	private static final class BlobGeom {
		final int area;
		final double cx;
		final double cy;
		final double axis;
		final double major;
		final double minor;

		private BlobGeom(int area, double cx, double cy, double axis, double major, double minor) {
			this.area = area;
			this.cx = cx;
			this.cy = cy;
			this.axis = axis;
			this.major = major;
			this.minor = minor;
		}

		static BlobGeom of(BooleanMask2D mask) throws InterruptedException {
			java.awt.Point[] pts = mask.getPoints();
			int n = pts == null ? 0 : pts.length;
			if (n == 0)
				return new BlobGeom(0, 0, 0, 0, 0, 0);
			double mx = 0;
			double my = 0;
			for (int i = 0; i < n; i++) {
				mx += pts[i].x;
				my += pts[i].y;
			}
			mx /= n;
			my /= n;
			double cxx = 0;
			double cxy = 0;
			double cyy = 0;
			for (int i = 0; i < n; i++) {
				double dx = pts[i].x - mx;
				double dy = pts[i].y - my;
				cxx += dx * dx;
				cxy += dx * dy;
				cyy += dy * dy;
			}
			double theta = 0.5 * Math.atan2(2.0 * cxy, cxx - cyy);
			double cos = Math.cos(theta);
			double sin = Math.sin(theta);
			double minU = Double.POSITIVE_INFINITY;
			double maxU = Double.NEGATIVE_INFINITY;
			double minV = Double.POSITIVE_INFINITY;
			double maxV = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < n; i++) {
				double dx = pts[i].x - mx;
				double dy = pts[i].y - my;
				double u = dx * cos + dy * sin;
				double v = -dx * sin + dy * cos;
				if (u < minU)
					minU = u;
				if (u > maxU)
					maxU = u;
				if (v < minV)
					minV = v;
				if (v > maxV)
					maxV = v;
			}
			double extentU = maxU - minU;
			double extentV = maxV - minV;
			double major = Math.max(extentU, extentV);
			double minor = Math.min(extentU, extentV);
			if (extentV > extentU)
				theta += Math.PI / 2.0;
			return new BlobGeom(n, mx, my, theta, major, minor);
		}
	}

}
