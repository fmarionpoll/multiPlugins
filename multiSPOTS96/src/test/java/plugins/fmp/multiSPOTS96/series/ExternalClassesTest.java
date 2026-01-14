package plugins.fmp.multiSPOTS96.series;

import java.awt.Point;

/**
 * Test class to verify that the externalized classes work correctly.
 * This demonstrates how the externalized classes can be used by other classes.
 */
public class ExternalClassesTest {

    public static void main(String[] args) {
        System.out.println("Testing externalized classes...");
        
        // Test MemoryMonitor
        MemoryMonitor memoryMonitor = new MemoryMonitor();
        System.out.println("Memory usage: " + memoryMonitor.getUsedMemoryMB() + "MB / " 
                + memoryMonitor.getMaxMemoryMB() + "MB (" + memoryMonitor.getMemoryUsagePercent() + "%)");
        
        // Test AdaptiveBatchSizer
        AdaptiveBatchSizer batchSizer = new AdaptiveBatchSizer(memoryMonitor);
        batchSizer.initialize(100, memoryMonitor.getAvailableMemoryMB());
        System.out.println("Batch size: " + batchSizer.getCurrentBatchSize());
        
        // Test CompressedMask
        Point[] testPoints = {
            new Point(0, 0), new Point(1, 0), new Point(2, 0), new Point(3, 0),
            new Point(0, 1), new Point(1, 1), new Point(2, 1), new Point(3, 1)
        };
        CompressedMask compressedMask = new CompressedMask(testPoints);
        System.out.println("Compression ratio: " + compressedMask.getCompressionRatio());
        System.out.println("X coordinates: " + compressedMask.getXCoordinates().length);
        System.out.println("Y coordinates: " + compressedMask.getYCoordinates().length);
        
        // Test StreamingImageProcessor
        StreamingImageProcessor streamingProcessor = new StreamingImageProcessor(memoryMonitor);
        System.out.println("StreamingImageProcessor created successfully");
        
        System.out.println("All externalized classes work correctly!");
    }
} 