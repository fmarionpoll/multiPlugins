package plugins.fmp.multitools.tools.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import plugins.fmp.multitools.service.LevelDetector;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;
import icy.sequence.SequenceListener;
import icy.type.collection.array.Array1DUtil;
import java.awt.geom.Point2D;
import java.util.List;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

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
    
    /** Jitter mode parameters */
    private boolean useJitterMode = false;
    private int jitterValue = 0;
    private int[] initialLevels = null;
    private boolean jitterDirectionUp = true;
    
    /** Detection ROI for visualization */
    private ROI2DPolyLine detectionROI = null;
    
    /** LevelDetector instance for calling detection methods */
    private final LevelDetector levelDetector = new LevelDetector();

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
        useJitterMode = false;
        removeDetectionROI();
    }
    
    /**
     * Sets threshold with jitter-based detection simulation.
     * This method simulates the actual detection logic used in LevelDetector,
     * showing what the detection result would be.
     * 
     * @param threshold the threshold value
     * @param transformOp the transformation operation to apply
     * @param directionUp true if values greater than threshold should be selected
     * @param jitter the vertical jitter (search range) in pixels
     * @param initialLevels array of initial level positions (one per column), or null
     * @throws IllegalArgumentException if transformOp is null
     */
    public void setThresholdSingleWithJitter(int threshold, ImageTransformEnums transformOp, boolean directionUp, 
            int jitter, int[] initialLevels) {
        if (transformOp == null) {
            throw new IllegalArgumentException("Transform operation cannot be null");
        }
        
        imageTransformOptions.setSingleThreshold(threshold, directionUp);
        imageTransformOptions.transformOption = transformOp;
        imageTransformFunction = transformOp.getFunction();
        imageThresholdFunction = ImageTransformEnums.THRESHOLD_SINGLE.getFunction();
        
        useJitterMode = (jitter > 0 && initialLevels != null);
        this.jitterValue = jitter;
        this.initialLevels = initialLevels;
        this.jitterDirectionUp = directionUp;
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
            Logger.warn("Cannot get transformed image: sequence is not set");
            return null;
        }
        
        try {
            IcyBufferedImage image = localSequence.getImage(timePoint, 0);
            if (image == null) {
                Logger.warn("No image found at time point: " + timePoint);
                return null;
            }
            
            return getTransformedImage(image);
        } catch (Exception e) {
            Logger.warn("Error getting transformed image for time point " + timePoint, e);
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
                Logger.warn("Transform function returned null image");
                return null;
            }
            
            return imageThresholdFunction.getTransformedImage(transformedImage, imageTransformOptions);
        } catch (Exception e) {
            Logger.warn("Error applying image transformation", e);
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
            
            if (useJitterMode && initialLevels != null) {
                IcyBufferedImage rawImage = sequence.getImage(timePosition, 0);
                if (rawImage != null) {
                    IcyBufferedImage transformedImage = imageTransformFunction.getTransformedImage(rawImage, imageTransformOptions);
                    if (transformedImage != null) {
                        drawDetectionPath(graphics, rawImage, transformedImage);
                    }
                }
            } else {
                IcyBufferedImage thresholdedImage = getTransformedImage(timePosition);
                if (thresholdedImage != null) {
                    renderOverlay(graphics, thresholdedImage);
                }
            }
        } catch (Exception e) {
            Logger.warn("Error painting overlay", e);
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
            Logger.warn("Overlay rendering was interrupted", e);
        } catch (Exception e) {
            Logger.warn("Error rendering overlay", e);
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
        removeDetectionROI();
        remove();
    }
    
    /**
     * Draws the detection path using the same logic as LevelDetector.
     * 
     * @param graphics the graphics context
     * @param rawImage the raw input image
     * @param transformedImage the transformed image
     */
    private void drawDetectionPath(Graphics2D graphics, IcyBufferedImage rawImage, IcyBufferedImage transformedImage) {
        if (initialLevels == null || initialLevels.length == 0) {
            return;
        }
        
        try {
            Object transformedArray = transformedImage.getDataXY(0);
            int[] transformed1DArray = Array1DUtil.arrayToIntArray(transformedArray, transformedImage.isSignedDataType());
            int imageWidth = transformedImage.getSizeX();
            int imageHeight = transformedImage.getSizeY();
            int threshold = imageTransformOptions.simplethreshold;
            
            int pathLength = Math.min(initialLevels.length, imageWidth);
            int[] detectedPath = new int[pathLength];
            System.arraycopy(initialLevels, 0, detectedPath, 0, pathLength);
            
            ImageTransformEnums transformOp = imageTransformOptions.transformOption;
            boolean useFindBestPosition = shouldUseFindBestPosition(transformOp);
            
            int firstColumn = 0;
            int lastColumn = pathLength - 1;
            
            if (useFindBestPosition) {
                levelDetector.findBestPosition(detectedPath, firstColumn, lastColumn, transformed1DArray, 
                        imageWidth, imageHeight, jitterValue, threshold, jitterDirectionUp);
            } else {
                levelDetector.detectThresholdUp(detectedPath, firstColumn, lastColumn, transformed1DArray, 
                        imageWidth, imageHeight, jitterValue, threshold, jitterDirectionUp);
            }
            
            createDetectionROI(detectedPath);
            
            graphics.setColor(new Color(0, 255, 0, (int)(opacity * 255)));
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            
            for (int ix = 0; ix < detectedPath.length - 1; ix++) {
                int y1 = detectedPath[ix];
                int y2 = detectedPath[ix + 1];
                if (y1 >= 0 && y1 < imageHeight && y2 >= 0 && y2 < imageHeight && ix < imageWidth) {
                    graphics.drawLine(ix, y1, ix + 1, y2);
                }
            }
            
        } catch (Exception e) {
            Logger.warn("Error drawing detection path", e);
        }
    }
    
    /**
     * Determines if the transform should use findBestPosition or detectThresholdUp.
     * 
     * @param transformOp the transform operation
     * @return true if findBestPosition should be used, false for detectThresholdUp
     */
    private boolean shouldUseFindBestPosition(ImageTransformEnums transformOp) {
        switch (transformOp) {
        case DERICHE:
        case DERICHE_COLOR:
        case COLORDISTANCE_L1_Y:
        case COLORDISTANCE_L2_Y:
        case YDIFFN:
        case YDIFFN2:
        case MINUSHORIZAVG:
            return true;
        case SUBTRACT_1RSTCOL:
        case L1DIST_TO_1RSTCOL:
            return false;
        default:
            return true;
        }
    }
    
    /**
     * Creates or updates the detection ROI from the detected path.
     * 
     * @param detectedPath array of Y coordinates for each column
     */
    private void createDetectionROI(int[] detectedPath) {
        if (localSequence == null || detectedPath == null || detectedPath.length == 0) {
            return;
        }
        
        try {
            removeDetectionROI();
            
            List<Point2D> points = new ArrayList<Point2D>();
            for (int ix = 0; ix < detectedPath.length; ix++) {
                points.add(new Point2D.Double(ix, detectedPath[ix]));
            }
            
            if (points.size() < 2) {
                return;
            }
            
            detectionROI = new ROI2DPolyLine(points);
            detectionROI.setName("detection_path_pass2");
            detectionROI.setColor(Color.RED);
            detectionROI.setReadOnly(false);
            
            localSequence.addROI(detectionROI);
            
        } catch (Exception e) {
            Logger.warn("Error creating detection ROI", e);
        }
    }
    
    /**
     * Removes the detection ROI from the sequence.
     */
    private void removeDetectionROI() {
        if (detectionROI != null && localSequence != null) {
            try {
                localSequence.removeROI(detectionROI);
            } catch (Exception e) {
                Logger.warn("Error removing detection ROI", e);
            }
            detectionROI = null;
        }
    }
}
