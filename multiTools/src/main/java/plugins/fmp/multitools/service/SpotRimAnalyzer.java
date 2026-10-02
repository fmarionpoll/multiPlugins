package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

/**
 * Closed rim inside a spot ellipse. Along each ray the outline sits on the dark
 * dye ridge, inside the pale fringe, taken over the first bins and smoothed
 * around the circle. The floor is that outline offset outward.
 */
public final class SpotRimAnalyzer {

	public static final int ANGLE_STEP_DEG = 5;
	public static final int OUTSIDE_RING_PX = 3;
	static final int MIN_VERTICES = 8;
	/** Half-width of the radial moving average, in pixels. */
	private static final int RADIAL_SMOOTH_HALF = 2;
	/** Half-width of the circular median, in angle steps. */
	private static final int ANGULAR_SMOOTH_HALF = 2;

	public static final class Params {
		public final double madMultiplier;
		public final int initialBins;
		public final int rimWidthPx;
		public final int outerPx;
		public final int smoothBins;
		public final boolean insectGate;
		public final ImageTransformEnums insectTransform;
		public final int insectThreshold;
		public final boolean insectAbove;

		public Params(double madMultiplier, int initialBins, int rimWidthPx, int outerPx, int smoothBins,
				boolean insectGate, ImageTransformEnums insectTransform, int insectThreshold, boolean insectAbove) {
			this.madMultiplier = madMultiplier;
			this.initialBins = Math.max(1, initialBins);
			this.rimWidthPx = Math.max(1, rimWidthPx);
			this.outerPx = Math.max(0, outerPx);
			this.smoothBins = Math.max(1, smoothBins);
			this.insectGate = insectGate;
			this.insectTransform = insectTransform != null ? insectTransform : ImageTransformEnums.B_RGB;
			this.insectThreshold = insectThreshold;
			this.insectAbove = insectAbove;
		}
	}

	/** Axis-aligned ellipse in image coordinates. */
	public static final class EllipseGeom {
		public final double cx;
		public final double cy;
		public final double rx;
		public final double ry;

		public EllipseGeom(double cx, double cy, double rx, double ry) {
			this.cx = cx;
			this.cy = cy;
			this.rx = Math.max(1.0, rx);
			this.ry = Math.max(1.0, ry);
		}

		public boolean contains(double x, double y) {
			double dx = (x - cx) / rx;
			double dy = (y - cy) / ry;
			return dx * dx + dy * dy <= 1.0;
		}
	}

	private SpotRimAnalyzer() {
	}

	public static EllipseGeom ellipseOf(Spot spot) {
		if (spot == null) {
			return new EllipseGeom(0, 0, 1, 1);
		}
		ROI2D roi = spot.getRoi();
		if (roi != null) {
			Rectangle rect = roi.getBounds();
			if (rect != null && rect.width > 0 && rect.height > 0) {
				return new EllipseGeom(rect.getCenterX(), rect.getCenterY(), rect.getWidth() / 2.0,
						rect.getHeight() / 2.0);
			}
		}
		int radius = spot.getProperties() != null ? spot.getProperties().getSpotRadius() : 0;
		int x = spot.getProperties() != null ? spot.getProperties().getSpotXCoord() : 0;
		int y = spot.getProperties() != null ? spot.getProperties().getSpotYCoord() : 0;
		int r = Math.max(1, radius);
		return new EllipseGeom(x, y, r, r);
	}

	/**
	 * Closed outline, or null when too few rays still see dye. {@code containers}
	 * are every spot ellipse; pixels inside one of them are not part of the floor
	 * ring that sets the cutoff.
	 */
	public static double[][] detect(EllipseGeom ellipse, int imageWidth, int imageHeight, int[][] red, int[][] green,
			int[][] blue, EllipseGeom[] containers, double madMultiplier) {
		if (ellipse == null || imageWidth <= 0 || imageHeight <= 0 || red == null || red.length == 0) {
			return null;
		}
		double cutoff = cutoff(ellipse, imageWidth, imageHeight, red, green, blue, containers, madMultiplier);
		if (Double.isNaN(cutoff)) {
			return null;
		}
		int nAngles = 360 / ANGLE_STEP_DEG;
		double[] byAngle = new double[nAngles];
		Arrays.fill(byAngle, Double.NaN);
		int detected = 0;
		for (int a = 0; a < nAngles; a++) {
			double theta = Math.toRadians(a * ANGLE_STEP_DEG);
			double cos = Math.cos(theta);
			double sin = Math.sin(theta);
			double[] radii = new double[red.length];
			int n = 0;
			for (int t = 0; t < red.length; t++) {
				double radius = ridgeRadius(ellipse, imageWidth, imageHeight, channel(red, t), channel(green, t),
						channel(blue, t), cos, sin, cutoff);
				if (radius >= 0) {
					radii[n++] = radius;
				}
			}
			if (n == 0) {
				continue;
			}
			byAngle[a] = median(Arrays.copyOf(radii, n));
			detected++;
		}
		int minKeep = Math.max(MIN_VERTICES, (nAngles + 3) / 4);
		if (detected < minKeep) {
			return null;
		}
		double[] smooth = circularMedian(byAngle, ANGULAR_SMOOTH_HALF);
		double[] xs = new double[nAngles];
		double[] ys = new double[nAngles];
		int kept = 0;
		for (int a = 0; a < nAngles; a++) {
			if (!Double.isFinite(byAngle[a]) || !Double.isFinite(smooth[a])) {
				continue;
			}
			double theta = Math.toRadians(a * ANGLE_STEP_DEG);
			xs[kept] = ellipse.cx + smooth[a] * Math.cos(theta);
			ys[kept] = ellipse.cy + smooth[a] * Math.sin(theta);
			kept++;
		}
		if (kept < minKeep) {
			return null;
		}
		return new double[][] { Arrays.copyOf(xs, kept), Arrays.copyOf(ys, kept) };
	}

	public static int[] signalPixels(double[] xs, double[] ys, int rimWidthPx, int imageWidth, int imageHeight) {
		return SpotRimGeometry.strokePixels(xs, ys, rimWidthPx, imageWidth, imageHeight);
	}

	/**
	 * Floor band. Pixels that fall inside {@code blockers} (other spot ellipses)
	 * are dropped so a neighbor's dye is not the zero.
	 */
	public static int[] floorPixels(double[] xs, double[] ys, int rimWidthPx, int outerPx, int imageWidth,
			int imageHeight, EllipseGeom[] blockers) {
		if (xs == null || ys == null || xs.length < 3) {
			return new int[0];
		}
		double[][] outer = SpotRimGeometry.offsetOutward(xs, ys, outerPx);
		int[] raw = SpotRimGeometry.strokePixels(outer[0], outer[1], rimWidthPx, imageWidth, imageHeight);
		if (blockers == null || blockers.length == 0) {
			return raw;
		}
		int[] kept = new int[raw.length];
		int n = 0;
		for (int pix : raw) {
			int x = pix % imageWidth;
			int y = pix / imageWidth;
			if (!insideAny(blockers, x, y)) {
				kept[n++] = pix;
			}
		}
		return Arrays.copyOf(kept, n);
	}

	public static Layout layoutFor(int imageWidth, int imageHeight, List<Spot> spots, List<EllipseGeom> spotEllipses,
			List<EllipseGeom> allEllipses) {
		int n = spots != null ? spots.size() : 0;
		int[][] signal = new int[n][];
		int[][] floor = new int[n][];
		for (int i = 0; i < n; i++) {
			signal[i] = new int[0];
			floor[i] = new int[0];
			Spot spot = spots.get(i);
			if (spot == null) {
				continue;
			}
			SpotRimGeometry rim = spot.getRimGeometry();
			if (!rim.hasOutline()) {
				continue;
			}
			double[] xs = rim.outlineX();
			double[] ys = rim.outlineY();
			EllipseGeom self = spotEllipses != null && i < spotEllipses.size() ? spotEllipses.get(i) : null;
			signal[i] = signalPixels(xs, ys, rim.getRimWidthPx(), imageWidth, imageHeight);
			floor[i] = floorPixels(xs, ys, rim.getRimWidthPx(), rim.getOuterPx(), imageWidth, imageHeight,
					others(self, allEllipses));
		}
		return new Layout(imageWidth, n, floor, signal);
	}

	private static EllipseGeom[] others(EllipseGeom self, List<EllipseGeom> all) {
		if (all == null || all.isEmpty()) {
			return new EllipseGeom[0];
		}
		List<EllipseGeom> out = new ArrayList<>();
		for (EllipseGeom ellipse : all) {
			if (ellipse != null && ellipse != self) {
				out.add(ellipse);
			}
		}
		return out.toArray(new EllipseGeom[0]);
	}

	private static double cutoff(EllipseGeom ellipse, int imageWidth, int imageHeight, int[][] red, int[][] green,
			int[][] blue, EllipseGeom[] containers, double madMultiplier) {
		List<double[]> chunks = new ArrayList<>();
		int n = 0;
		for (int t = 0; t < red.length; t++) {
			double[] sample = outsideRing(ellipse, imageWidth, imageHeight, channel(red, t), channel(green, t),
					channel(blue, t), containers);
			if (sample.length > 0) {
				chunks.add(sample);
				n += sample.length;
			}
		}
		if (n == 0) {
			return Double.NaN;
		}
		double[] all = new double[n];
		int k = 0;
		for (double[] chunk : chunks) {
			System.arraycopy(chunk, 0, all, k, chunk.length);
			k += chunk.length;
		}
		double med = median(all);
		double spread = mad(all);
		return med + madMultiplier * spread;
	}

	private static double[] outsideRing(EllipseGeom ellipse, int imageWidth, int imageHeight, int[] red, int[] green,
			int[] blue, EllipseGeom[] containers) {
		if (red == null || green == null || blue == null) {
			return new double[0];
		}
		int steps = 360 / ANGLE_STEP_DEG;
		double[] values = new double[steps * OUTSIDE_RING_PX];
		int n = 0;
		for (int a = 0; a < steps; a++) {
			double theta = Math.toRadians(a * ANGLE_STEP_DEG);
			double cos = Math.cos(theta);
			double sin = Math.sin(theta);
			double bound = boundaryRadius(ellipse, cos, sin);
			for (int extra = 1; extra <= OUTSIDE_RING_PX; extra++) {
				int x = (int) Math.round(ellipse.cx + (bound + extra) * cos);
				int y = (int) Math.round(ellipse.cy + (bound + extra) * sin);
				if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
					continue;
				}
				if (insideAny(containers, x, y)) {
					continue;
				}
				double deficit = deficitAt(red, green, blue, y * imageWidth + x);
				if (Double.isFinite(deficit)) {
					values[n++] = deficit;
				}
			}
		}
		return Arrays.copyOf(values, n);
	}

	/**
	 * Radius of the dark dye ridge on this ray, or -1. The ridge is the outermost
	 * strong local maximum inside the ellipse, so a pale fringe beyond the dark
	 * ring is left outside the outline.
	 */
	private static double ridgeRadius(EllipseGeom ellipse, int imageWidth, int imageHeight, int[] red, int[] green,
			int[] blue, double cos, double sin, double cutoff) {
		if (red == null || green == null || blue == null) {
			return -1;
		}
		int limit = (int) Math.floor(boundaryRadius(ellipse, cos, sin));
		if (limit < 2) {
			return -1;
		}
		double[] profile = new double[limit + 1];
		Arrays.fill(profile, Double.NaN);
		for (int r = 0; r <= limit; r++) {
			int x = (int) Math.round(ellipse.cx + r * cos);
			int y = (int) Math.round(ellipse.cy + r * sin);
			if (x < 0 || y < 0 || x >= imageWidth || y >= imageHeight) {
				break;
			}
			if (!ellipse.contains(x + 0.0, y + 0.0)) {
				break;
			}
			profile[r] = deficitAt(red, green, blue, y * imageWidth + x);
		}
		smoothRadial(profile, RADIAL_SMOOTH_HALF);
		double peak = Double.NEGATIVE_INFINITY;
		for (double value : profile) {
			if (Double.isFinite(value) && value > peak) {
				peak = value;
			}
		}
		if (!(peak > cutoff)) {
			return -1;
		}
		double level = cutoff + 0.5 * (peak - cutoff);
		int ridge = -1;
		for (int r = 1; r < limit; r++) {
			double value = profile[r];
			double prev = profile[r - 1];
			double next = profile[r + 1];
			if (!Double.isFinite(value) || !Double.isFinite(prev) || !Double.isFinite(next)) {
				continue;
			}
			if (value >= level && value >= prev && value >= next) {
				ridge = r;
			}
		}
		if (ridge >= 0) {
			return ridge;
		}
		for (int r = limit; r >= 1; r--) {
			if (Double.isFinite(profile[r]) && profile[r] >= level) {
				return r;
			}
		}
		return -1;
	}

	private static void smoothRadial(double[] profile, int half) {
		double[] copy = profile.clone();
		for (int i = 0; i < profile.length; i++) {
			double sum = 0;
			int n = 0;
			int from = Math.max(0, i - half);
			int to = Math.min(copy.length - 1, i + half);
			for (int j = from; j <= to; j++) {
				if (!Double.isFinite(copy[j])) {
					continue;
				}
				sum += copy[j];
				n++;
			}
			profile[i] = n == 0 ? Double.NaN : sum / n;
		}
	}

	private static double[] circularMedian(double[] radii, int half) {
		int n = radii.length;
		double[] out = new double[n];
		double[] window = new double[half * 2 + 1];
		for (int i = 0; i < n; i++) {
			int count = 0;
			for (int k = -half; k <= half; k++) {
				double value = radii[Math.floorMod(i + k, n)];
				if (Double.isFinite(value)) {
					window[count++] = value;
				}
			}
			out[i] = count == 0 ? Double.NaN : median(Arrays.copyOf(window, count));
		}
		return out;
	}

	static double boundaryRadius(EllipseGeom ellipse, double cos, double sin) {
		double a = cos / ellipse.rx;
		double b = sin / ellipse.ry;
		double q = a * a + b * b;
		if (q <= 1e-12) {
			return 0;
		}
		return 1.0 / Math.sqrt(q);
	}

	static double deficitAt(int[] red, int[] green, int[] blue, int pix) {
		if (red == null || green == null || blue == null || pix < 0 || pix >= red.length || pix >= green.length
				|| pix >= blue.length) {
			return Double.NaN;
		}
		return ((green[pix] + blue[pix]) * 0.5) - red[pix];
	}

	private static boolean insideAny(EllipseGeom[] ellipses, int x, int y) {
		if (ellipses == null) {
			return false;
		}
		for (EllipseGeom ellipse : ellipses) {
			if (ellipse != null && ellipse.contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	private static int[] channel(int[][] frames, int t) {
		if (frames == null || t < 0 || t >= frames.length) {
			return null;
		}
		return frames[t];
	}

	static double median(double[] values) {
		int n = 0;
		if (values != null) {
			for (double v : values) {
				if (Double.isFinite(v)) {
					n++;
				}
			}
		}
		if (n == 0) {
			return Double.NaN;
		}
		double[] copy = new double[n];
		int k = 0;
		for (double v : values) {
			if (Double.isFinite(v)) {
				copy[k++] = v;
			}
		}
		Arrays.sort(copy);
		if ((n & 1) == 1) {
			return copy[n / 2];
		}
		return 0.5 * (copy[n / 2 - 1] + copy[n / 2]);
	}

	private static double mad(double[] values) {
		double med = median(values);
		if (Double.isNaN(med)) {
			return Double.NaN;
		}
		double[] dev = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			dev[i] = Math.abs(values[i] - med);
		}
		return median(dev);
	}
}
