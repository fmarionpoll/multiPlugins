package plugins.fmp.multitools.experiment.spots;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

public final class SpotsSequenceMapper {
	private SpotsSequenceMapper() {

	}

	/**
	 * Transfers spot ROIs from the spots model to the camera sequence.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Removes all existing ROIs with names containing "spot" from the sequence</li>
	 *   <li>Adds all spot ROIs to the sequence for visualization and editing</li>
	 * </ol>
	 * 
	 * @param spots the spots containing ROIs to transfer
	 * @param seqCamData the camera sequence to receive the ROIs
	 * @see #transferROIsFromSequence
	 */
	public static void transferROIsToSequence(Spots spots, SequenceCamData seqCamData) {
		if (spots == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		seqCamData.removeROIsContainingString("spot");
		spots.transferROIsToSequence(seqCamData);
	}

	/**
	 * Transfers ROIs from the camera sequence back to the spots model.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Finds all ROIs in the sequence with names containing "spot"</li>
	 *   <li>Matches ROIs to existing spots by name</li>
	 *   <li>Updates spot positions from the ROI data</li>
	 * </ol>
	 * 
	 * @param spots the spots to update
	 * @param seqCamData the camera sequence containing edited ROIs
	 * @see #transferROIsToSequence
	 */
	public static void transferROIsFromSequence(Spots spots, SequenceCamData seqCamData) {
		if (spots == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		spots.transferROIsFromSequence(seqCamData);
	}

	// === DEPRECATED METHODS ===

	/**
	 * @deprecated Use {@link #transferROIsToSequence(Spots, SequenceCamData)} instead.
	 */
	@Deprecated
	public static void transferROIsFromSpotsToSequence(Spots spots, SequenceCamData seqCamData) {
		transferROIsToSequence(spots, seqCamData);
	}

	/**
	 * @deprecated Use {@link #transferROIsFromSequence(Spots, SequenceCamData)} instead.
	 */
	@Deprecated
	public static void transferROIsFromSequenceToSpots(Spots spots, SequenceCamData seqCamData) {
		transferROIsFromSequence(spots, seqCamData);
	}

}
