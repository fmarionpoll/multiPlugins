package plugins.fmp.multitools.service;

import java.util.ArrayDeque;
import java.util.Arrays;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;

/**
 * Post-lift binary mask (spot metric &gt; threshold, valid sum RGB) per spot band, optionally after excluding pixels
 * that match a second insect-style metric on the same RGB. When the insect gate is on, each time column where insect
 * is detected anywhere in the band is marked; along each row, OFF gaps are filled when every column in the gap is
 * insect-marked: strictly between ON pixels (internal jump), or as a leading prefix before the first ON when the
 * bracketing ON run is at least {@link #MIN_HORIZONTAL_ON_RUN} columns (start-of-strip occlusion), up to
 * {@link #MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS}. Then: generic row-wise temporal gap closing, a single-pass vertical
 * bridge (only immediate 1-row holes), left-anchored 4-connected keep, and removal of sub-pixel-wide horizontal ON
 * runs. No leading fill to x=0. See {@link CageKymoAnalyzer.Params#maxRowOcclusionGapColumns}.
 */
public final class KymoStripPostLiftMetricMask {

	private static final int MIN_HORIZONTAL_ON_RUN = 2;
	/** Max width (columns) of an insect-only OFF gap that may be bridged along a row between spot ON pixels. */
	private static final int MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS = 262144;

	private KymoStripPostLiftMetricMask() {
	}

	public static boolean[] build(IcyBufferedImage img, CageKymographSpotBands band, CageKymoAnalyzer.Params params,
			int imgW, int imgH) {
		if (img == null || band == null || params == null) {
			return new boolean[imgW * imgH];
		}
		IcyBufferedImage metricImg = KymoImageTransforms.applyMetricTransform(img, params.metricTransform,
				params.useGpuTransforms);
		double[] metric = metricImg != null ? KymoImageTransforms.channel0AsDouble(metricImg) : null;
		int nC = Math.max(1, img.getSizeC());
		int[] r = nC > 0 ? Array1DUtil.arrayToIntArray(img.getDataXY(0), img.isSignedDataType()) : null;
		int[] g = nC > 1 ? Array1DUtil.arrayToIntArray(img.getDataXY(1), img.isSignedDataType()) : null;
		int[] b = nC > 2 ? Array1DUtil.arrayToIntArray(img.getDataXY(2), img.isSignedDataType()) : null;
		return buildWithMetric(img, band, params, imgW, imgH, metric, r, g, b, nC);
	}

	/**
	 * Same as {@link #build} but reuses a precomputed metric channel (same layout as {@code img} pixels).
	 */
	public static boolean[] buildWithMetric(IcyBufferedImage img, CageKymographSpotBands band,
			CageKymoAnalyzer.Params params, int imgW, int imgH, double[] metric, int[] r, int[] g, int[] b, int nC) {
		boolean[] m = new boolean[imgW * imgH];
		if (img == null || band == null || params == null || band.geometryMissing) {
			return m;
		}
		int y0 = Math.max(0, band.y0);
		int y1 = Math.min(imgH, band.y1Exclusive);
		if (y1 <= y0) {
			return m;
		}
		if (metric == null || metric.length == 0) {
			return m;
		}
		double[] insectMetric = null;
		if (params.insectMetricGateEnabled) {
			IcyBufferedImage ins = KymoImageTransforms.applyMetricTransform(img, params.insectMetricTransform,
					params.useGpuTransforms);
			insectMetric = ins != null ? KymoImageTransforms.channel0AsDouble(ins) : null;
		}
		int metricLen = metric.length;
		double thr = params.metricThreshold;
		int minSum = params.minSumRgbForValidPixel;
		int maxGap = params.maxRowOcclusionGapColumns;

		for (int y = y0; y < y1; y++) {
			for (int x = 0; x < imgW; x++) {
				int idx = y * imgW + x;
				if (idx < 0 || idx >= metricLen) {
					continue;
				}
				int rv = sample(r, idx);
				int gv = nC > 1 ? sample(g, idx) : rv;
				int bv = nC > 2 ? sample(b, idx) : rv;
				if (rv + gv + bv < minSum) {
					continue;
				}
				double mv = metric[idx];
				boolean spotPass = Double.isFinite(mv) && mv > thr;
				boolean insectP = insectPixelOn(insectMetric, idx, params);
				if (spotPass && !insectP) {
					m[idx] = true;
				}
			}
		}

		if (params.insectMetricGateEnabled && insectMetric != null) {
			boolean[] colInsect = new boolean[imgW];
			fillColumnInsectInBand(colInsect, imgW, y0, y1, insectMetric, r, g, b, nC, minSum, params);
			insectColumnMediatedRowBridges(m, imgW, y0, y1, colInsect, MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS);
			insectColumnMediatedLeadingGaps(m, imgW, y0, y1, colInsect, MAX_INSECT_COLUMN_MEDIATED_GAP_COLUMNS,
					MIN_HORIZONTAL_ON_RUN);
		}

		for (int y = y0; y < y1; y++) {
			closeRowGapsInSlice(m, imgW, y, maxGap);
		}
		verticalBridgeOnce(m, imgW, y0, y1);

		applyLeftAnchoredKeep(m, imgW, imgH, y0, y1);
		stripShortHorizontalRuns(m, imgW, y0, y1, MIN_HORIZONTAL_ON_RUN);
		return m;
	}

	/**
	 * For each time column x, true if some row in the band has valid RGB sum and insect metric on at (y,x).
	 */
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
				if (insectPixelOn(insectMetric, idx, params)) {
					colInsect[x] = true;
				}
			}
		}
	}

	/**
	 * Along each row, fills OFF runs strictly bracketed by ON when every column in the run is insect-present in the
	 * band (see {@link #fillColumnInsectInBand}).
	 */
	private static void insectColumnMediatedRowBridges(boolean[] m, int w, int y0, int y1, boolean[] colInsect,
			int maxBridge) {
		if (maxBridge <= 0) {
			return;
		}
		boolean any = false;
		for (int x = 0; x < w; x++) {
			if (colInsect[x]) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		for (int y = y0; y < y1; y++) {
			int row = y * w;
			int x = 0;
			while (x < w && !m[row + x]) {
				x++;
			}
			while (x < w) {
				while (x < w && m[row + x]) {
					x++;
				}
				if (x >= w) {
					break;
				}
				int L = x - 1;
				int gapStart = x;
				while (x < w && !m[row + x]) {
					x++;
				}
				int gapEnd = x;
				if (gapEnd >= w) {
					break;
				}
				if (L < 0 || !m[row + L] || !m[row + gapEnd]) {
					continue;
				}
				int len = gapEnd - gapStart;
				if (len > maxBridge) {
					continue;
				}
				boolean allInsectCol = true;
				for (int xi = gapStart; xi < gapEnd; xi++) {
					if (!colInsect[xi]) {
						allInsectCol = false;
						break;
					}
				}
				if (!allInsectCol) {
					continue;
				}
				for (int xi = gapStart; xi < gapEnd; xi++) {
					m[row + xi] = true;
				}
			}
		}
	}

	/**
	 * Fills a leading OFF prefix [0, firstOn) when every column in the prefix is insect-marked and the first ON run to
	 * the right is at least {@code minBracketRun} columns (avoids extending to t=0 from a 1-pixel speck).
	 */
	private static void insectColumnMediatedLeadingGaps(boolean[] m, int w, int y0, int y1, boolean[] colInsect,
			int maxBridge, int minBracketRun) {
		if (maxBridge <= 0 || minBracketRun <= 0) {
			return;
		}
		boolean any = false;
		for (int x = 0; x < w; x++) {
			if (colInsect[x]) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		for (int y = y0; y < y1; y++) {
			int row = y * w;
			int xFirst = 0;
			while (xFirst < w && !m[row + xFirst]) {
				xFirst++;
			}
			if (xFirst <= 0 || xFirst > maxBridge) {
				continue;
			}
			boolean allInsectCol = true;
			for (int xi = 0; xi < xFirst; xi++) {
				if (!colInsect[xi]) {
					allInsectCol = false;
					break;
				}
			}
			if (!allInsectCol) {
				continue;
			}
			int runLen = 0;
			for (int x = xFirst; x < w && m[row + x]; x++) {
				runLen++;
			}
			if (runLen < minBracketRun) {
				continue;
			}
			for (int xi = 0; xi < xFirst; xi++) {
				m[row + xi] = true;
			}
		}
	}

	private static void closeRowGapsInSlice(boolean[] m, int w, int y, int maxGap) {
		int row = y * w;
		int x = 0;
		while (x < w && !m[row + x]) {
			x++;
		}
		while (x < w) {
			while (x < w && m[row + x]) {
				x++;
			}
			if (x >= w) {
				break;
			}
			int L = x - 1;
			int gapStart = x;
			while (x < w && !m[row + x]) {
				x++;
			}
			int gapEnd = x;
			if (gapEnd >= w) {
				break;
			}
			if (L < 0 || !m[row + L] || !m[row + gapEnd]) {
				continue;
			}
			int len = gapEnd - gapStart;
			if (maxGap <= 0 || len > maxGap) {
				continue;
			}
			for (int xi = gapStart; xi < gapEnd; xi++) {
				m[row + xi] = true;
			}
		}
	}

	private static boolean verticalBridgeOnce(boolean[] m, int w, int y0, int y1) {
		boolean changed = false;
		if (y1 - y0 < 3) {
			return false;
		}
		for (int y = y0 + 1; y < y1 - 1; y++) {
			int row = y * w;
			int above = row - w;
			int below = row + w;
			for (int x = 0; x < w; x++) {
				int idx = row + x;
				if (!m[idx] && m[above + x] && m[below + x]) {
					m[idx] = true;
					changed = true;
				}
			}
		}
		return changed;
	}

	private static void applyLeftAnchoredKeep(boolean[] m, int w, int imgH, int y0, int y1) {
		int L = -1;
		for (int x = 0; x < w; x++) {
			for (int y = y0; y < y1; y++) {
				if (m[y * w + x]) {
					L = x;
					break;
				}
			}
			if (L >= 0) {
				break;
			}
		}
		if (L < 0) {
			Arrays.fill(m, false);
			return;
		}

		int bestLen = -1;
		int bestY0 = y0;
		int bestY1 = y0;
		int runStart = -1;
		for (int y = y0; y < y1; y++) {
			boolean on = m[y * w + L];
			if (on && runStart < 0) {
				runStart = y;
			}
			if (!on && runStart >= 0) {
				int len = y - runStart;
				if (len > bestLen) {
					bestLen = len;
					bestY0 = runStart;
					bestY1 = y;
				}
				runStart = -1;
			}
		}
		if (runStart >= 0) {
			int len = y1 - runStart;
			if (len > bestLen) {
				bestLen = len;
				bestY0 = runStart;
				bestY1 = y1;
			}
		}
		if (bestLen <= 0) {
			Arrays.fill(m, false);
			return;
		}

		boolean[] keep = new boolean[w * imgH];
		ArrayDeque<Integer> q = new ArrayDeque<>();
		for (int y = bestY0; y < bestY1; y++) {
			int idx = y * w + L;
			if (m[idx]) {
				keep[idx] = true;
				q.add(idx);
			}
		}
		while (!q.isEmpty()) {
			int idx = q.poll();
			int y = idx / w;
			int x = idx - y * w;
			if (x + 1 < w) {
				tryPush(m, keep, q, idx + 1, y0, y1, w);
			}
			if (x > 0) {
				tryPush(m, keep, q, idx - 1, y0, y1, w);
			}
			if (y + 1 < y1) {
				tryPush(m, keep, q, idx + w, y0, y1, w);
			}
			if (y > y0) {
				tryPush(m, keep, q, idx - w, y0, y1, w);
			}
		}

		for (int y = y0; y < y1; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				int idx = row + x;
				if (!keep[idx]) {
					m[idx] = false;
				}
			}
		}
		for (int i = 0; i < m.length; i++) {
			int y = i / w;
			if (y < y0 || y >= y1) {
				m[i] = false;
			}
		}
	}

	/**
	 * Removes ON pixels that are not part of a horizontal run of at least {@code minRun} columns (same row). Kills
	 * 1-pixel-wide vertical artifacts.
	 */
	private static void stripShortHorizontalRuns(boolean[] m, int w, int y0, int y1, int minRun) {
		if (minRun <= 1) {
			return;
		}
		boolean[] tmp = new boolean[m.length];
		for (int y = y0; y < y1; y++) {
			int row = y * w;
			int x = 0;
			while (x < w) {
				while (x < w && !m[row + x]) {
					x++;
				}
				int runStart = x;
				while (x < w && m[row + x]) {
					x++;
				}
				int runEnd = x;
				int len = runEnd - runStart;
				if (len >= minRun) {
					for (int xi = runStart; xi < runEnd; xi++) {
						tmp[row + xi] = true;
					}
				}
			}
		}
		for (int y = y0; y < y1; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				m[row + x] = tmp[row + x];
			}
		}
	}

	private static void tryPush(boolean[] m, boolean[] keep, ArrayDeque<Integer> q, int nidx, int y0, int y1, int w) {
		int ny = nidx / w;
		if (ny < y0 || ny >= y1 || !m[nidx] || keep[nidx]) {
			return;
		}
		keep[nidx] = true;
		q.add(nidx);
	}

	private static int sample(int[] ch, int idx) {
		if (ch == null || idx < 0 || idx >= ch.length) {
			return 0;
		}
		return ch[idx];
	}

	private static boolean insectPixelOn(double[] insectMetric, int idx, CageKymoAnalyzer.Params params) {
		if (!params.insectMetricGateEnabled || insectMetric == null || idx < 0 || idx >= insectMetric.length) {
			return false;
		}
		return KymoMetricGate.directedFinite(insectMetric[idx], params.insectMetricThreshold,
				params.insectMetricThresholdUp);
	}
}
