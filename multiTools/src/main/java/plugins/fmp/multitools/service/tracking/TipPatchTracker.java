package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Point2D;

/**
 * CODEX Anchor-relative 21x21 patch matching with texture, ambiguity and
 * round-trip checks.
 */
public final class TipPatchTracker {
	/** Per-run state; a rejected observation never becomes a new reference position. */
	public static final class AcceptedPositions {
		private Point2D start, end;
		public AcceptedPositions(java.awt.geom.Line2D anchor) {
			start = copy(anchor.getP1());
			end = copy(anchor.getP2());
		}
		public java.awt.geom.Line2D update(Match first, Match second) {
			if (first.reliable) start = copy(first.position);
			if (second.reliable) end = copy(second.position);
			return new java.awt.geom.Line2D.Double(start, end);
		}
		private static Point2D copy(Point2D p) {
			return new Point2D.Double(p.getX(), p.getY());
		}
	}
	private static final int HALF = 10, SEARCH = 12;

	public static final class Match {
		public final Point2D position;
		public final boolean reliable;
		public final double correlation, roundTripError;

		Match(Point2D p, boolean ok, double score, double error) {
			position = p;
			reliable = ok;
			correlation = score;
			roundTripError = error;
		}
	}

	public Match track(Point2D anchor, double[] reference, double[] current, int width, int height) {
		if (anchor == null || reference == null || current == null || reference.length != width * height
				|| current.length != width * height)
			throw new IllegalArgumentException("Invalid patch tracking input");
		double[] forward = search(anchor, anchor, reference, current, width, height);
		Point2D candidate = new Point2D.Double(forward[0], forward[1]);
		double[] backward = search(candidate, anchor, current, reference, width, height);
		double error = anchor.distance(backward[0], backward[1]);
		boolean ok = forward[2] >= .75 && forward[3] >= .025 && backward[2] >= .75 && error <= 1.5
				&& Math.abs(candidate.getX() - anchor.getX()) < SEARCH
				&& Math.abs(candidate.getY() - anchor.getY()) < SEARCH;
		return new Match(ok ? candidate : new Point2D.Double(anchor.getX(), anchor.getY()), ok, forward[2], error);
	}

	private double[] search(Point2D source, Point2D guess, double[] a, double[] b, int w, int h) {
		double[][] scores = new double[2 * SEARCH + 1][2 * SEARCH + 1];
		double best = -2.;
		int bx = 0, by = 0;
		for (int y = -SEARCH; y <= SEARCH; y++)
			for (int x = -SEARCH; x <= SEARCH; x++) {
				double s = score(source.getX(), source.getY(), guess.getX() + x, guess.getY() + y, a, b, w, h);
				scores[y + SEARCH][x + SEARCH] = s;
				if (s > best) {
					best = s;
					bx = x;
					by = y;
				}
			}
		double second = -2.;
		for (int y = -SEARCH; y <= SEARCH; y++)
			for (int x = -SEARCH; x <= SEARCH; x++)
				if (Math.abs(x - bx) > 2 || Math.abs(y - by) > 2)
					second = Math.max(second, scores[y + SEARCH][x + SEARCH]);
		double sx = 0., sy = 0.;
		if (Math.abs(bx) < SEARCH && Math.abs(by) < SEARCH) {
			sx = peak(scores[by + SEARCH][bx + SEARCH - 1], best, scores[by + SEARCH][bx + SEARCH + 1]);
			sy = peak(scores[by + SEARCH - 1][bx + SEARCH], best, scores[by + SEARCH + 1][bx + SEARCH]);
		}
		return new double[] { guess.getX() + bx + sx, guess.getY() + by + sy, best, best - second };
	}

	private double peak(double a, double b, double c) {
		double d = a - 2 * b + c;
		return d < -1.e-9 ? Math.max(-.5, Math.min(.5, .5 * (a - c) / d)) : 0.;
	}

	private double score(double ax, double ay, double bx, double by, double[] a, double[] b, int w, int h) {
		if (Math.min(ax, bx) < HALF || Math.min(ay, by) < HALF || Math.max(ax, bx) >= w - HALF - 1
				|| Math.max(ay, by) >= h - HALF - 1)
			return -2.;
		double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
		int n = 0;
		for (int y = -HALF; y <= HALF; y++)
			for (int x = -HALF; x <= HALF; x++) {
				double u = pixel(a, ax + x, ay + y, w), v = pixel(b, bx + x, by + y, w);
				sa += u;
				sb += v;
				saa += u * u;
				sbb += v * v;
				sab += u * v;
				n++;
			}
		double va = saa - sa * sa / n, vb = sbb - sb * sb / n;
		if (va / n < 4. || vb / n < 4.)
			return -2.;
		return (sab - sa * sb / n) / Math.sqrt(va * vb);
	}

	private double pixel(double[] p, double x, double y, int w) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y), k = ix + iy * w;
		double fx = x - ix, fy = y - iy;
		return (1 - fy) * ((1 - fx) * p[k] + fx * p[k + 1]) + fy * ((1 - fx) * p[k + w] + fx * p[k + w + 1]);
	}
}
