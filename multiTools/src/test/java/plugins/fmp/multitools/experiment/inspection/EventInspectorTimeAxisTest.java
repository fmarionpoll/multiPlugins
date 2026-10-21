package plugins.fmp.multitools.experiment.inspection;

import static org.junit.Assert.*;
import org.junit.Test;

public class EventInspectorTimeAxisTest {
    @Test public void irregularFramesUseAcquisitionClock() {
        EventInspectorTimeAxis axis = EventInspectorTimeAxis.create(4, 1,
                new long[] { 0, 47000, 125000, 190000 }, 0, 60000);
        assertEquals(125000, axis.timeMs(2));
        assertEquals(2, axis.nearestColumn(2.0));
        assertEquals(3, axis.nearestColumn(20));
        assertEquals(0, axis.nearestColumn(-2));
        assertTrue(axis.hasAcquisitionTimes());
    }

    @Test public void restrictedWindowRetainsFrameOffset() {
        EventInspectorTimeAxis axis = EventInspectorTimeAxis.create(2, 1,
                new long[] { 0, 47000, 125000, 190000 }, 125000, 60000);
        assertEquals(2, axis.cameraFrame(0));
        assertEquals(3, axis.cameraFrame(1));
        assertEquals(190000, axis.timeMs(1));
    }

    @Test public void nominalClockDoesNotInventCameraFrameMapping() {
        EventInspectorTimeAxis axis = EventInspectorTimeAxis.create(4, 1, null, 120000, 30000);
        assertFalse(axis.hasAcquisitionTimes());
        assertEquals(-1, axis.cameraFrame(2));
        assertEquals(180000, axis.timeMs(2));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsDownsampling() {
        EventInspectorTimeAxis.create(4, 2, null, 0, 60000);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsDuplicateTimes() {
        EventInspectorTimeAxis.create(3, 1, new long[] { 0, 60000, 60000 }, 0, 60000);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnorderedTimes() {
        EventInspectorTimeAxis.create(3, 1, new long[] { 0, 60000, 30000 }, 0, 60000);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsAmbiguousWindow() {
        EventInspectorTimeAxis.create(2, 1, new long[] { 0, 60000, 120000 }, 30000, 60000);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMoreColumnsThanFrames() {
        EventInspectorTimeAxis.create(4, 1, new long[] { 0, 60000, 120000 }, 0, 60000);
    }
}
