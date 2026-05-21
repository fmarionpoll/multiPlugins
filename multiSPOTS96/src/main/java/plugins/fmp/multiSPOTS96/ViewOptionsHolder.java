package plugins.fmp.multiSPOTS96;

import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.ViewOptionsHolderBase;
import plugins.fmp.multitools.series.options.SpotDetectionMode;
import plugins.fmp.multitools.tools.Logger;

public class ViewOptionsHolder extends ViewOptionsHolderBase {

	private static final String KEY_VIEW_SPOTS = "viewSpots";
	private static final String KEY_SPOT_DETECTION_MODE = "spotDetectionMode";

	private static final String KEY_CREATE_CAGES_GRID_COLS = "createCagesGridCols";
	private static final String KEY_CREATE_CAGES_GRID_ROWS = "createCagesGridRows";
	private static final String KEY_CREATE_CAGES_PIXEL_SPACING = "createCagesPixelSpacing";
	private static final String KEY_DETECT_BLOBS_THRESHOLD = "detectBlobsThreshold";
	private static final String KEY_DETECT_BLOBS_SPOT_DIAMETER_PX = "detectBlobsSpotDiameterPx";

	private static final String LEGACY_INTERVALS_NODE = "multiSPOTS96Intervals";
	private static final String LEGACY_KEY_DEFAULT_NOMINAL_INTERVAL_SEC = "defaultNominalIntervalSec";

	private boolean viewSpots = true;
	private SpotDetectionMode spotDetectionMode = SpotDetectionMode.AUTO;

	private int createCagesGridCols = 6;
	private int createCagesGridRows = 8;
	private int createCagesPixelSpacing = 4;
	private int detectBlobsThreshold = 35;
	private int detectBlobsSpotDiameterPx = 22;

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

	public int getCreateCagesGridCols() {
		return createCagesGridCols;
	}

	public void setCreateCagesGridCols(int createCagesGridCols) {
		this.createCagesGridCols = clamp(createCagesGridCols, 0, 10000);
	}

	public int getCreateCagesGridRows() {
		return createCagesGridRows;
	}

	public void setCreateCagesGridRows(int createCagesGridRows) {
		this.createCagesGridRows = clamp(createCagesGridRows, 0, 10000);
	}

	public int getCreateCagesPixelSpacing() {
		return createCagesPixelSpacing;
	}

	public void setCreateCagesPixelSpacing(int createCagesPixelSpacing) {
		this.createCagesPixelSpacing = clamp(createCagesPixelSpacing, 0, 10000);
	}

	public int getDetectBlobsThreshold() {
		return detectBlobsThreshold;
	}

	public void setDetectBlobsThreshold(int detectBlobsThreshold) {
		this.detectBlobsThreshold = clamp(detectBlobsThreshold, 0, 255);
	}

	public int getDetectBlobsSpotDiameterPx() {
		return detectBlobsSpotDiameterPx;
	}

	public void setDetectBlobsSpotDiameterPx(int detectBlobsSpotDiameterPx) {
		this.detectBlobsSpotDiameterPx = clamp(detectBlobsSpotDiameterPx, 1, 1200);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
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
		createCagesGridCols = clamp(readInt(prefs, KEY_CREATE_CAGES_GRID_COLS, 6), 0, 10000);
		createCagesGridRows = clamp(readInt(prefs, KEY_CREATE_CAGES_GRID_ROWS, 8), 0, 10000);
		createCagesPixelSpacing = clamp(readInt(prefs, KEY_CREATE_CAGES_PIXEL_SPACING, 4), 0, 10000);
		detectBlobsThreshold = clamp(readInt(prefs, KEY_DETECT_BLOBS_THRESHOLD, 35), 0, 255);
		detectBlobsSpotDiameterPx = clamp(readInt(prefs, KEY_DETECT_BLOBS_SPOT_DIAMETER_PX, 22), 1, 1200);
	}

	@Override
	protected void savePluginFields(XMLPreferences prefs) {
		prefs.put(KEY_VIEW_SPOTS, String.valueOf(viewSpots));
		prefs.put(KEY_SPOT_DETECTION_MODE, spotDetectionMode.name());
		prefs.put(KEY_CREATE_CAGES_GRID_COLS, String.valueOf(createCagesGridCols));
		prefs.put(KEY_CREATE_CAGES_GRID_ROWS, String.valueOf(createCagesGridRows));
		prefs.put(KEY_CREATE_CAGES_PIXEL_SPACING, String.valueOf(createCagesPixelSpacing));
		prefs.put(KEY_DETECT_BLOBS_THRESHOLD, String.valueOf(detectBlobsThreshold));
		prefs.put(KEY_DETECT_BLOBS_SPOT_DIAMETER_PX, String.valueOf(detectBlobsSpotDiameterPx));
	}
}
