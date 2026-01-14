package plugins.fmp.multiSPOTS96.tools.ROI2D;

/**
 * Base exception class for ROI2D operations. Provides a foundation for specific
 * ROI2D-related exceptions with context information.
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public class ROI2DException extends Exception {

	private static final long serialVersionUID = 1L;

	private final String operation;
	private final String context;

	/**
	 * Creates a new ROI2D exception with a message.
	 * 
	 * @param message The error message
	 */
	public ROI2DException(String message) {
		super(message);
		this.operation = null;
		this.context = null;
	}

	/**
	 * Creates a new ROI2D exception with a message and cause.
	 * 
	 * @param message The error message
	 * @param cause   The underlying cause
	 */
	public ROI2DException(String message, Throwable cause) {
		super(message, cause);
		this.operation = null;
		this.context = null;
	}

	/**
	 * Creates a new ROI2D exception with detailed context.
	 * 
	 * @param message   The error message
	 * @param operation The operation that failed
	 * @param context   Additional context information
	 */
	public ROI2DException(String message, String operation, String context) {
		super(buildMessage(message, operation, context));
		this.operation = operation;
		this.context = context;
	}

	/**
	 * Creates a new ROI2D exception with detailed context and cause.
	 * 
	 * @param message   The error message
	 * @param cause     The underlying cause
	 * @param operation The operation that failed
	 * @param context   Additional context information
	 */
	public ROI2DException(String message, Throwable cause, String operation, String context) {
		super(buildMessage(message, operation, context), cause);
		this.operation = operation;
		this.context = context;
	}

	/**
	 * Gets the operation that failed.
	 * 
	 * @return The operation name, or null if not specified
	 */
	public String getOperation() {
		return operation;
	}

	/**
	 * Gets the context information.
	 * 
	 * @return The context, or null if not specified
	 */
	public String getContext() {
		return context;
	}

	/**
	 * Builds a formatted error message with operation and context.
	 */
	private static String buildMessage(String message, String operation, String context) {
		StringBuilder sb = new StringBuilder(message);
		if (operation != null) {
			sb.append(" [Operation: ").append(operation).append("]");
		}
		if (context != null) {
			sb.append(" [Context: ").append(context).append("]");
		}
		return sb.toString();
	}
}
