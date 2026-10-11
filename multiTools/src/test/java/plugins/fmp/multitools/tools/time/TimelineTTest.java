package plugins.fmp.multitools.tools.time;

import static org.junit.Assert.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TimelineTTest {
    @Test public void lookupIsPiecewiseConstantAndIndependentOfTraversalDirection() {
        TimelineT<String> timeline = new TimelineT<>(value -> value);
        assertNull(timeline.valueAt(10, TimelineT.BeforeFirst.FIRST));
        timeline.put(80, "last");
        timeline.put(25, "middle");
        timeline.put(0, "first");
        assertEquals(Arrays.asList(0L, 25L, 80L), new ArrayList<>(timeline.snapshot().keySet()));
        assertEquals("first", timeline.valueAt(24, TimelineT.BeforeFirst.NONE));
        assertEquals("middle", timeline.valueAt(25, TimelineT.BeforeFirst.NONE));
        assertEquals("last", timeline.valueAt(Long.MAX_VALUE, TimelineT.BeforeFirst.NONE));
        assertEquals("first", timeline.valueAt(0, TimelineT.BeforeFirst.NONE));
        assertNull(timeline.startAt(-1));
        assertNull(timeline.valueAt(-1, TimelineT.BeforeFirst.NONE));
        assertEquals("first", timeline.valueAt(-1, TimelineT.BeforeFirst.FIRST));
        timeline.put(25, "replacement");
        assertEquals(3, timeline.snapshot().size());
        assertEquals("replacement", timeline.startingAt(25));
        assertNull(timeline.startingAt(26));
        assertFalse(timeline.removeLater(0));
        assertTrue(timeline.removeLater(25));
        assertEquals("first", timeline.valueAt(79, TimelineT.BeforeFirst.NONE));
        assertEquals("last", timeline.valueAt(80, TimelineT.BeforeFirst.NONE));
        timeline.removeRange(80, Long.MAX_VALUE);
        assertEquals(1, timeline.snapshot().size());
        timeline.clear();
        assertTrue(timeline.isEmpty());
    }

    @Test public void firstStoredReferenceIsProtectedEvenWhenItStartsAfterZero() {
        TimelineT<String> timeline = new TimelineT<>(value -> value);
        timeline.put(10, "reference");
        timeline.put(20, "later");
        assertFalse(timeline.removeLater(10));
        assertNull(timeline.startAt(0));
        assertEquals(Long.valueOf(10), timeline.startAt(0, TimelineT.BeforeFirst.FIRST));
        assertTrue(timeline.removeLater(20));
    }

    @Test public void mutableCoordinatesAreCopiedOnInsertionReadAndSnapshot() {
        TimelineT<Point2D> timeline = new TimelineT<>(point -> new Point2D.Double(point.getX(), point.getY()));
        Point2D input = new Point2D.Double(10, 20);
        timeline.put(0, input);
        input.setLocation(99, 99);
        timeline.valueAt(0, TimelineT.BeforeFirst.NONE).setLocation(88, 88);
        timeline.snapshot().get(0L).setLocation(77, 77);
        assertEquals(10, timeline.startingAt(0).getX(), 0);
        try {
            timeline.snapshot().clear();
            fail("Snapshot map must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    private static class Value {
        long start;
        Value(long start) { this.start = start; }
    }

    @Test public void liveAdapterSeesExternalListAndStartEditsWithoutStaleCache() {
        List<Value> values = new ArrayList<>();
        TimelineT<Value> timeline = TimelineT.wrapOrderedList(() -> values, value -> value.start, value -> value);
        Value first = new Value(0), later = new Value(25);
        timeline.put(25, later);
        timeline.put(0, first);
        assertSame(later, timeline.valueAt(30, TimelineT.BeforeFirst.NONE));
        later.start = 40;
        assertSame(first, timeline.valueAt(30, TimelineT.BeforeFirst.NONE));
        assertSame(later, timeline.valueAt(40, TimelineT.BeforeFirst.NONE));
        values.remove(0);
        assertNull(timeline.valueAt(30, TimelineT.BeforeFirst.NONE));
        assertSame(later, timeline.valueAt(40, TimelineT.BeforeFirst.NONE));
        try {
            timeline.put(50, new Value(60));
            fail("Mismatched legacy starts must be rejected");
        } catch (IllegalArgumentException expected) { }
        assertEquals(1, values.size());
    }
}
