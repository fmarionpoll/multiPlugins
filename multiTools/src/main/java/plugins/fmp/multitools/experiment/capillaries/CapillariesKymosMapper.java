package plugins.fmp.multitools.experiment.capillaries;

import plugins.fmp.multitools.experiment.capillary.Capillary;
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
	 * Pushes capillary measures for the current kymograph frame to the sequence as ROIs.
	 */
	public static void pushCapillaryMeasuresToKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		int t = seqKymos.getCurrentFrame();
		if (t < 0 && seqKymos.getSequence().getFirstViewer() != null)
			t = seqKymos.getSequence().getFirstViewer().getPositionT();
		if (t < 0)
			t = 0;
		seqKymos.syncROIsForCurrentFrame(t, capillaries);
	}

	/**
	 * Pulls capillary measures from the current frame's ROIs on the kymograph sequence
	 * back into the {@link Capillaries} model.
	 */
	public static void pullCapillaryMeasuresFromKymos(Capillaries capillaries, SequenceKymos seqKymos) {
		if (capillaries == null || seqKymos == null || seqKymos.getSequence() == null) {
			return;
		}
		int t = seqKymos.getCurrentFrame();
		if (t < 0 && seqKymos.getSequence().getFirstViewer() != null)
			t = seqKymos.getSequence().getFirstViewer().getPositionT();
		if (t >= 0) {
			seqKymos.validateRoisAtT(t);
			Capillary cap = capillaries.getCapillaryAtT(t);
			if (cap != null)
				seqKymos.transferKymosRoi_at_T_To_Capillaries_Measures(t, cap);
		}
	}
}


