package plugins.fmp.multitools.experiment.spot;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Physical rim of a spot: a closed centerline, the stroke used as signal, and
 * the outward distance of the floor band. The floor band is rebuilt from the
 * centerline and is not stored.
 */
public final class SpotRimGeometry {

	public static final int DEFAULT_RIM_WIDTH_PX = 2;
	public static final int DEFAULT_OUTER_PX = 4;

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

	/** Pixels within half the stroke of the closed centerline, as {@code y * width + x}. */
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
