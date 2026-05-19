package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.experiment.spot.SpotPreConsumedSupport;
import plugins.fmp.multitools.experiment.spots.Spots;
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
		return resultType == EnumResults.AREA_SUMCLEAN || resultType == EnumResults.AGG_SUMCLEAN;
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
	 * charts so AGG_SUMCLEAN has the same time base as the displayed AREA_SUMCLEAN spot curves.
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

		double[] camTimeMin = null;
		if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getTimeManager() != null) {
			if (exp.getSeqCamData().getTimeManager().getCamImagesTime_Ms() == null) {
				exp.getSeqCamData().build_MsTimesArray_From_FileNamesList();
			}
			camTimeMin = exp.getSeqCamData().getTimeManager().getCamImagesTime_Minutes();
		}

		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (Spot s : spots) {
			if (s != null && s.getProperties() != null) {
				keys.add(new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration()));
			}
		}

		List<AggregateSeries> out = new ArrayList<>(keys.size());
		for (StimulusConcKey k : keys) {
			int n = nativeAggregateLength(spots, k, camTimeMin);
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
				ArrayList<Double> norm = computeNormalizedConsumptionNative(exp, s, options, camTimeMin, n);
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

	private static int nativeAggregateLength(List<Spot> spots, StimulusConcKey key, double[] camTimeMin) {
		int n = Integer.MAX_VALUE;
		boolean found = false;
		for (Spot s : spots) {
			if (s == null || s.getProperties() == null || s.getSumClean() == null) {
				continue;
			}
			StimulusConcKey keySpot = new StimulusConcKey(s.getProperties().getStimulus(),
					s.getProperties().getConcentration());
			if (!key.equals(keySpot)) {
				continue;
			}
			int count = s.getSumClean().getCount();
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
			double[] camTimeMin, int n) {
		if (spot == null || spot.getSumClean() == null || spot.getSumClean().getValues() == null || n <= 0) {
			return null;
		}
		SpotMeasure clean = spot.getSumClean();
		int count = Math.min(n, clean.getCount());
		double maxBaseline = SpotPreConsumedSupport.computeBaselineMaxValue(exp, spot, clean, camTimeMin, options);
		if (!(maxBaseline > 0.0) || !Double.isFinite(maxBaseline)) {
			return null;
		}

		ArrayList<Double> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double raw = clean.getValueAt(i);
			if (!Double.isFinite(raw)) {
				out.add(Double.NaN);
			} else {
				out.add(SpotPreConsumedSupport.computeDepletionValue(spot, exp, i, raw, maxBaseline));
			}
		}
		return out;
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
}

