package plugins.fmp.multitools.experiment;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotString;

/**
 * After spots are loaded and dispatched to cages, assigns {@code cageRow}/{@code cageColumn} (and
 * four-part ROI names) when legacy three-part names or missing row/column metadata need inference.
 * <ul>
 * <li>If stimulus, concentration, or color changes along the ordered spot list, start a new row;
 * columns count within the current row.</li>
 * <li>If all spots share the same triple, use {@link Cage#reorderSpotsReadingOrderAndAssignRowCol} geometry.</li>
 * </ul>
 * Ordering for the stimulus pass is legacy linear index when present, otherwise top-to-bottom then
 * left-to-right by spot centers.
 */
public final class SpotCageHeuristicLayout {

	private SpotCageHeuristicLayout() {
	}

	public static void applyAfterSpotsDispatched(Experiment exp) {
		if (exp == null || exp.getCages() == null || exp.getSpots() == null) {
			return;
		}
		// Align synthetic ROI tiling with MS96_cages.xml (or v2 cage description) before any
		// per-cage inference; otherwise ensureSpotROIsFromCageGeometry keeps the 8×4 default
		// and overwrites CSV/XML spots that lack a deserialized ROI (~30px vs real ~22px).
		Cages cages0 = exp.getCages();
		if (cages0.nColumnsPerCage > 0 && cages0.nRowsPerCage > 0) {
			cages0.setSpotRoiGridCells(cages0.nColumnsPerCage, cages0.nRowsPerCage);
		}
		int maxRow = 0;
		int maxCol = 0;
		boolean anyInference = false;
		for (Cage cage : exp.getCages().cagesList) {
			if (cage == null) {
				continue;
			}
			List<Spot> spots = new ArrayList<>(cage.getSpotList(exp.getSpots()));
			spots.removeIf(Objects::isNull);
			if (spots.isEmpty()) {
				continue;
			}
			if (!cageNeedsInference(spots)) {
				for (Spot s : spots) {
					if (s.getProperties().getCageRow() >= 0 && s.getProperties().getCageColumn() >= 0) {
						maxRow = Math.max(maxRow, s.getProperties().getCageRow());
						maxCol = Math.max(maxCol, s.getProperties().getCageColumn());
					}
				}
				continue;
			}
			anyInference = true;
			boolean anyLegacy = spots.stream()
					.anyMatch(s -> LegacySpotNameConverter.isLegacyThreePartSpotName(LegacySpotNameConverter.primarySpotName(s)));
			int cageId = cage.getCageID();
			if (allSameStimulusConcentrationColor(spots)) {
				cage.reorderSpotsReadingOrderAndAssignRowCol(exp.getSpots());
				for (Spot s : cage.getSpotList(exp.getSpots())) {
					if (s == null) {
						continue;
					}
					int r = s.getProperties().getCageRow();
					int c = s.getProperties().getCageColumn();
					if (r >= 0 && c >= 0) {
						s.setNameRowCol(cageId, r, c);
						maxRow = Math.max(maxRow, r);
						maxCol = Math.max(maxCol, c);
					}
				}
				continue;
			}
			spots.sort(buildOrderComparator(anyLegacy));

			int row = 0;
			int col = 0;
			String prevKey = null;
			for (int i = 0; i < spots.size(); i++) {
				Spot s = spots.get(i);
				String key = tripleKey(s);
				if (prevKey != null && !prevKey.equals(key)) {
					row++;
					col = 0;
				}
				prevKey = key;
				s.getProperties().setCageID(cageId);
				s.getProperties().setCagePosition(i);
				s.getProperties().setCageRow(row);
				s.getProperties().setCageColumn(col);
				s.setNameRowCol(cageId, row, col);
				maxRow = Math.max(maxRow, row);
				maxCol = Math.max(maxCol, col);
				col++;
			}
		}
		if (anyInference) {
			exp.getCages().setSpotRoiGridCells(Math.max(1, maxCol + 1), Math.max(1, maxRow + 1));
		}
	}

	private static boolean cageNeedsInference(List<Spot> spots) {
		boolean anyLegacy = false;
		for (Spot s : spots) {
			if (LegacySpotNameConverter.isLegacyThreePartSpotName(LegacySpotNameConverter.primarySpotName(s))) {
				anyLegacy = true;
				break;
			}
		}
		if (anyLegacy) {
			return true;
		}
		boolean allMissingRowCol = true;
		for (Spot s : spots) {
			if (s.getProperties().getCageRow() >= 0 && s.getProperties().getCageColumn() >= 0) {
				allMissingRowCol = false;
				break;
			}
		}
		return allMissingRowCol;
	}

	private static Comparator<Spot> buildOrderComparator(boolean anyLegacy) {
		if (anyLegacy) {
			return Comparator.comparingInt(SpotCageHeuristicLayout::legacyOrPositionIndex)
					.thenComparingDouble(SpotCageHeuristicLayout::yCenter).thenComparingDouble(SpotCageHeuristicLayout::xCenter);
		}
		return Comparator.comparingDouble(SpotCageHeuristicLayout::yCenter).thenComparingDouble(SpotCageHeuristicLayout::xCenter)
				.thenComparingInt(s -> s.getSpotUniqueID() != null ? s.getSpotUniqueID().getId() : 0);
	}

	private static int legacyOrPositionIndex(Spot s) {
		String n = LegacySpotNameConverter.primarySpotName(s);
		if (LegacySpotNameConverter.isLegacyThreePartSpotName(n)) {
			int idx = SpotString.getSpotCagePositionFromSpotName(n);
			return idx >= 0 ? idx : Integer.MAX_VALUE;
		}
		return s.getProperties().getCagePosition();
	}

	private static double yCenter(Spot spot) {
		if (spot.getRoiDirect() != null) {
			Rectangle b = spot.getRoiDirect().getBounds();
			if (b != null) {
				return b.getCenterY();
			}
		}
		int y = spot.getProperties().getSpotYCoord();
		return y >= 0 ? y : 0.0;
	}

	private static double xCenter(Spot spot) {
		if (spot.getRoiDirect() != null) {
			Rectangle b = spot.getRoiDirect().getBounds();
			if (b != null) {
				return b.getCenterX();
			}
		}
		int x = spot.getProperties().getSpotXCoord();
		return x >= 0 ? x : 0.0;
	}

	private static String tripleKey(Spot s) {
		String stim = s.getProperties().getStimulus();
		if (stim == null) {
			stim = "";
		}
		stim = stim.trim();
		String conc = s.getProperties().getConcentration();
		if (conc == null) {
			conc = "";
		}
		conc = conc.trim();
		Color c = s.getProperties().getColor();
		int rgb = c == null ? 0 : (c.getRGB() & 0xffffff);
		return stim + "\u0001" + conc + "\u0001" + rgb;
	}

	private static boolean allSameStimulusConcentrationColor(List<Spot> spots) {
		if (spots.size() <= 1) {
			return true;
		}
		String first = tripleKey(spots.get(0));
		for (int i = 1; i < spots.size(); i++) {
			if (!first.equals(tripleKey(spots.get(i)))) {
				return false;
			}
		}
		return true;
	}
}
