package plugins.fmp.multitools.experiment.capillary.measurefilter;

/**
 * One Find rule: Source → Stat → Op + threshold(s).
 */
public class MeasureFilterRule {
	public MeasureFilterSource source = MeasureFilterSource.BOTTOM_BASELINE_MAD;
	public MeasureFilterStat stat = MeasureFilterStat.VALUE;
	public MeasureFilterOp op = MeasureFilterOp.GE;
	public double threshold = 3.0;
	public double threshold2 = 0.0;

	/**
	 * When true and source is BOTTOM_BASELINE_Y with IS_NAN, require BOTTOMLEVEL series present.
	 */
	public boolean requireBottomSeriesIfBaselineMissing = true;

	public MeasureFilterRule() {
	}

	public MeasureFilterRule(MeasureFilterSource source, MeasureFilterStat stat, MeasureFilterOp op, double threshold) {
		this.source = source;
		this.stat = stat;
		this.op = op;
		this.threshold = threshold;
	}

	public MeasureFilterRule copy() {
		MeasureFilterRule r = new MeasureFilterRule();
		r.source = source;
		r.stat = stat;
		r.op = op;
		r.threshold = threshold;
		r.threshold2 = threshold2;
		r.requireBottomSeriesIfBaselineMissing = requireBottomSeriesIfBaselineMissing;
		return r;
	}
}