package plugins.fmp.multitools.tools.polyline;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import icy.gui.frame.progress.AnnounceFrame;
import icy.type.geom.Polygon2D;
import plugins.fmp.multitools.tools.Logger;

/**
 * Utility class for polygon operations and geometric calculations. This class
 * provides static methods for polygon manipulation, grid generation, and
 * geometric transformations commonly used in image analysis and ROI processing.
 * 
 * <p>
 * The PolygonUtilities class provides:
 * <ul>
 * <li>Polygon vertex ordering and validation</li>
 * <li>Polygon inflation and deflation operations</li>
 * <li>Grid generation within polygonal regions</li>
 * <li>Line intersection calculations</li>
 * <li>Coordinate transformation utilities</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * // Order vertices of a 4-corner polygon
 * Polygon2D ordered = PolygonUtilities.orderVerticesOf4CornersPolygon(roiPolygon);
 * 
 * // Create a grid inside the polygon
 * Point2D.Double[][] grid = PolygonUtilities.createGridInsidePolygon(ordered, 5, 5);
 * 
 * // Inflate the polygon
 * Polygon2D inflated = PolygonUtilities.inflatePolygon(ordered, 10, 10, 100, 20, 100, 20);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see icy.type.geom.Polygon2D
 * @see java.awt.Polygon
 * @see java.awt.geom.Point2D
 */
public final class PolygonUtilities {

	/** Required number of vertices for 4-corner polygon operations */
	private static final int REQUIRED_VERTICES = 4;

	/** Minimum number of columns/rows for grid operations */
	private static final int MIN_GRID_SIZE = 1;

	/** Epsilon for floating-point comparisons */
	private static final double EPSILON = 1e-10;

	/** Factor for rectangle subdivision in vertex ordering */
	private static final int SUBDIVISION_FACTOR = 2;

	/** Offset for rectangle positioning */
	private static final int RECTANGLE_OFFSET = 2;

	/**
	 * Private constructor to prevent instantiation of utility class.
	 */
	private PolygonUtilities() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * Orders the vertices of a 4-corner polygon in a consistent sequence. The
	 * vertices are ordered as: upper-left, lower-left, lower-right, upper-right.
	 * 
	 * <p>
	 * This method analyzes the bounding rectangle of the polygon and assigns
	 * vertices to quadrants to ensure consistent ordering. If the polygon has more
	 * than 4 vertices, only the first 4 are used and a warning is displayed.
	 * </p>
	 * 
	 * @param roiPolygon the input polygon with at least 4 vertices
	 * @return a new Polygon2D with vertices ordered consistently
	 * @throws IllegalArgumentException if roiPolygon is null or has fewer than 4
	 *                                  vertices
	 */
	public static Polygon2D orderVerticesOf4CornersPolygon(Polygon roiPolygon) {
		if (roiPolygon == null) {
			throw new IllegalArgumentException("ROI polygon cannot be null");
		}

		if (roiPolygon.npoints < REQUIRED_VERTICES) {
			throw new IllegalArgumentException("Polygon must have at least 4 vertices, got: " + roiPolygon.npoints);
		}

		if (roiPolygon.npoints > REQUIRED_VERTICES) {
			new AnnounceFrame("Only the first 4 points of the polygon will be used...");
		}

		try {
			Polygon2D orderedPolygon = new Polygon2D();
			Rectangle bounds = roiPolygon.getBounds();

			// Create quadrant rectangles
			Rectangle upperLeft = new Rectangle(bounds.x, bounds.y, bounds.width / SUBDIVISION_FACTOR,
					bounds.height / SUBDIVISION_FACTOR);

			Rectangle lowerLeft = new Rectangle(bounds.x,
					bounds.y + bounds.height / SUBDIVISION_FACTOR + RECTANGLE_OFFSET, bounds.width / SUBDIVISION_FACTOR,
					bounds.height / SUBDIVISION_FACTOR);

			Rectangle lowerRight = new Rectangle(bounds.x + bounds.width / SUBDIVISION_FACTOR + RECTANGLE_OFFSET,
					bounds.y + bounds.height / SUBDIVISION_FACTOR + RECTANGLE_OFFSET, bounds.width / SUBDIVISION_FACTOR,
					bounds.height / SUBDIVISION_FACTOR);

			Rectangle upperRight = new Rectangle(bounds.x + bounds.width / SUBDIVISION_FACTOR + RECTANGLE_OFFSET,
					bounds.y, bounds.width / SUBDIVISION_FACTOR, bounds.height / SUBDIVISION_FACTOR);

			// Find vertices in each quadrant
			addVertexInQuadrant(orderedPolygon, roiPolygon, upperLeft, "upper-left");
			addVertexInQuadrant(orderedPolygon, roiPolygon, lowerLeft, "lower-left");
			addVertexInQuadrant(orderedPolygon, roiPolygon, lowerRight, "lower-right");
			addVertexInQuadrant(orderedPolygon, roiPolygon, upperRight, "upper-right");

			return orderedPolygon;

		} catch (Exception e) {
			Logger.error("Error ordering polygon vertices", e);
			return new Polygon2D();
		}
	}

	/**
	 * Inflates a polygon by expanding its boundaries based on grid parameters. This
	 * method is useful for creating expanded ROIs that accommodate grid layouts
	 * with specified cage sizes and intervals.
	 * 
	 * @param roiPolygon     the input 4-corner polygon
	 * @param numColumns     number of columns in the grid
	 * @param numRows        number of rows in the grid
	 * @param cageWidth      width of each cage/cell
	 * @param cageHeight     height of each cage/cell
	 * @param widthInterval  horizontal spacing between cages
	 * @param heightInterval vertical spacing between cages
	 * @return a new inflated Polygon2D
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public static Polygon2D inflatePolygon(Polygon2D roiPolygon, int numColumns, int numRows, int cageWidth,
			int cageHeight, int widthInterval, int heightInterval) {
		validateInflationParameters(roiPolygon, numColumns, numRows, cageWidth, cageHeight, widthInterval,
				heightInterval);

		try {
			// Calculate current dimensions
			double currentWidth = numColumns * (cageWidth + 2 * widthInterval) - 2 * widthInterval;
			double currentHeight = numRows * (cageHeight + 2 * heightInterval) - 2 * heightInterval;

			// Calculate horizontal deltas
			double topDeltaX = (roiPolygon.xpoints[3] - roiPolygon.xpoints[0]) * widthInterval / currentWidth;
			double bottomDeltaX = (roiPolygon.xpoints[2] - roiPolygon.xpoints[1]) * widthInterval / currentWidth;

			// Calculate vertical deltas
			double leftDeltaY = (roiPolygon.ypoints[1] - roiPolygon.ypoints[0]) * heightInterval / currentHeight;
			double rightDeltaY = (roiPolygon.ypoints[2] - roiPolygon.ypoints[3]) * heightInterval / currentHeight;

			// Create new polygon with inflated coordinates
			double[] newXPoints = new double[REQUIRED_VERTICES];
			double[] newYPoints = new double[REQUIRED_VERTICES];

			// Calculate inflated vertices
			newXPoints[0] = roiPolygon.xpoints[0] - topDeltaX; // upper-left
			newXPoints[1] = roiPolygon.xpoints[1] - bottomDeltaX; // lower-left
			newXPoints[2] = roiPolygon.xpoints[2] + bottomDeltaX; // lower-right
			newXPoints[3] = roiPolygon.xpoints[3] + topDeltaX; // upper-right

			newYPoints[0] = roiPolygon.ypoints[0] - leftDeltaY; // upper-left
			newYPoints[1] = roiPolygon.ypoints[1] + leftDeltaY; // lower-left
			newYPoints[2] = roiPolygon.ypoints[2] + rightDeltaY; // lower-right
			newYPoints[3] = roiPolygon.ypoints[3] - rightDeltaY; // upper-right

			return new Polygon2D(newXPoints, newYPoints, REQUIRED_VERTICES);

		} catch (Exception e) {
			Logger.error("Error inflating polygon", e);
			return new Polygon2D();
		}
	}

//    /**
//     * @deprecated Use {@link #inflatePolygon(Polygon2D, int, int, int, int, int, int)} instead.
//     * This method is kept for backward compatibility.
//     */
//    @Deprecated
//    public static Polygon2D inflate(Polygon2D roiPolygon, int ncolumns, int nrows, int width_cage, int width_interval) {
//        Logger.warn("Using deprecated method inflate(). Use inflatePolygon() instead.");
//        return inflatePolygon(roiPolygon, ncolumns, nrows, width_cage, width_cage, width_interval, width_interval);
//    }

//	/**
//	 * @deprecated Use
//	 *             {@link #inflatePolygon(Polygon2D, int, int, int, int, int, int)}
//	 *             instead. This method is kept for backward compatibility.
//	 */
//	@Deprecated
//	public static Polygon2D inflate2(Polygon2D roiPolygon, int ncolumns, int width_cage, int width_interval, int nrows,
//			int height_cage, int height_interval) {
//		Logger.warn("Using deprecated method inflate2(). Use inflatePolygon() instead.");
//		return inflatePolygon(roiPolygon, ncolumns, nrows, width_cage, height_cage, width_interval, height_interval);
//	}

	/**
	 * Divides a 4-corner polygon into a grid of points. This method creates a
	 * regular grid of coordinate points within the polygon by interpolating between
	 * the polygon's edges.
	 * 
	 * @param roiPolygon the input 4-corner polygon
	 * @param numColumns number of columns in the grid
	 * @param numRows    number of rows in the grid
	 * @return a list of Point2D.Double objects representing the grid points
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public static ArrayList<Point2D.Double> divide4CornersPolygon(Polygon2D roiPolygon, int numColumns, int numRows) {
		if (roiPolygon == null) {
			throw new IllegalArgumentException("ROI polygon cannot be null");
		}

		if (roiPolygon.npoints != REQUIRED_VERTICES) {
			throw new IllegalArgumentException("Polygon must have exactly 4 vertices, got: " + roiPolygon.npoints);
		}

		if (numColumns < MIN_GRID_SIZE || numRows < MIN_GRID_SIZE) {
			throw new IllegalArgumentException(
					"Grid dimensions must be at least 1x1, got: " + numColumns + "x" + numRows);
		}

		try {
			ArrayList<Point2D.Double> gridPoints = new ArrayList<>((numColumns + 1) * (numRows + 1));

			// Generate grid points by interpolating between polygon edges
			for (int row = 0; row <= numRows; row++) {
				double rowRatio = (numRows > 0) ? (double) row / numRows : 0.0;

				// Interpolate left edge
				Point2D.Double leftEdgePoint = interpolatePoint(roiPolygon.xpoints[0], roiPolygon.ypoints[0], // upper-left
						roiPolygon.xpoints[1], roiPolygon.ypoints[1], // lower-left
						rowRatio);

				// Interpolate right edge
				Point2D.Double rightEdgePoint = interpolatePoint(roiPolygon.xpoints[3], roiPolygon.ypoints[3], // upper-right
						roiPolygon.xpoints[2], roiPolygon.ypoints[2], // lower-right
						rowRatio);

				// Generate points along the current row
				for (int col = 0; col <= numColumns; col++) {
					double colRatio = (numColumns > 0) ? (double) col / numColumns : 0.0;

					Point2D.Double gridPoint = interpolatePoint(leftEdgePoint.x, leftEdgePoint.y, rightEdgePoint.x,
							rightEdgePoint.y, colRatio);

					gridPoints.add(gridPoint);
				}
			}

			return gridPoints;

		} catch (Exception e) {
			Logger.error("Error dividing polygon into grid", e);
			return new ArrayList<>();
		}
	}

	/**
	 * Finds the intersection point of two lines defined by their endpoints. This
	 * method calculates the intersection of two infinite lines (not segments).
	 * 
	 * @param x1 x-coordinate of first point on first line
	 * @param y1 y-coordinate of first point on first line
	 * @param x2 x-coordinate of second point on first line
	 * @param y2 y-coordinate of second point on first line
	 * @param x3 x-coordinate of first point on second line
	 * @param y3 y-coordinate of first point on second line
	 * @param x4 x-coordinate of second point on second line
	 * @param y4 y-coordinate of second point on second line
	 * @return the intersection point, or null if lines are parallel
	 */
	public static Point2D lineIntersect(double x1, double y1, double x2, double y2, double x3, double y3, double x4,
			double y4) {
		try {
			double denominator = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);

			// Check for parallel lines
			if (Math.abs(denominator) < EPSILON) {
				return null;
			}

			double numeratorA = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3);
			double numeratorB = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3);

			double ua = numeratorA / denominator;
			double ub = numeratorB / denominator;

			// Check if intersection is within both line segments
			if (ua >= 0.0 && ua <= 1.0 && ub >= 0.0 && ub <= 1.0) {
				double intersectionX = x1 + ua * (x2 - x1);
				double intersectionY = y1 + ua * (y2 - y1);
				return new Point2D.Double(intersectionX, intersectionY);
			}

			return null;

		} catch (Exception e) {
			Logger.error("Error calculating line intersection", e);
			return null;
		}
	}

	/**
	 * Creates a regular grid of points inside a 4-sided polygon. This method
	 * generates a 2D array of points that form a regular grid within the boundaries
	 * of the specified polygon.
	 * 
	 * @param roiPolygon the 4-sided polygon to fill with grid points
	 * @param numColumns number of columns in the grid
	 * @param numRows    number of rows in the grid
	 * @return a 2D array of Point2D.Double objects representing the grid
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public static Point2D.Double[][] createGridInsidePolygon(Polygon2D roiPolygon, int numColumns, int numRows) {
		if (roiPolygon == null) {
			throw new IllegalArgumentException("ROI polygon cannot be null");
		}

		if (roiPolygon.npoints != REQUIRED_VERTICES) {
			throw new IllegalArgumentException("Polygon must have exactly 4 vertices, got: " + roiPolygon.npoints);
		}

		if (numColumns <= 0 || numRows <= 0) {
			throw new IllegalArgumentException("Grid dimensions must be positive, got: " + numColumns + "x" + numRows);
		}

		try {
			Point2D.Double[][] gridPoints = new Point2D.Double[numColumns][numRows];

			// Generate grid points by bilinear interpolation
			for (int col = 0; col < numColumns; col++) {
				double colRatio = (numColumns > 1) ? (double) col / (numColumns - 1) : 0.0;

				// Interpolate top edge
				Point2D.Double topEdgePoint = interpolatePoint(roiPolygon.xpoints[0], roiPolygon.ypoints[0], // upper-left
						roiPolygon.xpoints[3], roiPolygon.ypoints[3], // upper-right
						colRatio);

				// Interpolate bottom edge
				Point2D.Double bottomEdgePoint = interpolatePoint(roiPolygon.xpoints[1], roiPolygon.ypoints[1], // lower-left
						roiPolygon.xpoints[2], roiPolygon.ypoints[2], // lower-right
						colRatio);

				// Generate points along the current column
				for (int row = 0; row < numRows; row++) {
					double rowRatio = (numRows > 1) ? (double) row / (numRows - 1) : 0.0;

					Point2D.Double gridPoint = interpolatePoint(topEdgePoint.x, topEdgePoint.y, bottomEdgePoint.x,
							bottomEdgePoint.y, rowRatio);

					gridPoints[col][row] = gridPoint;
				}
			}

			return gridPoints;

		} catch (Exception e) {
			Logger.error("Error creating grid inside polygon", e);
			return new Point2D.Double[0][0];
		}
	}

	/**
	 * Calculates the area of a polygon using the shoelace formula.
	 * 
	 * @param polygon the polygon to calculate area for
	 * @return the area of the polygon
	 * @throws IllegalArgumentException if polygon is null
	 */
	public static double calculatePolygonArea(Polygon2D polygon) {
		if (polygon == null) {
			throw new IllegalArgumentException("Polygon cannot be null");
		}

		if (polygon.npoints < 3) {
			return 0.0;
		}

		try {
			double area = 0.0;

			for (int i = 0; i < polygon.npoints; i++) {
				int j = (i + 1) % polygon.npoints;
				area += polygon.xpoints[i] * polygon.ypoints[j];
				area -= polygon.xpoints[j] * polygon.ypoints[i];
			}

			return Math.abs(area) / 2.0;

		} catch (Exception e) {
			Logger.error("Error calculating polygon area", e);
			return 0.0;
		}
	}

	/**
	 * Checks if a polygon is convex.
	 * 
	 * @param polygon the polygon to check
	 * @return true if the polygon is convex, false otherwise
	 * @throws IllegalArgumentException if polygon is null
	 */
	public static boolean isConvex(Polygon2D polygon) {
		if (polygon == null) {
			throw new IllegalArgumentException("Polygon cannot be null");
		}

		if (polygon.npoints < 3) {
			return false;
		}

		try {
			boolean isPositive = false;
			boolean isNegative = false;

			for (int i = 0; i < polygon.npoints; i++) {
				int j = (i + 1) % polygon.npoints;
				int k = (i + 2) % polygon.npoints;

				double crossProduct = getCrossProduct(polygon.xpoints[i], polygon.ypoints[i], polygon.xpoints[j],
						polygon.ypoints[j], polygon.xpoints[k], polygon.ypoints[k]);

				if (crossProduct > EPSILON) {
					isPositive = true;
				} else if (crossProduct < -EPSILON) {
					isNegative = true;
				}

				if (isPositive && isNegative) {
					return false;
				}
			}

			return true;

		} catch (Exception e) {
			Logger.error("Error checking polygon convexity", e);
			return false;
		}
	}

	// Private helper methods

	/**
	 * Adds a vertex from the specified quadrant to the ordered polygon.
	 */
	private static void addVertexInQuadrant(Polygon2D orderedPolygon, Polygon roiPolygon, Rectangle quadrant,
			String quadrantName) {
		for (int i = 0; i < roiPolygon.npoints && i < REQUIRED_VERTICES; i++) {
			if (quadrant.contains(roiPolygon.xpoints[i], roiPolygon.ypoints[i])) {
				orderedPolygon.addPoint(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
				return;
			}
		}

		Logger.warn("No vertex found in " + quadrantName + " quadrant");
	}

	/**
	 * Validates parameters for polygon inflation.
	 */
	private static void validateInflationParameters(Polygon2D roiPolygon, int numColumns, int numRows, int cageWidth,
			int cageHeight, int widthInterval, int heightInterval) {
		if (roiPolygon == null) {
			throw new IllegalArgumentException("ROI polygon cannot be null");
		}

		if (roiPolygon.npoints != REQUIRED_VERTICES) {
			throw new IllegalArgumentException("Polygon must have exactly 4 vertices, got: " + roiPolygon.npoints);
		}

		if (numColumns <= 0 || numRows <= 0) {
			throw new IllegalArgumentException("Grid dimensions must be positive: " + numColumns + "x" + numRows);
		}

		if (cageWidth <= 0 || cageHeight <= 0) {
			throw new IllegalArgumentException("Cage dimensions must be positive: " + cageWidth + "x" + cageHeight);
		}

		if (widthInterval < 0 || heightInterval < 0) {
			throw new IllegalArgumentException(
					"Intervals cannot be negative: " + widthInterval + ", " + heightInterval);
		}
	}

	/**
	 * Interpolates between two points using linear interpolation.
	 */
	private static Point2D.Double interpolatePoint(double x1, double y1, double x2, double y2, double ratio) {
		double x = x1 + ratio * (x2 - x1);
		double y = y1 + ratio * (y2 - y1);
		return new Point2D.Double(x, y);
	}

	/**
	 * Calculates the cross product of vectors formed by three points.
	 */
	private static double getCrossProduct(double x1, double y1, double x2, double y2, double x3, double y3) {
		return (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
	}
}
