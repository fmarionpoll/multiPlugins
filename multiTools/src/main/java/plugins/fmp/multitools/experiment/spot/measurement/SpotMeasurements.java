package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-spot method results. Replace an immutable result to publish a new acquisition
 * or manual decision. Derived results are lazy and cached; replacement invalidates
 * only that method. Legacy chart/export containers are independent compatibility data.
 */
public final class SpotMeasurements {
    private final Map<SpotMeasurementMethod, SpotMethodResult> results = new EnumMap<>(SpotMeasurementMethod.class);
    private final Map<Key, SpotDerivedSeries> cache = new LinkedHashMap<Key, SpotDerivedSeries>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;
        @Override protected boolean removeEldestEntry(Map.Entry<Key, SpotDerivedSeries> eldest) {
            return size() > 32;
        }
    };

    public synchronized void put(SpotMethodResult result) {
        Objects.requireNonNull(result);
        results.put(result.getMethod(), result);
        cache.keySet().removeIf(key -> key.method == result.getMethod());
    }

    public synchronized SpotMethodResult get(SpotMeasurementMethod method) { return results.get(method); }

    public synchronized Map<SpotMeasurementMethod, SpotMethodResult> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(results));
    }

    public synchronized void remove(SpotMeasurementMethod method) {
        results.remove(method);
        cache.keySet().removeIf(key -> key.method == method);
    }

    public synchronized void clear() { results.clear(); cache.clear(); }

    public void copyFrom(SpotMeasurements source) {
        Map<SpotMeasurementMethod, SpotMethodResult> snapshot = source.snapshot();
        synchronized (this) {
            results.clear();
            results.putAll(snapshot);
            cache.clear();
        }
    }

    public synchronized SpotDerivedSeries derive(SpotMeasurementMethod method, String quantityId,
            SpotProcessingSettings settings) {
        Objects.requireNonNull(settings);
        SpotMethodResult result = results.get(method);
        if (result == null) { throw new IllegalArgumentException("No measurements for " + method); }
        Key key = new Key(method, quantityId, settings);
        SpotDerivedSeries derived = cache.get(key);
        if (derived == null) {
            derived = SpotSeriesProcessor.process(result, quantityId, settings);
            cache.put(key, derived);
        }
        return derived;
    }

    private static final class Key {
        final SpotMeasurementMethod method;
        final String quantityId;
        final SpotProcessingSettings settings;
        Key(SpotMeasurementMethod method, String quantityId, SpotProcessingSettings settings) {
            this.method = method;
            this.quantityId = Objects.requireNonNull(quantityId);
            this.settings = settings;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) { return false; }
            Key key = (Key) other;
            return method == key.method && quantityId.equals(key.quantityId) && settings.equals(key.settings);
        }
        @Override public int hashCode() { return Objects.hash(method, quantityId, settings); }
    }
}
