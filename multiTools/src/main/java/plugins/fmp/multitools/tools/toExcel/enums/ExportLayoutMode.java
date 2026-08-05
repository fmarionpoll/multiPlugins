package plugins.fmp.multitools.tools.toExcel.enums;

/**
 * Excel export layout for measure series.
 */
public enum ExportLayoutMode {
	/** Wide matrix: one row per series, value columns {@code i*} or {@code t*}. */
	WIDE,
	/** Normalized: SERIES once per entity_id; DATA_<measure> joins on entity_id. */
	NORMALIZED
}
