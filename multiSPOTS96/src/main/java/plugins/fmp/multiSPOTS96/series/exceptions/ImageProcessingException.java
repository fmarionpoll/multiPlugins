package plugins.fmp.multiSPOTS96.series.exceptions;

/**
 * Exception for image processing errors. Provides specific error handling for
 * image-related operations.
 */
public class ImageProcessingException extends SeriesProcessingException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ImageProcessingException(String message) {
		super(message);
	}

	public ImageProcessingException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates an exception for image loading errors.
	 */
	public static ImageProcessingException imageLoadFailed(String filename, Throwable cause) {
		return new ImageProcessingException(String.format("Failed to load image: %s", filename), cause);
	}

	/**
	 * Creates an exception for image save errors.
	 */
	public static ImageProcessingException imageSaveFailed(String filename, Throwable cause) {
		return new ImageProcessingException(String.format("Failed to save image: %s", filename), cause);
	}

	/**
	 * Creates an exception for image transformation errors.
	 */
	public static ImageProcessingException transformationFailed(String transformation, Throwable cause) {
		return new ImageProcessingException(String.format("Image transformation failed: %s", transformation), cause);
	}
}