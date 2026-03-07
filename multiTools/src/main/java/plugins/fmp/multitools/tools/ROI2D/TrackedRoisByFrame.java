package plugins.fmp.multitools.tools.ROI2D;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import icy.roi.ROI2D;

/**
 * Expanded, frame-indexed ROI data for a tracking run. Holds a pool of distinct
 * ROIs (deduped by geometry) and a 2D table [capIndex][frame] -> pool index.
 * Used only during TrackCapillaries; convert to keyframe maps via
 * {@link #toTrackedMaps()} and inject into AlongT. Not persisted.
 */
public class TrackedRoisByFrame {

	private final int tStart;
	private final int tEnd;
	private final int nFrames;
	private final List<ROI2D> roiPool = new ArrayList<>();
	private final int[][] roiIndexByCap;

	public TrackedRoisByFrame(int tStart, int tEnd, int nCaps) {
		this.tStart = tStart;
		this.tEnd = tEnd;
		this.nFrames = tEnd - tStart + 1;
		this.roiIndexByCap = new int[nCaps][nFrames];
		for (int i = 0; i < nCaps; i++)
			for (int j = 0; j < nFrames; j++)
				roiIndexByCap[i][j] = -1;
	}

	/**
	 * Returns pool index for an ROI with the same geometry, or adds a copy and
	 * returns the new index.
	 */
	public int addOrGetRoiIndex(ROI2D roi) {
		if (roi == null)
			return -1;
		for (int i = 0; i < roiPool.size(); i++) {
			if (ROI2DUtilities.roiGeometryEquals(roiPool.get(i), roi))
				return i;
		}
		roiPool.add((ROI2D) roi.getCopy());
		return roiPool.size() - 1;
	}

	public void setRoiAt(int capIndex, int t, ROI2D roi) {
		int idx = addOrGetRoiIndex(roi);
		if (idx >= 0 && t >= tStart && t <= tEnd && capIndex >= 0 && capIndex < roiIndexByCap.length)
			roiIndexByCap[capIndex][t - tStart] = idx;
	}

	/**
	 * Returns the ROI at (capIndex, t) from the pool without copying. Caller must
	 * not modify. Returns null if not set or out of range.
	 */
	public ROI2D getRoiAtNoCopy(int capIndex, long t) {
		if (t < tStart || t > tEnd || capIndex < 0 || capIndex >= roiIndexByCap.length)
			return null;
		int idx = roiIndexByCap[capIndex][(int) (t - tStart)];
		return idx >= 0 ? roiPool.get(idx) : null;
	}

	/**
	 * Copies the index at (capIndex, t) from (capIndex, sourceT). Used to revert
	 * on SKIP_OUTLIERS_THIS_FRAME.
	 */
	public void copyIndexFromPreviousFrame(int capIndex, int t) {
		if (t <= tStart || t > tEnd || capIndex < 0 || capIndex >= roiIndexByCap.length)
			return;
		roiIndexByCap[capIndex][t - tStart] = roiIndexByCap[capIndex][t - tStart - 1];
	}

	/**
	 * Builds keyframe-only maps per capillary: only frames t where the ROI index
	 * changes from t-1 (or t == tStart). Returns list of size nCaps; unused caps
	 * get empty maps.
	 */
	public List<Map<Long, ROI2D>> toTrackedMaps() {
		return toTrackedMaps(tEnd);
	}

	/**
	 * Same as {@link #toTrackedMaps()} but only includes keyframes with t <= tEndInclusive.
	 * Used when tracking stops early (e.g. outlier stop).
	 */
	public List<Map<Long, ROI2D>> toTrackedMaps(int tEndInclusive) {
		int nCaps = roiIndexByCap.length;
		int end = Math.min(tEnd, Math.max(tStart, tEndInclusive));
		List<Map<Long, ROI2D>> out = new ArrayList<>(nCaps);
		for (int i = 0; i < nCaps; i++) {
			Map<Long, ROI2D> map = new LinkedHashMap<>();
			int prevIdx = -1;
			for (int t = tStart; t <= end; t++) {
				int idx = roiIndexByCap[i][t - tStart];
				if (idx != prevIdx && idx >= 0) {
					map.put((long) t, (ROI2D) roiPool.get(idx).getCopy());
					prevIdx = idx;
				}
			}
			out.add(map);
		}
		return out;
	}

	public int getTStart() {
		return tStart;
	}

	public int getTEnd() {
		return tEnd;
	}
}
