package plugins.fmp.multiSPOTS96.tools.overlay;

import java.awt.Color;
import icy.image.colormap.IcyColorMap;

/**
 * Color map overlay that creates a transparent background with colored mask areas.
 * This implementation uses a fully transparent background (alpha = 0x00000000).
 * 
 * <p>The color map is designed for overlay visualization where only specific
 * areas (mask positions) should be visible while the background remains transparent.</p>
 * 
 * @author MultiSPOTS96
 */
public class OverlayColorMap extends OverlayColorMapBase {
    
    /** Default transparent background color */
    private static final Color DEFAULT_BACKGROUND = new Color(0x00000000, true);
    
    /**
     * Creates a color map using the default RGB type with transparent background.
     * 
     * @param mapName the name of the color map
     * @param colorMask the color to use for the mask areas
     * @throws IllegalArgumentException if mapName or colorMask is null
     */
    public OverlayColorMap(String mapName, Color colorMask) {
        this(mapName, colorMask, IcyColorMapType.RGB);
    }

    /**
     * Creates a color map with the specified type and transparent background.
     * 
     * @param mapName the name of the color map
     * @param colorMask the color to use for the mask areas
     * @param type the color map type
     * @throws IllegalArgumentException if mapName, colorMask, or type is null
     */
    public OverlayColorMap(String mapName, Color colorMask, IcyColorMapType type) {
        super(mapName, colorMask, DEFAULT_BACKGROUND, type);
    }
}
