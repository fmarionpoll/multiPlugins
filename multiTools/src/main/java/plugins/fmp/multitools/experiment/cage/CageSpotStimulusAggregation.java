package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotPreConsumedSupport;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.AggSumCleanPolicy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Builds cage-level spot aggregates grouped by (stimulus, concentration).
 *
 * Aggregation is defined (for AREA_SUMCLEAN) as the sum over spots in the group of
 * per-spot normalized consumption:
 *
 *   c(t) = (maxBaseline - v(t)) / maxBaseline
 *
 * where maxBaseline is the maximum CLEAN value in the baseline window (default first N minutes).
 */
public final class CageSpotStimulusAggregation {

	public static final class StimulusConcKey {
		public final String stimulus;
		public final String concentration;

		public StimulusConcKey(String stimulus, String concentration) {
			this.stimulus = stimulus != null ? stimulus.trim() : "";
			this.concentration = concentration != null ? concentration.trim() : "";
		}

		@Override
		public int hashCode() {
			return Objects.hash(stimulus, concentration);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			StimulusConcKey other = (StimulusConcKey) obj;
			return Objects.equals(stimulus, other.stimulus) && Objects.equals(concentration, other.concentration);
		}

		@Override
		public String toString() {
			return stimulus + "@" + concentration;
		}
	}

	public static final class AggregateSeries {
		public final StimulusConcKey key;
		public final ArrayList<Double> values;
		public final int nSpotsExposed;

		public AggregateSeries(StimulusConcKey key, ArrayList<Double> values, int nSpotsExposed) {
			this.key = key;
			this.values = values;
			this.nSpotsExposed = nSpotsExposed;
		}
	}

	private CageSpotStimulusAggregation() {
	}

	public static boolean supportsAggregateResultType(EnumResults resultType) {
		return resultType == EnumResults.AREA_SUMCLEAN || resultType == EnumResults.AGG_SUMCLEAN
				|| resultType == EnumResults.AGG_SUMCLEAN_V5 || resultType == EnumResults.AGG_AREA_COUNT_V5
				|| resultType == EnumResults.AGG_MEDIANREF;
	}

	/**
	 * Union of (stimulus, concentration) keys in first-seen order across all cages and spots.
	 */
	public static List<StimulusConcKey> globalStimulusConcKeysFirstSeenOrder(Experiment exp, Spots allSpots) {
		if (exp == null || exp.getCages() == null || allSpots == null) {
			return new ArrayList<>();
		}
		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null) {
				continue;
			}
			for (Spot s : cage.getSpotList(allSpots)) {
				if (s == null || s.getProperties() == null) {
					continue;
				}
				keys.add(new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration()));
			}
		}
		return new ArrayList<>(keys);
	}

	/**
	 * Builds aggregates directly on the stored spot sample index. This is the path used by
	 * charts so AGG_SUMCLEAN / AGG_SUMCLEAN_V5 / AGG_AREA_COUNT_V5 share the same time base as the underlying per-spot series.
	 */
	public static List<AggregateSeries> buildAggregatesOnNativeSamples(Experiment exp, Cage cage, Spots allSpots,
			ResultsOptions options) {
		Objects.requireNonNull(cage, "cage");
		if (allSpots == null || options == null) {
			return List.of();
		}
		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			return List.of();
		}

		double[] camTimeMin = camTimeMinutesForExperiment(exp);

		if (options.resultType == EnumResults.AGG_AREA_COUNT_V5) {
			return buildAreaCountV5AggregatesOnNativeSamples(spots, camTimeMin);
		}

		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (Spot s : spots) {
			if (s != null && s.getProperties() != null) {
				keys.add(new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration()));
			}
		}

		AggSumCleanPolicy pol = options.aggSumCleanPolicy != null ? options.aggSumCleanPolicy : AggSumCleanPolicy.LEGACY;
		int nCageAll = minBinCountAllSpots(spots, camTimeMin, options);
		double[] cageMedianDriftFromT0 = null;
		double[] refMedianDriftFromT0 = null;
		if (pol == AggSumCleanPolicy.V4_COMMON_MODE && nCageAll > 0) {
			double[] med = medianDepletionIntensityPerBin(spots, nCageAll, null, options);
			cageMedianDriftFromT0 = driftCorrectionFromT0(med);
		} else if (pol == AggSumCleanPolicy.V4_REF_STIM && nCageAll > 0) {
			StimulusConcKey refKey = new StimulusConcKey(options.aggRefStimulus, options.aggRefConcentration);
			if (hasAnySpotMatching(spots, refKey)) {
				double[] med = medianDepletionIntensityPerBin(spots, nCageAll, refKey, options);
				refMedianDriftFromT0 = driftCorrectionFromT0(med);
			}
		}

		List<AggregateSeries> out = new ArrayList<>(keys.size());
		for (StimulusConcKey k : keys) {
			int n = nativeAggregateLength(spots, k, camTimeMin, options);
			if (n <= 0) {
				continue;
			}
			ArrayList<Double> sum = initNanSeries(n);
			int nExposed = 0;
			for (Spot s : spots) {
				if (s == null || s.getProperties() == null) {
					continue;
				}
				StimulusConcKey keySpot = new StimulusConcKey(s.getProperties().getStimulus(),
						s.getProperties().getConcentration());
				if (!k.equals(keySpot)) {
					continue;
				}
				ArrayList<Double> norm = computeNormalizedConsumptionNative(exp, s, options, camTimeMin, n,
						cageMedianDriftFromT0, refMedianDriftFromT0);
				if (norm == null) {
					continue;
				}
				addFiniteInPlace(sum, norm);
				nExposed++;
			}
			out.add(new AggregateSeries(k, sum, nExposed));
		}
		return out;
	}

	private static SpotMeasure depletionIntensitySeries(Spot s, EnumResults buildResultType) {
		if (s == null) {
			return null;
		}
		if (buildResultType == EnumResults.AGG_SUMCLEAN_V5) {
			return s.getGreySumCleanV5();
		}
		return s.getSumClean();
	}

	private static boolean isV5DepletionAggregate(EnumResults buildResultType) {
		return buildResultType == EnumResults.AGG_SUMCLEAN_V5;
	}

	private static int nativeAggregateLength(List<Spot> spots, StimulusConcKey key, double[] camTimeMin,
			ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : EnumResults.AGG_SUMCLEAN;
		int n = Integer.MAX_VALUE;
		boolean found = false;
		for (Spot s : spots) {
			if (s == null || s.getProperties() == null) {
				continue;
			}
			SpotMeasure intensity = depletionIntensitySeries(s, rt);
			if (intensity == null) {
				continue;
			}
			StimulusConcKey keySpot = new StimulusConcKey(s.getProperties().getStimulus(),
					s.getProperties().getConcentration());
			if (!key.equals(keySpot)) {
				continue;
			}
			int count = intensity.getCount();
			if (count <= 0) {
				continue;
			}
			if (camTimeMin != null) {
				count = Math.min(count, camTimeMin.length);
			}
			if (count > 0) {
				n = Math.min(n, count);
				found = true;
			}
		}
		return found ? n : 0;
	}

	private static ArrayList<Double> computeNormalizedConsumptionNative(Experiment exp, Spot spot, ResultsOptions options,
			double[] camTimeMin, int n, double[] cageMedianDriftFromT0, double[] refMedianDriftFromT0) {
		SpotMeasure intensity = depletionIntensitySeries(spot, options.resultType);
		if (spot == null || intensity == null || intensity.getValues() == null || n <= 0) {
			return null;
		}
		int count = Math.min(n, intensity.getCount());
		double maxBaseline;
		if (isV5DepletionAggregate(options.resultType)) {
			maxBaseline = SpotPreConsumedSupport.computeBaselineMaxFromMeasure(intensity, camTimeMin, options);
		} else {
			maxBaseline = SpotPreConsumedSupport.computeBaselineMaxValue(exp, spot, intensity, camTimeMin, options);
		}
		if (!(maxBaseline > 0.0) || !Double.isFinite(maxBaseline)) {
			return null;
		}

		AggSumCleanPolicy pol = options.aggSumCleanPolicy != null ? options.aggSumCleanPolicy : AggSumCleanPolicy.LEGACY;
		double flyTh = options.aggFlyGuardMaxFraction;
		if (!Double.isFinite(flyTh)) {
			flyTh = 0.2;
		}
		flyTh = Math.max(0.0, Math.min(1.0, flyTh));

		ArrayList<Double> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double raw = intensity.getValueAt(i);
			if (!Double.isFinite(raw)) {
				out.add(Double.NaN);
				continue;
			}
			if (pol == AggSumCleanPolicy.V4_COMMON_MODE && cageMedianDriftFromT0 != null
					&& cageMedianDriftFromT0.length > 0) {
				int ii = Math.min(i, cageMedianDriftFromT0.length - 1);
				double d = cageMedianDriftFromT0[ii];
				if (Double.isFinite(d)) {
					raw = raw - d;
				}
			} else if (pol == AggSumCleanPolicy.V4_REF_STIM && refMedianDriftFromT0 != null
					&& refMedianDriftFromT0.length > 0) {
				int ii = Math.min(i, refMedianDriftFromT0.length - 1);
				double d = refMedianDriftFromT0[ii];
				if (Double.isFinite(d)) {
					raw = raw - d;
				}
			}

			double y = SpotPreConsumedSupport.computeDepletionValue(spot, exp, i, raw, maxBaseline);
			if (pol == AggSumCleanPolicy.V4_FLY_GUARD && flyOccupancyFraction(spot, i) > flyTh) {
				y = 0.0;
			}
			out.add(y);
		}
		return out;
	}

	private static int minBinCountAllSpots(List<Spot> spots, double[] camTimeMin, ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : EnumResults.AGG_SUMCLEAN;
		int n = Integer.MAX_VALUE;
		boolean found = false;
		for (Spot s : spots) {
			if (s == null) {
				continue;
			}
			SpotMeasure intensity = depletionIntensitySeries(s, rt);
			if (intensity == null) {
				continue;
			}
			int c = intensity.getCount();
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

	private static boolean hasAnySpotMatching(List<Spot> spots, StimulusConcKey refKey) {
		if (refKey == null || spots == null) {
			return false;
		}
		for (Spot s : spots) {
			if (s == null || s.getProperties() == null) {
				continue;
			}
			StimulusConcKey k = new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration());
			if (refKey.equals(k)) {
				return true;
			}
		}
		return false;
	}

	private static double[] medianDepletionIntensityPerBin(List<Spot> spots, int n, StimulusConcKey filterKey,
			ResultsOptions options) {
		EnumResults rt = options != null ? options.resultType : EnumResults.AGG_SUMCLEAN;
		double[] out = new double[n];
		double[] scratch = new double[spots.size()];
		for (int i = 0; i < n; i++) {
			int m = 0;
			for (Spot s : spots) {
				SpotMeasure intensity = depletionIntensitySeries(s, rt);
				if (s == null || intensity == null) {
					continue;
				}
				if (filterKey != null) {
					if (s.getProperties() == null) {
						continue;
					}
					StimulusConcKey ks = new StimulusConcKey(s.getProperties().getStimulus(),
							s.getProperties().getConcentration());
					if (!filterKey.equals(ks)) {
						continue;
					}
				}
				if (i >= intensity.getCount()) {
					continue;
				}
				double v = intensity.getValueAt(i);
				if (Double.isFinite(v)) {
					scratch[m++] = v;
				}
			}
			out[i] = m > 0 ? medianOfFirst(scratch, m) : Double.NaN;
		}
		return out;
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

	private static double[] driftCorrectionFromT0(double[] series) {
		if (series == null || series.length == 0) {
			return null;
		}
		double t0 = Double.NaN;
		for (int i = 0; i < series.length; i++) {
			if (Double.isFinite(series[i])) {
				t0 = series[i];
				break;
			}
		}
		if (!Double.isFinite(t0)) {
			return new double[series.length];
		}
		double[] drift = new double[series.length];
		for (int i = 0; i < series.length; i++) {
			if (Double.isFinite(series[i])) {
				drift[i] = series[i] - t0;
			} else {
				drift[i] = 0.0;
			}
		}
		return drift;
	}

	private static double flyOccupancyFraction(Spot spot, int binIndex) {
		if (spot == null || spot.getFlyPresent() == null) {
			return 0.0;
		}
		SpotMeasure fp = spot.getFlyPresent();
		if (binIndex < 0 || binIndex >= fp.getCount()) {
			return 0.0;
		}
		int denom = spot.getFlyPresentDenomPixelCount();
		if (denom <= 0) {
			return 0.0;
		}
		double v = fp.getValueAt(binIndex);
		if (!Double.isFinite(v) || v < 0.0) {
			return 0.0;
		}
		return v / denom;
	}

	private static ArrayList<Double> initNanSeries(int n) {
		ArrayList<Double> out = new ArrayList<>(Math.max(1, n));
		for (int i = 0; i < n; i++) {
			out.add(Double.NaN);
		}
		return out;
	}

	/**
	 * Sums finite addend values per bin. Bins where no addend contributed a finite value stay NaN.
	 */
	private static void addFiniteInPlace(ArrayList<Double> acc, ArrayList<Double> add) {
		if (acc == null || add == null) {
			return;
		}
		int n = Math.min(acc.size(), add.size());
		for (int i = 0; i < n; i++) {
			Double bv = add.get(i);
			double b = (bv != null) ? bv.doubleValue() : Double.NaN;
			if (!Double.isFinite(b)) {
				continue;
			}
			Double av = acc.get(i);
			double a = (av != null) ? av.doubleValue() : Double.NaN;
			if (!Double.isFinite(a)) {
				acc.set(i, b);
			} else {
				acc.set(i, a + b);
			}
		}
	}

	private static double[] camTimeMinutesForExperiment(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getTimeManager() == null) {
			return null;
		}
		if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null) {
			exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
		}
		return exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();
	}

	private static List<AggregateSeries> buildAreaCountV5AggregatesOnNativeSamples(List<Spot> spots,
			double[] camTimeMin) {
		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (Spot s : spots) {
			if (s != null && s.getProperties() != null) {
				keys.add(new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration()));
			}
		}
		List<AggregateSeries> out = new ArrayList<>(keys.size());
		for (StimulusConcKey k : keys) {
			int n = nativeAggregateLengthForAreaCountV5(spots, k, camTimeMin);
			if (n <= 0) {
				continue;
			}
			ArrayList<Double> sum = initNanSeries(n);
			int nExposed = 0;
			for (Spot s : spots) {
				if (s == null || s.getProperties() == null) {
					continue;
				}
				StimulusConcKey keySpot = new StimulusConcKey(s.getProperties().getStimulus(),
						s.getProperties().getConcentration());
				if (!k.equals(keySpot)) {
					continue;
				}
				ArrayList<Double> row = areaCountV5ValuesForBins(s, n, camTimeMin);
				if (row == null) {
					continue;
				}
				addFiniteInPlace(sum, row);
				nExposed++;
			}
			out.add(new AggregateSeries(k, sum, nExposed));
		}
		return out;
	}

	private static int nativeAggregateLengthForAreaCountV5(List<Spot> spots, StimulusConcKey key, double[] camTimeMin) {
		int n = Integer.MAX_VALUE;
		boolean found = false;
		for (Spot s : spots) {
			if (s == null || s.getProperties() == null) {
				continue;
			}
			SpotMeasure intensity = s.getAreaCountV5();
			if (intensity == null) {
				continue;
			}
			StimulusConcKey keySpot = new StimulusConcKey(s.getProperties().getStimulus(),
					s.getProperties().getConcentration());
			if (!key.equals(keySpot)) {
				continue;
			}
			int c = intensity.getCount();
			if (c <= 0) {
				continue;
			}
			if (camTimeMin != null) {
				c = Math.min(c, camTimeMin.length);
			}
			if (c > 0) {
				n = Math.min(n, c);
				found = true;
			}
		}
		return found ? n : 0;
	}

	private static ArrayList<Double> areaCountV5ValuesForBins(Spot s, int n, double[] camTimeMin) {
		if (s == null || n <= 0) {
			return null;
		}
		SpotMeasure intensity = s.getAreaCountV5();
		if (intensity == null || intensity.getValues() == null) {
			return null;
		}
		int avail = intensity.getCount();
		if (avail <= 0) {
			return null;
		}
		if (camTimeMin != null) {
			avail = Math.min(avail, camTimeMin.length);
		}
		if (avail < n) {
			return null;
		}
		ArrayList<Double> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(intensity.getValueAt(i));
		}
		return out;
	}
}

