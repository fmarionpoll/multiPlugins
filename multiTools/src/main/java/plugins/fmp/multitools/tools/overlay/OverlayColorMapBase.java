package plugins.fmp.multiSPOTS96.tools.overlay;

import java.awt.Color;
import icy.image.colormap.IcyColorMap;
import icy.util.ColorUtil;

/**
 * Base class for overlay color maps that provides common functionality
 * for creating color maps with transparent backgrounds and colored masks.
 * 
 * @author MultiSPOTS96
 */
public abstract class OverlayColorMapBase extends IcyColorMap {
    
    /** Alpha value for fully transparent pixels */
    protected static final int ALPHA_TRANSPARENT = 0x00;
    
    /** Alpha value for fully opaque pixels */
    protected static final int ALPHA_OPAQUE = 0xFF;
    
    /** Default map name prefix */
    protected static final String DEFAULT_MAP_NAME = "overlay";
    
    /**
     * Creates a color map with the specified name, mask color, and background color.
     * 
     * @param mapName the name of the color map
     * @param colorMask the color to use for the mask
     * @param colorBackground the background color (should include alpha information)
     * @throws IllegalArgumentException if mapName or colorMask is null
     */
    public OverlayColorMapBase(String mapName, Color colorMask, Color colorBackground) {
        this(mapName, colorMask, colorBackground, IcyColorMapType.RGB);
    }
    
    /**
     * Creates a color map with the specified name, mask color, background color, and type.
     * 
     * @param mapName the name of the color map
     * @param colorMask the color to use for the mask
     * @param colorBackground the background color (should include alpha information)
     * @param type the color map type
     * @throws IllegalArgumentException if mapName, colorMask, or colorBackground is null
     */
    public OverlayColorMapBase(String mapName, Color colorMask, Color colorBackground, IcyColorMapType type) {
        super(validateMapName(mapName), validateType(type));
        
        if (colorMask == null) {
            throw new IllegalArgumentException("Color mask cannot be null");
        }
        if (colorBackground == null) {
            throw new IllegalArgumentException("Background color cannot be null");
        }
        
        initializeColorMap(colorMask, colorBackground);
    }
    
    /**
     * Initializes the color map with the specified mask and background colors.
     * 
     * @param colorMask the color to use for the mask
     * @param colorBackground the background color
     */
    private void initializeColorMap(Color colorMask, Color colorBackground) {
        beginUpdate();
        try {
            setupColorChannels(colorMask, colorBackground);
        } finally {
            endUpdate();
        }
    }
    
    /**
     * Sets up the color channels for the color map.
     * 
     * @param colorMask the color to use for the mask
     * @param colorBackground the background color
     */
    private void setupColorChannels(Color colorMask, Color colorBackground) {
        final int maskPosition = MAX_INDEX;
        final int flatStart = 0;
        final int flatEnd = MAX_INDEX - 1;
        
        // Set background color for the flat region
        setColorChannelValues(flatStart, colorBackground, ALPHA_TRANSPARENT);
        setColorChannelValues(flatEnd, colorBackground, ALPHA_TRANSPARENT);
        
        // Set mask color at the maximum position
        setMaskColorValues(maskPosition, colorMask, colorBackground);
    }
    
    /**
     * Sets the color channel values for a specific position.
     * 
     * @param position the position in the color map
     * @param color the color to set
     * @param alphaValue the alpha value to set
     */
    private void setColorChannelValues(int position, Color color, int alphaValue) {
        red.setControlPoint(position, color.getRed());
        green.setControlPoint(position, color.getGreen());
        blue.setControlPoint(position, color.getBlue());
        gray.setControlPoint(position, ColorUtil.getGrayMix(color));
        alpha.setControlPoint(position, alphaValue);
    }
    
    /**
     * Sets the mask color values at the specified position.
     * 
     * @param position the position in the color map
     * @param colorMask the mask color
     * @param colorBackground the background color (used for gray calculation)
     */
    private void setMaskColorValues(int position, Color colorMask, Color colorBackground) {
        red.setValue(position, colorMask.getRed());
        green.setValue(position, colorMask.getGreen());
        blue.setValue(position, colorMask.getBlue());
        gray.setValue(position, ColorUtil.getGrayMix(colorBackground));
        alpha.setValue(position, ALPHA_OPAQUE);
    }
    
    /**
     * Validates the map name parameter.
     * 
     * @param mapName the map name to validate
     * @return the validated map name
     * @throws IllegalArgumentException if mapName is null or empty
     */
    private static String validateMapName(String mapName) {
        if (mapName == null || mapName.trim().isEmpty()) {
            throw new IllegalArgumentException("Map name cannot be null or empty");
        }
        return mapName;
    }
    
    /**
     * Validates the color map type parameter.
     * 
     * @param type the type to validate
     * @return the validated type
     * @throws IllegalArgumentException if type is null
     */
    private static IcyColorMapType validateType(IcyColorMapType type) {
        if (type == null) {
            throw new IllegalArgumentException("Color map type cannot be null");
        }
        return type;
    }
} 