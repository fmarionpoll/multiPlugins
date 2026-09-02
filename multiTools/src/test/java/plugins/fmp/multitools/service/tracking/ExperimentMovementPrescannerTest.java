package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ExperimentMovementPrescannerTest {
	private ExperimentMovementPrescanner.Result evidence(double move, double residual, double inliers, int count) {
		ExperimentMovementPrescanner.Result result = new ExperimentMovementPrescanner.Result(null);
		for (int i = 0; i < count; i++) result.accept(new ExperimentMovementPrescanner.FrameMetrics(
				i + 1, move, move, 0, 0, 0, residual, inliers));
		return result;
	}

	@Test public void registrationDisagreementAloneIsNotMovement() {
		assertEquals(ExperimentMovementPrescanner.Assessment.UNCERTAIN, evidence(.5, 5, .9, 3).assessment(2));
	}
	@Test public void isolatedSpikeAndPoorMatchesRemainUncertain() {
		assertEquals(ExperimentMovementPrescanner.Assessment.UNCERTAIN, evidence(8, .3, .9, 1).assessment(2));
		assertEquals(ExperimentMovementPrescanner.Assessment.UNCERTAIN, evidence(8, .3, .4, 3).assessment(2));
	}
	@Test public void repeatedReliableMovementIsSelected() {
		assertTrue(evidence(5, .3, .9, 2).isCandidate(2));
	}
	@Test public void smallReliableMotionIsBelowThreshold() {
		assertEquals(ExperimentMovementPrescanner.Assessment.BELOW_THRESHOLD, evidence(.5, .3, .9, 3).assessment(2));
	}
	@Test
	public void sparseSamplingAlwaysIncludesFirstEarlyAndLastFrames() {
		List<Integer> frames = ExperimentMovementPrescanner.sampleFrames(151, 10);
		assertEquals(Integer.valueOf(0), frames.get(0));
		assertTrue(frames.contains(1));
		assertTrue(frames.contains(150));
		assertEquals(10, frames.size());
	}

	@Test
	public void samplingDoesNotDuplicateFramesInShortRecordings() {
		List<Integer> frames = ExperimentMovementPrescanner.sampleFrames(4, 10);
		assertEquals(4, frames.size());
		assertEquals(Integer.valueOf(0), frames.get(0));
		assertTrue(frames.contains(1));
		assertTrue(frames.contains(2));
		assertTrue(frames.contains(3));
	}
}
