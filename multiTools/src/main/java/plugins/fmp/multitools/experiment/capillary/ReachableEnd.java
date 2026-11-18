package plugins.fmp.multitools.experiment.capillary;

import java.util.Arrays;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
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
	private static final int BASELINE_SAMPLES = 10;
	private static final int RECOVERY_SAMPLES = 3;
	private static final double MIN_DEPARTURE_FRACTION = 0.8;

	private ReachableEnd() {
	}

	/**
	 * A capillary still waiting for review: no stored cutoff, not dismissed, and
	 * the rise rule finds a frame.
	 */
	public static boolean isReviewCandidate(Capillary cap) {
		return findReviewFrame(cap) >= 0;
	}

	public static int findReviewFrame(Capillary cap) {
		if (cap == null || cap.getNValid() >= 0 || cap.isDriftDismissed())
			return UNSET;
		return find(cap);
	}

	/** Review using a minimum rise expressed as a fraction of the initial capillary length. */
	public static int findReviewFrame(Capillary cap, Experiment exp, double minimumRiseFraction) {
		if (cap == null || cap.getNValid() >= 0 || cap.isDriftDismissed()
				|| !Double.isFinite(minimumRiseFraction) || minimumRiseFraction <= 0)
			return UNSET;
		return find(cap, exp, minimumRiseFraction);
	}

	/** Detects a rise independently of the capillary's saved review decision. */
	public static int find(Capillary cap, Experiment exp, double minimumRiseFraction) {
		if (cap == null || !Double.isFinite(minimumRiseFraction) || minimumRiseFraction <= 0)
			return UNSET;
		double length = capillaryLengthPixels(cap, exp);
		if (!Double.isFinite(length))
			return UNSET;
		Level2D bottom = cap.getBottomRaw().polylineLevel;
		Level2D top = cap.getTopRaw().polylineLevel;
		if (bottom == null || top == null)
			return UNSET;
		return find(bottom.ypoints, bottom.npoints, top.ypoints, top.npoints, minimumRiseFraction * length);
	}

	public static double capillaryLengthPixels(Capillary cap, Experiment exp) {
		return CapillaryPixelScale.nativePixelsPerCapillary(cap, 0, CapillaryPixelScale.sourceOf(exp),
				CapillaryPixelScale.expansionRatioOf(exp));
	}

	/** Signed movement toward the top meniscus relative to the early bottom position. */
	public static double riseFraction(Capillary cap, Experiment exp, int frame) {
		if (cap == null)
			return Double.NaN;
		Level2D bottom = cap.getBottomRaw().polylineLevel;
		Level2D top = cap.getTopRaw().polylineLevel;
		if (bottom == null || top == null || frame < 0 || frame >= bottom.npoints || frame >= bottom.ypoints.length)
			return Double.NaN;
		InitialBottom initial = initialBottom(bottom.ypoints, bottom.npoints, top.ypoints, top.npoints);
		if (initial == null)
			return Double.NaN;
		return (bottom.ypoints[frame] - initial.y) * initial.direction / capillaryLengthPixels(cap, exp);
	}

	/**
	 * Writes a cutoff only when the capillary is still a review candidate.
	 *
	 * @return true when a cutoff was stored
	 */
	public static boolean applyIfUnset(Capillary cap) {
		int found = findReviewFrame(cap);
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
		if (bottom == null || top == null)
			return UNSET;
		return find(bottom.ypoints, bottom.npoints, top.ypoints, top.npoints);
	}

	public static int find(double[] bottomY, double[] topY, int n) {
		return find(bottomY, n, topY, n);
	}

	private static int find(double[] bottomY, int bottomLength, double[] topY, int topLength) {
		return find(bottomY, bottomLength, topY, topLength, MIN_PIXELS);
	}

	private static int find(double[] bottomY, int bottomLength, double[] topY, int topLength, double minimumRisePixels) {
		InitialBottom initial = initialBottom(bottomY, bottomLength, topY, topLength);
		if (initial == null)
			return UNSET;
		int limit = Math.min(bottomLength, bottomY.length);
		int start = initial.start;
		double baseline = initial.y;
		double direction = initial.direction;
		double threshold = Math.max(minimumRisePixels, MAD_K * initial.mad);

		int end = limit - 1;
		while (end >= start && !Double.isFinite(bottomY[end]))
			end--;
		if (end - start + 1 < RUN_LENGTH || limit - end > RUN_LENGTH
				|| (bottomY[end] - baseline) * direction < threshold)
			return UNSET;

		// Work back from the end so a temporary rise followed by recovery is ignored.
		int departed = 0;
		int recovery = 0;
		int onset = UNSET;
		for (int i = end; i >= start; i--) {
			if (!Double.isFinite(bottomY[i]))
				continue;
			if ((bottomY[i] - baseline) * direction >= threshold) {
				recovery = 0;
				departed++;
				if (departed >= RUN_LENGTH && departed >= MIN_DEPARTURE_FRACTION * (end - i + 1))
					onset = i;
			} else if (++recovery >= RECOVERY_SAMPLES) {
				break;
			}
		}
		return onset;
	}

	private static InitialBottom initialBottom(double[] bottomY, int bottomLength, double[] topY, int topLength) {
		if (bottomY == null || topY == null)
			return null;
		int limit = Math.min(bottomLength, bottomY.length);
		int topLimit = Math.min(topLength, topY.length);
		int prefixSize = Math.min(BASELINE_SAMPLES, Math.max(3, limit / 4));
		double[] prefixBottom = new double[prefixSize];
		double[] prefixTop = new double[prefixSize];
		int topCount = 0;
		int filled = 0;
		int start = 0;
		for (; start < limit && filled < prefixSize; start++) {
			if (!Double.isFinite(bottomY[start]))
				continue;
			prefixBottom[filled++] = bottomY[start];
			if (start < topLimit && Double.isFinite(topY[start]))
				prefixTop[topCount++] = topY[start];
		}
		if (filled < prefixSize || topCount == 0 || limit - start < RUN_LENGTH)
			return null;

		double baseline = median(prefixBottom, prefixSize);
		double direction = Math.signum(median(prefixTop, topCount) - baseline);
		if (direction == 0.0)
			return null;
		return new InitialBottom(baseline, direction, mad(prefixBottom, prefixSize, baseline), start);
	}

	private static final class InitialBottom {
		final double y;
		final double direction;
		final double mad;
		final int start;

		InitialBottom(double y, double direction, double mad, int start) {
			this.y = y;
			this.direction = direction;
			this.mad = mad;
			this.start = start;
		}
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
