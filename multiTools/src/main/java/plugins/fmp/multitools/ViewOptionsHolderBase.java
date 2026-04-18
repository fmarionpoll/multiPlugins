package plugins.fmp.multitools;

import icy.preferences.XMLPreferences;

/**
 * Shared base for plugin ViewOptionsHolders. Keeps the fields that are
 * common across multiCAFE and multiSPOTS96 ({@code viewCages},
 * {@code defaultNominalIntervalSec}) and the load/save orchestration.
 * Plugin-specific flags are handled by subclasses via
 * {@link #loadPluginFields(XMLPreferences)} and
 * {@link #savePluginFields(XMLPreferences)}.
 */
public abstract class ViewOptionsHolderBase {

	protected static final String KEY_VIEW_CAGES = "viewCages";
	protected static final String KEY_DEFAULT_NOMINAL_INTERVAL_SEC = "defaultNominalIntervalSec";

	protected boolean viewCages = true;
	protected int defaultNominalIntervalSec = 60;

	public boolean isViewCages() {
		return viewCages;
	}

	public void setViewCages(boolean viewCages) {
		this.viewCages = viewCages;
	}

	public int getDefaultNominalIntervalSec() {
		return defaultNominalIntervalSec;
	}

	public void setDefaultNominalIntervalSec(int sec) {
		this.defaultNominalIntervalSec = sec;
	}

	public void load(XMLPreferences prefs) {
		if (prefs == null)
			return;
		viewCages = readBool(prefs, KEY_VIEW_CAGES, true);
		defaultNominalIntervalSec = Math.max(1, readInt(prefs, KEY_DEFAULT_NOMINAL_INTERVAL_SEC, 60));
		loadPluginFields(prefs);
	}

	public void save(XMLPreferences prefs) {
		if (prefs == null)
			return;
		prefs.put(KEY_VIEW_CAGES, String.valueOf(viewCages));
		prefs.put(KEY_DEFAULT_NOMINAL_INTERVAL_SEC, String.valueOf(defaultNominalIntervalSec));
		savePluginFields(prefs);
	}

	protected abstract void loadPluginFields(XMLPreferences prefs);

	protected abstract void savePluginFields(XMLPreferences prefs);

	protected static boolean readBool(XMLPreferences prefs, String key, boolean defaultValue) {
		return "true".equalsIgnoreCase(prefs.get(key, String.valueOf(defaultValue)));
	}

	protected static int readInt(XMLPreferences prefs, String key, int defaultValue) {
		try {
			return Integer.parseInt(prefs.get(key, String.valueOf(defaultValue)));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
