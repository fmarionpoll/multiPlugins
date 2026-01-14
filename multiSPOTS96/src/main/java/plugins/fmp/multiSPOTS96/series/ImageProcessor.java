package plugins.fmp.multiSPOTS96.series;

import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

/**
 * Service interface for image processing operations.
 * Provides abstraction for different image processing implementations.
 */
public interface ImageProcessor {
    
    /**
     * Loads an image from the specified filename.
     * @param filename The path to the image file
     * @return ProcessingResult containing the loaded image or error information
     */
    ProcessingResult<IcyBufferedImage> loadImage(String filename);
    
    /**
     * Saves an image to the specified filename.
     * @param image The image to save
     * @param filename The path where to save the image
     * @return ProcessingResult indicating success or failure
     */
    ProcessingResult<Void> saveImage(IcyBufferedImage image, String filename);
    
    /**
     * Applies a transformation to the source image.
     * @param sourceImage The source image to transform
     * @param options The transformation options
     * @return ProcessingResult containing the transformed image or error information
     */
    ProcessingResult<IcyBufferedImage> transformImage(IcyBufferedImage sourceImage, ImageTransformOptions options);
    
    /**
     * Transforms background image using specific algorithm.
     * @param sourceImage The source image
     * @param backgroundImage The background image to update
     * @param options The transformation options
     * @return ProcessingResult containing transformation statistics
     */
    ProcessingResult<BackgroundTransformResult> transformBackground(
        IcyBufferedImage sourceImage, 
        IcyBufferedImage backgroundImage, 
        ImageTransformOptions options
    );
    
    /**
     * Creates a binary mask from the image using threshold.
     * @param image The source image
     * @param threshold The threshold value
     * @param trackWhite Whether to track white pixels
     * @param videoChannel The video channel to use
     * @return ProcessingResult containing the binary mask
     */
    ProcessingResult<boolean[]> createBinaryMask(IcyBufferedImage image, int threshold, boolean trackWhite, int videoChannel);
    
    /**
     * Result of background transformation containing statistics.
     */
    public static class BackgroundTransformResult {
        private final int pixelsChanged;
        private final IcyBufferedImage transformedBackground;
        
        public BackgroundTransformResult(int pixelsChanged, IcyBufferedImage transformedBackground) {
            this.pixelsChanged = pixelsChanged;
            this.transformedBackground = transformedBackground;
        }
        
        public int getPixelsChanged() { return pixelsChanged; }
        public IcyBufferedImage getTransformedBackground() { return transformedBackground; }
    }
} 