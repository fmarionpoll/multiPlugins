package plugins.fmp.multitools.fmp_tools.polyline;

import java.awt.geom.Point2D;
import java.util.List;
import plugins.fmp.multitools.fmp_tools.Logger;

import icy.type.geom.Polyline2D;

/**
 * Extended polyline class for 2D level operations and data manipulation. This
 * class extends Icy's Polyline2D and provides additional functionality for
 * working with level data, including resizing, and specialized data processing
 * methods.
 * 
 * <p>
 * The Level2D class is particularly useful for:
 * <ul>
 * <li>Processing measurement data along lines</li>
 * <li>Resizing polylines to different widths</li>
 * <li>Performing mathematical operations on Y-coordinates</li>
 * <li>Computing derived values like polarization index</li>
 * <li>Threshold operations on level data</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * // Create a level from a list of points
 * Level2D level = new Level2D(pointsList);
 * 
 * // Resize to a new width
 * Level2D resized = level.expandPolylineToNewWidth(1000);
 *
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see icy.type.geom.Polyline2D
 */
public class Level2D extends Polyline2D {


	/**
	 * Creates an empty Level2D polyline.
	 */
	public Level2D() {
		super();
	}

	/**
	 * Creates a Level2D with the specified number of points. X-coordinates are
	 * automatically set to sequential values (0, 1, 2, ...).
	 * 
	 * @param numPoints the number of points in the polyline
	 * @throws IllegalArgumentException if numPoints is negative
	 */
	public Level2D(int numPoints) {
		if (numPoints < 0) {
			throw new IllegalArgumentException("Number of points cannot be negative: " + numPoints);
		}

		this.npoints = numPoints;
		this.xpoints = new double[numPoints];
		this.ypoints = new double[numPoints];

		// Initialize X coordinates to sequential values
		for (int i = 0; i < numPoints; i++) {
			xpoints[i] = i;
		}
	}

	/**
	 * Creates a Level2D from an existing Polyline2D.
	 * 
	 * @param polyline the source polyline
	 * @throws IllegalArgumentException if polyline is null
	 */
	public Level2D(Polyline2D polyline) {
		if (polyline == null) {
			throw new IllegalArgumentException("Source polyline cannot be null");
		}

		this.npoints = polyline.npoints;
		this.xpoints = new double[npoints];
		this.ypoints = new double[npoints];

		System.arraycopy(polyline.xpoints, 0, this.xpoints, 0, npoints);
		System.arraycopy(polyline.ypoints, 0, this.ypoints, 0, npoints);
	}

	/**
	 * Creates a Level2D from arrays of coordinates.
	 * 
	 * @param xPoints   array of x-coordinates
	 * @param yPoints   array of y-coordinates
	 * @param numPoints number of points to use
	 * @throws IllegalArgumentException if arrays are null, numPoints is invalid, or
	 *                                  arrays are too small
	 */
	public Level2D(double[] xPoints, double[] yPoints, int numPoints) {
		if (xPoints == null || yPoints == null) {
			throw new IllegalArgumentException("Coordinate arrays cannot be null");
		}
		if (numPoints < 0) {
			throw new IllegalArgumentException("Number of points cannot be negative: " + numPoints);
		}
		if (xPoints.length < numPoints || yPoints.length < numPoints) {
			throw new IllegalArgumentException("Arrays are too small for the specified number of points");
		}

		this.npoints = numPoints;
		this.xpoints = new double[numPoints];
		this.ypoints = new double[numPoints];

		System.arraycopy(xPoints, 0, this.xpoints, 0, numPoints);
		System.arraycopy(yPoints, 0, this.ypoints, 0, numPoints);
	}

	/**
	 * Creates a Level2D from arrays of integer coordinates.
	 * 
	 * @param xPoints   array of x-coordinates
	 * @param yPoints   array of y-coordinates
	 * @param numPoints number of points to use
	 * @throws IllegalArgumentException if arrays are null, numPoints is invalid, or
	 *                                  arrays are too small
	 */
	public Level2D(int[] xPoints, int[] yPoints, int numPoints) {
		if (xPoints == null || yPoints == null) {
			throw new IllegalArgumentException("Coordinate arrays cannot be null");
		}
		if (numPoints < 0) {
			throw new IllegalArgumentException("Number of points cannot be negative: " + numPoints);
		}
		if (xPoints.length < numPoints || yPoints.length < numPoints) {
			throw new IllegalArgumentException("Arrays are too small for the specified number of points");
		}

		this.npoints = numPoints;
		this.xpoints = new double[numPoints];
		this.ypoints = new double[numPoints];

		for (int i = 0; i < numPoints; i++) {
			this.xpoints[i] = xPoints[i];
			this.ypoints[i] = yPoints[i];
		}
	}

	/**
	 * Creates a Level2D from a list of Point2D objects.
	 * 
	 * @param pointsList the list of points
	 * @throws IllegalArgumentException if pointsList is null
	 */
	public Level2D(List<Point2D> pointsList) {
		if (pointsList == null) {
			throw new IllegalArgumentException("Points list cannot be null");
		}

		this.npoints = pointsList.size();
		this.xpoints = new double[npoints];
		this.ypoints = new double[npoints];

		for (int i = 0; i < npoints; i++) {
			Point2D point = pointsList.get(i);
			if (point == null) {
				throw new IllegalArgumentException("Point at index " + i + " is null");
			}
			this.xpoints[i] = point.getX();
			this.ypoints[i] = point.getY();
		}
	}

	@Override
	public Level2D clone() {
		Level2D cloned = new Level2D(npoints);
		System.arraycopy(this.xpoints, 0, cloned.xpoints, 0, npoints);
		System.arraycopy(this.ypoints, 0, cloned.ypoints, 0, npoints);
		return cloned;
	}

	/**
	 * Expands the polyline to a new width using linear interpolation.
	 * 
	 * @param imageWidth the new width
	 * @return a new Level2D with the expanded width
	 * @throws IllegalArgumentException if imageWidth is not positive
	 */
	public Level2D expandPolylineToNewWidth(int imageWidth) {
		if (imageWidth <= 0) {
			throw new IllegalArgumentException("Image width must be positive: " + imageWidth);
		}

		if (npoints <= 0) {
			return new Level2D(imageWidth);
		}

		try {
			double[] newXPoints = new double[imageWidth];
			double[] newYPoints = new double[imageWidth];

			for (int j = 0; j < npoints; j++) {
				int startIndex = j * imageWidth / npoints;
				int endIndex = (j + 1) * imageWidth / npoints;

				double currentY = ypoints[j];
				double nextY = (j + 1 < npoints) ? ypoints[j + 1] : currentY;

				for (int i = startIndex; i < endIndex && i < imageWidth; i++) {
					newXPoints[i] = i;
					if (endIndex > startIndex) {
						double ratio = (double) (i - startIndex) / (endIndex - startIndex);
						newYPoints[i] = currentY + (nextY - currentY) * ratio;
					} else {
						newYPoints[i] = currentY;
					}
				}
			}

			return new Level2D(newXPoints, newYPoints, imageWidth);

		} catch (Exception e) {
			Logger.error("Error expanding polyline", e);
			return new Level2D(imageWidth);
		}
	}

	/**
	 * Crops the polyline to a new width, padding with the last value if necessary.
	 * 
	 * @param imageWidth the new width
	 * @return a new Level2D with the cropped width
	 * @throws IllegalArgumentException if imageWidth is not positive
	 */
	public Level2D cropPolylineToNewWidth(int imageWidth) {
		if (imageWidth <= 0) {
			throw new IllegalArgumentException("Image width must be positive: " + imageWidth);
		}

		try {
			double[] newXPoints = new double[imageWidth];
			double[] newYPoints = new double[imageWidth];

			double lastValue = (npoints > 0) ? ypoints[npoints - 1] : 0.0;

			for (int i = 0; i < imageWidth; i++) {
				newXPoints[i] = i;
				newYPoints[i] = (i < npoints) ? ypoints[i] : lastValue;
			}

			return new Level2D(newXPoints, newYPoints, imageWidth);

		} catch (Exception e) {
			Logger.error("Error cropping polyline", e);
			return new Level2D(imageWidth);
		}
	}

	public double getMaximum_Y() {
		double maximum = ypoints[0];
		for (int i = 0; i < npoints; i++) {
			if (ypoints[i] > maximum)
				maximum = ypoints[i];
		}
		return maximum;
	}

	public int getPointCount() {
		return npoints;
	}

	public double getYAt(int index) {
		if (index < 0 || index >= npoints) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds [0, " + npoints + ")");
		}
		return ypoints[index];
	}

	public void setYAt(int index, double value) {
		if (index < 0 || index >= npoints) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds [0, " + npoints + ")");
		}
		ypoints[index] = value;
	}

}
