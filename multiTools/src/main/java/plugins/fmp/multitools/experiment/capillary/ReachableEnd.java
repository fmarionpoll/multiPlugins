package plugins.fmp.multitools.experiment.capillary;

import java.util.Arrays;

import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * First kymograph frame at which the bottom meniscus has risen toward the top
 * meniscus and stayed there. The stored value is the start of that run, so the
 * usable window is {@code [0, nValid)}. Returns {@code -1} when no such run
 * exists.
 */
public final class ReachableEnd {

	public static final int UNSET = -1;
	public static final double MIN_PIXELS = 3.0;
	public static final double MAD_K = 3.0;
	public static final int RUN_LENGTH = 5;

	private ReachableEnd() {
	}

	/**
	 * A capillary still waiting for review: no stored cutoff, not dismissed, and
	 * the rise rule finds a frame.
	 */
	public static boolean isReviewCandidate(Capillary cap) {
		if (cap == null || cap.getNValid() >= 0 || cap.isDriftDismissed())
			return false;
		return find(cap) >= 0;
	}

	/**
	 * Writes a cutoff only when the capillary is still a review candidate.
	 *
	 * @return true when a cutoff was stored
	 */
	public static boolean applyIfUnset(Capillary cap) {
		if (cap == null || cap.getNValid() >= 0 || cap.isDriftDismissed())
			return false;
		int found = find(cap);
		if (found < 0)
			return false;
		cap.setNValid(found);
		return true;
	}

	public static int find(Capillary cap) {
		if (cap == null)
			return UNSET;
		CapillaryMeasure bottom = cap.getBottomRaw();
		CapillaryMeasure top = cap.getTopRaw();
		if (bottom == null || top == null)
			return UNSET;
		return find(bottom.polylineLevel, top.polylineLevel);
	}

	public static int find(Level2D bottom, Level2D top) {
		if (bottom == null || top == null || bottom.ypoints == null || top.ypoints == null)
			return UNSET;
		int n = Math.min(bottom.npoints, top.npoints);
		n = Math.min(n, Math.min(bottom.ypoints.length, top.ypoints.length));
		return find(bottom.ypoints, top.ypoints, n);
	}

	public static int find(double[] bottomY, double[] topY, int n) {
		if (bottomY == null || topY == null || n <= 0)
			return UNSET;
		int limit = Math.min(n, Math.min(bottomY.length, topY.length));
		if (limit <= 0)
			return UNSET;

		int finite = 0;
		for (int i = 0; i < limit; i++) {
			if (Double.isFinite(bottomY[i]))
				finite++;
		}
		if (finite == 0)
			return UNSET;

		int prefixN = Math.min(finite, Math.max(10, finite / 4));
		double[] prefixBottom = new double[prefixN];
		double[] prefixTop = new double[prefixN];
		int topCount = 0;
		int filled = 0;
		for (int i = 0; i < limit && filled < prefixN; i++) {
			if (!Double.isFinite(bottomY[i]))
				continue;
			prefixBottom[filled] = bottomY[i];
			if (Double.isFinite(topY[i]))
				prefixTop[topCount++] = topY[i];
			filled++;
		}
		if (topCount == 0)
			return UNSET;

		double baseline = median(prefixBottom, prefixN);
		double topMedian = median(prefixTop, topCount);
		double direction = Math.signum(topMedian - baseline);
		if (direction == 0.0 || !Double.isFinite(baseline))
			return UNSET;

		double mad = mad(prefixBottom, prefixN, baseline);
		double scale = Double.isFinite(mad) ? mad : 0.0;
		double threshold = Math.max(MIN_PIXELS, MAD_K * scale);

		int run = 0;
		int runStart = -1;
		for (int i = 0; i < limit; i++) {
			double y = bottomY[i];
			boolean departs = Double.isFinite(y) && (y - baseline) * direction > threshold;
			if (departs) {
				if (run == 0)
					runStart = i;
				run++;
				if (run >= RUN_LENGTH)
					return runStart;
			} else {
				run = 0;
			}
		}
		return UNSET;
	}

	private static double median(double[] values, int n) {
		double[] copy = new double[n];
		System.arraycopy(values, 0, copy, 0, n);
		Arrays.sort(copy);
		if ((n & 1) == 1)
			return copy[n / 2];
		return 0.5 * (copy[n / 2 - 1] + copy[n / 2]);
	}

	private static double mad(double[] values, int n, double center) {
		double[] dev = new double[n];
		for (int i = 0; i < n; i++)
			dev[i] = Math.abs(values[i] - center);
		return median(dev, n);
	}
}
