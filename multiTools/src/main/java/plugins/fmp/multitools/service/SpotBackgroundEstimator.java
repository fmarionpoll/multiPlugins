package plugins.fmp.multitools.service;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;

/** Background-only extension of X-line sampling; never changes the signal mask. */
public final class SpotBackgroundEstimator {

	public static final int PATCH_SIZE = 5;
	public static final int PATCH_STEP = 10;
	public static final int ROI_CLEARANCE = 4;
	public static final int MIN_PATCHES = 3;
	public static final int MAX_PATCHES = 5;
	public static final double MIN_CLEAN_BRANCH_FRACTION = 0.9;

	public enum Source { BRANCHES, PATCHES, REFERENCE, UNKNOWN }

	public static final class Sample {
		public final double[] floors;
		public final Source[] sources;
		public final int[] cleanBranches;
		public final int[] totalBranches;
		public final int[][] selectedPatches;
		public final int cleanPatches;

		private Sample(int spots, int cleanPatches) {
			floors = new double[spots];
			Arrays.fill(floors, Double.NaN);
			sources = new Source[spots];
			Arrays.fill(sources, Source.UNKNOWN);
			cleanBranches = new int[spots];
			totalBranches = new int[spots];
			selectedPatches = new int[spots][];
			for (int s = 0; s < spots; s++) selectedPatches[s] = new int[0];
			this.cleanPatches = cleanPatches;
		}

		public double[] withReference(double[] reference) {
			double[] resolved = floors.clone();
			for (int s = 0; s < resolved.length; s++) {
				if (!Double.isFinite(resolved[s]) && reference != null && s < reference.length)
					resolved[s] = reference[s];
			}
			return resolved;
		}

		public Source sourceWithReference(int spot, double[] reference) {
			return sources[spot] == Source.UNKNOWN && reference != null && spot < reference.length
					&& Double.isFinite(reference[spot]) ? Source.REFERENCE : sources[spot];
		}
	}

	private final Layout layout;
	private final List<Point> centers = new ArrayList<>();
	private final List<int[]> patches = new ArrayList<>();
	private final int[][] nearestPatches;

	/**
	 * Restrict every patch pixel to both the convex hull of this cage's spot
	 * centers and the cage ROI, when available. The hull avoids walls outside the
	 * array; the ROI additionally handles irregular cage boundaries. Missing or
	 * collinear geometry disables patches rather than searching another cage.
	 */
	public SpotBackgroundEstimator(Layout layout, List<SpotGeom> spots, int height,
			BiPredicate<Integer, Integer> insideCage) {
		this.layout = layout;
		List<Point> points = new ArrayList<>();
		for (SpotGeom spot : spots) {
			if (spot != null && spot.radius > 0) points.add(new Point(spot.centerX, spot.centerY));
		}
		Polygon hull = hull(points);
		java.awt.Rectangle bounds = hull.getBounds();
		int half = PATCH_SIZE / 2;
		for (int y = Math.max(half, bounds.y + half); y < Math.min(height - half, bounds.y + bounds.height); y += PATCH_STEP) {
			for (int x = Math.max(half, bounds.x + half); x < Math.min(layout.width - half, bounds.x + bounds.width); x += PATCH_STEP) {
				int[] pixels = new int[PATCH_SIZE * PATCH_SIZE];
				int n = 0;
				boolean valid = true;
				for (int dy = -half; dy <= half && valid; dy++) {
					for (int dx = -half; dx <= half; dx++) {
						int px = x + dx, py = y + dy;
						if (!hull.contains(px, py) || (insideCage != null && !insideCage.test(px, py))
								|| nearSpot(spots, px, py)) {
							valid = false;
							break;
						}
						pixels[n++] = py * layout.width + px;
					}
				}
				if (valid) {
					centers.add(new Point(x, y));
					patches.add(pixels);
				}
			}
		}
		nearestPatches = new int[layout.nSpots][];
		for (int s = 0; s < layout.nSpots; s++) {
			SpotGeom spot = spots.get(s);
			if (spot == null || spot.radius <= 0) {
				nearestPatches[s] = new int[0];
				continue;
			}
			Integer[] order = new Integer[patches.size()];
			for (int i = 0; i < order.length; i++) order[i] = i;
			Arrays.sort(order, Comparator.comparingDouble(i -> centers.get(i).distanceSq(spot.centerX, spot.centerY)));
			nearestPatches[s] = Arrays.stream(order).mapToInt(Integer::intValue).toArray();
		}
	}

	public int patchCount() { return patches.size(); }
	public Point patchCenter(int index) { return new Point(centers.get(index)); }

	public Sample sample(int[] red, int[] green, int[] blue, boolean[] insect, double[] flanks) {
		double[] values = new double[patches.size()];
		Arrays.fill(values, Double.NaN);
		int clean = 0;
		for (int i = 0; i < patches.size(); i++) {
			int[] pixels = patches.get(i);
			double[] deficits = new double[pixels.length];
			boolean valid = red != null && green != null && blue != null;
			for (int j = 0; j < pixels.length && valid; j++) {
				int p = pixels[j];
				// Even one masked leg pixel excludes the whole small patch.
				valid = p < red.length && p < green.length && p < blue.length
						&& (insect == null || (p < insect.length && !insect[p]));
				if (valid) deficits[j] = (green[p] + blue[p]) * 0.5 - red[p];
			}
			if (valid) {
				values[i] = SpotLineDeficitAnalyzer.median(deficits);
				clean++;
			}
		}
		Sample result = new Sample(layout.nSpots, clean);
		double[] branchFloors = SpotLineDeficitAnalyzer.spotFloors(layout, flanks);
		int offset = 0;
		for (int s = 0; s < layout.nSpots; s++) {
			int total = layout.flankRun == null ? layout.flankPix[s].length : layout.flankRun[s].length;
			result.totalBranches[s] = total;
			for (int i = 0; i < total; i++) {
				if (flanks != null && offset + i < flanks.length && Double.isFinite(flanks[offset + i]))
					result.cleanBranches[s]++;
			}
			offset += total;
			if (total > 0 && result.cleanBranches[s] >= MIN_CLEAN_BRANCH_FRACTION * total && Double.isFinite(branchFloors[s])) {
				result.floors[s] = branchFloors[s];
				result.sources[s] = Source.BRANCHES;
				continue;
			}
			int[] selected = new int[MAX_PATCHES];
			double[] local = new double[MAX_PATCHES];
			int n = 0;
			for (int i : nearestPatches[s]) {
				if (!Double.isFinite(values[i])) continue;
				selected[n] = i;
				local[n++] = values[i];
				if (n == MAX_PATCHES) break;
			}
			if (n >= MIN_PATCHES) {
				result.floors[s] = SpotLineDeficitAnalyzer.median(Arrays.copyOf(local, n));
				result.sources[s] = Source.PATCHES;
				result.selectedPatches[s] = Arrays.copyOf(selected, n);
			}
		}
		return result;
	}

	private static boolean nearSpot(List<SpotGeom> spots, int x, int y) {
		for (SpotGeom spot : spots) {
			if (spot == null || spot.radius <= 0) continue;
			long dx = x - spot.centerX, dy = y - spot.centerY;
			long r = (long) spot.radius + ROI_CLEARANCE;
			if (dx * dx + dy * dy <= r * r) return true;
		}
		return false;
	}

	private static Polygon hull(List<Point> points) {
		points.sort(Comparator.comparingInt((Point p) -> p.x).thenComparingInt(p -> p.y));
		List<Point> edge = new ArrayList<>();
		for (Point p : points) {
			while (edge.size() >= 2 && turn(edge.get(edge.size() - 2), edge.get(edge.size() - 1), p) <= 0)
				edge.remove(edge.size() - 1);
			edge.add(p);
		}
		int lower = edge.size();
		for (int i = points.size() - 2; i >= 0; i--) {
			Point p = points.get(i);
			while (edge.size() > lower && turn(edge.get(edge.size() - 2), edge.get(edge.size() - 1), p) <= 0)
				edge.remove(edge.size() - 1);
			edge.add(p);
		}
		Polygon result = new Polygon();
		for (int i = 0; i + 1 < edge.size(); i++) result.addPoint(edge.get(i).x, edge.get(i).y);
		return result;
	}

	private static long turn(Point a, Point b, Point c) {
		return (long) (b.x - a.x) * (c.y - a.y) - (long) (b.y - a.y) * (c.x - a.x);
	}
}
