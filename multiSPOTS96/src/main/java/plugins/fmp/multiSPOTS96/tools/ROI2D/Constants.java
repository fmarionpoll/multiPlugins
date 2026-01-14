package plugins.fmp.multiSPOTS96.tools.ROI2D;

/**
 * Constants and configuration values for ROI2D operations.
 * Centralizes magic numbers, error messages, and default values used throughout the ROI2D package.
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public final class Constants {
    
    // Prevent instantiation
    private Constants() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Geometric and mathematical constants for ROI operations.
     */
    public static final class Geometry {
        public static final int MIN_ELLIPSE_POINTS = 4;
        public static final int MIN_POLYGON_POINTS = 3;
        public static final int ELLIPSE_PARAMETERS_COUNT = 12;
        public static final int ELLIPSE_EQUATION_PARAMETERS = 6;
        public static final double ELLIPSE_RADII_SCALE_FACTOR = 2.0;
        public static final double MATH_PRECISION_PLACES = 2.0;
        public static final int REQUIRED_POLYGON_SIDES = 4;
        public static final double FULL_CIRCLE_RADIANS = Math.PI;
        public static final double QUARTER_CIRCLE_RADIANS = Math.PI / 2;
        public static final double HALF_CIRCLE_RADIANS = Math.PI / 2;
    }
    
    /**
     * Default values and limits for grid operations.
     */
    public static final class Grid {
        public static final int MIN_GRID_COLUMNS = 1;
        public static final int MIN_GRID_ROWS = 1;
        public static final int MAX_GRID_COLUMNS = 1000;
        public static final int MAX_GRID_ROWS = 1000;
        public static final int DEFAULT_GRID_SIZE = 10;
        public static final String DEFAULT_CAGE_ROOT_NAME = "cage_";
        public static final String COLUMN_PREFIX = "col_";
        public static final String ROW_PREFIX = "row_";
        public static final String COLUMN_IDENTIFIER = "col";
        public static final String ROW_IDENTIFIER = "row";
        public static final String NAME_INDEX_SEPARATOR = "_";
    }
    
    /**
     * Interpolation and measurement constants.
     */
    public static final class Measurement {
        public static final int MIN_INTERPOLATION_INTERVALS = 1;
        public static final int THREAD_INTERRUPTION_CHECK_MASK = 0xFFFF;
        public static final int MAX_CONTOUR_COMPONENTS = 1000;
        public static final String SPOT_NAME_IDENTIFIER = "spot";
        public static final int MIN_SPOT_THRESHOLD = 0;
        public static final int MAX_SPOT_THRESHOLD = 255;
    }
    
    /**
     * XML persistence constants.
     */
    public static final class XML {
        public static final String ID_ROI_MC = "roiMC";
        public static final String ID_META = "metaT";
        public static final String ID_INDEX = "indexT";
        public static final String ID_START = "startT";
        public static final String CAGE_NAME_FORMAT = "%03d";
    }
    
    /**
     * Error messages for validation and operation failures.
     */
    public static final class ErrorMessages {
        public static final String NULL_ROI = "ROI cannot be null";
        public static final String NULL_SEQUENCE = "Sequence cannot be null";
        public static final String NULL_POLYGON = "Polygon cannot be null";
        public static final String NULL_POINTS = "Points list cannot be null";
        public static final String EMPTY_POINTS = "Points list cannot be empty";
        public static final String INVALID_POLYGON_SIDES = "Polygon must be %d-sided, but has %d sides";
        public static final String INVALID_GRID_DIMENSIONS = "Grid dimensions must be positive (columns: %d, rows: %d)";
        public static final String INSUFFICIENT_ELLIPSE_POINTS = "At least %d points required for ellipse fitting, but only %d provided";
        public static final String ELLIPSE_FIT_FAILED = "Failed to fit ellipse: %s";
        public static final String SINGULAR_MATRIX_DETECTED = "Singular matrix detected during ellipse fitting";
        public static final String INTERPOLATION_FAILED = "Failed to interpolate points: %s";
        public static final String GRID_UPDATE_FAILED = "Failed to update grid: %s";
        public static final String XML_SAVE_FAILED = "Failed to save ROI to XML: %s";
        public static final String XML_LOAD_FAILED = "Failed to load ROI from XML: %s";
        public static final String UNSUPPORTED_ROI_TYPE = "Unsupported ROI type for operation: %s";
        public static final String RESIZE_OPERATION_FAILED = "Failed to resize ROI: %s";
        public static final String RESCALE_OPERATION_FAILED = "Failed to rescale ROI: %s";
        public static final String INVALID_THRESHOLD_RANGE = "Threshold must be between %d and %d, but was %d";
        public static final String INVALID_SCALE_FACTOR = "Scale factor must be positive, but was %f";
        public static final String THREAD_INTERRUPTED = "Operation was interrupted";
        public static final String ARRAY_INDEX_OUT_OF_BOUNDS = "Array index %d is out of bounds for array of length %d";
    }
    
    /**
     * Default formatting and display constants.
     */
    public static final class Display {
        public static final String SCALED_NAME_SUFFIX = " x%.2f";
        public static final String DEFAULT_ROI_NAME = "ROI";
        public static final int DEFAULT_NAME_INDEX_START = 0;
        public static final String CONTOUR_DETECTION_FAILURE_MESSAGE = "Unsuccessful detection of spot limits";
    }
    
    /**
     * Performance and optimization constants.
     */
    public static final class Performance {
        public static final int DEFAULT_ARRAY_INITIAL_CAPACITY = 16;
        public static final int LARGE_COLLECTION_THRESHOLD = 1000;
        public static final int MAX_RETRY_ATTEMPTS = 3;
        public static final long OPERATION_TIMEOUT_MS = 30000; // 30 seconds
    }
} 