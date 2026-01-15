package plugins.fmp.multiSPOTS96.tools.imageTransform;

/**
 * Exception thrown when invalid parameters are provided to a transform.
 */
public class InvalidParameterException extends ImageTransformException {
    
    private static final long serialVersionUID = 1L;
    
    private final String parameterName;
    private final Object parameterValue;
    
    public InvalidParameterException(String parameterName, Object parameterValue, String reason) {
        super(String.format("Invalid parameter '%s' with value '%s': %s", 
                           parameterName, parameterValue, reason));
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
    }
    
    public InvalidParameterException(String parameterName, Object parameterValue, String reason, String transformName) {
        super(String.format("Invalid parameter '%s' with value '%s': %s", 
                           parameterName, parameterValue, reason), 
              transformName, "Parameter validation");
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
    }
    
    public String getParameterName() {
        return parameterName;
    }
    
    public Object getParameterValue() {
        return parameterValue;
    }
} 