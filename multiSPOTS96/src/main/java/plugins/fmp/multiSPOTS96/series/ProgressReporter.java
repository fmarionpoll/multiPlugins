package plugins.fmp.multiSPOTS96.series;

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
} 