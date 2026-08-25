package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import icy.image.IcyBufferedImage;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class LevelDetectorFromKymo {

	private static final int TOP_SEARCH_OFFSET_PIXELS = 5;

	public void detectLevels(Experiment exp, BuildSeriesOptions options) {
		final SequenceKymos seqKymos = exp.getSeqKymos();
		seqKymos.getSequence().beginUpdate();
		seqKymos.getSequence().removeAllROI();
		initArrayToBuildCapillaries(exp, options);

		int firsKymo = options.kymoFirst;
		if (firsKymo > seqKymos.getSequence().getSizeT() || firsKymo < 0)
			firsKymo = 0;
		int lastKymo = options.kymoLast;
		if (lastKymo >= seqKymos.getSequence().getSizeT())
			lastKymo = seqKymos.getSequence().getSizeT() - 1;
		if (options.detectSelectedKymo) {
			lastKymo = firsKymo;
		}

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectlevel");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>(lastKymo - firsKymo + 1);
		futures.clear();

		final int jitter = 10;
		final ImageTransformInterface transformPass1 = options.transform01.getFunction();
		final ImageTransformInterface transformPass2 = options.transform02.getFunction();
		SequenceLoaderService loader = new SequenceLoaderService();

		for (int iKymo = firsKymo; iKymo <= lastKymo; iKymo++) {
			String fullPath = seqKymos.getFileNameFromImageList(iKymo);
			String nameWithoutExt = new File(fullPath).getName().replaceFirst("[.][^.]+$", "");
			final Capillary capi = exp.getCapillaries().getCapillaryFromKymographName(nameWithoutExt);
			if (capi == null)
				continue;
			if (!options.detectR && capi.getKymographName().endsWith("2"))
				continue;
			if (!options.detectL && capi.getKymographName().endsWith("1"))
				continue;

			if (options.detectTop) {
				capi.getDerivative().clear();
				capi.getGulps().clear();
			}
			DetectionProvenanceSupport.copyLevelRecipeTo(capi.getProperties().getLimitsOptions(), options);
			final IcyBufferedImage rawImage = loader.imageIORead(fullPath);

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					int imageWidth = rawImage.getSizeX();
					int imageHeight = rawImage.getSizeY();
					final Rectangle searchRect = options.analyzePartOnly && options.searchArea != null
							? options.searchArea
							: new Rectangle(0, 0, imageWidth, imageHeight);

					if (options.pass1)
						detectPass1(rawImage, transformPass1, capi, imageWidth, imageHeight, searchRect, options);

					if (options.pass2 && options.detectTop)
						detectPass2(rawImage, transformPass2, capi, imageWidth, imageHeight, searchRect, jitter,
								options);

					int columnFirst = (int) searchRect.getX();
					int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
					if (columnFirst < 0)
						columnFirst = 0;
					if (columnLast >= imageWidth)
						columnLast = imageWidth - 1;
					if (columnLast < columnFirst)
						return;
					if (options.analyzePartOnly) {
						if (options.detectTop) {
							ensureFullWidthPolylineForPartialUpdate(capi.getTopRaw(), imageWidth, columnLast);
							if (capi.getTopRaw() != null && capi.getTopRaw().polylineLevel != null
									&& capi.getTopRaw().limit != null)
								capi.getTopRaw().polylineLevel.insertYPoints(capi.getTopRaw().limit, columnFirst,
										columnLast);
						}
						if (options.detectBottom) {
							ensureFullWidthPolylineForPartialUpdate(capi.getBottomRaw(), imageWidth, columnLast);
							if (capi.getBottomRaw() != null && capi.getBottomRaw().limit != null
									&& capi.getBottomRaw().polylineLevel != null)
								capi.getBottomRaw().polylineLevel.insertYPoints(capi.getBottomRaw().limit, columnFirst,
										columnLast);
						}
					} else {
						if (options.detectTop && capi.getTopRaw() != null && capi.getTopRaw().limit != null) {
							String topLevelName = capi.getLast2ofCapillaryName();
							if (topLevelName != null)
								capi.getTopRaw().setPolylineLevelFromTempData(topLevelName + "_toplevel",
										capi.getKymographIndex(), columnFirst, columnLast);
						}

						if (options.detectBottom && capi.getBottomRaw() != null && capi.getBottomRaw().limit != null) {
							String bottomLevelName = capi.getLast2ofCapillaryName();
							if (bottomLevelName != null)
								capi.getBottomRaw().setPolylineLevelFromTempData(bottomLevelName + "_bottomlevel",
										capi.getKymographIndex(), columnFirst, columnLast);
						}
					}
					if (capi.getTopRaw() != null)
						capi.getTopRaw().limit = null;
					if (capi.getBottomRaw() != null)
						capi.getBottomRaw().limit = null;
				}
			}));
		}

		waitFuturesCompletion(processor, futures);
		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		seqKymos.getSequence().endUpdate();
	}

	private void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futures) {
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException | InterruptedException e) {
				Logger.error("LevelDetector:waitFuturesCompletion", e);
			}
		}
		processor.shutdown();
	}

	private void ensureFullWidthPolylineForPartialUpdate(CapillaryMeasure measure, int imageWidth,
			int columnLastInclusive) {
		if (measure == null || imageWidth <= 0)
			return;
		if (measure.polylineLevel == null || measure.polylineLevel.npoints <= columnLastInclusive)
			measure.polylineLevel = new Level2D(imageWidth);
	}

	private void detectPass1(IcyBufferedImage rawImage, ImageTransformInterface transformPass1, Capillary capi,
			int imageWidth, int imageHeight, Rectangle searchRect, BuildSeriesOptions options) {
		boolean doTop = options.detectTop;
		boolean doBottom = options.detectBottom;
		if (!doTop && !doBottom)
			return;

		int columnFirst = (int) searchRect.getX();
		int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
		if (columnFirst < 0)
			columnFirst = 0;
		if (columnLast >= imageWidth)
			columnLast = imageWidth - 1;
		if (columnLast < columnFirst)
			return;
		int n_measures = columnLast - columnFirst + 1;

		boolean sameTransformForBottom = doTop && doBottom && options.transformBottom == options.transform01
				&& options.detectLevelBottomThreshold == options.detectLevel1Threshold
				&& options.directionUpBottom == options.directionUp1;

		int[] topLimits = null;
		int[] bottomLimits = null;
		if (doTop) {
			capi.getTopRaw().limit = new int[n_measures];
			topLimits = capi.getTopRaw().limit;
		}
		if (doBottom) {
			capi.getBottomRaw().limit = new int[n_measures];
			bottomLimits = capi.getBottomRaw().limit;
		}

		if (doTop) {
			CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
			IcyBufferedImage transformedImage1 = transformPass1.getTransformedImage(rawImage, transformOptions);
			Object transformedArray1 = transformedImage1.getDataXY(0);
			int[] transformed1DArray1 = Array1DUtil.arrayToIntArray(transformedArray1,
					transformedImage1.isSignedDataType());
			computeTopThresholds(transformed1DArray1, imageWidth, imageHeight, searchRect, options.directionUp1,
					options.detectLevel1Threshold, columnFirst, columnLast, topLimits);
			if (doBottom && sameTransformForBottom) {
				computeBottomThresholds(transformed1DArray1, imageWidth, imageHeight, searchRect,
						options.directionUpBottom, options.detectLevelBottomThreshold,
						options.bottomSearchFromBottomPx, columnFirst, columnLast, bottomLimits);
			}
		}

		if (doBottom && !(doTop && sameTransformForBottom)) {
			ImageTransformInterface bottomTransform = options.transformBottom != null
					? options.transformBottom.getFunction()
					: transformPass1;
			CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
			IcyBufferedImage transformedBottom = bottomTransform.getTransformedImage(rawImage, transformOptions);
			Object transformedArray = transformedBottom.getDataXY(0);
			int[] transformed1D = Array1DUtil.arrayToIntArray(transformedArray, transformedBottom.isSignedDataType());
			computeBottomThresholds(transformed1D, imageWidth, imageHeight, searchRect, options.directionUpBottom,
					options.detectLevelBottomThreshold, options.bottomSearchFromBottomPx, columnFirst, columnLast,
					bottomLimits);
		}
	}

	private void detectPass2(IcyBufferedImage rawImage, ImageTransformInterface transformPass2, Capillary capi,
			int imageWidth, int imageHeight, Rectangle searchRect, int jitter, BuildSeriesOptions options) {

		if (capi.getTopRaw().limit == null)
			capi.getTopRaw().setTempDataFromPolylineLevel();
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage transformedImage2 = transformPass2.getTransformedImage(rawImage, transformOptions);
		Object transformedArray2 = transformedImage2.getDataXY(0);
		int[] transformed1DArray2 = Array1DUtil.arrayToIntArray(transformedArray2,
				transformedImage2.isSignedDataType());
		int columnFirst = (int) searchRect.getX();
		int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
		if (columnFirst < 0)
			columnFirst = 0;
		if (columnLast >= imageWidth)
			columnLast = imageWidth - 1;
		if (columnLast < columnFirst)
			return;
		switch (options.transform02) {
		case COLORDISTANCE_L1_Y:
		case COLORDISTANCE_L2_Y:
			findBestPosition(capi.getTopRaw().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		case SUBTRACT_1RSTCOL:
		case L1DIST_TO_1RSTCOL:
			detectThresholdUp(capi.getTopRaw().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		case DERICHE:
		case DERICHE_COLOR:
		case YDIFFN:
		case YDIFFN2:
		case MINUSHORIZAVG:
			findBestPosition(capi.getTopRaw().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		default:
			break;
		}
	}

	public void findBestPosition(int[] limits, int firstColumn, int lastColumn, int[] transformed1DArray2,
			int imageWidth, int imageHeight, int delta, int threshold, boolean directionUp) {
		if (limits == null || transformed1DArray2 == null || imageWidth <= 0 || imageHeight <= 0)
			return;
		int safeFirst = Math.max(0, firstColumn);
		int safeLast = Math.min(imageWidth - 1, lastColumn);
		if (safeLast < safeFirst)
			return;
		for (int ix = safeFirst; ix <= safeLast; ix++) {
			int limitIndex = ix - firstColumn;
			if (limitIndex < 0 || limitIndex >= limits.length)
				continue;
			int iy = limits[limitIndex];
			int maxVal = Integer.MIN_VALUE;
			int iyVal = iy;
			boolean foundCandidate = false;
			for (int irow = iy + delta; irow > iy - delta; irow--) {
				if (irow < 0 || irow >= imageHeight)
					continue;
				int val = transformed1DArray2[ix + irow * imageWidth];
				boolean meetsThreshold;
				if (directionUp)
					meetsThreshold = val > threshold;
				else
					meetsThreshold = val < threshold;
				if (meetsThreshold) {
					if (!foundCandidate || val > maxVal) {
						maxVal = val;
						iyVal = irow;
						foundCandidate = true;
					}
				}
			}
			if (foundCandidate) {
				limits[limitIndex] = iyVal;
			}
		}
	}

	public void detectThresholdUp(int[] limits, int firstColumn, int lastColumn, int[] transformed1DArray2,
			int imageWidth, int imageHeight, int delta, int threshold, boolean directionUp) {
		if (limits == null || transformed1DArray2 == null || imageWidth <= 0 || imageHeight <= 0)
			return;
		int safeFirst = Math.max(0, firstColumn);
		int safeLast = Math.min(imageWidth - 1, lastColumn);
		if (safeLast < safeFirst)
			return;
		for (int ix = safeFirst; ix <= safeLast; ix++) {
			int limitIndex = ix - firstColumn;
			if (limitIndex < 0 || limitIndex >= limits.length)
				continue;
			int iy = limits[limitIndex];
			int iyVal = iy;
			for (int irow = iy + delta; irow > iy - delta; irow--) {
				if (irow < 0 || irow >= imageHeight)
					continue;
				int val = transformed1DArray2[ix + irow * imageWidth];
				boolean meetsThreshold;
				if (directionUp)
					meetsThreshold = val > threshold;
				else
					meetsThreshold = val < threshold;
				if (meetsThreshold) {
					iyVal = irow;
					break;
				}
			}
			limits[limitIndex] = iyVal;
		}

	}

	/**
	 * Computes top and bottom threshold crossings for each column. Retained for
	 * overlay preview compatibility; delegates to independent top/bottom scanners.
	 */
	public void computeTopBottomThresholds(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect,
			boolean directionUp, int threshold, int firstColumn, int lastColumn, int[] topLimits,
			int[] bottomLimits) {
		computeTopThresholds(tabValues, imageWidth, imageHeight, searchRect, directionUp, threshold, firstColumn,
				lastColumn, topLimits);
		computeBottomThresholds(tabValues, imageWidth, imageHeight, searchRect, directionUp, threshold, 0, firstColumn,
				lastColumn, bottomLimits);
	}

	public void computeTopThresholds(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect,
			boolean directionUp, int threshold, int firstColumn, int lastColumn, int[] topLimits) {
		if (tabValues == null || topLimits == null || imageWidth <= 0 || imageHeight <= 0 || firstColumn > lastColumn)
			return;

		int searchTopY = 0;
		if (searchRect != null) {
			searchTopY = Math.max(0, searchRect.y);
		}
		int minTopStart = Math.min(searchTopY + TOP_SEARCH_OFFSET_PIXELS, imageHeight - 1);
		int safeFirst = Math.max(0, firstColumn);
		int safeLast = Math.min(imageWidth - 1, lastColumn);
		if (safeLast < safeFirst)
			return;

		for (int ix = safeFirst; ix <= safeLast; ix++) {
			int index = ix - firstColumn;
			if (index < 0 || index >= topLimits.length)
				continue;
			int yTop = imageHeight - 1;
			for (int iy = minTopStart; iy < imageHeight; iy++) {
				int offset = ix + iy * imageWidth;
				if (offset < 0 || offset >= tabValues.length)
					continue;
				int val = tabValues[offset];
				boolean passes = directionUp ? (val > threshold) : (val < threshold);
				if (passes) {
					yTop = iy;
					break;
				}
			}
			topLimits[index] = yTop;
		}
	}

	public void computeBottomThresholds(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect,
			boolean directionUp, int threshold, int searchFromBottomPx, int firstColumn, int lastColumn,
			int[] bottomLimits) {
		if (tabValues == null || bottomLimits == null || imageWidth <= 0 || imageHeight <= 0 || firstColumn > lastColumn)
			return;

		int searchBottomY = imageHeight - 1;
		int searchTopY = 0;
		if (searchRect != null) {
			searchTopY = Math.max(0, searchRect.y);
			searchBottomY = searchRect.y + searchRect.height - 1;
			if (searchBottomY >= imageHeight)
				searchBottomY = imageHeight - 1;
		}
		int minY = searchTopY;
		if (searchFromBottomPx > 0) {
			minY = Math.max(minY, searchBottomY - searchFromBottomPx + 1);
		}

		int safeFirst = Math.max(0, firstColumn);
		int safeLast = Math.min(imageWidth - 1, lastColumn);
		if (safeLast < safeFirst)
			return;

		for (int ix = safeFirst; ix <= safeLast; ix++) {
			int index = ix - firstColumn;
			if (index < 0 || index >= bottomLimits.length)
				continue;
			int yBottom = 0;
			for (int iy = searchBottomY; iy >= minY; iy--) {
				int offset = ix + iy * imageWidth;
				if (offset < 0 || offset >= tabValues.length)
					continue;
				int val = tabValues[offset];
				boolean passes = directionUp ? (val > threshold) : (val < threshold);
				if (passes) {
					yBottom = iy;
					break;
				}
			}
			bottomLimits[index] = yBottom;
		}
	}

	private void initArrayToBuildCapillaries(Experiment exp, BuildSeriesOptions options) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		List<String> kymographImagesList = exp.getSeqKymos().getImagesList();
		for (Capillary cap : exp.getCapillaries().getList()) {
			int i = cap.getKymographIndex();
			if (i < 0) {
				// Derive kymograph name from current ROI name (not from potentially stale
				// metadata)
				// This handles cases where capillaries have been deleted after kymograph
				// generation
				String roiName = cap.getRoiName();
				if (roiName != null) {
					String kymographName = Capillary.replace_LR_with_12(roiName);
					i = findKymographIndexByName(kymographImagesList, kymographName);
					if (i >= 0) {
						cap.setKymographIndex(i);
						cap.setKymographName(kymographName);
						if (cap.getKymographFileName() == null || cap.getKymographFileName().isEmpty()) {
							cap.setKymographFileName(kymographName + ".tiff");
						}
						Logger.debug("buildCapillaries - ROI=" + roiName + " index=" + cap.getKymographIndex()
								+ " name=" + cap.getKymographFileName());
					} else {
						Logger.warn(
								"buildCapillaries - ROI=" + roiName + " kymograph not found for name=" + kymographName);
					}
				}
			}
		}
	}

	/**
	 * Finds kymograph index by searching for the name in the image list.
	 * 
	 * @param kymographImagesList list of kymograph image file paths
	 * @param kymographName       name to search for (without extension)
	 * @return index in the list, or -1 if not found
	 */
	private int findKymographIndexByName(List<String> kymographImagesList, String kymographName) {
		if (kymographImagesList == null || kymographImagesList.isEmpty() || kymographName == null) {
			return -1;
		}

		for (int i = 0; i < kymographImagesList.size(); i++) {
			String imagePath = kymographImagesList.get(i);
			String imageFilename = new File(imagePath).getName();

			// Remove extension from image filename to compare with kymographName
			String imageNameWithoutExt = imageFilename;
			int lastDotIndex = imageFilename.lastIndexOf('.');
			if (lastDotIndex > 0) {
				imageNameWithoutExt = imageFilename.substring(0, lastDotIndex);
			}

			if (imageNameWithoutExt.equals(kymographName)) {
				return i;
			}
		}

		return -1;
	}
}