package plugins.fmp.multiSPOTS96.experiment.cages;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result class for cage operations with detailed feedback and metadata.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CageOperationResult {
    private final boolean success;
    private final String operationType;
    private final String message;
    private final Optional<Exception> error;
    private final long processingTimeMs;
    private final Map<String, Object> metadata;
    
    private CageOperationResult(Builder builder) {
        this.success = builder.success;
        this.operationType = Objects.requireNonNull(builder.operationType, "Operation type cannot be null");
        this.message = builder.message != null ? builder.message : "";
        this.error = Optional.ofNullable(builder.error);
        this.processingTimeMs = builder.processingTimeMs;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static CageOperationResult success(String operationType, String message) {
        return builder()
            .success(true)
            .operationType(operationType)
            .message(message)
            .build();
    }
    
    public static CageOperationResult failure(String operationType, Exception error, String message) {
        return builder()
            .success(false)
            .operationType(operationType)
            .error(error)
            .message(message)
            .build();
    }
    
    public static CageOperationResult partial(String operationType, String message, Map<String, Object> metadata) {
        return builder()
            .success(true)
            .operationType(operationType)
            .message(message)
            .metadata(metadata)
            .build();
    }
    
    public boolean isSuccess() { return success; }
    public String getOperationType() { return operationType; }
    public String getMessage() { return message; }
    public Optional<Exception> getError() { return error; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public boolean hasError() {
        return error.isPresent();
    }
    
    public boolean hasMetadata() {
        return !metadata.isEmpty();
    }
    
    public Optional<Object> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
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
            .operationType(operationType)
            .message(message)
            .error(error.orElse(null))
            .processingTimeMs(processingTimeMs)
            .metadata(metadata);
    }
    
    @Override
    public String toString() {
        return String.format("CageOperationResult{type='%s', success=%b, time=%dms, message='%s'}", 
                           operationType, success, processingTimeMs, message);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CageOperationResult that = (CageOperationResult) obj;
        return success == that.success &&
               processingTimeMs == that.processingTimeMs &&
               Objects.equals(operationType, that.operationType) &&
               Objects.equals(message, that.message);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, operationType, message, processingTimeMs);
    }
    
    public static class Builder {
        private boolean success = true;
        private String operationType = "UNKNOWN";
        private String message = "";
        private Exception error;
        private long processingTimeMs = 0;
        private Map<String, Object> metadata;
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder error(Exception error) {
            this.error = error;
            return this;
        }
        
        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = Map.of(key, value);
            } else {
                Map<String, Object> newMetadata = new java.util.HashMap<>(this.metadata);
                newMetadata.put(key, value);
                this.metadata = newMetadata;
            }
            return this;
        }
        
        public CageOperationResult build() {
            return new CageOperationResult(this);
        }
    }
} 