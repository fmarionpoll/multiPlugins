package plugins.fmp.multitools.tools.ROI2D;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import icy.roi.ROI2D;

/**
 * Serpentine visit order for spot grids: horizontal bands, alternating LTR / RTL.
 * With grid metadata, all spot rows in one cage block (e.g. cages 0–2) are visited before
 * the next block (e.g. cages 3–5). Otherwise falls back to Y clustering.
 */
public final class RoiSerpentineOrdering {

	private static final double Y_FLOOR = 4.0;

	private RoiSerpentineOrdering() {
	}

	public static ArrayList<ROI2D> orderSerpentine(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn,
			Function<ROI2D, int[]> rowColFn) {
		return orderSerpentine(rois, anchorFn, rowColFn, null);
	}

	public static ArrayList<ROI2D> orderSerpentine(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn,
			Function<ROI2D, int[]> rowColFn, Function<ROI2D, Integer> cageIdFn) {
		if (rois == null || rois.isEmpty()) {
			return new ArrayList<>();
		}
		if (rois.size() == 1) {
			ArrayList<ROI2D> one = new ArrayList<>(1);
			one.add(rois.get(0));
			return one;
		}

		List<SpotNode> nodes = buildNodes(rois, anchorFn, rowColFn, cageIdFn);
		List<RowBand> bands = buildRowBands(nodes);
		ArrayList<ROI2D> out = new ArrayList<>(rois.size());
		appendBandsByNextLevelChain(bands, out);
		return out;
	}

	private static List<SpotNode> buildNodes(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn,
			Function<ROI2D, int[]> rowColFn, Function<ROI2D, Integer> cageIdFn) {
		List<SpotNode> nodes = new ArrayList<>(rois.size());
		for (int i = 0; i < rois.size(); i++) {
			ROI2D roi = rois.get(i);
			Point2D p = anchorFn.apply(roi);
			if (p == null) {
				p = new Point2D.Double(0, 0);
			}
			int cageRow = -1;
			int cageColumn = -1;
			if (rowColFn != null) {
				int[] rc = rowColFn.apply(roi);
				if (rc != null && rc.length >= 2) {
					if (rc[0] >= 0) {
						cageRow = rc[0];
					}
					if (rc[1] >= 0) {
						cageColumn = rc[1];
					}
				}
			}
			int cageId = -1;
			if (cageIdFn != null) {
				Integer id = cageIdFn.apply(roi);
				if (id != null && id >= 0) {
					cageId = id;
				}
			}
			nodes.add(new SpotNode(roi, p, cageRow, cageColumn, cageId, i));
		}
		return nodes;
	}

	private static List<RowBand> buildRowBands(List<SpotNode> nodes) {
		if (canUseGridMetadata(nodes)) {
			return bandsFromGridMetadata(nodes);
		}
		int labeledRow = 0;
		for (SpotNode n : nodes) {
			if (n.cageRow >= 0) {
				labeledRow++;
			}
		}
		if (labeledRow >= nodes.size() / 2) {
			return bandsFromCageRowAndY(nodes);
		}
		return bandsFromScreenY(nodes);
	}

	private static boolean canUseGridMetadata(List<SpotNode> nodes) {
		for (SpotNode n : nodes) {
			if (n.cageRow < 0 || n.cageColumn < 0 || n.cageId < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Grid path: band = (cageRow, cage layout row on screen). Within band: cages left
	 * to right, columns 0..n inside each cage.
	 */
	private static List<RowBand> bandsFromGridMetadata(List<SpotNode> nodes) {
		Map<Integer, CageLayout> layoutByCageId = buildCageLayoutMap(nodes);
		Map<Long, List<SpotNode>> buckets = new LinkedHashMap<>();

		for (SpotNode n : nodes) {
			CageLayout layout = layoutByCageId.get(n.cageId);
			int layoutRow = layout != null ? layout.layoutRowIndex : 0;
			int slot = layout != null ? layout.slotInLayoutRow : 0;
			long key = bandKey(n.cageRow, layoutRow);
			buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
			n.cageSlotInLayoutRow = slot;
		}

		List<Integer> cageRows = new ArrayList<>();
		for (SpotNode n : nodes) {
			if (!cageRows.contains(n.cageRow)) {
				cageRows.add(n.cageRow);
			}
		}
		Collections.sort(cageRows);

		int layoutRowCount = 1;
		for (CageLayout cl : layoutByCageId.values()) {
			layoutRowCount = Math.max(layoutRowCount, cl.layoutRowIndex + 1);
		}

		List<RowBand> bands = new ArrayList<>();
		for (int cageRow : cageRows) {
			for (int layoutRow = 0; layoutRow < layoutRowCount; layoutRow++) {
				List<SpotNode> list = buckets.get(bandKey(cageRow, layoutRow));
				if (list == null || list.isEmpty()) {
					continue;
				}
				bands.add(new RowBand(list, bandKey(cageRow, layoutRow), compareInRowByCageSlotColumnThenX()));
			}
		}
		return bands;
	}

	private static Map<Integer, CageLayout> buildCageLayoutMap(List<SpotNode> nodes) {
		Map<Integer, List<SpotNode>> byCage = new HashMap<>();
		for (SpotNode n : nodes) {
			byCage.computeIfAbsent(n.cageId, k -> new ArrayList<>()).add(n);
		}

		List<CageCenter> centers = new ArrayList<>();
		for (Map.Entry<Integer, List<SpotNode>> e : byCage.entrySet()) {
			double sx = 0;
			double sy = 0;
			for (SpotNode s : e.getValue()) {
				sx += s.x;
				sy += s.y;
			}
			int n = e.getValue().size();
			centers.add(new CageCenter(e.getKey(), sx / n, sy / n));
		}

		Map<Integer, CageLayout> fromIds = tryLayoutFromConsecutiveCageIds(centers);
		if (fromIds != null) {
			return fromIds;
		}

		List<List<Integer>> layoutRows = clusterCageIdsIntoLayoutRows(centers);

		Map<Integer, CageLayout> map = new HashMap<>();
		for (int row = 0; row < layoutRows.size(); row++) {
			List<Integer> cageIds = layoutRows.get(row);
			for (int slot = 0; slot < cageIds.size(); slot++) {
				map.put(cageIds.get(slot), new CageLayout(row, slot));
			}
		}
		return map;
	}

	/**
	 * When cage IDs are consecutive and form a full grid (e.g. 0–5 in 2×3), use ID
	 * arithmetic so layout rows match plate rows even if spot Y is noisy.
	 */
	private static Map<Integer, CageLayout> tryLayoutFromConsecutiveCageIds(List<CageCenter> centers) {
		if (centers.size() < 4) {
			return null;
		}
		List<Integer> ids = new ArrayList<>();
		for (CageCenter c : centers) {
			ids.add(c.cageId);
		}
		Collections.sort(ids);
		int minId = ids.get(0);
		for (int i = 1; i < ids.size(); i++) {
			if (ids.get(i) != ids.get(i - 1) + 1) {
				return null;
			}
		}

		List<List<Integer>> yRows = clusterCageIdsIntoLayoutRows(centers);
		if (yRows.isEmpty()) {
			return null;
		}
		int layoutRowCount = yRows.size();
		int colsPerRow = centers.size() / layoutRowCount;
		if (colsPerRow < 2 || colsPerRow * layoutRowCount != centers.size()) {
			return null;
		}

		Map<Integer, CageLayout> map = new HashMap<>();
		for (CageCenter c : centers) {
			int layoutRow = (c.cageId - minId) / colsPerRow;
			int slot = (c.cageId - minId) % colsPerRow;
			if (layoutRow < 0 || layoutRow >= layoutRowCount) {
				return null;
			}
			map.put(c.cageId, new CageLayout(layoutRow, slot));
		}

		if (layoutRowCount >= 2) {
			double y0 = meanLayoutRowY(map, centers, 0);
			double y1 = meanLayoutRowY(map, centers, 1);
			if (y0 > y1) {
				Map<Integer, CageLayout> flipped = new HashMap<>();
				for (CageCenter c : centers) {
					CageLayout cl = map.get(c.cageId);
					flipped.put(c.cageId,
							new CageLayout(layoutRowCount - 1 - cl.layoutRowIndex, cl.slotInLayoutRow));
				}
				return flipped;
			}
		}
		return map;
	}

	private static double meanLayoutRowY(Map<Integer, CageLayout> map, List<CageCenter> centers, int layoutRow) {
		double s = 0;
		int n = 0;
		for (CageCenter c : centers) {
			CageLayout cl = map.get(c.cageId);
			if (cl != null && cl.layoutRowIndex == layoutRow) {
				s += c.y;
				n++;
			}
		}
		return n == 0 ? 0 : s / n;
	}

	private static List<List<Integer>> clusterCageIdsIntoLayoutRows(List<CageCenter> centers) {
		if (centers.isEmpty()) {
			return new ArrayList<>();
		}
		if (centers.size() == 1) {
			List<List<Integer>> single = new ArrayList<>();
			single.add(Collections.singletonList(centers.get(0).cageId));
			return single;
		}

		Collections.sort(centers, Comparator.comparingDouble((CageCenter c) -> c.y)
				.thenComparingDouble(c -> c.x));

		int splitAt = -1;
		double maxGap = 0;
		for (int i = 1; i < centers.size(); i++) {
			double gap = centers.get(i).y - centers.get(i - 1).y;
			if (gap > maxGap) {
				maxGap = gap;
				splitAt = i;
			}
		}

		List<Double> gaps = new ArrayList<>();
		for (int i = 1; i < centers.size(); i++) {
			double g = centers.get(i).y - centers.get(i - 1).y;
			if (g > 0) {
				gaps.add(g);
			}
		}
		Collections.sort(gaps);
		double medianGap = gaps.isEmpty() ? Y_FLOOR : gaps.get(gaps.size() / 2);
		boolean split = splitAt > 0 && splitAt < centers.size() && maxGap >= Math.max(Y_FLOOR * 3, medianGap * 4);

		List<List<Integer>> rows = new ArrayList<>();
		if (!split) {
			List<Integer> one = new ArrayList<>();
			for (CageCenter c : centers) {
				one.add(c.cageId);
			}
			one.sort((idA, idB) -> Double.compare(cageCenterX(centers, idA), cageCenterX(centers, idB)));
			rows.add(one);
			return rows;
		}

		List<Integer> upper = new ArrayList<>();
		List<Integer> lower = new ArrayList<>();
		for (int i = 0; i < centers.size(); i++) {
			if (i < splitAt) {
				upper.add(centers.get(i).cageId);
			} else {
				lower.add(centers.get(i).cageId);
			}
		}
		upper.sort((idA, idB) -> Double.compare(cageCenterX(centers, idA), cageCenterX(centers, idB)));
		lower.sort((idA, idB) -> Double.compare(cageCenterX(centers, idA), cageCenterX(centers, idB)));
		rows.add(upper);
		rows.add(lower);
		return rows;
	}

	private static double cageCenterX(List<CageCenter> centers, int cageId) {
		for (CageCenter c : centers) {
			if (c.cageId == cageId) {
				return c.x;
			}
		}
		return 0;
	}

	private static List<RowBand> bandsFromCageRowAndY(List<SpotNode> nodes) {
		Map<Long, List<SpotNode>> buckets = new LinkedHashMap<>();
		Map<Integer, List<SpotNode>> byCageRow = new LinkedHashMap<>();
		for (SpotNode n : nodes) {
			if (n.cageRow >= 0) {
				byCageRow.computeIfAbsent(n.cageRow, k -> new ArrayList<>()).add(n);
			}
		}

		List<Integer> rowKeys = new ArrayList<>(byCageRow.keySet());
		Collections.sort(rowKeys);

		for (int cageRow : rowKeys) {
			List<SpotNode> group = byCageRow.get(cageRow);
			double[] tiers = layoutTiersFromY(group);
			for (SpotNode n : group) {
				int tier = 0;
				if (tiers[0] >= 2) {
					tier = n.y <= tiers[2] ? 0 : 1;
				}
				buckets.computeIfAbsent(bandKey(cageRow, tier), k -> new ArrayList<>()).add(n);
			}
		}

		List<RowBand> bands = new ArrayList<>();
		for (int cageRow : rowKeys) {
			double[] tiers = layoutTiersFromY(byCageRow.get(cageRow));
			int tierCount = tiers[0] >= 2 ? 2 : 1;
			for (int tier = 0; tier < tierCount; tier++) {
				List<SpotNode> list = buckets.get(bandKey(cageRow, tier));
				if (list != null && !list.isEmpty()) {
					bands.add(new RowBand(list, bandKey(cageRow, tier), compareInRowByXThenColumn()));
				}
			}
		}

		List<SpotNode> unlabeled = new ArrayList<>();
		for (SpotNode n : nodes) {
			if (n.cageRow < 0) {
				unlabeled.add(n);
			}
		}
		if (!unlabeled.isEmpty()) {
			bands.addAll(bandsFromScreenY(unlabeled));
		}
		return bands;
	}

	private static double[] layoutTiersFromY(List<SpotNode> group) {
		if (group.size() < 6) {
			return new double[] { 1, 0, -1 };
		}
		List<Double> ys = new ArrayList<>(group.size());
		for (SpotNode n : group) {
			ys.add(n.y);
		}
		Collections.sort(ys);
		double median = ys.get(ys.size() / 2);
		int lowN = 0;
		int highN = 0;
		double lowMean = 0;
		double highMean = 0;
		for (SpotNode n : group) {
			if (n.y <= median) {
				lowMean += n.y;
				lowN++;
			} else {
				highMean += n.y;
				highN++;
			}
		}
		if (lowN < 3 || highN < 3) {
			return new double[] { 1, 0, -1 };
		}
		lowMean /= lowN;
		highMean /= highN;
		if (highMean - lowMean < layoutSeparationThreshold(ys)) {
			return new double[] { 1, 0, -1 };
		}
		return new double[] { 2, 0, median };
	}

	private static List<RowBand> bandsFromScreenY(List<SpotNode> nodes) {
		List<SpotNode> sorted = new ArrayList<>(nodes);
		sorted.sort(Comparator.comparingDouble(n -> n.y));

		double yTol = withinRowYTolerance(sorted);
		List<RowBand> bands = new ArrayList<>();

		for (SpotNode n : sorted) {
			RowBand target = null;
			double best = Double.POSITIVE_INFINITY;
			for (RowBand band : bands) {
				double d = Math.abs(n.y - band.meanY);
				if (d <= yTol && d < best) {
					best = d;
					target = band;
				}
			}
			if (target == null) {
				target = new RowBand(new ArrayList<>(), (long) (bands.size() + 1) << 32,
						compareInRowByXThenColumn());
				bands.add(target);
			}
			target.nodes.add(n);
			target.recalcMeanY();
		}
		return bands;
	}

	private static long bandKey(int cageRow, int layoutRow) {
		return ((long) cageRow << 4) | layoutRow;
	}

	private static double withinRowYTolerance(List<SpotNode> sortedByY) {
		if (sortedByY.size() < 2) {
			return Y_FLOOR;
		}
		List<Double> gaps = new ArrayList<>();
		for (int i = 1; i < sortedByY.size(); i++) {
			double g = sortedByY.get(i).y - sortedByY.get(i - 1).y;
			if (g > 0) {
				gaps.add(g);
			}
		}
		if (gaps.isEmpty()) {
			return Y_FLOOR;
		}
		Collections.sort(gaps);
		int p25 = gaps.size() / 4;
		return Math.max(Y_FLOOR, gaps.get(Math.min(p25, gaps.size() - 1)) * 1.75);
	}

	private static double layoutSeparationThreshold(List<Double> sortedY) {
		if (sortedY.size() < 2) {
			return Double.POSITIVE_INFINITY;
		}
		double span = sortedY.get(sortedY.size() - 1) - sortedY.get(0);
		if (span < Y_FLOOR * 2) {
			return Double.POSITIVE_INFINITY;
		}
		List<Double> gaps = new ArrayList<>();
		for (int i = 1; i < sortedY.size(); i++) {
			double g = sortedY.get(i) - sortedY.get(i - 1);
			if (g > 0) {
				gaps.add(g);
			}
		}
		if (gaps.isEmpty()) {
			return span * 0.35;
		}
		Collections.sort(gaps);
		double median = gaps.get(gaps.size() / 2);
		return Math.max(Y_FLOOR * 3, Math.min(span * 0.28, median * 5));
	}

	/**
	 * Visits bands one after another: each step picks the next band at the following level
	 * (grid metadata: next {@code cageRow} in the same cage block, then row 0 of the next block), or
	 * the nearest row below on screen when metadata keys are absent.
	 */
	private static void appendBandsByNextLevelChain(List<RowBand> bands, ArrayList<ROI2D> out) {
		if (bands.isEmpty()) {
			return;
		}
		if (!hasAnyGridBandKey(bands)) {
			List<RowBand> byY = new ArrayList<>(bands);
			byY.sort((a, b) -> Double.compare(a.meanY, b.meanY));
			double lastX = Double.NaN;
			double lastY = Double.NaN;
			for (RowBand band : byY) {
				List<SpotNode> row = new ArrayList<>(band.nodes);
				row.sort(band.inRowComparator);
				orientRowFromLastPoint(row, lastX, lastY);
				for (SpotNode n : row) {
					out.add(n.roi);
					lastX = n.x;
					lastY = n.y;
				}
			}
			return;
		}
		boolean[] used = new boolean[bands.size()];
		double medianRowGap = estimateBandRowSpacing(bands);
		double yTol = Math.max(Y_FLOOR, medianRowGap * 0.35);

		int current = findStartBandIndex(bands);
		double lastX = Double.NaN;
		double lastY = Double.NaN;
		double currentMeanY = bands.get(current).meanY;

		for (int step = 0; step < bands.size(); step++) {
			RowBand band = bands.get(current);
			used[current] = true;
			List<SpotNode> row = new ArrayList<>(band.nodes);
			row.sort(band.inRowComparator);
			orientRowFromLastPoint(row, lastX, lastY);
			for (SpotNode n : row) {
				out.add(n.roi);
				lastX = n.x;
				lastY = n.y;
			}
			currentMeanY = band.meanY;

			if (step + 1 >= bands.size()) {
				break;
			}
			int next = findNextBandIndex(bands, used, band.sortKey, currentMeanY, lastX, lastY, yTol, medianRowGap);
			if (next < 0) {
				next = findFirstUnused(bands, used);
			}
			current = next;
		}
	}

	private static boolean hasAnyGridBandKey(List<RowBand> bands) {
		for (RowBand band : bands) {
			if (band.sortKey < (1L << 32)) {
				return true;
			}
		}
		return false;
	}

	private static int findStartBandIndex(List<RowBand> bands) {
		int best = 0;
		long bestKey = Long.MAX_VALUE;
		boolean hasGridKey = false;
		for (int i = 0; i < bands.size(); i++) {
			long key = bands.get(i).sortKey;
			if (key < (1L << 32) && key < bestKey) {
				bestKey = key;
				best = i;
				hasGridKey = true;
			}
		}
		if (hasGridKey) {
			return best;
		}
		for (int i = 1; i < bands.size(); i++) {
			if (bands.get(i).meanY < bands.get(best).meanY) {
				best = i;
			}
		}
		return best;
	}

	private static int findNextBandIndex(List<RowBand> bands, boolean[] used, long currentKey, double currentMeanY,
			double lastX, double lastY, double yTol, double medianRowGap) {
		if (currentKey < (1L << 32)) {
			int cageRow = cageRowFromBandKey(currentKey);
			int layoutRow = layoutRowFromBandKey(currentKey);
			int maxCageRow = maxCageRowFromBands(bands);
			int maxLayoutRow = maxLayoutRowFromBands(bands);
			for (int r = cageRow + 1; r <= maxCageRow; r++) {
				int next = findBandIndexByKey(bands, used, bandKey(r, layoutRow));
				if (next >= 0) {
					return next;
				}
			}
			for (int lr = layoutRow + 1; lr <= maxLayoutRow; lr++) {
				for (int r = 0; r <= maxCageRow; r++) {
					int next = findBandIndexByKey(bands, used, bandKey(r, lr));
					if (next >= 0) {
						return next;
					}
				}
			}
		}
		return findNextBandByScreenRowBelow(bands, used, currentMeanY, lastX, lastY, yTol, medianRowGap);
	}

	private static int findNextBandByScreenRowBelow(List<RowBand> bands, boolean[] used, double currentMeanY,
			double lastX, double lastY, double yTol, double medianRowGap) {
		int best = -1;
		double bestDy = Double.POSITIVE_INFINITY;
		double bestConn = Double.POSITIVE_INFINITY;
		for (int j = 0; j < bands.size(); j++) {
			if (used[j]) {
				continue;
			}
			double dy = bands.get(j).meanY - currentMeanY;
			if (dy < yTol * 0.5) {
				continue;
			}
			double conn = minConnectionDistanceSq(lastX, lastY, bands.get(j));
			if (dy < bestDy - 0.5 || (Math.abs(dy - bestDy) <= 0.5 && conn < bestConn)) {
				best = j;
				bestDy = dy;
				bestConn = conn;
			}
		}
		if (best >= 0) {
			return best;
		}
		return findNearestUnusedBand(bands, used, lastX, lastY);
	}

	private static int findBandIndexByKey(List<RowBand> bands, boolean[] used, long key) {
		for (int i = 0; i < bands.size(); i++) {
			if (!used[i] && bands.get(i).sortKey == key) {
				return i;
			}
		}
		return -1;
	}

	private static int maxCageRowFromBands(List<RowBand> bands) {
		int max = 0;
		for (RowBand band : bands) {
			if (band.sortKey < (1L << 32)) {
				max = Math.max(max, cageRowFromBandKey(band.sortKey));
			}
		}
		return max;
	}

	private static int maxLayoutRowFromBands(List<RowBand> bands) {
		int max = 0;
		for (RowBand band : bands) {
			if (band.sortKey < (1L << 32)) {
				max = Math.max(max, layoutRowFromBandKey(band.sortKey));
			}
		}
		return max;
	}

	private static int cageRowFromBandKey(long key) {
		return (int) (key >> 4);
	}

	private static int layoutRowFromBandKey(long key) {
		return (int) (key & 0xF);
	}

	private static int findFirstUnused(List<RowBand> bands, boolean[] used) {
		for (int i = 0; i < bands.size(); i++) {
			if (!used[i]) {
				return i;
			}
		}
		return 0;
	}

	private static int findNearestUnusedBand(List<RowBand> bands, boolean[] used, double lastX, double lastY) {
		int best = -1;
		double bestD = Double.POSITIVE_INFINITY;
		for (int j = 0; j < bands.size(); j++) {
			if (used[j]) {
				continue;
			}
			double d = minConnectionDistanceSq(lastX, lastY, bands.get(j));
			if (d < bestD) {
				bestD = d;
				best = j;
			}
		}
		return best;
	}

	private static double minConnectionDistanceSq(double lastX, double lastY, RowBand band) {
		if (band.nodes.isEmpty()) {
			return Double.POSITIVE_INFINITY;
		}
		if (Double.isNaN(lastX)) {
			return 0;
		}
		List<SpotNode> row = new ArrayList<>(band.nodes);
		SpotNode left = leftmost(row);
		SpotNode right = rightmost(row);
		double dLeft = distanceSq(lastX, lastY, left.x, left.y);
		double dRight = distanceSq(lastX, lastY, right.x, right.y);
		return Math.min(dLeft, dRight);
	}

	private static double estimateBandRowSpacing(List<RowBand> bands) {
		if (bands.size() < 2) {
			return Y_FLOOR;
		}
		List<Double> meanYs = new ArrayList<>();
		for (RowBand band : bands) {
			meanYs.add(band.meanY);
		}
		Collections.sort(meanYs);
		List<Double> gaps = new ArrayList<>();
		for (int i = 1; i < meanYs.size(); i++) {
			double g = meanYs.get(i) - meanYs.get(i - 1);
			if (g > Y_FLOOR * 0.5) {
				gaps.add(g);
			}
		}
		if (gaps.isEmpty()) {
			return Y_FLOOR;
		}
		Collections.sort(gaps);
		return gaps.get(0);
	}

	private static void orientRowFromLastPoint(List<SpotNode> row, double lastX, double lastY) {
		if (row.size() <= 1 || Double.isNaN(lastX)) {
			return;
		}
		SpotNode geomLeft = leftmost(row);
		SpotNode geomRight = rightmost(row);
		double dLeft = distanceSq(lastX, lastY, geomLeft.x, geomLeft.y);
		double dRight = distanceSq(lastX, lastY, geomRight.x, geomRight.y);
		if (dRight < dLeft) {
			Collections.reverse(row);
		}
	}

	private static SpotNode leftmost(List<SpotNode> row) {
		SpotNode best = row.get(0);
		for (int i = 1; i < row.size(); i++) {
			SpotNode n = row.get(i);
			if (n.x < best.x || (n.x == best.x && n.tieIndex < best.tieIndex)) {
				best = n;
			}
		}
		return best;
	}

	private static SpotNode rightmost(List<SpotNode> row) {
		SpotNode best = row.get(0);
		for (int i = 1; i < row.size(); i++) {
			SpotNode n = row.get(i);
			if (n.x > best.x || (n.x == best.x && n.tieIndex < best.tieIndex)) {
				best = n;
			}
		}
		return best;
	}

	private static double distanceSq(double x1, double y1, double x2, double y2) {
		double dx = x1 - x2;
		double dy = y1 - y2;
		return dx * dx + dy * dy;
	}

	private static Comparator<SpotNode> compareInRowByCageSlotColumnThenX() {
		return (a, b) -> {
			int c = Integer.compare(a.cageSlotInLayoutRow, b.cageSlotInLayoutRow);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(a.cageColumn, b.cageColumn);
			if (c != 0) {
				return c;
			}
			c = Double.compare(a.x, b.x);
			if (c != 0) {
				return c;
			}
			return Integer.compare(a.tieIndex, b.tieIndex);
		};
	}

	private static Comparator<SpotNode> compareInRowByXThenColumn() {
		return (a, b) -> {
			int c = Double.compare(a.x, b.x);
			if (c != 0) {
				return c;
			}
			if (a.cageColumn >= 0 && b.cageColumn >= 0) {
				c = Integer.compare(a.cageColumn, b.cageColumn);
				if (c != 0) {
					return c;
				}
			}
			return Integer.compare(a.tieIndex, b.tieIndex);
		};
	}

	private static final class CageCenter {
		final int cageId;
		final double x;
		final double y;

		CageCenter(int cageId, double x, double y) {
			this.cageId = cageId;
			this.x = x;
			this.y = y;
		}
	}

	private static final class CageLayout {
		final int layoutRowIndex;
		final int slotInLayoutRow;

		CageLayout(int layoutRowIndex, int slotInLayoutRow) {
			this.layoutRowIndex = layoutRowIndex;
			this.slotInLayoutRow = slotInLayoutRow;
		}
	}

	private static final class SpotNode {
		final ROI2D roi;
		final double x;
		final double y;
		final int cageRow;
		final int cageColumn;
		final int cageId;
		final int tieIndex;
		int cageSlotInLayoutRow;

		SpotNode(ROI2D roi, Point2D p, int cageRow, int cageColumn, int cageId, int tieIndex) {
			this.roi = roi;
			this.x = p.getX();
			this.y = p.getY();
			this.cageRow = cageRow;
			this.cageColumn = cageColumn;
			this.cageId = cageId;
			this.tieIndex = tieIndex;
			this.cageSlotInLayoutRow = 0;
		}
	}

	private static final class RowBand {
		final List<SpotNode> nodes;
		final long sortKey;
		final Comparator<SpotNode> inRowComparator;
		double meanY;

		RowBand(List<SpotNode> nodes, long sortKey, Comparator<SpotNode> inRowComparator) {
			this.nodes = nodes;
			this.sortKey = sortKey;
			this.inRowComparator = inRowComparator;
			recalcMeanY();
		}

		void recalcMeanY() {
			if (nodes.isEmpty()) {
				meanY = 0;
				return;
			}
			double s = 0;
			for (SpotNode n : nodes) {
				s += n.y;
			}
			meanY = s / nodes.size();
		}
	}
}
