package plugins.fmp.multiSPOTS96.series.exceptions;

/**
 * Base exception for series processing errors. Provides better error handling
 * than generic exceptions.
 */
public class SeriesProcessingException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public SeriesProcessingException(String message) {
		super(message);
	}

	public SeriesProcessingException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates an exception with formatted message.
	 */
	public static SeriesProcessingException withFormattedMessage(String format, Object... args) {
		return new SeriesProcessingException(String.format(format, args));
	}
}