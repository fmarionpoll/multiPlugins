package plugins.fmp.multitools.experiment.spot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Baseline and t0 reference helpers for spots consumed before recording started.
 */
public final class SpotPreConsumedSupport {

	public static final Color PRE_CONSUMED_ROI_COLOR = new Color(255, 0, 255);

	private SpotPreConsumedSupport() {
	}

	public static void applyPreConsumedReferenceAtT0(Experiment exp) {
		if (exp == null || exp.getSpots() == null) {
			return;
		}
		for (Spot spot : exp.getSpots().getSpotList()) {
			if (spot != null && spot.isConsumedBeforeRecording()) {
				applyPreConsumedReferenceForSpot(exp, spot);
			}
		}
	}

	public static void applyPreConsumedReferenceForSpot(Experiment exp, Spot spot) {
		if (exp == null || spot == null || !spot.isConsumedBeforeRecording()) {
			return;
		}
		double ref = resolveReferenceT0(exp, spot);
		if (Double.isFinite(ref) && ref > 0.0) {
			spot.setConsumedReferenceT0(ref);
			applyReferenceAtT0(spot, ref);
		}
	}

	public static void markPreConsumed(Experiment exp, List<Spot> spots) {
		if (exp == null || spots == null) {
			return;
		}
		for (Spot spot : spots) {
			if (spot == null) {
				continue;
			}
			spot.setConsumedBeforeRecording(true);
			if (spot.getRoi() != null) {
				spot.getRoi().setColor(PRE_CONSUMED_ROI_COLOR);
			}
			spot.getProperties().setColor(PRE_CONSUMED_ROI_COLOR);
			applyPreConsumedReferenceForSpot(exp, spot);
		}
	}

	public static void clearPreConsumed(List<Spot> spots) {
		if (spots == null) {
			return;
		}
		for (Spot spot : spots) {
			if (spot == null) {
				continue;
			}
			spot.setConsumedBeforeRecording(false);
			spot.setConsumedReferenceT0(Double.NaN);
			Color restore = Color.GREEN;
			spot.getProperties().setColor(restore);
			if (spot.getRoi() != null) {
				spot.getRoi().setColor(restore);
			}
		}
	}

	public static void applyReferenceAtT0(Spot spot, double reference) {
		if (spot == null || !Double.isFinite(reference)) {
			return;
		}
		setMeasureT0(spot.getSumClean(), reference);
		setMeasureT0(spot.getSum(), reference);
		setMeasureT0(spot.getSumNoFly(), reference);
		setMeasureT0(spot.getSumCleanV2(), reference);
		setMeasureT0(spot.getSumV2(), reference);
		setMeasureT0(spot.getSumNoFlyV2(), reference);
	}

	private static void setMeasureT0(SpotMeasure measure, double reference) {
		if (measure == null || measure.getCount() < 1) {
			return;
		}
		measure.setValueAt(0, reference);
	}

	public static double resolveReferenceT0(Experiment exp, Spot spot) {
		if (spot == null) {
			return Double.NaN;
		}
		double cached = spot.getConsumedReferenceT0();
		if (Double.isFinite(cached) && cached > 0.0) {
			return cached;
		}
		double sibling = computeSiblingMedianCleanT0(exp, spot);
		if (Double.isFinite(sibling) && sibling > 0.0) {
			return sibling;
		}
		double cageMax = computeCageMaxCleanT0(exp, spot, false);
		if (Double.isFinite(cageMax) && cageMax > 0.0) {
			return cageMax;
		}
		return computeExperimentMedianCleanT0(exp, false);
	}

	public static double computeSiblingMedianCleanT0(Experiment exp, Spot target) {
		if (exp == null || target == null || exp.getSpots() == null) {
			return Double.NaN;
		}
		int cageId = target.getProperties().getCageID();
		List<Double> values = new ArrayList<>();
		for (Spot s : exp.getSpots().getSpotList()) {
			if (s == null || s == target || s.isConsumedBeforeRecording()) {
				continue;
			}
			if (s.getProperties().getCageID() != cageId) {
				continue;
			}
			double v = cleanValueAtT0(s);
			if (Double.isFinite(v) && v > 0.0) {
				values.add(v);
			}
		}
		return median(values);
	}

	private static double computeCageMaxCleanT0(Experiment exp, Spot target, boolean includeFlagged) {
		if (exp == null || target == null || exp.getSpots() == null) {
			return Double.NaN;
		}
		int cageId = target.getProperties().getCageID();
		double max = Double.NEGATIVE_INFINITY;
		for (Spot s : exp.getSpots().getSpotList()) {
			if (s == null || (!includeFlagged && s.isConsumedBeforeRecording())) {
				continue;
			}
			if (s.getProperties().getCageID() != cageId) {
				continue;
			}
			double v = cleanValueAtT0(s);
			if (Double.isFinite(v) && v > max) {
				max = v;
			}
		}
		return Double.isFinite(max) && max > 0.0 ? max : Double.NaN;
	}

	private static double computeExperimentMedianCleanT0(Experiment exp, boolean includeFlagged) {
		if (exp == null || exp.getSpots() == null) {
			return Double.NaN;
		}
		List<Double> values = new ArrayList<>();
		for (Spot s : exp.getSpots().getSpotList()) {
			if (s == null || (!includeFlagged && s.isConsumedBeforeRecording())) {
				continue;
			}
			double v = cleanValueAtT0(s);
			if (Double.isFinite(v) && v > 0.0) {
				values.add(v);
			}
		}
		return median(values);
	}

	private static double cleanValueAtT0(Spot spot) {
		SpotMeasure clean = spot.getSumClean();
		if (clean == null || clean.getCount() < 1) {
			return Double.NaN;
		}
		return clean.getValueAt(0);
	}

	private static double median(List<Double> values) {
		if (values == null || values.isEmpty()) {
			return Double.NaN;
		}
		Collections.sort(values);
		int n = values.size();
		if (n % 2 == 1) {
			return values.get(n / 2);
		}
		return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
	}

	/**
	 * Baseline max for depletion charts / aggregates. Pre-consumed spots use the synthetic t0 reference.
	 */
	public static double computeBaselineMaxValue(Experiment exp, Spot spot, SpotMeasure m, double[] camTimeMin,
			ResultsOptions o) {
		if (spot != null && spot.isConsumedBeforeRecording()) {
			double ref = resolveReferenceT0(exp, spot);
			if (Double.isFinite(ref) && ref > 0.0) {
				return ref;
			}
		}
		return computeBaselineMaxFromMeasure(m, camTimeMin, o);
	}

	public static double computeBaselineMaxFromMeasure(SpotMeasure m, double[] camTimeMin, ResultsOptions o) {
		if (m == null) {
			return 1.0;
		}
		double[] values = m.getValues();
		if (values == null || values.length == 0) {
			return 1.0;
		}

		int n = values.length;
		if (camTimeMin != null) {
			n = Math.min(n, camTimeMin.length);
		}
		if (n <= 0) {
			return 1.0;
		}

		double baselineMin = Math.max(0.0, o != null ? o.spotBaselineWindowMinutes : 2) * 1.0;
		double baselineEndMin = baselineMin;
		if (!(baselineEndMin > 0.0)) {
			baselineEndMin = camTimeMin != null && camTimeMin.length > 0 ? camTimeMin[Math.min(camTimeMin.length - 1, 0)]
					: 2.0;
		}

		double max = Double.NEGATIVE_INFINITY;
		int stableBins = Math.max(1, o != null ? o.spotBaselineStableBins : 3);
		boolean stopWhenStable = o != null && o.spotBaselineStopWhenStable;
		int stableCount = 0;

		for (int i = 0; i < n; i++) {
			double t = camTimeMin != null ? camTimeMin[i] : i;
			if (baselineEndMin > 0.0 && t > baselineEndMin) {
				break;
			}
			double v = values[i];
			if (Double.isFinite(v) && v > max) {
				max = v;
				stableCount = 0;
			} else {
				stableCount++;
			}
			if (stopWhenStable && stableCount >= stableBins) {
				break;
			}
		}
		if (!Double.isFinite(max) || max <= 0.0) {
			max = m.getMaximumValue();
		}
		return (max > 0.0 && Double.isFinite(max)) ? max : 1.0;
	}

	public static double computeBaselineMaxFromResampled(List<Double> values, long excelDeltaMs, ResultsOptions o) {
		if (values == null || values.isEmpty()) {
			return Double.NaN;
		}
		int n = values.size();
		long baselineMs = Math.max(0L, (long) (o != null ? o.spotBaselineWindowMinutes : 2) * 60_000L);
		int maxBins = baselineMs > 0 && excelDeltaMs > 0 ? (int) Math.min(n, (baselineMs / excelDeltaMs) + 1) : n;
		maxBins = Math.max(1, maxBins);

		int stableBins = Math.max(1, o != null ? o.spotBaselineStableBins : 3);
		boolean stopWhenStable = o != null && o.spotBaselineStopWhenStable;

		double max = Double.NEGATIVE_INFINITY;
		int stableCount = 0;
		for (int i = 0; i < maxBins; i++) {
			Double dv = values.get(i);
			if (dv != null && Double.isFinite(dv) && dv > max) {
				max = dv;
				stableCount = 0;
			} else {
				stableCount++;
			}
			if (stopWhenStable && stableCount >= stableBins) {
				break;
			}
		}
		return max;
	}

	/**
	 * Normalized depletion: 0 = full, 1 = empty. Pre-consumed: bin 0 forced to 0.
	 */
	public static double computeDepletionValue(Spot spot, Experiment exp, int binIndex, double raw,
			double baselineMax) {
		if (baselineMax <= 0.0 || !Double.isFinite(baselineMax)) {
			return 0.0;
		}
		if (spot != null && spot.isConsumedBeforeRecording() && binIndex == 0) {
			return 0.0;
		}
		if (!Double.isFinite(raw)) {
			return 0.0;
		}
		double y = (baselineMax - raw) / baselineMax;
		if (y < 0.0) {
			return 0.0;
		}
		if (y > 1.0) {
			return 1.0;
		}
		return y;
	}

	public static double computeBaselineMaxForResampled(Experiment exp, Spot spot, List<Double> values,
			long excelDeltaMs, ResultsOptions options) {
		if (spot != null && spot.isConsumedBeforeRecording()) {
			double ref = resolveReferenceT0(exp, spot);
			if (Double.isFinite(ref) && ref > 0.0) {
				return ref;
			}
		}
		return computeBaselineMaxFromResampled(values, excelDeltaMs, options);
	}
}
