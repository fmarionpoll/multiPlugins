package plugins.fmp.multiSPOTS96.tools.imageTransform;

/**
 * Base exception class for all image transformation errors.
 * Provides structured error handling with context information.
 * 
 * @author MultiSPOTS96 Team
 * @version 1.0
 */
public class ImageTransformException extends Exception {
    
    private static final long serialVersionUID = 1L;
    
    private final String transformName;
    private final String context;
    
    /**
     * Creates a new ImageTransformException.
     * 
     * @param message The error message
     */
    public ImageTransformException(String message) {
        this(message, null, null, null);
    }
    
    /**
     * Creates a new ImageTransformException with cause.
     * 
     * @param message The error message
     * @param cause The underlying cause
     */
    public ImageTransformException(String message, Throwable cause) {
        this(message, cause, null, null);
    }
    
    /**
     * Creates a new ImageTransformException with context.
     * 
     * @param message The error message
     * @param transformName The name of the transform that failed
     * @param context Additional context information
     */
    public ImageTransformException(String message, String transformName, String context) {
        this(message, null, transformName, context);
    }
    
    /**
     * Creates a new ImageTransformException with all details.
     * 
     * @param message The error message
     * @param cause The underlying cause
     * @param transformName The name of the transform that failed
     * @param context Additional context information
     */
    public ImageTransformException(String message, Throwable cause, String transformName, String context) {
        super(buildMessage(message, transformName, context), cause);
        this.transformName = transformName;
        this.context = context;
    }
    
    /**
     * Gets the name of the transform that failed.
     * 
     * @return The transform name, or null if not specified
     */
    public String getTransformName() {
        return transformName;
    }
    
    /**
     * Gets the additional context information.
     * 
     * @return The context, or null if not specified
     */
    public String getContext() {
        return context;
    }
    
    /**
     * Builds a comprehensive error message with context.
     */
    private static String buildMessage(String message, String transformName, String context) {
        StringBuilder sb = new StringBuilder();
        
        if (message != null) {
            sb.append(message);
        }
        
        if (transformName != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("[Transform: ").append(transformName).append("]");
        }
        
        if (context != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("[Context: ").append(context).append("]");
        }
        
        return sb.toString();
    }
}



/**
 * Exception thrown when images are incompatible for the requested operation.
 */
class IncompatibleImageException extends ImageTransformException {
    
    private static final long serialVersionUID = 1L;
    
    private final String imageDescription;
    private final String incompatibilityReason;
    
    public IncompatibleImageException(String imageDescription, String incompatibilityReason) {
        super(String.format("Image incompatibility: %s - %s", imageDescription, incompatibilityReason));
        this.imageDescription = imageDescription;
        this.incompatibilityReason = incompatibilityReason;
    }
    
    public IncompatibleImageException(String imageDescription, String incompatibilityReason, String transformName) {
        super(String.format("Image incompatibility: %s - %s", imageDescription, incompatibilityReason),
              transformName, "Image compatibility check");
        this.imageDescription = imageDescription;
        this.incompatibilityReason = incompatibilityReason;
    }
    
    public String getImageDescription() {
        return imageDescription;
    }
    
    public String getIncompatibilityReason() {
        return incompatibilityReason;
    }
}

 