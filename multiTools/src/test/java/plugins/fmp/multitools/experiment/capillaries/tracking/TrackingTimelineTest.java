package plugins.fmp.multitools.experiment.capillaries.tracking;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class TrackingTimelineTest {
	@Test
	public void boundaryAtTStartsTheNewSegmentAtT() {
		TrackingTimeline timeline = new TrackingTimeline();
		timeline.addManual(127, "camera jump");
		List<TrackingSegment> segments = timeline.segments(399);
		assertEquals(2, segments.size());
		assertEquals(0, segments.get(0).getStartFrame());
		assertEquals(126, segments.get(0).getEndFrame());
		assertEquals(127, segments.get(1).getStartFrame());
		assertEquals(399, segments.get(1).getEndFrame());
	}

	@Test
	public void movingAndDeletingBoundariesOnlyChangesDerivedSegments() {
		TrackingTimeline timeline = new TrackingTimeline();
		timeline.addManual(100, "first");
		timeline.addManual(200, "second");
		assertNotNull(timeline.move(100, 120));
		assertNull(timeline.get(100));
		assertNotNull(timeline.get(120));
		timeline.remove(200);
		List<TrackingSegment> segments = timeline.segments(299);
		assertEquals(2, segments.size());
		assertEquals(119, segments.get(0).getEndFrame());
		assertEquals(120, segments.get(1).getStartFrame());
	}

	@Test(expected = IllegalArgumentException.class)
	public void frameZeroCannotBeABoundary() {
		new TrackingBoundary(0, TrackingBoundary.Origin.MANUAL,
				TrackingBoundary.Status.CONFIRMED, "", Double.NaN);
	}
}
