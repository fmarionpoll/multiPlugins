package plugins.fmp.multiSPOTS96.series;

import icy.image.IcyBufferedImage;

/**
 * Memory usage monitor.
 * 
 * This class provides utilities for monitoring and managing memory usage
 * during image processing operations.
 */
public class MemoryMonitor {
    private final Runtime runtime = Runtime.getRuntime();

    public long getTotalMemoryMB() {
        return runtime.totalMemory() / 1024 / 1024;
    }

    public long getFreeMemoryMB() {
        return runtime.freeMemory() / 1024 / 1024;
    }

    public long getUsedMemoryMB() {
        return getTotalMemoryMB() - getFreeMemoryMB();
    }

    public long getMaxMemoryMB() {
        return runtime.maxMemory() / 1024 / 1024;
    }

    public double getMemoryUsagePercent() {
        return (double) getUsedMemoryMB() / getMaxMemoryMB() * 100.0;
    }

    public long getAvailableMemoryMB() {
        return getMaxMemoryMB() - getUsedMemoryMB();
    }

    /**
     * Estimate memory footprint of an IcyBufferedImage
     */
    public long estimateImageMemoryFootprint(IcyBufferedImage image) {
        if (image == null)
            return 0;

        long footprint = 0;

        // Base object size (rough estimate)
        footprint += 64; // Object header + basic fields

        // Image dimensions
        int sizeX = image.getSizeX();
        int sizeY = image.getSizeY();
        int sizeC = image.getSizeC();
        // Note: getSizeT() and getSizeZ() don't exist on IcyBufferedImage

        // Data arrays
        for (int c = 0; c < sizeC; c++) {
            Object data = image.getDataXY(c);
            if (data != null) {
                if (data instanceof byte[]) {
                    footprint += ((byte[]) data).length;
                } else if (data instanceof short[]) {
                    footprint += ((short[]) data).length * 2;
                } else if (data instanceof int[]) {
                    footprint += ((int[]) data).length * 4;
                } else if (data instanceof float[]) {
                    footprint += ((float[]) data).length * 4;
                } else if (data instanceof double[]) {
                    footprint += ((double[]) data).length * 8;
                }
            }
        }

        // Convert to MB
        return footprint / 1024 / 1024;
    }
} 