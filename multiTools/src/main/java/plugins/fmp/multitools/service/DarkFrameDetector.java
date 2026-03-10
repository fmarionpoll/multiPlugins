package plugins.fmp.multitools.service;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

/**
 * Detects dark intervals in camera sequences by measuring light level inside a
 * small rectangular ROI for each frame.
 *
 * The result is a per-frame light status array aligned with the camera frames:
 * value 1 means \"light\", 0 means \"dark\".
 */
public class DarkFrameDetector {

	public static class Options {
		public ROI2DRectangle rectMonitor = new ROI2DRectangle(100., 100., 200., 150.);
		public int roiX = 0;
		public int roiY = 0;
		public int roiWidth = 100;
		public int roiHeight = 50;
		public long thresholdSum = 0L;
		/**
		 * Optional threshold on the mean intensity per pixel inside the ROI.
		 * <p>
		 * If &gt; 0, this value is used in priority over {@link #thresholdSum} so that
		 * thresholding is independent of ROI size.
		 */
		public double thresholdMean = 20.0;

		public Options() {
		}
	}

	public int[] runDetection(Experiment exp, Options options) {
		if (exp == null || exp.getSeqCamData() == null) {
			Logger.warn("DarkFrameDetector: experiment or camera sequence is null");
			return null;
		}
		if (options == null) {
			options = new Options();
		}

		SequenceCamData seqCam = exp.getSeqCamData();
		IcyBufferedImage reference = seqCam.getReferenceImage();
		if (reference == null && seqCam.getSequence() != null && seqCam.getSequence().getSizeT() > 0) {
			reference = seqCam.getSequence().getFirstImage();
		}
		if (reference == null) {
			Logger.warn("DarkFrameDetector: no reference image available");
			return null;
		}

		int imageWidth = reference.getSizeX();
		int imageHeight = reference.getSizeY();

		int roiX = Math.max(0, options.roiX);
		int roiY = Math.max(0, options.roiY);
		int roiWidth = Math.max(1, options.roiWidth);
		int roiHeight = Math.max(1, options.roiHeight);

		if (roiX + roiWidth > imageWidth) {
			roiWidth = imageWidth - roiX;
		}
		if (roiY + roiHeight > imageHeight) {
			roiHeight = imageHeight - roiY;
		}

		int nFrames = seqCam.getSequence() != null ? seqCam.getSequence().getSizeT() : 0;
		if (nFrames <= 0) {
			Logger.warn("DarkFrameDetector: sequence has no frames");
			return null;
		}

		int[] lightStatus = new int[nFrames];
		long[] sumPerFrame = new long[nFrames];
		long minSum = Long.MAX_VALUE;
		long maxSum = Long.MIN_VALUE;
		double minMean = Double.POSITIVE_INFINITY;
		double maxMean = Double.NEGATIVE_INFINITY;

		int roiArea = roiWidth * roiHeight;
		if (roiArea <= 0) {
			Logger.warn("DarkFrameDetector: ROI area is zero or negative, aborting detection");
			return null;
		}

		for (int t = 0; t < nFrames; t++) {
			IcyBufferedImage image = seqCam.getSeqImage(t, 0);
			if (image == null) {
				lightStatus[t] = 0;
				sumPerFrame[t] = 0L;
				continue;
			}

			long sum = computeSumInROI(image, roiX, roiY, roiWidth, roiHeight);
			sumPerFrame[t] = sum;
			double mean = (double) sum / roiArea;
			if (sum < minSum)
				minSum = sum;
			if (sum > maxSum)
				maxSum = sum;
			if (mean < minMean)
				minMean = mean;
			if (mean > maxMean)
				maxMean = mean;

			if (options.thresholdMean > 0.0) {
				lightStatus[t] = (mean >= options.thresholdMean) ? 1 : 0;
			} else if (options.thresholdSum > 0L) {
				lightStatus[t] = (sum >= options.thresholdSum) ? 1 : 0;
			} else {
				// If no threshold is provided, keep all as light so that
				// the caller can inspect sums / means and choose a threshold.
				lightStatus[t] = 1;
			}
		}

		seqCam.setLightStatusPerFrame(lightStatus);
		if (nFrames > 0) {
			Logger.info("DarkFrameDetector: ROI sum per frame, min=" + minSum + ", max=" + maxSum
					+ " ; mean per pixel, min=" + minMean + ", max=" + maxMean);
		}
		return lightStatus;
	}

	private long computeSumInROI(IcyBufferedImage image, int x, int y, int width, int height) {
		int sizeX = image.getSizeX();
		int sizeY = image.getSizeY();

		if (x < 0 || y < 0 || x + width > sizeX || y + height > sizeY) {
			return 0L;
		}

		Object data = image.getDataXY(0);
		int[] arr = Array1DUtil.arrayToIntArray(data, image.isSignedDataType());

		long sum = 0L;
		for (int iy = y; iy < y + height; iy++) {
			int offset = iy * sizeX;
			for (int ix = x; ix < x + width; ix++) {
				int v = arr[offset + ix];
				if (v > 0) {
					sum += (v & 0xFFFF);
				}
			}
		}
		return sum;
	}
}
