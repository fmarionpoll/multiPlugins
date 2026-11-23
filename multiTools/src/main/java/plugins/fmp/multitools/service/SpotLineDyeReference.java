package plugins.fmp.multitools.service;

import java.util.Arrays;

import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;

/** Learns dye on the X without treating the whole enclosing ROI as dyed. */
public final class SpotLineDyeReference {

	/** Small movements are allowed without including the whole oversized ROI. */
	public static final int MOVEMENT_MARGIN_PX = 2;
	/** Background uses a larger expansion; this mask protects signal samples only. */
	public static final int SIGNAL_MASK_DILATE_PX = 1;
	/** Reject isolated JPEG/noise excursions before they can establish a reference. */
	public static final int MIN_DYE_STATIONS = 3;
	public static final double MIN_DYE_EXCESS = 3.0;

	private final Layout layout;
	private final int[][] centers;
	private final int[][] offsets;
	private final double[][] openingLevels;
	private final boolean[][][] openingDye;
	private final int[] counts;
	private final int[] firstClear;
	private final int[] firstDye;
	private final boolean[][] footprint;
	private final double[] levels;

	public SpotLineDyeReference(Layout layout, int initialBins) {
		this.layout = layout;
		int n = layout.nSpots;
		centers = new int[n][];
		offsets = new int[n][];
		openingLevels = new double[n][Math.max(1, initialBins)];
		openingDye = new boolean[n][Math.max(1, initialBins)][];
		counts = new int[n];
		firstClear = new int[n];
		firstDye = new int[n];
		Arrays.fill(firstClear, -1);
		Arrays.fill(firstDye, -1);
		footprint = new boolean[n][];
		levels = new double[n];
		Arrays.fill(levels, Double.NaN);
		for (int s = 0; s < n; s++) {
			int total = layout.spotRun == null ? layout.spotPix[s].length : layout.spotRun[s].length;
			centers[s] = new int[total];
			offsets[s] = new int[total + 1];
			footprint[s] = new boolean[total];
			for (int i = 0; i < total; i++) {
				centers[s][i] = layout.spotPix[s][offsets[s][i]];
				offsets[s][i + 1] = offsets[s][i] + (layout.spotRun == null ? 1 : layout.spotRun[s][i]);
			}
		}
	}

	public boolean complete(int spot) { return counts[spot] >= openingLevels[spot].length; }
	public int acceptedFrames(int spot) { return counts[spot]; }
	public int firstClearFrame(int spot) { return firstClear[spot]; }
	public int firstDyeFrame(int spot) { return firstDye[spot]; }
	public double dyeLevel(int spot) { return levels[spot]; }
	public int footprintStations(int spot) {
		int n = 0;
		for (boolean active : footprint[spot]) if (active) n++;
		return n;
	}

	/** Only a sufficiently unobscured view may add calibration evidence. */
	public void observe(int spot, int frame, int[] red, int[] green, int[] blue, double floor,
			double noise, boolean[] insect) {
		if (complete(spot) || !usable(red, green, blue, floor) || !visible(spot, insect)) return;
		double[] excess = excesses(spot, red, green, blue, floor, insect);
		if (!enoughValues(spot, excess)) return;
		boolean[] dye = coherentDye(spot, excess, Math.max(noise, MIN_DYE_EXCESS));
		double[] positive = new double[dye.length];
		int n = 0;
		for (int i = 0; i < dye.length; i++) if (dye[i]) positive[n++] = excess[i];
		// Sparse blue evidence is unresolved, not proof of an empty spot.
		if (n == 0) {
			for (double value : excess) if (value > Math.max(noise, MIN_DYE_EXCESS)) return;
		}
		double level = n == 0 ? 0.0 : SpotLineDeficitAnalyzer.quantile(Arrays.copyOf(positive, n), 0.75);
		int index = counts[spot]++;
		openingLevels[spot][index] = level;
		openingDye[spot][index] = dye;
		if (firstClear[spot] < 0) firstClear[spot] = frame;
		if (n > 0 && firstDye[spot] < 0) firstDye[spot] = frame;
		// Empty views never outvote an earlier reliable dye-positive observation.
		double peak = 0.0;
		for (int i = 0; i < counts[spot]; i++) peak = Math.max(peak, openingLevels[spot][i]);
		double[] kept = new double[counts[spot]];
		int k = 0;
		Arrays.fill(footprint[spot], false);
		for (int i = 0; i < counts[spot]; i++) {
			if (openingLevels[spot][i] < 0.5 * peak || openingLevels[spot][i] <= 0) continue;
			kept[k++] = openingLevels[spot][i];
			for (int j = 0; j < dye.length; j++) {
				if (!openingDye[spot][i][j]) continue;
				for (int q = 0; q < dye.length; q++) {
					if (distanceSq(centers[spot][j], centers[spot][q]) <= MOVEMENT_MARGIN_PX * MOVEMENT_MARGIN_PX)
						footprint[spot][q] = true;
				}
			}
		}
		levels[spot] = k == 0 ? 0.0 : SpotLineDeficitAnalyzer.median(Arrays.copyOf(kept, k));
	}

	/** Fixed footprint denominator: masking empty ROI margins cannot raise I/I0. */
	public double measure(int spot, int[] red, int[] green, int[] blue, double floor, double noise, boolean[] insect) {
		if (!usable(red, green, blue, floor) || !Double.isFinite(levels[spot]) || !visible(spot, insect)) return Double.NaN;
		double[] excess = excesses(spot, red, green, blue, floor, insect);
		if (!enoughValues(spot, excess)) return Double.NaN;
		int total = 0, above = 0;
		boolean learned = footprintStations(spot) > 0;
		double cut = Math.max(Math.max(noise, MIN_DYE_EXCESS), SpotLineDeficitAnalyzer.DYE_KEEP_FRACTION * levels[spot]);
		if (!learned) {
			for (double value : excess) if (value > cut) return Double.NaN;
		}
		for (int i = 0; i < excess.length; i++) {
			if (learned && !footprint[spot][i]) continue;
			total++;
			if (excess[i] > cut) above++;
		}
		return total == 0 ? Double.NaN : above / (double) total;
	}

	private boolean visible(int spot, boolean[] insect) {
		boolean learned = footprintStations(spot) > 0;
		int total = 0, masked = 0;
		for (int i = 0; i < centers[spot].length; i++) {
			if (learned && !footprint[spot][i]) continue;
			total++;
			if (blocked(spot, i, insect)) masked++;
		}
		return total > 0 && masked < SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION * total;
	}

	private boolean enoughValues(int spot, double[] values) {
		boolean learned = footprintStations(spot) > 0;
		int total = 0, missing = 0;
		for (int i = 0; i < values.length; i++) {
			if (learned && !footprint[spot][i]) continue;
			total++;
			if (!Double.isFinite(values[i])) missing++;
		}
		return total > 0 && missing < SpotLineDeficitAnalyzer.FLY_OCCUPANCY_FRACTION * total;
	}

	private boolean blocked(int spot, int station, boolean[] insect) {
		if (insect == null) return false;
		for (int j = offsets[spot][station]; j < offsets[spot][station + 1]; j++) {
			int p = layout.spotPix[spot][j];
			if (p >= insect.length || insect[p]) return true;
		}
		return false;
	}

	private double[] excesses(int spot, int[] red, int[] green, int[] blue, double floor, boolean[] insect) {
		double[] values = new double[centers[spot].length];
		for (int i = 0; i < values.length; i++) {
			values[i] = blocked(spot, i, insect) ? Double.NaN : SpotLineDeficitAnalyzer.stationDeficit(
					layout.spotPix[spot], offsets[spot][i], offsets[spot][i + 1] - offsets[spot][i], red, green, blue, insect) - floor;
		}
		return values;
	}

	/** Connected samples on either diagonal support small disks and thin rings. */
	private boolean[] coherentDye(int spot, double[] values, double cut) {
		boolean[] visited = new boolean[values.length], result = new boolean[values.length];
		int[] queue = new int[values.length];
		for (int i = 0; i < values.length; i++) {
			if (visited[i] || !(values[i] > cut)) continue;
			int count = 1;
			queue[0] = i;
			visited[i] = true;
			for (int at = 0; at < count; at++) {
				for (int j = 0; j < values.length; j++) {
					if (!visited[j] && values[j] > cut && distanceSq(centers[spot][queue[at]], centers[spot][j]) <= 9) {
						visited[j] = true;
						queue[count++] = j;
					}
				}
			}
			if (count >= MIN_DYE_STATIONS) for (int j = 0; j < count; j++) result[queue[j]] = true;
		}
		return result;
	}

	private long distanceSq(int a, int b) {
		long dx = a % layout.width - b % layout.width, dy = a / layout.width - b / layout.width;
		return dx * dx + dy * dy;
	}

	private static boolean usable(int[] r, int[] g, int[] b, double floor) {
		return r != null && g != null && b != null && Double.isFinite(floor);
	}
}
