package plugins.fmp.multitools.tools.time;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * Ordered, piecewise-constant values indexed by frame. A value applies from its
 * start (inclusive) to the next start (exclusive); no interpolation is performed.
 * Storage and rendering/persistence are independent. Mutable values require an
 * explicit copier; identity copying is suitable for legacy live entry adapters.
 */
public final class TimelineT<T> {
    public enum BeforeFirst { NONE, FIRST }

    private static final class Entry<T> {
        final long start;
        final T value;
        Entry(long start, T value) { this.start = start; this.value = value; }
    }

    private final List<Entry<T>> entries;
    private final UnaryOperator<T> copy;

    public TimelineT(UnaryOperator<T> copy) {
        this(new ArrayList<>(), copy);
    }

    private TimelineT(List<Entry<T>> entries, UnaryOperator<T> copy) {
        this.entries = entries;
        this.copy = Objects.requireNonNull(copy, "copier");
    }

    /**
     * Live adapter for an existing ordered list whose values contain their start.
     * External list edits and start changes are visible immediately. The caller
     * must keep starts sorted, as required by the legacy AlongT list contract.
     * External mutations must not run concurrently with timeline operations.
     */
    public static <T> TimelineT<T> wrapOrderedList(Supplier<List<T>> list,
            ToLongFunction<T> start, UnaryOperator<T> copy) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(start, "start");
        List<Entry<T>> adapter = new AbstractList<Entry<T>>() {
            @Override public int size() { return list.get().size(); }
            @Override public Entry<T> get(int index) {
                T value = list.get().get(index);
                return new Entry<>(start.applyAsLong(value), value);
            }
            private T checked(Entry<T> entry) {
                if (start.applyAsLong(entry.value) != entry.start)
                    throw new IllegalArgumentException("Value start must match timeline start");
                return entry.value;
            }
            @Override public void add(int index, Entry<T> entry) { list.get().add(index, checked(entry)); }
            @Override public Entry<T> set(int index, Entry<T> entry) {
                Entry<T> previous = get(index);
                list.get().set(index, checked(entry));
                return previous;
            }
            @Override public Entry<T> remove(int index) {
                Entry<T> previous = get(index);
                list.get().remove(index);
                return previous;
            }
            @Override public void clear() { list.get().clear(); }
        };
        return new TimelineT<>(adapter, copy);
    }

    /** Index after the last entry whose start is <= frame. */
    private int upperBound(long frame) {
        int low = 0, high = entries.size();
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (entries.get(mid).start <= frame) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    public synchronized boolean isEmpty() { return entries.isEmpty(); }

    public synchronized Long startAt(long frame) {
        return startAt(frame, BeforeFirst.NONE);
    }

    public synchronized Long startAt(long frame, BeforeFirst beforeFirst) {
        int index = upperBound(frame) - 1;
        if (index < 0 && beforeFirst == BeforeFirst.FIRST && !entries.isEmpty()) index = 0;
        return index < 0 ? null : entries.get(index).start;
    }

    public synchronized T valueAt(long frame, BeforeFirst beforeFirst) {
        int index = upperBound(frame) - 1;
        if (index < 0 && beforeFirst == BeforeFirst.FIRST && !entries.isEmpty()) index = 0;
        return index < 0 ? null : copy.apply(entries.get(index).value);
    }

    public synchronized T startingAt(long start) {
        int index = upperBound(start) - 1;
        return index < 0 || entries.get(index).start != start ? null : copy.apply(entries.get(index).value);
    }

    public synchronized void put(long start, T value) {
        if (start < 0) throw new IllegalArgumentException("Frame start must be non-negative");
        Entry<T> entry = new Entry<>(start, copy.apply(Objects.requireNonNull(value, "value")));
        int index = upperBound(start);
        if (index > 0 && entries.get(index - 1).start == start) entries.set(index - 1, entry);
        else entries.add(index, entry);
    }

    public synchronized boolean remove(long start) {
        int index = upperBound(start) - 1;
        if (index < 0 || entries.get(index).start != start) return false;
        entries.remove(index);
        return true;
    }

    /** Protect frame zero and the first stored reference, even for old nonzero starts. */
    public synchronized boolean removeLater(long start) {
        int index = upperBound(start) - 1;
        return start > 0 && index > 0 && entries.get(index).start == start && remove(start);
    }

    public synchronized void removeRange(long fromInclusive, long toInclusive) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            long start = entries.get(i).start;
            if (start >= fromInclusive && start <= toInclusive) entries.remove(i);
        }
    }

    public synchronized Map<Long, T> snapshot() {
        Map<Long, T> result = new LinkedHashMap<>();
        for (Entry<T> entry : entries) result.put(entry.start, copy.apply(entry.value));
        return Collections.unmodifiableMap(result);
    }

    public synchronized void clear() { entries.clear(); }
}
