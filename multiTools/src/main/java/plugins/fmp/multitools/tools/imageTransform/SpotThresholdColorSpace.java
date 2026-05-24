package plugins.fmp.multitools.tools.imageTransform;

/**
 * Color space for {@link ImageTransformEnums#THRESHOLD_COLORS} distance to reference colors.
 */
public enum SpotThresholdColorSpace {
	RGB,
	HSV,
	H1H2H3;

	public static SpotThresholdColorSpace fromOrdinal(int o) {
		SpotThresholdColorSpace[] v = values();
		if (o < 0 || o >= v.length) {
			return RGB;
		}
		return v[o];
	}

	public static SpotThresholdColorSpace fromXmlToken(String s) {
		if (s == null) {
			return RGB;
		}
		String t = s.trim();
		if (t.equalsIgnoreCase("HSV")) {
			return HSV;
		}
		if (t.equalsIgnoreCase("H1H2H3")) {
			return H1H2H3;
		}
		return RGB;
	}

	public String toXmlToken() {
		return name();
	}
}
