package plugins.fmp.multiSPOTS96.tools.ROI2D;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import icy.util.XMLUtil;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectShape;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class ROI2DUtilities {
	private static final String ID_ROIMC = "roiMC";

	public static Polygon2D getPolygonEnclosingROI2Ds(ArrayList<ROI2D> listRois, String filter) {
		ROI2D roi1 = listRois.get(0);
		Rectangle rect0 = roi1.getBounds();
		int x0 = (int) rect0.getX();
		int y0 = (int) rect0.getY();
		int width0 = (int) rect0.getWidth();
		int height0 = (int) rect0.getHeight();

		for (ROI2D roi : listRois) {
			if (!roi.getName().contains(filter))
				continue;
			Rectangle rect1 = roi.getBounds();
			int x1 = (int) rect1.getX();
			int y1 = (int) rect1.getY();
			int width1 = x1 - x0 + (int) rect1.getWidth();
			int height1 = y1 - y0 + (int) rect1.getHeight();
			if (x1 < x0)
				x0 = x1;
			if (y1 < y0)
				y0 = y1;
			if (width1 > width0)
				width0 = width1;
			if (height1 > height0)
				height0 = height1;
		}
		return new Polygon2D(new Rectangle2D.Double(x0, y0, width0, height0));
	}

	public static void saveToXML_ROI(Node node, ROI2D roi) {
		final Node nodeROI = XMLUtil.setElement(node, ID_ROIMC);
		if (!roi.saveToXML(nodeROI)) {
			XMLUtil.removeNode(node, nodeROI);
			System.err.println("Error: the roi " + roi.getName() + " was not correctly saved to XML !");
		}
	}

	public static ROI2D loadFromXML_ROI(Node node) {
		final Node nodeROI = XMLUtil.getElement(node, ID_ROIMC);
		if (nodeROI != null) {
			ROI2D roi = (ROI2D) ROI2D.createFromXML(nodeROI);
			return roi;
		}
		return null;
	}

	public static ROI2D resizeROI(ROI2D roi, int delta_pixel) {
		ROI2D out = (ROI2D) roi.getCopy();

		if (out instanceof ROI2DRectShape || out instanceof ROI2DLine) {
			out = resizeRectROI(roi, delta_pixel);
		} else if (out instanceof ROI2DShape) {
			out = resizeShape2DROI(roi, delta_pixel);
		} else {
			out.setName(roi.getName());
			new FailedAnnounceFrame("Cannot rescale a " + roi.getSimpleClassName());
		}
		return out;
	}

	private static ROI2D resizeRectROI(ROI2D out, int delta_pixel) {

		Rectangle2D b2 = ((ROI2D) out).getBounds2D();
		// translate to origin
		double oldX = b2.getCenterX();
		double oldY = b2.getCenterY();
		b2.setFrame(b2.getX() - oldX, b2.getY() - oldY, b2.getWidth(), b2.getHeight());
		// scale
		b2.setFrame(b2.getX() + delta_pixel, b2.getY() + delta_pixel, b2.getWidth() + delta_pixel * 2,
				b2.getHeight() + delta_pixel * 2);
		// translate back to initial position
		b2.setFrame(b2.getX() + oldX, b2.getY() + oldY, b2.getWidth(), b2.getHeight());

		((ROI2D) out).setBounds2D(b2);
		return out;
	}

	private static ROI2D resizeShape2DROI(ROI2D out, int delta_pixel) {
		ROI2DShape shape = (ROI2DShape) out;
		List<Point2D> pts = shape.getPoints();

		// determine the mass center
		Point2D.Double oldCenter = new Point2D.Double();
		for (Point2D pt : pts) {
			oldCenter.x += pt.getX();
			oldCenter.y += pt.getY();
		}
		oldCenter.x /= pts.size();
		oldCenter.y /= pts.size();

		// scale the ROI via its anchor points
		for (Point2D pt : pts) {
			double x = pt.getX(), y = pt.getY();
			// translate to the origin
			x -= oldCenter.x;
			y -= oldCenter.y;
			x = x >= 0 ? x + delta_pixel : x - delta_pixel;
			y = y >= 0 ? y + delta_pixel : y - delta_pixel;
			x += oldCenter.x;
			y += oldCenter.y;
			// set the final point location
			pt.setLocation(x, y);
		}

		if (out instanceof ROI2DPolygon) {
			ROI2DPolygon poly = (ROI2DPolygon) out;
			poly.setPoints(pts);
		} else if (out instanceof ROI2DPolyLine) {
			ROI2DPolyLine poly = (ROI2DPolyLine) out;
			poly.setPoints(pts);
		} else
			try {
				shape.getClass().getMethod("removeAllPoint").invoke(shape);
				for (Point2D pt : pts)
					shape.addNewPoint(pt, true);
			} catch (Exception e) {
				shape.setName(out.getName());
				new FailedAnnounceFrame("Cannot resize a " + out.getSimpleClassName());
			}
		return shape;
	}

	public static ROI2D rescaleROI(ROI2D roi, double scale) {
		ROI2D out = (ROI2D) roi.getCopy();
		out.setName(roi.getName() + " x" + scale);

		if (out instanceof ROI2DRectShape || out instanceof ROI2DLine) {
			out = rescaleRectROI(roi, scale);
		} else if (out instanceof ROI2DShape) {
			out = rescaleShape2DROI(roi, scale);
		} else {
			out.setName(roi.getName());
			new FailedAnnounceFrame("Cannot rescale a " + roi.getSimpleClassName());
		}
		return out;
	}

	private static ROI2D rescaleRectROI(ROI2D out, double scale) {
		Rectangle2D b2 = ((ROI2D) out).getBounds2D();
		// translate to origin
		double oldX = b2.getCenterX();
		double oldY = b2.getCenterY();
		b2.setFrame(b2.getX() - oldX, b2.getY() - oldY, b2.getWidth(), b2.getHeight());
		// scale
		b2.setFrame(b2.getX() * scale, b2.getY() * scale, b2.getWidth() * scale, b2.getHeight() * scale);
		// translate back to initial position
		b2.setFrame(b2.getX() + oldX, b2.getY() + oldY, b2.getWidth(), b2.getHeight());

		((ROI2D) out).setBounds2D(b2);
		return out;
	}

	private static ROI2D rescaleShape2DROI(ROI2D out, double scale) {
		ROI2DShape shape = (ROI2DShape) out;

		// determine the mass center
		Point2D.Double oldCenter = new Point2D.Double();
		List<Point2D> pts = shape.getPoints();
		for (Point2D pt : pts) {
			oldCenter.x += pt.getX();
			oldCenter.y += pt.getY();
		}
		oldCenter.x /= pts.size();
		oldCenter.y /= pts.size();

		// scale the ROI via its anchor points
		for (Point2D pt : pts) {
			double x = pt.getX(), y = pt.getY();
			// translate to the origin
			x -= oldCenter.x;
			y -= oldCenter.y;
			x *= scale;
			y *= scale;
			x += oldCenter.x;
			y += oldCenter.y;
			// set the final point location
			pt.setLocation(x, y);
		}

		if (out instanceof ROI2DPolygon) {
			ROI2DPolygon poly = (ROI2DPolygon) out;
			poly.setPoints(pts);
		} else if (out instanceof ROI2DPolyLine) {
			ROI2DPolyLine poly = (ROI2DPolyLine) out;
			poly.setPoints(pts);
		} else
			try {
				shape.getClass().getMethod("removeAllPoint").invoke(shape);
				for (Point2D pt : pts)
					shape.addNewPoint(pt, true);
			} catch (Exception e) {
				shape.setName(out.getName());
				new FailedAnnounceFrame("Cannot rescale a " + out.getSimpleClassName());
			}
		return shape;
	}

	public static void interpolateMissingPointsAlongXAxis(ROI2DPolyLine roiLine, int nintervals) {
		if (nintervals <= 1)
			return;
		// interpolate points so that each x step has a value
		// assume that points are ordered along x
		Polyline2D polyline = roiLine.getPolyline2D();
		int roiLine_npoints = polyline.npoints;
		if (roiLine_npoints == 0)
			return;

		if (roiLine_npoints > nintervals)
			roiLine_npoints = nintervals;

		List<Point2D> pts = new ArrayList<Point2D>(roiLine_npoints);
		double ylast = polyline.ypoints[roiLine_npoints - 1];
		int xfirst0 = (int) polyline.xpoints[0];

		for (int i = 1; i < roiLine_npoints; i++) {
			int xfirst = (int) polyline.xpoints[i - 1];
			if (xfirst < 0)
				xfirst = 0;
			int xlast = (int) polyline.xpoints[i];
			if (xlast > xfirst0 + nintervals - 1)
				xlast = xfirst0 + nintervals - 1;
			double yfirst = polyline.ypoints[i - 1];
			ylast = polyline.ypoints[i];
			for (int j = xfirst; j < xlast; j++) {
				int val = (int) (yfirst + (ylast - yfirst) * (j - xfirst) / (xlast - xfirst));
				Point2D pt = new Point2D.Double(j, val);
				pts.add(pt);
			}
		}
		Point2D pt = new Point2D.Double(polyline.xpoints[roiLine_npoints - 1], ylast);
		pts.add(pt);
		roiLine.setPoints(pts);
	}

	public static void mergeROI2DsListNoDuplicate(List<ROI2D> seqList, List<ROI2D> listRois, Sequence seq) {
		if (seqList.isEmpty()) {
			for (ROI2D roi : listRois)
				if (roi != null)
					seqList.add(roi);
		}

		for (ROI2D seqRoi : seqList) {
			Iterator<ROI2D> iterator = listRois.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				if (seqRoi == roi)
					iterator.remove();
				else if (seqRoi.getName().equals(roi.getName())) {
					seqRoi.copyFrom(roi);
					iterator.remove();
				}
			}
		}
	}

	public static void removeROI2DsMissingChar(List<ROI2D> listRois, char character) {
		Iterator<ROI2D> iterator = listRois.iterator();
		while (iterator.hasNext()) {
			ROI2D roi = iterator.next();
			if (roi.getName().indexOf(character) < 0)
				iterator.remove();
		}
	}

	public static List<ROI2D> loadROI2DsFromXML(Document doc) {
		List<ROI> localList = ROI.loadROIsFromXML(XMLUtil.getRootElement(doc));
		List<ROI2D> finalList = new ArrayList<ROI2D>(localList.size());
		for (ROI roi : localList)
			finalList.add((ROI2D) roi);
		return finalList;
	}

	// ----------------------

	public static void removeRoisContainingStringAtT(int t, String string, Sequence seq) {
		for (ROI roi : seq.getROIs()) {
			if (roi instanceof ROI2D && roi.getName().contains(string) && (t < 0 || ((ROI2D) roi).getT() == t))
				seq.removeROI(roi);
		}
	}

	public static Polygon2D orderVerticesofPolygon(Polygon roiPolygon) {
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

	public static Polygon2D getPolygonEnclosingCapillaries(ArrayList<ROI2D> listRois) {
		ROI2D roi1 = listRois.get(0);
		Point2D pt1 = getFirstPoint(roi1);
		Point2D pt2 = getLastPoint(roi1);
		double[] xpoints = { pt1.getX(), pt1.getX(), pt2.getX(), pt2.getX() };
		double[] ypoints = { pt1.getY(), pt1.getY(), pt2.getY(), pt2.getY() };

		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("line"))
				continue;

			pt1 = getFirstPoint(roi);
			updateUpperLeftCorner(pt1, xpoints, ypoints);
			updateUpperRightCorner(pt1, xpoints, ypoints);

			pt2 = getLastPoint(roi);
			updateLowerLeftCorner(pt2, xpoints, ypoints);
			updateLowerRightCorner(pt2, xpoints, ypoints);
		}
		return new Polygon2D(xpoints, ypoints, 4);
	}

	private static void updateUpperLeftCorner(Point2D pt, double[] xpoints, double[] ypoints) {
		final int i = 0;
		if (pt.getX() < xpoints[i]) {
			xpoints[i] = pt.getX();
			if (pt.getY() < ypoints[i])
				ypoints[i] = pt.getY();
		}
	}

	private static void updateUpperRightCorner(Point2D pt, double[] xpoints, double[] ypoints) {
		final int i = 1;
		if (pt.getX() > xpoints[i]) {
			xpoints[i] = pt.getX();
			if (pt.getY() < ypoints[i])
				ypoints[i] = pt.getY();
		}
	}

	private static void updateLowerLeftCorner(Point2D pt, double[] xpoints, double[] ypoints) {
		final int i = 3;
		if (pt.getX() < xpoints[i]) {
			xpoints[i] = pt.getX();
			if (pt.getY() > ypoints[i])
				ypoints[i] = pt.getY();
		}
	}

	private static void updateLowerRightCorner(Point2D pt, double[] xpoints, double[] ypoints) {
		final int i = 2;
		if (pt.getX() > xpoints[i]) {
			xpoints[i] = pt.getX();
			if (pt.getY() > ypoints[i])
				ypoints[i] = pt.getY();
		}
	}

	private static Point2D getFirstPoint(ROI2D roi) {
		Rectangle rect = roi.getBounds();
		return new Point2D.Double(rect.getX(), rect.getY());
	}

	private static Point2D getLastPoint(ROI2D roi) {
		Rectangle rect = roi.getBounds();
		return new Point2D.Double(rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight());
	}

	public static ArrayList<Point2D> getCapillaryPoints(ROI2D roi) {
		ArrayList<Point2D> points = new ArrayList<Point2D>();
		if (roi instanceof ROI2DPolyLine) {
			Polyline2D line = ((ROI2DPolyLine) roi).getPolyline2D();
			for (int i = 0; i < line.npoints; i++) {
				Point2D pt = new Point2D.Double(line.xpoints[i], line.ypoints[i]);
				points.add(pt);
			}
		} else if (roi instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) roi).getLine();
			Point2D pt = new Point2D.Double(line.getP1().getX(), line.getP1().getY());
			points.add(pt);
			pt = new Point2D.Double(line.getP2().getX(), line.getP2().getY());
			points.add(pt);
		}
		return points;
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

	public static Point2D lineIntersect(double x1, double y1, double x2, double y2, double x3, double y3, double x4,
			double y4) {
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

	public static List<ROI2D> loadROIsFromXML(Document doc) {
		List<ROI> localList = ROI.loadROIsFromXML(XMLUtil.getRootElement(doc));
		List<ROI2D> finalList = new ArrayList<ROI2D>(localList.size());
		for (ROI roi : localList)
			finalList.add((ROI2D) roi);
		return finalList;
	}

	public static void mergeROIsListNoDuplicate(List<ROI2D> seqList, List<ROI2D> listRois, Sequence seq) {
		if (seqList.isEmpty())
			seqList.addAll(listRois);
		for (ROI2D seqRoi : seqList) {
			Iterator<ROI2D> iterator = listRois.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				if (seqRoi == roi)
					iterator.remove();
				else if (seqRoi.getName().equals(roi.getName())) {
					seqRoi.copyFrom(roi);
					iterator.remove();
				}
			}
		}
	}

}
