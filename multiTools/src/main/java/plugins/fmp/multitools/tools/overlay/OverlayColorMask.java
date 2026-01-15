package plugins.fmp.multiSPOTS96.tools.overlay;

import java.awt.Color;

/**
 * Color mask overlay that creates a black background with colored mask areas.
 * This implementation uses a fully opaque black background (alpha =
 * 0xFF000000).
 * 
 * <p>
 * The color map is designed for mask visualization where the background is
 * visible as black and only specific areas (mask positions) show the mask
 * color.
 * </p>
 * 
 * @author MultiSPOTS96
 */
public class OverlayColorMask extends OverlayColorMapBase {

	/** Default opaque black background color */
	private static final Color DEFAULT_BACKGROUND = new Color(0xFF000000, true);

	/**
	 * Creates a color mask using the default RGB type with black background.
	 * 
	 * @param mapName   the name of the color map
	 * @param colorMask the color to use for the mask areas
	 * @throws IllegalArgumentException if mapName or colorMask is null
	 */
	public OverlayColorMask(String mapName, Color colorMask) {
		this(mapName, colorMask, IcyColorMapType.RGB);
	}

	/**
	 * Creates a color mask with the specified type and black background.
	 * 
	 * @param mapName   the name of the color map
	 * @param colorMask the color to use for the mask areas
	 * @param type      the color map type
	 * @throws IllegalArgumentException if mapName, colorMask, or type is null
	 */
	public OverlayColorMask(String mapName, Color colorMask, IcyColorMapType type) {
		super(mapName, colorMask, DEFAULT_BACKGROUND, type);
	}
}
