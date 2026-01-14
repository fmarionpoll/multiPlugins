package plugins.fmp.multiSPOTS96.series;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Test class to demonstrate and validate memory optimizations in BuildSpotsMeasures.
 * This class provides utilities to monitor memory usage and compare different configurations.
 */
public class BuildSpotsMeasuresMemoryTest {

    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    /**
     * Memory usage snapshot for monitoring.
     */
    public static class MemorySnapshot {
        public final long heapUsed;
        public final long heapMax;
        public final long nonHeapUsed;
        public final long timestamp;

        public MemorySnapshot() {
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
            this.heapUsed = heap.getUsed();
            this.heapMax = heap.getMax();
            this.nonHeapUsed = nonHeap.getUsed();
            this.timestamp = System.currentTimeMillis();
        }

        public long getHeapUsedMB() {
            return heapUsed / 1024 / 1024;
        }

        public long getHeapMaxMB() {
            return heapMax / 1024 / 1024;
        }

        public long getNonHeapUsedMB() {
            return nonHeapUsed / 1024 / 1024;
        }

        public double getHeapUsagePercent() {
            return (double) heapUsed / heapMax * 100.0;
        }

        @Override
        public String toString() {
            return String.format("Memory: Heap=%dMB/%dMB (%.1f%%), NonHeap=%dMB", 
                getHeapUsedMB(), getHeapMaxMB(), getHeapUsagePercent(), getNonHeapUsedMB());
        }
    }

    /**
     * Memory usage comparison between two snapshots.
     */
    public static class MemoryComparison {
        public final MemorySnapshot before;
        public final MemorySnapshot after;
        public final long durationMs;

        public MemoryComparison(MemorySnapshot before, MemorySnapshot after) {
            this.before = before;
            this.after = after;
            this.durationMs = after.timestamp - before.timestamp;
        }

        public long getHeapIncreaseMB() {
            return after.getHeapUsedMB() - before.getHeapUsedMB();
        }

        public long getPeakHeapMB() {
            return Math.max(before.getHeapUsedMB(), after.getHeapUsedMB());
        }

        public double getHeapIncreasePercent() {
            if (before.heapUsed == 0) return 0;
            return (double) (after.heapUsed - before.heapUsed) / before.heapUsed * 100.0;
        }

        @Override
        public String toString() {
            return String.format("Memory Change: +%dMB (%.1f%%), Peak: %dMB, Duration: %dms", 
                getHeapIncreaseMB(), getHeapIncreasePercent(), getPeakHeapMB(), durationMs);
        }
    }

    /**
     * Configure BuildSeriesOptions for different memory optimization scenarios.
     */
    public static void configureForMemoryOptimization(BuildSeriesOptions options, OptimizationLevel level) {
        switch (level) {
            case AGGRESSIVE:
                // Maximum memory savings, slower processing
                options.batchSize = 3;
                options.maxConcurrentTasks = 1;
                options.enableMemoryCleanup = true;
                options.enableGarbageCollection = true;
                options.usePrimitiveArrays = true;
                break;
                
            case CONSERVATIVE:
                // Good memory savings, moderate performance impact
                options.batchSize = 5;
                options.maxConcurrentTasks = 2;
                options.enableMemoryCleanup = true;
                options.enableGarbageCollection = true;
                options.usePrimitiveArrays = true;
                break;
                
            case BALANCED:
                // Default settings - good balance
                options.batchSize = 10;
                options.maxConcurrentTasks = 4;
                options.enableMemoryCleanup = true;
                options.enableGarbageCollection = true;
                options.usePrimitiveArrays = true;
                break;
                
            case PERFORMANCE:
                // Minimal memory optimization, maximum performance
                options.batchSize = 20;
                options.maxConcurrentTasks = 8;
                options.enableMemoryCleanup = false;
                options.enableGarbageCollection = false;
                options.usePrimitiveArrays = true;
                break;
        }
    }

    /**
     * Optimization levels for different use cases.
     */
    public enum OptimizationLevel {
        AGGRESSIVE,      // Maximum memory savings
        CONSERVATIVE,    // Good memory savings
        BALANCED,        // Default balanced approach
        PERFORMANCE      // Maximum performance
    }

    /**
     * Monitor memory usage during processing.
     */
    public static MemorySnapshot takeSnapshot() {
        return new MemorySnapshot();
    }

    /**
     * Compare memory usage between two snapshots.
     */
    public static MemoryComparison compareMemory(MemorySnapshot before, MemorySnapshot after) {
        return new MemoryComparison(before, after);
    }

    /**
     * Print current memory status.
     */
    public static void printMemoryStatus(String label) {
        MemorySnapshot snapshot = takeSnapshot();
        System.out.println(label + ": " + snapshot);
    }

    /**
     * Force garbage collection and wait for completion.
     */
    public static void forceGarbageCollection() {
        System.gc();
        try {
            Thread.sleep(100); // Give GC time to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get memory usage statistics as a formatted string.
     */
    public static String getMemoryStats() {
        MemorySnapshot snapshot = takeSnapshot();
        return String.format("Memory Stats: Heap=%dMB/%dMB (%.1f%%), NonHeap=%dMB", 
            snapshot.getHeapUsedMB(), snapshot.getHeapMaxMB(), 
            snapshot.getHeapUsagePercent(), snapshot.getNonHeapUsedMB());
    }

    /**
     * Validate that memory optimizations are working correctly.
     */
    public static boolean validateMemoryOptimizations(BuildSeriesOptions options) {
        boolean valid = true;
        
        // Check that batch size is reasonable
        if (options.batchSize < 1 || options.batchSize > 100) {
            System.err.println("Warning: batchSize should be between 1 and 100, got: " + options.batchSize);
            valid = false;
        }
        
        // Check that concurrent tasks is reasonable
        if (options.maxConcurrentTasks < 1 || options.maxConcurrentTasks > 16) {
            System.err.println("Warning: maxConcurrentTasks should be between 1 and 16, got: " + options.maxConcurrentTasks);
            valid = false;
        }
        
        // Check that batch size and concurrent tasks are compatible
        if (options.batchSize < options.maxConcurrentTasks) {
            System.err.println("Warning: batchSize should be >= maxConcurrentTasks for optimal performance");
            valid = false;
        }
        
        return valid;
    }

    /**
     * Example usage of memory monitoring.
     */
    public static void exampleUsage() {
        System.out.println("=== Memory Optimization Test Example ===");
        
        // Take initial snapshot
        MemorySnapshot initial = takeSnapshot();
        System.out.println("Initial: " + initial);
        
        // Configure for aggressive memory optimization
        BuildSeriesOptions options = new BuildSeriesOptions();
        configureForMemoryOptimization(options, OptimizationLevel.AGGRESSIVE);
        
        // Validate configuration
        if (validateMemoryOptimizations(options)) {
            System.out.println("Configuration validated successfully");
        }
        
        // Simulate some processing
        System.out.println("Simulating processing...");
        try {
            Thread.sleep(1000); // Simulate work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Take final snapshot
        MemorySnapshot final_snapshot = takeSnapshot();
        MemoryComparison comparison = compareMemory(initial, final_snapshot);
        System.out.println("Result: " + comparison);
        
        // Force cleanup
        forceGarbageCollection();
        MemorySnapshot afterGC = takeSnapshot();
        System.out.println("After GC: " + afterGC);
    }
} 