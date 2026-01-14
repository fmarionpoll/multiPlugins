package plugins.fmp.multiSPOTS96.dlg.d_spotsMeasures;

public enum EnumColorDistanceType {
	L1("L1"), L2("L2");

	private String label;

	EnumColorDistanceType(String label) {
		this.label = label;
	}

	public String toString() {
		return label;
	}

	public static EnumColorDistanceType findByText(String abbr) {
		for (EnumColorDistanceType v : values()) {
			if (v.toString().equals(abbr)) {
				return v;
			}
		}
		return null;
	}
}
