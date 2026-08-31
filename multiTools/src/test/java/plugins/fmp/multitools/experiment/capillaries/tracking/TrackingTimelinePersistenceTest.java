package plugins.fmp.multitools.experiment.capillaries.tracking;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class TrackingTimelinePersistenceTest {
	@Test
	public void roundTripsBoundariesWithoutLosingReasonText() throws Exception {
		Path dir = Files.createTempDirectory("capillary-boundaries");
		TrackingTimeline source = new TrackingTimeline();
		source.addManual(23, "zoom; upper corners became loose");
		source.put(new TrackingBoundary(85, TrackingBoundary.Origin.AUTOMATIC,
				TrackingBoundary.Status.SUGGESTED, "large residual", 4.25));
		assertTrue(TrackingTimelinePersistence.save(source, dir.toString()));

		TrackingTimeline loaded = new TrackingTimeline();
		assertTrue(TrackingTimelinePersistence.load(loaded, dir.toString()));
		assertEquals(2, loaded.getBoundaries().size());
		assertEquals("zoom; upper corners became loose", loaded.get(23).getReason());
		assertEquals(4.25, loaded.get(85).getScore(), 1e-9);
	}
}
