package plugins.fmp.multiSPOTS96.experiment.spots;

import java.util.List;
import java.util.Optional;

/**
 * Result class for spots data operations (load, save, merge, etc.).
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class SpotsDataOperationResult {
    private final boolean success;
    private final int processedCount;
    private final int failedCount;
    private final List<String> processedItems;
    private final List<String> failedItems;
    private final Optional<Exception> lastError;
    private final String message;
    private final long processingTimeMs;
    private final String operationType;
    
    private SpotsDataOperationResult(Builder builder) {
        this.success = builder.success;
        this.processedCount = builder.processedCount;
        this.failedCount = builder.failedCount;
        this.processedItems = builder.processedItems != null ? List.copyOf(builder.processedItems) : List.of();
        this.failedItems = builder.failedItems != null ? List.copyOf(builder.failedItems) : List.of();
        this.lastError = Optional.ofNullable(builder.lastError);
        this.message = builder.message;
        this.processingTimeMs = builder.processingTimeMs;
        this.operationType = builder.operationType;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static SpotsDataOperationResult success(String operationType, int processedCount, String message) {
        return builder()
            .success(true)
            .operationType(operationType)
            .processedCount(processedCount)
            .message(message)
            .build();
    }
    
    public static SpotsDataOperationResult failure(String operationType, Exception error, String message) {
        return builder()
            .success(false)
            .operationType(operationType)
            .lastError(error)
            .message(message)
            .build();
    }
    
    public static SpotsDataOperationResult partial(String operationType, int processedCount, int failedCount, String message) {
        return builder()
            .success(processedCount > 0)
            .operationType(operationType)
            .processedCount(processedCount)
            .failedCount(failedCount)
            .message(message)
            .build();
    }
    
    public boolean isSuccess() { return success; }
    public int getProcessedCount() { return processedCount; }
    public int getFailedCount() { return failedCount; }
    public List<String> getProcessedItems() { return processedItems; }
    public List<String> getFailedItems() { return failedItems; }
    public Optional<Exception> getLastError() { return lastError; }
    public String getMessage() { return message; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public String getOperationType() { return operationType; }
    
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
    
    public boolean hasPartialFailure() {
        return processedCount > 0 && failedCount > 0;
    }
    
    /**
     * Creates a builder initialized with this result's values.
     * Allows modification of an existing result.
     * 
     * @return a new builder with this result's values
     */
    public Builder toBuilder() {
        return builder()
            .success(success)
            .processedCount(processedCount)
            .failedCount(failedCount)
            .processedItems(processedItems)
            .failedItems(failedItems)
            .lastError(lastError.orElse(null))
            .message(message)
            .processingTimeMs(processingTimeMs)
            .operationType(operationType);
    }
    
    @Override
    public String toString() {
        return String.format("SpotsDataOperationResult{type='%s', success=%b, processed=%d, failed=%d, time=%dms, message='%s'}", 
                           operationType, success, processedCount, failedCount, processingTimeMs, message);
    }
    
    public static class Builder {
        private boolean success = true;
        private int processedCount = 0;
        private int failedCount = 0;
        private List<String> processedItems;
        private List<String> failedItems;
        private Exception lastError;
        private String message = "";
        private long processingTimeMs = 0;
        private String operationType = "UNKNOWN";
        
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
        
        public Builder processedItems(List<String> processedItems) {
            this.processedItems = processedItems;
            return this;
        }
        
        public Builder failedItems(List<String> failedItems) {
            this.failedItems = failedItems;
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
        
        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }
        
        public SpotsDataOperationResult build() {
            return new SpotsDataOperationResult(this);
        }
    }
} 