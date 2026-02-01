package plugins.fmp.multitools.series.options;

public enum GulpThresholdSmoothing {
	NONE("none"), BACKWARDS_RECURSION("backwards recursion"), SAVITZKY_GOLAY("Savitzky-Golay");

	private final String label;

	GulpThresholdSmoothing(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}
