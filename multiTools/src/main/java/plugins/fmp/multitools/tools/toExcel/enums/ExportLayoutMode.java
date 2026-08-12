package plugins.fmp.multitools.tools.toExcel.enums;

/**
 * Export layout for measure series.
 */
public enum ExportLayoutMode {
	/** Wide matrix Excel: one row per series, value columns {@code i*} or {@code t*}. */
	WIDE,
	/**
	 * Normalized CSV tables: descriptor files ({@code idexpt}/{@code idcage}/{@code idcap})
	 * plus dense {@code measure_*} and sparse {@code gulpevents} in a timestamped folder.
	 */
	NORMALIZED
}
