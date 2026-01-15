package plugins.fmp.multiSPOTS96.tools.polyline;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import icy.image.IcyBufferedImage;
import icy.roi.BooleanMask2D;
import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.tools.Logger;

/**
 * Blob detection and analysis utility class for processing binary images. This
 * class provides methods for connected component analysis, blob extraction, and
 * geometric operations on detected blobs.
 * 
 * <p>
 * The class supports:
 * <ul>
 * <li>Connected component labeling using 4-connectivity</li>
 * <li>Blob polygon extraction</li>
 * <li>Boolean mask generation</li>
 * <li>Bounding rectangle computation</li>
 * <li>Hole filling within blobs</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * IcyBufferedImage binaryImage = ...;
 * Blobs blobs = new Blobs(binaryImage);
 * int numBlobs = blobs.getPixelsConnected();
 * blobs.getBlobsConnected();
 * Polygon2D blobPolygon = blobs.getBlobPolygon2D(1);
 * </pre>
 * 
 * @author MultiSPOTS96
 */
public class Blobs {

	/** Minimum blob number (background pixels have value 0) */
	private static final int BACKGROUND_VALUE = 0;

	/** Starting blob number for labeling */
	private static final int FIRST_BLOB_NUMBER = 1;

	/** Polygon deviation parameter for smoothing */
	private static final double POLYGON_DEVIATION = 1.0;

	/** Offset for converting pixel coordinates to polygon coordinates */
	private static final double PIXEL_CENTER_OFFSET = 0.5;

	/** Binary image data as a 1D array */
	private final int[] binaryData;

	/** Image width in pixels */
	private final int imageWidth;

	/** Image height in pixels */
	private final int imageHeight;

	/** Total number of pixels in the image */
	private final int totalPixels;

	/**
	 * Creates a new Blobs instance from a binary image.
	 * 
	 * @param image the binary image to analyze
	 * @throws IllegalArgumentException if image is null or has invalid dimensions
	 */
	public Blobs(IcyBufferedImage image) {
		if (image == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}

		this.imageWidth = image.getSizeX();
		this.imageHeight = image.getSizeY();

		if (imageWidth <= 0 || imageHeight <= 0) {
			throw new IllegalArgumentException("Image dimensions must be positive: " + imageWidth + "x" + imageHeight);
		}

		this.totalPixels = imageWidth * imageHeight;
		this.binaryData = image.getDataXYAsInt(0);

		if (binaryData == null || binaryData.length != totalPixels) {
			throw new IllegalArgumentException("Invalid image data");
		}
	}

	/**
	 * Performs connected component labeling on the binary image. This method
	 * assigns unique labels to connected regions of non-zero pixels.
	 * 
	 * @return the number of distinct blobs found
	 */
	public int getPixelsConnected() {
		int currentBlobNumber = FIRST_BLOB_NUMBER;

		try {
			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					int currentIndex = getPixelIndex(x, y);

					if (binaryData[currentIndex] <= BACKGROUND_VALUE) {
						continue;
					}

					// Check neighboring pixels and assign label
					int assignedLabel = findNeighborLabel(x, y);

					if (assignedLabel > BACKGROUND_VALUE) {
						binaryData[currentIndex] = assignedLabel;
					} else {
						// New blob found
						binaryData[currentIndex] = currentBlobNumber;
						currentBlobNumber++;
					}
				}
			}

			int numBlobs = currentBlobNumber - FIRST_BLOB_NUMBER;
			return numBlobs;

		} catch (Exception e) {
			Logger.error("Error during connected component labeling", e);
			return 0;
		}
	}

	/**
	 * Merges connected components that were initially labeled separately. This
	 * method handles cases where components are connected through diagonal
	 * neighbors that weren't detected in the initial labeling pass.
	 */
	public void getBlobsConnected() {
		try {
			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					int currentIndex = getPixelIndex(x, y);

					if (binaryData[currentIndex] <= BACKGROUND_VALUE) {
						continue;
					}

					int currentValue = binaryData[currentIndex];

					// Check all 8 neighbors for different labels
					mergeWithNeighbors(x, y, currentValue);
				}
			}
		} catch (Exception e) {
			Logger.error("Error during blob merging", e);
		}
	}

	/**
	 * Fills gaps within blobs by setting all pixels between the first and last
	 * occurrence of a blob on each row to the same blob value.
	 */
	public void fillBlanksPixelsWithinBlobs() {
		try {
			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					int currentIndex = getPixelIndex(x, y);
					int blobValue = binaryData[currentIndex];

					if (blobValue <= BACKGROUND_VALUE) {
						continue;
					}

					// Find the extent of this blob on the current row
					int firstX = x;
					int lastX = findLastOccurrenceInRow(y, x, blobValue);

					// Fill all pixels between first and last occurrence
					for (int fillX = firstX; fillX <= lastX; fillX++) {
						binaryData[getPixelIndex(fillX, y)] = blobValue;
					}

					// Skip to the end of this blob to avoid redundant processing
					x = lastX;
				}
			}
		} catch (Exception e) {
			Logger.error("Error during hole filling", e);
		}
	}

	/**
	 * Gets the blob number at the specified coordinates.
	 * 
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @return the blob number at the specified position
	 * @throws IllegalArgumentException if coordinates are out of bounds
	 */
	public int getBlobAt(int x, int y) {
		if (!isValidCoordinate(x, y)) {
			throw new IllegalArgumentException("Coordinates out of bounds: (" + x + ", " + y + ")");
		}

		return binaryData[getPixelIndex(x, y)];
	}

	/**
	 * Gets a list of all unique blob numbers in the image.
	 * 
	 * @return a list of unique blob numbers, excluding background (0)
	 */
	public List<Integer> getListOfBlobs() {
		Set<Integer> uniqueBlobs = new HashSet<>();

		for (int value : binaryData) {
			if (value > BACKGROUND_VALUE) {
				uniqueBlobs.add(value);
			}
		}

		List<Integer> result = new ArrayList<>(uniqueBlobs);
		Collections.sort(result);
		return result;
	}

//	/**
//	 * @deprecated Use {@link #getListOfBlobs()} instead
//	 */
//	@Deprecated
//	public List<Integer> getListOfBlobs(int[] binaryData) {
//		Logger.warn("Using deprecated method getListOfBlobs(int[])");
//		return getListOfBlobs();
//	}

	/**
	 * Creates a polygon representation of the specified blob's boundary.
	 * 
	 * @param blobNumber the blob number to extract
	 * @return a Polygon2D representing the blob boundary
	 * @throws IllegalArgumentException if blobNumber is invalid
	 */
	public Polygon2D getBlobPolygon2D(int blobNumber) {
		if (blobNumber <= BACKGROUND_VALUE) {
			throw new IllegalArgumentException("Invalid blob number: " + blobNumber);
		}

		try {
			List<Point> leftBoundary = new ArrayList<>();
			List<Point> rightBoundary = new ArrayList<>();

			// Extract left and right boundaries for each row
			for (int y = 0; y < imageHeight; y++) {
				Point leftPoint = null;
				Point rightPoint = null;

				for (int x = 0; x < imageWidth; x++) {
					if (binaryData[getPixelIndex(x, y)] == blobNumber) {
						if (leftPoint == null) {
							leftPoint = new Point(x, y);
						}
						rightPoint = new Point(x, y);
					}
				}

				if (leftPoint != null) {
					leftBoundary.add(leftPoint);
					if (!leftPoint.equals(rightPoint)) {
						rightBoundary.add(rightPoint);
					}
				}
			}

			// Combine boundaries to form polygon
			List<Point> allBoundaryPoints = new ArrayList<>();
			allBoundaryPoints.addAll(leftBoundary);

			// Add right boundary in reverse order
			Collections.reverse(rightBoundary);
			allBoundaryPoints.addAll(rightBoundary);

			// Convert to Point2D with pixel center offset
			List<Point2D> polygonPoints = new ArrayList<>(allBoundaryPoints.size());
			for (Point point : allBoundaryPoints) {
				polygonPoints.add(new Point2D.Double(point.x + PIXEL_CENTER_OFFSET, point.y + PIXEL_CENTER_OFFSET));
			}

			return Polygon2D.getPolygon2D(polygonPoints, POLYGON_DEVIATION);

		} catch (Exception e) {
			Logger.error("Error creating polygon for blob " + blobNumber, e);
			return new Polygon2D();
		}
	}

	/**
	 * Creates a boolean mask for the specified blob.
	 * 
	 * @param blobNumber the blob number to extract
	 * @return a BooleanMask2D representing the blob
	 * @throws IllegalArgumentException if blobNumber is invalid
	 */
	public BooleanMask2D getBlobBooleanMask2D(int blobNumber) {
		if (blobNumber <= BACKGROUND_VALUE) {
			throw new IllegalArgumentException("Invalid blob number: " + blobNumber);
		}

		try {
			List<Point> blobPoints = new ArrayList<>();

			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					if (binaryData[getPixelIndex(x, y)] == blobNumber) {
						blobPoints.add(new Point(x, y));
					}
				}
			}

			if (blobPoints.isEmpty()) {
				Logger.warn("No pixels found for blob " + blobNumber);
				return new BooleanMask2D(new Point[0]);
			}

			Point[] pointArray = blobPoints.toArray(new Point[0]);
			return new BooleanMask2D(pointArray);

		} catch (Exception e) {
			Logger.error("Error creating boolean mask for blob " + blobNumber, e);
			return new BooleanMask2D(new Point[0]);
		}
	}

	/**
	 * Calculates the bounding rectangle of the specified blob.
	 * 
	 * @param blobNumber the blob number to analyze
	 * @return a Rectangle representing the blob's bounding box
	 * @throws IllegalArgumentException if blobNumber is invalid
	 */
	public Rectangle getBlobRectangle(int blobNumber) {
		if (blobNumber <= BACKGROUND_VALUE) {
			throw new IllegalArgumentException("Invalid blob number: " + blobNumber);
		}

		try {
			int minX = imageWidth;
			int maxX = -1;
			int minY = imageHeight;
			int maxY = -1;

			boolean blobFound = false;

			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					if (binaryData[getPixelIndex(x, y)] == blobNumber) {
						blobFound = true;
						minX = Math.min(minX, x);
						maxX = Math.max(maxX, x);
						minY = Math.min(minY, y);
						maxY = Math.max(maxY, y);
					}
				}
			}

			if (!blobFound) {
				Logger.warn("No pixels found for blob " + blobNumber);
				return new Rectangle(0, 0, 0, 0);
			}

			return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);

		} catch (Exception e) {
			Logger.error("Error calculating rectangle for blob " + blobNumber, e);
			return new Rectangle(0, 0, 0, 0);
		}
	}

	/**
	 * Gets the image dimensions.
	 * 
	 * @return a Point containing width and height
	 */
	public Point getImageDimensions() {
		return new Point(imageWidth, imageHeight);
	}

	/**
	 * Gets the total number of pixels in the image.
	 * 
	 * @return the total pixel count
	 */
	public int getTotalPixels() {
		return totalPixels;
	}

	// Private helper methods

	/**
	 * Converts 2D coordinates to 1D array index.
	 */
	private int getPixelIndex(int x, int y) {
		return x + imageWidth * y;
	}

	/**
	 * Checks if the given coordinates are within image bounds.
	 */
	private boolean isValidCoordinate(int x, int y) {
		return x >= 0 && x < imageWidth && y >= 0 && y < imageHeight;
	}

	/**
	 * Finds a neighboring pixel label for connected component labeling.
	 */
	private int findNeighborLabel(int x, int y) {
		// Check 4-connectivity neighbors in order: up, up-left, up-right, left

		// Check up (x, y-1)
		if (y > 0 && binaryData[getPixelIndex(x, y - 1)] > BACKGROUND_VALUE) {
			return binaryData[getPixelIndex(x, y - 1)];
		}

		// Check up-left (x-1, y-1)
		if (x > 0 && y > 0 && binaryData[getPixelIndex(x - 1, y - 1)] > BACKGROUND_VALUE) {
			return binaryData[getPixelIndex(x - 1, y - 1)];
		}

		// Check up-right (x+1, y-1)
		if (x < imageWidth - 1 && y > 0 && binaryData[getPixelIndex(x + 1, y - 1)] > BACKGROUND_VALUE) {
			return binaryData[getPixelIndex(x + 1, y - 1)];
		}

		// Check left (x-1, y)
		if (x > 0 && binaryData[getPixelIndex(x - 1, y)] > BACKGROUND_VALUE) {
			return binaryData[getPixelIndex(x - 1, y)];
		}

		return BACKGROUND_VALUE;
	}

	/**
	 * Merges the current pixel with neighboring pixels that have different labels.
	 */
	private void mergeWithNeighbors(int x, int y, int currentValue) {
		// Check all 8 neighbors
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dy == 0)
					continue; // Skip center pixel

				int neighborX = x + dx;
				int neighborY = y + dy;

				if (isValidCoordinate(neighborX, neighborY)) {
					int neighborValue = binaryData[getPixelIndex(neighborX, neighborY)];

					if (neighborValue > BACKGROUND_VALUE && neighborValue != currentValue) {
						// Merge by changing all pixels with neighborValue to currentValue
						changeAllBlobNumber(neighborValue, currentValue);
					}
				}
			}
		}
	}

	/**
	 * Changes all pixels with oldValue to newValue.
	 */
	private void changeAllBlobNumber(int oldValue, int newValue) {
		for (int i = 0; i < binaryData.length; i++) {
			if (binaryData[i] == oldValue) {
				binaryData[i] = newValue;
			}
		}
	}

	/**
	 * Finds the last occurrence of a blob value in a row.
	 */
	private int findLastOccurrenceInRow(int y, int startX, int blobValue) {
		int lastX = startX;

		for (int x = startX; x < imageWidth; x++) {
			if (binaryData[getPixelIndex(x, y)] == blobValue) {
				lastX = x;
			}
		}

		return lastX;
	}
}
