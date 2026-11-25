package plugins.fmp.multitools.service;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodRecording;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags;

/** Original strip measurements before temporal RGB reconstruction, masking or normalization. */
public final class SpotVerticalMeasurementCapture {
    private SpotVerticalMeasurementCapture() { }

    public static SpotMethodResult capture(IcyBufferedImage image, CageKymographSpotBands band,
            CageKymoAnalyzer.Params params, double[] seconds) {
        return capture(image, band, params, seconds, "caller_supplied_seconds");
    }

    public static SpotMethodResult capture(IcyBufferedImage image, CageKymographSpotBands band,
            CageKymoAnalyzer.Params params, double[] seconds, String timelineBasis) {
        if (seconds.length > image.getWidth()) { throw new IllegalArgumentException("Timeline exceeds strip width"); }
        int width = image.getWidth(), channels = image.getSizeC();
        int y0 = Math.max(0, band.y0), y1 = Math.min(image.getHeight(), band.y1Exclusive);
        double[] metric = KymoImageTransforms.channel0AsDouble(
                KymoImageTransforms.applyMetricTransform(image, params.metricTransform, params.useGpuTransforms));
        double[] insect = params.insectMetricGateEnabled ? KymoImageTransforms.channel0AsDouble(
                KymoImageTransforms.applyMetricTransform(image, params.insectMetricTransform, params.useGpuTransforms)) : null;
        int[] r = Array1DUtil.arrayToIntArray(image.getDataXY(0), image.isSignedDataType());
        int[] g = channels > 1 ? Array1DUtil.arrayToIntArray(image.getDataXY(1), image.isSignedDataType()) : r;
        int[] b = channels > 2 ? Array1DUtil.arrayToIntArray(image.getDataXY(2), image.isSignedDataType()) : r;
        SpotMethodRecording recording = new SpotMethodRecording(SpotMeasurementMethod.VERTICAL_LINE, seconds, "occupancy_fraction")
                .parameter("implementation", "CageKymoAnalyzer")
                .parameter("timeline_basis", timelineBasis)
                .parameter("metric_transform", String.valueOf(params.metricTransform))
                .parameter("metric_threshold", String.valueOf(params.metricThreshold))
                .parameter("min_rgb_sum", String.valueOf(params.minSumRgbForValidPixel))
                .parameter("min_valid_rows", String.valueOf(params.minValidRowsPerColumn))
                .parameter("insect_gate", String.valueOf(params.insectMetricGateEnabled))
                .parameter("insect_transform", String.valueOf(params.insectMetricTransform))
                .parameter("insect_threshold", String.valueOf(params.insectMetricThreshold))
                .parameter("insect_above", String.valueOf(params.insectMetricThresholdUp))
                .parameter("occlusion_rule", "any_insect_row_in_band")
                .quantity("row_fraction", "fraction").quantity("green_height", "pixels");
        for (int x = 0; x < seconds.length; x++) {
            int valid = 0, above = 0, fly = 0;
            if (!band.geometryMissing && metric != null) {
                for (int y = y0; y < y1; y++) {
                    int pixel = y * width + x;
                    // Inspect insect evidence even where low RGB makes the spot metric unusable.
                    if (insect != null && KymoMetricGate.directedFinite(insect[pixel], params.insectMetricThreshold,
                            params.insectMetricThresholdUp)) { fly++; }
                    if (r[pixel] + g[pixel] + b[pixel] < params.minSumRgbForValidPixel
                            || !Double.isFinite(metric[pixel])) { continue; }
                    valid++;
                    if (metric[pixel] > params.metricThreshold) { above++; }
                }
            }
            int flags = band.geometryMissing || metric == null || valid < params.minValidRowsPerColumn || valid == 0
                    ? SpotSampleFlags.DETECTION_FAILED : 0;
            if (fly > 0) { flags |= SpotSampleFlags.OCCLUDED; }
            if (params.insectMetricGateEnabled && insect == null) { flags |= SpotSampleFlags.QUALITY_UNKNOWN; }
            recording.recordFlyEvidence(x, insect != null && !band.geometryMissing && y1 > y0 ? fly / (double) (y1 - y0) : Double.NaN);
            recording.record("row_fraction", x, valid > 0 ? above / (double) valid : Double.NaN, flags);
            recording.record("green_height", x, valid > 0 ? above : Double.NaN, flags);
        }
        return recording.build();
    }
}
