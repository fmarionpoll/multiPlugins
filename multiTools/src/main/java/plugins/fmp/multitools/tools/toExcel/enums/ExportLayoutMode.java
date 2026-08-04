package plugins.fmp.multitools.tools.toExcel.enums;

/**
 * Excel export layout for measure series.
 */
public enum ExportLayoutMode {
	/** Wide matrix: one row per series, value columns {@code i*} or {@code t*}. */
	WIDE,
	/** Normalized: SERIES descriptors + DATA sheets with series_id, t, value. */
	NORMALIZED
}
