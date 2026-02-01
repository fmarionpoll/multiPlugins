package plugins.fmp.multitools.series.options;

public enum GulpThresholdMethod {
	MEAN_PLUS_SD("mean + k*SD"), MEDIAN_PLUS_IQR("median + k*IQR"), MEDIAN_PLUS_MAD("median + k*MAD");

	private final String label;

	GulpThresholdMethod(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}
