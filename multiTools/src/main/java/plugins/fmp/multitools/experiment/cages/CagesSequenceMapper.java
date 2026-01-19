package plugins.fmp.multitools.experiment.cages;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

/**
 * Handles transfer of cage data between the logical experiment model and sequences.
 * 
 * <p>This mapper provides operations for:
 * <ul>
 *   <li>Transferring cage ROIs to/from {@link SequenceCamData} (camera data sequence)</li>
 *   <li>Synchronizing cage geometry before persistence operations</li>
 * </ul>
 * 
 * <p>This class follows the same pattern as {@code SpotsSequenceMapper} and
 * {@code CapillariesSequenceMapper} to provide consistent sequence interaction
 * across all experiment entities.
 * 
 * @see SpotsSequenceMapper
 * @see CapillariesSequenceMapper
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CagesSequenceMapper {

	private CagesSequenceMapper() {
		// utility class
	}

	// === CAMERA SEQUENCE (seqCamData) OPERATIONS ===
	
	/**
	 * Transfers cage ROIs from the cages model to the camera sequence.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Removes all existing ROIs with names containing "cage" from the sequence</li>
	 *   <li>Adds all cage ROIs to the sequence for visualization and editing</li>
	 * </ol>
	 * 
	 * @param cages the cages containing ROIs to transfer
	 * @param seqCamData the camera sequence to receive the ROIs
	 * @see #transferROIsFromSequence
	 */
	public static void transferROIsToSequence(Cages cages, SequenceCamData seqCamData) {
		if (cages == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		seqCamData.removeROIsContainingString("cage");
		cages.transferROIsToSequence(seqCamData);
	}

	/**
	 * Transfers ROIs from the camera sequence back to the cages model.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Finds all ROIs in the sequence with names containing "cage"</li>
	 *   <li>Matches ROIs to existing cages by name</li>
	 *   <li>Updates cage positions from the ROI data</li>
	 * </ol>
	 * 
	 * @param cages the cages to update
	 * @param seqCamData the camera sequence containing edited ROIs
	 * @see #transferROIsToSequence
	 */
	public static void transferROIsFromSequence(Cages cages, SequenceCamData seqCamData) {
		if (cages == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		cages.transferROIsFromSequence(seqCamData);
	}

	/**
	 * Convenience operation used when saving cages: pull latest geometry from the
	 * sequence and then persist cages and positions.
	 * 
	 * @param cages the cages to sync
	 * @param seqCamData the camera sequence
	 */
	public static void syncCagesFromSequenceBeforeSave(Cages cages, SequenceCamData seqCamData) {
		transferROIsFromSequence(cages, seqCamData);
	}
	
	// === DEPRECATED METHODS ===

	/**
	 * @deprecated Use {@link #transferROIsToSequence(Cages, SequenceCamData)} instead.
	 */
	@Deprecated
	public static void transferROIsFromCagesToSequence(Cages cages, SequenceCamData seqCamData) {
		transferROIsToSequence(cages, seqCamData);
	}

	/**
	 * @deprecated Use {@link #transferROIsFromSequence(Cages, SequenceCamData)} instead.
	 */
	@Deprecated
	public static void transferROIsFromSequenceToCages(Cages cages, SequenceCamData seqCamData) {
		transferROIsFromSequence(cages, seqCamData);
	}

}
