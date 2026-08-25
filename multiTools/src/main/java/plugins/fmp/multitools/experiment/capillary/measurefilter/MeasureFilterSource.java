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
	BOTTOM_BASELINE_OUTLIER_FRAC("bottomBaselineOutlierFrac"),
	/** Meniscus Y difference: Y_top[t0] − Y_t00 (empty-mean at t0). */
	T00_MINUS_T0_FILL_PX("t00−t0 fill px");

	private final String label;

	MeasureFilterSource(String label) {
		this.label = label;
	}

	public boolean isScalar() {
		return this == BOTTOM_BASELINE_Y || this == BOTTOM_BASELINE_MAD || this == BOTTOM_BASELINE_OUTLIER_FRAC
				|| this == T00_MINUS_T0_FILL_PX;
	}

	public boolean isBottomRelated() {
		return this == BOTTOMLEVEL || this == BOTTOM_BASELINE_Y || this == BOTTOM_BASELINE_MAD
				|| this == BOTTOM_BASELINE_OUTLIER_FRAC;
	}

	/** True when the rule needs experiment-wide t00 Y cached on capillaries. */
	public boolean requiresT00() {
		return this == T00_MINUS_T0_FILL_PX;
	}

	@Override
	public String toString() {
		return label;
	}
}