package plugins.fmp.multiSPOTS96.series;

import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS96.experiment.Experiment;

/**
 * Service interface for image registration operations. Provides abstraction for
 * different registration implementations.
 */
public interface RegistrationProcessor {

	/**
	 * Performs drift correction on an experiment's image sequence.
	 * 
	 * @param experiment The experiment containing the image sequence
	 * @param options    Registration options including frame range and transform
	 *                   settings
	 * @return ProcessingResult containing registration statistics or error
	 *         information
	 */
	ProcessingResult<RegistrationResult> correctDrift(Experiment experiment, RegistrationOptions options);

	/**
	 * Performs rotation correction on an experiment's image sequence.
	 * 
	 * @param experiment The experiment containing the image sequence
	 * @param options    Registration options including frame range and transform
	 *                   settings
	 * @return ProcessingResult containing registration statistics or error
	 *         information
	 */
	ProcessingResult<RegistrationResult> correctRotation(Experiment experiment, RegistrationOptions options);

	/**
	 * Performs both drift and rotation correction on an experiment's image
	 * sequence.
	 * 
	 * @param experiment The experiment containing the image sequence
	 * @param options    Registration options including frame range and transform
	 *                   settings
	 * @return ProcessingResult containing registration statistics or error
	 *         information
	 */
	ProcessingResult<RegistrationResult> correctDriftAndRotation(Experiment experiment, RegistrationOptions options);

	/**
	 * Finds the translation between two images.
	 * 
	 * @param sourceImage The source image
	 * @param targetImage The target image
	 * @param channel     The channel to use for registration
	 * @return ProcessingResult containing the translation vector
	 */
	ProcessingResult<TranslationResult> findTranslation(IcyBufferedImage sourceImage, IcyBufferedImage targetImage,
			int channel);

	/**
	 * Finds the rotation between two images.
	 * 
	 * @param sourceImage         The source image
	 * @param targetImage         The target image
	 * @param channel             The channel to use for registration
	 * @param previousTranslation Optional previous translation applied
	 * @return ProcessingResult containing the rotation angle
	 */
	ProcessingResult<RotationResult> findRotation(IcyBufferedImage sourceImage, IcyBufferedImage targetImage,
			int channel, TranslationResult previousTranslation);

	/**
	 * Applies translation to an image.
	 * 
	 * @param image        The image to translate
	 * @param translation  The translation to apply
	 * @param channel      The channel to translate (-1 for all channels)
	 * @param preserveSize Whether to preserve original image size
	 * @return ProcessingResult containing the translated image
	 */
	ProcessingResult<IcyBufferedImage> applyTranslation(IcyBufferedImage image, TranslationResult translation,
			int channel, boolean preserveSize);

	/**
	 * Applies rotation to an image.
	 * 
	 * @param image        The image to rotate
	 * @param rotation     The rotation to apply
	 * @param channel      The channel to rotate (-1 for all channels)
	 * @param preserveSize Whether to preserve original image size
	 * @return ProcessingResult containing the rotated image
	 */
	ProcessingResult<IcyBufferedImage> applyRotation(IcyBufferedImage image, RotationResult rotation, int channel,
			boolean preserveSize);

	/**
	 * Result of registration operation containing statistics.
	 */
	public static class RegistrationResult {
		private final int framesProcessed;
		private final int framesCorrected;
		private final int totalTranslations;
		private final int totalRotations;
		private final double averageTranslationMagnitude;
		private final double averageRotationAngle;

		public RegistrationResult(int framesProcessed, int framesCorrected, int totalTranslations, int totalRotations,
				double averageTranslationMagnitude, double averageRotationAngle) {
			this.framesProcessed = framesProcessed;
			this.framesCorrected = framesCorrected;
			this.totalTranslations = totalTranslations;
			this.totalRotations = totalRotations;
			this.averageTranslationMagnitude = averageTranslationMagnitude;
			this.averageRotationAngle = averageRotationAngle;
		}

		public int getFramesProcessed() {
			return framesProcessed;
		}

		public int getFramesCorrected() {
			return framesCorrected;
		}

		public int getTotalTranslations() {
			return totalTranslations;
		}

		public int getTotalRotations() {
			return totalRotations;
		}

		public double getAverageTranslationMagnitude() {
			return averageTranslationMagnitude;
		}

		public double getAverageRotationAngle() {
			return averageRotationAngle;
		}
	}

	/**
	 * Result of translation operation.
	 */
	public static class TranslationResult {
		private final double x;
		private final double y;
		private final double magnitude;

		public TranslationResult(double x, double y) {
			this.x = x;
			this.y = y;
			this.magnitude = Math.sqrt(x * x + y * y);
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public double getMagnitude() {
			return magnitude;
		}

		public boolean isSignificant(double threshold) {
			return magnitude > threshold;
		}
	}

	/**
	 * Result of rotation operation.
	 */
	public static class RotationResult {
		private final double angleRadians;
		private final double angleDegrees;

		public RotationResult(double angleRadians) {
			this.angleRadians = angleRadians;
			this.angleDegrees = Math.toDegrees(angleRadians);
		}

		public double getAngleRadians() {
			return angleRadians;
		}

		public double getAngleDegrees() {
			return angleDegrees;
		}

		public boolean isSignificant(double threshold) {
			return Math.abs(angleRadians) > threshold;
		}
	}
}