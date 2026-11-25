package plugins.fmp.multitools.series;

/** Optional reporting context for measurement workers. Reporting never changes detection or saved measurements. */
public final class MeasurementProgress {
    @FunctionalInterface
    public interface Listener {
        void update(String phase, String unit, int completed, int total);
    }
    private static final Listener NONE = (phase, unit, completed, total) -> { };
    private static final ThreadLocal<Listener> CURRENT = new ThreadLocal<>();
    private MeasurementProgress() { }

    /** Capture on the batch thread before starting parallel frame workers. */
    public static Listener current() {
        Listener listener = CURRENT.get(); return listener == null ? NONE : listener;
    }
    public static void phase(String phase) { current().update(phase, null, 0, 0); }
    public static void images(int completed, int total) { current().update(null, "Image", completed, total); }
    public static Scope install(Listener listener) { return new Scope(listener); }

    public static final class Scope implements AutoCloseable {
        private final Listener previous;
        private Scope(Listener listener) { previous = CURRENT.get(); CURRENT.set(listener); }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    static String message(int ordinal, int batchSize, int listIndex, int listSize, String name,
            String phase, String unit, int completed, int total) {
        String text = "Experiment " + ordinal + "/" + batchSize + " (list " + listIndex + "/" + listSize + ")";
        if (name != null && !name.isEmpty()) text += " — " + name;
        if (phase != null && !phase.isEmpty()) text += " — " + phase;
        if (total > 0) text += " — " + unit + " " + Math.min(Math.max(0, completed), total) + "/" + total;
        return text;
    }
}
