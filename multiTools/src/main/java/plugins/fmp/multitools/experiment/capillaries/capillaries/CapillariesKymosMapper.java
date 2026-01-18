package plugins.fmp.multitools.experiment.capillaries.capillaries;

import plugins.fmp.multitools.experiment.sequence.SequenceKymos;

/**
 * Helper methods to move capillary measures between the logical experiment
 * model and the kymograph {@link SequenceKymos}.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>model → kymos: render measures as kymograph ROIs,</li>
 *   <li>kymos → model: read edited ROIs back into capillary measures.</li>
 * </ul>
 */
public final class CapillariesKymosMapper {

	private CapillariesKymosMapper() {
		// utility class
	}

	/**
	 * Pushes all capillary measures to the kymograph sequence as ROIs.
	 */
	public static void pushCapillaryMeasuresToKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		seqKymos.transferCapillariesMeasuresToKymos(capillaries);
	}

	/**
	 * Pulls capillary measures from ROIs defined on the kymograph sequence back
	 * into the {@link Capillaries} model.
	 */
	public static void pullCapillaryMeasuresFromKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		seqKymos.validateROIs();
		seqKymos.transferKymosRoisToCapillaries_Measures(capillaries);
	}
}


