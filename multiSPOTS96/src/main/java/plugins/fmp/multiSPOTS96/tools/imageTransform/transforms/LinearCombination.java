package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import java.util.logging.Logger;

import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS96.tools.imageTransform.AlgorithmException;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ArrayOperationCache;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformBase;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformConstants;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformException;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;
import plugins.fmp.multiSPOTS96.tools.imageTransform.InvalidParameterException;

/**
 * Enhanced linear combination transform with improved architecture.
 * 
 * <p>Computes a linear combination of RGB channels: result = wR*R + wG*G + wB*B
 * 
 * <p>Key improvements over original implementation:
 * <ul>
 * <li>Uses enhanced base class with validation and error handling</li>
 * <li>Eliminates code duplication through optimized array operations</li>
 * <li>Provides parameter validation and meaningful error messages</li>
 * <li>Uses constants instead of magic numbers</li>
 * <li>Comprehensive documentation and examples</li>
 * </ul>
 * 
 * <p>Usage examples:
 * <pre>
 * // Grayscale conversion using standard weights
 * LinearCombination grayscale = new LinearCombination(0.299, 0.587, 0.114);
 * 
 * // Red channel extraction
 * LinearCombination red = new LinearCombination(1.0, 0.0, 0.0);
 * 
 * // Custom color enhancement
 * LinearCombination enhanced = new LinearCombination(1.2, 0.8, 0.6);
 * </pre>
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public class LinearCombination extends ImageTransformBase {
    
    private static final Logger logger = Logger.getLogger(LinearCombination.class.getName());
    private static final ArrayOperationCache arrayCache = new ArrayOperationCache();
    
    private final double[] weights;
    
    /**
     * Creates a linear combination transform with specified weights.
     * 
     * @param wR Weight for red channel
     * @param wG Weight for green channel
     * @param wB Weight for blue channel
     */
    public LinearCombination(double wR, double wG, double wB) {
        this.weights = new double[]{wR, wG, wB};
    }
    
    /**
     * Creates a linear combination transform with weight array.
     * 
     * @param weights Array of weights [R, G, B]
     * @throws IllegalArgumentException If weights array is invalid
     */
    public LinearCombination(double[] weights) {
        if (weights == null || weights.length != 3) {
            throw new IllegalArgumentException("Weights array must contain exactly 3 values");
        }
        this.weights = weights.clone();
    }
    
    /**
     * Creates a grayscale linear combination using standard weights.
     * 
     * @return LinearCombination configured for grayscale conversion
     */
    public static LinearCombination createGrayscale() {
        return new LinearCombination(ImageTransformConstants.LinearCombination.GRAYSCALE_WEIGHTS);
    }
    
    /**
     * Creates an equal-weight linear combination.
     * 
     * @return LinearCombination with equal weights (1/3, 1/3, 1/3)
     */
    public static LinearCombination createEqualWeight() {
        return new LinearCombination(ImageTransformConstants.LinearCombination.EQUAL_WEIGHTS);
    }
    
    @Override
    protected void validateTransformSpecificParameters(IcyBufferedImage sourceImage, 
                                                      ImageTransformOptions options,
                                                      String transformName) throws ImageTransformException {
        
        // Validate that image has at least 3 channels for RGB operations
        if (sourceImage.getSizeC() < ImageTransformConstants.ColorSpace.RGB_CHANNELS) {
            throw new InvalidParameterException("channels", sourceImage.getSizeC(),
                "Linear combination requires at least 3 channels", transformName);
        }
        
        // Validate weights are reasonable (not all zero)
        boolean allZero = true;
        for (double weight : weights) {
            if (weight != 0.0) {
                allZero = false;
                break;
            }
        }
        
        if (allZero) {
            throw new InvalidParameterException("weights", weights,
                "At least one weight must be non-zero", transformName);
        }
        
        // Warn about potentially problematic weights
        double maxWeight = Math.max(Math.abs(weights[0]), Math.max(Math.abs(weights[1]), Math.abs(weights[2])));
        if (maxWeight > 10.0) {
            logger.warning("Large weight detected: " + maxWeight + ". Results may be out of range.");
        }
    }
    
    @Override
    protected IcyBufferedImage executeTransform(IcyBufferedImage sourceImage, ImageTransformOptions options) 
            throws ImageTransformException {
        
        try {
            // Create result image using base class utility
            IcyBufferedImage resultImage = createResultImage(sourceImage, 
                ImageTransformConstants.ColorSpace.RGB_CHANNELS);
            
            // Get optimized RGB arrays using cached operations
            double[][] rgbArrays = getRGBArraysOptimized(sourceImage);
            
            // Perform optimized linear combination using cached operations
            double[] resultArray = arrayCache.linearCombination(rgbArrays, weights);
            
            // Copy result to image using base class utility
            copyArrayToImage(resultArray, resultImage, options.copyResultsToThe3planes);
            
            return resultImage;
            
        } catch (Exception e) {
            throw new AlgorithmException("Linear combination computation", e.getMessage(), e);
        }
    }
    
    /**
     * Gets the weights used by this linear combination.
     * 
     * @return Copy of the weights array
     */
    public double[] getWeights() {
        return weights.clone();
    }
    
    /**
     * Gets a description of this linear combination.
     * 
     * @return Human-readable description
     */
    public String getDescription() {
        return String.format("Linear combination: %.3f*R + %.3f*G + %.3f*B", 
                           weights[0], weights[1], weights[2]);
    }
    
    @Override
    public String toString() {
        return getDescription();
    }
}
