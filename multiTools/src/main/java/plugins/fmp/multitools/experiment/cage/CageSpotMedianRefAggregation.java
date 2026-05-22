package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Cage-level reference curve: per time bin, median of {@code AREA_SUM} across spots in a cage,
 * excluding bins where the fly is present on that spot ({@code AREA_FLYPRESENT} pixel count &gt; 0).
 * Optional centered moving average over time reduces high-frequency noise from small spot pools.
 */
public final class CageSpotMedianRefAggregation {

	/** Minimum fly-pixel count to treat the spot as occluded at that bin (exclude from median pool). */
	public static final int FLY_PRESENT_EXCLUDE_MIN_PIXELS = 1;

	private CageSpotMedianRefAggregation() {
	}

	/**
	 * @param opt chart/export options; smoothing uses {@link ResultsOptions#aggMedianRefSmoothWindowBins}
	 *            (defaults when {@code opt} is null)
	 * @return one value per bin; {@link Double#NaN} when no finite sample contributes in the smoothing window
	 */
	public static ArrayList<Double> buildMedianRefFromAreaSum(Experiment exp, Cage cage, Spots allSpots,
			ResultsOptions opt) {
		ArrayList<Double> raw = buildMedianRefFromAreaSumRaw(exp, cage, allSpots);
		int windowBins = 5;
		if (opt != null) {
			windowBins = opt.aggMedianRefSmoothWindowBins;
		}
		return smoothMovingAverageFiniteNeighbors(raw, windowBins);
	}

	/**
	 * Per-bin median without temporal smoothing (for tests or custom post-processing).
	 */
	public static ArrayList<Double> buildMedianRefFromAreaSumRaw(Experiment exp, Cage cage, Spots allSpots) {
		ArrayList<Double> out = new ArrayList<>();
		if (cage == null || allSpots == null) {
			return out;
		}
		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			return out;
		}

		double[] camTimeMin = null;
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getTimeManager() != null) {
			if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null) {
				exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
			}
			camTimeMin = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();
		}

		int n = minNativeLength(spots, camTimeMin);
		if (n <= 0) {
			return out;
		}

		double[] scratch = new double[spots.size()];
		for (int t = 0; t < n; t++) {
			int m = 0;
			for (Spot s : spots) {
				if (s == null || !s.isReadyForAnalysis()) {
					continue;
				}
				if (isFlyPresentAt(s, t)) {
					continue;
				}
				SpotMeasure sum = s.getSum();
				if (sum == null || t >= sum.getCount()) {
					continue;
				}
				double v = sum.getValueAt(t);
				if (Double.isFinite(v)) {
					scratch[m++] = v;
				}
			}
			out.add(m > 0 ? medianOfFirst(scratch, m) : Double.NaN);
		}
		return out;
	}

	/**
	 * Centered moving average: at each bin, mean of all finite values in
	 * {@code [i - half, i + half]}. {@code NaN} bins are skipped (not counted as 0).
	 * <p>
	 * {@code windowBins} is forced to an odd number &gt;= 1 so the window is symmetric; even inputs
	 * are rounded up.
	 */
	private static ArrayList<Double> smoothMovingAverageFiniteNeighbors(ArrayList<Double> raw, int windowBins) {
		if (raw == null || raw.isEmpty()) {
			return new ArrayList<>();
		}
		int w = Math.max(1, windowBins);
		if ((w & 1) == 0) {
			w++;
		}
		int half = (w - 1) / 2;
		int n = raw.size();
		ArrayList<Double> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			double sum = 0.0;
			int cnt = 0;
			int j0 = Math.max(0, i - half);
			int j1 = Math.min(n - 1, i + half);
			for (int j = j0; j <= j1; j++) {
				double v = raw.get(j);
				if (Double.isFinite(v)) {
					sum += v;
					cnt++;
				}
			}
			out.add(cnt > 0 ? sum / cnt : Double.NaN);
		}
		return out;
	}

	private static boolean isFlyPresentAt(Spot s, int binIndex) {
		SpotMeasure fp = s.getFlyPresent();
		if (fp == null || binIndex < 0 || binIndex >= fp.getCount()) {
			return false;
		}
		double v = fp.getValueAt(binIndex);
		return Double.isFinite(v) && v >= FLY_PRESENT_EXCLUDE_MIN_PIXELS;
	}

	private static int minNativeLength(List<Spot> spots, double[] camTimeMin) {
		int n = Integer.MAX_VALUE;
		boolean found = false;
		for (Spot s : spots) {
			if (s == null || s.getSum() == null) {
				continue;
			}
			int c = s.getSum().getCount();
			if (c <= 0) {
				continue;
			}
			if (camTimeMin != null) {
				c = Math.min(c, camTimeMin.length);
			}
			n = Math.min(n, c);
			found = true;
		}
		return found ? n : 0;
	}

	private static double medianOfFirst(double[] values, int m) {
		if (m <= 0 || values == null) {
			return Double.NaN;
		}
		double[] copy = Arrays.copyOf(values, m);
		Arrays.sort(copy);
		if ((m & 1) == 1) {
			return copy[m / 2];
		}
		return (copy[m / 2 - 1] + copy[m / 2]) / 2.0;
	}
}
