package plugins.fmp.multitools.experiment.capillaries;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;

/**
 * Handles transfer of capillary data between the logical experiment model and sequences.
 * 
 * <p>This mapper provides operations for:
 * <ul>
 *   <li>Transferring capillary ROIs to/from {@link SequenceCamData} (camera data sequence)</li>
 *   <li>Transferring capillary measures to/from {@link SequenceKymos} (kymograph sequence)</li>
 * </ul>
 * 
 * <p>This class follows the same pattern as {@code SpotsSequenceMapper} and {@code CagesSequenceMapper}
 * to provide consistent sequence interaction across all experiment entities.
 * 
 * <p>Usage examples:
 * <pre>{@code
 * // Transfer capillary ROIs to camera sequence for display
 * CapillariesSequenceMapper.transferROIsToSequence(capillaries, seqCamData);
 * 
 * // Update capillaries from user-edited ROIs in camera sequence
 * CapillariesSequenceMapper.transferROIsFromSequence(capillaries, seqCamData);
 * 
 * // Transfer capillary measures to kymograph for visualization
 * CapillariesSequenceMapper.transferMeasuresToKymos(capillaries, seqKymos);
 * 
 * // Read back user-edited measures from kymograph ROIs
 * CapillariesSequenceMapper.transferMeasuresFromKymos(capillaries, seqKymos);
 * }</pre>
 * 
 * @see SpotsSequenceMapper
 * @see CagesSequenceMapper
 * @see CapillariesKymosMapper
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public final class CapillariesSequenceMapper {
	
	private CapillariesSequenceMapper() {
		// Utility class - prevent instantiation
	}

	// === CAMERA SEQUENCE (seqCamData) OPERATIONS ===
	// These handle capillary ROI definitions (line ROIs representing capillary positions)
	
	/**
	 * Transfers capillary ROIs from the capillaries model to the camera sequence.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Removes all existing ROIs with names containing "line" from the sequence</li>
	 *   <li>Adds all capillary ROIs to the sequence for visualization and editing</li>
	 * </ol>
	 * 
	 * <p>The ROIs represent the physical position and orientation of capillaries
	 * in the camera view. Users can edit these ROIs in the sequence, and then call
	 * {@link #transferROIsFromSequence} to update the capillaries model.
	 * 
	 * @param capillaries the capillaries containing ROIs to transfer
	 * @param seqCamData the camera sequence to receive the ROIs
	 * @see #transferROIsFromSequence
	 */
	public static void transferROIsToSequence(Capillaries capillaries, SequenceCamData seqCamData) {
		if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		
		// Remove existing capillary ROIs (containing "line")
		seqCamData.removeROIsContainingString("line");
		
		// Add capillary ROIs to sequence
		capillaries.transferROIsToSequence(seqCamData.getSequence());
	}
	
	/**
	 * Transfers ROIs from the camera sequence back to the capillaries model.
	 * 
	 * <p>This operation:
	 * <ol>
	 *   <li>Finds all ROIs in the sequence with names containing "line"</li>
	 *   <li>Matches ROIs to existing capillaries by name</li>
	 *   <li>Updates capillary positions from the ROI data</li>
	 *   <li>Removes capillaries that no longer have matching ROIs</li>
	 * </ol>
	 * 
	 * <p>This allows users to edit capillary positions/orientations in the ICY viewer
	 * and have those changes reflected in the experiment model.
	 * 
	 * @param capillaries the capillaries to update
	 * @param seqCamData the camera sequence containing edited ROIs
	 * @see #transferROIsToSequence
	 */
	public static void transferROIsFromSequence(Capillaries capillaries, SequenceCamData seqCamData) {
		if (capillaries == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		
		// Update capillaries from sequence ROIs
		capillaries.transferROIsFromSequence(seqCamData);
	}

	// === KYMOGRAPH SEQUENCE (seqKymos) OPERATIONS ===
	// These handle capillary measure data (top level, bottom level, derivative, gulps)
	
	/**
	 * Transfers capillary measures from the capillaries model to the kymograph sequence as ROIs.
	 * 
	 * <p>This operation renders capillary measurements (top level, bottom level, derivative, gulps)
	 * as polyline ROIs on the kymograph sequence. Each measurement type becomes a separate ROI
	 * that can be visualized and edited.
	 * 
	 * <p>The ROIs are named with the pattern: "{prefix}_{measureType}", for example:
	 * <ul>
	 *   <li>"0L_toplevel" - top level measurement for capillary 0L</li>
	 *   <li>"0L_bottomlevel" - bottom level measurement for capillary 0L</li>
	 *   <li>"0L_derivative" - derivative measurement for capillary 0L</li>
	 *   <li>"0L_gulps" - gulp detection points for capillary 0L</li>
	 * </ul>
	 * 
	 * <p>Users can edit these ROIs in the kymograph viewer, and then call
	 * {@link #transferMeasuresFromKymos} to update the capillaries model.
	 * 
	 * @param capillaries the capillaries containing measures to transfer
	 * @param seqKymos the kymograph sequence to receive the measure ROIs
	 * @see #transferMeasuresFromKymos
	 * @see CapillariesKymosMapper#pushCapillaryMeasuresToKymos
	 */
	public static void transferMeasuresToKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		
		// Delegate to existing CapillariesKymosMapper
		CapillariesKymosMapper.pushCapillaryMeasuresToKymos(capillaries, seqKymos);
	}
	
	/**
	 * Transfers measure ROIs from the kymograph sequence back to the capillaries model.
	 * 
	 * <p>This operation reads edited ROIs from the kymograph sequence and updates
	 * the corresponding capillary measurements. The sequence ROIs are validated before
	 * being transferred to ensure data integrity.
	 * 
	 * <p>ROI validation includes:
	 * <ul>
	 *   <li>Checking that ROIs have the expected naming pattern</li>
	 *   <li>Ensuring ROIs are assigned to the correct time point (T)</li>
	 *   <li>Verifying that polyline data is well-formed</li>
	 * </ul>
	 * 
	 * <p>This allows users to manually correct or refine measurements by editing
	 * the measure ROIs in the kymograph viewer.
	 * 
	 * @param capillaries the capillaries to update with measures
	 * @param seqKymos the kymograph sequence containing edited measure ROIs
	 * @see #transferMeasuresToKymos
	 * @see CapillariesKymosMapper#pullCapillaryMeasuresFromKymos
	 */
	public static void transferMeasuresFromKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		
		// Delegate to existing CapillariesKymosMapper
		CapillariesKymosMapper.pullCapillaryMeasuresFromKymos(capillaries, seqKymos);
	}
	
	// === CONVENIENCE METHODS ===
	
	/**
	 * Complete workflow: push capillaries to camera sequence and measures to kymograph.
	 * 
	 * <p>This is a convenience method that performs both operations:
	 * <ol>
	 *   <li>Transfers capillary ROIs to the camera sequence</li>
	 *   <li>Transfers capillary measures to the kymograph sequence</li>
	 * </ol>
	 * 
	 * <p>Use this when initializing or refreshing both sequences with capillary data.
	 * 
	 * @param capillaries the capillaries to transfer
	 * @param seqCamData the camera sequence (can be null to skip ROI transfer)
	 * @param seqKymos the kymograph sequence (can be null to skip measure transfer)
	 */
	public static void transferAllToSequences(Capillaries capillaries, 
			SequenceCamData seqCamData, SequenceKymos seqKymos) {
		if (seqCamData != null) {
			transferROIsToSequence(capillaries, seqCamData);
		}
		if (seqKymos != null) {
			transferMeasuresToKymos(capillaries, seqKymos);
		}
	}
	
	/**
	 * Complete workflow: pull capillaries from camera sequence and measures from kymograph.
	 * 
	 * <p>This is a convenience method that performs both operations:
	 * <ol>
	 *   <li>Updates capillaries from ROIs in the camera sequence</li>
	 *   <li>Updates capillary measures from ROIs in the kymograph sequence</li>
	 * </ol>
	 * 
	 * <p>Use this when loading user-edited data from both sequences back into the model.
	 * 
	 * @param capillaries the capillaries to update
	 * @param seqCamData the camera sequence (can be null to skip ROI update)
	 * @param seqKymos the kymograph sequence (can be null to skip measure update)
	 */
	public static void transferAllFromSequences(Capillaries capillaries,
			SequenceCamData seqCamData, SequenceKymos seqKymos) {
		if (seqCamData != null) {
			transferROIsFromSequence(capillaries, seqCamData);
		}
		if (seqKymos != null) {
			transferMeasuresFromKymos(capillaries, seqKymos);
		}
	}
}
