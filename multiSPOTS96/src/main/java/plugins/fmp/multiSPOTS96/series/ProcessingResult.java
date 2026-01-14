package plugins.fmp.multiSPOTS96.series;

import java.util.Optional;

import plugins.fmp.multiSPOTS96.series.exceptions.SeriesProcessingException;

/**
 * Generic processing result that can represent success or failure.
 * Replaces exceptions for recoverable errors and provides more information.
 */
public class ProcessingResult<T> {
    private final boolean success;
    private final String errorMessage;
    private final Optional<T> data;
    private final Optional<Throwable> cause;
    
    private ProcessingResult(boolean success, String errorMessage, T data, Throwable cause) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.data = Optional.ofNullable(data);
        this.cause = Optional.ofNullable(cause);
    }
    
    // Factory methods for success
    public static <T> ProcessingResult<T> success(T data) {
        return new ProcessingResult<>(true, null, data, null);
    }
    
    public static ProcessingResult<Void> success() {
        return new ProcessingResult<>(true, null, null, null);
    }
    
    // Factory methods for failure
    public static <T> ProcessingResult<T> failure(String errorMessage) {
        return new ProcessingResult<>(false, errorMessage, null, null);
    }
    
    public static <T> ProcessingResult<T> failure(String errorMessage, Throwable cause) {
        return new ProcessingResult<>(false, errorMessage, null, cause);
    }
    
    public static <T> ProcessingResult<T> failure(String format, Object... args) {
        return new ProcessingResult<>(false, String.format(format, args), null, null);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public boolean isFailure() { return !success; }
    public String getErrorMessage() { return errorMessage; }
    public Optional<T> getData() { return data; }
    public Optional<Throwable> getCause() { return cause; }
    
    // Utility methods
    public T getDataOrThrow() {
        if (success) {
            return data.orElse(null);
        } else {
            throw new SeriesProcessingException(errorMessage, cause.orElse(null));
        }
    }
    
    public T getDataOrDefault(T defaultValue) {
        return success ? data.orElse(defaultValue) : defaultValue;
    }
    
    public <U> ProcessingResult<U> map(java.util.function.Function<T, U> mapper) {
        if (success && data.isPresent()) {
            try {
                return ProcessingResult.success(mapper.apply(data.get()));
            } catch (Exception e) {
                return ProcessingResult.failure("Mapping failed", e);
            }
        } else {
            return ProcessingResult.failure(errorMessage, cause.orElse(null));
        }
    }
    
    public <U> ProcessingResult<U> flatMap(java.util.function.Function<T, ProcessingResult<U>> mapper) {
        if (success && data.isPresent()) {
            try {
                return mapper.apply(data.get());
            } catch (Exception e) {
                return ProcessingResult.failure("FlatMapping failed", e);
            }
        } else {
            return ProcessingResult.failure(errorMessage, cause.orElse(null));
        }
    }
    
    @Override
    public String toString() {
        if (success) {
            return "ProcessingResult.success(" + data.orElse(null) + ")";
        } else {
            return "ProcessingResult.failure(" + errorMessage + ")";
        }
    }
} 