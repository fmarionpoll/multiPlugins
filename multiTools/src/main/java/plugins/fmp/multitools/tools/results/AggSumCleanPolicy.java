package plugins.fmp.multitools.tools.results;

/**
 * Policy for cage-level {@code AGG_SUMCLEAN} and {@code AGG_SUMCLEAN_V5} depletion aggregation (evaluation / V4).
 */
public enum AggSumCleanPolicy {
	/** Current behaviour: fixed early-window max baseline, no drift correction. */
	LEGACY("Legacy (current AGG)"),
	/** Skip first N minutes (or bins if no camera time) when scanning for baseline max. */
	V4_BASELINE_PLUS("V4 Baseline+ (skip warm-up window)"),
	/** Subtract cage-wide median drift of {@code sumClean} before depletion. */
	V4_COMMON_MODE("V4 Common mode (cage median drift)"),
	/** Zero depletion bins where fly occupancy exceeds a fraction of the spot ROI. */
	V4_FLY_GUARD("V4 Fly guard (ignore high-occupancy bins)"),
	/** Subtract median drift of a reference (stimulus, conc) group in the same cage. */
	V4_REF_STIM("V4 Ref stimulus (median drift of control group)");

	private final String displayLabel;

	AggSumCleanPolicy(String displayLabel) {
		this.displayLabel = displayLabel;
	}

	public String getDisplayLabel() {
		return displayLabel;
	}

	@Override
	public String toString() {
		return displayLabel;
	}
}
