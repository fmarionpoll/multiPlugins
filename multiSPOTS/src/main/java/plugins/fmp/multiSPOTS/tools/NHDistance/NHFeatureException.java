package plugins.fmp.multiSPOTS.tools.NHDistance;

/**
 * The Class FeatureException.
 * 
 * @author Nicolas HERVE - nherve@ina.fr
 */
public class NHFeatureException extends Exception {
	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 7211467602505697543L;

	/**
	 * Instantiates a new feature exception.
	 */
	public NHFeatureException() {
	}

	/**
	 * Instantiates a new feature exception.
	 * 
	 * @param message the message
	 */
	public NHFeatureException(String message) {
		super(message);
	}

	/**
	 * Instantiates a new feature exception.
	 * 
	 * @param cause the cause
	 */
	public NHFeatureException(Throwable cause) {
		super(cause);
	}

	/**
	 * Instantiates a new feature exception.
	 * 
	 * @param message the message
	 * @param cause   the cause
	 */
	public NHFeatureException(String message, Throwable cause) {
		super(message, cause);
	}
}
