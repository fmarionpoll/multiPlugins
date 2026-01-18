package plugins.fmp.multitools.experiment.spots;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

public final class SpotsSequenceMapper {
	private SpotsSequenceMapper() {

	}

	/**
	 * Push spot ROIs (nested under cages) from the experiment model to the
	 * {@link SequenceCamData} as ROIs named with the {@code \"spot\"} pattern.
	 * Existing ROIs whose name contains {@code \"spot\"} are cleared first.
	 */
	public static void transferROIsFromSpotsToSequence(Spots spots, SequenceCamData seqCamData) {
		if (spots == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		seqCamData.removeROIsContainingString("spot");
		spots.transferROIsfromSpotsToSequence(seqCamData);
	}

	/**
	 * Pull spot ROIs from the {@link SequenceCamData} sequence back into the
	 * underlying {@code SpotsArray}, using the cages structure to map ROIs to
	 * logical spots.
	 */
	public static void transferROIsFromSequenceToSpots(Spots spots, SequenceCamData seqCamData) {
		if (spots == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		spots.transferROIsfromSequenceToSpots(seqCamData);
	}

}
