package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;

/**
 * One small rigid motion of the whole plate between two frames. Each spot
 * votes with a patch correlation. The translation is the median vote. The
 * rotation is the residual around the plate center.
 */
public final class SpotPlateRegistration {

	public static final int SEARCH_PX = 3;
	public static final int MIN_SPOTS = 4;
	static final double DEAD_TRANSLATION_PX = 0.15;
	static final double MAX_ANGLE_RAD = Math.toRadians(0.5);
	static final double DEAD_ANGLE_RAD = Math.toRadians(0.02);
	private static final double INSECT_FRACTION = 0.2;
	private static final double MIN_CORRELATION = 0.3;
	private static final double OUTLIER_PX = 1.5;
	private static final int MIN_PIXELS = 30;

	/** Extra motion from the previous frame to this one. */
	public static final class Step {
		public static final Step ZERO = new Step(0, 0, 0);

		public final double dx;
		public final double dy;
		public final double angleRad;

		public Step(double dx, double dy, double angleRad) {
			this.dx = dx;
			this.dy = dy;
			this.angleRad = angleRad;
		}
	}

	/**
	 * Maps a point in the first frame onto the current frame. {@code (cx, cy)} is
	 * the plate center in the first frame.
	 */
	public static final class Pose {
		public final double dx;
		public final double dy;
		public final double angleRad;
		public final double cx;
		public final double cy;

		public Pose(double dx, double dy, double angleRad, double cx, double cy) {
			this.dx = dx;
			this.dy = dy;
			this.angleRad = angleRad;
			this.cx = cx;
			this.cy = cy;
		}

		public static Pose identity(double cx, double cy) {
			return new Pose(0, 0, 0, cx, cy);
		}

		public boolean isIdentity() {
			return dx == 0 && dy == 0 && angleRad == 0;
		}

		public Pose compose(Step step) {
			if (step == null || (step.dx == 0 && step.dy == 0 && step.angleRad == 0)) {
				return this;
			}
			double cos = Math.cos(step.angleRad);
			double sin = Math.sin(step.angleRad);
			return new Pose(cos * dx - sin * dy + step.dx, sin * dx + cos * dy + step.dy, angleRad + step.angleRad, cx,
					cy);
		}

		public double[] map(double x, double y) {
			double x0 = x - cx;
			double y0 = y - cy;
			double cos = Math.cos(angleRad);
			double sin = Math.sin(angleRad);
			return new double[] { cx + cos * x0 - sin * y0 + dx, cy + sin * x0 + cos * y0 + dy };
		}

		public EllipseGeom mapEllipse(EllipseGeom ellipse) {
			double[] p = map(ellipse.cx, ellipse.cy);
			return new EllipseGeom(p[0], p[1], ellipse.rx, ellipse.ry);
		}
	}

	private SpotPlateRegistration() {
	}

	public static double[] pivot(List<EllipseGeom> ellipses) {
		double sx = 0;
		double sy = 0;
		int n = 0;
		if (ellipses != null) {
			for (EllipseGeom ellipse : ellipses) {
				if (ellipse == null) {
					continue;
				}
				sx += ellipse.cx;
				sy += ellipse.cy;
				n++;
			}
		}
		if (n == 0) {
			return new double[] { 0, 0 };
		}
		return new double[] { sx / n, sy / n };
	}

	/**
	 * Shift of {@code curr} relative to {@code prev}. Ellipses are where the spots
	 * sit in {@code prev}. {@code (pivotX, pivotY)} is the plate center in that
	 * same frame.
	 */
	public static Step fit(int width, int height, int[] prevRed, int[] prevGreen, int[] prevBlue, int[] currRed,
			int[] currGreen, int[] currBlue, boolean[] insectPrev, boolean[] insectCurr, List<EllipseGeom> ellipses,
			double pivotX, double pivotY) {
		if (width <= 0 || height <= 0 || prevRed == null || currRed == null || ellipses == null) {
			return Step.ZERO;
		}
		List<Vote> votes = new ArrayList<>();
		for (EllipseGeom ellipse : ellipses) {
			if (ellipse == null) {
				continue;
			}
			if (insectFraction(ellipse, width, height, insectPrev) >= INSECT_FRACTION
					|| insectFraction(ellipse, width, height, insectCurr) >= INSECT_FRACTION) {
				continue;
			}
			Vote vote = vote(width, height, prevRed, prevGreen, prevBlue, currRed, currGreen, currBlue, ellipse);
			if (vote != null) {
				votes.add(vote);
			}
		}
		if (votes.size() < MIN_SPOTS) {
			return Step.ZERO;
		}
		double[] dxs = new double[votes.size()];
		double[] dys = new double[votes.size()];
		for (int i = 0; i < votes.size(); i++) {
			dxs[i] = votes.get(i).dx;
			dys[i] = votes.get(i).dy;
		}
		double dx = median(dxs);
		double dy = median(dys);
		double angle = angleOf(votes, dx, dy, pivotX, pivotY);
		if (Math.hypot(dx, dy) < DEAD_TRANSLATION_PX && Math.abs(angle) < DEAD_ANGLE_RAD) {
			return Step.ZERO;
		}
		return new Step(dx, dy, angle);
	}

	/** Samples {@code src} at the pose, writing a frame in the first-frame coordinates. */
	public static int[] warp(int[] src, int width, int height, Pose pose) {
		if (src == null || pose == null || pose.isIdentity()) {
			return src;
		}
		int[] out = new int[width * height];
		double cos = Math.cos(pose.angleRad);
		double sin = Math.sin(pose.angleRad);
		for (int y = 0; y < height; y++) {
			double y0 = y - pose.cy;
			for (int x = 0; x < width; x++) {
				double x0 = x - pose.cx;
				double sx = pose.cx + cos * x0 - sin * y0 + pose.dx;
				double sy = pose.cy + sin * x0 + cos * y0 + pose.dy;
				out[y * width + x] = bilinear(src, width, height, sx, sy);
			}
		}
		return out;
	}

	public static boolean[] warpMask(boolean[] src, int width, int height, Pose pose) {
		if (src == null || pose == null || pose.isIdentity()) {
			return src;
		}
		boolean[] out = new boolean[width * height];
		double cos = Math.cos(pose.angleRad);
		double sin = Math.sin(pose.angleRad);
		for (int y = 0; y < height; y++) {
			double y0 = y - pose.cy;
			for (int x = 0; x < width; x++) {
				double x0 = x - pose.cx;
				int sx = (int) Math.round(pose.cx + cos * x0 - sin * y0 + pose.dx);
				int sy = (int) Math.round(pose.cy + sin * x0 + cos * y0 + pose.dy);
				if (sx >= 0 && sy >= 0 && sx < width && sy < height) {
					out[y * width + x] = src[sy * width + sx];
				}
			}
		}
		return out;
	}

	private static Vote vote(int width, int height, int[] prevRed, int[] prevGreen, int[] prevBlue, int[] currRed,
			int[] currGreen, int[] currBlue, EllipseGeom ellipse) {
		int span = SEARCH_PX * 2 + 1;
		double[][] score = new double[span][span];
		int bestX = 0;
		int bestY = 0;
		double best = -2;
		for (int iy = -SEARCH_PX; iy <= SEARCH_PX; iy++) {
			for (int ix = -SEARCH_PX; ix <= SEARCH_PX; ix++) {
				double value = correlate(width, height, prevRed, prevGreen, prevBlue, currRed, currGreen, currBlue,
						ellipse, ix, iy);
				score[iy + SEARCH_PX][ix + SEARCH_PX] = value;
				if (value > best) {
					best = value;
					bestX = ix;
					bestY = iy;
				}
			}
		}
		if (best < MIN_CORRELATION) {
			return null;
		}
		double dx = refine(score[bestY + SEARCH_PX], bestX);
		double dy = refine(column(score, bestX), bestY);
		return new Vote(ellipse.cx, ellipse.cy, dx, dy);
	}

	private static double refine(double[] row, int index) {
		int i = index + SEARCH_PX;
		if (row == null || i <= 0 || i >= row.length - 1) {
			return index;
		}
		double denom = row[i - 1] - 2 * row[i] + row[i + 1];
		if (Math.abs(denom) < 1e-6) {
			return index;
		}
		double delta = 0.5 * (row[i - 1] - row[i + 1]) / denom;
		if (delta > 0.75) {
			delta = 0.75;
		} else if (delta < -0.75) {
			delta = -0.75;
		}
		return index + delta;
	}

	private static double[] column(double[][] score, int ix) {
		double[] col = new double[score.length];
		int x = ix + SEARCH_PX;
		for (int y = 0; y < score.length; y++) {
			col[y] = score[y][x];
		}
		return col;
	}

	private static double correlate(int width, int height, int[] prevRed, int[] prevGreen, int[] prevBlue,
			int[] currRed, int[] currGreen, int[] currBlue, EllipseGeom ellipse, int dx, int dy) {
		int x0 = (int) Math.floor(ellipse.cx - ellipse.rx);
		int y0 = (int) Math.floor(ellipse.cy - ellipse.ry);
		int x1 = (int) Math.ceil(ellipse.cx + ellipse.rx);
		int y1 = (int) Math.ceil(ellipse.cy + ellipse.ry);
		double sumA = 0;
		double sumB = 0;
		int n = 0;
		for (int y = y0; y <= y1; y++) {
			int yb = y + dy;
			if (y < 0 || y >= height || yb < 0 || yb >= height) {
				continue;
			}
			for (int x = x0; x <= x1; x++) {
				int xb = x + dx;
				if (x < 0 || x >= width || xb < 0 || xb >= width || !ellipse.contains(x, y)) {
					continue;
				}
				sumA += level(prevRed, prevGreen, prevBlue, y * width + x);
				sumB += level(currRed, currGreen, currBlue, yb * width + xb);
				n++;
			}
		}
		if (n < MIN_PIXELS) {
			return -1;
		}
		double meanA = sumA / n;
		double meanB = sumB / n;
		double varA = 0;
		double varB = 0;
		double cov = 0;
		for (int y = y0; y <= y1; y++) {
			int yb = y + dy;
			if (y < 0 || y >= height || yb < 0 || yb >= height) {
				continue;
			}
			for (int x = x0; x <= x1; x++) {
				int xb = x + dx;
				if (x < 0 || x >= width || xb < 0 || xb >= width || !ellipse.contains(x, y)) {
					continue;
				}
				double a = level(prevRed, prevGreen, prevBlue, y * width + x) - meanA;
				double b = level(currRed, currGreen, currBlue, yb * width + xb) - meanB;
				varA += a * a;
				varB += b * b;
				cov += a * b;
			}
		}
		if (varA < 1e-6 || varB < 1e-6) {
			return -1;
		}
		return cov / Math.sqrt(varA * varB);
	}

	private static double level(int[] red, int[] green, int[] blue, int pix) {
		double r = red != null && pix < red.length ? red[pix] : 0;
		double g = green != null && pix < green.length ? green[pix] : r;
		double b = blue != null && pix < blue.length ? blue[pix] : r;
		return (r + g + b) / 3.0;
	}

	private static double insectFraction(EllipseGeom ellipse, int width, int height, boolean[] insect) {
		if (insect == null) {
			return 0;
		}
		int x0 = Math.max(0, (int) Math.floor(ellipse.cx - ellipse.rx));
		int y0 = Math.max(0, (int) Math.floor(ellipse.cy - ellipse.ry));
		int x1 = Math.min(width - 1, (int) Math.ceil(ellipse.cx + ellipse.rx));
		int y1 = Math.min(height - 1, (int) Math.ceil(ellipse.cy + ellipse.ry));
		int n = 0;
		int hit = 0;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				if (!ellipse.contains(x, y)) {
					continue;
				}
				n++;
				if (insect[y * width + x]) {
					hit++;
				}
			}
		}
		return n == 0 ? 0 : hit / (double) n;
	}

	private static double angleOf(List<Vote> votes, double dx, double dy, double pivotX, double pivotY) {
		double[] angles = new double[votes.size()];
		int n = 0;
		for (Vote vote : votes) {
			double rx = vote.dx - dx;
			double ry = vote.dy - dy;
			if (Math.hypot(rx, ry) > OUTLIER_PX) {
				continue;
			}
			double x = vote.x - pivotX;
			double y = vote.y - pivotY;
			double r2 = x * x + y * y;
			if (r2 < 4) {
				continue;
			}
			angles[n++] = (x * ry - y * rx) / r2;
		}
		if (n == 0) {
			return 0;
		}
		double angle = median(Arrays.copyOf(angles, n));
		if (angle > MAX_ANGLE_RAD) {
			return MAX_ANGLE_RAD;
		}
		if (angle < -MAX_ANGLE_RAD) {
			return -MAX_ANGLE_RAD;
		}
		return angle;
	}

	private static double median(double[] values) {
		double[] copy = Arrays.copyOf(values, values.length);
		Arrays.sort(copy);
		int n = copy.length;
		int mid = n / 2;
		if ((n & 1) == 1) {
			return copy[mid];
		}
		return 0.5 * (copy[mid - 1] + copy[mid]);
	}

	private static int bilinear(int[] src, int width, int height, double x, double y) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = x0 + 1;
		int y1 = y0 + 1;
		if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height) {
			return 0;
		}
		double tx = x - x0;
		double ty = y - y0;
		double v00 = src[y0 * width + x0];
		double v10 = src[y0 * width + x1];
		double v01 = src[y1 * width + x0];
		double v11 = src[y1 * width + x1];
		return (int) Math.round((1 - tx) * (1 - ty) * v00 + tx * (1 - ty) * v10 + (1 - tx) * ty * v01 + tx * ty * v11);
	}

	private static final class Vote {
		final double x;
		final double y;
		final double dx;
		final double dy;

		Vote(double x, double y, double dx, double dy) {
			this.x = x;
			this.y = y;
			this.dx = dx;
			this.dy = dy;
		}
	}
}
