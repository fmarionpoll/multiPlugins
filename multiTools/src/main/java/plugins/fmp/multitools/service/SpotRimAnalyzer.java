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
 * Outer perimeter of the dye inside a spot ellipse, from the first bins. The
 * stored outline is that perimeter. The path width is the inward distance that
 * still holds most of the dye. The floor is the outline offset outward.
 */
public final class SpotRimAnalyzer {

	public static final int ANGLE_STEP_DEG = 5;
	public static final int OUTSIDE_RING_PX = 3;
	static final int MIN_VERTICES = 8;
	/** Fraction of dye pixels the inward path must contain. */
	private static final double COLOR_COVERAGE = 0.8;
	private static final int MIN_DYE_PIXELS = 40;
	private static final int CONTOUR_SMOOTH_HALF = 4;
	/** Same share of the spot as the later rim measurement: a fly covers the spot. */
	private static final double FLY_SPOT_FRACTION = 0.08;
	/** Outline used when every opening frame has a fly on the spot: ROI radius minus 30%. */
	private static final double FLY_FALLBACK_SCALE = 0.7;

	/** Outer dye perimeter and the inward path width that covers most of the dye. */
	public static final class Detection {
		public final double[] x;
		public final double[] y;
		public final int pathWidthPx;
		/** True when the outline is the spot ROI shrunk because every frame had a fly. */
		public final boolean flyFallback;

		public Detection(double[] x, double[] y, int pathWidthPx) {
			this(x, y, pathWidthPx, false);
		}

		public Detection(double[] x, double[] y, int pathWidthPx, boolean flyFallback) {
			this.x = x;
			this.y = y;
			this.pathWidthPx = Math.max(1, pathWidthPx);
			this.flyFallback = flyFallback;
		}
	}

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
		public final boolean trackPlate;

		public Params(double madMultiplier, int initialBins, int rimWidthPx, int outerPx, int smoothBins,
				boolean insectGate, ImageTransformEnums insectTransform, int insectThreshold, boolean insectAbove,
				boolean trackPlate) {
			this.madMultiplier = madMultiplier;
			this.initialBins = Math.max(1, initialBins);
			this.rimWidthPx = Math.max(1, rimWidthPx);
			this.outerPx = Math.max(0, outerPx);
			this.smoothBins = Math.max(1, smoothBins);
			this.insectGate = insectGate;
			this.insectTransform = insectTransform != null ? insectTransform : ImageTransformEnums.B_RGB;
			this.insectThreshold = insectThreshold;
			this.insectAbove = insectAbove;
			this.trackPlate = trackPlate;
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
	 * Outer perimeter of the dye, or null when the ellipse has no separable stain.
	 * {@code containers} are every spot ellipse; pixels inside another one are not
	 * part of this spot, and they are not part of the floor ring that sets the cutoff.
	 */
	public static Detection detect(EllipseGeom ellipse, int imageWidth, int imageHeight, int[][] red, int[][] green,
			int[][] blue, EllipseGeom[] containers, double madMultiplier) {
		return detect(ellipse, imageWidth, imageHeight, red, green, blue, null, containers, madMultiplier);
	}

	/**
	 * {@code insect} aligns with the color frames. A pixel under a fly is left out
	 * of the median. A frame whose fly covers at least {@link #FLY_SPOT_FRACTION}
	 * of the ellipse is left out entirely. When every frame is covered, the
	 * outline is the spot ellipse at {@link #FLY_FALLBACK_SCALE}.
	 */
	public static Detection detect(EllipseGeom ellipse, int imageWidth, int imageHeight, int[][] red, int[][] green,
			int[][] blue, boolean[][] insect, EllipseGeom[] containers, double madMultiplier) {
		if (ellipse == null || imageWidth <= 0 || imageHeight <= 0 || red == null || red.length == 0) {
			return null;
		}
		Box box = Box.of(ellipse, imageWidth, imageHeight);
		if (box == null) {
			return null;
		}
		boolean[] covered = flyCoveredFrames(ellipse, imageWidth, imageHeight, red, insect);
		if (everyFrameCovered(red, covered)) {
			return spotRoiShrunk(ellipse);
		}
		double[] score = medianScore(box, imageWidth, red, green, blue, insect, covered);
		double threshold = dyeThreshold(score, box, ellipse, containers, imageWidth, imageHeight, red, green, blue,
				madMultiplier);
		if (Double.isNaN(threshold)) {
			return null;
		}
		boolean[] dye = dyeMask(score, box, ellipse, containers, threshold);
		if (count(dye) < MIN_DYE_PIXELS) {
			return null;
		}
		dye = largestComponent(dye, box.w);
		if (count(dye) < MIN_DYE_PIXELS) {
			return null;
		}
		double[][] contour = outerContour(dye, box);
		if (contour == null) {
			return null;
		}
		contour = smoothClosed(contour[0], contour[1], CONTOUR_SMOOTH_HALF);
		if (contour[0].length < MIN_VERTICES) {
			return null;
		}
		int path = pathWidth(dye, box, contour[0], contour[1]);
		return new Detection(contour[0], contour[1], path);
	}

	public static int[] signalPixels(double[] xs, double[] ys, int rimWidthPx, int imageWidth, int imageHeight) {
		return SpotRimGeometry.inwardBandPixels(xs, ys, rimWidthPx, imageWidth, imageHeight);
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
			floor[i] = floorPixels(xs, ys, SpotRimGeometry.DEFAULT_RIM_WIDTH_PX, rim.getOuterPx(), imageWidth,
					imageHeight, others(self, allEllipses));
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

	private static double[] medianScore(Box box, int imageWidth, int[][] red, int[][] green, int[][] blue,
			boolean[][] insect, boolean[] covered) {
		double[] score = new double[box.w * box.h];
		Arrays.fill(score, Double.NaN);
		double[] samples = new double[red.length];
		for (int y = 0; y < box.h; y++) {
			int iy = box.y0 + y;
			for (int x = 0; x < box.w; x++) {
				int ix = box.x0 + x;
				int n = 0;
				int pix = iy * imageWidth + ix;
				for (int t = 0; t < red.length; t++) {
					if (covered != null && t < covered.length && covered[t]) {
						continue;
					}
					if (insectAt(insect, t, pix)) {
						continue;
					}
					double value = deficitAt(channel(red, t), channel(green, t), channel(blue, t), pix);
					if (Double.isFinite(value)) {
						samples[n++] = value;
					}
				}
				if (n > 0) {
					score[y * box.w + x] = median(Arrays.copyOf(samples, n));
				}
			}
		}
		return score;
	}

	/** True for each frame whose fly covers at least {@link #FLY_SPOT_FRACTION} of the ellipse. */
	private static boolean[] flyCoveredFrames(EllipseGeom ellipse, int imageWidth, int imageHeight, int[][] red,
			boolean[][] insect) {
		boolean[] covered = new boolean[red.length];
		if (insect == null) {
			return covered;
		}
		for (int t = 0; t < red.length; t++) {
			boolean[] mask = t < insect.length ? insect[t] : null;
			if (mask == null || channel(red, t) == null) {
				continue;
			}
			int inside = 0;
			int hits = 0;
			int x0 = Math.max(0, (int) Math.floor(ellipse.cx - ellipse.rx));
			int y0 = Math.max(0, (int) Math.floor(ellipse.cy - ellipse.ry));
			int x1 = Math.min(imageWidth - 1, (int) Math.ceil(ellipse.cx + ellipse.rx));
			int y1 = Math.min(imageHeight - 1, (int) Math.ceil(ellipse.cy + ellipse.ry));
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					if (!ellipse.contains(x, y)) {
						continue;
					}
					inside++;
					int pix = y * imageWidth + x;
					if (pix >= 0 && pix < mask.length && mask[pix]) {
						hits++;
					}
				}
			}
			covered[t] = inside > 0 && hits >= FLY_SPOT_FRACTION * inside;
		}
		return covered;
	}

	private static boolean everyFrameCovered(int[][] red, boolean[] covered) {
		int data = 0;
		int hits = 0;
		for (int t = 0; t < red.length; t++) {
			if (channel(red, t) == null) {
				continue;
			}
			data++;
			if (covered != null && t < covered.length && covered[t]) {
				hits++;
			}
		}
		return data > 0 && hits == data;
	}

	private static boolean insectAt(boolean[][] insect, int t, int pix) {
		if (insect == null || t < 0 || t >= insect.length) {
			return false;
		}
		boolean[] mask = insect[t];
		return mask != null && pix >= 0 && pix < mask.length && mask[pix];
	}

	private static Detection spotRoiShrunk(EllipseGeom ellipse) {
		int n = 360 / ANGLE_STEP_DEG;
		double[] xs = new double[n];
		double[] ys = new double[n];
		double rx = ellipse.rx * FLY_FALLBACK_SCALE;
		double ry = ellipse.ry * FLY_FALLBACK_SCALE;
		for (int a = 0; a < n; a++) {
			double theta = Math.toRadians(a * ANGLE_STEP_DEG);
			xs[a] = ellipse.cx + rx * Math.cos(theta);
			ys[a] = ellipse.cy + ry * Math.sin(theta);
		}
		return new Detection(xs, ys, SpotRimGeometry.DEFAULT_RIM_WIDTH_PX, true);
	}

	private static double dyeThreshold(double[] score, Box box, EllipseGeom ellipse, EllipseGeom[] containers,
			int imageWidth, int imageHeight, int[][] red, int[][] green, int[][] blue, double madMultiplier) {
		double[] inside = new double[score.length];
		int n = 0;
		for (int i = 0; i < score.length; i++) {
			if (!Double.isFinite(score[i])) {
				continue;
			}
			int x = box.x0 + i % box.w;
			int y = box.y0 + i / box.w;
			if (!ellipse.contains(x, y) || insideAny(othersOf(ellipse, containers), x, y)) {
				continue;
			}
			inside[n++] = score[i];
		}
		double otsu = otsu(Arrays.copyOf(inside, n));
		if (Double.isNaN(otsu)) {
			return Double.NaN;
		}
		double floor = cutoff(ellipse, imageWidth, imageHeight, red, green, blue, containers, madMultiplier);
		if (Double.isNaN(floor)) {
			return otsu;
		}
		return Math.max(floor, otsu);
	}

	/** Otsu cut on {@code values}, or NaN when they are not split into two classes. */
	static double otsu(double[] values) {
		int n = 0;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		if (values != null) {
			for (double v : values) {
				if (!Double.isFinite(v)) {
					continue;
				}
				n++;
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
		}
		if (n < MIN_DYE_PIXELS || !(max - min > 1e-3)) {
			return Double.NaN;
		}
		int bins = 256;
		int[] hist = new int[bins];
		double scale = (bins - 1) / (max - min);
		for (double v : values) {
			if (!Double.isFinite(v)) {
				continue;
			}
			int b = (int) Math.round((v - min) * scale);
			if (b < 0) {
				b = 0;
			} else if (b >= bins) {
				b = bins - 1;
			}
			hist[b]++;
		}
		double sum = 0;
		for (int i = 0; i < bins; i++) {
			sum += (double) i * hist[i];
		}
		double sumB = 0;
		int wB = 0;
		double maxVar = -1;
		int best = 0;
		for (int i = 0; i < bins; i++) {
			wB += hist[i];
			if (wB == 0) {
				continue;
			}
			int wF = n - wB;
			if (wF == 0) {
				break;
			}
			sumB += (double) i * hist[i];
			double mB = sumB / wB;
			double mF = (sum - sumB) / wF;
			double between = (double) wB * wF * (mB - mF) * (mB - mF);
			if (between > maxVar) {
				maxVar = between;
				best = i;
			}
		}
		if (!(maxVar > 0)) {
			return Double.NaN;
		}
		return min + (best + 1) / scale;
	}

	private static boolean[] dyeMask(double[] score, Box box, EllipseGeom ellipse, EllipseGeom[] containers,
			double threshold) {
		boolean[] dye = new boolean[score.length];
		for (int i = 0; i < score.length; i++) {
			if (!Double.isFinite(score[i]) || score[i] < threshold) {
				continue;
			}
			int x = box.x0 + i % box.w;
			int y = box.y0 + i / box.w;
			if (ellipse.contains(x, y) && !insideAny(othersOf(ellipse, containers), x, y)) {
				dye[i] = true;
			}
		}
		return dye;
	}

	private static EllipseGeom[] othersOf(EllipseGeom self, EllipseGeom[] containers) {
		if (containers == null || containers.length == 0) {
			return new EllipseGeom[0];
		}
		List<EllipseGeom> out = new ArrayList<>();
		for (EllipseGeom ellipse : containers) {
			if (ellipse != null && ellipse != self) {
				out.add(ellipse);
			}
		}
		return out.toArray(new EllipseGeom[0]);
	}

	private static boolean[] largestComponent(boolean[] dye, int w) {
		boolean[] seen = new boolean[dye.length];
		boolean[] best = new boolean[dye.length];
		int bestN = 0;
		int[] stack = new int[dye.length];
		int h = dye.length / w;
		for (int seed = 0; seed < dye.length; seed++) {
			if (!dye[seed] || seen[seed]) {
				continue;
			}
			int sp = 0;
			stack[sp++] = seed;
			seen[seed] = true;
			int n = 0;
			int[] cells = new int[dye.length];
			while (sp > 0) {
				int i = stack[--sp];
				cells[n++] = i;
				int x = i % w;
				int y = i / w;
				if (x > 0) {
					sp = push(dye, seen, stack, sp, i - 1);
				}
				if (x + 1 < w) {
					sp = push(dye, seen, stack, sp, i + 1);
				}
				if (y > 0) {
					sp = push(dye, seen, stack, sp, i - w);
				}
				if (y + 1 < h) {
					sp = push(dye, seen, stack, sp, i + w);
				}
			}
			if (n > bestN) {
				bestN = n;
				Arrays.fill(best, false);
				for (int k = 0; k < n; k++) {
					best[cells[k]] = true;
				}
			}
		}
		return best;
	}

	private static int push(boolean[] dye, boolean[] seen, int[] stack, int sp, int i) {
		if (!dye[i] || seen[i]) {
			return sp;
		}
		seen[i] = true;
		stack[sp++] = i;
		return sp;
	}

	private static final int[] TRACE_X = { 1, 1, 0, -1, -1, -1, 0, 1 };
	private static final int[] TRACE_Y = { 0, 1, 1, 1, 0, -1, -1, -1 };

	/** Outer boundary of {@code dye}, in image coordinates. */
	private static double[][] outerContour(boolean[] dye, Box box) {
		int start = -1;
		for (int i = 0; i < dye.length; i++) {
			if (dye[i]) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			return null;
		}
		int sx = box.x0 + start % box.w;
		int sy = box.y0 + start / box.w;
		int x = sx;
		int y = sy;
		int dir = 7;
		double[] xs = new double[dye.length];
		double[] ys = new double[dye.length];
		int n = 0;
		int guard = dye.length + 2;
		while (guard-- > 0) {
			xs[n] = x;
			ys[n] = y;
			n++;
			boolean found = false;
			for (int k = 0; k < 8; k++) {
				int d = (dir + k) % 8;
				int nx = x + TRACE_X[d];
				int ny = y + TRACE_Y[d];
				if (!foreground(dye, box, nx, ny)) {
					continue;
				}
				x = nx;
				y = ny;
				dir = (d + 5) % 8;
				found = true;
				break;
			}
			if (!found || (x == sx && y == sy)) {
				break;
			}
		}
		if (n < MIN_VERTICES) {
			return null;
		}
		return new double[][] { Arrays.copyOf(xs, n), Arrays.copyOf(ys, n) };
	}

	private static boolean foreground(boolean[] dye, Box box, int x, int y) {
		if (x < box.x0 || y < box.y0 || x >= box.x0 + box.w || y >= box.y0 + box.h) {
			return false;
		}
		return dye[(y - box.y0) * box.w + (x - box.x0)];
	}

	private static double[][] smoothClosed(double[] xs, double[] ys, int half) {
		int n = Math.min(xs.length, ys.length);
		double[] sx = new double[n];
		double[] sy = new double[n];
		for (int i = 0; i < n; i++) {
			double ax = 0;
			double ay = 0;
			int c = 0;
			for (int k = -half; k <= half; k++) {
				int j = Math.floorMod(i + k, n);
				ax += xs[j];
				ay += ys[j];
				c++;
			}
			sx[i] = ax / c;
			sy[i] = ay / c;
		}
		int step = Math.max(1, n / 72);
		int m = (n + step - 1) / step;
		double[] ox = new double[m];
		double[] oy = new double[m];
		int t = 0;
		for (int i = 0; i < n; i += step) {
			ox[t] = sx[i];
			oy[t] = sy[i];
			t++;
		}
		return new double[][] { Arrays.copyOf(ox, t), Arrays.copyOf(oy, t) };
	}

	private static int pathWidth(boolean[] dye, Box box, double[] xs, double[] ys) {
		int n = count(dye);
		double[] dist = new double[n];
		int k = 0;
		for (int i = 0; i < dye.length; i++) {
			if (!dye[i]) {
				continue;
			}
			double x = box.x0 + i % box.w;
			double y = box.y0 + i / box.w;
			dist[k++] = SpotRimGeometry.distanceToClosed(x, y, xs, ys);
		}
		Arrays.sort(dist, 0, k);
		int at = Math.max(0, (int) Math.ceil(COLOR_COVERAGE * k) - 1);
		return Math.max(1, (int) Math.ceil(dist[at]));
	}

	private static int count(boolean[] dye) {
		int n = 0;
		for (boolean pixel : dye) {
			if (pixel) {
				n++;
			}
		}
		return n;
	}

	private static final class Box {
		final int x0;
		final int y0;
		final int w;
		final int h;

		Box(int x0, int y0, int w, int h) {
			this.x0 = x0;
			this.y0 = y0;
			this.w = w;
			this.h = h;
		}

		static Box of(EllipseGeom ellipse, int imageWidth, int imageHeight) {
			int x0 = (int) Math.floor(ellipse.cx - ellipse.rx) - 1;
			int y0 = (int) Math.floor(ellipse.cy - ellipse.ry) - 1;
			int x1 = (int) Math.ceil(ellipse.cx + ellipse.rx) + 1;
			int y1 = (int) Math.ceil(ellipse.cy + ellipse.ry) + 1;
			if (x0 < 0) {
				x0 = 0;
			}
			if (y0 < 0) {
				y0 = 0;
			}
			if (x1 >= imageWidth) {
				x1 = imageWidth - 1;
			}
			if (y1 >= imageHeight) {
				y1 = imageHeight - 1;
			}
			if (x1 <= x0 || y1 <= y0) {
				return null;
			}
			return new Box(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
		}
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
