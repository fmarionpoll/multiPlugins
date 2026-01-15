package plugins.fmp.multiSPOTS.series;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;
import plugins.fmp.multiSPOTS.experiment.cages.CagesArray;
import plugins.kernel.roi.roi2d.ROI2DArea;

public class FlyDetectTools {
	public List<BooleanMask2D> cageMaskList = new ArrayList<BooleanMask2D>();
	public Rectangle rectangleAllCages = null;
	public BuildSeriesOptions options = null;
	public CagesArray cages = null;

	// -----------------------------------------------------

	BooleanMask2D findLargestBlob(ROI2DArea roiAll, BooleanMask2D cageMask) throws InterruptedException {
		if (cageMask == null)
			return null;

		ROI2DArea roi = new ROI2DArea(roiAll.getBooleanMask(true).getIntersection(cageMask));

		// find largest component in the threshold
		int max = 0;
		BooleanMask2D bestMask = null;
		BooleanMask2D roiBooleanMask = roi.getBooleanMask(true);
		for (BooleanMask2D mask : roiBooleanMask.getComponents()) {
			int len = mask.getPoints().length;
			if (options.blimitLow && len < options.limitLow)
				len = 0;
			if (options.blimitUp && len > options.limitUp)
				len = 0;

			// trap condition where only a line is found
			int width = mask.bounds.width;
			int height = mask.bounds.height;
			int ratio = width / height;
			if (width < height)
				ratio = height / width;
			if (ratio > 4)
				len = 0;

			// get largest blob
			if (len > max) {
				bestMask = mask;
				max = len;
			}
		}
		return bestMask;
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
		List<Rectangle2D> listRectangles = new ArrayList<Rectangle2D>(cages.cagesList.size());

		for (Cage cage : cages.cagesList) {
			if (options.detectCage != -1 && cage.getCageNumberInteger() != options.detectCage)
				continue;
			if (cage.cageNFlies < 1)
				continue;

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					BooleanMask2D bestMask = getBestMask(binarizedImageRoi, cage.cageMask2D);
					Rectangle2D rect = saveMask(bestMask, cage, t);
					if (rect != null)
						listRectangles.add(rect);
				}
			}));
		}

		waitDetectCompletion(processor, futures, null);
		processor.shutdown();
		return listRectangles;
	}

	BooleanMask2D getBestMask(ROI2DArea binarizedImageRoi, BooleanMask2D cageMask) {
		BooleanMask2D bestMask = null;
		try {
			bestMask = findLargestBlob(binarizedImageRoi, cageMask);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return bestMask;
	}

	Rectangle2D saveMask(BooleanMask2D bestMask, Cage cage, int t) {
		Rectangle2D rect = null;
		if (bestMask != null)
			rect = bestMask.getOptimizedBounds();
		cage.flyPositions.addPositionWithoutRoiArea(t, rect);
		return rect;
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
			if (cage.cageNFlies < 1)
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
				System.out.println("FlyDetectTools:waitDetectCompletion - Interrupted exception: " + e);
			}
			futuresArray.remove(f);
			frame++;
		}
	}

}
