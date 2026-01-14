package plugins.fmp.multiSPOTS96.tools.JComponents.exceptions;

/**
 * Exception thrown when experiment processing operations fail.
 * This includes loading, chaining, and data processing errors.
 */
public class ExperimentProcessingException extends JComponentException {
    
    public ExperimentProcessingException(String message) {
        super(message);
    }
    
    public ExperimentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ExperimentProcessingException(String message, String operation, String context) {
        super(message, operation, context);
    }
    
    public ExperimentProcessingException(String message, String operation, String context, Throwable cause) {
        super(message, operation, context, cause);
    }
} 