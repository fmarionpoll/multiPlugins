package plugins.fmp.multitools.experiment.capillaries;

public enum EnumCapillaryMeasures {
	TOPRAW("TOPRAW", "top capillary limit relative to tO"), //
	TOPLEVEL("TOPLEVEL", "top capillary limit minus evaporation"), //
	BOTTOMLEVEL("BOTTOMLEVEL", "bottom capillary limit"), //
	TOPLEVELDIRECT("TOPLEVELDIRECT", "top level from direct cam detection"), //
	BOTTOMLEVELDIRECT("BOTTOMLEVELDIRECT", "bottom level from direct cam detection"), //
	TOPDERIVATIVE("TOPDERIVATIVE", "derivative of top capillary limit"), //
	THRESHOLD("THRESHOLD", "dynamic threshold computed from empty cages"), //
	GULPS("GULPS", "gulps detected from derivative"), ALL("ALL", "all options");

	private String label;
	private String unit;

	EnumCapillaryMeasures(String label, String unit) {
		this.label = label;
		this.unit = unit;
	}

	public String toString() {
		return label;
	}

	public String toUnit() {
		return unit;
	}

	public static EnumCapillaryMeasures findByText(String abbr) {
		for (EnumCapillaryMeasures v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
