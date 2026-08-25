package plugins.fmp.multitools.experiment.capillary.measurefilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.BottomBaselineEstimator;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Scans experiments for capillaries matching a {@link MeasureFilterRule}.
 */
public final class CapillaryMeasureFilter {

	@FunctionalInterface
	public interface ScanProgress {
		/** Called before each experiment is examined ({@code index} is 0-based). */
		void onExperiment(int index, int total, Experiment experiment);
	}

	private CapillaryMeasureFilter() {
	}

	public static List<MeasureFilterHit> scan(List<Experiment> experiments, MeasureFilterRule rule) {
		return scan(experiments, rule, null);
	}

	public static List<MeasureFilterHit> scan(List<Experiment> experiments, MeasureFilterRule rule,
			ScanProgress progress) {
		List<MeasureFilterHit> hits = new ArrayList<>();
		if (experiments == null || rule == null || rule.source == null)
			return hits;

		int total = experiments.size();
		for (int i = 0; i < total; i++) {
			Experiment exp = experiments.get(i);
			if (progress != null)
				progress.onExperiment(i, total, exp);
			if (exp == null)
				continue;
			try {
				exp.load_capillaries_description_and_measures();
			} catch (Exception e) {
				Logger.warn("CapillaryMeasureFilter: failed to load capillaries for "
						+ safeExpLabel(exp) + ": " + e.getMessage());
				continue;
			}
			if (exp.getCapillaries() == null || exp.getCapillaries().getList() == null)
				continue;

			if (rule.source.requiresT00() && exp.getCages() != null) {
				try {
					exp.getCages().computeT00References(exp);
				} catch (Exception e) {
					Logger.warn("CapillaryMeasureFilter: t00 compute failed for " + safeExpLabel(exp) + ": "
							+ e.getMessage());
					continue;
				}
			}

			String expLabel = shortExpLabel(exp);
			for (Capillary cap : exp.getCapillaries().getList()) {
				if (cap == null)
					continue;
				Double computed = compute(cap, rule);
				if (computed == null)
					continue;
				if (matches(computed, rule)) {
					String name = cap.getLast2ofCapillaryName();
					if (name == null || name.isEmpty())
						name = cap.getRoiName();
					hits.add(new MeasureFilterHit(i, exp, expLabel, name, cap.getKymographIndex(), computed,
							rule.copy()));
				}
			}
		}
		return hits;
	}

	/**
	 * @return computed value, or null if this capillary cannot be evaluated for the rule
	 */
	public static Double compute(Capillary cap, MeasureFilterRule rule) {
		if (cap == null || rule == null || rule.source == null)
			return null;

		if (rule.source.isScalar())
			return computeScalar(cap, rule);

		if (rule.stat == MeasureFilterStat.MISSING) {
			CapillaryMeasure m = getSeries(cap, rule.source);
			boolean missing = m == null || !m.isThereAnyMeasuresDone() || collectValues(m).isEmpty();
			return missing ? 1.0 : 0.0;
		}

		CapillaryMeasure m = getSeries(cap, rule.source);
		List<Double> values = collectValues(m);
		if (values.isEmpty())
			return null;
		return aggregate(values, rule.stat);
	}

	public static boolean matches(double value, MeasureFilterRule rule) {
		if (rule == null || rule.op == null)
			return false;
		switch (rule.op) {
		case IS_NAN:
			return !Double.isFinite(value);
		case GT:
			return Double.isFinite(value) && value > rule.threshold;
		case GE:
			return Double.isFinite(value) && value >= rule.threshold;
		case LT:
			return Double.isFinite(value) && value < rule.threshold;
		case LE:
			return Double.isFinite(value) && value <= rule.threshold;
		case BETWEEN:
			if (!Double.isFinite(value))
				return false;
			double lo = Math.min(rule.threshold, rule.threshold2);
			double hi = Math.max(rule.threshold, rule.threshold2);
			return value >= lo && value <= hi;
		default:
			return false;
		}
	}

	private static Double computeScalar(Capillary cap, MeasureFilterRule rule) {
		double v;
		switch (rule.source) {
		case BOTTOM_BASELINE_Y:
			v = cap.getBottomBaselineY();
			if (rule.op == MeasureFilterOp.IS_NAN && rule.requireBottomSeriesIfBaselineMissing) {
				CapillaryMeasure bottom = cap.getBottomRaw();
				if (bottom == null || !bottom.isThereAnyMeasuresDone())
					return null;
			}
			break;
		case BOTTOM_BASELINE_MAD:
			v = cap.getBottomBaselineMad();
			break;
		case BOTTOM_BASELINE_OUTLIER_FRAC:
			v = cap.getBottomBaselineOutlierFrac();
			break;
		case T00_MINUS_T0_FILL_PX:
			return computeT00MinusT0FillPx(cap);
		default:
			return null;
		}
		return v;
	}

	/**
	 * Meniscus Y difference (pixels): {@code Y_top[t0] − Y_t00} with
	 * {@code Y_t00 = tip − h}. Positive ⇒ lower fill than t00 reference at t0
	 * (early drink / underfill). Negative ⇒ fuller than reference.
	 */
	public static Double computeT00MinusT0FillPx(Capillary cap) {
		if (cap == null || !cap.hasT00YPixels())
			return null;
		CapillaryMeasure top = cap.getTopRaw();
		if (top == null || top.polylineLevel == null || top.polylineLevel.npoints <= 0)
			return null;
		Level2D poly = top.polylineLevel;
		if (poly.ypoints == null || poly.ypoints.length == 0 || !Double.isFinite(poly.ypoints[0]))
			return null;
		return poly.ypoints[0] - cap.getT00YPixels();
	}

	private static CapillaryMeasure getSeries(Capillary cap, MeasureFilterSource source) {
		switch (source) {
		case TOPRAW:
			return cap.getTopRaw();
		case TOPLEVEL:
			if (cap.getTopCorrected() != null && cap.getTopCorrected().isThereAnyMeasuresDone())
				return cap.getTopCorrected();
			return cap.getTopRaw();
		case BOTTOMLEVEL:
			return cap.getBottomRaw();
		case DERIVEDVALUES:
			return cap.getDerivative();
		default:
			return null;
		}
	}

	private static List<Double> collectValues(CapillaryMeasure m) {
		List<Double> values = new ArrayList<>();
		if (m == null || m.polylineLevel == null || m.polylineLevel.npoints <= 0)
			return values;
		double[] y = m.polylineLevel.ypoints;
		int n = m.polylineLevel.npoints;
		for (int i = 0; i < n && i < y.length; i++) {
			if (Double.isFinite(y[i]))
				values.add(y[i]);
		}
		return values;
	}

	public static Double aggregate(List<Double> values, MeasureFilterStat stat) {
		if (values == null || values.isEmpty() || stat == null)
			return null;
		switch (stat) {
		case VALUE:
		case FIRST:
			return values.get(0);
		case LAST:
			return values.get(values.size() - 1);
		case MIN: {
			double min = Double.POSITIVE_INFINITY;
			for (double v : values)
				if (v < min)
					min = v;
			return min;
		}
		case MAX: {
			double max = Double.NEGATIVE_INFINITY;
			for (double v : values)
				if (v > max)
					max = v;
			return max;
		}
		case ABSMAX: {
			double maxAbs = 0;
			double best = values.get(0);
			for (double v : values) {
				double a = Math.abs(v);
				if (a > maxAbs) {
					maxAbs = a;
					best = v;
				}
			}
			return best;
		}
		case RANGE: {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (double v : values) {
				if (v < min)
					min = v;
				if (v > max)
					max = v;
			}
			return max - min;
		}
		case MEAN: {
			double sum = 0;
			for (double v : values)
				sum += v;
			return sum / values.size();
		}
		case MEDIAN:
			return median(values);
		case MAD: {
			double med = median(values);
			List<Double> absDev = new ArrayList<>(values.size());
			for (double v : values)
				absDev.add(Math.abs(v - med));
			return median(absDev);
		}
		case MISSING:
			return 0.0;
		default:
			return null;
		}
	}

	private static double median(List<Double> values) {
		Double[] arr = values.toArray(new Double[0]);
		Arrays.sort(arr);
		int n = arr.length;
		if ((n & 1) == 1)
			return arr[n / 2];
		return 0.5 * (arr[n / 2 - 1] + arr[n / 2]);
	}

	private static String safeExpLabel(Experiment exp) {
		try {
			return shortExpLabel(exp);
		} catch (Exception e) {
			return "?";
		}
	}

	private static String shortExpLabel(Experiment exp) {
		if (exp == null)
			return "?";
		String results = exp.getResultsDirectory();
		if (results == null || results.isEmpty())
			return exp.toString();
		int slash = Math.max(results.lastIndexOf('/'), results.lastIndexOf('\\'));
		if (slash >= 0 && slash + 1 < results.length()) {
			String leaf = results.substring(slash + 1);
			if ("results".equalsIgnoreCase(leaf) && slash > 0) {
				String parent = results.substring(0, slash);
				int slash2 = Math.max(parent.lastIndexOf('/'), parent.lastIndexOf('\\'));
				if (slash2 >= 0 && slash2 + 1 < parent.length())
					return parent.substring(slash2 + 1);
				return parent;
			}
			return leaf;
		}
		return results;
	}

	/** Convenience: same noise ranking score as Bottom tab. */
	public static double bottomNoiseScore(Capillary cap) {
		return BottomBaselineEstimator.noiseScore(cap);
	}
}