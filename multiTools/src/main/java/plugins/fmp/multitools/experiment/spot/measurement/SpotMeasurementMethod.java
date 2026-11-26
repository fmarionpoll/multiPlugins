package plugins.fmp.multitools.experiment.spot.measurement;

/** Acquisition methods have independent timelines and fly detectors. */
public enum SpotMeasurementMethod {
    FILTER, COLOR, VERTICAL_LINE, X_LINES, RIM_LINES;

    /** Only vertical measurements depend on the selected kymograph bin. */
    public boolean isKymographDerived() { return this == VERTICAL_LINE; }
}
