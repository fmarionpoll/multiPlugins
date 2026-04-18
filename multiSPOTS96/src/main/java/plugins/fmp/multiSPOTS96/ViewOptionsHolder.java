package plugins.fmp.multiSPOTS96;

import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.ViewOptionsHolderBase;
import plugins.fmp.multitools.series.options.SpotDetectionMode;
import plugins.fmp.multitools.tools.Logger;

public class ViewOptionsHolder extends ViewOptionsHolderBase {

	private static final String KEY_VIEW_SPOTS = "viewSpots";
	private static final String KEY_SPOT_DETECTION_MODE = "spotDetectionMode";

	private static final String LEGACY_INTERVALS_NODE = "multiSPOTS96Intervals";
	private static final String LEGACY_KEY_DEFAULT_NOMINAL_INTERVAL_SEC = "defaultNominalIntervalSec";

	private boolean viewSpots = true;
	private SpotDetectionMode spotDetectionMode = SpotDetectionMode.AUTO;

	public boolean isViewSpots() {
		return viewSpots;
	}

	public void setViewSpots(boolean viewSpots) {
		this.viewSpots = viewSpots;
	}

	public SpotDetectionMode getSpotDetectionMode() {
		return spotDetectionMode;
	}

	public void setSpotDetectionMode(SpotDetectionMode spotDetectionMode) {
		if (spotDetectionMode == null)
			return;
		this.spotDetectionMode = spotDetectionMode;
	}

	private static SpotDetectionMode parseSpotDetectionMode(String value) {
		if (value == null)
			return SpotDetectionMode.AUTO;
		try {
			return SpotDetectionMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return SpotDetectionMode.AUTO;
		}
	}

	/**
	 * One-time migration from the legacy {@code multiSPOTS96Intervals} node to
	 * the shared {@code viewOptions} node. Must be called before
	 * {@link #load(XMLPreferences)} on plugin startup. No-op if either node is
	 * null, if the target already has the key, or if the legacy node has no
	 * value to migrate.
	 */
	public void migrateLegacyPreferencesIfNeeded(XMLPreferences target, XMLPreferences legacy) {
		if (target == null || legacy == null)
			return;
		if (target.get(KEY_DEFAULT_NOMINAL_INTERVAL_SEC, null) != null)
			return;
		String legacyValue = legacy.get(LEGACY_KEY_DEFAULT_NOMINAL_INTERVAL_SEC, null);
		if (legacyValue == null)
			return;
		target.put(KEY_DEFAULT_NOMINAL_INTERVAL_SEC, legacyValue);
		legacy.remove(LEGACY_KEY_DEFAULT_NOMINAL_INTERVAL_SEC);
		Logger.info("Migrated defaultNominalIntervalSec from " + LEGACY_INTERVALS_NODE
				+ " to viewOptions node (" + legacyValue + ")");
	}

	@Override
	protected void loadPluginFields(XMLPreferences prefs) {
		viewSpots = readBool(prefs, KEY_VIEW_SPOTS, true);
		spotDetectionMode = parseSpotDetectionMode(prefs.get(KEY_SPOT_DETECTION_MODE, SpotDetectionMode.AUTO.name()));
	}

	@Override
	protected void savePluginFields(XMLPreferences prefs) {
		prefs.put(KEY_VIEW_SPOTS, String.valueOf(viewSpots));
		prefs.put(KEY_SPOT_DETECTION_MODE, spotDetectionMode.name());
	}
}
