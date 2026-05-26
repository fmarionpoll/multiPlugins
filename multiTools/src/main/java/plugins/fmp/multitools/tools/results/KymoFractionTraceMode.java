package plugins.fmp.multitools.tools.results;

/**
 * Which kymograph fraction trace to plot when diagnostic arrays are available.
 */
public enum KymoFractionTraceMode {
	/** Final fraction after gap interpolation (default). */
	FINAL,
	/** Fraction before bounded gap fill (may contain NaN). */
	BEFORE_GAP_FILL,
	/** Final minus before gap fill (NaN if either operand is NaN). */
	DELTA_GAP_FILL
}
