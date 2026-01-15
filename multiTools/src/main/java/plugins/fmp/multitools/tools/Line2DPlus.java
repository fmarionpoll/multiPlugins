package plugins.fmp.multitools.tools;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class Line2DPlus extends Line2D.Double {
	private static final long serialVersionUID = 935528755853877320L;

	public double getXfromY(double y) {
		double x = 0d;
		if (getX1() == getX2())
			x = getX1();
		else {
			double slope = (getY1() - getY2()) / (getX1() - getX2());
			double intercept = getY1() - getX1() * slope;
			x = (y - intercept) / slope;
		}
		return x;
	}

	public double getYfromX(double x) {
		double y = 0d;
		if (getX1() == getX2())
			y = getY1();
		else {
			double slope = (getY1() - getY2()) / (getX1() - getX2());
			double intercept = getY1() - getX1() * slope;
			y = slope * x + intercept;
		}
		return y;
	}

	public Point2D.Double getIntersection(Line2D line) {
		double x1 = getX1();
		double x2 = getX2();
		double y1 = getY1();
		double y2 = getY2();

		double x3 = line.getX1();
		double x4 = line.getX2();
		double y3 = line.getY1();
		double y4 = line.getY2();

		double denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
		if (denom == 0.0) // parallel lines
			return null;

		double nume_a = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3));
		double nume_b = ((x2 - x1) * (y1 - y3)) - ((y2 - y1) * (x1 - x3));

		double ua = nume_a / denom;
		double ub = nume_b / denom;

		if ((ua >= 0.0f) && (ua <= 1.0f) && (ub >= 0.0f) && (ub <= 1.0f)) {
			// Get the intersection point.
			double intersectX = x1 + ua * (x2 - x1);
			double intersectY = y1 + ua * (y2 - y1);
			return new Point2D.Double(intersectX, intersectY);
		}
		return null;
	}

}
