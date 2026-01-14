package plugins.fmp.multiSPOTS96.tools.JComponents.exceptions;

/**
 * Exception thrown when file dialog operations fail.
 * This includes file selection, validation, and I/O errors.
 */
public class FileDialogException extends JComponentException {
    
    public FileDialogException(String message) {
        super(message);
    }
    
    public FileDialogException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FileDialogException(String message, String operation, String context) {
        super(message, operation, context);
    }
    
    public FileDialogException(String message, String operation, String context, Throwable cause) {
        super(message, operation, context, cause);
    }
} 