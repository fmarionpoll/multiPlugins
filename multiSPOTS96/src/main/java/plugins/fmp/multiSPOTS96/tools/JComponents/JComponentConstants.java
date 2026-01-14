package plugins.fmp.multiSPOTS96.tools.JComponents;

/**
 * Constants used throughout the JComponents subsystem.
 * Centralizes configuration values to improve maintainability.
 */
public final class JComponentConstants {
    
    // Progress and UI Messages
    public static final String LOAD_EXPERIMENTS_PROGRESS_TITLE = "Load experiment(s) parameters";
    public static final String ANALYZE_EXPERIMENT_MESSAGE_FORMAT = "Analyze experiment: %d//%d";
    public static final String LOAD_EXPERIMENT_MESSAGE_FORMAT = "Load experiment %d of %d";
    
    // Dialog titles and messages
    public static final String FILE_OVERWRITE_CONFIRMATION = "Overwrite existing file ?";
    public static final String COLOR_PICKER_TITLE = "Pick a Color";
    public static final String FILE_LOAD_BUTTON_TEXT = "Load";
    
    // File extension handling
    public static final String DOT_PREFIX = ".";
    public static final String FILES_SUFFIX = " files";
    
    // Time scale constants
    public static final class TimeScales {
        public static final String MILLISECONDS = "ms";
        public static final String SECONDS = "s";
        public static final String MINUTES = "min";
        public static final String HOURS = "h";
        public static final String DAYS = "day";
        
        public static final String[] ALL_SCALES = {
            MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS
        };
        
        // Conversion factors to milliseconds
        public static final int MS_TO_MS = 1;
        public static final int SECONDS_TO_MS = 1000;
        public static final int MINUTES_TO_MS = 1000 * 60;
        public static final int HOURS_TO_MS = 1000 * 60 * 60;
        public static final int DAYS_TO_MS = 1000 * 60 * 60 * 24;
        
        private TimeScales() {
            // Prevent instantiation
        }
    }
    
    // Color rendering constants
    public static final class ColorRendering {
        public static final String COLOR_FORMAT_SEPARATOR = ":";
        public static final String RGB_TOOLTIP_FORMAT = "RGB value: %d, %d, %d";
        public static final double LUMINANCE_THRESHOLD = 0.5;
        
        // Luminance calculation coefficients (ITU-R BT.709)
        public static final double LUMINANCE_RED_COEFFICIENT = 0.299;
        public static final double LUMINANCE_GREEN_COEFFICIENT = 0.587;
        public static final double LUMINANCE_BLUE_COEFFICIENT = 0.114;
        
        // Font colors for light/dark backgrounds
        public static final int BRIGHT_BACKGROUND_FONT_COLOR = 0;    // Black font for bright backgrounds
        public static final int DARK_BACKGROUND_FONT_COLOR = 255;    // White font for dark backgrounds
        
        private ColorRendering() {
            // Prevent instantiation
        }
    }
    
    // Table cell constants
    public static final class TableCell {
        public static final String EDIT_COMMAND = "edit";
        public static final String BUTTON_TEXT = "..";
        public static final int BORDER_THICKNESS = 2;
        public static final int BORDER_MARGIN = 5;
        
        private TableCell() {
            // Prevent instantiation
        }
    }
    
    // List rendering constants
    public static final class ListRendering {
        public static final String INDEX_FORMAT = "[%d:%d] ";
        public static final String TRUNCATION_INDICATOR = "...";
        public static final int MAX_DISPLAY_LENGTH = 70;
        public static final int TRUNCATION_BUFFER = 3; // Length of "..."
        
        private ListRendering() {
            // Prevent instantiation
        }
    }
    
    // Threading constants
    public static final class Threading {
        public static final String LOAD_ALL_EXPERIMENTS_THREAD_NAME = "loadAllExperiments";
        public static final int NORMAL_PRIORITY = Thread.NORM_PRIORITY;
        
        private Threading() {
            // Prevent instantiation
        }
    }
    
    // Experiment processing constants
    public static final class ExperimentProcessing {
        public static final long MIN_TIME_DIFFERENCE_MS = 1;
        public static final String TIME_DIFF_WARNING_FORMAT = 
            "ExperimentCombo:get_MsTime_of_StartAndEnd_AllExperiments() Expt # %d: " +
            "FileTime difference between last and first image < 1; set dt between images = 1 ms";
        
        private ExperimentProcessing() {
            // Prevent instantiation
        }
    }
    
    // Error messages
    public static final class ErrorMessages {
        public static final String WAIT_FUTURES_WARNING_FORMAT = 
            "ExperimentCombo:waitFuturesCompletion() - Warning: %s";
        public static final String INVALID_FILE_EXTENSION = "Invalid file extension: %s";
        public static final String FILE_OPERATION_FAILED = "File operation failed: %s";
        public static final String EXPERIMENT_LOADING_FAILED = "Failed to load experiment: %s";
        
        private ErrorMessages() {
            // Prevent instantiation
        }
    }
    
    // Validation constants
    public static final class Validation {
        public static final int MIN_EXPERIMENTS_FOR_CHAINING = 2;
        public static final int INVALID_INDEX = -1;
        
        private Validation() {
            // Prevent instantiation
        }
    }
    
    private JComponentConstants() {
        // Prevent instantiation
    }
} 