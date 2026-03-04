package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ProcessingException;
import plugins.fmp.multitools.tools.ROI2D.ROI2DWithMask;
import plugins.fmp.multitools.tools.ROI2D.ValidationException;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Memory-light detector that computes per-spot measures directly from camera
 * images, following the streaming pattern used in {@link LevelDetectorFromCam}.
 * <p>
 * For each time bin, this detector:
 * <ul>
 * <li>Finds the nearest camera frame.</li>
 * <li>Loads a single cam image from disk.</li>
 * <li>Applies two transforms (spot / fly).</li>
 * <li>Iterates over precomputed ROI masks for all spots and updates their
 * time-series arrays.</li>
 * </ul>
 * No global compressed mask cache or image memory pools are used; only per-spot
 * ROI masks and per-frame transformed images are kept in memory.
 */
public class SpotLevelDetectorFromCam {

	public void detectSpots(Experiment exp, BuildSeriesOptions options) {
		if (exp == null || options == null)
			return;

		Spots spots = exp.getSpots();
		if (spots == null || spots.isSpotListEmpty())
			return;

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null || seqCamData.getImageLoader() == null)
			return;

		int nCamFrames = seqCamData.getImageLoader().getNTotalFrames();
		if (nCamFrames <= 0)
			return;

		// Build time grid similar to LevelDetectorFromCam
		long firstMs = exp.getKymoFirst_ms();
		long lastMs = exp.getKymoLast_ms();
		long stepMs = exp.getKymoBin_ms();
		if (stepMs <= 0)
			stepMs = 1;
		if (firstMs == 0 && lastMs == 0) {
			long camFirst = exp.getCamImageFirst_ms();
			long camLast = exp.getCamImageLast_ms();
			if (camFirst >= 0 && camLast > camFirst) {
				firstMs = 0;
				lastMs = camLast - camFirst;
				long camBin = exp.getCamImageBin_ms();
				if (camBin > 0)
					stepMs = camBin;
			} else {
				firstMs = 0;
				lastMs = (nCamFrames - 1) * stepMs;
			}
		}
		int nTimeBins = (int) ((lastMs - firstMs) / stepMs + 1);
		if (nTimeBins <= 0)
			return;

		// Prepare list of spots to process and allocate their time-series arrays
		List<Spot> toProcess = buildSpotsToProcess(spots);
		if (toProcess.isEmpty())
			return;

		initializeSpotArrays(toProcess, nTimeBins);
		initializeSpotMasks(exp, toProcess);

		SequenceLoaderService loader = new SequenceLoaderService();

		// Configure transforms for spots and flies
		final ImageTransformInterface transformSpot = options.transform01 != null ? options.transform01.getFunction()
				: null;
		final ImageTransformInterface transformFly = options.transform02 != null ? options.transform02.getFunction()
				: null;
		if (transformSpot == null || transformFly == null) {
			Logger.warn("SpotLevelDetectorFromCam: missing transform functions");
			return;
		}

		final CanvasImageTransformOptions transformOptionsSpot = ImageTransformUtils.buildCanvasOptionsForSpot(options);
		final CanvasImageTransformOptions transformOptionsFly = ImageTransformUtils.buildCanvasOptionsForFly(options);

		IcyBufferedImage camImage = null;
		IcyBufferedImage spotImage = null;
		IcyBufferedImage flyImage = null;
		IcyBufferedImageCursor cursorSpot = null;
		IcyBufferedImageCursor cursorFly = null;

		try {
			for (int t = 0; t < nTimeBins; t++) {
				long timeMs = firstMs + t * stepMs;
				final int camFrameIndex = exp.findNearestIntervalWithBinarySearch(timeMs, 0, Math.max(0, nCamFrames - 1));
				if (camFrameIndex < 0 || camFrameIndex >= nCamFrames)
					continue;

				String path = seqCamData.getFileNameFromImageList(camFrameIndex);
				if (path == null)
					continue;

				camImage = loader.imageIORead(path);
				if (camImage == null)
					continue;

				spotImage = transformSpot.getTransformedImage(camImage, transformOptionsSpot, spotImage);
				flyImage = transformFly.getTransformedImage(camImage, transformOptionsFly, flyImage);
				if (spotImage == null || flyImage == null)
					continue;

				cursorSpot = new IcyBufferedImageCursor(spotImage);
				cursorFly = new IcyBufferedImageCursor(flyImage);

				updateSpotsAtTimeIndex(toProcess, cursorSpot, cursorFly, spotImage, t, options);
			}
		} finally {
			camImage = null;
			spotImage = null;
			flyImage = null;
			cursorSpot = null;
			cursorFly = null;
		}

		// Transfer values to Level2D for downstream consumers (charts, export, etc.)
		spots.transferMeasuresToLevel2D();
		spots.medianFilterFromSumToSumClean();

		// Persist results using the standard experiment helpers
		String directory = exp.getDirectoryToSaveResults();
		if (directory != null) {
			exp.saveExperimentDescriptors();
			exp.save_spots_description_and_measures();
		}
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

	private void initializeSpotArrays(List<Spot> spotsToProcess, int nTimeBins) {
		for (Spot spot : spotsToProcess) {
			if (spot.getSum() != null) {
				spot.getSum().setValues(new double[nTimeBins]);
			}
			if (spot.getSumClean() != null) {
				spot.getSumClean().setValues(new double[nTimeBins]);
			}
			if (spot.getFlyPresent() != null) {
				spot.getFlyPresent().setIsPresent(new int[nTimeBins]);
			}
		}
	}

	private void initializeSpotMasks(Experiment exp, List<Spot> spotsToProcess) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;

		// Ensure we have at least one image attached so ROI masks have valid bounds
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
				Logger.warn("SpotLevelDetectorFromCam: failed to build mask for spot " + spot.getName() + " - "
						+ e.getMessage());
			}
		}
	}

	private void updateSpotsAtTimeIndex(List<Spot> spotsToProcess, IcyBufferedImageCursor cursorSpot,
			IcyBufferedImageCursor cursorFly, IcyBufferedImage spotImage, int timeIndex, BuildSeriesOptions options) {

		if (cursorSpot == null || cursorFly == null || spotImage == null)
			return;

		final int imageWidth = spotImage.getSizeX();
		final int imageHeight = spotImage.getSizeY();

		for (Spot spot : spotsToProcess) {
			if (!spot.isReadyForAnalysis())
				continue;

			ROI2DWithMask roiMask = spot.getROIMask();
			if (roiMask == null || !roiMask.hasMaskData())
				continue;

			int[][] maskArrays = roiMask.getMaskPointsAsArrays();
			if (maskArrays == null || maskArrays.length != 2)
				continue;

			int[] maskX = maskArrays[0];
			int[] maskY = maskArrays[1];
			if (maskX == null || maskY == null || maskX.length == 0 || maskY.length == 0
					|| maskX.length != maskY.length)
				continue;

			double sumOverThreshold = 0;
			double sumNoFlyOverThreshold = 0;
			int nPointsIn = maskX.length;
			int nPointsNoFly = 0;
			int nPointsFlyPresent = 0;

			for (int i = 0; i < maskX.length; i++) {
				int x = maskX[i];
				int y = maskY[i];
				if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight)
					continue;

				int valueSpot = (int) cursorSpot.get(x, y, 0);
				int valueFly = (int) cursorFly.get(x, y, 0);

				boolean flyThere = isFlyPresent(valueFly, options);
				if (!flyThere) {
					nPointsNoFly++;
				} else {
					nPointsFlyPresent++;
				}

				if (isOverThreshold(valueSpot, options)) {
					sumOverThreshold += valueSpot;
					if (!flyThere) {
						sumNoFlyOverThreshold += valueSpot;
					}
				}
			}

			if (nPointsIn > 0) {
				double meanAll = sumOverThreshold / nPointsIn;
				spot.getSum().setValueAt(timeIndex, meanAll);

				if (nPointsNoFly > 0) {
					double meanNoFly = sumNoFlyOverThreshold / nPointsNoFly;
					spot.getSumClean().setValueAt(timeIndex, meanNoFly);
				} else {
					spot.getSumClean().setValueAt(timeIndex, meanAll);
				}

				spot.getFlyPresent().setIsPresentAt(timeIndex, nPointsFlyPresent);
			}
		}
	}

	private boolean isFlyPresent(double value, BuildSeriesOptions options) {
		boolean flag = value > options.flyThreshold;
		if (!options.flyThresholdUp)
			flag = !flag;
		return flag;
	}

	private boolean isOverThreshold(double value, BuildSeriesOptions options) {
		boolean flag = value > options.spotThreshold;
		if (!options.spotThresholdUp)
			flag = !flag;
		return flag;
	}

	/**
	 * Local helper for building transform options without pulling in the heavier
	 * advanced classes.
	 */
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
