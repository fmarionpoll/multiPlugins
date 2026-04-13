package plugins.fmp.multitools.experiment.cage;

/**
 * Which side of the cage ROI carries food, for distance-to-food exports.
 */
public enum FoodSide {
	TOP,
	BOTTOM,
	LEFT,
	RIGHT;

	public static FoodSide fromPersistedString(String s) {
		if (s == null || s.isEmpty()) {
			return TOP;
		}
		String t = s.trim();
		for (FoodSide v : values()) {
			if (v.name().equalsIgnoreCase(t)) {
				return v;
			}
		}
		return TOP;
	}
}
