package plugins.fmp.multitools.series;

import java.util.List;

import plugins.fmp.multitools.experiment.capillary.Capillary;

/**
 * Interface for progress reporting during series processing.
 * Allows decoupling of UI progress updates from business logic.
 */
public interface ProgressReporter {
    
    /**
     * Updates the progress message.
     * @param message The progress message to display
     */
    void updateMessage(String message);
    
    /**
     * Updates the progress with formatted message.
     * @param format The format string
     * @param args The arguments for formatting
     */
    default void updateMessage(String format, Object... args) {
        updateMessage(String.format(format, args));
    }
    
    /**
     * Updates the progress percentage.
     * @param percentage The progress percentage (0-100)
     */
    void updateProgress(int percentage);
    
    /**
     * Updates both message and progress.
     * @param message The progress message
     * @param current The current progress value
     * @param total The total progress value
     */
    default void updateProgress(String message, int current, int total) {
        updateMessage(message);
        updateProgress((int) (((double) current / total) * 100));
    }
    
    /**
     * Indicates that the process has completed.
     */
    void completed();
    
    /**
     * Indicates that the process has failed.
     * @param errorMessage The error message
     */
    void failed(String errorMessage);
    
    /**
     * Checks if the process should be cancelled.
     * @return true if the process should be cancelled
     */
    boolean isCancelled();

    /**
     * Called when outlier capillaries are detected at a frame (unusual movement vs others).
     * @param frameT frame index where outliers were detected
     * @param outlierIndices capillary indices (into experiment's list) that are outliers
     * @param caps full list of capillaries (to resolve names)
     * @return 0 = continue and apply all, 1 = stop tracking, 2 = skip outliers for this frame only and continue
     */
    default int reportOutliers(int frameT, List<Integer> outlierIndices, List<Capillary> caps) {
        return 0;
    }

    /**
     * No-op implementation for cases where progress reporting is not needed.
     */
    public static final ProgressReporter NO_OP = new ProgressReporter() {
        @Override
        public void updateMessage(String message) {}
        
        @Override
        public void updateProgress(int percentage) {}
        
        @Override
        public void completed() {}
        
        @Override
        public void failed(String errorMessage) {}
        
        @Override
        public boolean isCancelled() { return false; }
    };

    int CONTINUE_APPLY_ALL = 0;
    int STOP_TRACKING = 1;
    int SKIP_OUTLIERS_THIS_FRAME = 2;
} 