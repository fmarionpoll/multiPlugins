package plugins.fmp.multitools.experiment.timebase;

/**
 * Caller intent for {@link TimestepResolver}: which consumer is asking for a step
 * in ms, so the resolver can apply the right precedence rules.
 */
public enum TimestepResolutionContext {
	/** Excel export: user's {@code buildExcelStepMs} wins when {@code > 0}. */
	FOR_EXCEL_EXPORT,
	/** Charts / native measures: ignore Excel; use measure-native spacing from bin + camera. */
	FOR_CHART_NATIVE_MEASURE,
	/** Cage / series bounds: use {@code userExcelStepMs} when {@code > 0} (caller-supplied). */
	FOR_SERIES_BOUNDS_BUILD
}
