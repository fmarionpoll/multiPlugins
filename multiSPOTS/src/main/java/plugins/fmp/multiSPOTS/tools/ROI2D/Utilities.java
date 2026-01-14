package plugins.fmp.multiSPOTS.tools.ROI2D;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

import icy.gui.frame.progress.FailedAnnounceFrame;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.type.geom.Polyline2D;
import icy.util.XMLUtil;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectShape;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Utilities {
	private static final String ID_ROIMC = "roiMC";

	public static Polygon2D getPolygonEnclosingROI2Ds(ArrayList<ROI2D> listRois) {
		ROI2D roi1 = listRois.get(0);
		Rectangle rect0 = roi1.getBounds();
		int x0 = (int) rect0.getX();
		int y0 = (int) rect0.getY();
		int width0 = (int) rect0.getWidth();
		int height0 = (int) rect0.getHeight();

		for (ROI2D roi : listRois) {
			if (!roi.getName().contains("spot"))
				continue;
			Rectangle rect1 = roi.getBounds();
			int x1 = (int) rect1.getX();
			int y1 = (int) rect1.getY();
			int width1 = x1 - x0 + (int) rect1.getWidth();
			int height1 = y1 - y0 + (int) rect1.getHeight();
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

}
