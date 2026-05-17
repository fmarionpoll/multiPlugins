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
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.utils.SpotExcelTimeline;

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

	public static List<AggregateSeries> buildAggregates(Experiment exp, Cage cage, Spots allSpots,
			ResultsOptions options, SpotExcelTimeline.SpotExcelGrid grid) {
		Objects.requireNonNull(cage, "cage");
		if (allSpots == null || options == null || grid == null) {
			return List.of();
		}

		if (!supportsAggregateResultType(options.resultType)) {
			Logger.warn("CageSpotStimulusAggregation: unsupported resultType " + options.resultType);
			return List.of();
		}

		List<Spot> spots = cage.getSpotList(allSpots);
		if (spots.isEmpty()) {
			return List.of();
		}

		// Preserve deterministic order: first appearance order in cage spot list.
		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (Spot s : spots) {
			if (s == null || s.getProperties() == null) {
				continue;
			}
			keys.add(new StimulusConcKey(s.getProperties().getStimulus(), s.getProperties().getConcentration()));
		}

		List<AggregateSeries> out = new ArrayList<>(keys.size());
		for (StimulusConcKey k : keys) {
			ArrayList<Double> sum = initZeroSeries(grid.getNBins());
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
				ArrayList<Double> norm = computeNormalizedConsumptionOverGrid(exp, s, options, grid);
				if (norm == null) {
					continue; // discard unusable spot
				}
				addInPlace(sum, norm);
				nExposed++;
			}
			out.add(new AggregateSeries(k, sum, nExposed));
		}
		return out;
	}

	/**
	 * Returns normalized consumption series over the Excel grid, or null if the spot is unusable
	 * (no data / invalid baseline max).
	 */
	private static ArrayList<Double> computeNormalizedConsumptionOverGrid(Experiment exp, Spot spot,
			ResultsOptions options, SpotExcelTimeline.SpotExcelGrid grid) {
		if (spot == null) {
			return null;
		}

		SpotMeasure clean = spot.getSumClean();
		if (clean == null || clean.getValues() == null || clean.getValues().length < 1) {
			return null;
		}

		List<Double> v = clean.getValuesResampledToExcelGrid(grid);
		if (v == null || v.isEmpty()) {
			return null;
		}

		double maxBaseline = SpotPreConsumedSupport.computeBaselineMaxForResampled(exp, spot, v,
				grid.getExcelDeltaMs(), options);
		if (!(maxBaseline > 0.0) || !Double.isFinite(maxBaseline)) {
			return null;
		}

		ArrayList<Double> out = new ArrayList<>(v.size());
		for (int i = 0; i < v.size(); i++) {
			Double dv = v.get(i);
			double raw = (dv != null && Double.isFinite(dv)) ? dv : 0.0;
			out.add(SpotPreConsumedSupport.computeDepletionValue(spot, exp, i, raw, maxBaseline));
		}
		return out;
	}

	private static ArrayList<Double> initZeroSeries(int n) {
		ArrayList<Double> out = new ArrayList<>(Math.max(1, n));
		for (int i = 0; i < n; i++) {
			out.add(0.0);
		}
		return out;
	}

	private static void addInPlace(ArrayList<Double> acc, ArrayList<Double> add) {
		if (acc == null || add == null) {
			return;
		}
		int n = Math.min(acc.size(), add.size());
		for (int i = 0; i < n; i++) {
			double a = acc.get(i) != null ? acc.get(i) : 0.0;
			double b = add.get(i) != null ? add.get(i) : 0.0;
			acc.set(i, a + b);
		}
	}
}

