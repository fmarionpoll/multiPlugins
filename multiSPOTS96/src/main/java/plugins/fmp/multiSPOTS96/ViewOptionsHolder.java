package plugins.fmp.multiSPOTS96;

import icy.preferences.XMLPreferences;

public class ViewOptionsHolder {

	private static final String KEY_VIEW_SPOTS = "viewSpots";
	private static final String KEY_VIEW_CAGES = "viewCages";

	private boolean viewSpots = true;
	private boolean viewCages = true;

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

	public void load(XMLPreferences prefs) {
		if (prefs == null)
			return;
		viewSpots = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_SPOTS, "true"));
		viewCages = "true".equalsIgnoreCase(prefs.get(KEY_VIEW_CAGES, "true"));
	}

	public void save(XMLPreferences prefs) {
		if (prefs == null)
			return;
		prefs.put(KEY_VIEW_SPOTS, String.valueOf(viewSpots));
		prefs.put(KEY_VIEW_CAGES, String.valueOf(viewCages));
	}
}
