package plugins.fmp.multiSPOTS96.tools.toExcel;

public enum EnumMeasure {
	TS("t", "s"), TI("i", "n"), X("x", "mm"), Y("y", "mm"), W("w", "mm"), H("h", "mm"), ALIVE("alive", "0/1"),
	SLEEP("sleep", "0/1"), DISTANCE("distance", "mm"), CUMDIST("cumdist", "mm"), OTHER("other", "??");

	private String label;
	private String unit;

	EnumMeasure(String label, String unit) {
		this.label = label;
		this.unit = unit;
	}

	public String toString() {
		return label;
	}

	public String toUnit() {
		return unit;
	}

	public static EnumMeasure findByText(String abbr) {
		for (EnumMeasure v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
