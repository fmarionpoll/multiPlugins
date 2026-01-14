package plugins.fmp.multiSPOTS96.tools.imageTransform;

/**
 * Exception thrown when an algorithm fails during execution.
 */
public class AlgorithmException extends ImageTransformException {
    
    private static final long serialVersionUID = 1L;
    
    private final String algorithmStep;
    
    public AlgorithmException(String algorithmStep, String message) {
        super(String.format("Algorithm failure in step '%s': %s", algorithmStep, message));
        this.algorithmStep = algorithmStep;
    }
    
    public AlgorithmException(String algorithmStep, String message, Throwable cause) {
        super(String.format("Algorithm failure in step '%s': %s", algorithmStep, message), cause);
        this.algorithmStep = algorithmStep;
    }
    
    public AlgorithmException(String algorithmStep, String message, String transformName) {
        super(String.format("Algorithm failure in step '%s': %s", algorithmStep, message),
              transformName, "Algorithm execution");
        this.algorithmStep = algorithmStep;
    }
    
    public String getAlgorithmStep() {
        return algorithmStep;
    }
} 