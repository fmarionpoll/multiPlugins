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
 * Kymograph top-level detection v2: optional Hz-avg / tape-row prepass, temporal
 * tracking, first-crossing or edge-peak localization, then median/spike
 * post-smooth.
 */
public class LevelDetectorFromKymoV2 {

	private static final double TAPE_MIN_COHERENCE = 0.35;
	private static final int TAPE_MIN_EDGE = 12;
	private static final int TAPE_MAX_ROWS = 3;
	private static final int TAPE_TOLERANCE_PX = 1;

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

		int[] topLimits = computeTopLimits(rawImage, searchRect, v2);
		if (topLimits == null)
			return;

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
	 * Shared by Detect and View overlay: transform + track (+ optional bidirectional
	 * merge when runBackwards) + post-smooth. Returns limits for columns in the
	 * search rect (length = columnLast - columnFirst + 1), or null on failure.
	 */
	public int[] computeTopLimits(IcyBufferedImage rawImage, Rectangle searchRect, LevelDetectV2Options v2) {
		if (rawImage == null || v2 == null || v2.transform == null)
			return null;
		int imageWidth = rawImage.getSizeX();
		int imageHeight = rawImage.getSizeY();
		if (searchRect == null)
			searchRect = new Rectangle(0, 0, imageWidth, imageHeight);
		int columnFirst = Math.max(0, searchRect.x);
		int columnLast = Math.min(imageWidth - 1, searchRect.x + searchRect.width - 1);
		if (columnLast < columnFirst)
			return null;

		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		IcyBufferedImage working = rawImage;
		if (v2.removeHorizontalAverage) {
			IcyBufferedImage pre = ImageTransformEnums.MINUSHORIZAVG.getFunction().getTransformedImage(rawImage,
					transformOptions);
			if (pre != null)
				working = pre;
		}
		IcyBufferedImage transformed = v2.transform.getFunction().getTransformedImage(working, transformOptions);
		if (transformed == null)
			return null;
		Object transformedArray = transformed.getDataXY(0);
		int[] values = Array1DUtil.arrayToIntArray(transformedArray, transformed.isSignedDataType());

		int n = columnLast - columnFirst + 1;
		int[] topLimits = new int[n];
		int[] tapeRows = v2.tapePrepass
				? findHorizontalTapeRows(values, imageWidth, imageHeight, searchRect, columnFirst, columnLast)
				: null;
		detectTopSeries(values, imageWidth, imageHeight, searchRect, columnFirst, columnLast, v2, tapeRows, topLimits);
		LevelSeriesSmoother.smooth(topLimits, v2.medianWindow, v2.maxSpikePx);
		return topLimits;
	}

	/**
	 * Find thin, persistent horizontal seams (background tape) as row Y indices.
	 * Scores rows by vertical thinness + edge strength coherence across columns.
	 */
	static int[] findHorizontalTapeRows(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect,
			int columnFirst, int columnLast) {
		if (tabValues == null || imageWidth <= 0 || imageHeight < 3)
			return null;
		int y0 = 1;
		int y1 = imageHeight - 2;
		if (searchRect != null) {
			y0 = Math.max(1, searchRect.y);
			y1 = Math.min(imageHeight - 2, searchRect.y + searchRect.height - 1);
		}
		int x0 = Math.max(0, columnFirst);
		int x1 = Math.min(imageWidth - 1, columnLast);
		if (x1 < x0 || y1 <= y0)
			return null;

		int nCol = x1 - x0 + 1;
		int nY = y1 - y0 + 1;
		double[] score = new double[nY];
		double[] coherence = new double[nY];
		for (int iy = y0; iy <= y1; iy++) {
			int hits = 0;
			long edgeSum = 0;
			for (int ix = x0; ix <= x1; ix++) {
				int off = ix + iy * imageWidth;
				int offA = off - imageWidth;
				int offB = off + imageWidth;
				if (offA < 0 || offB >= tabValues.length)
					continue;
				int vA = tabValues[offA];
				int v = tabValues[off];
				int vB = tabValues[offB];
				int edge = Math.abs(v - vA) + Math.abs(vB - v);
				boolean thin = (v <= vA && v <= vB) || (v >= vA && v >= vB);
				if (thin && edge >= TAPE_MIN_EDGE) {
					hits++;
					edgeSum += edge;
				}
			}
			int idx = iy - y0;
			coherence[idx] = hits / (double) nCol;
			score[idx] = hits > 0 ? (edgeSum / (double) hits) * coherence[idx] : 0;
		}

		boolean[] isPeak = new boolean[nY];
		for (int i = 0; i < nY; i++) {
			if (coherence[i] < TAPE_MIN_COHERENCE)
				continue;
			double s = score[i];
			double left = i > 0 ? score[i - 1] : 0;
			double right = i + 1 < nY ? score[i + 1] : 0;
			if (s >= left && s >= right && s > 0)
				isPeak[i] = true;
		}

		List<Integer> peaks = new ArrayList<>(TAPE_MAX_ROWS);
		for (int k = 0; k < TAPE_MAX_ROWS; k++) {
			int bestI = -1;
			double bestS = -1;
			for (int i = 0; i < nY; i++) {
				if (!isPeak[i])
					continue;
				if (score[i] > bestS) {
					bestS = score[i];
					bestI = i;
				}
			}
			if (bestI < 0)
				break;
			peaks.add(y0 + bestI);
			isPeak[bestI] = false;
			if (bestI > 0)
				isPeak[bestI - 1] = false;
			if (bestI + 1 < nY)
				isPeak[bestI + 1] = false;
		}
		if (peaks.isEmpty())
			return null;
		Collections.sort(peaks);
		int[] rows = new int[peaks.size()];
		for (int i = 0; i < peaks.size(); i++)
			rows[i] = peaks.get(i);
		return rows;
	}

	private static boolean isNearTapeRow(int y, int[] tapeRows) {
		if (tapeRows == null)
			return false;
		for (int row : tapeRows) {
			if (Math.abs(y - row) <= TAPE_TOLERANCE_PX)
				return true;
		}
		return false;
	}

	/**
	 * Per-column top localization with temporal tracking. When
	 * {@code runBackwards}, runs forward and backward passes then fuses them
	 * left-to-right: keep a candidate only if it stays within the track band of
	 * the previous column (prefer the higher meniscus). If both jump away (typical
	 * tape-shadow lock), hold the previous Y instead of following the dip.
	 */
	public void detectTopSeries(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect, int firstColumn,
			int lastColumn, LevelDetectV2Options v2, int[] topLimits) {
		detectTopSeries(tabValues, imageWidth, imageHeight, searchRect, firstColumn, lastColumn, v2, null, topLimits);
	}

	public void detectTopSeries(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect, int firstColumn,
			int lastColumn, LevelDetectV2Options v2, int[] tapeRows, int[] topLimits) {
		if (tabValues == null || topLimits == null || imageWidth <= 0 || imageHeight <= 0 || firstColumn > lastColumn)
			return;

		if (v2.runBackwards) {
			int[] yFwd = new int[topLimits.length];
			int[] yBwd = new int[topLimits.length];
			fillTrackedSeries(tabValues, imageWidth, imageHeight, searchRect, firstColumn, lastColumn, v2, tapeRows,
					false, yFwd);
			fillTrackedSeries(tabValues, imageWidth, imageHeight, searchRect, firstColumn, lastColumn, v2, tapeRows,
					true, yBwd);
			fuseTrackedSeries(yFwd, yBwd, v2, topLimits);
		} else {
			fillTrackedSeries(tabValues, imageWidth, imageHeight, searchRect, firstColumn, lastColumn, v2, tapeRows,
					false, topLimits);
		}
	}

	/**
	 * Causal left-to-right fuse of forward and backward tracks. Rejects sudden
	 * dives (tape shadow) that would break continuity with the previous column.
	 */
	private static void fuseTrackedSeries(int[] yFwd, int[] yBwd, LevelDetectV2Options v2, int[] out) {
		if (yFwd == null || yBwd == null || out == null || yFwd.length == 0)
			return;
		int trackUp = Math.max(0, v2.trackUp);
		int trackDown = Math.max(0, v2.trackDown);
		out[0] = Math.min(yFwd[0], yBwd[0]);
		for (int i = 1; i < out.length; i++) {
			int prev = out[i - 1];
			int cF = yFwd[i];
			int cB = yBwd[i];
			boolean fOk = cF >= prev - trackUp && cF <= prev + trackDown;
			boolean bOk = cB >= prev - trackUp && cB <= prev + trackDown;
			if (fOk && bOk)
				out[i] = Math.min(cF, cB);
			else if (fOk)
				out[i] = cF;
			else if (bOk)
				out[i] = cB;
			else
				out[i] = prev;
		}
	}

	private void fillTrackedSeries(int[] tabValues, int imageWidth, int imageHeight, Rectangle searchRect,
			int firstColumn, int lastColumn, LevelDetectV2Options v2, int[] tapeRows, boolean backwards,
			int[] topLimits) {
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
		int ixStart = backwards ? safeLast : safeFirst;
		int ixEnd = backwards ? safeFirst : safeLast;
		int bandUp = backwards ? Math.max(0, v2.trackDown) : Math.max(0, v2.trackUp);
		int bandDown = backwards ? Math.max(0, v2.trackUp) : Math.max(0, v2.trackDown);

		for (int ix = ixStart; backwards ? ix >= ixEnd : ix <= ixEnd; ix += (backwards ? -1 : 1)) {
			int index = ix - firstColumn;
			if (index < 0 || index >= topLimits.length)
				continue;

			int yMin;
			int yMax;
			if (yPrev == null) {
				yMin = globalMinY;
				yMax = searchBottomY;
			} else {
				yMin = Math.max(globalMinY, yPrev - bandUp);
				yMax = Math.min(searchBottomY, yPrev + bandDown);
				if (yMin > yMax) {
					yMin = globalMinY;
					yMax = searchBottomY;
				}
			}

			int yFound;
			if (v2.edgePeak)
				yFound = localizeEdgePeak(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2, tapeRows);
			else
				yFound = localizeFirstCrossing(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2, tapeRows);

			topLimits[index] = yFound;
			yPrev = yFound;
		}
	}

	private static int localizeFirstCrossing(int[] tabValues, int imageWidth, int imageHeight, int ix, int yMin,
			int yMax, LevelDetectV2Options v2, int[] tapeRows) {
		int yTop = yMax;
		for (int iy = yMin; iy <= yMax; iy++) {
			int offset = ix + iy * imageWidth;
			if (offset < 0 || offset >= tabValues.length)
				continue;
			int val = tabValues[offset];
			boolean passes = v2.directionUp ? (val > v2.threshold) : (val < v2.threshold);
			if (!passes)
				continue;
			if (isNearTapeRow(iy, tapeRows)) {
				int below = findNextStraddle(tabValues, imageWidth, ix, iy + 1 + TAPE_TOLERANCE_PX, yMax, v2,
						tapeRows);
				if (below >= 0)
					return below;
			}
			yTop = iy;
			break;
		}
		return yTop;
	}

	/** First empty→liquid threshold straddle at or below yFrom; returns below-side Y, or -1. */
	private static int findNextStraddle(int[] tabValues, int imageWidth, int ix, int yFrom, int yMax,
			LevelDetectV2Options v2, int[] tapeRows) {
		if (yFrom > yMax)
			return -1;
		int start = Math.max(0, yFrom - 1);
		for (int iy = start; iy < yMax; iy++) {
			int edgeY = iy + 1;
			if (edgeY < yFrom)
				continue;
			int off0 = ix + iy * imageWidth;
			int off1 = ix + edgeY * imageWidth;
			if (off0 < 0 || off1 >= tabValues.length)
				continue;
			int v0 = tabValues[off0];
			int v1 = tabValues[off1];
			boolean abovePasses = v2.directionUp ? (v0 > v2.threshold) : (v0 < v2.threshold);
			boolean belowPasses = v2.directionUp ? (v1 > v2.threshold) : (v1 < v2.threshold);
			if (!abovePasses && belowPasses) {
				if (isNearTapeRow(edgeY, tapeRows) || isNearTapeRow(iy, tapeRows))
					continue;
				return edgeY;
			}
		}
		return -1;
	}

	/**
	 * Within [yMin, yMax], take the first top-down empty->liquid threshold straddle
	 * with a local |Δv| peak (not the global strongest edge). Skips tape-row edges
	 * when a second liquid edge exists below.
	 */
	private static int localizeEdgePeak(int[] tabValues, int imageWidth, int imageHeight, int ix, int yMin, int yMax,
			LevelDetectV2Options v2, int[] tapeRows) {
		int firstCrossing = localizeFirstCrossing(tabValues, imageWidth, imageHeight, ix, yMin, yMax, v2, tapeRows);
		int searchLo = Math.max(yMin, firstCrossing - 8);
		int searchHi = Math.min(yMax, firstCrossing + 8);
		if (searchLo >= searchHi)
			return firstCrossing;

		int bestStraddleY = -1;
		int bestStraddleScore = -1;
		int bestY = firstCrossing;
		int bestScore = -1;
		for (int iy = searchLo; iy < searchHi; iy++) {
			int off0 = ix + iy * imageWidth;
			int off1 = ix + (iy + 1) * imageWidth;
			if (off0 < 0 || off1 >= tabValues.length)
				continue;
			int v0 = tabValues[off0];
			int v1 = tabValues[off1];
			int score = Math.abs(v1 - v0);
			boolean abovePasses = v2.directionUp ? (v0 > v2.threshold) : (v0 < v2.threshold);
			boolean belowPasses = v2.directionUp ? (v1 > v2.threshold) : (v1 < v2.threshold);
			int edgeY = iy + 1;
			if (isNearTapeRow(edgeY, tapeRows) || isNearTapeRow(iy, tapeRows))
				continue;
			if (score > bestScore) {
				bestScore = score;
				bestY = edgeY;
			}
			if (!abovePasses && belowPasses && score > bestStraddleScore) {
				bestStraddleScore = score;
				bestStraddleY = edgeY;
			}
		}
		if (bestStraddleY >= 0)
			return bestStraddleY;
		return bestY;
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
