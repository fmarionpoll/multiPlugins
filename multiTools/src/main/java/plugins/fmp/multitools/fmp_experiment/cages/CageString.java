package plugins.fmp.multitools.fmp_experiment.cages;

public class CageString {
	private final String underlyingString;

	public CageString(String underlyingString) {
		this.underlyingString = underlyingString;
	}

	public String getUnderlyingString() {
		return underlyingString;
	}

	static public String getCageNumberFromCageRoiName(String description) {
		return description.substring(description.length() - 3);
	}
}
