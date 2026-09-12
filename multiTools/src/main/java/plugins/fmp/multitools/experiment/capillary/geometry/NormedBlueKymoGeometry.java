package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Sampling geometry for kymographs built from physical blue lines: expand both
 * ends by a fraction of blue length, then resample at equal arc length onto a
 * constant height.
 */
public final class NormedBlueKymoGeometry {
	private NormedBlueKymoGeometry() {
	}

	/**
	 * Grows each end of {@code blue} by {@code expansionRatio / 2} of its length.
	 * {@code expansionRatio} 0.10 means +5% beyond each endpoint (total 110%).
	 */
	public static Line2D expandBothEnds(Line2D blue, double expansionRatio) {
		if (blue == null || blue.getP1().distance(blue.getP2()) <= 0)
			throw new IllegalArgumentException("a non-empty blue line is required");
		double e = expansionRatio;
		if (!Double.isFinite(e) || e < 0)
			throw new IllegalArgumentException("expansion ratio must be finite and non-negative");
		if (e == 0)
			return CapillaryPhaseGeometry.copy(blue);
		double length = blue.getP1().distance(blue.getP2());
		double dx = (blue.getX2() - blue.getX1()) / length;
		double dy = (blue.getY2() - blue.getY1()) / length;
		double extra = e * length / 2.0;
		return new Line2D.Double(blue.getX1() - extra * dx, blue.getY1() - extra * dy, blue.getX2() + extra * dx,
				blue.getY2() + extra * dy);
	}

	/** Reverses {@code blue} when it points opposite to {@code reference}. */
	public static Line2D orientLike(Line2D blue, Line2D reference) {
		if (blue == null)
			return null;
		if (reference == null)
			return CapillaryPhaseGeometry.copy(blue);
		double bdx = blue.getX2() - blue.getX1();
		double bdy = blue.getY2() - blue.getY1();
		double rdx = reference.getX2() - reference.getX1();
		double rdy = reference.getY2() - reference.getY1();
		return rdx * bdx + rdy * bdy >= 0 ? CapillaryPhaseGeometry.copy(blue)
				: new Line2D.Double(blue.getP2(), blue.getP1());
	}

	public static Line2D lineFromRoi(ROI2D roi) {
		if (roi instanceof ROI2DLine)
			return CapillaryPhaseGeometry.copy(((ROI2DLine) roi).getLine());
		if (roi instanceof ROI2DPolyLine) {
			Polyline2D poly = ((ROI2DPolyLine) roi).getPolyline2D();
			if (poly == null || poly.npoints < 2)
				return null;
			int last = poly.npoints - 1;
			return new Line2D.Double(poly.xpoints[0], poly.ypoints[0], poly.xpoints[last], poly.ypoints[last]);
		}
		return null;
	}

	/**
	 * Blue at {@code t}, oriented like the green corridor, then expanded. Null when
	 * phase geometry is missing or the blue line is empty.
	 */
	public static Line2D expandedSamplingLine(Capillary cap, long t, double expansionRatio) {
		if (cap == null || cap.getPhaseGeometry() == null || !cap.getPhaseGeometry().isInitialized())
			return null;
		Line2D blue = cap.getPhaseGeometry().getBlueAt(t);
		if (blue == null || blue.getP1().distance(blue.getP2()) <= 0)
			return null;
		Line2D green = lineFromRoi(cap.getRoiAtFrameT(t));
		return expandBothEnds(orientLike(blue, green), expansionRatio);
	}

	/**
	 * Ceil of the longest expanded blue among initialized capillaries. 0 when none
	 * have blue geometry.
	 */
	public static int maxExpandedLengthPx(Iterable<Capillary> capillaries, double expansionRatio) {
		if (capillaries == null)
			return 0;
		int max = 0;
		for (Capillary cap : capillaries) {
			int length = maxExpandedLengthPx(cap, expansionRatio);
			if (length > max)
				max = length;
		}
		return max;
	}

	/** Ceil of this capillary's longest expanded blue. 0 when geometry is missing. */
	public static int maxExpandedLengthPx(Capillary cap, double expansionRatio) {
		if (cap == null || cap.getPhaseGeometry() == null || !cap.getPhaseGeometry().isInitialized())
			return 0;
		int max = 0;
		for (Line2D blue : cap.getPhaseGeometry().getBlueKeyframes().values()) {
			if (blue == null || blue.getP1().distance(blue.getP2()) <= 0)
				continue;
			Line2D green = lineFromRoi(cap.getRoi());
			Line2D expanded = expandBothEnds(orientLike(blue, green), expansionRatio);
			int length = (int) Math.ceil(expanded.getP1().distance(expanded.getP2()));
			if (length > max)
				max = length;
		}
		return max;
	}

	public static int kymoHeight(Capillary cap, double expansionRatio) {
		return Math.max(2, maxExpandedLengthPx(cap, expansionRatio));
	}

	public static int kymoHeight(Iterable<Capillary> capillaries, double expansionRatio) {
		return Math.max(2, maxExpandedLengthPx(capillaries, expansionRatio));
	}

	/** {@code height} equally spaced points from p1 to p2, inclusive. */
	public static Point2D[] sampleArc(Line2D line, int height) {
		if (line == null || line.getP1().distance(line.getP2()) <= 0)
			throw new IllegalArgumentException("a non-empty line is required");
		if (height < 2)
			throw new IllegalArgumentException("height must be at least 2");
		Point2D[] points = new Point2D[height];
		double x1 = line.getX1();
		double y1 = line.getY1();
		double dx = line.getX2() - x1;
		double dy = line.getY2() - y1;
		double denom = height - 1;
		for (int i = 0; i < height; i++) {
			double s = i / denom;
			points[i] = new Point2D.Double(x1 + s * dx, y1 + s * dy);
		}
		return points;
	}
}
