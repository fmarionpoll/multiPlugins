package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Derived values retain observation flags and distinguish estimates from observations. */
public final class SpotDerivedSeries {
    public enum GapKind { INTERNAL, LEADING, TRAILING, ENTIRE_RECORDING }

    public static final class Gap {
        public final int firstMissing;
        public final int lastMissing;
        public final GapKind kind;
        /** True only for pure occlusions with reliable brackets within the specified tolerance and duration. */
        public final boolean connectableForDisplay;

        Gap(int firstMissing, int lastMissing, GapKind kind, boolean connectableForDisplay) {
            this.firstMissing = firstMissing;
            this.lastMissing = lastMissing;
            this.kind = kind;
            this.connectableForDisplay = connectableForDisplay;
        }
    }

    private final double[] values;
    private final int[] observationFlags;
    private final boolean[] estimated;
    private final List<Gap> gaps;
    public final double baselineValue;
    public final double baselineTimeSeconds;
    public final String baselineSource;
    public final boolean reviewRequired;
    public final boolean smoothed;
    public final SpotProcessingSettings settings;

    SpotDerivedSeries(double[] values, int[] flags, boolean[] estimated, List<Gap> gaps,
            double baselineValue, double baselineTimeSeconds, String baselineSource,
            boolean reviewRequired, SpotProcessingSettings settings) {
        this.values = values.clone();
        this.observationFlags = flags.clone();
        this.estimated = estimated.clone();
        this.gaps = Collections.unmodifiableList(new ArrayList<>(gaps));
        this.baselineValue = baselineValue;
        this.baselineTimeSeconds = baselineTimeSeconds;
        this.baselineSource = baselineSource;
        this.reviewRequired = reviewRequired;
        this.smoothed = settings.medianRadius > 0;
        this.settings = settings;
    }

    public double[] getValues() { return values.clone(); }
    public int[] getObservationFlags() { return observationFlags.clone(); }
    public boolean[] getEstimatedSamples() { return estimated.clone(); }
    public List<Gap> getGaps() { return gaps; }
}
