package plugins.fmp.multitools.experiment.capillary.measurefilter;

public enum MeasureFilterOp {
	GT(">"),
	GE(">="),
	LT("<"),
	LE("<="),
	BETWEEN("between"),
	IS_NAN("isNaN");

	private final String label;

	MeasureFilterOp(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}
}