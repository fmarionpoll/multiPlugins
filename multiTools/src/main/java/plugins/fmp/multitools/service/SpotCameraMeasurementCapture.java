package plugins.fmp.multitools.service;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodRecording;
import plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.experiment.spots.Spots;

/** Captures raw camera measures before legacy gating/cleanup; each detector owns its fly evidence. */
final class SpotCameraMeasurementCapture {
    private final Map<Spot, SpotMethodRecording> recordings = new IdentityHashMap<>();
    private final String[] quantityIds;

    SpotCameraMeasurementCapture(Experiment exp, List<Spot> spots, int count, SpotMeasurementMethod method,
            BuildSeriesOptions options, String implementation) {
        double[] seconds = SpotAcquisitionTimeline.camera(exp, count);
        quantityIds = method == SpotMeasurementMethod.COLOR
                ? new String[] { "grey_signal", "selected_area" }
                : new String[] { "thresholded_signal", "fly_pixel_excluded_signal" };
        for (Spot spot : spots) {
            SpotMethodRecording recording = new SpotMethodRecording(method, seconds, "covered_pixels")
                    .parameter("implementation", implementation)
                    .parameter("timeline_basis", "camera_acquisition_times")
                    .parameter("spot_transform", String.valueOf(options.transform01))
                    .parameter("spot_threshold", String.valueOf(options.spotThreshold))
                    .parameter("spot_threshold_above", String.valueOf(options.spotThresholdUp))
                    .parameter("fly_transform", String.valueOf(options.transform02))
                    .parameter("fly_threshold", String.valueOf(options.flyThreshold))
                    .parameter("fly_threshold_above", String.valueOf(options.flyThresholdUp))
                    .parameter("minimum_fly_pixels", String.valueOf(Spots.getMinFlyPixelsForOccupancyGate(
                            spot, options.getFlyOccupancyFractionForSpotSumNoFly())))
                    .parameter("color_references", String.valueOf(options.spotColorReferenceList))
                    .parameter("color_space", String.valueOf(options.spotColorSpace))
                    .parameter("color_distance_type", String.valueOf(options.spotColorDistanceType))
                    .parameter("color_distance_max", String.valueOf(options.spotColorDistanceMax))
                    .parameter("color_exclude_references", String.valueOf(options.spotColorExcludeList))
                    .parameter("color_exclude_distance_max", String.valueOf(options.spotColorExcludeDistanceMax))
                    .quantity(quantityIds[0], method == SpotMeasurementMethod.COLOR ? "RGB_intensity" : "transform_units_per_ROI_pixel")
                    .quantity(quantityIds[1], method == SpotMeasurementMethod.COLOR ? "pixels" : "transform_units_per_visible_pixel");
            recordings.put(spot, recording);
        }
    }

    synchronized void record(Spot spot, int sample, int flyPixels, boolean occluded, double first, double second) {
        SpotMethodRecording recording = recordings.get(spot);
        if (recording == null) { return; }
        recording.recordFlyEvidence(sample, flyPixels);
        int flags = occluded ? SpotSampleFlags.OCCLUDED : 0;
        recording.record(quantityIds[0], sample, first, flags);
        recording.record(quantityIds[1], sample, second, flags);
    }

    synchronized void missingImage(int sample) {
        for (SpotMethodRecording recording : recordings.values()) {
            for (String id : quantityIds) { recording.record(id, sample, Double.NaN, SpotSampleFlags.MISSING_IMAGE); }
        }
    }

    synchronized void publish() {
        for (Map.Entry<Spot, SpotMethodRecording> entry : recordings.entrySet()) {
            entry.getKey().getMethodMeasurements().put(entry.getValue().build());
        }
    }
}
