package plugins.fmp.multitools.experiment.spot.measurement;

/** Bit flags describing rejected observations; zero means usable. */
public final class SpotSampleFlags {
    public static final int OCCLUDED = 1;
    public static final int MISSING_IMAGE = 2;
    public static final int DETECTION_FAILED = 4;
    public static final int INVALID_BACKGROUND = 8;
    public static final int MANUALLY_REJECTED = 16;
    public static final int QUALITY_UNKNOWN = 32;

    private SpotSampleFlags() { }
}
