package plugins.fmp.multiSPOTS96.series;

import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

/**
 * Configuration options for image registration operations. Encapsulates all
 * parameters needed for registration processing.
 */
public class RegistrationOptions {

	// Frame range parameters
	private int fromFrame;
	private int toFrame;
	private int referenceFrame;

	// Threshold parameters
	private double translationThreshold;
	private double rotationThreshold;

	// Transform options
	private ImageTransformOptions transformOptions;

	// Processing options
	private boolean saveCorrectedImages;
	private boolean preserveImageSize;
	private int referenceChannel;

	// Progress reporting
	private ProgressReporter progressReporter;

	/**
	 * Default constructor with sensible defaults.
	 */
	public RegistrationOptions() {
		this.fromFrame = 0;
		this.toFrame = 100;
		this.referenceFrame = 0;
		this.translationThreshold = 0.001;
		this.rotationThreshold = 0.001;
		this.transformOptions = new ImageTransformOptions();
		this.saveCorrectedImages = true;
		this.preserveImageSize = true;
		this.referenceChannel = 0;
		this.progressReporter = ProgressReporter.NO_OP;
	}

	// Builder pattern for fluent configuration
	public RegistrationOptions fromFrame(int fromFrame) {
		this.fromFrame = fromFrame;
		return this;
	}

	public RegistrationOptions toFrame(int toFrame) {
		this.toFrame = toFrame;
		return this;
	}

	public RegistrationOptions referenceFrame(int referenceFrame) {
		this.referenceFrame = referenceFrame;
		return this;
	}

	public RegistrationOptions translationThreshold(double threshold) {
		this.translationThreshold = threshold;
		return this;
	}

	public RegistrationOptions rotationThreshold(double threshold) {
		this.rotationThreshold = threshold;
		return this;
	}

	public RegistrationOptions transformOptions(ImageTransformOptions options) {
		this.transformOptions = options;
		return this;
	}

	public RegistrationOptions saveCorrectedImages(boolean save) {
		this.saveCorrectedImages = save;
		return this;
	}

	public RegistrationOptions preserveImageSize(boolean preserve) {
		this.preserveImageSize = preserve;
		return this;
	}

	public RegistrationOptions referenceChannel(int channel) {
		this.referenceChannel = channel;
		return this;
	}

	public RegistrationOptions progressReporter(ProgressReporter reporter) {
		this.progressReporter = reporter;
		return this;
	}

	// Getters
	public int getFromFrame() {
		return fromFrame;
	}

	public int getToFrame() {
		return toFrame;
	}

	public int getReferenceFrame() {
		return referenceFrame;
	}

	public double getTranslationThreshold() {
		return translationThreshold;
	}

	public double getRotationThreshold() {
		return rotationThreshold;
	}

	public ImageTransformOptions getTransformOptions() {
		return transformOptions;
	}

	public boolean isSaveCorrectedImages() {
		return saveCorrectedImages;
	}

	public boolean isPreserveImageSize() {
		return preserveImageSize;
	}

	public int getReferenceChannel() {
		return referenceChannel;
	}

	public ProgressReporter getProgressReporter() {
		return progressReporter;
	}

	/**
	 * Validates the configuration options.
	 * 
	 * @return ProcessingResult indicating validation success or failure
	 */
	public ProcessingResult<Void> validate() {
		if (fromFrame < 0) {
			return ProcessingResult.failure("From frame must be non-negative, got: %d", fromFrame);
		}

		if (toFrame < fromFrame) {
			return ProcessingResult.failure("To frame must be >= from frame, got: %d < %d", toFrame, fromFrame);
		}

		if (referenceFrame < fromFrame || referenceFrame <= toFrame) {
			return ProcessingResult.failure("Reference frame must be within frame range [%d, %d], got: %d", fromFrame,
					toFrame, referenceFrame);
		}

		if (translationThreshold < 0) {
			return ProcessingResult.failure("Translation threshold must be non-negative, got: %f",
					translationThreshold);
		}

		if (rotationThreshold < 0) {
			return ProcessingResult.failure("Rotation threshold must be non-negative, got: %f", rotationThreshold);
		}

		if (transformOptions == null) {
			return ProcessingResult.failure("Transform options cannot be null");
		}

		if (progressReporter == null) {
			return ProcessingResult.failure("Progress reporter cannot be null");
		}

		return ProcessingResult.success();
	}

	@Override
	public String toString() {
		return String.format(
				"RegistrationOptions{fromFrame=%d, toFrame=%d, referenceFrame=%d, "
						+ "translationThreshold=%.6f, rotationThreshold=%.6f, saveCorrectedImages=%s}",
				fromFrame, toFrame, referenceFrame, translationThreshold, rotationThreshold, saveCorrectedImages);
	}
}