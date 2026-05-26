package plugins.fmp.multitools.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.spot.Spot;

/**
 * In-memory chroma-fraction (and optional |Δf|) series per spot, keyed by cage id.
 */
public final class KymographChromaAnalysisResult {

	public final Map<Integer, List<SpotChromaKymoSeries>> byCageId;
	public final double[] xAxisMinutes;
	public final int widthBins;

	public KymographChromaAnalysisResult(Map<Integer, List<SpotChromaKymoSeries>> byCageId, double[] xAxisMinutes,
			int widthBins) {
		this.byCageId = byCageId != null ? byCageId : Collections.emptyMap();
		this.xAxisMinutes = xAxisMinutes != null ? xAxisMinutes : new double[0];
		this.widthBins = widthBins;
	}

	public List<SpotChromaKymoSeries> curvesForCage(int cageId) {
		List<SpotChromaKymoSeries> list = byCageId.get(cageId);
		return list != null ? list : Collections.emptyList();
	}

	public static final class SpotChromaKymoSeries {
		public final Spot spot;
		public final int indexInCage;
		public final double[] fraction;
		public final double[] absDeltaFraction;

		public SpotChromaKymoSeries(Spot spot, int indexInCage, double[] fraction, double[] absDeltaFraction) {
			this.spot = spot;
			this.indexInCage = indexInCage;
			this.fraction = fraction;
			this.absDeltaFraction = absDeltaFraction;
		}
	}
}
