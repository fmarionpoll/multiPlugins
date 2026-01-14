package plugins.fmp.multiSPOTS96.tools.toExcel;

import plugins.fmp.multiSPOTS96.tools.toExcel.exceptions.ExcelExportException;

/**
 * Factory class for creating appropriate XLSExport implementations based on
 * dataset size and available memory.
 * 
 * <p>
 * This factory automatically selects the most suitable implementation:
 * <ul>
 * <li><strong>Original</strong>: For small datasets with no memory constraints</li>
 * <li><strong>Optimized</strong>: For medium datasets with moderate memory constraints</li>
 * <li><strong>Streaming</strong>: For large datasets with severe memory constraints</li>
 * </ul>
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class XLSExportFactory {

    // Memory thresholds (in MB)
    private static final int SMALL_DATASET_THRESHOLD = 10; // experiments
    private static final int MEDIUM_DATASET_THRESHOLD = 100; // experiments
    private static final double MEMORY_USAGE_THRESHOLD = 0.7; // 70% of available memory
    
    // Memory estimation constants
    private static final int BYTES_PER_SPOT_PER_TIME_POINT = 24; // Double + ArrayList overhead
    private static final int AVERAGE_TIME_POINTS_PER_SPOT = 1000;
    private static final int SPOTS_PER_EXPERIMENT = 96;
    private static final int EXPORT_TYPES = 3; // AREA_SUM, AREA_FLYPRESENT, AREA_SUMCLEAN

    /**
     * Creates the most appropriate XLSExport implementation based on dataset characteristics.
     * 
     * @param experimentCount The number of experiments to export
     * @param options The export options
     * @return The appropriate XLSExport implementation
     * @throws ExcelExportException If no suitable implementation can be created
     */
    public static XLSExport createExporter(int experimentCount, XLSExportOptions options) 
            throws ExcelExportException {
        
        // Estimate memory requirements
        long estimatedMemoryMB = estimateMemoryUsage(experimentCount);
        double availableMemoryMB = getAvailableMemoryMB();
        double memoryUsageRatio = estimatedMemoryMB / availableMemoryMB;
        
        // Select implementation based on dataset size and memory constraints
        if (experimentCount <= SMALL_DATASET_THRESHOLD && memoryUsageRatio < 0.3) {
            return createOriginalExporter();
        } else if (experimentCount <= MEDIUM_DATASET_THRESHOLD && memoryUsageRatio < MEMORY_USAGE_THRESHOLD) {
            return createOptimizedExporter();
        } else {
            return createStreamingExporter();
        }
    }

    /**
     * Creates the most appropriate XLSExport implementation with automatic memory monitoring.
     * 
     * @param experimentCount The number of experiments to export
     * @param options The export options
     * @param enableMemoryMonitoring Whether to enable memory monitoring
     * @return The appropriate XLSExport implementation
     * @throws ExcelExportException If no suitable implementation can be created
     */
    public static XLSExport createExporter(int experimentCount, XLSExportOptions options, 
            boolean enableMemoryMonitoring) throws ExcelExportException {
        
        XLSExport exporter = createExporter(experimentCount, options);
        
        if (enableMemoryMonitoring && exporter instanceof XLSExportMeasuresFromSpotStreaming) {
            ((XLSExportMeasuresFromSpotStreaming) exporter).setMemoryMonitoringEnabled(true);
        }
        
        return exporter;
    }

    /**
     * Creates the original XLSExport implementation.
     * 
     * @return The original XLSExport implementation
     */
    public static XLSExport createOriginalExporter() {
        return new XLSExportMeasuresFromSpot();
    }

    /**
     * Creates the optimized XLSExport implementation.
     * 
     * @return The optimized XLSExport implementation
     */
    public static XLSExport createOptimizedExporter() {
        return new XLSExportMeasuresFromSpotOptimized();
    }

    /**
     * Creates the streaming XLSExport implementation.
     * 
     * @return The streaming XLSExport implementation
     */
    public static XLSExport createStreamingExporter() {
        return new XLSExportMeasuresFromSpotStreaming();
    }

    /**
     * Estimates memory usage for the given number of experiments.
     * 
     * @param experimentCount The number of experiments
     * @return Estimated memory usage in MB
     */
    public static long estimateMemoryUsage(int experimentCount) {
        long totalSpots = (long) experimentCount * SPOTS_PER_EXPERIMENT;
        long totalTimePoints = totalSpots * AVERAGE_TIME_POINTS_PER_SPOT;
        long totalDataPoints = totalTimePoints * EXPORT_TYPES;
        
        // Estimate memory usage including overhead
        long estimatedBytes = totalDataPoints * BYTES_PER_SPOT_PER_TIME_POINT;
        return estimatedBytes / (1024 * 1024); // Convert to MB
    }

    /**
     * Gets the available memory in MB.
     * 
     * @return Available memory in MB
     */
    public static double getAvailableMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        long availableMemory = maxMemory - totalMemory + freeMemory;
        return availableMemory / (1024.0 * 1024.0);
    }

    /**
     * Gets the current memory usage percentage.
     * 
     * @return Memory usage percentage (0.0 to 1.0)
     */
    public static double getMemoryUsagePercentage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (double) usedMemory / runtime.maxMemory();
    }

    /**
     * Determines if the system is under memory pressure.
     * 
     * @return true if memory usage is above threshold
     */
    public static boolean isUnderMemoryPressure() {
        return getMemoryUsagePercentage() > MEMORY_USAGE_THRESHOLD;
    }

    /**
     * Gets a recommendation for the best implementation based on current conditions.
     * 
     * @param experimentCount The number of experiments
     * @return A string describing the recommended implementation
     */
    public static String getRecommendation(int experimentCount) {
        long estimatedMemoryMB = estimateMemoryUsage(experimentCount);
        double availableMemoryMB = getAvailableMemoryMB();
        double memoryUsageRatio = estimatedMemoryMB / availableMemoryMB;
        
        if (experimentCount <= SMALL_DATASET_THRESHOLD && memoryUsageRatio < 0.3) {
            return "Original implementation recommended (small dataset, low memory usage)";
        } else if (experimentCount <= MEDIUM_DATASET_THRESHOLD && memoryUsageRatio < MEMORY_USAGE_THRESHOLD) {
            return "Optimized implementation recommended (medium dataset, moderate memory usage)";
        } else {
            return "Streaming implementation recommended (large dataset or memory constraints)";
        }
    }

    /**
     * Creates an exporter with custom memory thresholds.
     * 
     * @param experimentCount The number of experiments
     * @param options The export options
     * @param smallDatasetThreshold Custom small dataset threshold
     * @param mediumDatasetThreshold Custom medium dataset threshold
     * @param memoryUsageThreshold Custom memory usage threshold
     * @return The appropriate XLSExport implementation
     * @throws ExcelExportException If no suitable implementation can be created
     */
    public static XLSExport createExporterWithCustomThresholds(int experimentCount, XLSExportOptions options,
            int smallDatasetThreshold, int mediumDatasetThreshold, double memoryUsageThreshold) 
            throws ExcelExportException {
        
        long estimatedMemoryMB = estimateMemoryUsage(experimentCount);
        double availableMemoryMB = getAvailableMemoryMB();
        double memoryUsageRatio = estimatedMemoryMB / availableMemoryMB;
        
        if (experimentCount <= smallDatasetThreshold && memoryUsageRatio < 0.3) {
            return createOriginalExporter();
        } else if (experimentCount <= mediumDatasetThreshold && memoryUsageRatio < memoryUsageThreshold) {
            return createOptimizedExporter();
        } else {
            return createStreamingExporter();
        }
    }

    /**
     * Gets detailed memory analysis for the given dataset.
     * 
     * @param experimentCount The number of experiments
     * @return A detailed memory analysis string
     */
    public static String getMemoryAnalysis(int experimentCount) {
        long estimatedMemoryMB = estimateMemoryUsage(experimentCount);
        double availableMemoryMB = getAvailableMemoryMB();
        double memoryUsageRatio = estimatedMemoryMB / availableMemoryMB;
        double currentMemoryUsage = getMemoryUsagePercentage();
        
        StringBuilder analysis = new StringBuilder();
        analysis.append("Memory Analysis:\n");
        analysis.append(String.format("- Experiments: %d\n", experimentCount));
        analysis.append(String.format("- Estimated memory usage: %.1f MB\n", estimatedMemoryMB));
        analysis.append(String.format("- Available memory: %.1f MB\n", availableMemoryMB));
        analysis.append(String.format("- Memory usage ratio: %.1f%%\n", memoryUsageRatio * 100));
        analysis.append(String.format("- Current memory usage: %.1f%%\n", currentMemoryUsage * 100));
        analysis.append(String.format("- Recommendation: %s\n", getRecommendation(experimentCount)));
        
        return analysis.toString();
    }

    /**
     * Validates that the system can handle the export operation.
     * 
     * @param experimentCount The number of experiments
     * @return true if the system can handle the export
     */
    public static boolean canHandleExport(int experimentCount) {
        long estimatedMemoryMB = estimateMemoryUsage(experimentCount);
        double availableMemoryMB = getAvailableMemoryMB();
        
        // Require at least 20% free memory after export
        return estimatedMemoryMB < availableMemoryMB * 0.8;
    }

    /**
     * Gets the minimum required memory for the export operation.
     * 
     * @param experimentCount The number of experiments
     * @return Minimum required memory in MB
     */
    public static long getMinimumRequiredMemory(int experimentCount) {
        return estimateMemoryUsage(experimentCount);
    }
} 