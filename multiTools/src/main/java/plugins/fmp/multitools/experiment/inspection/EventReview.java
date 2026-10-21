package plugins.fmp.multitools.experiment.inspection;

import java.util.Arrays;
import java.util.List;

/** Diagnostic annotation only; never used by measurement computations. */
public final class EventReview {
    public static final String[] HEADER = { "experiment", "cage", "capillary", "source_kymograph",
            "column_0based", "camera_frame_0based", "original_frame_0based", "relative_time_ms",
            "acquisition_time", "timing_source", "source_image", "classification", "comment",
            "upper_px", "lower_px", "upper_delta_px", "lower_delta_px", "gulp_amplitude_px" };
    public static final String[] CLASSIFICATIONS = { "Genuine reverse movement",
            "Upper-meniscus detection artifact", "Lower-meniscus detection artifact",
            "Capillary/image movement", "Fly obstruction", "Uncertain", "No anomaly" };
    private final String[] values;

    public EventReview(String... fields) {
        if (fields.length != HEADER.length) throw new IllegalArgumentException("Invalid review field count");
        values = fields.clone();
        for (int i = 0; i < values.length; i++) if (values[i] == null) values[i] = "";
    }

    public String get(int index) { return values[index]; }
    public String[] fields() { return values.clone(); }
    public List<String> key() { return Arrays.asList(values[0], values[1], values[2], values[3], values[4]); }
}
