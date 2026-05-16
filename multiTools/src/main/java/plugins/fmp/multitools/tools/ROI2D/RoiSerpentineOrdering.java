package plugins.fmp.multitools.tools.ROI2D;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import icy.roi.ROI2D;

/**
 * Orders ROIs in serpentine (boustrophedon) reading order for polyline "snake"
 * display: even rows left-to-right, odd rows right-to-left.
 */
public final class RoiSerpentineOrdering {

	private static final double Y_TOLERANCE_FLOOR = 5.0;
	private static final double COLLINEAR_Y_EPSILON = 0.5;

	private RoiSerpentineOrdering() {
	}

	/**
	 * Geometry-first serpentine order; falls back to row/column metadata when anchors
	 * are collinear on one Y but metadata defines multiple rows.
	 */
	public static ArrayList<ROI2D> orderSerpentine(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn,
			Function<ROI2D, int[]> rowColFn) {
		if (rois == null || rois.isEmpty()) {
			return new ArrayList<>();
		}
		if (rowColFn != null && shouldUseRowColFallback(rois, anchorFn, rowColFn)) {
			return orderByRowColSerpentine(rois, rowColFn);
		}
		return orderByGeometrySerpentine(rois, anchorFn);
	}

	public static ArrayList<ROI2D> orderByGeometrySerpentine(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn) {
		ArrayList<ROI2D> result = new ArrayList<>();
		if (rois == null || rois.isEmpty()) {
			return result;
		}
		if (rois.size() == 1) {
			result.add(rois.get(0));
			return result;
		}

		List<RoiWithAnchor> items = new ArrayList<>(rois.size());
		for (int i = 0; i < rois.size(); i++) {
			ROI2D roi = rois.get(i);
			Point2D anchor = anchorFn.apply(roi);
			if (anchor == null) {
				anchor = new Point2D.Double(0, 0);
			}
			items.add(new RoiWithAnchor(roi, anchor, i));
		}

		Collections.sort(items, compareByYThenXThenIndex());

		double yTol = estimateRowYTolerance(items);
		List<List<RoiWithAnchor>> rows = clusterIntoRows(items, yTol);

		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			List<RoiWithAnchor> row = rows.get(rowIndex);
			Collections.sort(row, compareByXThenIndex());
			if (rowIndex % 2 == 1) {
				Collections.reverse(row);
			}
			for (RoiWithAnchor item : row) {
				result.add(item.roi);
			}
		}
		return result;
	}

	public static ArrayList<ROI2D> orderByRowColSerpentine(List<ROI2D> rois, Function<ROI2D, int[]> rowColFn) {
		ArrayList<ROI2D> result = new ArrayList<>();
		if (rois == null || rois.isEmpty() || rowColFn == null) {
			if (rois != null) {
				result.addAll(rois);
			}
			return result;
		}

		List<RoiWithRowCol> items = new ArrayList<>(rois.size());
		for (int i = 0; i < rois.size(); i++) {
			ROI2D roi = rois.get(i);
			int[] rc = rowColFn.apply(roi);
			if (rc == null || rc.length < 2 || rc[0] < 0 || rc[1] < 0) {
				return new ArrayList<>(rois);
			}
			items.add(new RoiWithRowCol(roi, rc[0], rc[1], i));
		}

		Collections.sort(items, Comparator.comparingInt((RoiWithRowCol a) -> a.row));
		List<List<RoiWithRowCol>> rows = new ArrayList<>();
		int currentRow = Integer.MIN_VALUE;
		for (RoiWithRowCol item : items) {
			if (rows.isEmpty() || item.row != currentRow) {
				rows.add(new ArrayList<>());
				currentRow = item.row;
			}
			rows.get(rows.size() - 1).add(item);
		}

		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			List<RoiWithRowCol> row = rows.get(rowIndex);
			if (rowIndex % 2 == 0) {
				row.sort(Comparator.comparingInt((RoiWithRowCol a) -> a.col)
						.thenComparingInt(a -> a.tieIndex));
			} else {
				row.sort((a, b) -> {
					int cmp = Integer.compare(b.col, a.col);
					if (cmp != 0) {
						return cmp;
					}
					return Integer.compare(a.tieIndex, b.tieIndex);
				});
			}
			for (RoiWithRowCol item : row) {
				result.add(item.roi);
			}
		}
		return result;
	}

	private static boolean shouldUseRowColFallback(List<ROI2D> rois, Function<ROI2D, Point2D> anchorFn,
			Function<ROI2D, int[]> rowColFn) {
		if (rois.size() < 2) {
			return false;
		}

		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		Set<Integer> metaRows = new HashSet<>();

		for (ROI2D roi : rois) {
			int[] rc = rowColFn.apply(roi);
			if (rc == null || rc.length < 2 || rc[0] < 0 || rc[1] < 0) {
				return false;
			}
			metaRows.add(rc[0]);

			Point2D anchor = anchorFn.apply(roi);
			if (anchor != null) {
				minY = Math.min(minY, anchor.getY());
				maxY = Math.max(maxY, anchor.getY());
			}
		}

		return metaRows.size() > 1 && (maxY - minY) <= COLLINEAR_Y_EPSILON;
	}

	private static Comparator<RoiWithAnchor> compareByYThenXThenIndex() {
		return (a, b) -> {
			int cmp = Double.compare(a.anchor.getY(), b.anchor.getY());
			if (cmp != 0) {
				return cmp;
			}
			cmp = Double.compare(a.anchor.getX(), b.anchor.getX());
			if (cmp != 0) {
				return cmp;
			}
			return Integer.compare(a.tieIndex, b.tieIndex);
		};
	}

	private static Comparator<RoiWithAnchor> compareByXThenIndex() {
		return (a, b) -> {
			int cmp = Double.compare(a.anchor.getX(), b.anchor.getX());
			if (cmp != 0) {
				return cmp;
			}
			return Integer.compare(a.tieIndex, b.tieIndex);
		};
	}

	private static List<List<RoiWithAnchor>> clusterIntoRows(List<RoiWithAnchor> sortedByYThenX, double yTol) {
		List<List<RoiWithAnchor>> rows = new ArrayList<>();
		for (RoiWithAnchor item : sortedByYThenX) {
			if (rows.isEmpty()) {
				List<RoiWithAnchor> row0 = new ArrayList<>();
				row0.add(item);
				rows.add(row0);
				continue;
			}
			List<RoiWithAnchor> lastRow = rows.get(rows.size() - 1);
			double lastMeanY = meanY(lastRow);
			if (Math.abs(item.anchor.getY() - lastMeanY) <= yTol) {
				lastRow.add(item);
			} else {
				List<RoiWithAnchor> newRow = new ArrayList<>();
				newRow.add(item);
				rows.add(newRow);
			}
		}
		return rows;
	}

	private static double meanY(List<RoiWithAnchor> row) {
		if (row == null || row.isEmpty()) {
			return 0;
		}
		double sum = 0;
		for (RoiWithAnchor item : row) {
			sum += item.anchor.getY();
		}
		return sum / row.size();
	}

	private static double estimateRowYTolerance(List<RoiWithAnchor> sortedByYThenX) {
		if (sortedByYThenX == null || sortedByYThenX.size() < 2) {
			return Y_TOLERANCE_FLOOR;
		}

		List<Double> diffs = new ArrayList<>(sortedByYThenX.size() - 1);
		double prevY = sortedByYThenX.get(0).anchor.getY();
		for (int i = 1; i < sortedByYThenX.size(); i++) {
			double y = sortedByYThenX.get(i).anchor.getY();
			double dy = y - prevY;
			if (dy > 0) {
				diffs.add(dy);
			}
			prevY = y;
		}

		if (diffs.isEmpty()) {
			return Y_TOLERANCE_FLOOR;
		}

		Collections.sort(diffs);
		int idx90 = (int) Math.floor(0.90 * (diffs.size() - 1));
		double p90 = diffs.get(Math.max(0, Math.min(diffs.size() - 1, idx90)));
		return Math.max(Y_TOLERANCE_FLOOR, 0.5 * p90);
	}

	private static final class RoiWithAnchor {
		final ROI2D roi;
		final Point2D anchor;
		final int tieIndex;

		RoiWithAnchor(ROI2D roi, Point2D anchor, int tieIndex) {
			this.roi = roi;
			this.anchor = anchor;
			this.tieIndex = tieIndex;
		}
	}

	private static final class RoiWithRowCol {
		final ROI2D roi;
		final int row;
		final int col;
		final int tieIndex;

		RoiWithRowCol(ROI2D roi, int row, int col, int tieIndex) {
			this.roi = roi;
			this.row = row;
			this.col = col;
			this.tieIndex = tieIndex;
		}
	}
}
