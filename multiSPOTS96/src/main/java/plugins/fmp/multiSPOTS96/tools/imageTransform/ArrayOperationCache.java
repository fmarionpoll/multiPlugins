package plugins.fmp.multiSPOTS96.tools.imageTransform;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;

/**
 * Performance optimization cache for image array operations.
 * Eliminates the massive code duplication in array conversions
 * that exists across 20+ transform implementations.
 * 
 * <p>This cache provides:
 * <ul>
 * <li>Automatic caching of RGB array conversions</li>
 * <li>Thread-safe operation for concurrent transforms</li>
 * <li>Memory management with size limits</li>
 * <li>Optimized array operations</li>
 * </ul>
 * 
 * @author MultiSPOTS96 Team
 * @version 1.0
 */
public class ArrayOperationCache {
    
    private final Map<String, CachedArrays> cache = new ConcurrentHashMap<>();
    private final int maxCacheSize;
    
    /**
     * Creates a new ArrayOperationCache with default size limit.
     */
    public ArrayOperationCache() {
        this(ImageTransformConstants.Performance.ARRAY_CACHE_SIZE);
    }
    
    /**
     * Creates a new ArrayOperationCache with specified size limit.
     * 
     * @param maxCacheSize Maximum number of cached arrays
     */
    public ArrayOperationCache(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }
    
    /**
     * Gets RGB arrays for an image, with caching for performance.
     * This method replaces the repeated code pattern found in 20+ transform classes.
     * 
     * @param sourceImage The source image
     * @return Array containing [R, G, B] double arrays
     */
    public double[][] getRGBArrays(IcyBufferedImage sourceImage) {
        if (sourceImage == null) {
            return null;
        }
        
        // Generate cache key based on image properties
        String cacheKey = generateCacheKey(sourceImage);
        
        // Check if image is cacheable (not too large)
        boolean isCacheable = isCacheable(sourceImage);
        
        if (isCacheable) {
            CachedArrays cached = cache.get(cacheKey);
            if (cached != null && cached.isValid(sourceImage)) {
                return cached.getRGBArrays();
            }
        }
        
        // Extract arrays from image
        double[][] rgbArrays = extractRGBArrays(sourceImage);
        
        // Cache if appropriate
        if (isCacheable && cache.size() < maxCacheSize) {
            cache.put(cacheKey, new CachedArrays(sourceImage, rgbArrays));
        }
        
        return rgbArrays;
    }
    
    /**
     * Performs optimized linear combination of RGB channels.
     * Replaces the repeated manual loops found in multiple transform classes.
     * 
     * @param rgbArrays The RGB arrays [R, G, B]
     * @param weights The combination weights [wR, wG, wB]
     * @return The combined result array
     */
    public double[] linearCombination(double[][] rgbArrays, double[] weights) {
        if (rgbArrays == null || rgbArrays.length < 3 || weights == null || weights.length < 3) {
            throw new IllegalArgumentException("Invalid RGB arrays or weights");
        }
        
        double[] r = rgbArrays[0];
        double[] g = rgbArrays[1];
        double[] b = rgbArrays[2];
        double[] result = new double[r.length];
        
        double wR = weights[0];
        double wG = weights[1];
        double wB = weights[2];
        
        // Optimized loop for linear combination
        for (int i = 0; i < result.length; i++) {
            result[i] = r[i] * wR + g[i] * wG + b[i] * wB;
        }
        
        return result;
    }
    
    /**
     * Performs optimized difference calculation between arrays.
     * 
     * @param array1 First array
     * @param array2 Second array
     * @return Difference array
     */
    public double[] arrayDifference(double[] array1, double[] array2) {
        if (array1 == null || array2 == null || array1.length != array2.length) {
            throw new IllegalArgumentException("Arrays must be non-null and same length");
        }
        
        double[] result = new double[array1.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = array1[i] - array2[i];
        }
        
        return result;
    }
    
    /**
     * Performs optimized absolute difference calculation.
     * 
     * @param array1 First array
     * @param array2 Second array
     * @return Absolute difference array
     */
    public double[] arrayAbsoluteDifference(double[] array1, double[] array2) {
        if (array1 == null || array2 == null || array1.length != array2.length) {
            throw new IllegalArgumentException("Arrays must be non-null and same length");
        }
        
        double[] result = new double[array1.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.abs(array1[i] - array2[i]);
        }
        
        return result;
    }
    
    /**
     * Clears the cache to free memory.
     */
    public void clearCache() {
        cache.clear();
    }
    
    /**
     * Gets the current cache size.
     * 
     * @return Number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Generates a cache key for an image.
     */
    private String generateCacheKey(IcyBufferedImage image) {
        return String.format("img_%d_%d_%d_%d", 
                           image.getWidth(), image.getHeight(), 
                           image.getSizeC(), System.identityHashCode(image));
    }
    
    /**
     * Determines if an image should be cached based on size.
     */
    private boolean isCacheable(IcyBufferedImage image) {
        int pixelCount = image.getWidth() * image.getHeight();
        return pixelCount <= ImageTransformConstants.Performance.MAX_CACHEABLE_IMAGE_SIZE;
    }
    
    /**
     * Extracts RGB arrays from an image.
     * This centralizes the array extraction logic used throughout the transform classes.
     */
    private double[][] extractRGBArrays(IcyBufferedImage sourceImage) {
        int channels = Math.min(3, sourceImage.getSizeC());
        double[][] arrays = new double[3][];
        
        // Extract available channels
        for (int c = 0; c < channels; c++) {
            arrays[c] = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
        }
        
        // Fill missing channels with zeros if image has fewer than 3 channels
        for (int c = channels; c < 3; c++) {
            arrays[c] = new double[sourceImage.getWidth() * sourceImage.getHeight()];
        }
        
        return arrays;
    }
    
    /**
     * Internal class to hold cached RGB arrays with validation.
     */
    private static class CachedArrays {
        private final double[][] rgbArrays;
        private final long timestamp;
        private final int width;
        private final int height;
        private final int channels;
        
        public CachedArrays(IcyBufferedImage sourceImage, double[][] rgbArrays) {
            this.rgbArrays = rgbArrays;
            this.timestamp = System.currentTimeMillis();
            this.width = sourceImage.getWidth();
            this.height = sourceImage.getHeight();
            this.channels = sourceImage.getSizeC();
        }
        
        public double[][] getRGBArrays() {
            return rgbArrays;
        }
        
        public boolean isValid(IcyBufferedImage sourceImage) {
            return sourceImage.getWidth() == width &&
                   sourceImage.getHeight() == height &&
                   sourceImage.getSizeC() == channels;
        }
    }
} 