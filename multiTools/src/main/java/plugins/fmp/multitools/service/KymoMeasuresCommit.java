package plugins.fmp.multitools.service;

import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries;

/**
 * Copies in-memory kymograph analysis series onto per-spot {@link SpotMeasure} fields.
 */
public final class KymoMeasuresCommit {

	private KymoMeasuresCommit() {
	}

	public static void apply(KymoAnalysisResult result, Experiment exp) {
		if (result == null || result.byCageId.isEmpty()) {
			return;
		}
		for (Map.Entry<Integer, List<SpotKymoSeries>> e : result.byCageId.entrySet()) {
			List<SpotKymoSeries> rows = e.getValue();
			if (rows == null) {
				continue;
			}
			for (SpotKymoSeries row : rows) {
				if (row == null || row.spot == null) {
					continue;
				}
				copyRow(row);
			}
		}
		if (exp != null && exp.getCages() != null) {
			exp.getCages().clearSpotAggregatesCache();
		}
	}

	private static void copyRow(SpotKymoSeries row) {
		Spot spot = row.spot;
		copyDoubles(spot.getKymoFract(), row.fraction);
		copyDoubles(spot.getKymoAbsDelta(), row.absDeltaFraction);
		copyIntsAsDoubles(spot.getKymoGreenHeight(), row.greenHeight);
		copyDoubles(spot.getKymoGreenHeightRatio(), row.greenHeightRatio);
	}

	private static void copyDoubles(SpotMeasure measure, double[] src) {
		if (measure == null) {
			return;
		}
		if (src == null || src.length == 0) {
			measure.setValues(new double[0]);
			return;
		}
		measure.setValues(src.clone());
	}

	private static void copyIntsAsDoubles(SpotMeasure measure, int[] src) {
		if (measure == null) {
			return;
		}
		if (src == null || src.length == 0) {
			measure.setValues(new double[0]);
			return;
		}
		double[] vals = new double[src.length];
		for (int i = 0; i < src.length; i++) {
			vals[i] = src[i];
		}
		measure.setValues(vals);
	}
}
