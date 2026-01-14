package plugins.fmp.multiSPOTS96.tools.toExcel;

/**
 * Constants used throughout the Excel export subsystem.
 * Centralizes configuration values to improve maintainability.
 */
public final class ExcelExportConstants {
    
    // Progress and UI constants
    public static final String DEFAULT_PROGRESS_TITLE = "Export data to Excel";
    public static final String SAVE_PROGRESS_MESSAGE = "Save Excel file to disk... ";
    public static final String EXPORT_START_MESSAGE = "start output";
    public static final String EXPORT_FINISH_MESSAGE = "XLS output finished";
    
    // Date and time formatting
    public static final String DEFAULT_DATE_FORMAT = "MM/dd/yyyy";
    public static final String TIME_COLUMN_PREFIX = "t";
    
    // Sheet and cell formatting
    public static final String SHEET_SEPARATOR = "--";
    public static final String ALIVE_SHEET_SUFFIX = "_alive";
    public static final String CHOICE_NOCHOICE_DEFAULT = "";
    
    // File path processing
    public static final String CAMERA_IDENTIFIER = "cam";
    public static final int CAMERA_IDENTIFIER_LENGTH = 5;
    public static final String CAMERA_DEFAULT_VALUE = "-";
    
    // Data processing thresholds
    public static final int MINIMUM_PIXELS_CHANGED_THRESHOLD = 10;
    public static final int MINIMUM_STIMULUS_COUNT = 2;
    
    // Default values for missing data
    public static final double DEFAULT_LOWEST_PI_ALLOWED = -1.2;
    public static final double DEFAULT_HIGHEST_PI_ALLOWED = 1.2;
    public static final int DEFAULT_SLEEP_THRESHOLD = 5;
    public static final int DEFAULT_MEDIAN_T0_POINTS = 5;
    
    // Export type specific constants
    public static final double ZERO_REPLACEMENT_VALUE = Double.NaN;
    public static final boolean DEFAULT_REMOVE_ZEROS = false;
    
    // Column header positions (maintaining original order)
    public static final class ColumnPositions {
        public static final int PATH = 0;
        public static final int DATE = 1;
        public static final int EXP_BOXID = 2;
        public static final int CAM = 3;
        public static final int EXP_EXPT = 4;
        public static final int CAGEID = 5;
        public static final int EXP_STIM = 6;
        public static final int EXP_CONC = 7;
        public static final int EXP_STRAIN = 8;
        public static final int EXP_SEX = 9;
        public static final int EXP_COND1 = 10;
        public static final int EXP_COND2 = 11;
        public static final int CAGEPOS = 12;
        public static final int SPOT_VOLUME = 13;
        public static final int SPOT_PIXELS = 14;
        public static final int CHOICE_NOCHOICE = 15;
        public static final int SPOT_STIM = 16;
        public static final int SPOT_CONC = 17;
        public static final int SPOT_NFLIES = 18;
        public static final int SPOT_CAGEID = 19;
        public static final int SPOT_CAGEROW = 20;
        public static final int SPOT_CAGECOL = 21;
        public static final int CAGE_STRAIN = 22;
        public static final int CAGE_SEX = 23;
        public static final int CAGE_AGE = 24;
        public static final int CAGE_COMMENT = 25;
        public static final int DUM4 = 26;
        
        private ColumnPositions() {
            // Prevent instantiation
        }
    }
    
    // Error messages
    public static final class ErrorMessages {
        public static final String EXPORT_ERROR_FORMAT = "XLSExport:ExportError() ERROR in %s\n nOutputFrames=%d kymoFirstCol_Ms=%d kymoLastCol_Ms=%d";
        public static final String ONLY_ONE_STIMULUS_FORMAT = "Only 1 stimulus in cage %s - file %s";
        public static final String WORKBOOK_INIT_ERROR = "Failed to initialize Excel workbook";
        public static final String FILE_WRITE_ERROR = "Failed to write Excel file";
        public static final String DATA_PROCESSING_ERROR = "Error processing data for export";
        public static final String RESOURCE_CLEANUP_ERROR = "Error cleaning up Excel resources";
        
        private ErrorMessages() {
            // Prevent instantiation
        }
    }
    
    // Default export options
    public static final class DefaultOptions {
        public static final boolean XY_IMAGE = true;
        public static final boolean XY_CAGE = true;
        public static final boolean XY_CAPILLARIES = true;
        public static final boolean ELLIPSE_AXES = false;
        public static final boolean DISTANCE = false;
        public static final boolean ALIVE = true;
        public static final boolean SLEEP = true;
        public static final boolean TOP_LEVEL = true;
        public static final boolean TOP_LEVEL_DELTA = false;
        public static final boolean BOTTOM_LEVEL = false;
        public static final boolean DERIVATIVE = false;
        public static final boolean LR_PI = true;
        public static final boolean SPOT_AREAS = true;
        public static final boolean SUM = true;
        public static final boolean SUM2 = true;
        public static final boolean N_PIXELS = true;
        public static final boolean AUTOCORRELATION = false;
        public static final boolean CROSSCORRELATION = false;
        public static final boolean CROSSCORRELATION_LR = false;
        public static final boolean SUM_PER_CAGE = true;
        public static final boolean SUBTRACT_T0 = true;
        public static final boolean RELATIVE_TO_T0 = true;
        public static final boolean RELATIVE_TO_MEDIAN_T0 = false;
        public static final boolean ONLY_ALIVE = true;
        public static final boolean TRANSPOSE = false;
        public static final boolean DUPLICATE_SERIES = true;
        public static final boolean FIXED_INTERVALS = false;
        public static final boolean EXPORT_ALL_FILES = true;
        public static final boolean ABSOLUTE_TIME = false;
        public static final boolean COLLATE_SERIES = false;
        public static final boolean PAD_INTERVALS = true;
        public static final boolean TRIM_ALIVE = false;
        public static final boolean COMPENSATE_EVAPORATION = false;
        
        // Default numeric values
        public static final int BUILD_EXCEL_STEP_MS = 1;
        public static final int BUILD_EXCEL_UNIT_MS = 1;
        public static final int N_BINS_CORRELATION = 40;
        public static final long START_ALL_MS = 0;
        public static final long END_ALL_MS = 999999;
        public static final double LR_PI_THRESHOLD = 0.0;
        
        // Default index values
        public static final int EXPERIMENT_INDEX_FIRST = -1;
        public static final int EXPERIMENT_INDEX_LAST = -1;
        public static final int CAGE_INDEX_FIRST = -1;
        public static final int CAGE_INDEX_LAST = -1;
        public static final int SERIES_INDEX_FIRST = -1;
        public static final int SERIES_INDEX_LAST = -1;
        
        private DefaultOptions() {
            // Prevent instantiation
        }
    }
    
    private ExcelExportConstants() {
        // Prevent instantiation
    }
} 