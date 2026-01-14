package plugins.fmp.multiSPOTS96.tools.ROI2D;

/**
 * Exception thrown when ROI validation fails.
 */
public class ValidationException extends ROI2DException {

	private static final long serialVersionUID = 1L;

	private final String parameterName;
	private final Object parameterValue;

	public ValidationException(String parameterName, Object parameterValue, String reason) {
		super(String.format("Invalid parameter '%s' with value '%s': %s", parameterName, parameterValue, reason));
		this.parameterName = parameterName;
		this.parameterValue = parameterValue;
	}

	public ValidationException(String parameterName, Object parameterValue, String reason, String operation) {
		super(String.format("Invalid parameter '%s' with value '%s': %s", parameterName, parameterValue, reason),
				operation, "Parameter validation");
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
