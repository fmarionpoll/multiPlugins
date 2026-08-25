package plugins.fmp.multitools.experiment.capillary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Robust constant tip marker from a noisy bottom-level series (median after MAD
 * outlier rejection).
 */
public final class BottomBaselineEstimator {

	public static final double DEFAULT_MAD_K = 3.0;

	private BottomBaselineEstimator() {
	}

	public static final class Result {
		public final double baselineY;
		public final double mad;
		public final double outlierFrac;
		public final int nValid;
		public final int nInliers;

		public Result(double baselineY, double mad, double outlierFrac, int nValid, int nInliers) {
			this.baselineY = baselineY;
			this.mad = mad;
			this.outlierFrac = outlierFrac;
			this.nValid = nValid;
			this.nInliers = nInliers;
		}

		public boolean isValid() {
			return Double.isFinite(baselineY);
		}
	}

	public static Result estimate(CapillaryMeasure bottom) {
		return estimate(bottom, DEFAULT_MAD_K);
	}

	public static Result estimate(CapillaryMeasure bottom, double madK) {
		if (bottom == null || bottom.polylineLevel == null || bottom.polylineLevel.npoints <= 0)
			return empty();
		return estimate(bottom.polylineLevel.ypoints, bottom.polylineLevel.npoints, madK);
	}

	public static Result estimate(double[] ypoints, int npoints, double madK) {
		if (ypoints == null || npoints <= 0)
			return empty();
		List<Double> values = new ArrayList<>(npoints);
		for (int i = 0; i < npoints && i < ypoints.length; i++) {
			double y = ypoints[i];
			if (Double.isFinite(y))
				values.add(y);
		}
		if (values.isEmpty())
			return empty();

		double median0 = median(values);
		double mad = mad(values, median0);
		double k = madK > 0 ? madK : DEFAULT_MAD_K;
		List<Double> inliers = new ArrayList<>(values.size());
		if (!Double.isFinite(mad) || mad <= 1e-9) {
			inliers.addAll(values);
		} else {
			double thresh = k * 1.4826 * mad;
			for (double y : values) {
				if (Math.abs(y - median0) <= thresh)
					inliers.add(y);
			}
			if (inliers.isEmpty())
				inliers.addAll(values);
		}
		double baseline = median(inliers);
		double residualMad = mad(inliers, baseline);
		double outlierFrac = values.isEmpty() ? Double.NaN
				: (double) (values.size() - inliers.size()) / (double) values.size();
		return new Result(baseline, residualMad, outlierFrac, values.size(), inliers.size());
	}

	public static void applyToCapillary(Capillary cap, Result result) {
		if (cap == null)
			return;
		if (result == null || !result.isValid()) {
			cap.clearBottomBaseline();
			return;
		}
		cap.setBottomBaselineY(result.baselineY);
		cap.setBottomBaselineMad(result.mad);
		cap.setBottomBaselineOutlierFrac(result.outlierFrac);
	}

	public static Result estimateAndApply(Capillary cap) {
		return estimateAndApply(cap, DEFAULT_MAD_K);
	}

	public static Result estimateAndApply(Capillary cap, double madK) {
		if (cap == null)
			return empty();
		Result result = estimate(cap.getBottomRaw(), madK);
		applyToCapillary(cap, result);
		return result;
	}

	public static List<Capillary> rankByBottomNoise(List<Capillary> capillaries) {
		List<Capillary> ranked = new ArrayList<>();
		if (capillaries == null)
			return ranked;
		for (Capillary cap : capillaries) {
			if (cap == null)
				continue;
			if (cap.getBottomRaw() != null && cap.getBottomRaw().isThereAnyMeasuresDone())
				ranked.add(cap);
		}
		ranked.sort(new Comparator<Capillary>() {
			@Override
			public int compare(Capillary a, Capillary b) {
				return Double.compare(noiseScore(b), noiseScore(a));
			}
		});
		return ranked;
	}

	public static double noiseScore(Capillary cap) {
		if (cap == null)
			return Double.POSITIVE_INFINITY;
		if (!Double.isFinite(cap.getBottomBaselineY()))
			return Double.POSITIVE_INFINITY;
		double mad = Double.isFinite(cap.getBottomBaselineMad()) ? cap.getBottomBaselineMad() : 0;
		double frac = Double.isFinite(cap.getBottomBaselineOutlierFrac()) ? cap.getBottomBaselineOutlierFrac() : 0;
		return mad + 100.0 * frac;
	}

	private static Result empty() {
		return new Result(Double.NaN, Double.NaN, Double.NaN, 0, 0);
	}

	private static double median(List<Double> values) {
		if (values == null || values.isEmpty())
			return Double.NaN;
		Double[] arr = values.toArray(new Double[0]);
		Arrays.sort(arr);
		int n = arr.length;
		if ((n & 1) == 1)
			return arr[n / 2];
		return 0.5 * (arr[n / 2 - 1] + arr[n / 2]);
	}

	private static double mad(List<Double> values, double center) {
		if (values == null || values.isEmpty() || !Double.isFinite(center))
			return Double.NaN;
		List<Double> absDev = new ArrayList<>(values.size());
		for (double y : values)
			absDev.add(Math.abs(y - center));
		return median(absDev);
	}
}