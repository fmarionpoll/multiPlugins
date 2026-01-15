package plugins.fmp.multiSPOTS96.tools.polyline;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * Extended Line2D class with additional geometric operations and utility
 * methods. This class extends Line2D.Double and provides enhanced functionality
 * for line calculations including X-from-Y coordinate lookup and line
 * intersection detection.
 * 
 * <p>
 * The Line2DPlus class provides:
 * <ul>
 * <li>Inverse coordinate lookup (X from Y value)</li>
 * <li>Line intersection calculations with proper boundary checking</li>
 * <li>Robust handling of edge cases (vertical lines, parallel lines)</li>
 * <li>Comprehensive validation and error handling</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * Line2DPlus line = new Line2DPlus();
 * line.setLine(0, 0, 10, 5);
 * 
 * // Get X coordinate for Y = 2.5
 * double x = line.getXfromY(2.5);
 * 
 * // Find intersection with another line
 * Line2D otherLine = new Line2D.Double(0, 5, 10, 0);
 * Point2D.Double intersection = line.getIntersection(otherLine);
 * </pre>
 * 
 * @author MultiSPOTS96
 * @see java.awt.geom.Line2D.Double
 * @see java.awt.geom.Point2D.Double
 */
public class Line2DPlus extends Line2D.Double {

	/** Serial version UID for serialization */
	private static final long serialVersionUID = 935528755853877320L;

	/** Epsilon for floating-point comparisons */
	private static final double EPSILON = 1e-10;

	/** Minimum parameter value for line segment intersection */
	private static final double MIN_PARAMETER = 0.0;

	/** Maximum parameter value for line segment intersection */
	private static final double MAX_PARAMETER = 1.0;

	/**
	 * Creates a new empty Line2DPlus.
	 */
	public Line2DPlus() {
		super();
	}

	/**
	 * Creates a new Line2DPlus with the specified endpoints.
	 * 
	 * @param x1 x-coordinate of the first point
	 * @param y1 y-coordinate of the first point
	 * @param x2 x-coordinate of the second point
	 * @param y2 y-coordinate of the second point
	 */
	public Line2DPlus(double x1, double y1, double x2, double y2) {
		super(x1, y1, x2, y2);
	}

	/**
	 * Creates a new Line2DPlus with the specified endpoints.
	 * 
	 * @param p1 the first point
	 * @param p2 the second point
	 * @throws IllegalArgumentException if either point is null
	 */
	public Line2DPlus(Point2D p1, Point2D p2) {
		if (p1 == null || p2 == null) {
			throw new IllegalArgumentException("Line endpoints cannot be null");
		}
		super.setLine(p1, p2);
	}

	/**
	 * Creates a new Line2DPlus from an existing Line2D.
	 * 
	 * @param line the source line
	 * @throws IllegalArgumentException if line is null
	 */
	public Line2DPlus(Line2D line) {
		if (line == null) {
			throw new IllegalArgumentException("Source line cannot be null");
		}
		super.setLine(line);
	}

	/**
	 * Calculates the X-coordinate for a given Y-coordinate on this line.
	 * 
	 * <p>
	 * This method solves for X in the line equation: Y = mX + b where m is the
	 * slope and b is the Y-intercept.
	 * </p>
	 * 
	 * <p>
	 * For vertical lines (where X1 == X2), the X-coordinate is constant and equals
	 * the X-coordinate of both endpoints.
	 * </p>
	 * 
	 * @param y the Y-coordinate for which to find the corresponding X-coordinate
	 * @return the X-coordinate corresponding to the given Y-coordinate
	 * @throws IllegalArgumentException if y is NaN or infinite
	 */
	public double getXfromY(double y) {
		if (java.lang.Double.isNaN(y) || java.lang.Double.isInfinite(y)) {
			throw new IllegalArgumentException("Y-coordinate must be a finite number: " + y);
		}

		double x1 = getX1();
		double x2 = getX2();
		double y1 = getY1();
		double y2 = getY2();

		// Handle vertical line case
		if (Math.abs(x1 - x2) < EPSILON) {
			return x1;
		}

		// Calculate slope and intercept
		double slope = (y1 - y2) / (x1 - x2);
		double intercept = y1 - x1 * slope;

		// Solve for X: X = (Y - intercept) / slope
		return (y - intercept) / slope;
	}

	/**
	 * Finds the intersection point between this line and another line.
	 * 
	 * <p>
	 * This method uses parametric line equations to find the intersection point
	 * between two line segments. The intersection is only reported if it occurs
	 * within both line segments (not on their infinite extensions).
	 * </p>
	 * 
	 * <p>
	 * The method handles the following cases:
	 * <ul>
	 * <li>Parallel lines (returns null)</li>
	 * <li>Intersection outside segment bounds (returns null)</li>
	 * <li>Valid intersection within both segments (returns Point2D.Double)</li>
	 * </ul>
	 * 
	 * @param line the other line to intersect with
	 * @return the intersection point, or null if no intersection exists within both
	 *         segments
	 * @throws IllegalArgumentException if line is null
	 */
	public Point2D.Double getIntersection(Line2D line) {
		if (line == null) {
			throw new IllegalArgumentException("Line cannot be null");
		}

		// Get coordinates of both lines
		double x1 = getX1();
		double y1 = getY1();
		double x2 = getX2();
		double y2 = getY2();

		double x3 = line.getX1();
		double y3 = line.getY1();
		double x4 = line.getX2();
		double y4 = line.getY2();

		// Calculate the denominator for the parametric equations
		double denominator = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);

		// Check for parallel lines
		if (Math.abs(denominator) < EPSILON) {
			return null; // Lines are parallel or coincident
		}

		// Calculate parameters for both lines
		double numeratorA = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3);
		double numeratorB = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3);

		double parameterA = numeratorA / denominator;
		double parameterB = numeratorB / denominator;

		// Check if intersection occurs within both line segments
		if (isParameterInRange(parameterA) && isParameterInRange(parameterB)) {
			// Calculate intersection point
			double intersectionX = x1 + parameterA * (x2 - x1);
			double intersectionY = y1 + parameterA * (y2 - y1);

			return new Point2D.Double(intersectionX, intersectionY);
		}

		return null; // Intersection is outside one or both segments
	}

	/**
	 * Checks if this line is vertical (X1 == X2).
	 * 
	 * @return true if the line is vertical, false otherwise
	 */
	public boolean isVertical() {
		return Math.abs(getX1() - getX2()) < EPSILON;
	}

	/**
	 * Checks if this line is horizontal (Y1 == Y2).
	 * 
	 * @return true if the line is horizontal, false otherwise
	 */
	public boolean isHorizontal() {
		return Math.abs(getY1() - getY2()) < EPSILON;
	}

	/**
	 * Calculates the slope of this line.
	 * 
	 * @return the slope of the line
	 * @throws ArithmeticException if the line is vertical (infinite slope)
	 */
	public double getSlope() {
		if (isVertical()) {
			throw new ArithmeticException("Cannot calculate slope of vertical line");
		}

		return (getY2() - getY1()) / (getX2() - getX1());
	}

	/**
	 * Calculates the Y-intercept of this line.
	 * 
	 * @return the Y-intercept of the line
	 * @throws ArithmeticException if the line is vertical (no Y-intercept)
	 */
	public double getYIntercept() {
		if (isVertical()) {
			throw new ArithmeticException("Vertical line has no Y-intercept");
		}

		double slope = getSlope();
		return getY1() - slope * getX1();
	}

	/**
	 * Calculates the length of this line segment.
	 * 
	 * @return the length of the line segment
	 */
	public double getLength() {
		double dx = getX2() - getX1();
		double dy = getY2() - getY1();
		return Math.sqrt(dx * dx + dy * dy);
	}

	/**
	 * Gets the midpoint of this line segment.
	 * 
	 * @return the midpoint as a Point2D.Double
	 */
	public Point2D.Double getMidpoint() {
		double midX = (getX1() + getX2()) / 2.0;
		double midY = (getY1() + getY2()) / 2.0;
		return new Point2D.Double(midX, midY);
	}

	/**
	 * Checks if a point lies on this line segment within a specified tolerance.
	 * 
	 * @param point     the point to check
	 * @param tolerance the tolerance for the check
	 * @return true if the point lies on the line segment, false otherwise
	 * @throws IllegalArgumentException if point is null or tolerance is negative
	 */
	public boolean containsPoint(Point2D point, double tolerance) {
		if (point == null) {
			throw new IllegalArgumentException("Point cannot be null");
		}
		if (tolerance < 0) {
			throw new IllegalArgumentException("Tolerance cannot be negative: " + tolerance);
		}

		return ptSegDist(point) <= tolerance;
	}

	/**
	 * Checks if a point lies on this line segment using default tolerance.
	 * 
	 * @param point the point to check
	 * @return true if the point lies on the line segment, false otherwise
	 * @throws IllegalArgumentException if point is null
	 */
	public boolean containsPoint(Point2D point) {
		return containsPoint(point, EPSILON);
	}

	@Override
	public String toString() {
		return String.format("Line2DPlus[(%f, %f) -> (%f, %f)]", getX1(), getY1(), getX2(), getY2());
	}

	// Private helper methods

	/**
	 * Checks if a parameter value is within the valid range [0, 1] for line segment
	 * intersection.
	 * 
	 * @param parameter the parameter value to check
	 * @return true if the parameter is within range, false otherwise
	 */
	private boolean isParameterInRange(double parameter) {
		return parameter >= MIN_PARAMETER && parameter <= MAX_PARAMETER;
	}
}
