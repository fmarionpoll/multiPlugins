package plugins.fmp.multiSPOTS96.series;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;

/**
 * Refactored BuildBackground class with improved architecture. Demonstrates
 * proper dependency injection, error handling, and method decomposition.
 */
public class BuildBackground extends BuildSeries {

	// Dependencies - injected for better testability
	private final ImageProcessor imageProcessor;
	private final ProgressReporter progressReporter;

	// State
	private Sequence dataSequence = new Sequence();
	private Sequence referenceSequence = null;
	private ViewerFMP dataViewer = null;
	private ViewerFMP referenceViewer = null;
	private DetectFlyTools flyDetectionTools = new DetectFlyTools();
	private boolean saveSuccessful = false;

	// Constants
	private static final int MINIMUM_PIXELS_CHANGED_THRESHOLD = 10;

	// Constructor with dependency injection
	public BuildBackground() {
		this(new SafeImageProcessor(), ProgressReporter.NO_OP);
	}

	public BuildBackground(ImageProcessor imageProcessor, ProgressReporter progressReporter) {
		this.imageProcessor = imageProcessor;
		this.progressReporter = progressReporter;
	}

	@Override
	void analyzeExperiment(Experiment experiment) {
		ProcessingResult<Void> result = analyzeExperimentSafely(experiment);

		if (result.isFailure()) {
			System.err.println("Background analysis failed: " + result.getErrorMessage());
			progressReporter.failed(result.getErrorMessage());
		} else {
			progressReporter.completed();
		}
	}

	/**
	 * Safely analyzes an experiment with proper error handling. Replaces the
	 * original analyzeExperiment method with better error handling.
	 */
	private ProcessingResult<Void> analyzeExperimentSafely(Experiment experiment) {
		try {
			// Validate inputs
			ProcessingResult<Void> validationResult = validateExperiment(experiment);
			if (validationResult.isFailure()) {
				return validationResult;
			}

			// Load experiment data
			ProcessingResult<Void> loadResult = loadExperimentData(experiment);
			if (loadResult.isFailure()) {
				return loadResult;
			}

			// Validate bounds
			ProcessingResult<Void> boundsResult = validateBoundsForCages(experiment);
			if (boundsResult.isFailure()) {
				return boundsResult;
			}

			// Execute background building
			ProcessingResult<Void> buildResult = buildBackgroundSafely(experiment);
			if (buildResult.isFailure()) {
				return buildResult;
			}

			return ProcessingResult.success();

		} finally {
			cleanupResources();
		}
	}

	/**
	 * Validates the experiment for required data.
	 */
	private ProcessingResult<Void> validateExperiment(Experiment experiment) {
		if (experiment == null) {
			return ProcessingResult.failure("Experiment cannot be null");
		}

		if (experiment.getSeqCamData() == null) {
			return ProcessingResult.failure("Experiment must have camera data");
		}

		return ProcessingResult.success();
	}

	/**
	 * Loads experiment data with proper error handling. For background building,
	 * cages are optional - we can build background even if cages don't exist yet.
	 */
	private ProcessingResult<Void> loadExperimentData(Experiment experiment) {
		try {
			experiment.getSeqCamData().attachSequence(experiment.getSeqCamData().getImageLoader()
					.initSequenceFromFirstImage(experiment.getSeqCamData().getImagesList(true)));

			boolean cagesLoaded = experiment.load_cages_description_and_measures();
			if (!cagesLoaded) {
				Logger.warn(
						"Cages not loaded for background building - this is optional and background building will continue");
			}

//			// CRITICAL: Also load capillaries to prevent them from being overwritten as empty
//			// when save operations are triggered (e.g., closeViewsForCurrentExperiment)
//			// This protects kymograph measures from being erased during background building
//			experiment.loadMCCapillaries_Only();
//			if (experiment.getKymosBinFullDirectory() != null) {
//				experiment.getCapillaries().load_Capillaries(experiment.getKymosBinFullDirectory());
//			}

			return ProcessingResult.success();
		} catch (Exception e) {
			return ProcessingResult.failure("Error loading experiment data", e);
		}
	}

	/**
	 * Validates bounds for cages with proper error handling. For background
	 * building, this is optional if cages don't exist yet.
	 */
	private ProcessingResult<Void> validateBoundsForCages(Experiment experiment) {
		try {
			if (experiment.getCages() == null || experiment.getCages().cagesList == null
					|| experiment.getCages().cagesList.size() == 0) {
				Logger.warn("No cages found for background building - skipping bounds validation");
				return ProcessingResult.success();
			}

			boolean boundsValid = checkBoundsForCages(experiment);
			if (!boundsValid) {
				return ProcessingResult.failure("Invalid bounds for cages");
			}
			return ProcessingResult.success();
		} catch (Exception e) {
			return ProcessingResult.failure("Error validating cage bounds", e);
		}
	}

	/**
	 * Safely builds the background with proper error handling.
	 */
	private ProcessingResult<Void> buildBackgroundSafely(Experiment experiment) {
		try {
			// Initialize detection parameters
			initializeDetectionParameters(experiment);

			// Open viewers
			ProcessingResult<Void> viewerResult = openBackgroundViewers(experiment);
			if (viewerResult.isFailure()) {
				return viewerResult;
			}

			// Build background
			ProcessingResult<Void> backgroundResult = executeBackgroundBuilding(experiment);
			if (backgroundResult.isFailure()) {
				return backgroundResult;
			}

			// Save results
			ProcessingResult<Void> saveResult = saveBackgroundResults(experiment);
			if (saveResult.isFailure()) {
				return saveResult;
			}

			return ProcessingResult.success();

		} catch (Exception e) {
			return ProcessingResult.failure("Unexpected error during background building", e);
		}
	}

	/**
	 * Initializes detection parameters in a focused method.
	 */
	private void initializeDetectionParameters(Experiment experiment) {
		experiment.cleanPreviousDetectedFliesROIs();
		flyDetectionTools.initParametersForDetection(experiment, options);
		experiment.getCages().initFlyPositions(options.detectCage);
		options.threshold = options.thresholdDiff;
	}

	/**
	 * Opens background viewers with proper error handling.
	 */
	private ProcessingResult<Void> openBackgroundViewers(Experiment experiment) {
		try {
			SwingUtilities.invokeAndWait(() -> {
				createDataSequence(experiment);
				createReferenceSequence(experiment);
			});
			return ProcessingResult.success();
		} catch (InvocationTargetException | InterruptedException e) {
			return ProcessingResult.failure("Failed to open background viewers", e);
		}
	}

	/**
	 * Creates the data sequence viewer.
	 */
	private void createDataSequence(Experiment experiment) {
		dataSequence = newSequence("data recorded", experiment.getSeqCamData().getSeqImage(0, 0));
		dataViewer = new ViewerFMP(dataSequence, true, true);
	}

	/**
	 * Creates the reference sequence viewer.
	 */
	private void createReferenceSequence(Experiment experiment) {
		referenceSequence = newSequence("referenceImage", experiment.getSeqCamData().getReferenceImage());
		experiment.setSeqReference(referenceSequence);
		referenceViewer = new ViewerFMP(referenceSequence, true, true);
	}

	/**
	 * Executes the background building process.
	 */
	private ProcessingResult<Void> executeBackgroundBuilding(Experiment experiment) {
		try {
			ImageTransformOptions transformOptions = createTransformOptions();
			ProcessingResult<Void> buildResult = buildBackgroundImages(experiment, transformOptions);
			return buildResult;
		} catch (Exception e) {
			return ProcessingResult.failure("Error during background building execution", e);
		}
	}

	/**
	 * Creates transformation options for background building.
	 */
	private ImageTransformOptions createTransformOptions() {
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = ImageTransformEnums.SUBTRACT;
		transformOptions.setSingleThreshold(options.backgroundThreshold, stopFlag);
		transformOptions.background_delta = options.background_delta;
		transformOptions.background_jitter = options.background_jitter;
		return transformOptions;
	}

	/**
	 * Builds background images with proper progress reporting.
	 */
	private ProcessingResult<Void> buildBackgroundImages(Experiment experiment,
			ImageTransformOptions transformOptions) {
		progressReporter.updateMessage("Building background image...");

		try {
			// Load initial background image
			ProcessingResult<IcyBufferedImage> initialBackgroundResult = loadInitialBackgroundImage(experiment);
			if (initialBackgroundResult.isFailure()) {
				String errorMsg = "Failed to load initial background: " + initialBackgroundResult.getErrorMessage();
				Logger.error(errorMsg);
				return ProcessingResult.failure(errorMsg);
			}

			IcyBufferedImage initialImage = initialBackgroundResult.getData().orElse(null);
			if (initialImage == null) {
				String errorMsg = "Initial background image is null after loading";
				Logger.error(errorMsg);
				return ProcessingResult.failure(errorMsg);
			}

			transformOptions.backgroundImage = initialImage;

			// Calculate frame range
			FrameRange frameRange = calculateFrameRange(experiment);
			Logger.info("Processing background frames from " + frameRange.getFirst() + " to " + frameRange.getLast());

			// Process frames
			ProcessingResult<Void> processResult = processFramesForBackground(experiment, transformOptions, frameRange);
			if (processResult.isFailure()) {
				Logger.error("Frame processing failed: " + processResult.getErrorMessage());
				return processResult;
			}

			Logger.info("Background image building completed successfully");
			return ProcessingResult.success();

		} catch (Exception e) {
			String errorMsg = "Error building background images: " + e.getMessage();
			Logger.error(errorMsg, e);
			return ProcessingResult.failure(errorMsg, e);
		}
	}

	/**
	 * Loads the initial background image.
	 */
	private ProcessingResult<IcyBufferedImage> loadInitialBackgroundImage(Experiment experiment) {
		try {
			if (options.backgroundFirst < 0) {
				return ProcessingResult.failure("Background first frame index is negative: " + options.backgroundFirst);
			}

			String filename = experiment.getSeqCamData().getFileNameFromImageList(options.backgroundFirst);
			if (filename == null || filename.isEmpty()) {
				return ProcessingResult.failure("Cannot get filename for frame index: " + options.backgroundFirst);
			}

			Logger.info("Loading initial background image from: " + filename);
			ProcessingResult<IcyBufferedImage> result = imageProcessor.loadImage(filename);

			if (result.isFailure()) {
				Logger.error(
						"Failed to load initial background image from: " + filename + " - " + result.getErrorMessage());
			}

			return result;
		} catch (Exception e) {
			Logger.error("Exception loading initial background image", e);
			return ProcessingResult.failure("Exception loading initial background image: " + e.getMessage(), e);
		}
	}

	/**
	 * Calculates the frame range for background processing.
	 */
	private FrameRange calculateFrameRange(Experiment experiment) {
		int firstFrame = options.backgroundFirst;
		int lastFrame = options.backgroundFirst + options.backgroundNFrames;
		int totalFrames = experiment.getSeqCamData().getImageLoader().getNTotalFrames();

		if (firstFrame < 0) {
			firstFrame = 0;
		}
		if (lastFrame > totalFrames) {
			lastFrame = totalFrames;
		}
		if (lastFrame <= firstFrame) {
			lastFrame = Math.min(firstFrame + 1, totalFrames);
		}

		return new FrameRange(firstFrame, lastFrame);
	}

	/**
	 * Processes frames for background building with proper error handling.
	 */
	private ProcessingResult<Void> processFramesForBackground(Experiment experiment,
			ImageTransformOptions transformOptions, FrameRange frameRange) {
		if (transformOptions.backgroundImage == null) {
			return ProcessingResult.failure("Background image is null - cannot process frames");
		}

		if (referenceSequence == null) {
			return ProcessingResult.failure("Reference sequence is null - cannot update background");
		}

		for (int frame = frameRange.getFirst() + 1; frame <= frameRange.getLast() && !stopFlag; frame++) {
			// Update progress
			progressReporter.updateProgress("Processing frame", frame, frameRange.getLast());

			// Load current frame
			ProcessingResult<IcyBufferedImage> imageResult = loadFrame(experiment, frame);
			if (imageResult.isFailure()) {
				String errorMsg = String.format("Failed to load frame %d: %s", frame, imageResult.getErrorMessage());
				Logger.error(errorMsg);
				return ProcessingResult.failure(errorMsg);
			}

			IcyBufferedImage currentImage = imageResult.getData().orElse(null);
			if (currentImage == null) {
				String errorMsg = "Loaded image is null for frame " + frame;
				Logger.error(errorMsg);
				return ProcessingResult.failure(errorMsg);
			}

			// Update data sequence
			dataSequence.setImage(0, 0, currentImage);

			// Transform background
			ProcessingResult<ImageProcessor.BackgroundTransformResult> transformResult = imageProcessor
					.transformBackground(currentImage, transformOptions.backgroundImage, transformOptions);

			if (transformResult.isFailure()) {
				String errorMsg = String.format("Background transformation failed at frame %d: %s", frame,
						transformResult.getErrorMessage());
				Logger.error(errorMsg);
				return ProcessingResult.failure(errorMsg);
			}

			// Update reference sequence
			referenceSequence.setImage(0, 0, transformOptions.backgroundImage);

			// Check convergence
			ImageProcessor.BackgroundTransformResult result = transformResult.getData().orElse(null);
			if (result != null && result.getPixelsChanged() < MINIMUM_PIXELS_CHANGED_THRESHOLD) {
				Logger.info("Background converged at frame " + frame);
				progressReporter.updateMessage("Background converged at frame %d", frame);
				break;
			}
		}

		return ProcessingResult.success();
	}

	/**
	 * Loads a frame with proper error handling.
	 */
	private ProcessingResult<IcyBufferedImage> loadFrame(Experiment experiment, int frameIndex) {
		String filename = experiment.getSeqCamData().getFileNameFromImageList(frameIndex);
		return imageProcessor.loadImage(filename);
	}

	/**
	 * Saves background results with proper error handling.
	 */
	private ProcessingResult<Void> saveBackgroundResults(Experiment experiment) {
		try {
			if (referenceSequence == null || referenceSequence.getFirstImage() == null) {
				String errorMsg = "Cannot save background: reference sequence or image is null";
				Logger.error(errorMsg, null, true);
				saveSuccessful = false;
				return ProcessingResult.failure(errorMsg);
			}

			experiment.getSeqCamData()
					.setReferenceImage(IcyBufferedImageUtil.getCopy(referenceSequence.getFirstImage()));

			String savePath = experiment.getResultsDirectory() + java.io.File.separator + "referenceImage.jpg";
			Logger.info("Saving background image to: " + savePath);

			boolean saveSuccess = experiment.saveReferenceImage(referenceSequence.getFirstImage());
			if (!saveSuccess) {
				String errorMsg = "Failed to save background image to: " + savePath
						+ "\nPlease check file permissions and disk space.";
				Logger.error(errorMsg, null, true);
				saveSuccessful = false;
				return ProcessingResult.failure(errorMsg);
			}

			Logger.info("Background image saved successfully to: " + savePath);
			saveSuccessful = true;

			java.io.File savedFile = new java.io.File(savePath);
			if (!savedFile.exists()) {
				Logger.warn(
						"Background image file not found immediately after save - may need to wait for file system flush");
			}

			return ProcessingResult.success();
		} catch (Exception e) {
			String errorMsg = "Exception while saving background results: " + e.getMessage();
			Logger.error(errorMsg, e, true);
			saveSuccessful = false;
			return ProcessingResult.failure(errorMsg, e);
		}
	}

	/**
	 * Returns whether the background image was successfully saved.
	 */
	public boolean isSaveSuccessful() {
		return saveSuccessful;
	}

	/**
	 * Cleans up resources properly.
	 */
	private void cleanupResources() {
		closeViewer(referenceViewer);
		closeViewer(dataViewer);
		closeSequence(referenceSequence);
		closeSequence(dataSequence);
	}

	/**
	 * Helper class to represent frame range.
	 */
	private static class FrameRange {
		private final int first;
		private final int last;

		public FrameRange(int first, int last) {
			this.first = first;
			this.last = last;
		}

		public int getFirst() {
			return first;
		}

		public int getLast() {
			return last;
		}
	}
}