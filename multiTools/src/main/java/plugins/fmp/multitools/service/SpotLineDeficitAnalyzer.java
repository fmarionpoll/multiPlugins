package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Red-deficit integral along a horizontal line through each row of spots.
 * The zero is the floor outside the circles, not a user threshold.
 */
public final class SpotLineDeficitAnalyzer {

	public static final class Params {
		public final double madMultiplier;
		public final int initialBins;
		public final int flankPx;

		public Params(double madMultiplier, int initialBins, int flankPx) {
			this.madMultiplier = madMultiplier;
			this.initialBins = Math.max(1, initialBins);
			this.flankPx = Math.max(0, flankPx);
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
	 * Streams frames in time order. {@link #ratios()} is {@code I(t) / I0} per spot,
	 * with {@code I0} the median integral over the initial window.
	 */
	public static final class SeriesAccumulator {
		private final Params params;
		private final int width;
		private final int height;
		private final int nSpots;
		private final List<Row> rows;
		private final List<double[][]> samplesByRow = new ArrayList<>();
		private int nFrames;

		public SeriesAccumulator(List<SpotGeom> spots, int width, int height, Params params) {
			this.params = params != null ? params : new Params(5.0, 5, 40);
			this.width = width;
			this.height = height;
			this.nSpots = spots != null ? spots.size() : 0;
			this.rows = buildRows(spots, this.width, this.height, this.params.flankPx);
			for (int i = 0; i < rows.size(); i++) {
				samplesByRow.add(new double[0][]);
			}
		}

		public void addFrame(int[] red, int[] green, int[] blue) {
			int rowCount = rows.size();
			for (int r = 0; r < rowCount; r++) {
				Row row = rows.get(r);
				double[] sample = new double[row.xs.length];
				Arrays.fill(sample, Double.NaN);
				if (red != null && green != null && blue != null) {
					for (int i = 0; i < row.xs.length; i++) {
						int pix = row.lineY * width + row.xs[i];
						if (pix < 0 || pix >= red.length || pix >= green.length || pix >= blue.length) {
							continue;
						}
						sample[i] = ((green[pix] + blue[pix]) * 0.5) - red[pix];
					}
				}
				double[][] prev = samplesByRow.get(r);
				double[][] next = Arrays.copyOf(prev, prev.length + 1);
				next[prev.length] = sample;
				samplesByRow.set(r, next);
			}
			nFrames++;
		}

		public int getFrameCount() {
			return nFrames;
		}

		public double[][] ratios() {
			double[][] ratio = new double[nSpots][nFrames];
			for (int s = 0; s < nSpots; s++) {
				Arrays.fill(ratio[s], Double.NaN);
			}
			if (nFrames == 0 || rows.isEmpty()) {
				return ratio;
			}
			int window = Math.min(params.initialBins, nFrames);
			double noise = params.madMultiplier * mad(pooledFlanks(window));
			double[][] integral = new double[nSpots][nFrames];
			for (int t = 0; t < nFrames; t++) {
				for (int r = 0; r < rows.size(); r++) {
					Row row = rows.get(r);
					double[] sample = samplesByRow.get(r)[t];
					double b = median(flankValues(row, sample));
					for (int i = 0; i < row.xs.length; i++) {
						int owner = row.owner[i];
						if (owner < 0 || owner >= nSpots || !Double.isFinite(sample[i])) {
							continue;
						}
						double excess = sample[i] - b;
						if (excess > noise) {
							integral[owner][t] += excess;
						}
					}
				}
			}
			for (int s = 0; s < nSpots; s++) {
				double[] initial = new double[window];
				System.arraycopy(integral[s], 0, initial, 0, window);
				double i0 = median(initial);
				if (!(i0 > 0.0)) {
					continue;
				}
				for (int t = 0; t < nFrames; t++) {
					ratio[s][t] = integral[s][t] / i0;
				}
			}
			return ratio;
		}

		private double[] pooledFlanks(int window) {
			List<Double> values = new ArrayList<>();
			for (int t = 0; t < window; t++) {
				for (int r = 0; r < rows.size(); r++) {
					Row row = rows.get(r);
					double[] sample = samplesByRow.get(r)[t];
					for (int i = 0; i < row.xs.length; i++) {
						if (row.flank[i] && Double.isFinite(sample[i])) {
							values.add(sample[i]);
						}
					}
				}
			}
			double[] out = new double[values.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = values.get(i);
			}
			return out;
		}
	}

	private static final class Row {
		final int lineY;
		final int[] xs;
		final int[] owner;
		final boolean[] flank;

		Row(int lineY, int[] xs, int[] owner, boolean[] flank) {
			this.lineY = lineY;
			this.xs = xs;
			this.owner = owner;
			this.flank = flank;
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

	private SpotLineDeficitAnalyzer() {
	}

	public static double[][] analyze(List<SpotGeom> spots, int width, int height, int[][] red, int[][] green,
			int[][] blue, Params params) {
		SeriesAccumulator acc = new SeriesAccumulator(spots, width, height, params);
		int n = red != null ? red.length : 0;
		for (int t = 0; t < n; t++) {
			int[] r = red[t];
			int[] g = green != null && t < green.length ? green[t] : null;
			int[] b = blue != null && t < blue.length ? blue[t] : null;
			acc.addFrame(r, g, b);
		}
		return acc.ratios();
	}

	static List<Row> buildRows(List<SpotGeom> spots, int width, int height, int flankPx) {
		List<Row> rows = new ArrayList<>();
		if (spots == null || width <= 0 || height <= 0) {
			return rows;
		}
		List<IndexedSpot> usable = new ArrayList<>();
		for (int i = 0; i < spots.size(); i++) {
			SpotGeom g = spots.get(i);
			if (g != null && g.radius > 0) {
				usable.add(new IndexedSpot(i, g));
			}
		}
		if (usable.isEmpty()) {
			return rows;
		}
		usable.sort((a, b) -> Integer.compare(a.geom.centerY, b.geom.centerY));
		double[] diameters = new double[usable.size()];
		for (int i = 0; i < usable.size(); i++) {
			diameters[i] = 2.0 * usable.get(i).geom.radius;
		}
		double typicalDiameter = median(diameters);
		if (typicalDiameter < 1.0) {
			typicalDiameter = 1.0;
		}
		List<List<IndexedSpot>> groups = new ArrayList<>();
		List<IndexedSpot> current = new ArrayList<>();
		current.add(usable.get(0));
		for (int i = 1; i < usable.size(); i++) {
			IndexedSpot next = usable.get(i);
			double gap = next.geom.centerY - current.get(current.size() - 1).geom.centerY;
			if (gap > typicalDiameter) {
				groups.add(current);
				current = new ArrayList<>();
			}
			current.add(next);
		}
		groups.add(current);
		for (List<IndexedSpot> group : groups) {
			Row row = rowFromGroup(group, width, height, flankPx);
			if (row != null) {
				rows.add(row);
			}
		}
		return rows;
	}

	private static Row rowFromGroup(List<IndexedSpot> group, int width, int height, int flankPx) {
		group.sort((a, b) -> Integer.compare(a.geom.centerX, b.geom.centerX));
		double[] ys = new double[group.size()];
		int minEdge = Integer.MAX_VALUE;
		int maxEdge = Integer.MIN_VALUE;
		for (int i = 0; i < group.size(); i++) {
			SpotGeom g = group.get(i).geom;
			ys[i] = g.centerY;
			minEdge = Math.min(minEdge, g.centerX - g.radius);
			maxEdge = Math.max(maxEdge, g.centerX + g.radius);
		}
		int lineY = (int) Math.round(median(ys));
		if (lineY < 0) {
			lineY = 0;
		}
		if (lineY >= height) {
			lineY = height - 1;
		}
		int x0 = Math.max(0, minEdge - flankPx);
		int x1 = Math.min(width - 1, maxEdge + flankPx);
		if (x1 < x0) {
			return null;
		}
		int n = x1 - x0 + 1;
		int[] xs = new int[n];
		int[] owner = new int[n];
		boolean[] flank = new boolean[n];
		for (int i = 0; i < n; i++) {
			int x = x0 + i;
			xs[i] = x;
			owner[i] = nearestOwner(group, x);
			flank[i] = !insideAnyCircle(group, x, lineY);
		}
		return new Row(lineY, xs, owner, flank);
	}

	private static int nearestOwner(List<IndexedSpot> group, int x) {
		int bestIndex = group.get(0).index;
		int bestDist = Math.abs(x - group.get(0).geom.centerX);
		for (int i = 1; i < group.size(); i++) {
			int dist = Math.abs(x - group.get(i).geom.centerX);
			if (dist < bestDist) {
				bestDist = dist;
				bestIndex = group.get(i).index;
			}
		}
		return bestIndex;
	}

	private static boolean insideAnyCircle(List<IndexedSpot> group, int x, int y) {
		for (IndexedSpot s : group) {
			long dx = (long) x - s.geom.centerX;
			long dy = (long) y - s.geom.centerY;
			long radius = s.geom.radius;
			if (dx * dx + dy * dy <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	private static double[] flankValues(Row row, double[] sample) {
		int n = 0;
		for (int i = 0; i < row.xs.length; i++) {
			if (row.flank[i] && Double.isFinite(sample[i])) {
				n++;
			}
		}
		double[] out = new double[n];
		int k = 0;
		for (int i = 0; i < row.xs.length; i++) {
			if (row.flank[i] && Double.isFinite(sample[i])) {
				out[k++] = sample[i];
			}
		}
		return out;
	}

	static double median(double[] values) {
		if (values == null || values.length == 0) {
			return 0.0;
		}
		double[] copy = Arrays.copyOf(values, values.length);
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
