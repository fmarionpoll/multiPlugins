package plugins.fmp.multiSPOTS96.tools.imageTransform;

/**
 * Constants used throughout the ImageTransform subsystem.
 * Centralizes configuration values and magic numbers to improve maintainability.
 * 
 * @author MultiSPOTS96 Team
 * @version 1.0
 */
public final class ImageTransformConstants {
    
    // Color space conversion constants
    public static final class ColorSpace {
        /** Standard scaling factor for HSV/HSB values (converts [0,1] to [0,100]) */
        public static final double HSV_SCALING_FACTOR = 100.0;
        
        /** Maximum RGB value (8-bit) */
        public static final int RGB_MAX_VALUE = 255;
        
        /** Undefined hue value for grayscale pixels */
        public static final int UNDEFINED_HUE = -1;
        
        /** Number of RGB channels */
        public static final int RGB_CHANNELS = 3;
        
        /** Degrees in a full circle for hue calculations */
        public static final double HUE_FULL_CIRCLE = 360.0;
        
        /** Hue sector size for RGB to HSV conversion */
        public static final double HUE_SECTOR_SIZE = 60.0;
        
        private ColorSpace() {
            // Prevent instantiation
        }
    }
    
    // Edge detection algorithm constants
    public static final class EdgeDetection {
        /** Default alpha parameter for Deriche edge detection */
        public static final double DEFAULT_DERICHE_ALPHA = 1.0;
        
        /** Minimum alpha value for stability */
        public static final double MIN_DERICHE_ALPHA = 0.1;
        
        /** Maximum alpha value for reasonable edge detection */
        public static final double MAX_DERICHE_ALPHA = 5.0;
        
        /** Default span size for difference operations */
        public static final int DEFAULT_SPAN_SIZE = 3;
        
        /** Default delta for difference operations */
        public static final int DEFAULT_DELTA = 0;
        
        private EdgeDetection() {
            // Prevent instantiation
        }
    }
    
    // Thresholding operation constants
    public static final class Thresholding {
        /** Binary true value (white) */
        public static final byte TRUE_VALUE = (byte) 0xFF;
        
        /** Binary false value (black) */
        public static final byte FALSE_VALUE = 0;
        
        /** Default threshold value */
        public static final int DEFAULT_THRESHOLD = 128;
        
        /** Minimum threshold value */
        public static final int MIN_THRESHOLD = 0;
        
        /** Maximum threshold value */
        public static final int MAX_THRESHOLD = 255;
        
        private Thresholding() {
            // Prevent instantiation
        }
    }
    
    // Linear combination operation constants
    public static final class LinearCombination {
        /** Standard RGB weights for grayscale conversion */
        public static final double[] GRAYSCALE_WEIGHTS = {0.299, 0.587, 0.114};
        
        /** Equal weights for simple averaging */
        public static final double[] EQUAL_WEIGHTS = {1.0/3.0, 1.0/3.0, 1.0/3.0};
        
        /** Red channel extraction weights */
        public static final double[] RED_WEIGHTS = {1.0, 0.0, 0.0};
        
        /** Green channel extraction weights */
        public static final double[] GREEN_WEIGHTS = {0.0, 1.0, 0.0};
        
        /** Blue channel extraction weights */
        public static final double[] BLUE_WEIGHTS = {0.0, 0.0, 1.0};
        
        private LinearCombination() {
            // Prevent instantiation
        }
    }
    
    // Difference operation constants
    public static final class DifferenceOperations {
        /** Default span for Y-direction differences */
        public static final int DEFAULT_Y_SPAN = 4;
        
        /** Default span for X-direction differences */
        public static final int DEFAULT_X_SPAN = 3;
        
        /** Default span for XY-direction differences */
        public static final int DEFAULT_XY_SPAN = 5;
        
        /** Maximum reasonable span to prevent excessive computation */
        public static final int MAX_SPAN = 20;
        
        private DifferenceOperations() {
            // Prevent instantiation
        }
    }
    
    // Performance and memory management constants
    public static final class Performance {
        /** Cache size for array operations */
        public static final int ARRAY_CACHE_SIZE = 100;
        
        /** Threshold for using optimized operations */
        public static final int OPTIMIZATION_THRESHOLD = 1000;
        
        /** Maximum image size for caching (in pixels) */
        public static final int MAX_CACHEABLE_IMAGE_SIZE = 1920 * 1080;
        
        private Performance() {
            // Prevent instantiation
        }
    }
    
    // Validation constants
    public static final class Validation {
        /** Minimum image width */
        public static final int MIN_IMAGE_WIDTH = 1;
        
        /** Minimum image height */
        public static final int MIN_IMAGE_HEIGHT = 1;
        
        /** Maximum reasonable image dimension */
        public static final int MAX_IMAGE_DIMENSION = 16384;
        
        /** Maximum number of channels supported */
        public static final int MAX_CHANNELS = 4;
        
        private Validation() {
            // Prevent instantiation
        }
    }
    
    // Error messages for consistent error reporting
    public static final class ErrorMessages {
        public static final String NULL_SOURCE_IMAGE = "Source image cannot be null";
        public static final String NULL_OPTIONS = "Transform options cannot be null";
        public static final String INVALID_IMAGE_DIMENSIONS = "Invalid image dimensions: width=%d, height=%d";
        public static final String UNSUPPORTED_CHANNELS = "Unsupported number of channels: %d";
        public static final String INVALID_PARAMETER = "Invalid parameter %s: %s";
        public static final String ALGORITHM_FAILURE = "Algorithm execution failed: %s";
        public static final String INCOMPATIBLE_IMAGES = "Images are not compatible: %s";
        public static final String OUT_OF_BOUNDS = "Array access out of bounds: index=%d, length=%d";
        
        private ErrorMessages() {
            // Prevent instantiation
        }
    }
    
    private ImageTransformConstants() {
        // Prevent instantiation
    }
} 