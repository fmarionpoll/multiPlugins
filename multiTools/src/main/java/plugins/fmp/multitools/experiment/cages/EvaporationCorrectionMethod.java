package plugins.fmp.multitools.experiment.cages;

/**
 * How Y_ref(t) is built from nFlies==0 topraw curves before TOPLEVEL
 * subtraction.
 */
public enum EvaporationCorrectionMethod {
	/** Mean of no-fly topraw, zeroed at t0 (traditional). */
	AVERAGE,
	/** Mean then fit Y=A*(1-exp(-t/tau)); fallback to AVERAGE if fit fails. */
	MODEL;

	public static EvaporationCorrectionMethod fromOrDefault(EvaporationCorrectionMethod m) {
		return m != null ? m : MODEL;
	}

	public String displayLabel() {
		switch (this) {
		case AVERAGE:
			return "evap: mean of no-fly";
		case MODEL:
		default:
			return "evap: exponential model";
		}
	}

	public String tooltip() {
		switch (this) {
		case AVERAGE:
			return "Traditional: mean topraw of capillaries with nFlies=0, zeroed at t0, subtracted from each capillary.";
		case MODEL:
		default:
			return "Mean of no-fly topraw, then fit Y=A*(1-exp(-t/tau)) (Huber-robust). Falls back to mean if fit fails.";
		}
	}

	@Override
	public String toString() {
		return displayLabel();
	}
}
