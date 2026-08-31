package plugins.fmp.multitools.experiment.capillary.geometry;

/** Green-corridor extensions expressed relative to physical blue length. */
public final class CorridorExtensionRatios {
	private final double upper;
	private final double lower;

	public CorridorExtensionRatios(double upper, double lower) {
		if (!Double.isFinite(upper) || !Double.isFinite(lower) || upper < 0 || lower < 0)
			throw new IllegalArgumentException("corridor extension ratios must be finite and non-negative");
		this.upper = upper;
		this.lower = lower;
	}

	public double getUpper() { return upper; }
	public double getLower() { return lower; }

	public CorridorExtensionRatios plus(double upperDelta, double lowerDelta) {
		return new CorridorExtensionRatios(Math.max(0, upper + upperDelta), Math.max(0, lower + lowerDelta));
	}
}
