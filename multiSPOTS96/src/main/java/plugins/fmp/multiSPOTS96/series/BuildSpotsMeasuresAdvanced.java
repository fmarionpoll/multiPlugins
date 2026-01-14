package plugins.fmp.multiSPOTS96.series;

import java.awt.Point;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.swing.SwingUtilities;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.sequence.Sequence;
import icy.system.SystemUtil;
import icy.system.thread.Processor;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.sequence.SequenceCamData;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.ViewerFMP;
import plugins.fmp.multiSPOTS96.tools.ROI2D.ProcessingException;
import plugins.fmp.multiSPOTS96.tools.ROI2D.ValidationException;
import plugins.fmp.multiSPOTS96.tools.ROI2D.ROI2DWithMask;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

/**
 * Advanced optimized version of BuildSpotsMeasures with streaming processing,
 * compressed mask storage, and memory pool optimizations.
 * 
 * Features: - Streaming image processing to avoid loading entire stack -
 * Compressed mask storage using run-length encoding - Memory pool for reusing
 * image objects - Adaptive batch sizing based on available memory - Advanced
 * memory monitoring and management
 */
public class BuildSpotsMeasuresAdvanced extends BuildSeries {

	// === MEMORY POOL ===
	// Removed cursor pooling to simplify and match original approach

	// === STREAMING PROCESSING ===
	private StreamingImageProcessor streamingProcessor;

	// === COMPRESSED MASK STORAGE ===
	private final ConcurrentHashMap<String, CompressedMask> compressedMasks = new ConcurrentHashMap<>();

	// === MEMORY PROFILING COUNTERS ===
//	private int totalImagesProcessed = 0;
	private int totalTransformedImagesCreated = 0;
	private int totalCursorsCreated = 0;
	private int batchCount = 0; // Counter for batch processing cleanup optimization

	// === ADAPTIVE MEMORY MANAGEMENT ===
	private final MemoryMonitor memoryMonitor;
	private final AdaptiveBatchSizer adaptiveBatchSizer;

	// === IMAGE MEMORY POOL ===
	private ImageMemoryPool imageMemoryPool = null;
	private ImageMemoryPool transformedImagePool = null; // Pool for transformed images
	private boolean memoryPoolEnabled = true;

	// === TRADITIONAL FIELDS ===
	public Sequence seqData = new Sequence();
	private ViewerFMP vData = null;
	private ImageTransformOptions transformOptions01 = null;
	ImageTransformInterface transformFunctionSpot = null;
	ImageTransformOptions transformOptions02 = null;
	ImageTransformInterface transformFunctionFly = null;

	// --------------------------------------------

	public BuildSpotsMeasuresAdvanced(AdvancedMemoryOptions advancedOptions) {
		if (advancedOptions == null)
			new AdvancedMemoryOptions();
		else {
		}
		this.memoryMonitor = new MemoryMonitor();
		this.streamingProcessor = new StreamingImageProcessor(memoryMonitor);
		this.adaptiveBatchSizer = new AdaptiveBatchSizer(memoryMonitor);

		// Initialize image memory pools (will be configured with first image)
		if (memoryPoolEnabled) {
			try {
				this.imageMemoryPool = new ImageMemoryPool();
				this.transformedImagePool = new ImageMemoryPool();
//				System.out.println("DEBUG: Image memory pools created (will be initialized with first image)");
			} catch (Exception e) {
				System.err.println("WARNING: Failed to create image memory pools: " + e.getMessage());
				this.memoryPoolEnabled = false;
				this.imageMemoryPool = null;
				this.transformedImagePool = null;
			}
		}
	}

	void analyzeExperiment(Experiment exp) {
		logMemoryUsage("Before Analysis");

		try {
			// Reinitialize streaming state per experiment
			this.streamingProcessor = new StreamingImageProcessor(new MemoryMonitor());
			this.batchCount = 0;
			getTimeLimitsOfSequence(exp);
			loadExperimentDataToMeasureSpots(exp);
			exp.cagesArray.setReadyToAnalyze(true, options);
			openViewers(exp);

			boolean processed = measureSpotsAdvanced(exp);
			if (processed)
				saveComputation(exp);

			logMemoryUsage("After Processing");

			exp.cagesArray.setReadyToAnalyze(false, options);
			closeViewers();
			cleanupResources();
			enhancedPostProcessingCleanup();

		} finally {
			// Ensure cleanup happens even if exceptions occur
			try {
//				exp.cagesArray.setReadyToAnalyze(false, options);
//				closeViewers();
//				cleanupResources();
//				enhancedPostProcessingCleanup();
			} catch (Exception e) {
				System.err.println("Error during final cleanup: " + e.getMessage());
			}
		}
	}

	private boolean loadExperimentDataToMeasureSpots(Experiment exp) {
		exp.load_MS96_experiment();
		exp.seqCamData.attachSequence(
				exp.seqCamData.getImageLoader().initSequenceFromFirstImage(exp.seqCamData.getImagesList(true)));

		boolean flag = exp.load_MS96_cages();
		if (exp.seqCamData.getTimeManager().getBinDurationMs() == 0)
			exp.loadFileIntervalsFromSeqCamData();

		return flag;
	}

	private void saveComputation(Experiment exp) {
		String directory = exp.getDirectoryToSaveResults();
		if (directory == null)
			return;

		exp.cagesArray.transferMeasuresToLevel2D();
		exp.cagesArray.medianFilterFromSumToSumClean();

		exp.save_MS96_experiment();
		exp.save_MS96_spotsMeasures();
	}

	private void initMeasureSpots(Experiment exp) {
		initMasks2DCompressed(exp);
		initSpotsDataArrays(exp);

		if (transformFunctionSpot == null) {
			transformOptions01 = new ImageTransformOptions();
			transformOptions01.transformOption = options.transform01;
			transformOptions01.copyResultsToThe3planes = false;
			transformOptions01.setSingleThreshold(options.spotThreshold, options.spotThresholdUp);
			transformFunctionSpot = options.transform01.getFunction();

			transformOptions02 = new ImageTransformOptions();
			transformOptions02.transformOption = options.transform02;
			transformOptions02.copyResultsToThe3planes = false;
			transformFunctionFly = options.transform02.getFunction();
		}
	}

	private boolean measureSpotsAdvanced(Experiment exp) {
		if (exp.cagesArray.getTotalNumberOfSpots() < 1) {
//			System.out.println("DetectAreas:measureAreas Abort (1): nbspots = 0");
			return false;
		}

		threadRunning = true;
		stopFlag = false;
		if (!exp.seqCamData.build_MsTimesArray_From_FileNamesList())
			return false;

		int iiFirst = 0;
		int iiLast = exp.seqCamData.getImageLoader().getNTotalFrames();
		vData.setTitle(exp.seqCamData.getCSCamFileName() + ": " + iiFirst + "-" + iiLast);
		ProgressFrame progressBar1 = new ProgressFrame("Analyze stack (Advanced)");

		// Initialize with adaptive batch sizing
		adaptiveBatchSizer.initialize(iiLast - iiFirst, memoryMonitor.getAvailableMemoryMB());
		initMeasureSpots(exp);

		// Initialize streaming processor
		streamingProcessor.start(exp.seqCamData, iiFirst, iiLast);

//		long startTime = System.currentTimeMillis();
//		int processedBatches = 0;

		try {
			for (int batchStart = iiFirst; batchStart < iiLast; batchStart += adaptiveBatchSizer
					.getCurrentBatchSize()) {
				if (stopFlag)
					break;

				int batchEnd = Math.min(batchStart + adaptiveBatchSizer.getCurrentBatchSize(), iiLast);
				processFrameBatchAdvanced(exp, batchStart, batchEnd, iiFirst, iiLast, progressBar1);
//				processedBatches++;

				adaptiveBatchSizer.updateBatchSize(memoryMonitor.getMemoryUsagePercent());
				checkMemoryPressure();
				System.gc();
			}
		} finally {
			streamingProcessor.stop();
		}

//		long endTime = System.currentTimeMillis();
//		long totalTime = endTime - startTime;

//		System.out.println("=== Processing Complete ===");
//		System.out.println("Total processing time: " + totalTime + "ms (" + (totalTime / 1000.0) + "s)");
//		System.out.println("Processed " + processedBatches + " batches");

		// Memory profiling summary
//		if (options.enableMemoryProfiling) {
//			System.out.println("=== Memory Profiling Summary ===");
//			System.out.println("Total transformed images created: " + totalTransformedImagesCreated);
//			System.out.println("Total cursors created: " + totalCursorsCreated);
//			System.out.println("Compressed masks cached: " + compressedMasks.size());
//			logMemoryUsage("Final");
//		}

		progressBar1.close();
		return true;
	}

	private void processFrameBatchAdvanced(Experiment exp, int batchStart, int batchEnd, int iiFirst, int iiLast,
			ProgressFrame progressBar1) {

		// Memory profiling - log before batch processing
		if (options.enableMemoryProfiling) {
			logMemoryUsage("Before Batch " + batchStart + "-" + (batchEnd - 1));
		}

		// Use parallel processing like the original BuildSpotsMeasures
		final Processor processor = new Processor(Math.min(options.maxConcurrentTasks, SystemUtil.getNumberOfCPUs()));
		processor.setThreadName("measureSpotsAdvanced");
		processor.setPriority(Processor.NORM_PRIORITY);

		ArrayList<Future<?>> tasks = new ArrayList<Future<?>>(batchEnd - batchStart);

		for (int ii = batchStart; ii < batchEnd; ii++) {
			if (stopFlag) {
				break;
			}

			if (options.concurrentDisplay) {
				IcyBufferedImage sourceImage0 = streamingProcessor.getImage(ii);
				if (sourceImage0 != null) {
					seqData.setImage(0, 0, sourceImage0);
					vData.setTitle("Frame #" + ii + " /" + iiLast);
				}
			}

			final int t = ii;
			progressBar1.setMessage("Analyze frame: " + t + "//" + iiLast);

			// Load image once and pass to processing method (like original)
			String fileName = exp.seqCamData.getFileNameFromImageList(t);
			if (fileName == null) {
				System.out.println("filename null at t=" + t);
				continue;
			}

			final IcyBufferedImage sourceImage = streamingProcessor.getImage(t);
			tasks.add(processor.submit(new Runnable() {
				@Override
				public void run() {
					processSingleFrameAdvanced(exp, t, iiFirst, sourceImage);
				}
			}));
		}

		// Wait for all tasks to complete
		waitFuturesCompletion(processor, tasks, null);

		// Memory profiling - log after batch processing
		if (options.enableMemoryProfiling) {
			logMemoryUsage("After Batch " + batchStart + "-" + (batchEnd - 1));
		}

		// OPTIMIZED CLEANUP: Only do aggressive cleanup every 5 batches or on high
		// memory pressure
		batchCount++;

		if (batchCount % 5 == 0 || getMemoryUsagePercent() > 60) {
			forceAggressiveCleanup();
		} else {
			// Light cleanup for better performance
			System.gc();
			Thread.yield();
		}
	}

	private void processSingleFrameAdvanced(Experiment exp, int frameIndex, int iiFirst, IcyBufferedImage sourceImage) {
		IcyBufferedImage transformToMeasureArea = null;
		IcyBufferedImage transformToDetectFly = null;
		IcyBufferedImageCursor cursorToDetectFly = null;
		IcyBufferedImageCursor cursorToMeasureArea = null;

		try {
			// Initialize memory pools with first image if not already done
			if (imageMemoryPool != null && memoryPoolEnabled && frameIndex == iiFirst) {
				imageMemoryPool.initializeWithImage(sourceImage);
				// Initialize transformed image pool with the same format
				transformedImagePool.initializeWithImage(sourceImage);
			}

			// Get transformed images from pool or create new ones
			if (transformedImagePool != null && memoryPoolEnabled) {
				transformToMeasureArea = transformedImagePool.getImage();
				transformToDetectFly = transformedImagePool.getImage();

				if (transformToMeasureArea != null && transformToDetectFly != null) {
					// Apply transformations to the pooled images
					// Note: We need to copy the transformed data to the pooled images
					IcyBufferedImage tempMeasureArea = transformFunctionSpot.getTransformedImage(sourceImage,
							transformOptions01);
					IcyBufferedImage tempDetectFly = transformFunctionFly.getTransformedImage(sourceImage,
							transformOptions02);

					// Copy data to pooled images
					copyImageData(tempMeasureArea, transformToMeasureArea);
					copyImageData(tempDetectFly, transformToDetectFly);

					totalTransformedImagesCreated += 2;
				} else {
					// Fallback to creating new images if pool is empty
					transformToMeasureArea = transformFunctionSpot.getTransformedImage(sourceImage, transformOptions01);
					transformToDetectFly = transformFunctionFly.getTransformedImage(sourceImage, transformOptions02);
					totalTransformedImagesCreated += 2;
				}
			} else {
				// Create new images if memory pool is disabled
				transformToMeasureArea = transformFunctionSpot.getTransformedImage(sourceImage, transformOptions01);
				transformToDetectFly = transformFunctionFly.getTransformedImage(sourceImage, transformOptions02);
				totalTransformedImagesCreated += 2;
			}

			// Create cursors (cursors cannot be pooled as they are tied to specific images)
			if (imageMemoryPool != null && memoryPoolEnabled) {
				cursorToDetectFly = imageMemoryPool.createCursor(transformToDetectFly);
				cursorToMeasureArea = imageMemoryPool.createCursor(transformToMeasureArea);
			} else {
				cursorToDetectFly = new IcyBufferedImageCursor(transformToDetectFly);
				cursorToMeasureArea = new IcyBufferedImageCursor(transformToMeasureArea);
			}
			totalCursorsCreated += 2;

			int ii_local = frameIndex - iiFirst;
			for (Cage cage : exp.cagesArray.cagesList) {
				for (Spot spot : cage.spotsArray.getSpotsList()) {
					if (!spot.isReadyForAnalysis()) {
						continue;
					}

					ROI2DWithMask roiT = spot.getROIMask();
					ResultsThreshold results = measureSpotOverThresholdCompressed(cursorToMeasureArea,
							cursorToDetectFly, roiT, transformToMeasureArea);

					if (results.npoints_in > 0) {
						spot.getFlyPresent().setIsPresentAt(ii_local, results.nPoints_fly_present);
						spot.getSum().setValueAt(ii_local, results.sumOverThreshold / results.npoints_in);
						if (results.nPoints_no_fly != results.npoints_in)
							spot.getSum().setValueAt(ii_local,
									results.sumTot_no_fly_over_threshold / results.nPoints_no_fly);
					}
				}
			}
		} finally {
			// Return transformed images to memory pool for reuse
			if (transformedImagePool != null && memoryPoolEnabled) {
				if (transformToMeasureArea != null) {
					transformedImagePool.returnImage(transformToMeasureArea);
				}
				if (transformToDetectFly != null) {
					transformedImagePool.returnImage(transformToDetectFly);
				}
			}

			// Clear references (cursors cannot be pooled as they are tied to specific
			// images)
			transformToMeasureArea = null;
			transformToDetectFly = null;
			cursorToDetectFly = null;
			cursorToMeasureArea = null;

			// Only force GC if memory pressure is high
			if (getMemoryUsagePercent() > 70) {
				System.gc();
			}
		}
	}

	private ResultsThreshold measureSpotOverThresholdCompressed(IcyBufferedImageCursor cursorToMeasureArea,
			IcyBufferedImageCursor cursorToDetectFly, ROI2DWithMask roiT, IcyBufferedImage transformToMeasureArea) {

		ResultsThreshold result = new ResultsThreshold();

		// Validate inputs
		if (cursorToMeasureArea == null || cursorToDetectFly == null) {
			System.err.println("Null cursor provided to measureSpotOverThresholdCompressed");
			result.npoints_in = 0;
			return result;
		}

		// Get compressed mask coordinates
		CompressedMask compressedMask = getCompressedMask(roiT);
		if (compressedMask == null) {
			result.npoints_in = 0;
			return result;
		}

		int[] maskX = compressedMask.getXCoordinates();
		int[] maskY = compressedMask.getYCoordinates();

		// Validate mask coordinates
		if (maskX == null || maskY == null || maskX.length == 0 || maskY.length == 0) {
			System.err.println("Invalid mask coordinates");
			result.npoints_in = 0;
			return result;
		}

		result.npoints_in = maskX.length;

		try {
			// Validate the image before getting dimensions
			if (transformToMeasureArea == null) {
				System.err.println("transformToMeasureArea is null in measureSpotOverThresholdCompressed");
				result.npoints_in = 0;
				return result;
			}

			// Get image dimensions for bounds checking - use the images directly
			// since we have access to them in the calling method
			int imageWidth = transformToMeasureArea.getSizeX();
			int imageHeight = transformToMeasureArea.getSizeY();

			for (int offset = 0; offset < maskX.length; offset++) {
				int x = maskX[offset];
				int y = maskY[offset];

				// Validate coordinates are within image bounds
				if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
					continue; // Skip out-of-bounds pixels
				}

				int value = (int) cursorToMeasureArea.get(x, y, 0);
				int value_to_detect_fly = (int) cursorToDetectFly.get(x, y, 0);

				boolean isFlyThere = isFlyPresent(value_to_detect_fly);
				if (!isFlyThere) {
					result.nPoints_no_fly++;
				} else
					result.nPoints_fly_present++;

				if (isOverThreshold(value)) {
					result.sumOverThreshold += value;
					result.nPointsOverThreshold++;
					if (!isFlyThere) {
						result.sumTot_no_fly_over_threshold += value;
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error in measureSpotOverThresholdCompressed: " + e.getMessage());
			result.npoints_in = 0;
		}

		return result;
	}

	private CompressedMask getCompressedMask(ROI2DWithMask roiT) {
		String maskKey = roiT.getInputRoi().getName() + "_" + System.identityHashCode(roiT.getInputRoi());

		return compressedMasks.computeIfAbsent(maskKey, key -> {
			Point[] maskPoints = roiT.getMaskPoints();
			if (maskPoints == null || maskPoints.length == 0) {
				return null;
			}
			return new CompressedMask(maskPoints);
		});
	}

	private boolean isFlyPresent(double value) {
		boolean flag = value > options.flyThreshold;
		if (!options.flyThresholdUp)
			flag = !flag;
		return flag;
	}

	private boolean isOverThreshold(double value) {
		boolean flag = value > options.spotThreshold;
		if (!options.spotThresholdUp)
			flag = !flag;
		return flag;
	}

	private void initSpotsDataArrays(Experiment exp) {
		int nFrames = exp.seqCamData.getImageLoader().getNTotalFrames();
		int spotArrayGlobalIndex = 0;
		for (Cage cage : exp.cagesArray.cagesList) {
			int spotPosition = 0;
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				spot.getProperties().setCagePosition(spotPosition);
				spot.getProperties().setCageID(cage.getProperties().getCageID());
				spot.getProperties().setSpotArrayIndex(spotArrayGlobalIndex);
				spot.getSum().setValues(new double[nFrames]);
				spot.getSumClean().setValues(new double[nFrames]);
				spot.getFlyPresent().setIsPresent(new int[nFrames]);
				spotArrayGlobalIndex++;
				spotPosition++;
			}
		}
	}

	private void initMasks2DCompressed(Experiment exp) {
		SequenceCamData seqCamData = exp.seqCamData;
		if (seqCamData.getSequence() == null)
			seqCamData.attachSequence(
					exp.seqCamData.getImageLoader().initSequenceFromFirstImage(exp.seqCamData.getImagesList(true)));

		for (Cage cage : exp.cagesArray.cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				ROI2DWithMask roiT = null;
				try {
					roiT = new ROI2DWithMask(spot.getRoi());
					roiT.buildMask2DFromInputRoi();
				} catch (ProcessingException | ValidationException e) {
					System.err.println("Error building mask for ROI: " + e.getMessage());
					e.printStackTrace();
				}
				spot.setROIMask(roiT);
			}
		}
	}

	private void cleanupResources() {
		compressedMasks.clear();

		// Clean up memory pools
		if (imageMemoryPool != null) {
			imageMemoryPool.performPeriodicCleanup();
		}
		if (transformedImagePool != null) {
			transformedImagePool.performPeriodicCleanup();
		}
	}

	private void closeViewers() {
		closeViewer(vData);
		closeSequence(seqData);
	}

	private void openViewers(Experiment exp) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					seqData = newSequence(exp.seqCamData.getCSCamFileName(), exp.seqCamData.getSeqImage(0, 0));
					vData = new ViewerFMP(seqData, true, true);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	// === MEMORY PROFILING ===

	private void logMemoryUsage(String stage) {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;
		long maxMemory = runtime.maxMemory();

		System.out.println("=== " + stage + " ===");
		System.out.println("Used Memory: " + (usedMemory / 1024 / 1024) + " MB");
		System.out.println("Free Memory: " + (freeMemory / 1024 / 1024) + " MB");
		System.out.println("Total Memory: " + (totalMemory / 1024 / 1024) + " MB");
		System.out.println("Max Memory: " + (maxMemory / 1024 / 1024) + " MB");
		System.out.println("Memory Usage: " + (usedMemory * 100 / maxMemory) + "%");

		// Additional tracking for memory leak analysis
		System.out.println("Compressed Masks: " + compressedMasks.size());
		System.out.println("Total Transformed Images: " + totalTransformedImagesCreated);
		System.out.println("Total Cursors: " + totalCursorsCreated);

		// Memory pool statistics
		if (imageMemoryPool != null && memoryPoolEnabled) {
			System.out.println("Source Image Pool: " + imageMemoryPool.getPoolStatistics());
		}
		if (transformedImagePool != null && memoryPoolEnabled) {
			System.out.println("Transformed Image Pool: " + transformedImagePool.getPoolStatistics());
		}
	}

	// === AGGRESSIVE MEMORY CLEANUP ===

	private void forceAggressiveCleanup() {
		// Multiple GC passes with delays
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(100); // Give GC time to work
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Force memory pool cleanup
		clearImageCaches();
		clearCompressedMaskCache();

		// Clean up memory pools
		if (imageMemoryPool != null) {
			imageMemoryPool.clearPool();
		}
		if (transformedImagePool != null) {
			transformedImagePool.clearPool();
		}
	}

	private void clearImageCaches() {
		// Clear any Icy internal caches if accessible
		try {
			// Try to clear Icy's image cache
			Class<?> icyImageCacheClass = Class.forName("icy.image.ImageCache");
			java.lang.reflect.Method clearCacheMethod = icyImageCacheClass.getDeclaredMethod("clearCache");
			if (clearCacheMethod != null) {
				clearCacheMethod.setAccessible(true);
				clearCacheMethod.invoke(null);
			}
		} catch (Exception e) {
			// Icy cache clearing not available
		}
	}

	private void clearCompressedMaskCache() {
		// Limit cache size to prevent unbounded growth
		if (compressedMasks.size() > 1000) {
			System.out.println("Clearing compressed mask cache (size: " + compressedMasks.size() + ")");
			compressedMasks.clear();
		}
	}

	// === MEMORY PRESSURE CHECKING ===
	private double getMemoryUsagePercent() {
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		return (usedMemory * 100.0) / runtime.maxMemory();
	}

	private void checkMemoryPressure() {
		double usagePercent = getMemoryUsagePercent();

		// Only trigger aggressive cleanup if memory usage is very high (>70%)
		if (usagePercent > 70) {
			System.out.println("=== HIGH MEMORY PRESSURE DETECTED: " + usagePercent + "% ===");
			forceAggressiveCleanup();

			// Reduce batch size for next batch
			if (adaptiveBatchSizer != null) {
				adaptiveBatchSizer.reduceBatchSize();
				System.out.println("Reduced batch size due to memory pressure");
			}
		}
		// For moderate memory usage (50-70%), just do light cleanup
		else if (usagePercent > 50) {
			System.gc();
			Thread.yield();
		}
	}

	// === ENHANCED POST-PROCESSING CLEANUP ===

	private void enhancedPostProcessingCleanup() {
//		System.out.println("=== ENHANCED POST-PROCESSING CLEANUP ===");

		// Force multiple GC passes with longer delays
		for (int i = 0; i < 5; i++) {
			System.gc();
			try {
				Thread.sleep(200); // Longer delays for post-processing cleanup
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		// Clear all caches and references
		clearAllCaches();
		clearAllReferences();
		forceIcyCleanup();
	}

	private void clearAllCaches() {
		// Clear compressed mask cache
		if (compressedMasks != null) {
			compressedMasks.clear();
		}

		// Clear any other caches
		totalTransformedImagesCreated = 0;
		totalCursorsCreated = 0;
//		totalImagesProcessed = 0;
	}

	private void clearAllReferences() {
		// Clear all image references
		if (seqData != null) {
			try {
				// Try to clear sequence data - use reflection to avoid compilation issues
				java.lang.reflect.Method clearMethod = seqData.getClass().getDeclaredMethod("clear");
				if (clearMethod != null) {
					clearMethod.setAccessible(true);
					clearMethod.invoke(seqData);
				}
			} catch (Exception e) {
				// Ignore cleanup errors
			}
		}

		// Clear viewer references
		if (vData != null) {
			try {
				vData.dispose();
			} catch (Exception e) {
				// Ignore cleanup errors
			}
			vData = null;
		}

	}

	private void forceIcyCleanup() {
		// Try to clear Icy's internal caches
		try {
			// Clear Icy image cache
			Class<?> icyImageCacheClass = Class.forName("icy.image.ImageCache");
			java.lang.reflect.Method clearCacheMethod = icyImageCacheClass.getDeclaredMethod("clearCache");
			if (clearCacheMethod != null) {
				clearCacheMethod.setAccessible(true);
				clearCacheMethod.invoke(null);
			}
		} catch (Exception e) {
			System.out.println("WARNING: Could not clear Icy image cache: " + e.getMessage());
		}

		// Try to clear Icy sequence cache
		try {
			Class<?> icySequenceClass = Class.forName("icy.sequence.Sequence");
			java.lang.reflect.Method disposeMethod = icySequenceClass.getDeclaredMethod("dispose");
			if (disposeMethod != null) {
				disposeMethod.setAccessible(true);
				disposeMethod.invoke(seqData);
				System.out.println("Disposed Icy sequence");
			}
		} catch (Exception e) {
			System.out.println("WARNING: Could not dispose Icy sequence: " + e.getMessage());
		}
	}

	/**
	 * Copies image data from source to destination image. Both images must have the
	 * same dimensions and data type.
	 * 
	 * @param source      the source image
	 * @param destination the destination image
	 */
	private void copyImageData(IcyBufferedImage source, IcyBufferedImage destination) {
		if (source == null || destination == null) {
			return;
		}

		try {
			// Copy pixel data from source to destination
			destination.setDataXY(0, source.getDataXY(0));
		} catch (Exception e) {
			System.err.println("Error copying image data: " + e.getMessage());
		}
	}

	// === INNER CLASSES REMOVED - NOW EXTERNAL CLASSES ===
}