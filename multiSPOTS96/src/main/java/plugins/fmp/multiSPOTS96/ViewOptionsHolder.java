package plugins.fmp.multiSPOTS96;

import icy.preferences.XMLPreferences;

public class ViewOptionsHolder {

	private static final String KEY_VIEW_SPOTS = "viewSpots";
	private static final String KEY_VIEW_CAGES = "viewCages";
	private static final String KEY_SPOT_DETECTION_MODE = "spotDetectionMode";

	private boolean viewSpots = true;
	private boolean viewCages = true;
	private String spotDetectionMode = "AUTO"; // BASIC, PIPELINED, AUTO

	public boolean isViewSpots() {
		return viewSpots;
	}

	public void setViewSpots(boolean viewSpots) {
		this.viewSpots = viewSpots;
	}

	public boolean isViewCages() {
		return viewCages;
	}

	public void setViewCages(boolean viewCages) {
		this.viewCages = viewCages;
	}

	public String getSpotDetectionMode() {
		return spotDetectionMode;
	}

	public void setSpotDetectionMode(String spotDetectionMode) {
		if (spotDetectionMode == null)
			return;
		this.spotDetectionMode = spotDetectionMode;
	}

	public void load(XMLPreferences prefs) {
		if (prefs == null)
			return;
		viewSpots = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_SPOTS, "true"));
		viewCages = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_CAGES, "true"));
		spotDetectionMode = prefs.get(KEY_SPOT_DETECTION_MODE, "AUTO");
	}

	public void save(XMLPreferences prefs) {
		if (prefs == null)
			return;
		prefs.put(KEY_VIEW_SPOTS, String.valueOf(viewSpots));
		prefs.put(KEY_VIEW_CAGES, String.valueOf(viewCages));
		prefs.put(KEY_SPOT_DETECTION_MODE, spotDetectionMode);
	}
}
