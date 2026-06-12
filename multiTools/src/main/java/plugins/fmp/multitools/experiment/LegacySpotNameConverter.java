package plugins.fmp.multitools.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotString;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;

/**
 * Legacy three-part names {@code spot_<cage>_<linearIndex>} → four-part
 * {@code spot_<cage>_<row>_<col>} (row-major: left-to-right, then next row).
 */
public final class LegacySpotNameConverter {

	/** One cage whose spot count does not match {@code nCols × nRows} while it contains legacy three-part names. */
	public static final class LegacyCageGridMismatch {
		public final int cageId;
		public final int spotCount;
		public final int expectedCount;
		public final int nCols;
		public final int nRows;

		public LegacyCageGridMismatch(int cageId, int spotCount, int expectedCount, int nCols, int nRows) {
			this.cageId = cageId;
			this.spotCount = spotCount;
			this.expectedCount = expectedCount;
			this.nCols = nCols;
			this.nRows = nRows;
		}

		public String toSummaryLine() {
			return "cage " + cageId + ": has " + spotCount + " spots, expected " + expectedCount + " for " + nCols
					+ "×" + nRows + ".";
		}
	}

	private LegacySpotNameConverter() {
	}

	public static boolean isLegacyThreePartSpotName(String name) {
		if (name == null) {
			return false;
		}
		String[] p = name.split("_");
		if (p.length != 3 || !"spot".equalsIgnoreCase(p[0])) {
			return false;
		}
		try {
			Integer.parseInt(p[1]);
			Integer.parseInt(p[2]);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/** Name from properties or materialized ROI, for legacy detection and heuristics. */
	public static String primarySpotName(Spot spot) {
		if (spot == null) {
			return null;
		}
		String n = spot.getProperties().getName();
		if (n != null && !n.isEmpty()) {
			return n;
		}
		if (spot.getRoiDirect() != null) {
			return spot.getRoiDirect().getName();
		}
		return null;
	}

	public static boolean anyLegacyThreePartSpots(Spots spots) {
		if (spots == null) {
			return false;
		}
		for (Spot s : spots.getSpotList()) {
			if (isLegacyThreePartSpotName(primarySpotName(s))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Cages that contain at least one legacy three-part spot name but whose spot count is not
	 * {@code nCols * nRows}. Empty if arguments are invalid.
	 */
	public static List<LegacyCageGridMismatch> listLegacyCageGridMismatches(Experiment exp, int nCols, int nRows) {
		if (exp == null || exp.getCages() == null || nCols <= 0 || nRows <= 0) {
			return Collections.emptyList();
		}
		int expected = nCols * nRows;
		List<LegacyCageGridMismatch> out = new ArrayList<>();
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null) {
				continue;
			}
			List<Spot> inCage = cage.getSpotList(exp.getSpots());
			boolean anyLegacy = false;
			for (Spot s : inCage) {
				if (isLegacyThreePartSpotName(primarySpotName(s))) {
					anyLegacy = true;
					break;
				}
			}
			if (!anyLegacy) {
				continue;
			}
			if (inCage.size() != expected) {
				out.add(new LegacyCageGridMismatch(cage.getCageID(), inCage.size(), expected, nCols, nRows));
			}
		}
		return out;
	}

	public static List<String> validateCageSpotCounts(Experiment exp, int nCols, int nRows) {
		List<String> problems = new ArrayList<>();
		if (exp == null || exp.getCages() == null || nCols <= 0 || nRows <= 0) {
			problems.add("Invalid experiment or grid dimensions.");
			return problems;
		}
		for (LegacyCageGridMismatch m : listLegacyCageGridMismatches(exp, nCols, nRows)) {
			problems.add(m.toSummaryLine());
		}
		return problems;
	}

	public static boolean applyConversion(Experiment exp, int nCols, int nRows) {
		if (exp == null || exp.getSpots() == null || nCols <= 0 || nRows <= 0) {
			return false;
		}
		List<String> bad = validateCageSpotCounts(exp, nCols, nRows);
		if (!bad.isEmpty()) {
			Logger.warn("LegacySpotNameConverter: validation failed: " + String.join(" ", bad));
			return false;
		}
		for (Spot spot : exp.getSpots().getSpotList()) {
			if (spot == null) {
				continue;
			}
			String name = primarySpotName(spot);
			if (!isLegacyThreePartSpotName(name)) {
				continue;
			}
			int cageId = SpotString.getCageIDFromSpotName(name);
			int yyy = SpotString.getSpotCagePositionFromSpotName(name);
			if (cageId < 0 || yyy < 0) {
				continue;
			}
			int row = yyy / nCols;
			int col = yyy % nCols;
			spot.getProperties().setCageID(cageId);
			spot.getProperties().setCagePosition(yyy);
			spot.getProperties().setCageRow(row);
			spot.getProperties().setCageColumn(col);
			spot.setNameRowCol(cageId, row, col);
		}
		return true;
	}
}
