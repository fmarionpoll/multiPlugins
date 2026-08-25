package plugins.fmp.multitools.experiment.capillary.measurefilter;

/**
 * Aggregate over a capillary series. Ignored for scalar sources (VALUE).
 */
public enum MeasureFilterStat {
	VALUE("value"),
	MIN("MIN"),
	MAX("MAX"),
	RANGE("RANGE"),
	MEDIAN("MEDIAN"),
	MAD("MAD"),
	MEAN("MEAN"),
	FIRST("FIRST"),
	LAST("LAST"),
	ABSMAX("ABSMAX"),
	MISSING("MISSING");

	private final String label;

	MeasureFilterStat(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}