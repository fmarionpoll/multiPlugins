package plugins.fmp.multiSPOTS96.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

/**
 * Threshold overlay that applies image transformations and thresholding operations
 * to visualize specific regions in a sequence with customizable opacity and color mapping.
 * 
 * <p>This overlay supports multiple threshold types:
 * <ul>
 * <li>Single value thresholding</li>
 * <li>Color-based thresholding</li>
 * <li>Combined image transformations with thresholding</li>
 * </ul>
 * 
 * <p>The overlay automatically updates when the sequence changes and provides
 * real-time visualization of threshold results.</p>
 * 
 * @author MultiSPOTS96
 */
public class OverlayThreshold extends Overlay implements SequenceListener {
    
    /** Logger for this class */
    private static final Logger LOGGER = Logger.getLogger(OverlayThreshold.class.getName());
    
    /** Default overlay name */
    private static final String DEFAULT_OVERLAY_NAME = "ThresholdOverlay";
    
    /** Default opacity for the overlay */
    private static final float DEFAULT_OPACITY = 0.3f;
    
    /** Default color for the overlay mask */
    private static final Color DEFAULT_MASK_COLOR = new Color(0x00FF0000, true);
    
    /** Default color map name */
    private static final String DEFAULT_COLOR_MAP_NAME = "overlaymask";
    
    /** Current opacity of the overlay */
    private float opacity = DEFAULT_OPACITY;
    
    /** Color map used for rendering the overlay */
    private final OverlayColorMask colorMap;
    
    /** Options for image transformations */
    private final ImageTransformOptions imageTransformOptions;
    
    /** Function for image transformation */
    private ImageTransformInterface imageTransformFunction;
    
    /** Function for thresholding */
    private ImageTransformInterface imageThresholdFunction;
    
    /** Reference to the sequence being processed */
    private Sequence localSequence;

    /**
     * Creates a new threshold overlay with default settings.
     */
    public OverlayThreshold() {
        this(null);
    }

    /**
     * Creates a new threshold overlay for the specified sequence.
     * 
     * @param sequence the sequence to attach the overlay to (can be null)
     */
    public OverlayThreshold(Sequence sequence) {
        super(DEFAULT_OVERLAY_NAME);
        
        this.colorMap = new OverlayColorMask(DEFAULT_COLOR_MAP_NAME, DEFAULT_MASK_COLOR);
        this.imageTransformOptions = new ImageTransformOptions();
        this.imageTransformFunction = ImageTransformEnums.NONE.getFunction();
        this.imageThresholdFunction = ImageTransformEnums.NONE.getFunction();
        
        if (sequence != null) {
            setSequence(sequence);
        }
    }

    /**
     * Sets the sequence for this overlay and registers as a listener.
     * 
     * @param sequence the sequence to attach to
     * @throws IllegalArgumentException if sequence is null
     */
    public void setSequence(Sequence sequence) {
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }
        
        // Remove listener from previous sequence if exists
        if (localSequence != null) {
            localSequence.removeListener(this);
        }
        
        this.localSequence = sequence;
        sequence.addListener(this);
    }

    /**
     * Gets the current sequence.
     * 
     * @return the current sequence or null if not set
     */
    public Sequence getSequence() {
        return localSequence;
    }

    /**
     * Sets the opacity of the overlay.
     * 
     * @param opacity the opacity value (0.0 to 1.0)
     * @throws IllegalArgumentException if opacity is not in the valid range
     */
    public void setOpacity(float opacity) {
        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException("Opacity must be between 0.0 and 1.0, got: " + opacity);
        }
        this.opacity = opacity;
    }

    /**
     * Gets the current opacity.
     * 
     * @return the current opacity value
     */
    public float getOpacity() {
        return opacity;
    }

    /**
     * Sets a single threshold value with the specified transformation operation.
     * 
     * @param threshold the threshold value
     * @param transformOp the transformation operation to apply
     * @param ifGreater true if values greater than threshold should be selected
     * @throws IllegalArgumentException if transformOp is null
     */
    public void setThresholdSingle(int threshold, ImageTransformEnums transformOp, boolean ifGreater) {
        setThresholdTransform(threshold, transformOp, ifGreater);
    }

    /**
     * Sets the threshold parameters with image transformation.
     * 
     * @param threshold the threshold value
     * @param transformOp the transformation operation to apply
     * @param ifGreater true if values greater than threshold should be selected
     * @throws IllegalArgumentException if transformOp is null
     */
    public void setThresholdTransform(int threshold, ImageTransformEnums transformOp, boolean ifGreater) {
        if (transformOp == null) {
            throw new IllegalArgumentException("Transform operation cannot be null");
        }
        
        imageTransformOptions.setSingleThreshold(threshold, ifGreater);
        imageTransformOptions.transformOption = transformOp;
        imageTransformFunction = transformOp.getFunction();
        imageThresholdFunction = ImageTransformEnums.THRESHOLD_SINGLE.getFunction();
    }

    /**
     * Sets the reference image for background subtraction operations.
     * 
     * @param referenceImage the reference image to use
     * @throws IllegalArgumentException if referenceImage is null
     */
    public void setReferenceImage(IcyBufferedImage referenceImage) {
        if (referenceImage == null) {
            throw new IllegalArgumentException("Reference image cannot be null");
        }
        imageTransformOptions.backgroundImage = referenceImage;
    }

    /**
     * Sets color-based threshold parameters.
     * 
     * @param colorArray array of colors to threshold against
     * @param distanceType the distance metric to use
     * @param threshold the threshold value for color distance
     * @throws IllegalArgumentException if colorArray is null or empty
     */
    public void setThresholdColor(ArrayList<Color> colorArray, int distanceType, int threshold) {
        if (colorArray == null || colorArray.isEmpty()) {
            throw new IllegalArgumentException("Color array cannot be null or empty");
        }
        
        imageTransformOptions.setColorArrayThreshold(distanceType, threshold, colorArray);
        imageTransformFunction = ImageTransformEnums.NONE.getFunction();
        imageThresholdFunction = ImageTransformEnums.THRESHOLD_COLORS.getFunction();
    }

    /**
     * Gets the transformed image for the specified time point.
     * 
     * @param timePoint the time point to process
     * @return the transformed image or null if sequence is not set or processing fails
     */
    public IcyBufferedImage getTransformedImage(int timePoint) {
        if (localSequence == null) {
            LOGGER.warning("Cannot get transformed image: sequence is not set");
            return null;
        }
        
        try {
            IcyBufferedImage image = localSequence.getImage(timePoint, 0);
            if (image == null) {
                LOGGER.warning("No image found at time point: " + timePoint);
                return null;
            }
            
            return getTransformedImage(image);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting transformed image for time point " + timePoint, e);
            return null;
        }
    }

    /**
     * Gets the transformed image for the specified input image.
     * 
     * @param inputImage the input image to transform
     * @return the transformed image or null if processing fails
     * @throws IllegalArgumentException if inputImage is null
     */
    public IcyBufferedImage getTransformedImage(IcyBufferedImage inputImage) {
        if (inputImage == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }
        
        try {
            IcyBufferedImage transformedImage = imageTransformFunction.getTransformedImage(inputImage, imageTransformOptions);
            if (transformedImage == null) {
                LOGGER.warning("Transform function returned null image");
                return null;
            }
            
            return imageThresholdFunction.getTransformedImage(transformedImage, imageTransformOptions);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error applying image transformation", e);
            return null;
        }
    }

    @Override
    public void paint(Graphics2D graphics, Sequence sequence, IcyCanvas canvas) {
        if (graphics == null || sequence == null || canvas == null) {
            return;
        }
        
        if (!(canvas instanceof IcyCanvas2D)) {
            return;
        }
        
        try {
            int timePosition = canvas.getPositionT();
            IcyBufferedImage thresholdedImage = getTransformedImage(timePosition);
            
            if (thresholdedImage != null) {
                renderOverlay(graphics, thresholdedImage);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error painting overlay", e);
        }
    }

    /**
     * Renders the overlay image with the current color map and opacity.
     * 
     * @param graphics the graphics context
     * @param thresholdedImage the thresholded image to render
     */
    private void renderOverlay(Graphics2D graphics, IcyBufferedImage thresholdedImage) {
        try {
            thresholdedImage.setColorMap(0, colorMap);
            BufferedImage bufferedImage = IcyBufferedImageUtil.getARGBImage(thresholdedImage);
            
            Composite originalComposite = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            graphics.drawImage(bufferedImage, 0, 0, null);
            graphics.setComposite(originalComposite);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Overlay rendering was interrupted", e);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error rendering overlay", e);
        }
    }

    @Override
    public void sequenceChanged(SequenceEvent sequenceEvent) {
        if (sequenceEvent == null) {
            return;
        }
        
        if (sequenceEvent.getSourceType() != SequenceEventSourceType.SEQUENCE_OVERLAY) {
            return;
        }
        
        if (sequenceEvent.getSource() == this && sequenceEvent.getType() == SequenceEventType.REMOVED) {
            cleanupSequenceListener(sequenceEvent.getSequence());
        }
    }

    @Override
    public void sequenceClosed(Sequence sequence) {
        if (sequence != null) {
            cleanupSequenceListener(sequence);
        }
    }

    /**
     * Cleans up the sequence listener and removes the overlay.
     * 
     * @param sequence the sequence to clean up
     */
    private void cleanupSequenceListener(Sequence sequence) {
        if (sequence != null) {
            sequence.removeListener(this);
        }
        remove();
    }
}
