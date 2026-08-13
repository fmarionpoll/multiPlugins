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

		List<ScoredMask> scored = new ArrayList<>();
		BooleanMask2D roiBooleanMask = roi.getBooleanMask(true);
		for (BooleanMask2D mask : roiBooleanMask.getComponents()) {
			int len = scoreComponent(mask, prevCenters, cage);
			if (len > 0)
				scored.add(new ScoredMask(mask, len));
		}

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

		return len;
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

}
