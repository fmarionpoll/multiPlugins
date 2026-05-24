package plugins.fmp.multitools.service;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ProcessingException;
import plugins.fmp.multitools.tools.ROI2D.ROI2DWithMask;
import plugins.fmp.multitools.tools.ROI2D.ValidationException;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFactory;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * V6 spot measures from camera using {@link ImageTransformEnums#THRESHOLD_COLORS} (multi-reference color distance)
 * for the spot mask and the same fly gating / grey-clean pipeline shape as {@link SpotLevelDetectorFromCamV5}.
 */
public class SpotLevelDetectorFromCamV6 implements SpotLevelDetectionRunner {

	@Override
	public void detectSpots(Experiment exp, BuildSeriesOptions options) {
		if (exp == null || options == null) {
			return;
		}

		Spots spots = exp.getSpots();
		if (spots == null || spots.isSpotListEmpty()) {
			return;
		}

		exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.DIRECT_FROM_STACK);
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getImageLoader() == null) {
			return;
		}

		int nCamFrames = seqCamData.getImageLoader().getNTotalFrames();
		if (nCamFrames <= 0) {
			return;
		}

		long firstMs = exp.getKymoFirst_ms();
		long lastMs = exp.getKymoLast_ms();
		long stepMs = exp.getKymoBin_ms();
		if (stepMs <= 0) {
			stepMs = 1;
		}
		if (firstMs == 0 && lastMs == 0) {
			long camFirst = exp.getCamImageFirst_ms();
			long camLast = exp.getCamImageLast_ms();
			if (camFirst >= 0 && camLast > camFirst) {
				firstMs = 0;
				lastMs = camLast - camFirst;
				long camBin = exp.getCamImageBin_ms();
				if (camBin > 0) {
					stepMs = camBin;
				}
			} else {
				firstMs = 0;
				lastMs = (nCamFrames - 1) * stepMs;
			}
		}
		int nTimeBins = (int) ((lastMs - firstMs) / stepMs + 1);
		if (nTimeBins <= 0) {
			return;
		}

		final long tGlobalStart = System.nanoTime();

		final long tInitStart = System.nanoTime();
		List<Spot> toProcess = buildSpotsToProcess(spots);
		if (toProcess.isEmpty()) {
			return;
		}

		initializeSpotArrays(toProcess, nTimeBins);
		initializeSpotMasks(exp, toProcess);
		final long tInitEnd = System.nanoTime();

		if (options.spotColorReferenceList == null || options.spotColorReferenceList.isEmpty()) {
			Logger.warn("SpotLevelDetectorFromCamV6: spotColorReferenceList is empty; aborting");
			return;
		}

		SequenceLoaderService loader = new SequenceLoaderService();

		final ImageTransformInterface transformSpot = ImageTransformFactory
				.getFunction(ImageTransformEnums.THRESHOLD_COLORS, options.useGpuTransforms);
		final ImageTransformInterface transformFly = ImageTransformFactory.getFunction(options.transform02,
				options.useGpuTransforms);
		if (transformSpot == null || transformFly == null) {
			Logger.warn("SpotLevelDetectorFromCamV6: missing transform functions");
			return;
		}

		final CanvasImageTransformOptions transformOptionsSpot = ImageTransformUtils.buildCanvasOptionsForSpot(options);
		final CanvasImageTransformOptions transformOptionsFly = ImageTransformUtils.buildCanvasOptionsForFly(options);

		ProgressFrame progress = new ProgressFrame("Detecting spot levels (V6 color-distance)");
		progress.setLength(nTimeBins);

		IcyBufferedImage camImage = null;
		IcyBufferedImage spotImage = null;
		IcyBufferedImage flyImage = null;

		final long tLoopStart = System.nanoTime();
		try {
			for (int t = 0; t < nTimeBins; t++) {
				progress.setMessage("Interval " + (t + 1) + " / " + nTimeBins);

				long timeMs = firstMs + t * stepMs;
				final int camFrameIndex = exp.findNearestIntervalWithBinarySearch(timeMs, 0,
						Math.max(0, nCamFrames - 1));
				if (camFrameIndex < 0 || camFrameIndex >= nCamFrames) {
					progress.incPosition();
					continue;
				}

				String path = seqCamData.getFileNameFromImageList(camFrameIndex);
				if (path == null) {
					progress.incPosition();
					continue;
				}

				camImage = loader.imageIORead(path);
				if (camImage == null) {
					progress.incPosition();
					continue;
				}

				if (camImage.getSizeC() < 3) {
					progress.incPosition();
					continue;
				}

				spotImage = transformSpot.getTransformedImage(camImage, transformOptionsSpot, spotImage);
				flyImage = transformFly.getTransformedImage(camImage, transformOptionsFly, flyImage);
				if (flyImage == null || spotImage == null) {
					progress.incPosition();
					continue;
				}

				IcyBufferedImageCursor cursorFly = new IcyBufferedImageCursor(flyImage);
				IcyBufferedImageCursor cursorSpot = new IcyBufferedImageCursor(spotImage);
				updateSpotsAtTimeIndex(toProcess, cursorSpot, cursorFly, spotImage, t, options);
				progress.incPosition();
			}
		} finally {
			progress.close();
			camImage = null;
			spotImage = null;
			flyImage = null;
		}
		final long tLoopEnd = System.nanoTime();

		applyV6FlyNanBorderAdaptiveTrim(toProcess, options.v5FlyNaNDilationBins,
				options.v5FlyNaNBorderMedianHalfWidth, options.v5FlyNaNBorderSpikeRatio);

		for (Spot spot : toProcess) {
			if (spot != null) {
				spot.getMeasurementsV6().rebuildGreySumCleanFromGreySum(options);
			}
		}

		final long tPostStart = System.nanoTime();
		spots.transferMeasuresToLevel2D();

		String directory = exp.getDirectoryToSaveResults();
		if (directory != null) {
			exp.saveExperimentDescriptors();
			exp.save_spots_description_and_measures();
		}
		final long tPostEnd = System.nanoTime();

		long initMs = (tInitEnd - tInitStart) / 1_000_000L;
		long loopMs = (tLoopEnd - tLoopStart) / 1_000_000L;
		long postMs = (tPostEnd - tPostStart) / 1_000_000L;
		long totalMs = (tPostEnd - tGlobalStart) / 1_000_000L;

		Logger.info("SpotLevelDetectorFromCamV6: " + toProcess.size() + " spots, " + nTimeBins + " time bins, "
				+ nCamFrames + " cam frames");
		Logger.info("SpotLevelDetectorFromCamV6 timings [ms] - init=" + initMs + ", mainLoop=" + loopMs + ", post="
				+ postMs + ", total=" + totalMs);
	}

	private List<Spot> buildSpotsToProcess(Spots spots) {
		List<Spot> list = new ArrayList<>();
		for (Spot spot : spots.getSpotList()) {
			if (spot != null && spot.isReadyForAnalysis()) {
				list.add(spot);
			}
		}
		return list;
	}

	/**
	 * Clears {@code GREY_SUM_V6} / {@code AREA_COUNT_V6} only for finite bins near a fly-gated NaN where grey is an
	 * <strong>upward</strong> outlier vs. a local median and vs. the previous finite grey (genuine consumption drops
	 * are never trimmed).
	 */
	private static void applyV6FlyNanBorderAdaptiveTrim(List<Spot> spotsToProcess, int probeBins, int medianHalfWidth,
			double spikeRatio) {
		if (probeBins < 1 || spotsToProcess == null) {
			return;
		}
		double ratio = (!Double.isFinite(spikeRatio) || spikeRatio <= 1.0) ? 1.35 : spikeRatio;
		int mw = Math.max(0, medianHalfWidth);
		int bufCap = Math.max(8, 2 * mw + 4);

		for (Spot spot : spotsToProcess) {
			if (spot == null) {
				continue;
			}
			SpotMeasure grey = spot.getGreySumV6();
			SpotMeasure area = spot.getAreaCountV6();
			if (grey == null || area == null) {
				continue;
			}
			double[] g0 = grey.getValues();
			double[] a0 = area.getValues();
			if (g0 == null || a0 == null || g0.length != a0.length || g0.length == 0) {
				continue;
			}
			int n = g0.length;
			double[] g = Arrays.copyOf(g0, n);
			double[] a = Arrays.copyOf(a0, n);
			boolean[] nan = new boolean[n];
			for (int i = 0; i < n; i++) {
				nan[i] = !Double.isFinite(g0[i]) || !Double.isFinite(a0[i]);
			}
			double[] buf = new double[bufCap];
			for (int i = 0; i < n; i++) {
				if (nan[i]) {
					continue;
				}
				int loP = Math.max(0, i - probeBins);
				int hiP = Math.min(n - 1, i + probeBins);
				boolean nearNan = false;
				for (int k = loP; k <= hiP; k++) {
					if (nan[k]) {
						nearNan = true;
						break;
					}
				}
				if (!nearNan) {
					continue;
				}
				double prevGrey = previousFiniteGrey(g0, i);
				if (Double.isFinite(prevGrey) && g0[i] <= prevGrey) {
					continue;
				}
				int loW = Math.max(0, i - mw);
				int hiW = Math.min(n - 1, i + mw);
				int count = 0;
				for (int j = loW; j <= hiW; j++) {
					if (j == i) {
						continue;
					}
					if (!Double.isFinite(g0[j])) {
						continue;
					}
					if (count >= buf.length) {
						break;
					}
					buf[count++] = g0[j];
				}
				if (count < 2) {
					continue;
				}
				double med = medianOfSortedBuffer(buf, count);
				if (!Double.isFinite(med)) {
					continue;
				}
				if (g0[i] > med * ratio) {
					g[i] = Double.NaN;
					a[i] = Double.NaN;
				}
			}
			grey.setValues(g);
			area.setValues(a);
		}
	}

	private static double previousFiniteGrey(double[] g, int fromIndex) {
		for (int j = fromIndex - 1; j >= 0; j--) {
			if (Double.isFinite(g[j])) {
				return g[j];
			}
		}
		return Double.NaN;
	}

	private static double medianOfSortedBuffer(double[] buf, int count) {
		Arrays.sort(buf, 0, count);
		if (count % 2 == 1) {
			return buf[count / 2];
		}
		return 0.5 * (buf[count / 2 - 1] + buf[count / 2]);
	}

	private void initializeSpotArrays(List<Spot> spotsToProcess, int nTimeBins) {
		for (Spot spot : spotsToProcess) {
			if (spot.getAreaCountV6() != null) {
				spot.getAreaCountV6().setValues(new double[nTimeBins]);
			}
			if (spot.getGreySumV6PreFly() != null) {
				spot.getGreySumV6PreFly().setValues(new double[nTimeBins]);
			}
			if (spot.getGreySumV6() != null) {
				spot.getGreySumV6().setValues(new double[nTimeBins]);
			}
			if (spot.getFlyPresent() != null) {
				spot.getFlyPresent().setIsPresent(new int[nTimeBins]);
			}
		}
	}

	private void initializeSpotMasks(Experiment exp, List<Spot> spotsToProcess) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null) {
			return;
		}

		if (seqCamData.getSequence() == null) {
			SequenceLoaderService loader = new SequenceLoaderService();
			loader.loadFirstImage(seqCamData);
		}

		for (Spot spot : spotsToProcess) {
			try {
				ROI2DWithMask roiMask = new ROI2DWithMask(spot.getRoi());
				roiMask.buildMask2DFromInputRoi();
				spot.setROIMask(roiMask);
			} catch (ValidationException | ProcessingException e) {
				Logger.warn("SpotLevelDetectorFromCamV6: failed to build mask for spot " + spot.getName() + " - "
						+ e.getMessage());
			}
		}
	}

	private void updateSpotsAtTimeIndex(List<Spot> spotsToProcess, IcyBufferedImageCursor cursorSpot,
			IcyBufferedImageCursor cursorFly, IcyBufferedImage spotImage, int timeIndex, BuildSeriesOptions options) {

		if (cursorSpot == null || cursorFly == null || spotImage == null) {
			return;
		}

		final int imageWidth = spotImage.getSizeX();
		final int imageHeight = spotImage.getSizeY();

		for (Spot spot : spotsToProcess) {
			updateSingleSpotAtTimeIndex(spot, cursorSpot, cursorFly, imageWidth, imageHeight, timeIndex, options);
		}
	}

	private void updateSingleSpotAtTimeIndex(Spot spot, IcyBufferedImageCursor cursorSpot,
			IcyBufferedImageCursor cursorFly, int imageWidth, int imageHeight, int timeIndex,
			BuildSeriesOptions options) {

		if (spot == null || !spot.isReadyForAnalysis()) {
			return;
		}

		ROI2DWithMask roiMask = spot.getROIMask();
		if (roiMask == null || !roiMask.hasMaskData()) {
			return;
		}

		int[][] maskArrays = roiMask.getMaskPointsAsArrays();
		if (maskArrays == null || maskArrays.length != 2) {
			return;
		}

		int[] maskX = maskArrays[0];
		int[] maskY = maskArrays[1];
		if (maskX == null || maskY == null || maskX.length == 0 || maskY.length == 0 || maskX.length != maskY.length) {
			return;
		}

		double sumOverThreshold = 0;
		int nOver = 0;

		int nPointsIn = maskX.length;
		int nPointsFlyPresent = 0;

		for (int i = 0; i < maskX.length; i++) {
			int x = maskX[i];
			int y = maskY[i];
			if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
				continue;
			}

			int valueSpot = (int) cursorSpot.get(x, y, 0);
			int valueFly = (int) cursorFly.get(x, y, 0);

			boolean flyThere = isFlyPresent(valueFly, options);
			if (flyThere) {
				nPointsFlyPresent++;
			}

			if (isOverThreshold(valueSpot, options)) {
				sumOverThreshold += valueSpot;
				nOver++;
			}
		}

		if (nPointsIn > 0) {
			int minFlyPx = Spots.getMinFlyPixelsForOccupancyGate(spot, options.getFlyOccupancyFractionForSpotSumNoFly());
			double greyVal = sumOverThreshold / (double) nPointsIn;
			spot.getGreySumV6PreFly().setValueAt(timeIndex, greyVal);
			if (nPointsFlyPresent >= minFlyPx) {
				spot.getAreaCountV6().setValueAt(timeIndex, Double.NaN);
				spot.getGreySumV6().setValueAt(timeIndex, Double.NaN);
			} else {
				spot.getAreaCountV6().setValueAt(timeIndex, nOver);
				spot.getGreySumV6().setValueAt(timeIndex, greyVal);
			}
			spot.getFlyPresent().setIsPresentAt(timeIndex, nPointsFlyPresent);
		}
	}

	private boolean isFlyPresent(double value, BuildSeriesOptions options) {
		boolean flag = value > options.flyThreshold;
		if (!options.flyThresholdUp) {
			flag = !flag;
		}
		return flag;
	}

	private boolean isOverThreshold(double value, BuildSeriesOptions options) {
		boolean flag = value > options.spotThreshold;
		if (!options.spotThresholdUp) {
			flag = !flag;
		}
		return flag;
	}

	private static class ImageTransformUtils {

		static CanvasImageTransformOptions buildCanvasOptionsForSpot(BuildSeriesOptions options) {
			CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
			transformOptions.transformOption = ImageTransformEnums.THRESHOLD_COLORS;
			transformOptions.copyResultsToThe3planes = false;
			ArrayList<Color> refs = options.spotColorReferenceList;
			transformOptions.setColorArrayThreshold(options.spotColorDistanceType, options.spotColorDistanceMax,
					refs != null ? new ArrayList<>(refs) : new ArrayList<>(), options.spotColorSpace);
			transformOptions.setSingleThreshold(options.spotThreshold, options.spotThresholdUp);
			return transformOptions;
		}

		static CanvasImageTransformOptions buildCanvasOptionsForFly(BuildSeriesOptions options) {
			CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
			transformOptions.transformOption = options.transform02;
			transformOptions.copyResultsToThe3planes = false;
			transformOptions.setSingleThreshold(options.flyThreshold, options.flyThresholdUp);
			return transformOptions;
		}
	}
}
