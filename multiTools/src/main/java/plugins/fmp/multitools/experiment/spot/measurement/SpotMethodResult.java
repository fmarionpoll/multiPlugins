package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of one acquisition method on one spot. Times are elapsed seconds
 * from the experiment origin, not sample indices. Fly evidence has explicitly named
 * units (for example pixels or occupancy fraction); NaN means unknown, never absent.
 * Instances can safely be shared by spot copies and processing caches.
 */
public final class SpotMethodResult {
    private final SpotMeasurementMethod method;
    private final double[] timesSeconds;
    private final double[] flyEvidence;
    private final String flyEvidenceUnit;
    private final Map<String, String> parameters;
    private final Map<String, SpotMeasuredQuantity> quantities;

    public SpotMethodResult(SpotMeasurementMethod method, double[] timesSeconds,
            double[] flyEvidence, String flyEvidenceUnit, Map<String, String> parameters,
            Collection<SpotMeasuredQuantity> quantities) {
        this.method = Objects.requireNonNull(method, "method");
        this.flyEvidenceUnit = Objects.requireNonNull(flyEvidenceUnit, "fly evidence unit");
        if (timesSeconds.length != flyEvidence.length) {
            throw new IllegalArgumentException("Fly evidence must share the method timeline");
        }
        this.timesSeconds = timesSeconds.clone();
        this.flyEvidence = flyEvidence.clone();
        for (int i = 0; i < timesSeconds.length; i++) {
            if (!Double.isFinite(timesSeconds[i]) || (i > 0 && timesSeconds[i] <= timesSeconds[i - 1])) {
                throw new IllegalArgumentException("Times must be finite and strictly increasing");
            }
            if (Double.isInfinite(flyEvidence[i]) || flyEvidence[i] < 0) {
                throw new IllegalArgumentException("Fly evidence must be nonnegative or NaN");
            }
        }
        LinkedHashMap<String, String> copiedParameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            copiedParameters.put(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
        }
        this.parameters = Collections.unmodifiableMap(copiedParameters);
        LinkedHashMap<String, SpotMeasuredQuantity> copiedQuantities = new LinkedHashMap<>();
        for (SpotMeasuredQuantity quantity : quantities) {
            if (quantity.size() != timesSeconds.length || copiedQuantities.put(quantity.getId(), quantity) != null) {
                throw new IllegalArgumentException("Quantities must have unique ids and share the method timeline");
            }
        }
        if (copiedQuantities.isEmpty()) {
            throw new IllegalArgumentException("At least one measured quantity is required");
        }
        this.quantities = Collections.unmodifiableMap(copiedQuantities);
    }

    public SpotMeasurementMethod getMethod() { return method; }
    public double[] getTimesSeconds() { return timesSeconds.clone(); }
    public double[] getFlyEvidence() { return flyEvidence.clone(); }
    public String getFlyEvidenceUnit() { return flyEvidenceUnit; }
    public Map<String, String> getParameters() { return parameters; }
    public Map<String, SpotMeasuredQuantity> getQuantities() { return quantities; }

    public SpotMeasuredQuantity getQuantity(String id) {
        SpotMeasuredQuantity quantity = quantities.get(id);
        if (quantity == null) {
            throw new IllegalArgumentException("Unknown measured quantity: " + id);
        }
        return quantity;
    }

    public SpotMethodResult withQuantity(SpotMeasuredQuantity quantity) {
        LinkedHashMap<String, SpotMeasuredQuantity> replacements = new LinkedHashMap<>(quantities);
        replacements.put(quantity.getId(), quantity);
        return new SpotMethodResult(method, timesSeconds, flyEvidence, flyEvidenceUnit, parameters,
                replacements.values());
    }
}
