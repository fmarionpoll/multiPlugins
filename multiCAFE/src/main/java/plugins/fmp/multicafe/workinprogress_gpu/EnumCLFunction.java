package plugins.fmp.multicafe.workinprogress_gpu;

public enum EnumCLFunction {
	MULTIPLY2ARRAYS("Multiply2Arrays"),
	AFFINETRANSFORM2D("affineTransform2D"),
	PERSPECTIVETRANSFORM2D("perspectiveTransform2D");

	private String label;

	EnumCLFunction(String label) {
		this.label = label;
	}

	public String toString() {
		return label;
	}

	public static EnumCLFunction findByText(String abbr) {
		for (EnumCLFunction v : values())
			if (v.toString().equals(abbr))
				return v;
		return null;
	}

}
