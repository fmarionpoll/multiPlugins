package plugins.fmp.multitools.service;

import java.util.ArrayDeque;
import java.util.Arrays;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;

/**
 * Post-lift binary mask (metric &gt; threshold, valid sum RGB) per spot band: row-wise temporal gap closing (short OFF
 * between ON along time), vertical bridging of one-row holes, then keep only pixels 4-connected within the band to
 * the longest vertical run at the leftmost time column where the mask is ON (suppresses disconnected noise to the
 * right or on isolated rows).
 */
public final class KymoStripPostLiftMetricMask {

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
				if (Double.isFinite(mv) && mv > thr) {
					m[idx] = true;
				}
			}
		}

		for (int y = y0; y < y1; y++) {
			closeRowGapsInSlice(m, imgW, y, maxGap);
		}
		for (int pass = 0; pass < 3; pass++) {
			if (!verticalBridgeOnce(m, imgW, y0, y1)) {
				break;
			}
		}

		applyLeftAnchoredKeep(m, imgW, imgH, y0, y1);
		return m;
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
}
