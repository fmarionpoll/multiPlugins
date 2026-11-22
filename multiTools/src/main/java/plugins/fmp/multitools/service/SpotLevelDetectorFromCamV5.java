package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
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
import plugins.fmp.multitools.tools.imageTransform.transforms.RoiMaskedLocalSumDiffRgb;
import plugins.fmp.multitools.tools.imageTransform.transforms.SumDiffLocalMeanRgb;

/**
 * Light-path detector for V5 spot measures: {@code FILTER_V5_PIXEL_COUNT}, {@code FILTER_V5_GREY},
 * {@code FILTER_V5_GREY_PRE_GATE} (same grey scalar before the fly-occupancy NaN gate), and
 * {@code FILTER_V5_GREY_SMOOTHED} (causal upward spike trim vs past-only median when enabled, then running-median smooth;
 * same running-median span as legacy {@code sumClean} from {@code sumNoFly}).
 * When fly-pixel count in the ROI for a bin reaches the same occupancy gate as legacy {@code sumNoFly}
 * reconstruction ({@link plugins.fmp.multitools.experiment.spots.Spots#getMinFlyPixelsForOccupancyGate},
 * from {@link BuildSeriesOptions#getFlyOccupancyFractionForSpotSumNoFly()}), {@code FILTER_V5_PIXEL_COUNT} and
 * {@code FILTER_V5_GREY} are set to {@code NaN} for that bin (prefly grey retains the finite value for diagnostics).
 * Optional adaptive border trim: finite bins within
 * {@link BuildSeriesOptions#v5FlyNaNDilationBins} of a fly NaN are cleared only when {@code FILTER_V5_GREY} is an
 * <strong>upward</strong> outlier (strictly above the previous finite grey and above a local median ×
 * {@link BuildSeriesOptions#v5FlyNaNBorderSpikeRatio}); genuine downward steps from feeding are never cleared.
 * {@code FILTER_V5_GREY} matches legacy {@code FILTER_GREY} scaling: sum of over-threshold spot-channel values divided
 * by the total number of ROI mask pixels ({@code sumOverThreshold / nPointsIn}).
 * After all bins are filled, {@code FILTER_V5_GREY_SMOOTHED} is rebuilt from {@code FILTER_V5_GREY}.
 * Legacy sum/sumClean pipelines are not written here.
 * <p>
 * Optional CPU test path: when {@link BuildSeriesOptions#v5SpotLocalMeanRestrictedToRoi} is true and the spot
 * transform is {@link ImageTransformEnums#RGB_DIFFS_LOCAL_MEAN}, spot scalars use {@link RoiMaskedLocalSumDiffRgb}
 * (local mean restricted to the spot disk) instead of a full-frame transform.
 */
public class SpotLevelDetectorFromCamV5 implements SpotLevelDetectionRunner {

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

		// Keep one sample per analysis-interval camera frame, matching X-line/rim.
		int nTimeBins = nCamFrames;

		final long tGlobalStart = System.nanoTime();

		final long tInitStart = System.nanoTime();
		List<Spot> toProcess = buildSpotsToProcess(spots);
		if (toProcess.isEmpty()) {
			return;
		}

		initializeSpotArrays(toProcess, nTimeBins);
		initializeSpotMasks(exp, toProcess);
		final long tInitEnd = System.nanoTime();

		SequenceLoaderService loader = new SequenceLoaderService();

		final boolean roiRestrictedLocalMean = options.v5SpotLocalMeanRestrictedToRoi
				&& options.transform01 == ImageTransformEnums.RGB_DIFFS_LOCAL_MEAN;

		final ImageTransformInterface transformSpot = ImageTransformFactory.getFunction(options.transform01,
				options.useGpuTransforms);
		final ImageTransformInterface transformFly = ImageTransformFactory.getFunction(options.transform02,
				options.useGpuTransforms);
		if (transformSpot == null || transformFly == null) {
			Logger.warn("SpotLevelDetectorFromCamV5: missing transform functions");
			return;
		}

		final CanvasImageTransformOptions transformOptionsSpot = ImageTransformUtils.buildCanvasOptionsForSpot(options);
		final CanvasImageTransformOptions transformOptionsFly = ImageTransformUtils.buildCanvasOptionsForFly(options);

		if (roiRestrictedLocalMean) {
			Logger.info("SpotLevelDetectorFromCamV5: ROI-restricted loc\u03bc spot path (CPU), fly transform unchanged");
		}

		ProgressFrame progress = new ProgressFrame("Detecting spot levels (V5)");
		progress.setLength(nTimeBins);

		IcyBufferedImage camImage = null;
		IcyBufferedImage spotImage = null;
		IcyBufferedImage flyImage = null;

		final long tLoopStart = System.nanoTime();
		try {
			for (int t = 0; t < nTimeBins; t++) {
				progress.setMessage("Frame " + (t + 1) + " / " + nTimeBins);

				final int camFrameIndex = t;

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

				if (roiRestrictedLocalMean) {
					spotImage = null;
				} else {
					spotImage = transformSpot.getTransformedImage(camImage, transformOptionsSpot, spotImage);
				}
				flyImage = transformFly.getTransformedImage(camImage, transformOptionsFly, flyImage);
				if (flyImage == null) {
					progress.incPosition();
					continue;
				}
				if (!roiRestrictedLocalMean && spotImage == null) {
					progress.incPosition();
					continue;
				}

				IcyBufferedImageCursor cursorFly = new IcyBufferedImageCursor(flyImage);

				if (roiRestrictedLocalMean) {
					if (camImage.getSizeC() < 3) {
						progress.incPosition();
						continue;
					}
					final int iw = camImage.getWidth();
					final int ih = camImage.getHeight();
					int[] fullRn = Array1DUtil.arrayToIntArray(camImage.getDataXY(0), camImage.isSignedDataType());
					int[] fullGn = Array1DUtil.arrayToIntArray(camImage.getDataXY(1), camImage.isSignedDataType());
					int[] fullBn = Array1DUtil.arrayToIntArray(camImage.getDataXY(2), camImage.isSignedDataType());
					final int spotR = SumDiffLocalMeanRgb.defaultBoxHalfWidth(iw, ih);
					updateSpotsAtTimeIndexRoiLocal(toProcess, cursorFly, iw, ih, fullRn, fullGn, fullBn, spotR, t,
							options);
				} else {
					IcyBufferedImageCursor cursorSpot = new IcyBufferedImageCursor(spotImage);
					updateSpotsAtTimeIndex(toProcess, cursorSpot, cursorFly, spotImage, t, options);
				}
				progress.incPosition();
			}
		} finally {
			progress.close();
			camImage = null;
			spotImage = null;
			flyImage = null;
		}
		final long tLoopEnd = System.nanoTime();

		applyV5FlyNanBorderAdaptiveTrim(toProcess, options.v5FlyNaNDilationBins,
				options.v5FlyNaNBorderMedianHalfWidth, options.v5FlyNaNBorderSpikeRatio);

		for (Spot spot : toProcess) {
			if (spot != null) {
				spot.getMeasurementsV5().rebuildGreySumCleanFromGreySum(options);
			}
		}

		final long tPostStart = System.nanoTime();
		spots.transferMeasuresToLevel2D();

		String directory = exp.getDirectoryToSaveResults();
		if (directory != null) {
			exp.saveExperimentDescriptors();
			if (exp.save_spots_description_and_measures()) {
				SpotDetectionProvenance.recordCamera(exp, SpotDetectionProvenance.Family.FILTER_V5,
						getClass().getSimpleName(), options);
			}
		}
		final long tPostEnd = System.nanoTime();

		long initMs = (tInitEnd - tInitStart) / 1_000_000L;
		long loopMs = (tLoopEnd - tLoopStart) / 1_000_000L;
		long postMs = (tPostEnd - tPostStart) / 1_000_000L;
		long totalMs = (tPostEnd - tGlobalStart) / 1_000_000L;

		Logger.info("SpotLevelDetectorFromCamV5: " + toProcess.size() + " spots, " + nTimeBins + " time bins, "
				+ nCamFrames + " cam frames");
		Logger.info("SpotLevelDetectorFromCamV5 timings [ms] - init=" + initMs + ", mainLoop=" + loopMs + ", post="
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
	 * Clears {@code FILTER_V5_GREY} / {@code FILTER_V5_PIXEL_COUNT} only for finite bins near a fly-gated NaN where grey is an
	 * <strong>upward</strong> outlier vs. a local median and vs. the previous finite grey (genuine consumption drops
	 * are never trimmed).
	 */
	private static void applyV5FlyNanBorderAdaptiveTrim(List<Spot> spotsToProcess, int probeBins, int medianHalfWidth,
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
			SpotMeasure grey = spot.getFilterV5GreySignal();
			SpotMeasure area = spot.getFilterV5SelectedPixelCount();
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
			if (spot.getFilterV5SelectedPixelCount() != null) {
				spot.getFilterV5SelectedPixelCount().setValues(new double[nTimeBins]);
			}
			if (spot.getFilterV5GreySignalBeforeOccupancyCheck() != null) {
				spot.getFilterV5GreySignalBeforeOccupancyCheck().setValues(new double[nTimeBins]);
			}
			if (spot.getFilterV5GreySignal() != null) {
				spot.getFilterV5GreySignal().setValues(new double[nTimeBins]);
			}
			if (spot.getFlyCoveredPixelCount() != null) {
				spot.getFlyCoveredPixelCount().setIsPresent(new int[nTimeBins]);
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
				Logger.warn("SpotLevelDetectorFromCamV5: failed to build mask for spot " + spot.getName() + " - "
						+ e.getMessage());
			}
		}
	}

	private void updateSpotsAtTimeIndexRoiLocal(List<Spot> spotsToProcess, IcyBufferedImageCursor cursorFly,
			int imageWidth, int imageHeight, int[] fullRn, int[] fullGn, int[] fullBn, int spotR, int timeIndex,
			BuildSeriesOptions options) {

		if (cursorFly == null || fullRn == null || fullGn == null || fullBn == null) {
			return;
		}

		for (Spot spot : spotsToProcess) {
			updateSingleSpotAtTimeIndexRoiLocal(spot, cursorFly, imageWidth, imageHeight, fullRn, fullGn, fullBn,
					spotR, timeIndex, options);
		}
	}

	private void updateSingleSpotAtTimeIndexRoiLocal(Spot spot, IcyBufferedImageCursor cursorFly, int imageWidth,
			int imageHeight, int[] fullRn, int[] fullGn, int[] fullBn, int spotR, int timeIndex,
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

		int[] spotScalars = RoiMaskedLocalSumDiffRgb.computeScalarsForMask(imageWidth, imageHeight, fullRn, fullGn,
				fullBn, maskX, maskY, spotR);
		if (spotScalars == null || spotScalars.length != maskX.length) {
			Logger.warn("SpotLevelDetectorFromCamV5: ROI loc\u03bc scalars failed for spot " + spot.getName());
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

			int valueSpot = spotScalars[i];
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
			spot.getFilterV5GreySignalBeforeOccupancyCheck().setValueAt(timeIndex, greyVal);
			if (nPointsFlyPresent >= minFlyPx) {
				spot.getFilterV5SelectedPixelCount().setValueAt(timeIndex, Double.NaN);
				spot.getFilterV5GreySignal().setValueAt(timeIndex, Double.NaN);
			} else {
				spot.getFilterV5SelectedPixelCount().setValueAt(timeIndex, nOver);
				spot.getFilterV5GreySignal().setValueAt(timeIndex, greyVal);
			}
			spot.getFlyCoveredPixelCount().setIsPresentAt(timeIndex, nPointsFlyPresent);
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
			spot.getFilterV5GreySignalBeforeOccupancyCheck().setValueAt(timeIndex, greyVal);
			if (nPointsFlyPresent >= minFlyPx) {
				spot.getFilterV5SelectedPixelCount().setValueAt(timeIndex, Double.NaN);
				spot.getFilterV5GreySignal().setValueAt(timeIndex, Double.NaN);
			} else {
				spot.getFilterV5SelectedPixelCount().setValueAt(timeIndex, nOver);
				spot.getFilterV5GreySignal().setValueAt(timeIndex, greyVal);
			}
			spot.getFlyCoveredPixelCount().setIsPresentAt(timeIndex, nPointsFlyPresent);
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
			transformOptions.transformOption = options.transform01;
			transformOptions.copyResultsToThe3planes = false;
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
