package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/** Tracks both glass endpoints with profiles aligned to the capillary shaft. */
public final class DirectionalCapillaryTracker {
	private static final int SEARCH = 12;
	private static final int PROFILE_RADIUS = 18;
	private static final int AVERAGE_HALF_WIDTH = 3;

	public Line2D track(Line2D referenceLine, double[] reference, double[] current, int width, int height) {
		if (referenceLine == null || reference == null || current == null || reference.length != current.length)
			return null;
		double dx = referenceLine.getX2() - referenceLine.getX1();
		double dy = referenceLine.getY2() - referenceLine.getY1();
		double length = Math.hypot(dx, dy);
		if (length < 2)
			return null;
		double ux = dx / length, uy = dy / length;
		double nx = -uy, ny = ux;
		Point2D p1 = refine(referenceLine.getP1(), ux, uy, nx, ny, reference, current, width, height);
		Point2D p2 = refine(referenceLine.getP2(), ux, uy, nx, ny, reference, current, width, height);
		return p1 == null || p2 == null ? null : new Line2D.Double(p1, p2);
	}

	private Point2D refine(Point2D point, double ux, double uy, double nx, double ny, double[] ref, double[] cur,
			int width, int height) {
		double[] refAcross = profile(point, nx, ny, ux, uy, ref, width, height);
		double[] curAcross = profile(point, nx, ny, ux, uy, cur, width, height);
		double[] refAlong = profile(point, ux, uy, nx, ny, ref, width, height);
		double[] curAlong = profile(point, ux, uy, nx, ny, cur, width, height);
		if (refAcross == null || curAcross == null || refAlong == null || curAlong == null)
			return null;
		double lateral = bestShift(refAcross, curAcross);
		double axial = bestShift(gradient(refAlong), gradient(curAlong));
		return new Point2D.Double(point.getX() + lateral * nx + axial * ux,
				point.getY() + lateral * ny + axial * uy);
	}

	private double[] profile(Point2D center, double ax, double ay, double bx, double by, double[] image, int width,
			int height) {
		int radius = PROFILE_RADIUS + SEARCH;
		double[] out = new double[2 * radius + 1];
		for (int i = -radius; i <= radius; i++) {
			double sum = 0;
			for (int j = -AVERAGE_HALF_WIDTH; j <= AVERAGE_HALF_WIDTH; j++) {
				double x = center.getX() + i * ax + j * bx;
				double y = center.getY() + i * ay + j * by;
				int ix = (int) Math.round(x), iy = (int) Math.round(y);
				if (ix < 0 || iy < 0 || ix >= width || iy >= height)
					return null;
				sum += image[iy * width + ix];
			}
			out[i + radius] = sum / (2 * AVERAGE_HALF_WIDTH + 1);
		}
		return out;
	}

	private double bestShift(double[] reference, double[] current) {
		int center = reference.length / 2;
		double best = -Double.MAX_VALUE;
		int bestShift = 0;
		for (int shift = -SEARCH; shift <= SEARCH; shift++) {
			double score = normalizedCorrelation(reference, current, center - PROFILE_RADIUS,
					center + PROFILE_RADIUS, shift);
			if (score > best) {
				best = score;
				bestShift = shift;
			}
		}
		return bestShift;
	}

	private static double normalizedCorrelation(double[] a, double[] b, int from, int to, int shift) {
		double ma = 0, mb = 0;
		int n = to - from + 1;
		for (int i = from; i <= to; i++) { ma += a[i]; mb += b[i + shift]; }
		ma /= n; mb /= n;
		double num = 0, da = 0, db = 0;
		for (int i = from; i <= to; i++) {
			double va = a[i] - ma, vb = b[i + shift] - mb;
			num += va * vb; da += va * va; db += vb * vb;
		}
		return num / Math.sqrt(Math.max(1e-12, da * db));
	}

	private static double[] gradient(double[] values) {
		double[] out = new double[values.length];
		for (int i = 1; i < values.length - 1; i++)
			out[i] = Math.abs(values[i + 1] - values[i - 1]);
		return out;
	}
}
