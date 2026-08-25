package plugins.fmp.multitools.experiment.capillary.measurefilter;

import plugins.fmp.multitools.experiment.Experiment;

/**
 * One capillary that matched a Find rule.
 */
public class MeasureFilterHit {
	public final int experimentIndex;
	public final Experiment experiment;
	public final String experimentLabel;
	public final String capillaryName;
	public final int kymographIndex;
	public final double value;
	public final MeasureFilterRule rule;

	public MeasureFilterHit(int experimentIndex, Experiment experiment, String experimentLabel, String capillaryName,
			int kymographIndex, double value, MeasureFilterRule rule) {
		this.experimentIndex = experimentIndex;
		this.experiment = experiment;
		this.experimentLabel = experimentLabel != null ? experimentLabel : "";
		this.capillaryName = capillaryName != null ? capillaryName : "";
		this.kymographIndex = kymographIndex;
		this.value = value;
		this.rule = rule;
	}

	public String formatLabel() {
		String v = Double.isFinite(value) ? String.format("%.2f", value) : "NaN";
		String src = rule != null && rule.source != null ? rule.source.toString() : "?";
		String st = rule != null && rule.stat != null && !rule.source.isScalar() ? (" " + rule.stat) : "";
		return String.format("%s — %s — %s%s=%s", experimentLabel, capillaryName, src, st, v);
	}
}