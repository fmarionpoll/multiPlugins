package plugins.fmp.multitools.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * In-memory per-spot kymograph metric fraction series (and optional |Δf|), keyed by cage id.
 */
public final class KymoAnalysisResult {

	public final Map<Integer, List<SpotKymoSeries>> byCageId;
	public final double[] xAxisMinutes;
	public final int widthBins;

	public KymoAnalysisResult(Map<Integer, List<SpotKymoSeries>> byCageId, double[] xAxisMinutes, int widthBins) {
		this.byCageId = byCageId != null ? byCageId : Collections.emptyMap();
		this.xAxisMinutes = xAxisMinutes != null ? xAxisMinutes : new double[0];
		this.widthBins = widthBins;
	}

	public List<SpotKymoSeries> curvesForCage(int cageId) {
		List<SpotKymoSeries> list = byCageId.get(cageId);
		return list != null ? list : Collections.emptyList();
	}

	public static final class SpotKymoSeries {
		public final Spot spot;
		public final int indexInCage;
		public final double[] fraction;
		public final double[] absDeltaFraction;
		/** Present only when analysis ran with diagnostics: fraction before bounded gap fill. */
		public final double[] fractionBeforeGapFill;

		public SpotKymoSeries(Spot spot, int indexInCage, double[] fraction, double[] absDeltaFraction) {
			this(spot, indexInCage, fraction, absDeltaFraction, null);
		}

		public SpotKymoSeries(Spot spot, int indexInCage, double[] fraction, double[] absDeltaFraction,
				double[] fractionBeforeGapFill) {
			this.spot = spot;
			this.indexInCage = indexInCage;
			this.fraction = fraction;
			this.absDeltaFraction = absDeltaFraction;
			this.fractionBeforeGapFill = fractionBeforeGapFill;
		}
	}
}
