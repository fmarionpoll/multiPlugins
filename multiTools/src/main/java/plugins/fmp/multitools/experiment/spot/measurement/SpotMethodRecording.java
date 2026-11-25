package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acquisition-side builder. Unrecorded samples stay unknown/failed, never zero.
 * Publish build() through SpotMeasurements.put() only after acquisition completes.
 * A recording is owned by one acquisition task, not shared between threads.
 */
public final class SpotMethodRecording {
    private final SpotMeasurementMethod method;
    private final double[] timesSeconds;
    private final double[] flyEvidence;
    private final String flyEvidenceUnit;
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private final Map<String, Samples> quantities = new LinkedHashMap<>();

    public SpotMethodRecording(SpotMeasurementMethod method, double[] timesSeconds, String flyEvidenceUnit) {
        this.method = method;
        this.timesSeconds = timesSeconds.clone();
        this.flyEvidenceUnit = flyEvidenceUnit;
        flyEvidence = new double[timesSeconds.length];
        Arrays.fill(flyEvidence, Double.NaN);
    }

    public SpotMethodRecording parameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    public SpotMethodRecording quantity(String id, String unit) {
        if (quantities.containsKey(id)) { throw new IllegalArgumentException("Duplicate quantity: " + id); }
        quantities.put(id, new Samples(unit, timesSeconds.length));
        return this;
    }

    public void recordFlyEvidence(int sample, double evidence) { flyEvidence[sample] = evidence; }

    public void record(String id, int sample, double rawValue, int acquisitionFlags) {
        Samples samples = quantities.get(id);
        if (samples == null) { throw new IllegalArgumentException("Unknown quantity: " + id); }
        samples.values[sample] = rawValue;
        samples.flags[sample] = acquisitionFlags;
    }

    public SpotMethodResult build() {
        List<SpotMeasuredQuantity> built = new ArrayList<>();
        for (Map.Entry<String, Samples> entry : quantities.entrySet()) {
            Samples s = entry.getValue();
            built.add(new SpotMeasuredQuantity(entry.getKey(), s.unit, s.values, s.flags));
        }
        return new SpotMethodResult(method, timesSeconds, flyEvidence, flyEvidenceUnit, parameters, built);
    }

    private static final class Samples {
        final String unit;
        final double[] values;
        final int[] flags;
        Samples(String unit, int size) {
            this.unit = unit;
            values = new double[size];
            Arrays.fill(values, Double.NaN);
            flags = new int[size];
            Arrays.fill(flags, SpotSampleFlags.DETECTION_FAILED);
        }
    }
}
