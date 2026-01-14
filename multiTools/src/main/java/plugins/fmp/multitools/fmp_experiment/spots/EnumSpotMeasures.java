package plugins.fmp.multitools.fmp_experiment.spots;

public enum EnumSpotMeasures {
	SPOTS_DESCRIPTION("DESCRIPTION", "experiment description fields"), //
	SPOTS_ARRAY("SPOTS", "array of spots"), //
	SPOTS_MEASURES("MEASURES", "spot measures"), //

	AREA_SUM("AREA_SUM", "Sum grey values of pixels over threshold"), //
	AREA_SUMCLEAN("AREA_SUMCLEAN", "Ratio grey values of pixels over threshold"), //
	AREA_OUT("AREA_OUT", "pixel grey value background"), //
	AREA_DIFF("AREA_DIFF", "grey value -background"), //
	AREA_FLYPRESENT("AREA_FLYPRESENT", "fly is present or not over the spot"), //
	ALL("ALL", "all options");

	private String label;
	private String unit;

	EnumSpotMeasures(String label, String unit) {
		this.label = label;
		this.unit = unit;
	}

	public String toString() {
		return label;
	}

	public String toUnit() {
		return unit;
	}

	public static EnumSpotMeasures findByText(String abbr) {
		for (EnumSpotMeasures v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
