package plugins.fmp.multiSPOTS96.series;

import java.util.logging.Logger;

import plugins.fmp.multiSPOTS96.experiment.Experiment;

/**
 * Refactored Registration class following clean code principles.
 * 
 * This class demonstrates the improved architecture by:
 * 1. Using dependency injection
 * 2. Proper error handling with ProcessingResult
 * 3. Separation of concerns
 * 4. Clear method names and responsibilities
 * 5. Configuration through RegistrationOptions
 * 6. Proper logging instead of System.out.println
 */
public class RegistrationRefactored extends BuildSeries {
    
    private static final Logger LOGGER = Logger.getLogger(RegistrationRefactored.class.getName());
    
    private final RegistrationProcessor registrationProcessor;
    private final ProgressReporter progressReporter;
    
    /**
     * Constructor with dependency injection for better testability.
     */
    public RegistrationRefactored() {
        this(new SafeRegistrationProcessor(), ProgressReporter.NO_OP);
    }
    
    public RegistrationRefactored(RegistrationProcessor registrationProcessor, ProgressReporter progressReporter) {
        this.registrationProcessor = registrationProcessor;
        this.progressReporter = progressReporter;
    }
    
    @Override
    void analyzeExperiment(Experiment experiment) {
        LOGGER.info("Starting registration analysis for experiment: " + experiment.getResultsDirectory());
        
        // Create registration options from current BuildSeriesOptions
        RegistrationOptions options = createRegistrationOptions();
        
        // Perform registration with proper error handling
        ProcessingResult<RegistrationProcessor.RegistrationResult> result = 
            registrationProcessor.correctDriftAndRotation(experiment, options);
        
        if (result.isFailure()) {
            String errorMessage = "Registration analysis failed: " + result.getErrorMessage();
            LOGGER.severe(errorMessage);
            progressReporter.failed(errorMessage);
        } else {
            RegistrationProcessor.RegistrationResult registrationResult = result.getDataOrThrow();
            LOGGER.info("Registration completed successfully. Processed: " + 
                       registrationResult.getFramesProcessed() + " frames, Corrected: " + 
                       registrationResult.getFramesCorrected() + " frames");
            progressReporter.completed();
        }
    }
    
    /**
     * Creates RegistrationOptions from the current BuildSeriesOptions.
     * This method encapsulates the configuration logic.
     */
    private RegistrationOptions createRegistrationOptions() {
        return new RegistrationOptions()
            .fromFrame(options.fromFrame)
            .toFrame(options.toFrame)
            .referenceFrame(options.referenceFrame)
            .translationThreshold(0.001)  // Extracted constant
            .rotationThreshold(0.001)     // Extracted constant
            .transformOptions(createImageTransformOptions(options.transformop))
            .saveCorrectedImages(true)
            .preserveImageSize(true)
            .referenceChannel(0)
            .progressReporter(progressReporter);
    }
    
    /**
     * Alternative method for drift-only correction.
     */
    public ProcessingResult<RegistrationProcessor.RegistrationResult> correctDriftOnly(Experiment experiment) {
        RegistrationOptions options = createRegistrationOptions();
        return registrationProcessor.correctDrift(experiment, options);
    }
    
    /**
     * Alternative method for rotation-only correction.
     */
    public ProcessingResult<RegistrationProcessor.RegistrationResult> correctRotationOnly(Experiment experiment) {
        RegistrationOptions options = createRegistrationOptions();
        return registrationProcessor.correctRotation(experiment, options);
    }
    
    /**
     * Method to get registration statistics without performing corrections.
     */
    public ProcessingResult<RegistrationProcessor.RegistrationResult> analyzeRegistration(Experiment experiment) {
        RegistrationOptions options = createRegistrationOptions().saveCorrectedImages(false);
        return registrationProcessor.correctDriftAndRotation(experiment, options);
    }
    
    /**
     * Creates ImageTransformOptions from ImageTransformEnums.
     */
    private plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions createImageTransformOptions(
            plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums transformEnum) {
        plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions options = 
            new plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions();
        options.transformOption = transformEnum;
        return options;
    }
} 