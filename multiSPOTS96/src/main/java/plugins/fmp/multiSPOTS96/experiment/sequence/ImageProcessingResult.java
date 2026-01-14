package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.util.List;
import java.util.Optional;

/**
 * Result class for image processing operations.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class ImageProcessingResult {
    private final boolean success;
    private final int processedCount;
    private final int failedCount;
    private final List<String> processedFiles;
    private final List<String> failedFiles;
    private final Optional<Exception> lastError;
    private final String message;
    private final long processingTimeMs;
    
    private ImageProcessingResult(Builder builder) {
        this.success = builder.success;
        this.processedCount = builder.processedCount;
        this.failedCount = builder.failedCount;
        this.processedFiles = builder.processedFiles != null ? List.copyOf(builder.processedFiles) : List.of();
        this.failedFiles = builder.failedFiles != null ? List.copyOf(builder.failedFiles) : List.of();
        this.lastError = Optional.ofNullable(builder.lastError);
        this.message = builder.message;
        this.processingTimeMs = builder.processingTimeMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ImageProcessingResult success(int processedCount, String message) {
        return builder()
            .success(true)
            .processedCount(processedCount)
            .message(message)
            .build();
    }
    
    public static ImageProcessingResult failure(Exception error, String message) {
        return builder()
            .success(false)
            .lastError(error)
            .message(message)
            .build();
    }
    
    public static ImageProcessingResult partial(int processedCount, int failedCount, String message) {
        return builder()
            .success(processedCount > 0)
            .processedCount(processedCount)
            .failedCount(failedCount)
            .message(message)
            .build();
    }
    
    public boolean isSuccess() { return success; }
    public int getProcessedCount() { return processedCount; }
    public int getFailedCount() { return failedCount; }
    public List<String> getProcessedFiles() { return processedFiles; }
    public List<String> getFailedFiles() { return failedFiles; }
    public Optional<Exception> getLastError() { return lastError; }
    public String getMessage() { return message; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    
    public int getTotalCount() {
        return processedCount + failedCount;
    }
    
    public double getSuccessRate() {
        int total = getTotalCount();
        return total > 0 ? (double) processedCount / total : 0.0;
    }
    
    public boolean hasErrors() {
        return failedCount > 0 || lastError.isPresent();
    }
    
    @Override
    public String toString() {
        return String.format("ImageProcessingResult{success=%b, processed=%d, failed=%d, time=%dms, message='%s'}", 
                           success, processedCount, failedCount, processingTimeMs, message);
    }
    
    public static class Builder {
        private boolean success = true;
        private int processedCount = 0;
        private int failedCount = 0;
        private List<String> processedFiles;
        private List<String> failedFiles;
        private Exception lastError;
        private String message = "";
        private long processingTimeMs = 0;
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder processedCount(int processedCount) {
            this.processedCount = processedCount;
            return this;
        }
        
        public Builder failedCount(int failedCount) {
            this.failedCount = failedCount;
            return this;
        }
        
        public Builder processedFiles(List<String> processedFiles) {
            this.processedFiles = processedFiles;
            return this;
        }
        
        public Builder failedFiles(List<String> failedFiles) {
            this.failedFiles = failedFiles;
            return this;
        }
        
        public Builder lastError(Exception lastError) {
            this.lastError = lastError;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public ImageProcessingResult build() {
            return new ImageProcessingResult(this);
        }
    }
} 