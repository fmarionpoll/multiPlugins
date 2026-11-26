package plugins.fmp.multitools.experiment.spot.fraction;

import java.util.*;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

/** Saved calculation decisions for one experiment/acquisition directory; no global defaults are imposed on saved work. */
public final class SpotFractionPlan {
    public enum Tolerance { PERCENT_REFERENCE, ABSOLUTE, ROBUST_NOISE }
    public enum Illumination { NONE, CAGE, EXPERIMENT }
    public enum ReferenceKind { INITIAL, DELAYED, EXPLICIT, BORROWED, PRECONSUMED, FAILED_DEPOSIT, OBSERVED_MAXIMUM, BASELINE_MEDIAN }
    public enum ReferenceEstimation {
        INITIAL_OBSERVATION("Initial valid observation"), OBSERVED_MAXIMUM("Maximum valid observation (hmax / signal max)"),
        BASELINE_MEDIAN("Median of baseline period (at least 3 valid samples)");
        private final String label;
        ReferenceEstimation(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static final class Key {
        public final SpotMeasurementMethod method;
        public final String quantity, spot;
        public Key(SpotMeasurementMethod method, String quantity, String spot) {
            this.method = Objects.requireNonNull(method); this.quantity = Objects.requireNonNull(quantity);
            this.spot = Objects.requireNonNull(spot);
            if (quantity.trim().isEmpty()) throw new IllegalArgumentException("A measured quantity is required");
        }
        public Key forSpot(String name) { return new Key(method, quantity, name); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o; return method == k.method && quantity.equals(k.quantity) && spot.equals(k.spot);
        }
        @Override public int hashCode() { return Objects.hash(method, quantity, spot); }
    }

    public static final class Settings {
        public static final Settings DEFAULT = new Settings(GapPolicy.LINEAR, GapPolicy.LINEAR, TailPolicy.KEEP_MISSING,
                600, Tolerance.PERCENT_REFERENCE, 8, 0, Illumination.NONE);
        public final GapPolicy similarGap, changingGap;
        public final TailPolicy tail;
        public final double maximumGapSeconds, tolerance;
        public final Tolerance toleranceMode;
        public final int medianRadius;
        public final Illumination illumination;
        public final ReferenceEstimation referenceEstimation;
        public final double baselineStartSeconds, baselineEndSeconds;
        public Settings(GapPolicy similar, GapPolicy changing, TailPolicy tail, double maxSeconds,
                Tolerance mode, double tolerance, int medianRadius, Illumination illumination) {
            this(similar, changing, tail, maxSeconds, mode, tolerance, medianRadius, illumination,
                    ReferenceEstimation.INITIAL_OBSERVATION, 600, 1200);
        }
        public Settings(GapPolicy similar, GapPolicy changing, TailPolicy tail, double maxSeconds,
                Tolerance mode, double tolerance, int medianRadius, Illumination illumination,
                ReferenceEstimation referenceEstimation, double baselineStartSeconds, double baselineEndSeconds) {
            this.similarGap = Objects.requireNonNull(similar); this.changingGap = Objects.requireNonNull(changing);
            this.tail = Objects.requireNonNull(tail); this.toleranceMode = Objects.requireNonNull(mode);
            this.illumination = Objects.requireNonNull(illumination);
            if (!Double.isFinite(maxSeconds) || maxSeconds < 0 || !Double.isFinite(tolerance) || tolerance < 0 || medianRadius < 0)
                throw new IllegalArgumentException("Gap duration, tolerance and smoothing radius must be nonnegative");
            this.maximumGapSeconds = maxSeconds; this.tolerance = tolerance; this.medianRadius = medianRadius;
            this.referenceEstimation = Objects.requireNonNull(referenceEstimation);
            if (!Double.isFinite(baselineStartSeconds) || baselineStartSeconds < 0
                    || !Double.isFinite(baselineEndSeconds) || baselineEndSeconds <= baselineStartSeconds)
                throw new IllegalArgumentException("Baseline end must be later than its nonnegative start");
            this.baselineStartSeconds = baselineStartSeconds; this.baselineEndSeconds = baselineEndSeconds;
        }
    }

    public static final class Reference {
        public final ReferenceKind kind;
        public final double value, timeSeconds;
        public final String source, fingerprint;
        public final boolean approved;
        public final List<String> donors;
        public Reference(ReferenceKind kind, double value, double seconds, String source, boolean approved,
                String fingerprint, List<String> donors) {
            this.kind = Objects.requireNonNull(kind); this.source = Objects.requireNonNull(source);
            this.fingerprint = Objects.requireNonNull(fingerprint); this.approved = approved;
            if (kind != ReferenceKind.FAILED_DEPOSIT && (!Double.isFinite(value) || value <= 0
                    || !Double.isFinite(seconds) || source.trim().isEmpty()))
                throw new IllegalArgumentException("A reference needs a positive value, time and source");
            this.value = value; this.timeSeconds = seconds;
            this.donors = Collections.unmodifiableList(new ArrayList<>(donors));
        }
    }

    public static final class Review {
        public final Key key;
        public final String reason;
        public Review(Key key, String reason) { this.key = Objects.requireNonNull(key); this.reason = Objects.requireNonNull(reason); }
    }

    /** A plan is owned by one preview/batch task. Copy before changing a displayed or saved plan. */
    public final Map<Key, Settings> settings = new LinkedHashMap<>();
    public final Map<Key, Reference> references = new LinkedHashMap<>();
    public final List<Review> review = new ArrayList<>();

    public Settings settingsFor(Key key) {
        Settings s = settings.get(key);
        return s != null ? s : settings.getOrDefault(key.forSpot(""), Settings.DEFAULT);
    }
    public SpotFractionPlan copy() {
        SpotFractionPlan plan = new SpotFractionPlan();
        plan.settings.putAll(settings); plan.references.putAll(references); plan.review.addAll(review); return plan;
    }
}
