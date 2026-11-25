package plugins.fmp.multitools.experiment.spot.measurement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.spot.measurement.SpotDerivedSeries.Gap;
import plugins.fmp.multitools.experiment.spot.measurement.SpotDerivedSeries.GapKind;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.BaselinePolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;

/** Shared post-processing; never modifies acquisition data and never extrapolates edge gaps. */
public final class SpotSeriesProcessor {
    private SpotSeriesProcessor() { }

    public static SpotDerivedSeries process(SpotMethodResult result, String quantityId,
            SpotProcessingSettings settings) {
        SpotMeasuredQuantity quantity = result.getQuantity(quantityId);
        double[] values = quantity.getRawValues();
        double[] times = result.getTimesSeconds();
        int[] flags = quantity.getEffectiveFlags();
        boolean[] estimated = new boolean[values.length];
        int firstValid = -1;
        for (int i = 0; i < values.length; i++) {
            if (flags[i] != 0) { values[i] = Double.NaN; }
            else if (firstValid < 0) { firstValid = i; }
        }

        // Select the baseline from observations BEFORE filling or smoothing.
        double baseline = Double.NaN;
        double baselineTime = Double.NaN;
        String baselineSource = "";
        boolean review = values.length > 0 && firstValid != 0;
        if (settings.baselinePolicy == BaselinePolicy.FIRST_VALID && firstValid >= 0) {
            baseline = values[firstValid];
            baselineTime = times[firstValid];
            baselineSource = "first_valid_observation";
            if (!(baseline > 0)) { review = true; }
        } else if (settings.baselinePolicy == BaselinePolicy.EXPLICIT) {
            baseline = settings.explicitBaseline;
            baselineTime = settings.explicitBaselineTimeSeconds;
            baselineSource = settings.baselineSource;
            review = !settings.baselineReviewed || (values.length > 0 && firstValid < 0);
        } else if (settings.baselinePolicy == BaselinePolicy.FIRST_VALID) {
            review = true;
        }

        List<Gap> gaps = new ArrayList<>();
        int cursor = 0;
        while (cursor < values.length) {
            if (Double.isFinite(values[cursor])) { cursor++; continue; }
            int start = cursor;
            boolean pureOcclusion = true;
            while (cursor < values.length && !Double.isFinite(values[cursor])) {
                pureOcclusion &= flags[cursor] == SpotSampleFlags.OCCLUDED;
                cursor++;
            }
            int end = cursor - 1;
            boolean leading = start == 0;
            boolean trailing = cursor == values.length;
            GapKind kind = leading && trailing ? GapKind.ENTIRE_RECORDING
                    : leading ? GapKind.LEADING : trailing ? GapKind.TRAILING : GapKind.INTERNAL;
            boolean bracketed = kind == GapKind.INTERNAL;
            boolean eligible = bracketed && pureOcclusion
                    && times[cursor] - times[start - 1] <= settings.maximumGapSeconds;
            boolean connectable = eligible && Math.abs(values[cursor] - values[start - 1]) <= settings.noiseTolerance;
            gaps.add(new Gap(start, end, kind, connectable));
            if (!eligible || settings.gapPolicy == GapPolicy.NONE) { continue; }
            double left = values[start - 1];
            double right = values[cursor];
            for (int i = start; i <= end; i++) {
                double fraction = (times[i] - times[start - 1]) / (times[cursor] - times[start - 1]);
                values[i] = settings.gapPolicy == GapPolicy.HOLD_PREVIOUS ? left : left + fraction * (right - left);
                estimated[i] = true;
            }
        }

        if (settings.medianRadius > 0) { values = medianWithinFiniteRuns(values, settings.medianRadius); }
        if (settings.baselinePolicy != BaselinePolicy.NONE) {
            if (!Double.isFinite(baseline) || baseline <= 0) {
                Arrays.fill(values, Double.NaN);
            } else {
                for (int i = 0; i < values.length; i++) { values[i] /= baseline; }
            }
        }
        return new SpotDerivedSeries(values, flags, estimated, gaps, baseline, baselineTime,
                baselineSource, review, settings);
    }

    private static double[] medianWithinFiniteRuns(double[] values, int radius) {
        double[] out = values.clone();
        int start = 0;
        while (start < values.length) {
            if (!Double.isFinite(values[start])) { start++; continue; }
            int end = start;
            while (end + 1 < values.length && Double.isFinite(values[end + 1])) { end++; }
            for (int i = start; i <= end; i++) {
                int left = (int) Math.max(start, (long) i - radius);
                int right = (int) Math.min(end, (long) i + radius);
                double[] window = Arrays.copyOfRange(values, left, right + 1);
                Arrays.sort(window);
                int middle = window.length / 2;
                out[i] = window.length % 2 == 0 ? window[middle - 1] / 2 + window[middle] / 2 : window[middle];
            }
            start = end + 1;
        }
        return out;
    }
}
