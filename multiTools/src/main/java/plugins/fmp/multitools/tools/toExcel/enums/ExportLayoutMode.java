package plugins.fmp.multitools.tools.toExcel.enums;

/**
 * Export layout for measure series.
 */
public enum ExportLayoutMode {
	/** Wide matrix Excel: one row per series, value columns {@code i*} or {@code t*}. */
	WIDE,
	/**
	 * Normalized CSV tables in a timestamped folder. Capillary export writes
	 * {@code idexpt}/{@code idcage}/{@code idcap} plus {@code measure_*} and {@code gulpevents}.
	 * Spot export writes {@code idexpt}/{@code idcage}/{@code idspot} (or {@code idspotgroup})
	 * plus {@code measure_spot_*} / {@code measure_spotgroup_*}.
	 */
	NORMALIZED
}
