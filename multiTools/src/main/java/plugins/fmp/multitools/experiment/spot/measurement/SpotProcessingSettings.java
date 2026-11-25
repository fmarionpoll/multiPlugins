package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.Objects;

/** Explicit, immutable settings; safe defaults retain gaps and do not normalize. */
public final class SpotProcessingSettings {
    public enum GapPolicy { NONE, LINEAR, HOLD_PREVIOUS }
    public enum BaselinePolicy { NONE, FIRST_VALID, EXPLICIT }

    public static final SpotProcessingSettings OBSERVATIONS = new SpotProcessingSettings(
            GapPolicy.NONE, 0, 0, 0, BaselinePolicy.NONE, Double.NaN, Double.NaN, "", false);

    public final GapPolicy gapPolicy;
    /** Maximum duration between the two bracket observations, in seconds. */
    public final double maximumGapSeconds;
    /** Absolute endpoint tolerance in the quantity's original units; used only for display metadata. */
    public final double noiseTolerance;
    /** Median radius in samples; zero disables smoothing. Gaps remain boundaries. */
    public final int medianRadius;
    public final BaselinePolicy baselinePolicy;
    public final double explicitBaseline;
    public final double explicitBaselineTimeSeconds;
    public final String baselineSource;
    public final boolean baselineReviewed;

    public SpotProcessingSettings(GapPolicy gapPolicy, double maximumGapSeconds, double noiseTolerance,
            int medianRadius, BaselinePolicy baselinePolicy, double explicitBaseline,
            double explicitBaselineTimeSeconds, String baselineSource, boolean baselineReviewed) {
        this.gapPolicy = Objects.requireNonNull(gapPolicy);
        this.baselinePolicy = Objects.requireNonNull(baselinePolicy);
        this.baselineSource = Objects.requireNonNull(baselineSource);
        if (!Double.isFinite(maximumGapSeconds) || maximumGapSeconds < 0
                || !Double.isFinite(noiseTolerance) || noiseTolerance < 0 || medianRadius < 0) {
            throw new IllegalArgumentException("Gap duration, noise tolerance and median radius must be nonnegative");
        }
        if (baselinePolicy == BaselinePolicy.EXPLICIT && (!Double.isFinite(explicitBaseline)
                || explicitBaseline <= 0 || !Double.isFinite(explicitBaselineTimeSeconds)
                || baselineSource.trim().isEmpty())) {
            throw new IllegalArgumentException("An explicit baseline needs a positive value, time and source");
        }
        this.maximumGapSeconds = maximumGapSeconds;
        this.noiseTolerance = noiseTolerance;
        this.medianRadius = medianRadius;
        this.explicitBaseline = explicitBaseline;
        this.explicitBaselineTimeSeconds = explicitBaselineTimeSeconds;
        this.baselineReviewed = baselineReviewed;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SpotProcessingSettings)) { return false; }
        SpotProcessingSettings s = (SpotProcessingSettings) other;
        return gapPolicy == s.gapPolicy && baselinePolicy == s.baselinePolicy
                && Double.compare(maximumGapSeconds, s.maximumGapSeconds) == 0
                && Double.compare(noiseTolerance, s.noiseTolerance) == 0 && medianRadius == s.medianRadius
                && Double.compare(explicitBaseline, s.explicitBaseline) == 0
                && Double.compare(explicitBaselineTimeSeconds, s.explicitBaselineTimeSeconds) == 0
                && baselineSource.equals(s.baselineSource) && baselineReviewed == s.baselineReviewed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gapPolicy, maximumGapSeconds, noiseTolerance, medianRadius, baselinePolicy,
                explicitBaseline, explicitBaselineTimeSeconds, baselineSource, baselineReviewed);
    }
}
