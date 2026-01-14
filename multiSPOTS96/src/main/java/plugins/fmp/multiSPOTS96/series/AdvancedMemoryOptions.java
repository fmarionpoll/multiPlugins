package plugins.fmp.multiSPOTS96.series;

import java.util.zip.Deflater;

/**
 * Advanced memory optimization configuration options for BuildSpotsMeasuresAdvanced.
 * This class provides comprehensive settings for all advanced memory optimizations.
 */
public class AdvancedMemoryOptions {
	
	// === STREAMING PROCESSING ===
	/** Enable streaming image processing to avoid loading entire stack */
	public boolean enableStreaming = true;
	/** Number of images to pre-load in streaming buffer */
	public int streamBufferSize = 5;
	/** Enable background prefetching of images */
	public boolean enablePrefetching = true;
	/** Prefetch thread priority (1-10) */
	public int prefetchThreadPriority = Thread.NORM_PRIORITY;
	
	// === MEMORY POOL ===
	/** Enable memory pool for reusing image objects */
	public boolean enableMemoryPool = true;
	/** Maximum number of images in pool */
	public int maxImagePoolSize = 20;
	/** Maximum number of cursors in pool */
	public int maxCursorPoolSize = 20;
	/** Enable pool statistics tracking */
	public boolean enablePoolStatistics = true;
	
	// === COMPRESSED MASK STORAGE ===
	/** Enable compressed mask storage using run-length encoding */
	public boolean enableCompression = true;
	/** Compression level (0-9, see Deflater constants) */
	public int compressionLevel = Deflater.BEST_SPEED;
	/** Enable mask caching */
	public boolean enableMaskCaching = true;
	/** Maximum number of cached masks */
	public int maxCachedMasks = 100;
	
	// === ADAPTIVE MEMORY MANAGEMENT ===
	/** Enable adaptive batch sizing based on available memory */
	public boolean enableAdaptiveBatchSizing = true;
	/** Memory usage threshold for batch size reduction (%) */
	public int memoryThresholdPercent = 80;
	/** Memory usage threshold for batch size increase (%) */
	public int memoryIncreaseThresholdPercent = 50;
	/** Minimum batch size */
	public int minBatchSize = 3;
	/** Maximum batch size */
	public int maxBatchSize = 50;
	/** Batch size adjustment step */
	public int batchSizeAdjustmentStep = 2;
	
	// === CONCURRENT PROCESSING ===
	/** Maximum number of concurrent processing tasks */
	public int maxConcurrentTasks = 4;
	/** Thread pool keep-alive time in milliseconds */
	public long threadPoolKeepAliveMs = 60000;
	/** Enable thread pool statistics */
	public boolean enableThreadPoolStatistics = true;
	
	// === MEMORY MONITORING ===
	/** Enable detailed memory monitoring */
	public boolean enableMemoryMonitoring = true;
	/** Memory monitoring interval in milliseconds */
	public long memoryMonitoringIntervalMs = 1000;
	/** Enable memory usage logging */
	public boolean enableMemoryLogging = false;
	/** Memory usage logging threshold (%) */
	public int memoryLoggingThresholdPercent = 90;
	
	// === GARBAGE COLLECTION ===
	/** Enable forced garbage collection on high memory pressure */
	public boolean enableForcedGC = true;
	/** Memory threshold for forced GC (%) */
	public int forcedGCThresholdPercent = 85;
	/** GC frequency in batches */
	public int gcFrequencyBatches = 5;
	
	// === PERFORMANCE TUNING ===
	/** Enable performance profiling */
	public boolean enableProfiling = false;
	/** Profiling output interval in batches */
	public int profilingIntervalBatches = 10;
	/** Enable adaptive optimization based on performance metrics */
	public boolean enableAdaptiveOptimization = true;
	
	// === DEBUGGING ===
	/** Enable debug logging */
	public boolean enableDebugLogging = false;
	/** Enable detailed error reporting */
	public boolean enableDetailedErrorReporting = true;
	/** Enable performance metrics collection */
	public boolean enablePerformanceMetrics = false;
	
	/**
	 * Creates a new AdvancedMemoryOptions with default settings.
	 */
	public AdvancedMemoryOptions() {
		// Default constructor with all defaults set above
	}
	
	/**
	 * Creates a new AdvancedMemoryOptions with conservative settings for memory-constrained systems.
	 */
	public static AdvancedMemoryOptions createConservative() {
		AdvancedMemoryOptions options = new AdvancedMemoryOptions();
		options.enableStreaming = true;
		options.streamBufferSize = 3;
		options.maxImagePoolSize = 10;
		options.maxCursorPoolSize = 10;
		options.maxConcurrentTasks = 2;
		options.minBatchSize = 2;
		options.maxBatchSize = 20;
		options.memoryThresholdPercent = 70;
		options.enableForcedGC = true;
		options.forcedGCThresholdPercent = 75;
		return options;
	}

	/**
	 * Creates a new AdvancedMemoryOptions with ultra-conservative settings for severe memory constraints.
	 * This configuration is designed to minimize memory usage at the cost of some performance.
	 */
	public static AdvancedMemoryOptions createUltraConservative() {
		AdvancedMemoryOptions options = new AdvancedMemoryOptions();
		options.enableStreaming = true;
		options.streamBufferSize = 1; // Minimal buffer
		options.enablePrefetching = false; // Disable prefetching to save memory
		options.maxImagePoolSize = 2; // Very small pool
		options.maxCursorPoolSize = 2;
		options.maxConcurrentTasks = 1; // Single-threaded to reduce memory overhead
		options.minBatchSize = 1; // Process one frame at a time
		options.maxBatchSize = 5; // Very small batches
		options.memoryThresholdPercent = 60; // Lower threshold
		options.enableForcedGC = true;
		options.forcedGCThresholdPercent = 65; // More aggressive GC
		options.gcFrequencyBatches = 2; // GC every 2 batches
		options.enableCompression = true;
		options.compressionLevel = Deflater.BEST_COMPRESSION; // Maximum compression
		options.maxCachedMasks = 10; // Minimal mask cache
		options.enableMemoryMonitoring = true;
		options.enableMemoryLogging = true;
		options.memoryLoggingThresholdPercent = 70;
		return options;
	}
	
	/**
	 * Creates a new AdvancedMemoryOptions with aggressive settings for high-performance systems.
	 */
	public static AdvancedMemoryOptions createAggressive() {
		AdvancedMemoryOptions options = new AdvancedMemoryOptions();
		options.enableStreaming = true;
		options.streamBufferSize = 10;
		options.maxImagePoolSize = 50;
		options.maxCursorPoolSize = 50;
		options.maxConcurrentTasks = 8;
		options.minBatchSize = 5;
		options.maxBatchSize = 100;
		options.memoryThresholdPercent = 90;
		options.enableForcedGC = false;
		options.enableProfiling = true;
		return options;
	}
	
	/**
	 * Creates a new AdvancedMemoryOptions with balanced settings for typical systems.
	 */
	public static AdvancedMemoryOptions createBalanced() {
		AdvancedMemoryOptions options = new AdvancedMemoryOptions();
		// Use default settings which are already balanced
		return options;
	}
	
	/**
	 * Validates the configuration options and returns any issues.
	 * 
	 * @return Validation result with any issues found
	 */
	public ValidationResult validate() {
		ValidationResult result = new ValidationResult();
		
		// Validate streaming settings
		if (streamBufferSize < 1 || streamBufferSize > 50) {
			result.addIssue("streamBufferSize should be between 1 and 50, got: " + streamBufferSize);
		}
		
		// Validate pool settings
		if (maxImagePoolSize < 1 || maxImagePoolSize > 100) {
			result.addIssue("maxImagePoolSize should be between 1 and 100, got: " + maxImagePoolSize);
		}
		if (maxCursorPoolSize < 1 || maxCursorPoolSize > 100) {
			result.addIssue("maxCursorPoolSize should be between 1 and 100, got: " + maxCursorPoolSize);
		}
		
		// Validate compression settings
		if (compressionLevel < 0 || compressionLevel > 9) {
			result.addIssue("compressionLevel should be between 0 and 9, got: " + compressionLevel);
		}
		if (maxCachedMasks < 1 || maxCachedMasks > 1000) {
			result.addIssue("maxCachedMasks should be between 1 and 1000, got: " + maxCachedMasks);
		}
		
		// Validate batch sizing settings
		if (minBatchSize < 1 || minBatchSize > maxBatchSize) {
			result.addIssue("minBatchSize should be >= 1 and <= maxBatchSize, got: " + minBatchSize);
		}
		if (maxBatchSize < minBatchSize || maxBatchSize > 200) {
			result.addIssue("maxBatchSize should be >= minBatchSize and <= 200, got: " + maxBatchSize);
		}
		if (memoryThresholdPercent < 50 || memoryThresholdPercent > 95) {
			result.addIssue("memoryThresholdPercent should be between 50 and 95, got: " + memoryThresholdPercent);
		}
		
		// Validate concurrent processing settings
		if (maxConcurrentTasks < 1 || maxConcurrentTasks > 16) {
			result.addIssue("maxConcurrentTasks should be between 1 and 16, got: " + maxConcurrentTasks);
		}
		if (threadPoolKeepAliveMs < 1000 || threadPoolKeepAliveMs > 300000) {
			result.addIssue("threadPoolKeepAliveMs should be between 1000 and 300000, got: " + threadPoolKeepAliveMs);
		}
		
		// Validate monitoring settings
		if (memoryMonitoringIntervalMs < 100 || memoryMonitoringIntervalMs > 10000) {
			result.addIssue("memoryMonitoringIntervalMs should be between 100 and 10000, got: " + memoryMonitoringIntervalMs);
		}
		if (memoryLoggingThresholdPercent < 50 || memoryLoggingThresholdPercent > 100) {
			result.addIssue("memoryLoggingThresholdPercent should be between 50 and 100, got: " + memoryLoggingThresholdPercent);
		}
		
		// Validate GC settings
		if (forcedGCThresholdPercent < 50 || forcedGCThresholdPercent > 95) {
			result.addIssue("forcedGCThresholdPercent should be between 50 and 95, got: " + forcedGCThresholdPercent);
		}
		if (gcFrequencyBatches < 1 || gcFrequencyBatches > 50) {
			result.addIssue("gcFrequencyBatches should be between 1 and 50, got: " + gcFrequencyBatches);
		}
		
		return result;
	}
	
	/**
	 * Gets a summary of the current configuration.
	 * 
	 * @return Configuration summary string
	 */
	public String getConfigurationSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Advanced Memory Options Configuration:\n");
		sb.append("  Streaming: ").append(enableStreaming).append(" (buffer: ").append(streamBufferSize).append(")\n");
		sb.append("  Memory Pool: ").append(enableMemoryPool).append(" (images: ").append(maxImagePoolSize)
		  .append(", cursors: ").append(maxCursorPoolSize).append(")\n");
		sb.append("  Compression: ").append(enableCompression).append(" (level: ").append(compressionLevel).append(")\n");
		sb.append("  Adaptive Batching: ").append(enableAdaptiveBatchSizing).append(" (").append(minBatchSize)
		  .append("-").append(maxBatchSize).append(")\n");
		sb.append("  Concurrent Tasks: ").append(maxConcurrentTasks).append("\n");
		sb.append("  Memory Threshold: ").append(memoryThresholdPercent).append("%\n");
		sb.append("  Forced GC: ").append(enableForcedGC).append(" (").append(forcedGCThresholdPercent).append("%)\n");
		return sb.toString();
	}
	
	/**
	 * Validation result containing any configuration issues.
	 */
	public static class ValidationResult {
		private final java.util.List<String> issues = new java.util.ArrayList<>();
		
		public void addIssue(String issue) {
			issues.add(issue);
		}
		
		public boolean isValid() {
			return issues.isEmpty();
		}
		
		public java.util.List<String> getIssues() {
			return new java.util.ArrayList<>(issues);
		}
		
		@Override
		public String toString() {
			if (isValid()) {
				return "Configuration is valid";
			} else {
				StringBuilder sb = new StringBuilder("Configuration issues:\n");
				for (String issue : issues) {
					sb.append("  - ").append(issue).append("\n");
				}
				return sb.toString();
			}
		}
	}
} 