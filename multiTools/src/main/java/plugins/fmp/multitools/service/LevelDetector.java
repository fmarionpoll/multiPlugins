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
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class LevelDetector {

	public void detectLevels(Experiment exp, BuildSeriesOptions options) {
		final SequenceKymos seqKymos = exp.getSeqKymos();
		seqKymos.getSequence().beginUpdate();
		seqKymos.getSequence().removeAllROI();
		initArrayToBuildCapillaries(exp, options);

		int tFirsKymo = options.kymoFirst;
		if (tFirsKymo > seqKymos.getSequence().getSizeT() || tFirsKymo < 0)
			tFirsKymo = 0;
		int tLastKymo = options.kymoLast;
		if (tLastKymo >= seqKymos.getSequence().getSizeT())
			tLastKymo = seqKymos.getSequence().getSizeT() - 1;

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectlevel");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<Future<?>>(tLastKymo - tFirsKymo + 1);
		futures.clear();

		final int jitter = 10;
		final ImageTransformInterface transformPass1 = options.transform01.getFunction();
		final ImageTransformInterface transformPass2 = options.transform02.getFunction();
		final Rectangle searchRect = options.searchArea;
		SequenceLoaderService loader = new SequenceLoaderService();

		for (int tKymo = tFirsKymo; tKymo <= tLastKymo; tKymo++) {
			String fullPath = seqKymos.getFileNameFromImageList(tKymo);
			String nameWithoutExt = new File(fullPath).getName().replaceFirst("[.][^.]+$", "");
			final Capillary capi = exp.getCapillaries().getCapillaryFromKymographName(nameWithoutExt);
			if (capi == null)
				continue;
			if (!options.detectR && capi.getKymographName().endsWith("2"))
				continue;
			if (!options.detectL && capi.getKymographName().endsWith("1"))
				continue;

			capi.getDerivative().clear();
			capi.getGulps().clear();
			capi.getProperties().getLimitsOptions().copyFrom(options);
			final IcyBufferedImage rawImage = loader.imageIORead(fullPath);

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					int imageWidth = rawImage.getSizeX();
					int imageHeight = rawImage.getSizeY();

					if (options.pass1)
						detectPass1(rawImage, transformPass1, capi, imageWidth, imageHeight, searchRect, jitter,
								options);

					if (options.pass2)
						detectPass2(rawImage, transformPass2, capi, imageWidth, imageHeight, searchRect, jitter,
								options);

					int columnFirst = (int) searchRect.getX();
					int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
					if (options.analyzePartOnly) {
						ensureFullWidthPolylineForPartialUpdate(capi.getTopLevel(), imageWidth, columnLast);
						ensureFullWidthPolylineForPartialUpdate(capi.getBottomLevel(), imageWidth, columnLast);
						if (capi.getTopLevel() != null && capi.getTopLevel().polylineLevel != null
								&& capi.getTopLevel().limit != null)
							capi.getTopLevel().polylineLevel.insertYPoints(capi.getTopLevel().limit, columnFirst,
									columnLast);
						if (capi.getBottomLevel() != null && capi.getBottomLevel().limit != null
								&& capi.getBottomLevel().polylineLevel != null)
							capi.getBottomLevel().polylineLevel.insertYPoints(capi.getBottomLevel().limit, columnFirst,
									columnLast);
					} else {
						if (capi.getTopLevel() != null) {
							String topLevelName = capi.getLast2ofCapillaryName();
							if (topLevelName != null)
								capi.getTopLevel().setPolylineLevelFromTempData(topLevelName + "_toplevel",
										capi.getKymographIndex(), columnFirst, columnLast);
						}

						if (capi.getBottomLevel() != null && capi.getBottomLevel().limit != null) {
							String bottomLevelName = capi.getLast2ofCapillaryName();
							if (bottomLevelName != null)
								capi.getBottomLevel().setPolylineLevelFromTempData(bottomLevelName + "_bottomlevel",
										capi.getKymographIndex(), columnFirst, columnLast);
						}
					}
					if (capi.getTopLevel() != null)
						capi.getTopLevel().limit = null;
					if (capi.getBottomLevel() != null)
						capi.getBottomLevel().limit = null;
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

	private void ensureFullWidthPolylineForPartialUpdate(CapillaryMeasure measure, int imageWidth, int columnLastInclusive) {
		if (measure == null || imageWidth <= 0)
			return;
		if (measure.polylineLevel == null || measure.polylineLevel.npoints <= columnLastInclusive)
			measure.polylineLevel = new Level2D(imageWidth);
	}

	private void detectPass1(IcyBufferedImage rawImage, ImageTransformInterface transformPass1, Capillary capi,
			int imageWidth, int imageHeight, Rectangle searchRect, int jitter, BuildSeriesOptions options) {
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage transformedImage1 = transformPass1.getTransformedImage(rawImage, transformOptions);
		Object transformedArray1 = transformedImage1.getDataXY(0);
		int[] transformed1DArray1 = Array1DUtil.arrayToIntArray(transformedArray1,
				transformedImage1.isSignedDataType());
		int topSearchFrom = 0;
		int columnFirst = (int) searchRect.getX();
		int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
		int n_measures = columnLast - columnFirst + 1;
		capi.getTopLevel().limit = new int[n_measures];
		capi.getBottomLevel().limit = new int[n_measures];
		if (options.runBackwards)
			for (int ix = columnLast; ix >= columnFirst; ix--)
				topSearchFrom = detectLimitOnOneColumn(ix, columnFirst, topSearchFrom, jitter, imageWidth, imageHeight,
						capi, transformed1DArray1, searchRect, options);
		else
			for (int ix = columnFirst; ix <= columnLast; ix++)
				topSearchFrom = detectLimitOnOneColumn(ix, columnFirst, topSearchFrom, jitter, imageWidth, imageHeight,
						capi, transformed1DArray1, searchRect, options);
	}

	private int detectLimitOnOneColumn(int ix, int istart, int topSearchFrom, int jitter, int imageWidth,
			int imageHeight, Capillary capi, int[] transformed1DArray1, Rectangle searchRect,
			BuildSeriesOptions options) {
		int iyTop = detectThresholdFromTop(ix, topSearchFrom, jitter, transformed1DArray1, imageWidth, imageHeight,
				options, searchRect);
		int iyBottom = detectThresholdFromBottom(ix, jitter, transformed1DArray1, imageWidth, imageHeight, options,
				searchRect);
		if (iyBottom <= iyTop)
			iyTop = topSearchFrom;
		capi.getTopLevel().limit[ix - istart] = iyTop;
		capi.getBottomLevel().limit[ix - istart] = iyBottom;
		return iyTop;
	}

	private void detectPass2(IcyBufferedImage rawImage, ImageTransformInterface transformPass2, Capillary capi,
			int imageWidth, int imageHeight, Rectangle searchRect, int jitter, BuildSeriesOptions options) {

		if (capi.getTopLevel().limit == null)
			capi.getTopLevel().setTempDataFromPolylineLevel();
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage transformedImage2 = transformPass2.getTransformedImage(rawImage, transformOptions);
		Object transformedArray2 = transformedImage2.getDataXY(0);
		int[] transformed1DArray2 = Array1DUtil.arrayToIntArray(transformedArray2,
				transformedImage2.isSignedDataType());
		int columnFirst = (int) searchRect.getX();
		int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
		switch (options.transform02) {
		case COLORDISTANCE_L1_Y:
		case COLORDISTANCE_L2_Y:
			findBestPosition(capi.getTopLevel().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		case SUBTRACT_1RSTCOL:
		case L1DIST_TO_1RSTCOL:
			detectThresholdUp(capi.getTopLevel().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		case DERICHE:
		case DERICHE_COLOR:
		case YDIFFN:
		case YDIFFN2:
		case MINUSHORIZAVG:
			findBestPosition(capi.getTopLevel().limit, columnFirst, columnLast, transformed1DArray2, imageWidth,
					imageHeight, options.jitter2, options.detectLevel2Threshold, options.directionUp2);
			break;
		default:
			break;
		}
	}

	public void findBestPosition(int[] limits, int firstColumn, int lastColumn, int[] transformed1DArray2,
			int imageWidth, int imageHeight, int delta, int threshold, boolean directionUp) {
		for (int ix = firstColumn; ix <= lastColumn; ix++) {
			int limitIndex = ix - firstColumn;
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
		for (int ix = firstColumn; ix <= lastColumn; ix++) {
			int limitIndex = ix - firstColumn;
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

	private int checkIndexLimits(int rowIndex, int maximumRowIndex) {
		if (rowIndex < 0)
			rowIndex = 0;
		if (rowIndex > maximumRowIndex)
			rowIndex = maximumRowIndex;
		return rowIndex;

	}

	private int detectThresholdFromTop(int ix, int searchFrom, int jitter, int[] tabValues, int imageWidth,
			int imageHeight, BuildSeriesOptions options, Rectangle searchRect) {
		int y = imageHeight - 1;
		searchFrom = checkIndexLimits(searchFrom - jitter, imageHeight - 1);
		if (searchFrom < searchRect.y)
			searchFrom = searchRect.y;
		for (int iy = searchFrom; iy < imageHeight; iy++) {
			boolean flag = false;
			if (options.directionUp1)
				flag = tabValues[ix + iy * imageWidth] > options.detectLevel1Threshold;
			else
				flag = tabValues[ix + iy * imageWidth] < options.detectLevel1Threshold;
			if (flag) {
				y = iy;
				break;
			}
		}
		return y;
	}

	private int detectThresholdFromBottom(int ix, int jitter, int[] tabValues, int imageWidth, int imageHeight,
			BuildSeriesOptions options, Rectangle searchRect) {
		int y = 0;
		int searchFrom = imageHeight - 1;
		if (searchFrom > (searchRect.y + searchRect.height))
			searchFrom = searchRect.y + searchRect.height - 1;
		for (int iy = searchFrom; iy >= 0; iy--) {
			boolean flag = false;
			if (options.directionUp1)
				flag = tabValues[ix + iy * imageWidth] > options.detectLevel1Threshold;
			else
				flag = tabValues[ix + iy * imageWidth] < options.detectLevel1Threshold;
			if (flag) {
				y = iy;
				break;
			}
		}
		return y;
	}

	private void initArrayToBuildCapillaries(Experiment exp, BuildSeriesOptions options) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		List<String> kymographImagesList = exp.getSeqKymos().getImagesList();
		for (Capillary cap : exp.getCapillaries().getList()) {
			int i = cap.getKymographIndex();
			if (i < 0) {
				// Derive kymograph name from current ROI name (not from potentially stale metadata)
				// This handles cases where capillaries have been deleted after kymograph generation
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
						System.out.println(
								"buildCapillaries - ROI=" + roiName + " index=" + cap.getKymographIndex() + " name=" + cap.getKymographFileName());
					} else {
						System.out.println(
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