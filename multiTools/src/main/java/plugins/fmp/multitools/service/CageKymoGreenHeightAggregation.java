package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;

/**
 * Cage-level kymograph aggregates of per-spot {@link KymoAnalysisResult.SpotKymoSeries#greenHeightRatio}, grouped by
 * (stimulus, concentration). Each bin sums finite per-spot h/h₀ values (same role as {@code AGG_SUMCLEAN} on camera
 * traces).
 */
public final class CageKymoGreenHeightAggregation {

	public static final class SumSeries {
		public final StimulusConcKey key;
		public final double[] values;
		public final int nSpotsExposed;

		public SumSeries(StimulusConcKey key, double[] values, int nSpotsExposed) {
			this.key = key;
			this.values = values;
			this.nSpotsExposed = nSpotsExposed;
		}
	}

	private CageKymoGreenHeightAggregation() {
	}

	public static List<SumSeries> buildSumRatioByStimulusConc(List<SpotKymoSeries> rows, int nBins) {
		if (rows == null || rows.isEmpty() || nBins <= 0) {
			return List.of();
		}
		Set<StimulusConcKey> keys = new LinkedHashSet<>();
		for (SpotKymoSeries row : rows) {
			StimulusConcKey k = keyForRow(row);
			if (k != null) {
				keys.add(k);
			}
		}
		List<SumSeries> out = new ArrayList<>(keys.size());
		for (StimulusConcKey k : keys) {
			double[] sum = new double[nBins];
			for (int j = 0; j < nBins; j++) {
				sum[j] = Double.NaN;
			}
			int nExposed = 0;
			for (SpotKymoSeries row : rows) {
				if (row == null || !k.equals(keyForRow(row))) {
					continue;
				}
				nExposed++;
				double[] ratio = row.greenHeightRatio;
				int len = ratio != null ? Math.min(nBins, ratio.length) : 0;
				for (int j = 0; j < len; j++) {
					double v = ratio[j];
					if (!Double.isFinite(v)) {
						continue;
					}
					if (!Double.isFinite(sum[j])) {
						sum[j] = 0.0;
					}
					sum[j] += v;
				}
			}
			if (nExposed > 0) {
				out.add(new SumSeries(k, sum, nExposed));
			}
		}
		return out;
	}

	private static StimulusConcKey keyForRow(SpotKymoSeries row) {
		if (row == null) {
			return null;
		}
		Spot spot = row.spot;
		if (spot == null || spot.getProperties() == null) {
			return new StimulusConcKey("", "");
		}
		return new StimulusConcKey(spot.getProperties().getStimulus(), spot.getProperties().getConcentration());
	}
}
