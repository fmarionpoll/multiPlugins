package plugins.fmp.multiSPOTS.tools.polyline;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import icy.gui.frame.progress.AnnounceFrame;
import icy.type.geom.Polygon2D;

public class PolygonUtilities {

	public static Polygon2D orderVerticesOf4CornersPolygon(Polygon roiPolygon) {
		if (roiPolygon.npoints > 4)
			new AnnounceFrame("Only the first 4 points of the polygon will be used...");
		Polygon2D extFrame = new Polygon2D();
		Rectangle rect = roiPolygon.getBounds();
		Rectangle rect1 = new Rectangle(rect);
		// find upper left
		rect1.setSize(rect.width / 2, rect.height / 2);
		for (int i = 0; i < roiPolygon.npoints; i++) {
			if (rect1.contains(roiPolygon.xpoints[i], roiPolygon.ypoints[i])) {
				extFrame.addPoint(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
				break;
			}
		}
		// find lower left
		rect1.translate(0, rect.height / 2 + 2);
		for (int i = 0; i < roiPolygon.npoints; i++) {
			if (rect1.contains(roiPolygon.xpoints[i], roiPolygon.ypoints[i])) {
				extFrame.addPoint(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
				break;
			}
		}
		// find lower right
		rect1.translate(rect.width / 2 + 2, 0);
		for (int i = 0; i < roiPolygon.npoints; i++) {
			if (rect1.contains(roiPolygon.xpoints[i], roiPolygon.ypoints[i])) {
				extFrame.addPoint(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
				break;
			}
		}
		// find upper right
		rect1.translate(0, -rect.height / 2 - 2);
		for (int i = 0; i < roiPolygon.npoints; i++) {
			if (rect1.contains(roiPolygon.xpoints[i], roiPolygon.ypoints[i])) {
				extFrame.addPoint(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
				break;
			}
		}
		return extFrame;
	}

	public static Polygon2D inflate(Polygon2D roiPolygon, int ncolumns, int nrows, int width_cage, int width_interval) {
		double width_x_current = ncolumns * (width_cage + 2 * width_interval) - 2 * width_interval;
		double deltax_top = (roiPolygon.xpoints[3] - roiPolygon.xpoints[0]) * width_interval / width_x_current;
		double deltax_bottom = (roiPolygon.xpoints[2] - roiPolygon.xpoints[1]) * width_interval / width_x_current;

		double width_y_current = nrows * (width_cage + 2 * width_interval) - 2 * width_interval;
		double deltay_left = (roiPolygon.ypoints[1] - roiPolygon.ypoints[0]) * width_interval / width_y_current;
		double deltay_right = (roiPolygon.ypoints[2] - roiPolygon.ypoints[3]) * width_interval / width_y_current;

		double[] xpoints = new double[4];
		double[] ypoints = new double[4];
		int npoints = 4;

		xpoints[0] = roiPolygon.xpoints[0] - deltax_top;
		xpoints[1] = roiPolygon.xpoints[1] - deltax_bottom;
		xpoints[3] = roiPolygon.xpoints[3] + deltax_top;
		xpoints[2] = roiPolygon.xpoints[2] + deltax_bottom;

		ypoints[0] = roiPolygon.ypoints[0] - deltay_left;
		ypoints[3] = roiPolygon.ypoints[3] - deltay_right;
		ypoints[1] = roiPolygon.ypoints[1] + deltay_left;
		ypoints[2] = roiPolygon.ypoints[2] + deltay_right;

		Polygon2D result = new Polygon2D(xpoints, ypoints, npoints);
		return result;
	}

	public static Point2D lineIntersect(
			double x1, double y1, 
			double x2, double y2, 
			double x3, double y3, 
			double x4, double y4) {
		double denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
		if (denom == 0.0) // Lines are parallel.
			return null;

		double ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denom;
		double ub = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denom;
		if (ua >= 0.0f && ua <= 1.0f && ub >= 0.0f && ub <= 1.0f)
			// Get the intersection point.
			return new Point2D.Double((x1 + ua * (x2 - x1)), (y1 + ua * (y2 - y1)));
		return null;
	}

	public static Point2D.Double[][] createArrayOfPointsFromPolygon(Polygon2D roiPolygon, int nbcols, int nbrows) {

		if (roiPolygon.npoints != 4)
			throw new IllegalArgumentException("Polygon must be 4-sided");
		if (nbcols <= 0 || nbrows <= 0)
			throw new IllegalArgumentException("There must be a positive number of parts per side");

		Point2D.Double[][] arrayPoints = new Point2D.Double[nbcols][nbrows];

		for (int col = 0; col < nbcols; col++) {

			double ratioX0 = col / (double) (nbcols - 1);

			double x = roiPolygon.xpoints[0] + (roiPolygon.xpoints[3] - roiPolygon.xpoints[0]) * ratioX0;
			double y = roiPolygon.ypoints[0] + (roiPolygon.ypoints[3] - roiPolygon.ypoints[0]) * ratioX0;
			Point2D.Double ipoint0 = new Point2D.Double(x, y);

			x = roiPolygon.xpoints[1] + (roiPolygon.xpoints[2] - roiPolygon.xpoints[1]) * ratioX0;
			y = roiPolygon.ypoints[1] + (roiPolygon.ypoints[2] - roiPolygon.ypoints[1]) * ratioX0;
			Point2D.Double ipoint1 = new Point2D.Double(x, y);

			for (int row = 0; row < nbrows; row++) {

				double ratioY0 = row / (double) (nbrows - 1);
				x = ipoint0.x + (ipoint1.x - ipoint0.x) * ratioY0;
				y = ipoint0.y + (ipoint1.y - ipoint0.y) * ratioY0;

				Point2D.Double point = new Point2D.Double(x, y);
				arrayPoints[col][row] = point;
			}
		}

		return arrayPoints;
	}

}
