package plugins.fmp.multiSPOTS96.series;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;

/**
 * Memory pool for reusing image objects with identical dimensions.
 * 
 * This class implements a memory pooling strategy to reuse image memory chunks
 * across all images in a stack, since all images have identical dimensions and
 * data types.
 * 
 * The pool is initialized with the first image encountered during processing,
 * ensuring compatibility with any image format (RGB, grayscale, etc.).
 * 
 * Benefits: - Reduces memory allocation/deallocation overhead - Improves
 * garbage collection performance - Reduces memory fragmentation - Provides
 * predictable memory usage patterns - Adapts to any image format automatically
 */
public class ImageMemoryPool {

	// === POOL CONFIGURATION ===
	private static final int DEFAULT_POOL_SIZE = 10; // Number of images to keep in pool
	private static final int MAX_POOL_SIZE = 50; // Maximum pool size to prevent memory bloat
	private static final long POOL_CLEANUP_INTERVAL_MS = 30000; // 30 seconds

	// === MEMORY POOLS ===
	private final ConcurrentLinkedQueue<IcyBufferedImage> imagePool = new ConcurrentLinkedQueue<>();

	// === POOL STATISTICS ===
	private final AtomicInteger totalImagesCreated = new AtomicInteger(0);
	private final AtomicInteger totalImagesReused = new AtomicInteger(0);
	private final AtomicLong totalMemorySaved = new AtomicLong(0);

	// === POOL STATE ===
	private int imageWidth = -1;
	private int imageHeight = -1;
	private int imageChannels = -1;
	private icy.type.DataType dataType = null;
	private volatile boolean poolEnabled = true;
	private volatile long lastCleanupTime = System.currentTimeMillis();
	private volatile boolean initialized = false;

	/**
	 * Creates a new image memory pool. The pool will be initialized with the first
	 * image encountered during processing.
	 */
	public ImageMemoryPool() {
		// Don't pre-populate pool - initialize lazily when first needed
		// This avoids issues with Icy framework initialization
	}

	/**
	 * Initializes the pool with the format of the provided image. This method
	 * should be called with the first image to establish the format.
	 * 
	 * @param templateImage the image to use as a template for the pool
	 */
	public void initializeWithImage(IcyBufferedImage templateImage) {
		if (templateImage == null) {
			System.err.println("ERROR: Cannot initialize pool with null image");
			return;
		}

		this.imageWidth = templateImage.getSizeX();
		this.imageHeight = templateImage.getSizeY();
		this.imageChannels = templateImage.getSizeC();
		this.dataType = templateImage.getDataType_();
		this.initialized = true;

//		System.out.println("DEBUG: Image memory pool initialized with format: " + imageWidth + "x" + imageHeight + "x"
//				+ imageChannels + " (" + dataType + ")");
	}

	/**
	 * Initializes the pool with default number of images. Note: This method is not
	 * called during construction to avoid Icy framework initialization issues.
	 */
	private void initializePool() {
//		System.out.println(
//				"DEBUG: Initializing image memory pool for " + imageWidth + "x" + imageHeight + "x" + imageChannels);
//		int successfulImages = 0;
		for (int i = 0; i < DEFAULT_POOL_SIZE; i++) {
			IcyBufferedImage pooledImage = createNewImage();
			if (pooledImage != null) {
				imagePool.offer(pooledImage);
				totalImagesCreated.incrementAndGet();
//				successfulImages++;
			}
		}
//		System.out.println("DEBUG: Successfully created " + successfulImages + " pooled images out of "
//				+ DEFAULT_POOL_SIZE + " requested");
	}

	/**
	 * Gets an image from the pool or creates a new one.
	 * 
	 * @return an IcyBufferedImage ready for use
	 */
	public IcyBufferedImage getImage() {
		if (!poolEnabled || !initialized) {
			return null; // Pool not ready
		}

		IcyBufferedImage image = imagePool.poll();
		if (image != null) {
			totalImagesReused.incrementAndGet();
			// Reset image data to ensure clean state
			resetImage(image);
			return image;
		} else {
			// Pool is empty, create new image on-demand
			IcyBufferedImage newImage = createNewImage();
			if (newImage != null) {
				totalImagesCreated.incrementAndGet();
//				System.out.println("DEBUG: Created new image on-demand (pool was empty)");
			}
			return newImage;
		}
	}

	/**
	 * Returns an image to the pool for reuse.
	 * 
	 * @param image the image to return to the pool
	 */
	public void returnImage(IcyBufferedImage image) {
		if (!poolEnabled || image == null) {
			return;
		}

		// Check if pool is full
		if (imagePool.size() < MAX_POOL_SIZE) {
			// Clear image data before returning to pool
			clearImageData(image);
			imagePool.offer(image);
		}
		// If pool is full, let the image be garbage collected
	}

	/**
	 * Creates a new cursor for the given image. Note: Cursors cannot be pooled as
	 * they are tied to specific images.
	 * 
	 * @param image the image to create cursor for
	 * @return an IcyBufferedImageCursor ready for use
	 */
	public IcyBufferedImageCursor createCursor(IcyBufferedImage image) {
		if (image == null) {
			return null;
		}
		return new IcyBufferedImageCursor(image);
	}

	/**
	 * Creates a new image with the pool's dimensions.
	 * 
	 * @return a new IcyBufferedImage
	 */
	private IcyBufferedImage createNewImage() {
		if (!initialized) {
			System.err.println("ERROR: Cannot create image - pool not initialized");
			return null;
		}

		try {
//			System.out.println("DEBUG: Attempting to create image " + imageWidth + "x" + imageHeight + "x"
//					+ imageChannels + " (" + dataType + ")");
			IcyBufferedImage image = new IcyBufferedImage(imageWidth, imageHeight, imageChannels, dataType);
//			System.out.println("DEBUG: Successfully created image");
			return image;
		} catch (Exception e) {
			System.err.println("ERROR creating pooled image: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Resets an image to a clean state.
	 * 
	 * @param image the image to reset
	 */
	private void resetImage(IcyBufferedImage image) {
		if (image != null) {
			try {
				// Clear all pixel data
				image.setDataXY(0, null);
				// Reset any other image state as needed
			} catch (Exception e) {
				// Ignore reset errors
			}
		}
	}

	/**
	 * Clears image data before returning to pool.
	 * 
	 * @param image the image to clear
	 */
	private void clearImageData(IcyBufferedImage image) {
		if (image != null) {
			try {
				// Clear pixel data to free memory
				image.setDataXY(0, null);
			} catch (Exception e) {
				// Ignore clear errors
			}
		}
	}

	/**
	 * Performs periodic pool cleanup to prevent memory bloat.
	 */
	public void performPeriodicCleanup() {
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastCleanupTime > POOL_CLEANUP_INTERVAL_MS) {
			cleanupPool();
			lastCleanupTime = currentTime;
		}
	}

	/**
	 * Cleans up the pool by reducing its size.
	 */
	private void cleanupPool() {
		// Reduce pool size to prevent memory bloat
		int targetSize = Math.max(DEFAULT_POOL_SIZE / 2, 5);

		while (imagePool.size() > targetSize) {
			IcyBufferedImage image = imagePool.poll();
			if (image != null) {
				clearImageData(image);
				// Let it be garbage collected
			}
		}
	}

	/**
	 * Clears all pooled objects.
	 */
	public void clearPool() {
		// Clear image pool
		IcyBufferedImage image;
		while ((image = imagePool.poll()) != null) {
			clearImageData(image);
		}
	}

	/**
	 * Enables or disables the memory pool.
	 * 
	 * @param enabled true to enable pooling, false to disable
	 */
	public void setPoolEnabled(boolean enabled) {
		this.poolEnabled = enabled;
		if (!enabled) {
			clearPool();
		}
	}

	/**
	 * Gets pool statistics.
	 * 
	 * @return a string with pool statistics
	 */
	public String getPoolStatistics() {
		if (!initialized) {
			return "Image Pool: Not initialized yet";
		}

		return String.format("Image Pool: %d/%d images, %d created, %d reused", imagePool.size(), MAX_POOL_SIZE,
				totalImagesCreated.get(), totalImagesReused.get());
	}

	/**
	 * Gets memory usage statistics.
	 * 
	 * @return memory usage information
	 */
	public String getMemoryStatistics() {
		if (!initialized) {
			return "Pool Memory: Not initialized yet";
		}

		// Calculate bytes per pixel based on data type
		int bytesPerPixel = 1; // Default for UINT8
		if (dataType != null) {
			switch (dataType.toString()) {
			case "UINT16":
				bytesPerPixel = 2;
				break;
			case "FLOAT":
			case "INT32":
				bytesPerPixel = 4;
				break;
			case "DOUBLE":
				bytesPerPixel = 8;
				break;
			default:
				bytesPerPixel = 1; // UINT8 and others
			}
		}

		long imageMemory = (long) imageWidth * imageHeight * imageChannels * bytesPerPixel;
		long totalPooledMemory = imageMemory * imagePool.size();

		return String.format("Pool Memory: %d images × %d bytes = %d MB | " + "Estimated Memory Saved: %d MB",
				imagePool.size(), imageMemory, totalPooledMemory / 1024 / 1024, totalMemorySaved.get() / 1024 / 1024);
	}

	/**
	 * Gets the current pool size.
	 * 
	 * @return number of images in the pool
	 */
	public int getPoolSize() {
		return imagePool.size();
	}

}