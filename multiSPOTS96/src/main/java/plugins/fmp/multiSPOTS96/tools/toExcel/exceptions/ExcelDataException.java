package plugins.fmp.multiSPOTS96.tools.toExcel.exceptions;

/**
 * Exception thrown when Excel export encounters data processing issues.
 * This includes data validation errors, transformation failures, and calculation problems.
 */
public class ExcelDataException extends ExcelExportException {
    
    public ExcelDataException(String message) {
        super(message);
    }
    
    public ExcelDataException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ExcelDataException(String message, String operation, String context) {
        super(message, operation, context);
    }
    
    public ExcelDataException(String message, String operation, String context, Throwable cause) {
        super(message, operation, context, cause);
    }
} 