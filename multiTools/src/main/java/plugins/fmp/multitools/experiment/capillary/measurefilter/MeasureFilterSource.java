package plugins.fmp.multitools.experiment.capillary.measurefilter;

/**
 * Capillary measure / scalar sources for Browse Find.
 */
public enum MeasureFilterSource {
	TOPRAW("TOPRAW"),
	TOPLEVEL("TOPLEVEL"),
	BOTTOMLEVEL("BOTTOMLEVEL"),
	DERIVEDVALUES("DERIVEDVALUES"),
	BOTTOM_BASELINE_Y("bottomBaselineY"),
	BOTTOM_BASELINE_MAD("bottomBaselineMad"),
	BOTTOM_BASELINE_OUTLIER_FRAC("bottomBaselineOutlierFrac");

	private final String label;

	MeasureFilterSource(String label) {
		this.label = label;
	}

	public boolean isScalar() {
		return this == BOTTOM_BASELINE_Y || this == BOTTOM_BASELINE_MAD || this == BOTTOM_BASELINE_OUTLIER_FRAC;
	}

	public boolean isBottomRelated() {
		return this == BOTTOMLEVEL || this == BOTTOM_BASELINE_Y || this == BOTTOM_BASELINE_MAD
				|| this == BOTTOM_BASELINE_OUTLIER_FRAC;
	}

	@Override
	public String toString() {
		return label;
	}
}