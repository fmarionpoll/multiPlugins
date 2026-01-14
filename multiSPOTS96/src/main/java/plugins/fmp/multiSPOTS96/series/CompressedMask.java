package plugins.fmp.multiSPOTS96.series;

import java.awt.Point;
import java.util.ArrayList;

/**
 * Compressed mask storage using run-length encoding.
 * 
 * This class provides efficient storage and retrieval of mask coordinates
 * using run-length encoding to reduce memory footprint.
 */
public class CompressedMask {
    private final byte[] compressedData;
    private volatile int[] xCoords; // Lazy decompression
    private volatile int[] yCoords;
    private final int originalSize;

    public CompressedMask(Point[] points) {
        this.originalSize = points.length * 8; // 4 bytes per int, 2 ints per point

        // Compress using run-length encoding for consecutive coordinates
        this.compressedData = compressCoordinates(points);
    }

    public int[] getXCoordinates() {
        if (xCoords == null) {
            decompressData();
        }
        return xCoords;
    }

    public int[] getYCoordinates() {
        if (yCoords == null) {
            decompressData();
        }
        return yCoords;
    }

    public double getCompressionRatio() {
        return (double) compressedData.length / originalSize;
    }

    /**
     * Decompress data on demand
     */
    private synchronized void decompressData() {
        if (xCoords != null && yCoords != null) {
            return; // Already decompressed
        }

        // Decompress the data
        ArrayList<Integer> xList = new ArrayList<>();
        ArrayList<Integer> yList = new ArrayList<>();

        for (int i = 0; i < compressedData.length; i++) {
            byte code = compressedData[i];
            if (code == 0) {
                // New coordinate
                if (i + 4 < compressedData.length) {
                    int x = ((compressedData[i + 1] & 0xFF) << 8) | (compressedData[i + 2] & 0xFF);
                    int y = ((compressedData[i + 3] & 0xFF) << 8) | (compressedData[i + 4] & 0xFF);
                    xList.add(x);
                    yList.add(y);
                    i += 4; // Skip the coordinate bytes
                }
            } else if (code == 1) {
                // Same row, consecutive column
                if (!xList.isEmpty() && !yList.isEmpty()) {
                    int lastX = xList.get(xList.size() - 1);
                    int lastY = yList.get(yList.size() - 1);
                    xList.add(lastX + 1);
                    yList.add(lastY);
                }
            } else if (code == 2) {
                // Same column, consecutive row
                if (!xList.isEmpty() && !yList.isEmpty()) {
                    int lastX = xList.get(xList.size() - 1);
                    int lastY = yList.get(yList.size() - 1);
                    xList.add(lastX);
                    yList.add(lastY + 1);
                }
            }
        }

        // Convert to arrays
        this.xCoords = new int[xList.size()];
        this.yCoords = new int[yList.size()];
        for (int i = 0; i < xList.size(); i++) {
            this.xCoords[i] = xList.get(i);
            this.yCoords[i] = yList.get(i);
        }
    }

    private byte[] compressCoordinates(Point[] points) {
        // Simple run-length encoding for consecutive coordinates
        ArrayList<Byte> compressed = new ArrayList<>();

        for (int i = 0; i < points.length; i++) {
            if (i > 0 && points[i].x == points[i - 1].x + 1 && points[i].y == points[i - 1].y) {
                // Same row, consecutive column
                compressed.add((byte) 1);
            } else if (i > 0 && points[i].x == points[i - 1].x && points[i].y == points[i - 1].y + 1) {
                // Same column, consecutive row
                compressed.add((byte) 2);
            } else {
                // New coordinate
                compressed.add((byte) 0);
                compressed.add((byte) (points[i].x >> 8));
                compressed.add((byte) (points[i].x & 0xFF));
                compressed.add((byte) (points[i].y >> 8));
                compressed.add((byte) (points[i].y & 0xFF));
            }
        }

        byte[] result = new byte[compressed.size()];
        for (int i = 0; i < compressed.size(); i++) {
            result[i] = compressed.get(i);
        }
        return result;
    }
} 