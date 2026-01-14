package plugins.fmp.multiSPOTS96.series;

/**
 * Adaptive batch sizing based on available memory.
 * 
 * This class provides dynamic batch sizing capabilities that adjust based on
 * available memory to optimize processing performance while preventing
 * memory overflow.
 */
public class AdaptiveBatchSizer {
    private final MemoryMonitor memoryMonitor;
    private int currentBatchSize;
    private final int minBatchSize = 1; // Process one frame at a time for sequential processing
    private final int maxBatchSize = 10; // Reduced from 20 to 10

    public AdaptiveBatchSizer(MemoryMonitor memoryMonitor) {
        this.memoryMonitor = memoryMonitor;
        this.currentBatchSize = 3; // Reduced default for sequential processing
    }

    public void initialize(int totalFrames, long availableMemoryMB) {
        // Use smaller batch sizes for sequential processing to reduce memory usage
        int optimalBatchSize = (int) Math.min(maxBatchSize, Math.max(minBatchSize, availableMemoryMB / 400)); // Reduced
                                                                                                                // from
                                                                                                                // 200
                                                                                                                // to
                                                                                                                // 400
        this.currentBatchSize = Math.min(optimalBatchSize, totalFrames);
    }

    public void updateBatchSize(double memoryUsagePercent) {
        if (memoryUsagePercent > 85) {
            // High memory pressure - reduce batch size
            currentBatchSize = Math.max(minBatchSize, currentBatchSize - 2);
        } else if (memoryUsagePercent < 50) {
            // Low memory pressure - increase batch size
            currentBatchSize = Math.min(maxBatchSize, currentBatchSize + 1);
        }
    }
    
    public void reduceBatchSize() {
        // Force batch size reduction for memory pressure
        currentBatchSize = Math.max(minBatchSize, currentBatchSize - 1);
        System.out.println("Reduced batch size to: " + currentBatchSize + " due to memory pressure");
    }

    public int getCurrentBatchSize() {
        return currentBatchSize;
    }
} 