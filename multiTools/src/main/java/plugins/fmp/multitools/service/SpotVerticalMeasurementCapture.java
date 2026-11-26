package plugins.fmp.multitools.service;

import java.util.Arrays;
import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodRecording;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

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
        double[] integralMetric = integralMetric(image, params);
        double[] insect = params.insectMetricGateEnabled ? KymoImageTransforms.channel0AsDouble(
                KymoImageTransforms.applyMetricTransform(image, params.insectMetricTransform, params.useGpuTransforms)) : null;
        int[] r = Array1DUtil.arrayToIntArray(image.getDataXY(0), image.isSignedDataType());
        int[] g = channels > 1 ? Array1DUtil.arrayToIntArray(image.getDataXY(1), image.isSignedDataType()) : r;
        int[] b = channels > 2 ? Array1DUtil.arrayToIntArray(image.getDataXY(2), image.isSignedDataType()) : r;
        SpotMethodRecording recording = new SpotMethodRecording(SpotMeasurementMethod.VERTICAL_LINE, seconds, "occupancy_fraction")
                .parameter("implementation", "SpotVerticalMeasurementCapture_v2")
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
                .parameter("background_method", "median_of_outer_20_percent_rows_each_edge")
                .parameter("integral_metric_precision", params.metricTransform == ImageTransformEnums.RGB_DIFFS
                        ? "full_precision_RGB_DIFFS" : "image_transform")
                .quantity("row_fraction", "fraction").quantity("green_height", "pixels")
                .quantity("color_integral", "metric_units_times_pixels").quantity("background", "metric_units");
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
            int edge = Math.max(1, (y1 - y0) / 5), count = 0, integralValid = 0;
            double[] floor = new double[2 * edge];
            if (!band.geometryMissing && integralMetric != null) {
                for (int y = y0; y < y1; y++) {
                    int pixel = y * width + x;
                    if (!Double.isFinite(integralMetric[pixel]) || r[pixel] + g[pixel] + b[pixel] < params.minSumRgbForValidPixel) continue;
                    integralValid++;
                    if (y < y0 + edge || y >= y1 - edge) floor[count++] = integralMetric[pixel];
                }
            }
            boolean usable = y1 > y0 && integralValid == y1 - y0
                    && integralValid >= params.minValidRowsPerColumn && count > 0;
            double background = Double.NaN, sum = Double.NaN;
            if (usable) {
                Arrays.sort(floor, 0, count);
                background = count % 2 == 0 ? (floor[count / 2 - 1] + floor[count / 2]) / 2 : floor[count / 2];
                sum = 0;
                for (int y = y0; y < y1; y++) sum += Math.max(0, integralMetric[y * width + x] - background);
            }
            // Keep observed values and flags; no baseline, interpolation, smoothing or normalization here.
            recording.record("color_integral", x, sum, flags | (usable ? 0 : SpotSampleFlags.DETECTION_FAILED));
            recording.record("background", x, background, usable ? 0 : SpotSampleFlags.INVALID_BACKGROUND);
        }
        return recording.build();
    }

    static double[] integralMetric(IcyBufferedImage image, CageKymoAnalyzer.Params params) {
        if (params.metricTransform != ImageTransformEnums.RGB_DIFFS)
            return KymoImageTransforms.channel0AsDouble(KymoImageTransforms.applyMetricTransform(image, params.metricTransform, false));
        if (image.getSizeC() < 3) return null;
        double[] r = Array1DUtil.arrayToDoubleArray(image.getDataXY(0), image.isSignedDataType());
        double[] g = Array1DUtil.arrayToDoubleArray(image.getDataXY(1), image.isSignedDataType());
        double[] b = Array1DUtil.arrayToDoubleArray(image.getDataXY(2), image.isSignedDataType());
        double[] metric = new double[r.length];
        for (int i = 0; i < metric.length; i++)
            metric[i] = Math.abs(r[i] - b[i]) + Math.abs(r[i] - g[i]) + Math.abs(b[i] - g[i]);
        return metric;
    }
}
