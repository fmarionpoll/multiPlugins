package plugins.fmp.multitools.experiment.spot.measurement;

/** Acquisition methods have independent timelines and fly detectors. */
public enum SpotMeasurementMethod {
    FILTER("SpotsPrimaryFilter.csv"),
    COLOR("SpotsPrimaryColor.csv"),
    VERTICAL_LINE("SpotsPrimaryVertical.csv"),
    X_LINES("SpotsPrimaryXLine.csv"),
    RIM_LINES("SpotsPrimaryRimLine.csv");

    private final String fileName;

    SpotMeasurementMethod(String fileName) { this.fileName = fileName; }

    /** One primary CSV per method. Image methods live in results; vertical lives in the active bin. */
    public String fileName() { return fileName; }

    /** Only vertical measurements depend on the selected kymograph bin. */
    public boolean isKymographDerived() { return this == VERTICAL_LINE; }
}
