package plugins.fmp.multiSPOTS96.tools.imageTransform;

import java.util.logging.Logger;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;

/**
 * Enhanced abstract base class for image transformations.
 * Provides comprehensive common functionality including validation, optimization,
 * error handling, and performance improvements.
 * 
 * <p>This class eliminates the massive code duplication present in the original
 * implementation by providing shared utilities for:
 * <ul>
 * <li>Input validation and parameter checking</li>
 * <li>Optimized array access and conversion</li>
 * <li>Common image creation patterns</li>
 * <li>Structured error handling and logging</li>
 * <li>Performance optimization through caching</li>
 * </ul>
 * 
 * @author MultiSPOTS96 Team
 * @version 2.0
 */
public abstract class ImageTransformBase implements ImageTransformInterface {
    
    private static final Logger logger = Logger.getLogger(ImageTransformBase.class.getName());
    
    // Static cache for array operations to improve performance
    private static final ArrayOperationCache arrayCache = new ArrayOperationCache();
    
    @Override
    public final IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
        String transformName = getClass().getSimpleName();
        
        try {
            // Comprehensive input validation
            validateInputs(sourceImage, options, transformName);
            
            // Execute the transformation with error handling
            return executeTransformSafely(sourceImage, options, transformName);
            
        } catch (ImageTransformException e) {
            logger.severe("Transform failed: " + e.getMessage());
            return handleTransformError(e, sourceImage);
        } catch (Exception e) {
            logger.severe("Unexpected error in transform " + transformName + ": " + e.getMessage());
            ImageTransformException transformException = new ImageTransformException(
                "Unexpected error during transformation", e, transformName, "Transform execution");
            return handleTransformError(transformException, sourceImage);
        }
    }
    
    /**
     * Executes the specific transformation algorithm.
     * This method must be implemented by concrete transform classes.
     * 
     * @param sourceImage The source image to transform
     * @param options The transformation options
     * @return The transformed image
     * @throws ImageTransformException If the transformation fails
     */
    protected abstract IcyBufferedImage executeTransform(IcyBufferedImage sourceImage, ImageTransformOptions options) 
            throws ImageTransformException;
    
    /**
     * Validates input parameters comprehensively.
     * 
     * @param sourceImage The source image to validate
     * @param options The options to validate
     * @param transformName The name of the transform for error context
     * @throws ImageTransformException If validation fails
     */
    protected void validateInputs(IcyBufferedImage sourceImage, ImageTransformOptions options, String transformName) 
            throws ImageTransformException {
        
        // Null checks
        if (sourceImage == null) {
            throw new ImageTransformException(ImageTransformConstants.ErrorMessages.NULL_SOURCE_IMAGE, 
                                            transformName, "Input validation");
        }
        
        if (options == null) {
            throw new ImageTransformException(ImageTransformConstants.ErrorMessages.NULL_OPTIONS,
                                            transformName, "Input validation");
        }
        
        // Image dimension validation
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        
        if (width < ImageTransformConstants.Validation.MIN_IMAGE_WIDTH || 
            height < ImageTransformConstants.Validation.MIN_IMAGE_HEIGHT) {
            throw new IncompatibleImageException(
                String.format("Image size %dx%d", width, height),
                "Dimensions too small", transformName);
        }
        
        if (width > ImageTransformConstants.Validation.MAX_IMAGE_DIMENSION || 
            height > ImageTransformConstants.Validation.MAX_IMAGE_DIMENSION) {
            throw new IncompatibleImageException(
                String.format("Image size %dx%d", width, height),
                "Dimensions too large", transformName);
        }
        
        // Channel validation
        int channels = sourceImage.getSizeC();
        if (channels > ImageTransformConstants.Validation.MAX_CHANNELS) {
            throw new IncompatibleImageException(
                String.format("%d channels", channels),
                "Too many channels", transformName);
        }
        
        // Additional transform-specific validation
        validateTransformSpecificParameters(sourceImage, options, transformName);
    }
    
    /**
     * Validates transform-specific parameters.
     * Override this method to add custom validation logic.
     * 
     * @param sourceImage The source image
     * @param options The transformation options
     * @param transformName The transform name for error context
     * @throws ImageTransformException If validation fails
     */
    protected void validateTransformSpecificParameters(IcyBufferedImage sourceImage, 
                                                      ImageTransformOptions options, 
                                                      String transformName) throws ImageTransformException {
        // Default implementation - no additional validation
        // Override in subclasses for specific validation needs
    }
    
    /**
     * Safely executes the transformation with comprehensive error handling.
     */
    private IcyBufferedImage executeTransformSafely(IcyBufferedImage sourceImage, 
                                                   ImageTransformOptions options, 
                                                   String transformName) throws ImageTransformException {
        
        try {
            // Pre-processing hook
            preprocessImage(sourceImage, options);
            
            // Execute the actual transformation
            IcyBufferedImage result = executeTransform(sourceImage, options);
            
            // Post-processing hook
            result = postprocessImage(result, options);
            
            // Validate result
            if (result == null) {
                throw new AlgorithmException("Transform execution", "Null result returned", transformName);
            }
            
            return result;
            
        } catch (ImageTransformException e) {
            // Re-throw transform exceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions
            throw new AlgorithmException("Transform execution", e.getMessage(), e);
        }
    }
    
    /**
     * Pre-processing hook called before transformation.
     * Override to add custom pre-processing logic.
     * 
     * @param sourceImage The source image
     * @param options The transformation options
     */
    protected void preprocessImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
        // Default implementation - no pre-processing
    }
    
    /**
     * Post-processing hook called after transformation.
     * Override to add custom post-processing logic.
     * 
     * @param result The transformed image
     * @param options The transformation options
     * @return The post-processed image (may be the same as input)
     */
    protected IcyBufferedImage postprocessImage(IcyBufferedImage result, ImageTransformOptions options) {
        // Default implementation - no post-processing
        return result;
    }
    
    /**
     * Gets RGB arrays from source image with optimization and caching.
     * This method eliminates the code duplication present in 20+ transform classes.
     * 
     * @param sourceImage The source image
     * @return Array containing R, G, B double arrays
     */
    protected double[][] getRGBArraysOptimized(IcyBufferedImage sourceImage) {
        return arrayCache.getRGBArrays(sourceImage);
    }
    
    /**
     * Creates a result image with proper dimensions and data type.
     * 
     * @param sourceImage The source image for reference
     * @param channels Number of channels in result image
     * @return A new properly configured result image
     */
    protected IcyBufferedImage createResultImage(IcyBufferedImage sourceImage, int channels) {
        return new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 
                                   channels, sourceImage.getDataType_());
    }
    
    /**
     * Creates a result image with specific data type.
     * 
     * @param sourceImage The source image for reference
     * @param channels Number of channels in result image
     * @param dataType The desired data type
     * @return A new properly configured result image
     */
    protected IcyBufferedImage createResultImage(IcyBufferedImage sourceImage, int channels, DataType dataType) {
        return new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), channels, dataType);
    }
    
    /**
     * Copies result array to image with proper channel handling.
     * Eliminates duplication of the copyExGDoubleToIcyBufferedImage pattern.
     * 
     * @param resultArray The computed result array
     * @param resultImage The target image
     * @param copyToAllChannels Whether to copy to all 3 channels
     */
    protected void copyArrayToImage(double[] resultArray, IcyBufferedImage resultImage, boolean copyToAllChannels) {
        Array1DUtil.doubleArrayToSafeArray(resultArray, resultImage.getDataXY(0), false);
        resultImage.setDataXY(0, resultImage.getDataXY(0));
        
        if (copyToAllChannels && resultImage.getSizeC() >= 3) {
            for (int c = 1; c < 3; c++) {
                resultImage.copyData(resultImage, 0, c);
                resultImage.setDataXY(c, resultImage.getDataXY(c));
            }
        }
    }
    
    /**
     * Copies result array to image (int version).
     * 
     * @param resultArray The computed result array
     * @param resultImage The target image
     * @param copyToAllChannels Whether to copy to all 3 channels
     */
    protected void copyArrayToImage(int[] resultArray, IcyBufferedImage resultImage, boolean copyToAllChannels) {
        Array1DUtil.intArrayToSafeArray(resultArray, resultImage.getDataXY(0), false, false);
        resultImage.setDataXY(0, resultImage.getDataXY(0));
        
        if (copyToAllChannels && resultImage.getSizeC() >= 3) {
            for (int c = 1; c < 3; c++) {
                resultImage.copyData(resultImage, 0, c);
                resultImage.setDataXY(c, resultImage.getDataXY(c));
            }
        }
    }
    
    /**
     * Handles transformation errors gracefully.
     * 
     * @param error The transformation error
     * @param fallbackImage The fallback image to return
     * @return The fallback image or null if no fallback is appropriate
     */
    protected IcyBufferedImage handleTransformError(ImageTransformException error, IcyBufferedImage fallbackImage) {
        logger.warning("Transform error handled: " + error.getMessage());
        
        // For now, return null to indicate failure
        // In a production system, you might want to return the original image
        // or a processed error indicator image
        return null;
    }
    
    /**
     * Checks if two images are compatible for operations.
     * 
     * @param image1 First image
     * @param image2 Second image
     * @return true if images are compatible
     */
    protected boolean areImagesCompatible(IcyBufferedImage image1, IcyBufferedImage image2) {
        if (image1 == null || image2 == null) {
            return false;
        }
        
        return image1.getWidth() == image2.getWidth() &&
               image1.getHeight() == image2.getHeight() &&
               image1.getSizeC() == image2.getSizeC();
    }
    
    /**
     * Validates that an array index is within bounds.
     * 
     * @param index The index to check
     * @param arrayLength The array length
     * @param parameterName The parameter name for error reporting
     * @throws InvalidParameterException If index is out of bounds
     */
    protected void validateArrayIndex(int index, int arrayLength, String parameterName) 
            throws InvalidParameterException {
        if (index < 0 || index >= arrayLength) {
            throw new InvalidParameterException(parameterName, index, 
                String.format("Index must be between 0 and %d", arrayLength - 1));
        }
    }
    
    /**
     * Validates that a parameter is within a specified range.
     * 
     * @param value The value to check
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @param parameterName The parameter name for error reporting
     * @throws InvalidParameterException If value is out of range
     */
    protected void validateParameterRange(double value, double min, double max, String parameterName) 
            throws InvalidParameterException {
        if (value < min || value > max) {
            throw new InvalidParameterException(parameterName, value, 
                String.format("Value must be between %f and %f", min, max));
        }
    }
    
    /**
     * Gets the transform name for logging and error reporting.
     * 
     * @return The simple class name of the transform
     */
    protected String getTransformName() {
        return getClass().getSimpleName();
    }
} 