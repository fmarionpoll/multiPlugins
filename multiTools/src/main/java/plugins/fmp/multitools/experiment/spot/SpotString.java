package plugins.fmp.multitools.experiment.spot;

public class SpotString {
	private final String underlyingString;

	public SpotString(String underlyingString) {
		this.underlyingString = underlyingString;
	}

	public String getUnderlyingString() {
		return underlyingString;
	}

	static public int getCageIDFromSpotName(String description) {
		int index = -1;
		if (description == null) {
			return index;
		}
		String[] roiDescription = description.split("_");
		if (roiDescription.length < 2) {
			return index;
		}
		try {
			index = Integer.parseInt(roiDescription[1]);
		} catch (NumberFormatException e1) {
		}
		return index;
	}

	static public int getSpotCagePositionFromSpotName(String description) {
		int index = -1;
		if (description == null) {
			return index;
		}
		String[] roiDescription = description.split("_");
		// Legacy: spot_<cage>_<position>
		if (roiDescription.length != 3) {
			return -1;
		}
		try {
			index = Integer.parseInt(roiDescription[2]);
		} catch (NumberFormatException e1) {
		}
		return index;
	}

	static public int getSpotCageRowFromSpotName(String description) {
		if (description == null) {
			return -1;
		}
		String[] roiDescription = description.split("_");
		// New: spot_<cage>_<row>_<col>
		if (roiDescription.length != 4) {
			return -1;
		}
		try {
			return Integer.parseInt(roiDescription[2]);
		} catch (NumberFormatException e1) {
			return -1;
		}
	}

	static public int getSpotCageColumnFromSpotName(String description) {
		if (description == null) {
			return -1;
		}
		String[] roiDescription = description.split("_");
		// New: spot_<cage>_<row>_<col>
		if (roiDescription.length != 4) {
			return -1;
		}
		try {
			return Integer.parseInt(roiDescription[3]);
		} catch (NumberFormatException e1) {
			return -1;
		}
	}

	static public String createSpotString(int cageID, int cagePosition) {
		return "spot_" + String.format("%03d", cageID) + "_" + String.format("%03d", cagePosition);
	}

	static public String createSpotString(int cageID, int cageRow, int cageColumn) {
		return "spot_" + String.format("%03d", cageID) + "_" + String.format("%03d", cageRow) + "_"
				+ String.format("%03d", cageColumn);
	}

}
