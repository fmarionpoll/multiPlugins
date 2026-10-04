package plugins.fmp.multitools.experiment.spot;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Physical rim of a spot: the outer dye perimeter, the inward path used as
 * signal, and the outward distance of the floor band. The floor band is rebuilt
 * from the perimeter and is not stored.
 */
public final class SpotRimGeometry {

	public static final int DEFAULT_RIM_WIDTH_PX = 3;
	public static final int DEFAULT_OUTER_PX = 5;
	/** One stored vertex per this many degrees. A full rim has 24 vertices. */
	public static final int OUTLINE_STEP_DEG = 15;
	/** A vertex this far inside the hull is an inward bite, not pixel noise. */
	public static final double INWARD_BITE_PX = 2.0;

	private double[] x = new double[0];
	private double[] y = new double[0];
	private int rimWidthPx = DEFAULT_RIM_WIDTH_PX;
	private int outerPx = DEFAULT_OUTER_PX;

	public SpotRimGeometry() {
	}

	public SpotRimGeometry copy() {
		SpotRimGeometry copy = new SpotRimGeometry();
		copy.copyFrom(this);
		return copy;
	}

	public void copyFrom(SpotRimGeometry source) {
		if (source == null) {
			x = new double[0];
			y = new double[0];
			rimWidthPx = DEFAULT_RIM_WIDTH_PX;
			outerPx = DEFAULT_OUTER_PX;
			return;
		}
		x = source.x.clone();
		y = source.y.clone();
		rimWidthPx = source.rimWidthPx;
		outerPx = source.outerPx;
	}

	public boolean hasOutline() {
		return x != null && y != null && x.length >= 3 && y.length == x.length;
	}

	public int vertexCount() {
		return hasOutline() ? x.length : 0;
	}

	public double[] outlineX() {
		return x.clone();
	}

	public double[] outlineY() {
		return y.clone();
	}

	public void setOutline(double[] xs, double[] ys) {
		if (xs == null || ys == null || xs.length < 3 || xs.length != ys.length) {
			x = new double[0];
			y = new double[0];
			return;
		}
		int n = xs.length;
		if (n >= 4 && xs[0] == xs[n - 1] && ys[0] == ys[n - 1]) {
			n--;
		}
		if (n < 3) {
			x = new double[0];
			y = new double[0];
			return;
		}
		x = Arrays.copyOf(xs, n);
		y = Arrays.copyOf(ys, n);
	}

	public int getRimWidthPx() {
		return rimWidthPx;
	}

	public void setRimWidthPx(int rimWidthPx) {
		this.rimWidthPx = Math.max(1, rimWidthPx);
	}

	public int getOuterPx() {
		return outerPx;
	}

	public void setOuterPx(int outerPx) {
		this.outerPx = Math.max(0, outerPx);
	}

	/**
	 * Vertices of the convex hull, in boundary order. A point that lies inside the
	 * others is omitted.
	 */
	public static double[][] convexHull(double[] xs, double[] ys) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		if (n <= 0) {
			return new double[][] { new double[0], new double[0] };
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> {
			int cmp = Double.compare(xs[a], xs[b]);
			return cmp != 0 ? cmp : Double.compare(ys[a], ys[b]);
		});
		int[] uniq = new int[n];
		int u = 0;
		for (int i = 0; i < n; i++) {
			int id = order[i];
			if (u > 0 && xs[uniq[u - 1]] == xs[id] && ys[uniq[u - 1]] == ys[id]) {
				continue;
			}
			uniq[u++] = id;
		}
		if (u < 3) {
			return copyPoints(xs, ys, uniq, u);
		}
		int[] hull = new int[u * 2];
		int k = 0;
		for (int i = 0; i < u; i++) {
			while (k >= 2 && cross(xs, ys, hull[k - 2], hull[k - 1], uniq[i]) <= 0) {
				k--;
			}
			hull[k++] = uniq[i];
		}
		int t = k + 1;
		for (int i = u - 2; i >= 0; i--) {
			while (k >= t && cross(xs, ys, hull[k - 2], hull[k - 1], uniq[i]) <= 0) {
				k--;
			}
			hull[k++] = uniq[i];
		}
		k--;
		return copyPoints(xs, ys, hull, k);
	}

	/**
	 * Convex hull when a vertex lies more than {@code minDepthPx} inside it.
	 * Returns null when the outline has no inward bite.
	 */
	public static double[][] withoutInwardBite(double[] xs, double[] ys, double minDepthPx) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		if (n < 4) {
			return null;
		}
		double[][] hull = convexHull(xs, ys);
		if (hull[0].length < 3 || hull[0].length >= n) {
			return null;
		}
		double limit = Math.max(0, minDepthPx);
		for (int i = 0; i < n; i++) {
			if (distanceToClosed(xs[i], ys[i], hull[0], hull[1]) > limit) {
				return hull;
			}
		}
		return null;
	}

	/**
	 * One radius every {@link #OUTLINE_STEP_DEG} from {@code (cx, cy)}, using the
	 * outermost hit on the polygon, then the convex hull of those radii.
	 */
	public static double[][] simplifyOutline(double[] xs, double[] ys, double cx, double cy) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		if (n >= 2 && xs[0] == xs[n - 1] && ys[0] == ys[n - 1]) {
			n--;
		}
		if (n < 3) {
			return null;
		}
		int steps = 360 / OUTLINE_STEP_DEG;
		double[] rx = new double[steps];
		double[] ry = new double[steps];
		int m = 0;
		for (int a = 0; a < steps; a++) {
			double theta = Math.toRadians(a * OUTLINE_STEP_DEG);
			double cos = Math.cos(theta);
			double sin = Math.sin(theta);
			double radius = outermostRayHit(xs, ys, n, cx, cy, cos, sin);
			if (radius < 0) {
				continue;
			}
			rx[m] = cx + radius * cos;
			ry[m] = cy + radius * sin;
			m++;
		}
		if (m < 3) {
			return null;
		}
		double[][] hull = convexHull(Arrays.copyOf(rx, m), Arrays.copyOf(ry, m));
		if (hull[0].length < 3) {
			return null;
		}
		return hull;
	}

	/** Closed ellipse sampled every {@link #OUTLINE_STEP_DEG}. */
	public static double[][] ellipseOutline(double cx, double cy, double rx, double ry) {
		double safeRx = Math.max(1.0, rx);
		double safeRy = Math.max(1.0, ry);
		int steps = 360 / OUTLINE_STEP_DEG;
		double[] xs = new double[steps];
		double[] ys = new double[steps];
		for (int i = 0; i < steps; i++) {
			double theta = Math.toRadians(i * OUTLINE_STEP_DEG);
			double cos = Math.cos(theta);
			double sin = Math.sin(theta);
			double a = cos / safeRx;
			double b = sin / safeRy;
			double q = a * a + b * b;
			double bound = q <= 1e-12 ? 0 : 1.0 / Math.sqrt(q);
			xs[i] = cx + bound * cos;
			ys[i] = cy + bound * sin;
		}
		return new double[][] { xs, ys };
	}

	private static double[][] copyPoints(double[] xs, double[] ys, int[] ids, int n) {
		double[] ox = new double[n];
		double[] oy = new double[n];
		for (int i = 0; i < n; i++) {
			ox[i] = xs[ids[i]];
			oy[i] = ys[ids[i]];
		}
		return new double[][] { ox, oy };
	}

	private static double cross(double[] xs, double[] ys, int o, int a, int b) {
		return (xs[a] - xs[o]) * (ys[b] - ys[o]) - (ys[a] - ys[o]) * (xs[b] - xs[o]);
	}

	/** Distance along the unit ray to the farthest edge hit, or -1. */
	private static double outermostRayHit(double[] xs, double[] ys, int n, double cx, double cy, double dx, double dy) {
		double best = -1;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double ex = xs[j] - xs[i];
			double ey = ys[j] - ys[i];
			double denom = dx * ey - dy * ex;
			if (Math.abs(denom) < 1e-9) {
				continue;
			}
			double fx = xs[i] - cx;
			double fy = ys[i] - cy;
			double t = (fx * ey - fy * ex) / denom;
			double s = (fx * dy - fy * dx) / denom;
			if (t > 1e-6 && s >= -1e-9 && s <= 1.0 + 1e-9 && t > best) {
				best = t;
			}
		}
		return best;
	}

	/** Slides the stored centerline. The floor band is derived again from it. */
	public void translate(double dx, double dy) {
		for (int i = 0; i < x.length; i++) {
			x[i] += dx;
			y[i] += dy;
		}
	}

	public double[][] outerCenterline() {
		return offsetOutward(x, y, outerPx);
	}

	/**
	 * Parallel curve. Each vertex steps along the normal that points away from the
	 * centroid.
	 */
	public static double[][] offsetOutward(double[] xs, double[] ys, double distance) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		double[] ox = new double[n];
		double[] oy = new double[n];
		if (n == 0) {
			return new double[][] { ox, oy };
		}
		double cx = 0;
		double cy = 0;
		for (int i = 0; i < n; i++) {
			cx += xs[i];
			cy += ys[i];
		}
		cx /= n;
		cy /= n;
		for (int i = 0; i < n; i++) {
			int prev = (i + n - 1) % n;
			int next = (i + 1) % n;
			double tx = xs[next] - xs[prev];
			double ty = ys[next] - ys[prev];
			double len = Math.hypot(tx, ty);
			if (len < 1e-6 || distance == 0) {
				ox[i] = xs[i];
				oy[i] = ys[i];
				continue;
			}
			double nx = -ty / len;
			double ny = tx / len;
			double vx = xs[i] - cx;
			double vy = ys[i] - cy;
			if (nx * vx + ny * vy < 0) {
				nx = -nx;
				ny = -ny;
			}
			ox[i] = xs[i] + nx * distance;
			oy[i] = ys[i] + ny * distance;
		}
		return new double[][] { ox, oy };
	}

	/**
	 * Pixels inside the closed outline and within {@code widthPx} of it, as
	 * {@code y * width + x}.
	 */
	public static int[] inwardBandPixels(double[] xs, double[] ys, int widthPx, int imageWidth, int imageHeight) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		if (n < 3 || imageWidth <= 0 || imageHeight <= 0 || widthPx <= 0) {
			return new int[0];
		}
		double minX = xs[0];
		double maxX = xs[0];
		double minY = ys[0];
		double maxY = ys[0];
		for (int i = 1; i < n; i++) {
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		int x0 = Math.max(0, (int) Math.floor(minX) - 1);
		int y0 = Math.max(0, (int) Math.floor(minY) - 1);
		int x1 = Math.min(imageWidth - 1, (int) Math.ceil(maxX) + 1);
		int y1 = Math.min(imageHeight - 1, (int) Math.ceil(maxY) + 1);
		int cap = Math.max(16, (x1 - x0 + 1) * (y1 - y0 + 1));
		int[] raw = new int[cap];
		int count = 0;
		double limit = widthPx;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				double d = distanceToClosed(x, y, xs, ys);
				if (d <= limit && (d <= 0.6 || insideClosed(x, y, xs, ys))) {
					if (count == raw.length) {
						raw = Arrays.copyOf(raw, raw.length * 2);
					}
					raw[count++] = y * imageWidth + x;
				}
			}
		}
		return Arrays.copyOf(raw, count);
	}

	public static double distanceToClosed(double px, double py, double[] xs, double[] ys) {
		int n = Math.min(xs.length, ys.length);
		double best = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			best = Math.min(best, distanceToSegment(px, py, xs[i], ys[i], xs[j], ys[j]));
		}
		return best;
	}

	private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double len2 = dx * dx + dy * dy;
		double t = 0;
		if (len2 > 1e-12) {
			t = ((px - ax) * dx + (py - ay) * dy) / len2;
			if (t < 0) {
				t = 0;
			} else if (t > 1) {
				t = 1;
			}
		}
		double qx = ax + t * dx;
		double qy = ay + t * dy;
		return Math.hypot(px - qx, py - qy);
	}

	static boolean insideClosed(double px, double py, double[] xs, double[] ys) {
		boolean in = false;
		int n = Math.min(xs.length, ys.length);
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double yi = ys[i];
			double yj = ys[j];
			if ((yi > py) != (yj > py)) {
				double x = xs[j] + (xs[i] - xs[j]) * (py - yj) / (yi - yj);
				if (px < x) {
					in = !in;
				}
			}
		}
		return in;
	}

	/**
	 * Pixels within half the stroke of the closed centerline, as
	 * {@code y * width + x}.
	 */
	public static int[] strokePixels(double[] xs, double[] ys, int strokePx, int imageWidth, int imageHeight) {
		int n = xs != null && ys != null ? Math.min(xs.length, ys.length) : 0;
		if (n < 2 || imageWidth <= 0 || imageHeight <= 0) {
			return new int[0];
		}
		double radius = Math.max(0.5, strokePx / 2.0);
		int pad = (int) Math.ceil(radius);
		Set<Integer> pixels = new LinkedHashSet<>();
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double dx = xs[j] - xs[i];
			double dy = ys[j] - ys[i];
			double len = Math.hypot(dx, dy);
			int steps = Math.max(1, (int) Math.ceil(len * 2.0));
			for (int s = 0; s <= steps; s++) {
				double t = s / (double) steps;
				double px = xs[i] + t * dx;
				double py = ys[i] + t * dy;
				int ix = (int) Math.round(px);
				int iy = (int) Math.round(py);
				for (int yy = iy - pad; yy <= iy + pad; yy++) {
					if (yy < 0 || yy >= imageHeight) {
						continue;
					}
					for (int xx = ix - pad; xx <= ix + pad; xx++) {
						if (xx < 0 || xx >= imageWidth) {
							continue;
						}
						double ddx = xx - px;
						double ddy = yy - py;
						if (ddx * ddx + ddy * ddy <= radius * radius + 1e-6) {
							pixels.add(yy * imageWidth + xx);
						}
					}
				}
			}
		}
		int[] out = new int[pixels.size()];
		int k = 0;
		for (Integer pix : pixels) {
			out[k++] = pix;
		}
		return out;
	}
}
