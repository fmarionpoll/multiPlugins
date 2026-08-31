package plugins.fmp.multitools.experiment.capillary;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import icy.roi.ROI2D;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** Converts a legacy capillary polyline to its best-fit straight corridor. */
public final class CapillaryCorridorNormalizer {
	public static final double DEFAULT_WARNING_DEVIATION_PX = 3.0;

	public static final class Result {
		private final ROI2DLine line;
		private final double maxPerpendicularDeviation;

		Result(ROI2DLine line, double maxPerpendicularDeviation) {
			this.line = line;
			this.maxPerpendicularDeviation = maxPerpendicularDeviation;
		}

		public ROI2DLine getLine() { return line; }
		public double getMaxPerpendicularDeviation() { return maxPerpendicularDeviation; }
	}

	private CapillaryCorridorNormalizer() {}

	static final class Fit {
		final Line2D line;
		final double maxDeviation;

		Fit(Line2D line, double maxDeviation) {
			this.line = line;
			this.maxDeviation = maxDeviation;
		}
	}

	public static Result normalize(ROI2D roi) {
		if (roi == null)
			return new Result(null, Double.NaN);
		if (roi instanceof ROI2DLine)
			return new Result((ROI2DLine) roi.getCopy(), 0.0);
		ArrayList<Point2D> points = ROI2DUtilities.getCapillaryPoints(roi);
		if (points.size() < 2)
			return new Result(null, Double.NaN);
		Fit fit = fitPoints(points);
		ROI2DLine line = new ROI2DLine(fit.line);
		line.setName(roi.getName());
		line.setColor(roi.getColor());
		line.setStroke(roi.getStroke());
		line.setReadOnly(roi.isReadOnly());
		line.setT(roi.getT());
		line.setZ(roi.getZ());
		line.setC(roi.getC());
		return new Result(line, fit.maxDeviation);
	}

	static Fit fitPoints(java.util.List<Point2D> points) {
		if (points == null || points.size() < 2)
			throw new IllegalArgumentException("at least two corridor points are required");
		double cx = 0, cy = 0;
		for (Point2D point : points) {
			cx += point.getX();
			cy += point.getY();
		}
		cx /= points.size();
		cy /= points.size();
		double xx = 0, xy = 0, yy = 0;
		for (Point2D point : points) {
			double dx = point.getX() - cx, dy = point.getY() - cy;
			xx += dx * dx;
			xy += dx * dy;
			yy += dy * dy;
		}
		double angle = 0.5 * Math.atan2(2.0 * xy, xx - yy);
		double ux = Math.cos(angle), uy = Math.sin(angle);
		Point2D first = points.get(0), last = points.get(points.size() - 1);
		if ((last.getX() - first.getX()) * ux + (last.getY() - first.getY()) * uy < 0) {
			ux = -ux;
			uy = -uy;
		}

		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, maxDeviation = 0;
		for (Point2D point : points) {
			double dx = point.getX() - cx, dy = point.getY() - cy;
			double along = dx * ux + dy * uy;
			min = Math.min(min, along);
			max = Math.max(max, along);
			maxDeviation = Math.max(maxDeviation, Math.abs(-dx * uy + dy * ux));
		}
		Point2D p1 = new Point2D.Double(cx + min * ux, cy + min * uy);
		Point2D p2 = new Point2D.Double(cx + max * ux, cy + max * uy);
		return new Fit(new Line2D.Double(p1, p2), maxDeviation);
	}
}
