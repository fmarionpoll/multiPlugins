package plugins.fmp.multitools.service;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Comparators;

/**
 * Vertical band layout (y0 inclusive, y1 exclusive) for one cage's stacked kymograph image.
 * Matches row stacking in {@link CageSpotKymographBuilder}. When present in the kymograph bin,
 * {@link CageKymographStripLayoutCsv} overrides ROI-derived heights for picking and analysis.
 * <p>
 * {@link #layout} emits <strong>one band per spot</strong> in the cage (name-sorted), so stack index aligns
 * with that ordering. When ROI geometry is missing, a one-pixel placeholder band ({@link #geometryMissing})
 * still reserves vertical space and analysis yields an all-NaN series for that spot.
 */
public final class CageKymographSpotBands {

	public final Spot spot;
	public final int y0;
	public final int y1Exclusive;
	/** When true, ROI was unusable; the band is a single-row placeholder and metrics are not computed from pixels. */
	public final boolean geometryMissing;

	public CageKymographSpotBands(Spot spot, int y0, int y1Exclusive, boolean geometryMissing) {
		this.spot = spot;
		this.y0 = y0;
		this.y1Exclusive = y1Exclusive;
		this.geometryMissing = geometryMissing;
	}

	public int height() {
		return y1Exclusive - y0;
	}

	/**
	 * Same ordering as {@link CageSpotKymographBuilder}: spots sorted by name, one band per spot.
	 * Band height = clipped ROI bounding-box height, or a single-row placeholder if geometry is invalid.
	 */
	public static List<CageKymographSpotBands> layout(Cage cage, Spots allSpots, int refSizex, int refSizey) {
		List<CageKymographSpotBands> out = new ArrayList<>();
		if (cage == null || allSpots == null || refSizex <= 0 || refSizey <= 0) {
			return out;
		}
		List<Spot> raw = cage.getSpotList(allSpots);
		if (raw.isEmpty()) {
			return out;
		}
		ArrayList<Spot> sorted = new ArrayList<>(raw);
		Collections.sort(sorted, new Comparators.Spot_Name());
		int row = 0;
		for (Spot s : sorted) {
			Rectangle b = getSpotBounds(s, refSizex, refSizey);
			int y0 = row;
			if (b == null || b.height <= 0 || b.width <= 0) {
				row += 1;
				out.add(new CageKymographSpotBands(s, y0, row, true));
			} else {
				row += b.height;
				out.add(new CageKymographSpotBands(s, y0, row, false));
			}
		}
		return out;
	}

	static Rectangle getSpotBounds(Spot spot, int refW, int refH) {
		if (spot == null) {
			return null;
		}
		ROI2D roi = spot.getRoi();
		if (roi == null) {
			return null;
		}
		Rectangle b = roi.getBounds();
		if (b == null) {
			return null;
		}
		int x0 = Math.max(0, b.x);
		int y0 = Math.max(0, b.y);
		int x1 = Math.min(refW - 1, b.x + b.width - 1);
		int y1 = Math.min(refH - 1, b.y + b.height - 1);
		if (x1 < x0 || y1 < y0) {
			return null;
		}
		return new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
	}
}
