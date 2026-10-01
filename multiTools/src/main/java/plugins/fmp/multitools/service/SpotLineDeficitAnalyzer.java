package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

/**
 * Fraction of an X through each spot that still sits above the floor. The two
 * diagonals cross at the circle center and run past the rim by flankPx. Pixels
 * on those arms outside every circle are that spot's floor. A pixel inside the
 * circle counts when its red deficit clears that floor. Darkening the same
 * pixels does not raise the fraction. A shrinking drop loses pixels and the
 * ratio falls. A short moving median takes out bin-to-bin flicker.
 */
public final class SpotLineDeficitAnalyzer {

	/** Odd width, in bins, of the moving median applied before I/I0. */
	public static final int DEFAULT_SMOOTH_BINS = 9;

	/** Same cutoff as the other spot measures: a fly on at least this fraction of the cross drops the bin. */
	static final double FLY_OCCUPANCY_FRACTION = 0.08;

	/**
	 * A pixel still counts only while its excess is at least this share of the
	 * spot's initial upper-quartile excess. The median of the cross sits in the
	 * empty center of a thin dye ring and would keep an eaten spot on.
	 */
	public static final double DYE_KEEP_FRACTION = 0.5;

	/** Quantile of the in-circle excess used as that spot's initial dye level. */
	static final double DYE_REFERENCE_QUANTILE = 0.75;

	public static final class Params {
		public final double madMultiplier;
		public final int initialBins;
		public final int flankPx;
		public final int smoothBins;
		public final boolean insectGate;
		public final ImageTransformEnums insectTransform;
		public final int insectThreshold;
		public final boolean insectAbove;

		public Params(double madMultiplier, int initialBins, int flankPx) {
			this(madMultiplier, initialBins, flankPx, 1);
		}

		public Params(double madMultiplier, int initialBins, int flankPx, int smoothBins) {
			this(madMultiplier, initialBins, flankPx, smoothBins, false, ImageTransformEnums.B_RGB, 50, false);
		}

		public Params(double madMultiplier, int initialBins, int flankPx, int smoothBins, boolean insectGate,
				ImageTransformEnums insectTransform, int insectThreshold, boolean insectAbove) {
			this.madMultiplier = madMultiplier;
			this.initialBins = Math.max(1, initialBins);
			this.flankPx = Math.max(0, flankPx);
			this.smoothBins = Math.max(1, smoothBins);
			this.insectGate = insectGate;
			this.insectTransform = insectTransform != null ? insectTransform : ImageTransformEnums.B_RGB;
			this.insectThreshold = insectThreshold;
			this.insectAbove = insectAbove;
		}
	}

	/**
	 * One X. {@code (x0,y0)-(x1,y1)} and {@code (u0,v0)-(u1,v1)} are the two
	 * diagonals, tips included. Each {@code tipX0[i]..tipX1[i]} segment is floor:
	 * outside every circle, and the only pixels used as that spot's zero.
	 */
	public static final class SpotCross {
		public final int x0;
		public final int y0;
		public final int x1;
		public final int y1;
		public final int u0;
		public final int v0;
		public final int u1;
		public final int v1;
		public final int[] tipX0;
		public final int[] tipY0;
		public final int[] tipX1;
		public final int[] tipY1;

		SpotCross(int x0, int y0, int x1, int y1, int u0, int v0, int u1, int v1, int[] tipX0, int[] tipY0,
				int[] tipX1, int[] tipY1) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.tipX0 = tipX0;
			this.tipY0 = tipY0;
			this.tipX1 = tipX1;
			this.tipY1 = tipY1;
		}
	}

	/** Circle center and radius in camera pixels. The circle encloses the drop. */
	public static final class SpotGeom {
		public final int centerX;
		public final int centerY;
		public final int radius;

		public SpotGeom(int centerX, int centerY, int radius) {
			this.centerX = centerX;
			this.centerY = centerY;
			this.radius = radius;
		}
	}

	/**
	 * Pixel sets for one cage. Flank pixels are the X tips of one spot, outside
	 * every circle. Spot pixels are the same X inside that circle. Index matches
	 * the spot list.
	 */
	public static final class Layout {
		public final int width;
		public final int nSpots;
		final int[][] flankPix;
		final int[][] spotPix;

		Layout(int width, int nSpots, int[][] flankPix, int[][] spotPix) {
			this.width = width;
			this.nSpots = nSpots;
			this.flankPix = flankPix;
			this.spotPix = spotPix;
		}
	}

	private static final class IndexedSpot {
		final int index;
		final SpotGeom geom;

		IndexedSpot(int index, SpotGeom geom) {
			this.index = index;
			this.geom = geom;
		}
	}

	private static final class BuiltCross {
		final int[] signal;
		final int[] flank;
		final SpotCross cross;

		BuiltCross(int[] signal, int[] flank, SpotCross cross) {
			this.signal = signal;
			this.flank = flank;
			this.cross = cross;
		}
	}

	private SpotLineDeficitAnalyzer() {
	}

	/** One cross per input spot. Missing circles are null. */
	public static List<SpotCross> crosses(List<SpotGeom> spots, int width, int height, int flankPx) {
		int n = spots != null ? spots.size() : 0;
		List<SpotCross> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(null);
		}
		if (spots == null || width <= 0 || height <= 0) {
			return out;
		}
		int flank = Math.max(0, flankPx);
		List<IndexedSpot> usable = usableSpots(spots);
		for (IndexedSpot s : usable) {
			out.set(s.index, buildCross(s, usable, width, height, flank).cross);
		}
		return out;
	}

	public static Layout layout(List<SpotGeom> spots, int width, int height, int flankPx) {
		int nSpots = spots != null ? spots.size() : 0;
		int[][] spotPix = new int[nSpots][];
		int[][] flankPix = new int[nSpots][];
		for (int i = 0; i < nSpots; i++) {
			spotPix[i] = new int[0];
			flankPix[i] = new int[0];
		}
		if (spots == null || width <= 0 || height <= 0) {
			return new Layout(width, nSpots, flankPix, spotPix);
		}
		int flank = Math.max(0, flankPx);
		List<IndexedSpot> usable = usableSpots(spots);
		for (IndexedSpot s : usable) {
			BuiltCross built = buildCross(s, usable, width, height, flank);
			spotPix[s.index] = built.signal;
			flankPix[s.index] = built.flank;
		}
		return new Layout(width, nSpots, flankPix, spotPix);
	}

	public static double[][] analyze(List<SpotGeom> spots, int width, int height, int[][] red, int[][] green,
			int[][] blue, Params params) {
		Params p = params != null ? params : new Params(5.0, 5, 40);
		Layout layout = layout(spots, width, height, p.flankPx);
		int n = red != null ? red.length : 0;
		double[][] flanks = new double[n][];
		for (int t = 0; t < n; t++) {
			flanks[t] = sampleFlanks(layout, channel(red, t), channel(green, t), channel(blue, t));
		}
		int window = Math.min(p.initialBins, n);
		double noise = p.madMultiplier * mad(pool(flanks, window));
		double[] dye = dyeReference(layout, red, green, blue, flanks, window);
		double[][] integral = new double[layout.nSpots][n];
		for (int t = 0; t < n; t++) {
			double[] floors = spotFloors(layout, flanks[t]);
			integrate(layout, channel(red, t), channel(green, t), channel(blue, t), floors, noise, integral, t, null,
					dye);
		}
		return ratiosFromIntegrals(integral, window, p.smoothBins);
	}

	/** Deficits on flank pixels, concatenated in spot order. Insect pixels are skipped. */
	public static double[] sampleFlanks(Layout layout, int[] red, int[] green, int[] blue) {
		return sampleFlanks(layout, red, green, blue, null);
	}

	public static double[] sampleFlanks(Layout layout, int[] red, int[] green, int[] blue, boolean[] insect) {
		if (layout == null || red == null || green == null || blue == null) {
			return new double[0];
		}
		int n = 0;
		for (int[] row : layout.flankPix) {
			if (row != null) {
				n += row.length;
			}
		}
		double[] out = new double[n];
		int k = 0;
		for (int[] row : layout.flankPix) {
			if (row == null) {
				continue;
			}
			for (int pix : row) {
				out[k++] = isInsect(insect, pix) ? Double.NaN : deficitAt(red, green, blue, pix);
			}
		}
		return out;
	}

	/**
	 * One floor level per spot, in spot-list order. Values are medians of that
	 * spot's flank deficits inside {@code allFlanks}, which is the concatenation
	 * produced by {@link #sampleFlanks}.
	 */
	public static double[] spotFloors(Layout layout, double[] allFlanks) {
		int nSpots = layout != null && layout.flankPix != null ? layout.flankPix.length : 0;
		double[] floors = new double[nSpots];
		if (layout == null || allFlanks == null) {
			return floors;
		}
		int offset = 0;
		for (int s = 0; s < nSpots; s++) {
			int len = layout.flankPix[s].length;
			if (offset + len <= allFlanks.length && len > 0) {
				floors[s] = median(Arrays.copyOfRange(allFlanks, offset, offset + len));
			}
			offset += len;
		}
		return floors;
	}

	public static void integrate(Layout layout, int[] red, int[] green, int[] blue, double[] spotFloor, double noise,
			double[][] integral, int time) {
		integrate(layout, red, green, blue, spotFloor, noise, integral, time, null);
	}

	public static void integrate(Layout layout, int[] red, int[] green, int[] blue, double[] spotFloor, double noise,
			double[][] integral, int time, boolean[] insect) {
		integrate(layout, red, green, blue, spotFloor, noise, integral, time, insect, null);
	}

	public static void integrate(Layout layout, int[] red, int[] green, int[] blue, double[] spotFloor, double noise,
			double[][] integral, int time, boolean[] insect, double[] dyeLevel) {
		if (layout == null || red == null || green == null || blue == null || integral == null) {
			return;
		}
		for (int s = 0; s < layout.nSpots; s++) {
			double floor = spotFloor != null && s < spotFloor.length ? spotFloor[s] : 0.0;
			int[] pix = layout.spotPix[s];
			int total = pix.length;
			int insectCount = 0;
			int used = 0;
			int above = 0;
			for (int i = 0; i < pix.length; i++) {
				if (isInsect(insect, pix[i])) {
					insectCount++;
					continue;
				}
				used++;
				double excess = deficitAt(red, green, blue, pix[i]) - floor;
				if (excess > cutFor(noise, dyeLevel, s)) {
					above++;
				}
			}
			double value = Double.NaN;
			if (used > 0 && insectCount < FLY_OCCUPANCY_FRACTION * total) {
				value = above / (double) used;
			}
			if (time >= 0 && time < integral[s].length) {
				integral[s][time] = value;
			}
		}
	}

	static double cutFor(double noise, double[] dyeLevel, int spot) {
		double cut = noise;
		if (dyeLevel != null && spot >= 0 && spot < dyeLevel.length && Double.isFinite(dyeLevel[spot])
				&& dyeLevel[spot] > 0.0) {
			cut = Math.max(noise, DYE_KEEP_FRACTION * dyeLevel[spot]);
		}
		return cut;
	}

	/** Upper-quartile excess on the cross for one frame. NaN when the cross is empty. */
	public static double medianSignalExcess(Layout layout, int spot, int[] red, int[] green, int[] blue, double floor,
			boolean[] insect) {
		if (layout == null || layout.spotPix == null || spot < 0 || spot >= layout.spotPix.length || red == null) {
			return Double.NaN;
		}
		int[] pix = layout.spotPix[spot];
		if (pix == null || pix.length == 0) {
			return Double.NaN;
		}
		double[] excess = new double[pix.length];
		int n = 0;
		for (int i = 0; i < pix.length; i++) {
			if (isInsect(insect, pix[i])) {
				continue;
			}
			double deficit = deficitAt(red, green, blue, pix[i]);
			if (Double.isFinite(deficit)) {
				excess[n++] = deficit - floor;
			}
		}
		if (n == 0) {
			return Double.NaN;
		}
		return quantile(Arrays.copyOf(excess, n), DYE_REFERENCE_QUANTILE);
	}

	public static double referenceExcess(double[] frameMedians) {
		return median(frameMedians);
	}

	public static double[] dyeReference(Layout layout, int[][] red, int[][] green, int[][] blue, double[][] flanks,
			int window) {
		int nSpots = layout != null ? layout.nSpots : 0;
		double[] ref = new double[nSpots];
		int limit = Math.min(Math.max(0, window), flanks != null ? flanks.length : 0);
		for (int s = 0; s < nSpots; s++) {
			double[] samples = new double[limit];
			for (int t = 0; t < limit; t++) {
				double[] floors = spotFloors(layout, flanks[t]);
				double floor = floors != null && s < floors.length ? floors[s] : 0.0;
				samples[t] = medianSignalExcess(layout, s, channel(red, t), channel(green, t), channel(blue, t), floor,
						null);
			}
			ref[s] = median(samples);
		}
		return ref;
	}

	private static boolean isInsect(boolean[] insect, int pix) {
		return insect != null && pix >= 0 && pix < insect.length && insect[pix];
	}

	public static double[][] ratiosFromIntegrals(double[][] integral, int initialBins) {
		return ratiosFromIntegrals(integral, initialBins, 1);
	}

	/**
	 * Moving median of {@code smoothBins}, then each bin divided by the median of
	 * the first {@code initialBins} of that smoothed series.
	 */
	public static double[][] ratiosFromIntegrals(double[][] integral, int initialBins, int smoothBins) {
		int nSpots = integral != null ? integral.length : 0;
		int nFrames = nSpots > 0 && integral[0] != null ? integral[0].length : 0;
		double[][] ratio = new double[nSpots][nFrames];
		for (int s = 0; s < nSpots; s++) {
			Arrays.fill(ratio[s], Double.NaN);
		}
		if (nFrames == 0) {
			return ratio;
		}
		int window = Math.min(Math.max(1, initialBins), nFrames);
		for (int s = 0; s < nSpots; s++) {
			double[] smoothed = movingMedian(integral[s], smoothBins);
			double[] initial = new double[window];
			System.arraycopy(smoothed, 0, initial, 0, window);
			double i0 = median(initial);
			if (!(i0 > 0.0)) {
				continue;
			}
			for (int t = 0; t < nFrames; t++) {
				ratio[s][t] = smoothed[t] / i0;
			}
		}
		return ratio;
	}

	static double[] movingMedian(double[] values, int window) {
		int n = values != null ? values.length : 0;
		double[] out = new double[n];
		if (n == 0) {
			return out;
		}
		int w = Math.max(1, window);
		if ((w & 1) == 0) {
			w++;
		}
		int half = w / 2;
		double[] buf = new double[w];
		for (int i = 0; i < n; i++) {
			int count = 0;
			int from = Math.max(0, i - half);
			int to = Math.min(n - 1, i + half);
			for (int j = from; j <= to; j++) {
				if (Double.isFinite(values[j])) {
					buf[count++] = values[j];
				}
			}
			out[i] = count == 0 ? Double.NaN : median(Arrays.copyOf(buf, count));
		}
		return out;
	}

	public static double poolMad(double[][] flankFrames, int window, double madMultiplier) {
		return madMultiplier * mad(pool(flankFrames, window));
	}

	private static BuiltCross buildCross(IndexedSpot spot, List<IndexedSpot> all, int width, int height, int flankPx) {
		int cx = spot.geom.centerX;
		int cy = spot.geom.centerY;
		int radius = spot.geom.radius;
		int sIn = edgeSteps(radius);
		int sTip = armSteps(radius, flankPx);
		int[] sigTmp = new int[Math.max(1, 2 * (2 * sIn + 1))];
		int nSig = 0;
		boolean centerAdded = false;
		int ax0 = cx;
		int ay0 = cy;
		int ax1 = cx;
		int ay1 = cy;
		int bx0 = cx;
		int by0 = cy;
		int bx1 = cx;
		int by1 = cy;
		boolean anyA = false;
		boolean anyB = false;
		int minA = 0;
		int maxA = 0;
		int minB = 0;
		int maxB = 0;
		for (int s = -sTip; s <= sTip; s++) {
			int ax = cx + s;
			int ay = cy + s;
			if (inImage(ax, ay, width, height)) {
				if (!anyA || s < minA) {
					minA = s;
					ax0 = ax;
					ay0 = ay;
				}
				if (!anyA || s > maxA) {
					maxA = s;
					ax1 = ax;
					ay1 = ay;
				}
				anyA = true;
				if (Math.abs(s) <= sIn && (s != 0 || !centerAdded) && nearest(all, ax, ay) == spot.index) {
					sigTmp[nSig++] = ay * width + ax;
					if (s == 0) {
						centerAdded = true;
					}
				}
			}
			int bx = cx + s;
			int by = cy - s;
			if (inImage(bx, by, width, height) && Math.abs(s) <= sIn && s != 0
					&& nearest(all, bx, by) == spot.index) {
				sigTmp[nSig++] = by * width + bx;
			}
			if (inImage(bx, by, width, height)) {
				if (!anyB || s < minB) {
					minB = s;
					bx0 = bx;
					by0 = by;
				}
				if (!anyB || s > maxB) {
					maxB = s;
					bx1 = bx;
					by1 = by;
				}
				anyB = true;
			}
		}
		int[][] dirs = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		int flankCap = 4 * Math.max(0, sTip - sIn);
		int[] flankTmp = new int[flankCap];
		int nFlank = 0;
		List<int[]> tips = new ArrayList<>();
		for (int[] d : dirs) {
			int run = -1;
			for (int s = sIn + 1; s <= sTip; s++) {
				int x = cx + d[0] * s;
				int y = cy + d[1] * s;
				boolean floor = inImage(x, y, width, height) && !insideAny(all, x, y);
				if (floor && run < 0) {
					run = s;
				}
				if (!floor && run >= 0) {
					tips.add(new int[] { cx + d[0] * run, cy + d[1] * run, cx + d[0] * (s - 1), cy + d[1] * (s - 1) });
					run = -1;
				}
				if (floor) {
					flankTmp[nFlank++] = y * width + x;
				}
			}
			if (run >= 0) {
				tips.add(new int[] { cx + d[0] * run, cy + d[1] * run, cx + d[0] * sTip, cy + d[1] * sTip });
			}
		}
		int nTips = tips.size();
		int[] tipX0 = new int[nTips];
		int[] tipY0 = new int[nTips];
		int[] tipX1 = new int[nTips];
		int[] tipY1 = new int[nTips];
		for (int i = 0; i < nTips; i++) {
			int[] tip = tips.get(i);
			tipX0[i] = tip[0];
			tipY0[i] = tip[1];
			tipX1[i] = tip[2];
			tipY1[i] = tip[3];
		}
		SpotCross cross = new SpotCross(ax0, ay0, ax1, ay1, bx0, by0, bx1, by1, tipX0, tipY0, tipX1, tipY1);
		return new BuiltCross(Arrays.copyOf(sigTmp, nSig), Arrays.copyOf(flankTmp, nFlank), cross);
	}

	/** Largest chessboard step whose diagonal pixel still lies inside the circle. */
	private static int edgeSteps(int radius) {
		int s = 0;
		long r2 = (long) radius * radius;
		while ((long) 2 * (s + 1) * (s + 1) <= r2) {
			s++;
		}
		return s;
	}

	private static int armSteps(int radius, int flankPx) {
		int s = edgeSteps(radius);
		long reach2 = (long) (radius + flankPx) * (radius + flankPx);
		while ((long) 2 * (s + 1) * (s + 1) <= reach2) {
			s++;
		}
		return s;
	}

	private static double[] pool(double[][] flankFrames, int window) {
		int n = 0;
		int limit = Math.min(window, flankFrames != null ? flankFrames.length : 0);
		for (int t = 0; t < limit; t++) {
			if (flankFrames[t] != null) {
				n += flankFrames[t].length;
			}
		}
		double[] out = new double[n];
		int k = 0;
		for (int t = 0; t < limit; t++) {
			if (flankFrames[t] == null) {
				continue;
			}
			for (double v : flankFrames[t]) {
				if (Double.isFinite(v)) {
					out[k++] = v;
				}
			}
		}
		return k == out.length ? out : Arrays.copyOf(out, k);
	}

	private static int[] channel(int[][] frames, int t) {
		if (frames == null || t < 0 || t >= frames.length) {
			return null;
		}
		return frames[t];
	}

	private static double deficitAt(int[] red, int[] green, int[] blue, int pix) {
		if (pix < 0 || pix >= red.length || pix >= green.length || pix >= blue.length) {
			return Double.NaN;
		}
		return ((green[pix] + blue[pix]) * 0.5) - red[pix];
	}

	private static List<IndexedSpot> usableSpots(List<SpotGeom> spots) {
		List<IndexedSpot> usable = new ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			SpotGeom g = spots.get(i);
			if (g != null && g.radius > 0) {
				usable.add(new IndexedSpot(i, g));
			}
		}
		return usable;
	}

	private static boolean inImage(int x, int y, int width, int height) {
		return x >= 0 && y >= 0 && x < width && y < height;
	}

	private static boolean insideAny(List<IndexedSpot> spots, int x, int y) {
		for (IndexedSpot s : spots) {
			long dx = (long) x - s.geom.centerX;
			long dy = (long) y - s.geom.centerY;
			long radius = s.geom.radius;
			if (dx * dx + dy * dy <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	private static int nearest(List<IndexedSpot> spots, int x, int y) {
		int best = spots.get(0).index;
		long bestDist = Long.MAX_VALUE;
		for (IndexedSpot s : spots) {
			long dx = (long) x - s.geom.centerX;
			long dy = (long) y - s.geom.centerY;
			long dist = dx * dx + dy * dy;
			if (dist < bestDist) {
				bestDist = dist;
				best = s.index;
			}
		}
		return best;
	}

	static double quantile(double[] values, double q) {
		if (values == null || values.length == 0) {
			return 0.0;
		}
		int n = 0;
		for (int i = 0; i < values.length; i++) {
			if (Double.isFinite(values[i])) {
				n++;
			}
		}
		if (n == 0) {
			return 0.0;
		}
		double[] copy = new double[n];
		int k = 0;
		for (int i = 0; i < values.length; i++) {
			if (Double.isFinite(values[i])) {
				copy[k++] = values[i];
			}
		}
		Arrays.sort(copy);
		double pos = Math.min(1.0, Math.max(0.0, q)) * (copy.length - 1);
		int lo = (int) pos;
		int hi = Math.min(copy.length - 1, lo + 1);
		double w = pos - lo;
		return copy[lo] * (1.0 - w) + copy[hi] * w;
	}

	static double median(double[] values) {
		if (values == null || values.length == 0) {
			return 0.0;
		}
		int n = 0;
		for (int i = 0; i < values.length; i++) {
			if (Double.isFinite(values[i])) {
				n++;
			}
		}
		if (n == 0) {
			return 0.0;
		}
		double[] copy = new double[n];
		int k = 0;
		for (int i = 0; i < values.length; i++) {
			if (Double.isFinite(values[i])) {
				copy[k++] = values[i];
			}
		}
		Arrays.sort(copy);
		int mid = copy.length / 2;
		if ((copy.length & 1) == 1) {
			return copy[mid];
		}
		return 0.5 * (copy[mid - 1] + copy[mid]);
	}

	static double mad(double[] values) {
		if (values == null || values.length == 0) {
			return 0.0;
		}
		double med = median(values);
		double[] dev = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			dev[i] = Math.abs(values[i] - med);
		}
		return median(dev);
	}
}
