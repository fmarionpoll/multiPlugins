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
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DArea;

public class DetectFlyTools {
	public List<BooleanMask2D> cageMaskList = new ArrayList<BooleanMask2D>();
	public Rectangle rectangleAllCages = null;
	public BuildSeriesOptions options = null;
	public Cages cages = null;

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
			int len = scoreComponent(mask, prevCenters);
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

	private int scoreComponent(BooleanMask2D mask, List<Point2D> prevCenters) throws InterruptedException {
		int len = mask.getPoints().length;
		if (options.blimitLow && len < options.limitLow)
			return 0;
		if (options.blimitUp && len > options.limitUp)
			return 0;

		int width = mask.bounds.width;
		int height = mask.bounds.height;
		if (width < 1 || height < 1)
			return 0;
		int ratio = width / height;
		if (width < height)
			ratio = height / width;
		if (options.blimitRatio && options.limitRatio > 0 && ratio > options.limitRatio)
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
			if (cage.getProperties().getCageNFlies() < 1)
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
		BooleanMask2D bmask = new BooleanMask2D(img.getBounds(), mask);
		return new ROI2DArea(bmask);
	}

	public List<Rectangle2D> findFlies(IcyBufferedImage workimage, int t) throws InterruptedException {
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
			if (cage.getProperties().getCageNFlies() < 1)
				continue;

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						saveMasksForCage(binarizedImageRoi, cage, t, listRectangles);
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

	private void saveMasksForCage(ROI2DArea binarizedImageRoi, Cage cage, int t, List<Rectangle2D> listRectangles)
			throws InterruptedException {
		List<BooleanMask2D> masks = findBlobMasksForCage(binarizedImageRoi, cage.cageMask2D, cage, t);
		if (masks.isEmpty()) {
			cage.flyPositions.addPositionWithoutRoiArea(t, null);
			return;
		}
		for (BooleanMask2D m : masks) {
			Rectangle2D rect = m.getOptimizedBounds();
			cage.flyPositions.addPositionWithoutRoiArea(t, rect);
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
		BooleanMask2D bmask = new BooleanMask2D(img.getBounds(), mask);
		return new ROI2DArea(bmask);
	}

	public void initParametersForDetection(Experiment exp, BuildSeriesOptions options) {
		this.options = options;
		exp.getCages().detect_nframes = (int) (((exp.getCages().detectLast_Ms - exp.getCages().detectFirst_Ms)
				/ exp.getCages().detectBin_Ms) + 1);
		exp.getCages().clearAllMeasures(options.detectCage);
		cages = exp.getCages();
		cages.computeBooleanMasksForCages();
		rectangleAllCages = null;
		for (Cage cage : cages.cagesList) {
			if (cage.getProperties().getCageNFlies() < 1)
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
