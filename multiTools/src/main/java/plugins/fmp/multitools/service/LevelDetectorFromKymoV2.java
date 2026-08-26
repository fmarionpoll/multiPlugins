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
import plugins.fmp.multitools.series.options.LevelDetectV2Options;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Kymograph top-level detection v2: optional tape prepass, temporal tracking,
 * first-crossing or edge-peak localization, then median/spike post-smooth.
 */
public class LevelDetectorFromKymoV2 {

	public void detectLevels(Experiment exp, BuildSeriesOptions batch, LevelDetectV2Options v2) {
		if (exp == null || batch == null || v2 == null)
			return;
		final SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return;

		seqKymos.getSequence().beginUpdate();
		seqKymos.getSequence().removeAllROI();
		initCapillaryKymoIndexes(exp);

		int firstKymo = batch.kymoFirst;
		if (firstKymo > seqKymos.getSequence().getSizeT() || firstKymo < 0)
			firstKymo = 0;
		int lastKymo = batch.kymoLast;
		if (lastKymo >= seqKymos.getSequence().getSizeT())
			lastKymo = seqKymos.getSequence().getSizeT() - 1;
		if (batch.detectSelectedKymo)
			lastKymo = firstKymo;

		final Processor processor = new Processor(SystemUtil.getNumberOfCPUs());
		processor.setThreadName("detectlevel-v2");
		processor.setPriority(Processor.NORM_PRIORITY);
		ArrayList<Future<?>> futures = new ArrayList<>(Math.max(1, lastKymo - firstKymo + 1));

		final LevelDetectV2Options v2Opts = v2.copy();
		final ImageTransformInterface colorTransform = v2Opts.transform.getFunction();
		final ImageTransformInterface hzAvg = ImageTransformEnums.MINUSHORIZAVG.getFunction();
		SequenceLoaderService loader = new SequenceLoaderService();

		for (int iKymo = firstKymo; iKymo <= lastKymo; iKymo++) {
			String fullPath = seqKymos.getFileNameFromImageList(iKymo);
			if (fullPath == null)
				continue;
			String nameWithoutExt = new File(fullPath).getName().replaceFirst("[.][^.]+$", "");
			final Capillary capi = exp.getCapillaries().getCapillaryFromKymographName(nameWithoutExt);
			if (capi == null)
				continue;
			if (!batch.detectR && capi.getKymographName() != null && capi.getKymographName().endsWith("2"))
				continue;
			if (!batch.detectL && capi.getKymographName() != null && capi.getKymographName().endsWith("1"))
				continue;

			capi.getDerivative().clear();
			capi.getGulps().clear();

			final IcyBufferedImage rawImage = loader.imageIORead(fullPath);
			if (rawImage == null)
				continue;

			futures.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					detectOneKymo(rawImage, capi, batch, v2Opts, colorTransform, hzAvg);
				}
			}));
		}

		waitFuturesCompletion(processor, futures);
		exp.save_capillaries_description_and_measures();
		exp.saveMCCapillaries_Only();
		seqKymos.getSequence().endUpdate();
	}

	private void detectOneKymo(IcyBufferedImage rawImage, Capillary capi, BuildSeriesOptions batch,
			LevelDetectV2Options v2, ImageTransformInterface colorTransform, ImageTransformInterface hzAvg) {
		int imageWidth = rawImage.getSizeX();
		int imageHeight = rawImage.getSizeY();
		Rectangle searchRect = batch.analyzePartOnly && batch.searchArea != null ? batch.searchArea
				: new Rectangle(0, 0, imageWidth, imageHeight);

		int columnFirst = (int) searchRect.getX();
		int columnLast = (int) (searchRect.getWidth() + columnFirst) - 1;
		if (columnFirst < 0)
			columnFirst = 0;
		if (columnLast >= imageWidth)
			columnLast = imageWidth - 1;
		if (columnLast < columnFirst)
			return;

		IcyBufferedImage working = rawImage;
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		if (v2.removeHorizontalAverage) {
			IcyBufferedImage pre = hzAvg.getTransformedImage(rawImage, transformOptions);
			if (pre != null)
				working = pre;
		}
		IcyBufferedImage transformed = colorTransform.getTransformedImage(working, transformOptions);
		if (transformed == null)
			return;
		Object transformedArray = transformed.getDataXY(0);
		int[] values = Array1DUtil.arrayToIntArray(transformedArray, transformed.isSignedDataType());

		int n = columnLast - columnFirst + 1;
		int[] topLimits = new int[n];
		detectTopSeries(values, imageWidth, imageHeight, searchRect, columnFirst, columnLast, v2, topLimits);
		LevelSeriesSmoother.smooth(topLimits, v2.medianWindow, v2.maxSpikePx);

		capi.getTopRaw().limit = topLimits;
		if (batch.analyzePartOnly) {
			ensureFullWidthPolylineForPartialUpdate(capi.getTopRaw(), imageWidth, columnLast);
			if (capi.getTopRaw().polylineLevel != null)
				capi.getTopRaw().polylineLevel.insertYPoints(topLimits, columnFirst, columnLast);
		} else {
			String topLevelName = capi.getLast2ofCapillaryName();
			if (topLevelName != null)
				capi.getTopRaw().setPolylineLevelFromTempData(topLevelName + "_toplevel", capi.getKymographIndex(),
						columnFirst, columnLast);
		}
		capi.getTopRaw().limit = null;
	}

	/**
	 * Per-column top localization with temporal tracking band.
	 */
	void detectTopSeries(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect, int firstColumn,
			int lastColumn, LevelDetectV2Options v2, int[] topLimits) {
		if (tabValues == null || topLimits == null || imageWidth <= 0 || imageHeight <= 0 || firstColumn > lastColumn)
			return;

		int searchTopY = 0;
		int searchBottomY = imageHeight - 1;
		if (searchRect != null) {
			searchTopY = Math.max(0, searchRect.y);
			searchBottomY = Math.min(imageHeight - 1, searchRect.y + searchRect.height - 1);
		}
		int globalMinY = Math.min(searchTopY + LevelDetectV2Options.TOP_SEARCH_OFFSET_PIXELS, imageHeight - 1);
		if (globalMinY > searchBottomY)
			globalMinY = searchTopY;

		int safeFirst = Math.max(0, firstColumn);
		int safeLast = Math.min(imageWidth - 1, lastColumn);
		Integer yPrev = null;

		for (int ix = safeFirst; ix <= safeLast; ix++) {
			int index = ix - firstColumn;
			if (index < 0 || index >= topLimits.length)
				continue;

			int yMin;
			int yMax;
			if (yPrev == null) {
				yMin = globalMinY;
				yMax = searchBottomY;
			} else {
				yMin = Math.max(globalMinY, yPrev - Math.max(0, v2.trackUp));
				yMax = Math.min(searchBottomY, yPrev + Math.max(0, v2.trackDown));
				if (yMin > yMax) {
					yMin = globalMinY;
					yMax = searchBottomY;
				}
			}

			int yFound;
			if (v2.edgePeak)
				yFound = localizeEdgePeak(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2);
			else
				yFound = localizeFirstCrossing(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2);

			topLimits[index] = yFound;
			yPrev = yFound;
		}
	}

	private static int localizeFirstCrossing(int[] tabValues, int imageWidth, int imageHeight, int ix, int yMin,
			int yMax, LevelDetectV2Options v2) {
		int yTop = yMax;
		for (int iy = yMin; iy <= yMax; iy++) {
			int offset = ix + iy * imageWidth;
			if (offset < 0 || offset >= tabValues.length)
				continue;
			int val = tabValues[offset];
			boolean passes = v2.directionUp ? (val > v2.threshold) : (val < v2.threshold);
			if (passes) {
				yTop = iy;
				break;
			}
		}
		return yTop;
	}

	/**
	 * Within [yMin, yMax], pick the row with strongest |Δv| that straddles the
	 * threshold (empty side above, liquid side below) when possible.
	 */
	private static int localizeEdgePeak(int[] tabValues, int imageWidth, int imageHeight, int ix, int yMin, int yMax,
			LevelDetectV2Options v2) {
		int bestY = yMin;
		int bestScore = -1;
		int bestStraddleY = -1;
		int bestStraddleScore = -1;

		for (int iy = yMin; iy < yMax; iy++) {
			int off0 = ix + iy * imageWidth;
			int off1 = ix + (iy + 1) * imageWidth;
			if (off0 < 0 || off1 >= tabValues.length)
				continue;
			int v0 = tabValues[off0];
			int v1 = tabValues[off1];
			int score = Math.abs(v1 - v0);
			if (score > bestScore) {
				bestScore = score;
				bestY = iy + 1;
			}
			boolean abovePasses = v2.directionUp ? (v0 > v2.threshold) : (v0 < v2.threshold);
			boolean belowPasses = v2.directionUp ? (v1 > v2.threshold) : (v1 < v2.threshold);
			// Prefer transition empty (above fails) -> liquid (below passes)
			if (!abovePasses && belowPasses && score > bestStraddleScore) {
				bestStraddleScore = score;
				bestStraddleY = iy + 1;
			}
		}
		if (bestStraddleY >= 0)
			return bestStraddleY;
		if (bestScore >= 0)
			return bestY;
		return localizeFirstCrossing(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2);
	}

	private void ensureFullWidthPolylineForPartialUpdate(CapillaryMeasure measure, int imageWidth,
			int columnLastInclusive) {
		if (measure == null || imageWidth <= 0)
			return;
		if (measure.polylineLevel == null || measure.polylineLevel.npoints <= columnLastInclusive)
			measure.polylineLevel = new Level2D(imageWidth);
	}

	private void waitFuturesCompletion(Processor processor, ArrayList<Future<?>> futures) {
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException | InterruptedException e) {
				Logger.error("LevelDetectorFromKymoV2:waitFuturesCompletion", e);
			}
		}
		processor.shutdown();
	}

	private void initCapillaryKymoIndexes(Experiment exp) {
		Collections.sort(exp.getCapillaries().getList(), new Comparators.Capillary_ROIName());
		List<String> kymographImagesList = exp.getSeqKymos().getImagesList();
		for (Capillary cap : exp.getCapillaries().getList()) {
			int i = cap.getKymographIndex();
			if (i < 0) {
				String roiName = cap.getRoiName();
				if (roiName != null) {
					String kymographName = Capillary.replace_LR_with_12(roiName);
					i = findKymographIndexByName(kymographImagesList, kymographName);
					if (i >= 0) {
						cap.setKymographIndex(i);
						cap.setKymographName(kymographName);
						if (cap.getKymographFileName() == null || cap.getKymographFileName().isEmpty())
							cap.setKymographFileName(kymographName + ".tiff");
					}
				}
			}
		}
	}

	private int findKymographIndexByName(List<String> kymographImagesList, String kymographName) {
		if (kymographImagesList == null || kymographImagesList.isEmpty() || kymographName == null)
			return -1;
		for (int i = 0; i < kymographImagesList.size(); i++) {
			String imagePath = kymographImagesList.get(i);
			String imageFilename = new File(imagePath).getName();
			String imageNameWithoutExt = imageFilename;
			int lastDotIndex = imageFilename.lastIndexOf('.');
			if (lastDotIndex > 0)
				imageNameWithoutExt = imageFilename.substring(0, lastDotIndex);
			if (imageNameWithoutExt.equals(kymographName))
				return i;
		}
		return -1;
	}
}
