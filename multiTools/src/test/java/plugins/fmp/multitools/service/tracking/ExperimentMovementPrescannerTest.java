package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ExperimentMovementPrescannerTest {
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
