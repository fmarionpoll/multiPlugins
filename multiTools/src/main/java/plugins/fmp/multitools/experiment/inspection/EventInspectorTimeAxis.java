package plugins.fmp.multitools.experiment.inspection;

import java.util.Arrays;

/** Read-only column clock for one-frame-per-column kymographs. Indices are zero based. */
public final class EventInspectorTimeAxis {
    private final long[] timesMs;
    private final int firstFrame;
    private final boolean acquisitionTimes;

    private EventInspectorTimeAxis(long[] times, int first, boolean actual) {
        timesMs = times;
        firstFrame = first;
        acquisitionTimes = actual;
    }

    public static EventInspectorTimeAxis create(int width, int subsample, long[] frameTimes,
            long firstColumnMs, long nominalStepMs) {
        if (width < 1 || subsample != 1)
            throw new IllegalArgumentException("Requires one frame per column and a nonempty kymograph.");
        if (frameTimes != null && frameTimes.length > 0) {
            for (int i = 1; i < frameTimes.length; i++)
                if (frameTimes[i] <= frameTimes[i - 1])
                    throw new IllegalArgumentException("Duplicate or unordered frame timestamps: mapping is ambiguous.");
            int first = frameTimes.length == width ? 0 : Arrays.binarySearch(frameTimes, firstColumnMs);
            if (first < 0 || first + width > frameTimes.length)
                throw new IllegalArgumentException("Kymograph window cannot be matched to frame timestamps.");
            return new EventInspectorTimeAxis(Arrays.copyOfRange(frameTimes, first, first + width), first, true);
        }
        if (nominalStepMs <= 0)
            throw new IllegalArgumentException("No frame timestamps or valid nominal sampling interval.");
        long[] times = new long[width];
        for (int i = 0; i < width; i++)
            times[i] = firstColumnMs + i * nominalStepMs;
        // No verified camera frame correspondence when only nominal timing is known.
        return new EventInspectorTimeAxis(times, -1, false);
    }

    public int size() { return timesMs.length; }
    public long timeMs(int column) { return timesMs[column]; }
    public double minutes(int column) { return timeMs(column) / 60000.0; }
    public boolean hasAcquisitionTimes() { return acquisitionTimes; }
    public int cameraFrame(int column) { return firstFrame < 0 ? -1 : firstFrame + column; }

    public int nearestColumn(double minutes) {
        long value = Math.round(minutes * 60000.0);
        int i = Arrays.binarySearch(timesMs, value);
        if (i >= 0) return i;
        i = -i - 1;
        if (i == 0) return 0;
        if (i == size()) return size() - 1;
        return value - timesMs[i - 1] <= timesMs[i] - value ? i - 1 : i;
    }
}
