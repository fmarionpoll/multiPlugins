package plugins.fmp.multitools.experiment.cages;

import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

/**
 * Helper methods to move cage geometry and spot ROIs between the logical
 * experiment model and the camera {@link SequenceCamData}.
 * <p>
 * This class is intentionally small and stateless; it is meant to document and
 * centralize the typical flows:
 * <ul>
 * <li>model → sequence: push cages / spots as ROIs,</li>
 * <li>sequence → model: pull updated cages / spots from ROIs.</li>
 * </ul>
 *
 * The goal is to make the high‑level persistence/orchestration code in
 * {@code Experiment} easier to read, without changing any underlying behavior.
 */
public final class CagesSequenceMapper {

	private CagesSequenceMapper() {
		// utility class
	}

	/**
	 * Push cage ROIs from the {@link Cages} model to the given
	 * {@link SequenceCamData}. Existing ROIs whose name contains {@code "cage"} are
	 * cleared before transfer.
	 */
	public static void transferROIsFromCagesToSequence(Cages cages, SequenceCamData seqCamData) {
		if (cages == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		seqCamData.removeROIsContainingString("cage");
		cages.transferROIsFromCagesToSequence(seqCamData);
	}

	/**
	 * Pull cage geometry from ROIs on the given {@link SequenceCamData} back into
	 * the {@link Cages} model.
	 */
	public static void transferROIsFromSequenceToCages(Cages cages, SequenceCamData seqCamData) {
		if (cages == null || seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		cages.transferROIsFromSequenceToCages(seqCamData);
	}

	/**
	 * Convenience operation used when saving cages: pull latest geometry from the
	 * sequence and then persist cages and positions.
	 */
	public static void syncCagesFromSequenceBeforeSave(Cages cages, SequenceCamData seqCamData) {
		transferROIsFromSequenceToCages(cages, seqCamData);
	}

}
