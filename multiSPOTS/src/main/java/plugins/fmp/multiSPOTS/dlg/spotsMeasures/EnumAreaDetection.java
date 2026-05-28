package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

public enum EnumAreaDetection {
	SINGLE("simple threshold"), COLORARRAY("Color array"), NONE("undefined");

	private String label;

	EnumAreaDetection(String label) {
		this.label = label;
	}

	public String toString() {
		return label;
	}

	public static EnumAreaDetection findByText(String abbr) {
		for (EnumAreaDetection v : values()) {
			if (v.toString().equals(abbr)) {
				return v;
			}
		}
		return null;
	}
}
