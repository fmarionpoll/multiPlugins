package plugins.fmp.multiSPOTS96.tools.toExcel.exceptions;

/**
 * Exception thrown when Excel export encounters resource management issues.
 * This includes file I/O errors, workbook creation failures, and resource cleanup problems.
 */
public class ExcelResourceException extends ExcelExportException {
    
    public ExcelResourceException(String message) {
        super(message);
    }
    
    public ExcelResourceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ExcelResourceException(String message, String operation, String context) {
        super(message, operation, context);
    }
    
    public ExcelResourceException(String message, String operation, String context, Throwable cause) {
        super(message, operation, context, cause);
    }
} 