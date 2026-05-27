package plugins.fmp.multitools.service;

import java.util.Arrays;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.Logger;

/**
 * Per spot band, each horizontal row (fixed {@code y}) is scanned along time ({@code x}). A pixel is
 * <em>good</em> when it passes the same gates as the spot metric overlay (valid sum RGB and spot metric above
 * threshold), and when {@link CageKymoAnalyzer.Params#insectMetricGateEnabled} is on, it must not be classified as
 * insect-like by the insect metric (so fly pixels are not used as bracket anchors). A maximal run of <em>bad</em>
 * pixels strictly between a good pixel on the left and a good pixel on the right is filled with a constant per
 * channel: the average of the bracketing good pixels. Only gaps whose length (columns) is ≤
 * {@link CageKymoAnalyzer.Params#maxRowOcclusionGapColumns} are modified. Leading/trailing bad runs are left
 * unchanged unless the insect gate is on: a leading prefix whose columns are all insect-marked in the band may be
 * filled from the first good bracket pixel (same rule as {@link KymoStripPostLiftMetricMask}). Bands do not interact;
 * rows outside bands are unchanged.
 */
public final class KymoStripRowwiseOcclusionFill {

	private static final int MIN_BRACKET_RUN = 2;
	private static final int MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS = 262144;

	private KymoStripRowwiseOcclusionFill() {
	}

	public static IcyBufferedImage apply(IcyBufferedImage rgb, List<CageKymographSpotBands> bands,
			CageKymoAnalyzer.Params params) {
		if (rgb == null || bands == null || params == null || !params.rowwiseOcclusionFill) {
			return rgb;
		}
		boolean anyBand = false;
		for (CageKymographSpotBands b : bands) {
			if (b != null && !b.geometryMissing) {
				anyBand = true;
				break;
			}
		}
		if (!anyBand) {
			return rgb;
		}
		IcyBufferedImage work;
		try {
			work = IcyBufferedImageUtil.getCopy(rgb);
		} catch (Exception e) {
			Logger.warn("KymoStripRowwiseOcclusionFill: getCopy failed", e);
			return rgb;
		}
		int w = rgb.getSizeX();
		int h = rgb.getSizeY();
		int nC = Math.max(1, rgb.getSizeC());
		IcyBufferedImage metricImg = KymoImageTransforms.applyMetricTransform(rgb, params.metricTransform,
				params.useGpuTransforms);
		double[] metric = KymoImageTransforms.channel0AsDouble(metricImg);
		int metricLen = metric != null ? metric.length : 0;
		double thr = params.metricThreshold;
		int minSum = params.minSumRgbForValidPixel;
		int maxGap = params.maxRowOcclusionGapColumns;
		double[] insectMetric = null;
		if (params.insectMetricGateEnabled) {
			IcyBufferedImage ins = KymoImageTransforms.applyMetricTransform(rgb, params.insectMetricTransform,
					params.useGpuTransforms);
			insectMetric = ins != null ? KymoImageTransforms.channel0AsDouble(ins) : null;
		}

		int[] ro = nC > 0 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(0), rgb.isSignedDataType()) : null;
		int[] go = nC > 1 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(1), rgb.isSignedDataType()) : null;
		int[] bo = nC > 2 ? Array1DUtil.arrayToIntArray(rgb.getDataXY(2), rgb.isSignedDataType()) : null;

		int[] rw = nC > 0 ? Array1DUtil.arrayToIntArray(work.getDataXY(0), work.isSignedDataType()) : null;
		int[] gw = nC > 1 ? Array1DUtil.arrayToIntArray(work.getDataXY(1), work.isSignedDataType()) : null;
		int[] bw = nC > 2 ? Array1DUtil.arrayToIntArray(work.getDataXY(2), work.isSignedDataType()) : null;

		boolean[] good = new boolean[w];
		boolean[] colInsect = params.insectMetricGateEnabled && insectMetric != null ? new boolean[w] : null;
		for (CageKymographSpotBands band : bands) {
			if (band == null || band.geometryMissing) {
				continue;
			}
			int y0 = Math.max(0, band.y0);
			int y1 = Math.min(h, band.y1Exclusive);
			if (colInsect != null) {
				fillColumnInsectInBand(colInsect, w, y0, y1, insectMetric, ro, go, bo, nC, minSum, params);
			}
			for (int y = y0; y < y1; y++) {
				for (int x = 0; x < w; x++) {
					int idx = y * w + x;
					good[x] = isGood(ro, go, bo, nC, metric, metricLen, idx, minSum, thr, insectMetric, params);
				}
				if (colInsect != null) {
					fillLeadingInsectRow(w, y, good, colInsect, MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS, MIN_BRACKET_RUN,
							ro, go, bo, nC, rw, gw, bw);
				}
				fillRowGaps(w, y, good, maxGap, ro, go, bo, nC, rw, gw, bw);
			}
		}
		for (int c = 0; c < nC; c++) {
			int[] ch = c == 0 ? rw : c == 1 ? gw : bw;
			if (ch != null) {
				work.setDataXY(c, ch);
			}
		}
		work.dataChanged();
		return work;
	}

	private static void fillColumnInsectInBand(boolean[] colInsect, int w, int y0, int y1, double[] insectMetric,
			int[] r, int[] g, int[] b, int nC, int minSum, CageKymoAnalyzer.Params params) {
		Arrays.fill(colInsect, false);
		int ml = insectMetric.length;
		for (int y = y0; y < y1; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				int idx = row + x;
				if (idx < 0 || idx >= ml) {
					continue;
				}
				int rv = sample(r, idx);
				int gv = nC > 1 ? sample(g, idx) : rv;
				int bv = nC > 2 ? sample(b, idx) : rv;
				if (rv + gv + bv < minSum) {
					continue;
				}
				if (KymoMetricGate.directedFinite(insectMetric[idx], params.insectMetricThreshold,
						params.insectMetricThresholdUp)) {
					colInsect[x] = true;
				}
			}
		}
	}

	private static void fillLeadingInsectRow(int w, int y, boolean[] good, boolean[] colInsect, int maxBridge,
			int minBracketRun, int[] ro, int[] go, int[] bo, int nC, int[] rw, int[] gw, int[] bw) {
		if (maxBridge <= 0 || minBracketRun <= 0) {
			return;
		}
		int xFirst = 0;
		while (xFirst < w && !good[xFirst]) {
			xFirst++;
		}
		if (xFirst <= 0 || xFirst > maxBridge) {
			return;
		}
		for (int xi = 0; xi < xFirst; xi++) {
			if (!colInsect[xi]) {
				return;
			}
		}
		int runLen = 0;
		for (int x = xFirst; x < w && good[x]; x++) {
			runLen++;
		}
		if (runLen < minBracketRun) {
			return;
		}
		int iR = y * w + xFirst;
		int rFill = sample(ro, iR);
		int gFill = nC > 1 ? sample(go, iR) : rFill;
		int bFill = nC > 2 ? sample(bo, iR) : rFill;
		for (int xi = 0; xi < xFirst; xi++) {
			int idx = y * w + xi;
			if (rw != null) {
				rw[idx] = rFill;
			}
			if (gw != null) {
				gw[idx] = gFill;
			}
			if (bw != null) {
				bw[idx] = bFill;
			}
		}
	}

	private static void fillRowGaps(int w, int y, boolean[] good, int maxGap, int[] ro, int[] go, int[] bo, int nC,
			int[] rw, int[] gw, int[] bw) {
		int x = 0;
		while (x < w && !good[x]) {
			x++;
		}
		while (x < w) {
			while (x < w && good[x]) {
				x++;
			}
			if (x >= w) {
				break;
			}
			int L = x - 1;
			int gapStart = x;
			while (x < w && !good[x]) {
				x++;
			}
			int gapEnd = x;
			if (gapEnd >= w) {
				break;
			}
			if (L < 0 || !good[L] || !good[gapEnd]) {
				continue;
			}
			int len = gapEnd - gapStart;
			if (maxGap <= 0 || len > maxGap) {
				continue;
			}
			int iL = y * w + L;
			int iR = y * w + gapEnd;
			int rL = sample(ro, iL);
			int gL = nC > 1 ? sample(go, iL) : rL;
			int bL = nC > 2 ? sample(bo, iL) : rL;
			int rR = sample(ro, iR);
			int gR = nC > 1 ? sample(go, iR) : rR;
			int bR = nC > 2 ? sample(bo, iR) : rR;
			int rFill = clampByte((rL + rR) / 2);
			int gFill = clampByte((gL + gR) / 2);
			int bFill = clampByte((bL + bR) / 2);
			for (int xi = gapStart; xi < gapEnd; xi++) {
				int idx = y * w + xi;
				if (rw != null) {
					rw[idx] = rFill;
				}
				if (gw != null) {
					gw[idx] = gFill;
				}
				if (bw != null) {
					bw[idx] = bFill;
				}
			}
		}
	}

	private static boolean isGood(int[] r, int[] g, int[] b, int nC, double[] metric, int metricLen, int idx,
			int minSum, double thr, double[] insectMetric, CageKymoAnalyzer.Params params) {
		int rv = sample(r, idx);
		int gv = nC > 1 ? sample(g, idx) : rv;
		int bv = nC > 2 ? sample(b, idx) : rv;
		if (rv + gv + bv < minSum) {
			return false;
		}
		if (metric == null || idx < 0 || idx >= metricLen) {
			return false;
		}
		double m = metric[idx];
		if (!Double.isFinite(m) || m <= thr) {
			return false;
		}
		if (params != null && params.insectMetricGateEnabled && insectMetric != null && idx >= 0
				&& idx < insectMetric.length) {
			if (KymoMetricGate.directedFinite(insectMetric[idx], params.insectMetricThreshold,
					params.insectMetricThresholdUp)) {
				return false;
			}
		}
		return true;
	}

	private static int sample(int[] ch, int idx) {
		if (ch == null || idx < 0 || idx >= ch.length) {
			return 0;
		}
		return ch[idx];
	}

	private static int clampByte(int v) {
		if (v < 0) {
			return 0;
		}
		if (v > 255) {
			return 255;
		}
		return v;
	}
}
