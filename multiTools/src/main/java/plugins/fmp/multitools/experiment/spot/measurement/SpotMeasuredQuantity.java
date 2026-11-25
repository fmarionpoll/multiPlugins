package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.Objects;

/** Immutable acquisition values. Manual rejection never changes acquisition flags or values. */
public final class SpotMeasuredQuantity {
    private final String id;
    private final String unit;
    private final double[] rawValues;
    private final int[] acquisitionFlags;
    private final boolean[] manualRejections;

    public SpotMeasuredQuantity(String id, String unit, double[] rawValues, int[] acquisitionFlags) {
        this(id, unit, rawValues, acquisitionFlags, new boolean[rawValues.length]);
    }

    public SpotMeasuredQuantity(String id, String unit, double[] rawValues, int[] acquisitionFlags,
            boolean[] manualRejections) {
        this.id = Objects.requireNonNull(id, "quantity id");
        this.unit = Objects.requireNonNull(unit, "unit");
        if (id.trim().isEmpty() || rawValues.length != acquisitionFlags.length
                || rawValues.length != manualRejections.length) {
            throw new IllegalArgumentException("Quantity id and parallel sample arrays are required");
        }
        this.rawValues = rawValues.clone();
        this.acquisitionFlags = acquisitionFlags.clone();
        this.manualRejections = manualRejections.clone();
        for (int i = 0; i < rawValues.length; i++) {
            if (Double.isInfinite(rawValues[i]) || acquisitionFlags[i] < 0) {
                throw new IllegalArgumentException("Use NaN for unavailable values and nonnegative flags");
            }
        }
    }

    public String getId() { return id; }
    public String getUnit() { return unit; }
    public int size() { return rawValues.length; }
    public double[] getRawValues() { return rawValues.clone(); }
    public int[] getAcquisitionFlags() { return acquisitionFlags.clone(); }
    public boolean[] getManualRejections() { return manualRejections.clone(); }

    public int[] getEffectiveFlags() {
        int[] flags = acquisitionFlags.clone();
        for (int i = 0; i < flags.length; i++) {
            if (!Double.isFinite(rawValues[i]) && flags[i] == 0) {
                flags[i] |= SpotSampleFlags.DETECTION_FAILED;
            }
            if (manualRejections[i]) {
                flags[i] |= SpotSampleFlags.MANUALLY_REJECTED;
            }
        }
        return flags;
    }

    public SpotMeasuredQuantity withManualRejection(int sample, boolean rejected) {
        boolean[] decisions = manualRejections.clone();
        decisions[sample] = rejected;
        return new SpotMeasuredQuantity(id, unit, rawValues, acquisitionFlags, decisions);
    }
}
