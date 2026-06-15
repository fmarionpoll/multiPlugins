package plugins.fmp.multiSPOTS.dlg.experiment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

/**
 * In-memory snapshot of descriptor values immediately before an EditPanel Apply,
 * for a single undo step.
 */
public final class EditApplyUndoSnapshot {

	private final EnumXLSColumnHeader field;
	private final List<Block> blocks = new ArrayList<>();

	private EditApplyUndoSnapshot(EnumXLSColumnHeader field) {
		this.field = field;
	}

	public EnumXLSColumnHeader getField() {
		return field;
	}

	public boolean isEmpty() {
		return blocks.isEmpty();
	}

	public static EditApplyUndoSnapshot capture(JComboBoxExperimentLazy editExpList, int nExperiments,
			EnumXLSColumnHeader field, String oldValue) {
		EditApplyUndoSnapshot snap = new EditApplyUndoSnapshot(field);
		if (oldValue == null) {
			return snap;
		}
		for (int i = 0; i < nExperiments; i++) {
			Experiment exp = editExpList.getItemAtNoLoad(i);
			if (exp == null) {
				continue;
			}
			String rd = resolveResultsDirectory(exp);
			if (rd == null) {
				continue;
			}
			switch (field) {
			case EXP_EXPT:
			case EXP_ID:
			case EXP_STIM1:
			case EXP_CONC1:
			case EXP_STRAIN:
			case EXP_SEX:
			case EXP_STIM2:
			case EXP_CONC2: {
				String current = exp.getCurrentValueForExperimentFieldReplace(field);
				if (current == null) {
					break;
				}
				if (!experimentValueMatchesOldForReplace(oldValue, current)) {
					break;
				}
				snap.blocks.add(new Block(rd, current, null, null));
				break;
			}
			case CAGE_SEX:
			case CAGE_STRAIN:
			case CAGE_AGE: {
				Map<Integer, String> cagePrev = new LinkedHashMap<>();
				exp.load_cages_description_and_measures();
				for (Cage cage : exp.getCages().getCageList()) {
					String current = cage.getField(field);
					if (current != null && oldValue != null && current.trim().equals(oldValue.trim())) {
						cagePrev.put(cage.getCageID(), current);
					}
				}
				if (!cagePrev.isEmpty()) {
					snap.blocks.add(new Block(rd, null, cagePrev, null));
				}
				break;
			}
			case SPOT_STIM:
			case SPOT_CONC:
			case SPOT_VOLUME: {
				List<SpotRec> spotPrev = new ArrayList<>();
				exp.load_cages_description_and_measures();
				exp.load_spots_description_and_measures();
				for (Cage cage : exp.getCages().getCageList()) {
					for (Spot spot : cage.getSpotList(exp.getSpots())) {
						String current = spot.getField(field);
						if (current != null && oldValue != null && current.trim().equals(oldValue.trim())) {
							int uid = spot.getSpotUniqueID() != null ? spot.getSpotUniqueID().getId() : -1;
							spotPrev.add(new SpotRec(uid, cage.getCageID(), spot.getProperties().getCagePosition(),
									current));
						}
					}
				}
				if (!spotPrev.isEmpty()) {
					snap.blocks.add(new Block(rd, null, null, spotPrev));
				}
				break;
			}
			default:
				break;
			}
		}
		return snap;
	}

	public boolean undo(JComboBoxExperimentLazy editExpList, int nExperiments, EnumXLSColumnHeader currentField) {
		if (currentField != field || blocks.isEmpty()) {
			return false;
		}
		boolean any = false;
		for (int i = 0; i < nExperiments; i++) {
			Experiment exp = editExpList.getItemAtNoLoad(i);
			if (exp == null) {
				continue;
			}
			String rd = resolveResultsDirectory(exp);
			if (rd == null) {
				continue;
			}
			Block b = findBlock(rd);
			if (b == null) {
				continue;
			}
			switch (field) {
			case EXP_EXPT:
			case EXP_ID:
			case EXP_STIM1:
			case EXP_CONC1:
			case EXP_STRAIN:
			case EXP_SEX:
			case EXP_STIM2:
			case EXP_CONC2:
				if (b.experimentPrevious == null) {
					break;
				}
				exp.loadExperimentDescriptors();
				exp.setExperimentFieldNoTest(field, b.experimentPrevious);
				if (exp instanceof LazyExperiment) {
					LazyExperiment le = (LazyExperiment) exp;
					ExperimentProperties cached = le.getCachedProperties();
					if (cached != null) {
						cached.setFieldNoTest(field, b.experimentPrevious);
					}
				}
				exp.saveExperimentDescriptors();
				DescriptorsIO.buildFromExperiment(exp);
				any = true;
				break;
			case CAGE_SEX:
			case CAGE_STRAIN:
			case CAGE_AGE:
				if (b.cagePrevious == null || b.cagePrevious.isEmpty()) {
					break;
				}
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}
				exp.load_cages_description_and_measures();
				for (Cage cage : exp.getCages().getCageList()) {
					String prev = b.cagePrevious.get(cage.getCageID());
					if (prev != null) {
						cage.setField(field, prev);
						any = true;
					}
				}
				exp.save_cages_description_and_measures();
				DescriptorsIO.buildFromExperiment(exp);
				break;
			case SPOT_STIM:
			case SPOT_CONC:
			case SPOT_VOLUME:
				if (b.spotPrevious == null || b.spotPrevious.isEmpty()) {
					break;
				}
				if (exp instanceof LazyExperiment) {
					((LazyExperiment) exp).loadIfNeeded();
				}
				exp.load_cages_description_and_measures();
				exp.load_spots_description_and_measures();
				for (Cage cage : exp.getCages().getCageList()) {
					for (Spot spot : cage.getSpotList(exp.getSpots())) {
						for (SpotRec rec : b.spotPrevious) {
							if (matchesSpotRec(spot, rec)) {
								spot.setField(field, rec.previousValue);
								any = true;
								break;
							}
						}
					}
				}
				exp.save_spots_description_and_measures();
				DescriptorsIO.buildFromExperiment(exp);
				break;
			default:
				break;
			}
		}
		return any;
	}

	private Block findBlock(String resultsDirectory) {
		for (Block b : blocks) {
			if (resultsDirectory.equals(b.resultsDirectory)) {
				return b;
			}
		}
		return null;
	}

	private static boolean matchesSpotRec(Spot spot, SpotRec rec) {
		if (rec.spotUid >= 0 && spot.getSpotUniqueID() != null
				&& spot.getSpotUniqueID().getId() == rec.spotUid) {
			return true;
		}
		return rec.spotUid < 0 && spot.getProperties().getCageID() == rec.cageId
				&& spot.getProperties().getCagePosition() == rec.cagePosition;
	}

	private static boolean experimentValueMatchesOldForReplace(String oldValue, String current) {
		String oldNorm = oldValue.trim();
		if (oldNorm.isEmpty()) {
			oldNorm = "..";
		}
		String curNorm = current.trim();
		if (curNorm.isEmpty()) {
			curNorm = "..";
		}
		return curNorm.equalsIgnoreCase(oldNorm);
	}

	static String resolveResultsDirectory(Experiment exp) {
		String rd = exp.getResultsDirectory();
		if (rd == null && exp instanceof LazyExperiment) {
			LazyExperiment le = (LazyExperiment) exp;
			if (le.getMetadata() != null) {
				rd = le.getMetadata().getResultsDirectory();
			}
		}
		return rd;
	}

	private static final class Block {
		final String resultsDirectory;
		final String experimentPrevious;
		final Map<Integer, String> cagePrevious;
		final List<SpotRec> spotPrevious;

		Block(String resultsDirectory, String experimentPrevious, Map<Integer, String> cagePrevious,
				List<SpotRec> spotPrevious) {
			this.resultsDirectory = resultsDirectory;
			this.experimentPrevious = experimentPrevious;
			this.cagePrevious = cagePrevious;
			this.spotPrevious = spotPrevious;
		}
	}

	private static final class SpotRec {
		final int spotUid;
		final int cageId;
		final int cagePosition;
		final String previousValue;

		SpotRec(int spotUid, int cageId, int cagePosition, String previousValue) {
			this.spotUid = spotUid;
			this.cageId = cageId;
			this.cagePosition = cagePosition;
			this.previousValue = previousValue;
		}
	}
}
