package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.List;
import java.awt.geom.Rectangle2D;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.gui.frame.progress.ProgressFrame;
import icy.roi.ROI2D;
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
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFactory;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Basic, single-threaded detector that computes per-spot measures directly from
 * camera images. This version is intended for lower-end machines: it avoids
 * multi-frame pipelining and uses a simple per-frame loop with modest memory
 * usage.
 */
public class SpotLevelDetectorFromCamBasic implements SpotLevelDetectionRunner {

	// V2 background estimation: use an annulus inside the (ellipse) ROI bounds.
	// Fractions are in normalized ellipse radius units (0=center, 1=ellipse boundary).
	private static final double V2_BG_ANNULUS_INNER_FRAC = 0.72;
	private static final double V2_BG_ANNULUS_OUTER_FRAC = 0.98;

	@Override
	public void detectSpots(Experiment exp, BuildSeriesOptions options) {
		if (exp == null || options == null)
			return;

		Spots spots = exp.getSpots();
		if (spots == null || spots.isSpotListEmpty())
			return;

		exp.setGenerationMode(plugins.fmp.multitools.experiment.GenerationMode.DIRECT_FROM_STACK);
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

		// Timing: global detector start
		final long tGlobalStart = System.nanoTime();

		// Prepare list of spots to process and allocate their time-series arrays
		final long tInitStart = System.nanoTime();
		List<Spot> toProcess = buildSpotsToProcess(spots);
		if (toProcess.isEmpty())
			return;

		initializeSpotArrays(toProcess, nTimeBins);
		initializeSpotMasks(exp, toProcess);
		final long tInitEnd = System.nanoTime();

		SequenceLoaderService loader = new SequenceLoaderService();

		// Configure transforms for spots and flies
		final ImageTransformInterface transformSpot = ImageTransformFactory.getFunction(options.transform01,
				options.useGpuTransforms);
		final ImageTransformInterface transformFly = ImageTransformFactory.getFunction(options.transform02,
				options.useGpuTransforms);
		if (transformSpot == null || transformFly == null) {
			Logger.warn("SpotLevelDetectorFromCamBasic: missing transform functions");
			return;
		}

		final CanvasImageTransformOptions transformOptionsSpot = ImageTransformUtils.buildCanvasOptionsForSpot(options);
		final CanvasImageTransformOptions transformOptionsFly = ImageTransformUtils.buildCanvasOptionsForFly(options);

		ProgressFrame progress = new ProgressFrame("Detecting spot levels (basic)");
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

				spotImage = transformSpot.getTransformedImage(camImage, transformOptionsSpot, spotImage);
				flyImage = transformFly.getTransformedImage(camImage, transformOptionsFly, flyImage);
				if (spotImage == null || flyImage == null) {
					progress.incPosition();
					continue;
				}

				IcyBufferedImageCursor cursorSpot = new IcyBufferedImageCursor(spotImage);
				IcyBufferedImageCursor cursorFly = new IcyBufferedImageCursor(flyImage);

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

		// sumNoFly from sum + fly (same as Edit / load), then sumClean = sumNoFly; then Level2D
		final long tPostStart = System.nanoTime();
		// Pixel-level no-fly: apply conservative plateau correction on bins with high fly occupancy,
		// then rebuild CLEAN from the corrected sumNoFly.
		spots.applyFlyPlateauOnSumNoFlyForSpots(toProcess, options.getFlyOccupancyFractionForSpotSumNoFly());
		spots.applyFlyPlateauOnSumNoFlyV2ForSpots(toProcess, options.getFlyOccupancyFractionForSpotSumNoFly());
		spots.rebuildSumCleanOnlyForSpots(toProcess);
		spots.rebuildSumCleanOnlyForSpotsV2(toProcess);
		spots.rebuildV3ResidualFromSumCleanExperimentMedian(10);
		spots.applyPreConsumedReferenceAtT0(exp);
		spots.transferMeasuresToLevel2D();

		// Persist results using the standard experiment helpers
		String directory = exp.getDirectoryToSaveResults();
		if (directory != null) {
			exp.saveExperimentDescriptors();
			exp.save_spots_description_and_measures();
		}
		final long tPostEnd = System.nanoTime();

		// Timing summary
		long initMs = (tInitEnd - tInitStart) / 1_000_000L;
		long loopMs = (tLoopEnd - tLoopStart) / 1_000_000L;
		long postMs = (tPostEnd - tPostStart) / 1_000_000L;
		long totalMs = (tPostEnd - tGlobalStart) / 1_000_000L;

		Logger.info("SpotLevelDetectorFromCamBasic: " + toProcess.size() + " spots, " + nTimeBins + " time bins, "
				+ nCamFrames + " cam frames");
		Logger.info("SpotLevelDetectorFromCamBasic timings [ms] - init=" + initMs + ", mainLoop=" + loopMs + ", post="
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

	private void initializeSpotArrays(List<Spot> spotsToProcess, int nTimeBins) {
		for (Spot spot : spotsToProcess) {
			if (spot.getSum() != null) {
				spot.getSum().setValues(new double[nTimeBins]);
			}
			if (spot.getSumV2() != null) {
				spot.getSumV2().setValues(new double[nTimeBins]);
			}
			if (spot.getSumNoFly() != null) {
				spot.getSumNoFly().setValues(new double[nTimeBins]);
			}
			if (spot.getSumNoFlyV2() != null) {
				spot.getSumNoFlyV2().setValues(new double[nTimeBins]);
			}
			if (spot.getSumClean() != null) {
				spot.getSumClean().setValues(new double[nTimeBins]);
			}
			if (spot.getSumCleanV2() != null) {
				spot.getSumCleanV2().setValues(new double[nTimeBins]);
			}
			if (spot.getSumCleanV3() != null) {
				spot.getSumCleanV3().setValues(new double[nTimeBins]);
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
				Logger.warn("SpotLevelDetectorFromCamBasic: failed to build mask for spot " + spot.getName() + " - "
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
			updateSingleSpotAtTimeIndex(spot, cursorSpot, cursorFly, imageWidth, imageHeight, timeIndex, options);
		}
	}

	private void updateSingleSpotAtTimeIndex(Spot spot, IcyBufferedImageCursor cursorSpot,
			IcyBufferedImageCursor cursorFly, int imageWidth, int imageHeight, int timeIndex,
			BuildSeriesOptions options) {

		if (spot == null || !spot.isReadyForAnalysis())
			return;

		ROI2DWithMask roiMask = spot.getROIMask();
		if (roiMask == null || !roiMask.hasMaskData())
			return;

		int[][] maskArrays = roiMask.getMaskPointsAsArrays();
		if (maskArrays == null || maskArrays.length != 2)
			return;

		int[] maskX = maskArrays[0];
		int[] maskY = maskArrays[1];
		if (maskX == null || maskY == null || maskX.length == 0 || maskY.length == 0 || maskX.length != maskY.length)
			return;

		double sumOverThreshold = 0;
		int nOver = 0;
		double sumUnderThreshold = 0;
		int nUnder = 0;
		double sumBgAnnulus = 0;
		int nBgAnnulus = 0;

		// No-fly (pixel-masked) measures:
		int nNoFly = 0;
		double sumOverThresholdNoFly = 0;
		int nOverNoFly = 0;
		double sumUnderThresholdNoFly = 0;
		int nUnderNoFly = 0;
		double sumBgAnnulusNoFly = 0;
		int nBgAnnulusNoFly = 0;
		int nPointsIn = maskX.length;
		int nPointsFlyPresent = 0;

		// Ellipse parameters from ROI bounds (works best when ROI is an ellipse, but remains stable for near-ellipses).
		double cx = 0.0, cy = 0.0, rx = 0.0, ry = 0.0;
		ROI2D roi = spot.getRoi();
		if (roi != null) {
			Rectangle2D b = roi.getBounds2D();
			if (b != null) {
				cx = b.getCenterX();
				cy = b.getCenterY();
				rx = Math.max(1.0, b.getWidth() / 2.0);
				ry = Math.max(1.0, b.getHeight() / 2.0);
			}
		}
		final double inner2 = V2_BG_ANNULUS_INNER_FRAC * V2_BG_ANNULUS_INNER_FRAC;
		final double outer2 = V2_BG_ANNULUS_OUTER_FRAC * V2_BG_ANNULUS_OUTER_FRAC;

		for (int i = 0; i < maskX.length; i++) {
			int x = maskX[i];
			int y = maskY[i];
			if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight)
				continue;

			int valueSpot = (int) cursorSpot.get(x, y, 0);
			int valueFly = (int) cursorFly.get(x, y, 0);

			boolean flyThere = isFlyPresent(valueFly, options);
			if (flyThere) {
				nPointsFlyPresent++;
			}
			if (!flyThere) {
				nNoFly++;
			}

			if (isOverThreshold(valueSpot, options)) {
				sumOverThreshold += valueSpot;
				nOver++;
				if (!flyThere) {
					sumOverThresholdNoFly += valueSpot;
					nOverNoFly++;
				}
			} else {
				sumUnderThreshold += valueSpot;
				nUnder++;
				if (!flyThere) {
					sumUnderThresholdNoFly += valueSpot;
					nUnderNoFly++;
				}
			}

			// Annulus background: accumulate below-threshold pixels close to ellipse boundary.
			if (rx > 0.0 && ry > 0.0) {
				double dx = (x + 0.5 - cx) / rx;
				double dy = (y + 0.5 - cy) / ry;
				double r2 = dx * dx + dy * dy;
				boolean inAnnulus = (r2 >= inner2 && r2 <= outer2);
				if (inAnnulus && !isOverThreshold(valueSpot, options)) {
					sumBgAnnulus += valueSpot;
					nBgAnnulus++;
					if (!flyThere) {
						sumBgAnnulusNoFly += valueSpot;
						nBgAnnulusNoFly++;
					}
				}
			}
		}

		if (nPointsIn > 0) {
			double meanAll = sumOverThreshold / nPointsIn;
			spot.getSum().setValueAt(timeIndex, meanAll);

			// Compute sumNoFly directly by excluding fly pixels (avoids long interpolation ramps).
			if (nNoFly > 0) {
				double meanNoFly = sumOverThresholdNoFly / nNoFly;
				spot.getSumNoFly().setValueAt(timeIndex, meanNoFly);
			} else {
				spot.getSumNoFly().setValueAt(timeIndex, Double.NaN);
			}

			// V2: estimate foreground on over-threshold pixels (avoid dilution by ROI background)
			// then subtract local background estimated from below-threshold pixels.
			if (nOver <= 0) {
				// When stain is not detected, keep the curve continuous by holding last finite value.
				// This avoids a drop to 0 (looks like "eaten") and avoids long NaN gaps.
				double prev = (timeIndex > 0 && spot.getSumV2() != null) ? spot.getSumV2().getValueAt(timeIndex - 1)
						: Double.NaN;
				spot.getSumV2().setValueAt(timeIndex, Double.isFinite(prev) ? prev : Double.NaN);
			} else {
				double fg = sumOverThreshold / nOver;
				double bg = (nBgAnnulus > 0) ? (sumBgAnnulus / nBgAnnulus)
						: (nUnder > 0) ? (sumUnderThreshold / nUnder) : 0.0;
				spot.getSumV2().setValueAt(timeIndex, Math.max(0.0, fg - bg));
			}

			// V2 no-fly: same as V2 but exclude fly pixels.
			if (nOverNoFly <= 0) {
				double prev = (timeIndex > 0 && spot.getSumNoFlyV2() != null)
						? spot.getSumNoFlyV2().getValueAt(timeIndex - 1)
						: Double.NaN;
				spot.getSumNoFlyV2().setValueAt(timeIndex, Double.isFinite(prev) ? prev : Double.NaN);
			} else {
				double fg = sumOverThresholdNoFly / nOverNoFly;
				double bg = (nBgAnnulusNoFly > 0) ? (sumBgAnnulusNoFly / nBgAnnulusNoFly)
						: (nUnderNoFly > 0) ? (sumUnderThresholdNoFly / nUnderNoFly) : 0.0;
				spot.getSumNoFlyV2().setValueAt(timeIndex, Math.max(0.0, fg - bg));
			}

			spot.getFlyPresent().setIsPresentAt(timeIndex, nPointsFlyPresent);
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

