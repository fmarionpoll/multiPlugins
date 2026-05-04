package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
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

	public static List<AggregateSeries> buildAggregates(Cage cage, Spots allSpots, ResultsOptions options,
			SpotExcelTimeline.SpotExcelGrid grid) {
		Objects.requireNonNull(cage, "cage");
		if (allSpots == null || options == null || grid == null) {
			return List.of();
		}

		if (options.resultType != EnumResults.AREA_SUMCLEAN) {
			Logger.warn("CageSpotStimulusAggregation: only AREA_SUMCLEAN is supported (got " + options.resultType + ")");
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
				ArrayList<Double> norm = computeNormalizedConsumptionOverGrid(s, options, grid);
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
	private static ArrayList<Double> computeNormalizedConsumptionOverGrid(Spot spot, ResultsOptions options,
			SpotExcelTimeline.SpotExcelGrid grid) {
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

		double maxBaseline = computeBaselineMax(v, grid.getExcelDeltaMs(), options);
		if (!(maxBaseline > 0.0) || !Double.isFinite(maxBaseline)) {
			return null;
		}

		ArrayList<Double> out = new ArrayList<>(v.size());
		for (Double dv : v) {
			if (dv == null || !Double.isFinite(dv)) {
				out.add(0.0); // missing bins do not contribute
				continue;
			}
			out.add((maxBaseline - dv) / maxBaseline);
		}
		return out;
	}

	private static double computeBaselineMax(List<Double> values, long excelDeltaMs, ResultsOptions o) {
		int n = values.size();
		if (n < 1) {
			return Double.NaN;
		}
		long baselineMs = Math.max(0L, (long) o.spotBaselineWindowMinutes * 60_000L);
		int maxBins = baselineMs > 0 && excelDeltaMs > 0 ? (int) Math.min(n, (baselineMs / excelDeltaMs) + 1) : n;
		maxBins = Math.max(1, maxBins);

		int stableBins = Math.max(1, o.spotBaselineStableBins);
		boolean stopWhenStable = o.spotBaselineStopWhenStable;

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

