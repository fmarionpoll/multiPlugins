package plugins.fmp.multitools.experiment.cage;

import java.util.ArrayList;

import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.tools.results.EnumResults;

/**
 * One cage-level aggregate curve (e.g. sum of normalized consumption for a stimulus/conc group),
 * stored as a {@link SpotMeasure} on the native camera/sample index so charts and exports share the
 * same series shape.
 */
public final class CageSpotAggregateSeries {

	private final CageSpotStimulusAggregation.StimulusConcKey key;
	private final SpotMeasure measure;
	private final int nSpotsExposed;

	public CageSpotAggregateSeries(CageSpotStimulusAggregation.StimulusConcKey key, SpotMeasure measure,
			int nSpotsExposed) {
		this.key = key;
		this.measure = measure;
		this.nSpotsExposed = nSpotsExposed;
	}

	public CageSpotStimulusAggregation.StimulusConcKey getKey() {
		return key;
	}

	public SpotMeasure getMeasure() {
		return measure;
	}

	public int getNSpotsExposed() {
		return nSpotsExposed;
	}

	public ArrayList<Double> getDataValuesAsList() {
		double[] v = measure != null ? measure.getValues() : null;
		if (v == null || v.length == 0) {
			return new ArrayList<>();
		}
		ArrayList<Double> out = new ArrayList<>(v.length);
		for (double d : v) {
			out.add(d);
		}
		return out;
	}

	public static CageSpotAggregateSeries fromNativeAggregate(CageSpotStimulusAggregation.AggregateSeries agg) {
		if (agg == null || agg.key == null || agg.values == null || agg.values.isEmpty()) {
			return null;
		}
		SpotMeasure m = new SpotMeasure(EnumResults.AGG_SUMCLEAN.name());
		int n = agg.values.size();
		double[] vals = new double[n];
		for (int i = 0; i < n; i++) {
			Double dv = agg.values.get(i);
			vals[i] = (dv != null && Double.isFinite(dv.doubleValue())) ? dv.doubleValue() : Double.NaN;
		}
		m.setValues(vals);
		return new CageSpotAggregateSeries(agg.key, m, agg.nSpotsExposed);
	}
}
