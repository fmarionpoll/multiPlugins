package plugins.fmp.multiSPOTS96.tools.JComponents.exceptions;

/**
 * Base exception class for all JComponent related errors.
 * Provides structured error handling for the JComponents subsystem.
 */
public class JComponentException extends Exception {
    
    private final String operation;
    private final String context;
    
    public JComponentException(String message) {
        super(message);
        this.operation = null;
        this.context = null;
    }
    
    public JComponentException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.context = null;
    }
    
    public JComponentException(String message, String operation, String context) {
        super(message);
        this.operation = operation;
        this.context = context;
    }
    
    public JComponentException(String message, String operation, String context, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.context = context;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getContext() {
        return context;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        if (operation != null) {
            sb.append("Operation: ").append(operation).append(". ");
        }
        if (context != null) {
            sb.append("Context: ").append(context).append(". ");
        }
        sb.append(super.getMessage());
        return sb.toString();
    }
} 