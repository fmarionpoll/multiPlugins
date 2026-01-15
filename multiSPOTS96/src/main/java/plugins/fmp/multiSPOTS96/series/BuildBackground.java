package plugins.fmp.multiSPOTS96.series;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;

/**
 * Refactored BuildBackground class with improved architecture.
 * Demonstrates proper dependency injection, error handling, and method decomposition.
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
     * Safely analyzes an experiment with proper error handling.
     * Replaces the original analyzeExperiment method with better error handling.
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
        
        if (experiment.seqCamData == null) {
            return ProcessingResult.failure("Experiment must have camera data");
        }
        
        return ProcessingResult.success();
    }
    
    /**
     * Loads experiment data with proper error handling.
     */
    private ProcessingResult<Void> loadExperimentData(Experiment experiment) {
        try {
            boolean loadSuccess = loadSeqCamDataAndCages(experiment);
            if (!loadSuccess) {
                return ProcessingResult.failure("Failed to load DrosoTrack data");
            }
            return ProcessingResult.success();
        } catch (Exception e) {
            return ProcessingResult.failure("Error loading experiment data", e);
        }
    }
    
    /**
     * Validates bounds for cages with proper error handling.
     */
    private ProcessingResult<Void> validateBoundsForCages(Experiment experiment) {
        try {
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
        experiment.cagesArray.initFlyPositions(options.detectCage);
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
        dataSequence = newSequence("data recorded", experiment.seqCamData.getSeqImage(0, 0));
        dataViewer = new ViewerFMP(dataSequence, true, true);
    }
    
    /**
     * Creates the reference sequence viewer.
     */
    private void createReferenceSequence(Experiment experiment) {
        referenceSequence = newSequence("referenceImage", experiment.seqCamData.getReferenceImage());
        experiment.seqReference = referenceSequence;
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
    private ProcessingResult<Void> buildBackgroundImages(Experiment experiment, ImageTransformOptions transformOptions) {
        progressReporter.updateMessage("Building background image...");
        
        try {
            // Load initial background image
            ProcessingResult<IcyBufferedImage> initialBackgroundResult = loadInitialBackgroundImage(experiment);
            if (initialBackgroundResult.isFailure()) {
                return ProcessingResult.failure("Failed to load initial background: " + initialBackgroundResult.getErrorMessage());
            }
            
            transformOptions.backgroundImage = initialBackgroundResult.getData().orElse(null);
            
            // Calculate frame range
            FrameRange frameRange = calculateFrameRange(experiment);
            
            // Process frames
            ProcessingResult<Void> processResult = processFramesForBackground(experiment, transformOptions, frameRange);
            if (processResult.isFailure()) {
                return processResult;
            }
            
            return ProcessingResult.success();
            
        } catch (Exception e) {
            return ProcessingResult.failure("Error building background images", e);
        }
    }
    
    /**
     * Loads the initial background image.
     */
    private ProcessingResult<IcyBufferedImage> loadInitialBackgroundImage(Experiment experiment) {
        String filename = experiment.seqCamData.getFileNameFromImageList(options.backgroundFirst);
        return imageProcessor.loadImage(filename);
    }
    
    /**
     * Calculates the frame range for background processing.
     */
    private FrameRange calculateFrameRange(Experiment experiment) {
        long firstMs = experiment.cagesArray.detectFirst_Ms + 
                      (options.backgroundFirst * experiment.seqCamData.getTimeManager().getBinImage_ms());
        int firstFrame = (int) ((firstMs - experiment.cagesArray.detectFirst_Ms) / 
                               experiment.seqCamData.getTimeManager().getBinImage_ms());
        
        int lastFrame = options.backgroundFirst + options.backgroundNFrames;
        int totalFrames = experiment.seqCamData.getImageLoader().getNTotalFrames();
        
        if (lastFrame > totalFrames) {
            lastFrame = totalFrames;
        }
        
        return new FrameRange(firstFrame, lastFrame);
    }
    
    /**
     * Processes frames for background building with proper error handling.
     */
    private ProcessingResult<Void> processFramesForBackground(Experiment experiment, ImageTransformOptions transformOptions, FrameRange frameRange) {
        for (int frame = frameRange.getFirst() + 1; frame <= frameRange.getLast() && !stopFlag; frame++) {
            // Update progress
            progressReporter.updateProgress("Processing frame", frame, frameRange.getLast());
            
            // Load current frame
            ProcessingResult<IcyBufferedImage> imageResult = loadFrame(experiment, frame);
            if (imageResult.isFailure()) {
                return ProcessingResult.failure("Failed to load frame %d: %s", frame, imageResult.getErrorMessage());
            }
            
            IcyBufferedImage currentImage = imageResult.getData().orElse(null);
            
            // Update data sequence
            dataSequence.setImage(0, 0, currentImage);
            
            // Transform background
            ProcessingResult<ImageProcessor.BackgroundTransformResult> transformResult = 
                imageProcessor.transformBackground(currentImage, transformOptions.backgroundImage, transformOptions);
            
            if (transformResult.isFailure()) {
                return ProcessingResult.failure("Background transformation failed at frame %d: %s", 
                                               frame, transformResult.getErrorMessage());
            }
            
            // Update reference sequence
            referenceSequence.setImage(0, 0, transformOptions.backgroundImage);
            
            // Check convergence
            ImageProcessor.BackgroundTransformResult result = transformResult.getData().orElse(null);
            if (result != null && result.getPixelsChanged() < MINIMUM_PIXELS_CHANGED_THRESHOLD) {
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
        String filename = experiment.seqCamData.getFileNameFromImageList(frameIndex);
        return imageProcessor.loadImage(filename);
    }
    
    /**
     * Saves background results with proper error handling.
     */
    private ProcessingResult<Void> saveBackgroundResults(Experiment experiment) {
        try {
            experiment.seqCamData.setReferenceImage(IcyBufferedImageUtil.getCopy(referenceSequence.getFirstImage()));
            experiment.saveReferenceImage(referenceSequence.getFirstImage());
            return ProcessingResult.success();
        } catch (Exception e) {
            return ProcessingResult.failure("Failed to save background results", e);
        }
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
        
        public int getFirst() { return first; }
        public int getLast() { return last; }
    }
}