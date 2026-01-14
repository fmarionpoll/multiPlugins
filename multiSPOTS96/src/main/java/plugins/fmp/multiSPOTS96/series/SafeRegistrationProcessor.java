package plugins.fmp.multiSPOTS96.series;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.logging.Logger;

import javax.vecmath.Vector2d;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.image.ImageUtil;
import icy.type.geom.Polygon2D;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.GaspardRigidRegistration;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;

/**
 * Safe implementation of RegistrationProcessor with proper error handling.
 * Replaces scattered registration operations with centralized, safe operations.
 */
public class SafeRegistrationProcessor implements RegistrationProcessor {

	private static final Logger LOGGER = Logger.getLogger(SafeRegistrationProcessor.class.getName());

	private final ImageProcessor imageProcessor;
	private final ProgressReporter progressReporter;

	/**
	 * Constructor with dependency injection.
	 */
	public SafeRegistrationProcessor() {
		this(new SafeImageProcessor(), ProgressReporter.NO_OP);
	}

	public SafeRegistrationProcessor(ImageProcessor imageProcessor, ProgressReporter progressReporter) {
		this.imageProcessor = imageProcessor;
		this.progressReporter = progressReporter;
	}

	@Override
	public ProcessingResult<RegistrationResult> correctDrift(Experiment experiment, RegistrationOptions options) {
		return performRegistration(experiment, options, false, true);
	}

	@Override
	public ProcessingResult<RegistrationResult> correctRotation(Experiment experiment, RegistrationOptions options) {
		return performRegistration(experiment, options, true, false);
	}

	@Override
	public ProcessingResult<RegistrationResult> correctDriftAndRotation(Experiment experiment,
			RegistrationOptions options) {
		return performRegistration(experiment, options, true, true);
	}

	@Override
	public ProcessingResult<TranslationResult> findTranslation(IcyBufferedImage sourceImage,
			IcyBufferedImage targetImage, int channel) {
		if (sourceImage == null) {
			return ProcessingResult.failure("Source image cannot be null");
		}

		if (targetImage == null) {
			return ProcessingResult.failure("Target image cannot be null");
		}

		if (channel < 0 || channel >= sourceImage.getSizeC()) {
			return ProcessingResult.failure("Invalid channel: %d", channel);
		}

		try {
			Vector2d translation = GaspardRigidRegistration.findTranslation2D(sourceImage, channel, targetImage,
					channel);
			TranslationResult result = new TranslationResult(translation.x, translation.y);
			return ProcessingResult.success(result);

		} catch (Exception e) {
			LOGGER.warning("Failed to find translation: " + e.getMessage());
			return ProcessingResult.failure("Translation detection failed", e);
		}
	}

	@Override
	public ProcessingResult<RotationResult> findRotation(IcyBufferedImage sourceImage, IcyBufferedImage targetImage,
			int channel, TranslationResult previousTranslation) {
		if (sourceImage == null) {
			return ProcessingResult.failure("Source image cannot be null");
		}

		if (targetImage == null) {
			return ProcessingResult.failure("Target image cannot be null");
		}

		if (channel < 0 || channel >= sourceImage.getSizeC()) {
			return ProcessingResult.failure("Invalid channel: %d", channel);
		}

		try {
			Vector2d translation = previousTranslation != null
					? new Vector2d(previousTranslation.getX(), previousTranslation.getY())
					: null;

			double angle = GaspardRigidRegistration.findRotation2D(sourceImage, channel, targetImage, channel,
					translation);
			RotationResult result = new RotationResult(angle);
			return ProcessingResult.success(result);

		} catch (Exception e) {
			LOGGER.warning("Failed to find rotation: " + e.getMessage());
			return ProcessingResult.failure("Rotation detection failed", e);
		}
	}

	@Override
	public ProcessingResult<IcyBufferedImage> applyTranslation(IcyBufferedImage image, TranslationResult translation,
			int channel, boolean preserveSize) {
		if (image == null) {
			return ProcessingResult.failure("Image cannot be null");
		}

		if (translation == null) {
			return ProcessingResult.failure("Translation cannot be null");
		}

		try {
			Vector2d vector = new Vector2d(translation.getX(), translation.getY());
			IcyBufferedImage result = GaspardRigidRegistration.applyTranslation2D(image, channel, vector, preserveSize);
			return ProcessingResult.success(result);

		} catch (Exception e) {
			LOGGER.warning("Failed to apply translation: " + e.getMessage());
			return ProcessingResult.failure("Translation application failed", e);
		}
	}

	@Override
	public ProcessingResult<IcyBufferedImage> applyRotation(IcyBufferedImage image, RotationResult rotation,
			int channel, boolean preserveSize) {
		if (image == null) {
			return ProcessingResult.failure("Image cannot be null");
		}

		if (rotation == null) {
			return ProcessingResult.failure("Rotation cannot be null");
		}

		try {
			IcyBufferedImage result = GaspardRigidRegistration.applyRotation2D(image, channel,
					rotation.getAngleRadians(), preserveSize);
			return ProcessingResult.success(result);

		} catch (Exception e) {
			LOGGER.warning("Failed to apply rotation: " + e.getMessage());
			return ProcessingResult.failure("Rotation application failed", e);
		}
	}

	/**
	 * Performs the actual registration processing with proper error handling.
	 */
	private ProcessingResult<RegistrationResult> performRegistration(Experiment experiment, RegistrationOptions options,
			boolean correctRotation, boolean correctTranslation) {
		// Validate inputs
		ProcessingResult<Void> validationResult = validateRegistrationInputs(experiment, options);
		if (validationResult.isFailure()) {
			return ProcessingResult.failure(validationResult.getErrorMessage());
		}

		try {
			// Load experiment data
			ProcessingResult<Void> loadResult = loadExperimentData(experiment);
			if (loadResult.isFailure()) {
				return ProcessingResult.failure(loadResult.getErrorMessage());
			}

			// Perform registration
			return executeRegistration(experiment, options, correctRotation, correctTranslation);

		} finally {
			cleanupResources();
		}
	}

	/**
	 * Validates registration inputs.
	 */
	private ProcessingResult<Void> validateRegistrationInputs(Experiment experiment, RegistrationOptions options) {
		if (experiment == null) {
			return ProcessingResult.failure("Experiment cannot be null");
		}

		if (experiment.seqCamData == null) {
			return ProcessingResult.failure("Experiment must have camera data");
		}

		if (experiment.cagesArray == null) {
			return ProcessingResult.failure("Experiment must have cages array");
		}

		return options.validate();
	}

	/**
	 * Loads experiment data with proper error handling.
	 */
	private ProcessingResult<Void> loadExperimentData(Experiment experiment) {
		try {
			boolean loadSuccess = loadSeqCamDataAndCages(experiment);
			if (!loadSuccess) {
				return ProcessingResult.failure("Failed to load experiment data");
			}
			return ProcessingResult.success();
		} catch (Exception e) {
			return ProcessingResult.failure("Error loading experiment data", e);
		}
	}

	/**
	 * Executes the registration algorithm.
	 */
	private ProcessingResult<RegistrationResult> executeRegistration(Experiment experiment, RegistrationOptions options,
			boolean correctRotation, boolean correctTranslation) {
		ProgressFrame progressBar = new ProgressFrame("Registration Analysis");

		try {
			// Setup registration parameters
			int fromFrame = options.getFromFrame();
			int toFrame = options.getToFrame();
			int referenceFrame = options.getReferenceFrame();
			double translationThreshold = options.getTranslationThreshold();
			double rotationThreshold = options.getRotationThreshold();

			// Get reference image
			ProcessingResult<IcyBufferedImage> refImageResult = loadReferenceImage(experiment, referenceFrame, options);
			if (refImageResult.isFailure()) {
				return ProcessingResult.failure(refImageResult.getErrorMessage());
			}

			IcyBufferedImage referenceImage = refImageResult.getDataOrThrow();
			IcyBufferedImage transformedReference = applyTransform(referenceImage, options);
			IcyBufferedImage reducedReference = extractRegionOfInterest(transformedReference, experiment);

			// Process each frame
			RegistrationStatistics stats = new RegistrationStatistics();

			for (int frame = fromFrame; frame < toFrame && !stopFlag; frame++) {
				progressBar.setMessage("Processing frame: " + frame + "/" + toFrame);

				ProcessingResult<RegistrationResult> frameResult = processFrame(experiment, frame, referenceImage,
						reducedReference, options, correctRotation, correctTranslation, translationThreshold,
						rotationThreshold);

				if (frameResult.isSuccess()) {
					RegistrationProcessor.RegistrationResult frameResultData = frameResult.getDataOrThrow();
					// Update statistics from frame result
					stats.incrementFramesProcessed();
					if (frameResultData.getFramesCorrected() > 0) {
						stats.incrementFramesCorrected();
					}
					stats.addTranslationMagnitude(frameResultData.getAverageTranslationMagnitude());
					stats.addRotationAngle(frameResultData.getAverageRotationAngle());
				} else {
					LOGGER.warning("Frame " + frame + " processing failed: " + frameResult.getErrorMessage());
				}
			}

			return ProcessingResult.success(stats.buildResult());

		} finally {
			progressBar.close();
		}
	}

	/**
	 * Loads the reference image for registration.
	 */
	private ProcessingResult<IcyBufferedImage> loadReferenceImage(Experiment experiment, int referenceFrame,
			RegistrationOptions options) {
		String fileName = experiment.seqCamData.getFileNameFromImageList(referenceFrame);
		if (fileName == null) {
			return ProcessingResult.failure("Reference frame file not found: %d", referenceFrame);
		}

		return imageProcessor.loadImage(fileName);
	}

	/**
	 * Applies transform to an image.
	 */
	private IcyBufferedImage applyTransform(IcyBufferedImage image, RegistrationOptions options) {
		ImageTransformInterface transformFunction = options.getTransformOptions().transformOption.getFunction();
		return transformFunction.getTransformedImage(image, options.getTransformOptions());
	}

	/**
	 * Extracts the region of interest from the image.
	 */
	private IcyBufferedImage extractRegionOfInterest(IcyBufferedImage image, Experiment experiment) {
		Polygon2D polygon2D = experiment.cagesArray.getPolygon2DEnclosingAllCages();
		Rectangle rect = polygon2D.getBounds();
		return IcyBufferedImageUtil.getSubImage(image, rect.x, rect.y, rect.height, rect.width);
	}

	/**
	 * Processes a single frame for registration.
	 */
	private ProcessingResult<RegistrationResult> processFrame(Experiment experiment, int frame,
			IcyBufferedImage referenceImage, IcyBufferedImage reducedReference, RegistrationOptions options,
			boolean correctRotation, boolean correctTranslation, double translationThreshold,
			double rotationThreshold) {

		String fileName = experiment.seqCamData.getFileNameFromImageList(frame);
		if (fileName == null) {
			return ProcessingResult.failure("Frame file not found: %d", frame);
		}

		// Load and transform work image
		ProcessingResult<IcyBufferedImage> workImageResult = imageProcessor.loadImage(fileName);
		if (workImageResult.isFailure()) {
			return ProcessingResult.failure("Failed to load frame %d: %s", frame, workImageResult.getErrorMessage());
		}

		IcyBufferedImage workImage = workImageResult.getDataOrThrow();
		IcyBufferedImage transformedWorkImage = applyTransform(workImage, options);
		IcyBufferedImage reducedWorkImage = extractRegionOfInterest(transformedWorkImage, experiment);

		RegistrationStatistics frameStats = new RegistrationStatistics();
		frameStats.incrementFramesProcessed();

		// Apply corrections
		if (correctTranslation) {
			ProcessingResult<TranslationResult> translationResult = findTranslation(reducedWorkImage, reducedReference,
					options.getReferenceChannel());
			if (translationResult.isSuccess()) {
				TranslationResult translation = translationResult.getDataOrThrow();
				if (translation.isSignificant(translationThreshold)) {
					ProcessingResult<IcyBufferedImage> appliedResult = applyTranslation(workImage, translation, -1,
							options.isPreserveImageSize());
					if (appliedResult.isSuccess()) {
						workImage = appliedResult.getDataOrThrow();
						frameStats.incrementTranslations();
						LOGGER.info("Applied translation correction: (" + translation.getX() + ", " + translation.getY()
								+ ")");
					}
				}
			}
		}

		if (correctRotation) {
			ProcessingResult<RotationResult> rotationResult = findRotation(reducedWorkImage, reducedReference,
					options.getReferenceChannel(), null);
			if (rotationResult.isSuccess()) {
				RotationResult rotation = rotationResult.getDataOrThrow();
				if (rotation.isSignificant(rotationThreshold)) {
					ProcessingResult<IcyBufferedImage> appliedResult = applyRotation(workImage, rotation, -1,
							options.isPreserveImageSize());
					if (appliedResult.isSuccess()) {
						workImage = appliedResult.getDataOrThrow();
						frameStats.incrementRotations();
						LOGGER.info("Applied rotation correction: " + rotation.getAngleDegrees() + " degrees");
					}
				}
			}
		}

		// Save corrected image if needed
		if (options.isSaveCorrectedImages()
				&& (frameStats.getTotalTranslations() > 0 || frameStats.getTotalRotations() > 0)) {
			ProcessingResult<Void> saveResult = saveCorrectedImage(workImage, fileName);
			if (saveResult.isFailure()) {
				LOGGER.warning("Failed to save corrected image: " + saveResult.getErrorMessage());
			}
		}

		frameStats.incrementFramesCorrected();
		return ProcessingResult.success(frameStats.buildResult());
	}

	/**
	 * Saves a corrected image.
	 */
	private ProcessingResult<Void> saveCorrectedImage(IcyBufferedImage image, String fileName) {
		try {
			File outputFile = new File(fileName);
			RenderedImage renderedImage = ImageUtil.toRGBImage(image);
			boolean success = ImageUtil.save(renderedImage, "jpg", outputFile);

			if (!success) {
				return ProcessingResult.failure("Failed to save image: %s", fileName);
			}

			return ProcessingResult.success();

		} catch (Exception e) {
			return ProcessingResult.failure("Error saving image: %s", fileName);
		}
	}

	/**
	 * Cleans up resources properly.
	 */
	private void cleanupResources() {
		// Cleanup any resources if needed
	}

	/**
	 * Helper class to track registration statistics.
	 */
	private static class RegistrationStatistics {
		private int framesProcessed = 0;
		private int framesCorrected = 0;
		private int totalTranslations = 0;
		private int totalRotations = 0;
		private double totalTranslationMagnitude = 0.0;
		private double totalRotationAngle = 0.0;

		public void incrementFramesProcessed() {
			framesProcessed++;
		}

		public void incrementFramesCorrected() {
			framesCorrected++;
		}

		public void incrementTranslations() {
			totalTranslations++;
		}

		public void incrementRotations() {
			totalRotations++;
		}

		public void addTranslationMagnitude(double magnitude) {
			totalTranslationMagnitude += magnitude;
		}

		public void addRotationAngle(double angle) {
			totalRotationAngle += Math.abs(angle);
		}

		public int getTotalTranslations() {
			return totalTranslations;
		}

		public int getTotalRotations() {
			return totalRotations;
		}

		public RegistrationResult buildResult() {
			double avgTranslation = totalTranslations > 0 ? totalTranslationMagnitude / totalTranslations : 0.0;
			double avgRotation = totalRotations > 0 ? totalRotationAngle / totalRotations : 0.0;

			return new RegistrationResult(framesProcessed, framesCorrected, totalTranslations, totalRotations,
					avgTranslation, avgRotation);
		}
	}

	// Placeholder for stopFlag - should be injected or managed properly
	private boolean stopFlag = false;

	// Placeholder for loadSeqCamDataAndCages - should be moved to a proper service
	private boolean loadSeqCamDataAndCages(Experiment experiment) {
		// Implementation would be moved from BuildSeries
		return true;
	}
}