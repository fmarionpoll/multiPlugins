package plugins.fmp.multitools.experiment.spot;

import java.util.ArrayList;
import java.util.List;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.EnumResults;

/** Sampling of image-based X-line and rim measurements, including older kymo-grid runs. */
public final class SpotLineSampling {
    private SpotLineSampling() { }

    public static List<Integer> cameraFrames(Experiment exp) {
        int count = exp.getSeqCamData().getImageLoader().getNTotalFrames();
        List<Integer> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) frames.add(i);
        return frames;
    }

    public static EnumResults source(EnumResults type) {
        if (type == EnumResults.AGG_X_LINE_CONSO) return EnumResults.X_LINE_RATIO;
        if (type == EnumResults.AGG_RIM_LINE_CONSO) return EnumResults.RIM_LINE_RATIO;
        return type;
    }

    public static boolean isLineMeasure(EnumResults type) {
        EnumResults value = source(type);
        return value == EnumResults.RIM_LINE_FRACTION || value == EnumResults.X_LINE_FRACTION
                || value == EnumResults.X_LINE_RATIO || value == EnumResults.RIM_LINE_RATIO;
    }

    public static boolean usesCameraFrames(Experiment exp, EnumResults type) {
        if (!isLineMeasure(type) || exp == null || exp.getSpots() == null) return false;
        Boolean camera = null;
        for (Spot spot : exp.getSpots().getSpotList()) {
            SpotMeasure measure = spot == null ? null : spot.getMeasurements(source(type));
            if (measure == null || measure.getCount() == 0) continue;
            if (camera != null && camera.booleanValue() != measure.isCameraSampled())
                throw new IllegalStateException("Mixed camera and legacy kymograph sampling for " + type
                        + "; recompute this measurement for all spots before combining it.");
            camera = measure.isCameraSampled();
        }
        return Boolean.TRUE.equals(camera);
    }
}
