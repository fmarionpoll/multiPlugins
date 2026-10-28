package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Experimental signed, persistent intake episodes. Amplitudes are kymograph pixels. */
public final class IntakeEpisodes {
    public static final String METHOD_VERSION = "v5";
    private IntakeEpisodes() { }

    public static final class EvaporationScale {
        public final double factor;
        public final int quietIntervals;
        public final boolean reliable;
        private EvaporationScale(double factor, int quietIntervals, boolean reliable) {
            this.factor = factor; this.quietIntervals = quietIntervals; this.reliable = reliable;
        }
    }

    /** Estimate slopes of raw level against the shared evaporation shape in quiet runs.
     * Separate run intercepts prevent feeding steps from becoming evaporation.
     * Require independent runs and adequate reference displacement; otherwise keep scale one.
     */
    public static EvaporationScale evaporationScale(double[] raw, double[] motion, double[] reference,
            double[] times, boolean[] valid, double sigma) {
        List<Double> slopes = new ArrayList<>();
        int start = 1, samples = 0;
        for (int i = 1; i <= raw.length; i++) {
            boolean quiet = i < raw.length && valid[i] && Double.isFinite(motion[i])
                    && Math.abs((motion[i] - reference[i] + reference[i - 1]) / (times[i] - times[i - 1])) <= 2 * sigma;
            if (quiet) continue;
            int end = i - 1;
            if (end - start + 1 >= 5 && reference[end] - reference[start - 1] >= .5) {
                double mx = 0, my = 0;
                int count = end - start + 2;
                for (int k = start - 1; k <= end; k++) { mx += reference[k]; my += raw[k]; }
                mx /= count; my /= count;
                double xy = 0, xx = 0;
                for (int k = start - 1; k <= end; k++) {
                    xy += (reference[k] - mx) * (raw[k] - my);
                    xx += (reference[k] - mx) * (reference[k] - mx);
                }
                double slope = xy / xx;
                if (Double.isFinite(slope) && slope >= .2 && slope <= 2) {
                    slopes.add(slope); samples += count - 1;
                }
            }
            start = i + 1;
        }
        double center = median(slopes);
        List<Double> deviations = new ArrayList<>();
        for (double s : slopes) deviations.add(Math.abs(s - center));
        boolean reliable = slopes.size() >= 3 && samples >= 20 && median(deviations) <= .25;
        return new EvaporationScale(reliable ? center : 1, samples, reliable);
    }

    public static final class Detection {
        public final double[] net;
        /** Jointly ordered evaporation-corrected positions; null for the legacy rate-only API. */
        public final double[] position;
        public final List<Gap> gaps;
        /** Surviving fitted episodes; a multi-bin episode need not be one biological gulp. */
        public final int episodes;
        private Detection(double[] net, int episodes) { this(net, episodes, null, new ArrayList<>()); }
        private Detection(double[] net, int episodes, double[] position, List<Gap> gaps) {
            this.net = net; this.episodes = episodes; this.position = position; this.gaps = gaps;
        }
    }

    /** An unassigned persistent displacement, not an automatically accepted intake. */
    public static final class Gap {
        public final int first, last;
        public final double excess, tolerance;
        public final boolean reliable;
        private Gap(int first, int last, double excess, double tolerance, boolean reliable) {
            this.first = first; this.last = last; this.excess = excess;
            this.tolerance = tolerance; this.reliable = reliable;
        }
    }

    private static final class Candidate {
        int first, last, boundary;
        double amount, score, limit;
    }

    /** Search 1..6 consecutive bins, calibrated against held-out empty capillaries.
     * Accepted-level mode compares signed movement with robust before/after plateaus;
     * neighbouring quality gaps truncate the flanks, never the candidate itself.
     * Resolve supported single-bin steps before grouping unresolved movement.
     */
    public static Detection detect(double[] rate, double[] times, boolean[] valid,
            List<double[]> controlRates, double automatic, double threshold) {
        return detect(rate, times, valid, controlRates, automatic, threshold, null);
    }

    public static Detection detect(double[] rate, double[] times, boolean[] valid,
            List<double[]> controlRates, double automatic, double threshold, double[] correctedLevels) {
        double[] net = new double[rate.length];
        if (!Double.isFinite(threshold) || threshold < 0) return new Detection(net, 0);
        if (!(automatic > 0)) automatic = Math.max(threshold, .05);
        List<double[]> controlLevels = new ArrayList<>();
        if (correctedLevels != null) for (double[] control : controlRates) {
            double[] levels = new double[control.length];
            for (int i = 1; i < levels.length; i++)
                levels[i] = Double.isFinite(control[i]) ? levels[i - 1] + control[i] * (times[i] - times[i - 1]) : 0;
            controlLevels.add(levels);
        }
        List<Candidate> all = new ArrayList<>();
        for (int width = 1; width <= 6; width++) {
            List<Double> background = new ArrayList<>();
            for (int c = 0; c < controlRates.size(); c++) {
                double[] control = controlRates.get(c);
                for (int a = 1; a + width <= rate.length; a++) {
                    double sum = sum(control, times, null, a, a + width - 1);
                    if (correctedLevels != null) sum = Math.min(sum,
                            plateauContrast(controlLevels.get(c), control, null, a, a + width - 1));
                    if (Double.isFinite(sum)) background.add(sum);
                }
            }
            // No duration calibration: preserve only the single-bin option.
            if (background.size() < 30 && width > 1) continue;
            background.sort(Double::compare);
            double empirical = background.isEmpty() ? 0
                    : background.get(Math.min(background.size() - 1, (int) Math.ceil(.995 * background.size()) - 1));
            for (int a = 1; a + width <= rate.length; a++) {
                int b = a + width - 1;
                double amount = sum(rate, times, valid, a, b);
                double duration = times[b] - times[a - 1];
                double limit = Math.max(empirical, automatic * duration / width) * threshold / automatic;
                if (!(amount > limit)) continue;
                double persistent;
                if (correctedLevels != null) {
                    // Test the sustained level difference, not the sum of every
                    // negative noise fluctuation on both sides of a true step.
                    // Controls above use this same sustained-change statistic.
                    persistent = Math.min(amount, plateauContrast(correctedLevels, rate, valid, a, b));
                } else {
                    // Preserve the legacy rate-only API when no level anchors exist.
                    persistent = amount;
                    for (int k = Math.max(1, a - 2); k <= Math.min(rate.length - 1, b + 2); k++) {
                        if (k >= a && k <= b) continue;
                        if (!valid[k] || !Double.isFinite(rate[k])) { persistent = Double.NaN; break; }
                        persistent += Math.min(0, rate[k] * (times[k] - times[k - 1]));
                    }
                }
                if (!(persistent > limit)) continue;
                Candidate c = new Candidate();
                c.first = a; c.last = b; c.amount = persistent; c.score = persistent / Math.max(limit, 1e-9);
                c.limit = limit;
                all.add(c);
            }
        }
        Comparator<Candidate> ranking = Comparator.comparingDouble((Candidate c) -> -c.score)
                .thenComparingInt(c -> c.last - c.first);
        if (correctedLevels != null)
            ranking = Comparator.comparingInt((Candidate c) -> c.first == c.last ? 0 : 1).thenComparing(ranking);
        all.sort(ranking);
        boolean[] used = new boolean[rate.length];
        boolean[] transition = new boolean[rate.length];
        List<Candidate> selected = new ArrayList<>();
        int count = 0;
        for (Candidate c : all) {
            boolean overlaps = false;
            for (int k = c.first; k <= c.last; k++) overlaps |= used[k];
            if (overlaps) continue;
            double positive = 0;
            for (int k = c.first; k <= c.last; k++) positive += Math.max(0, rate[k] * (times[k] - times[k - 1]));
            int boundary = c.first;
            for (int k = c.first + 1; k <= c.last; k++)
                if (rate[k] * (times[k] - times[k - 1]) > rate[boundary] * (times[boundary] - times[boundary - 1])) boundary = k;
            c.boundary = boundary;
            selected.add(c);
            for (int k = c.first; k <= c.last; k++) {
                used[k] = true;
                // A grouped change has one representative boundary; intermediate
                // ramp samples must not bias either neighbouring plateau's fit.
                if (correctedLevels != null && k < c.last) transition[k] = true;
                net[k] = correctedLevels == null
                        ? c.amount * Math.max(0, rate[k] * (times[k] - times[k - 1])) / positive
                        : k == boundary ? c.amount : 0;
            }
            count++;
        }
        if (correctedLevels == null) return new Detection(net, count);
        Detection fitted;
        for (;;) {
            fitted = ordered(net, correctedLevels, times, valid, controlRates, automatic, threshold, transition);
            boolean removed = false;
            for (java.util.Iterator<Candidate> it = selected.iterator(); it.hasNext();) {
                Candidate c = it.next();
                // A locally plausible candidate is not an intake if its fitted
                // amount no longer clears the same acceptance limit. Refit after
                // removal instead of keeping a tiny noise bar or clipping it.
                if (!(fitted.net[c.boundary] > c.limit)) {
                    net[c.boundary] = 0;
                    for (int k = c.first; k < c.last; k++) transition[k] = false;
                    it.remove();
                    removed = true;
                }
            }
            if (!removed) break;
        }
        double[] amounts = fitted.net.clone(), positions = fitted.position.clone();
        for (Candidate c : selected) {
            if (c.first == c.last) continue;
            double total = fitted.net[c.boundary], weight = 0;
            for (int k = c.first; k <= c.last; k++)
                weight += Math.max(0, rate[k] * (times[k] - times[k - 1]));
            double position = fitted.position[c.boundary - 1];
            for (int k = c.first; k <= c.last; k++) {
                amounts[k] = total * Math.max(0, rate[k] * (times[k] - times[k - 1])) / weight;
                position += amounts[k];
                positions[k] = position;
            }
        }
        return new Detection(amounts, selected.size(), positions, fitted.gaps);
    }

    /** Fit shared plateau positions, rather than assigning independent bar lengths.
     * Robust plateau medians are jointly constrained by weighted isotonic regression.
     * Acceptance uses signed motion; amplitude uses only the fitted level difference.
     * Samples inside an accepted grouped transition are excluded from plateau observations.
     * No endpoint constraint and no clipping of independently anchored bars is used.
     */
    public static Detection ordered(double[] accepted, double[] levels, double[] times, boolean[] valid,
            List<double[]> controls, double automatic, double threshold) {
        return ordered(accepted, levels, times, valid, controls, automatic, threshold, new boolean[levels.length]);
    }

    private static Detection ordered(double[] accepted, double[] levels, double[] times, boolean[] valid,
            List<double[]> controls, double automatic, double threshold, boolean[] transition) {
        int n = levels.length;
        if (accepted.length != n || times.length != n || valid.length != n)
            throw new IllegalArgumentException("Episode arrays must have equal lengths");
        double[] position = new double[n], net = new double[n];
        Arrays.fill(position, Double.NaN);
        List<Gap> gaps = new ArrayList<>();
        boolean[] unassigned = new boolean[n];
        int pauseStart = 0;
        for (int i = 1; i <= n; i++) {
            if (i < n && !(accepted[i] > 0) && !transition[i]) continue;
            inspectGap(gaps, levels, times, valid, controls, pauseStart, i - 1, automatic, threshold);
            pauseStart = i < n && transition[i] ? i + 1 : i;
        }
        // Let a flagged gap have its own unresolved level change, so its movement
        // is not silently absorbed into the amplitudes of neighbouring intakes.
        for (Gap gap : gaps) {
            int cut = (gap.first + gap.last + 1) / 2;
            double best = Double.NEGATIVE_INFINITY;
            for (int k = gap.first + 1; k < gap.last; k++) {
                double change = medianRange(levels, k, Math.min(gap.last, k + 2))
                        - medianRange(levels, Math.max(gap.first, k - 3), k - 1);
                if (change > best) { best = change; cut = k; }
            }
            unassigned[cut] = true;
        }
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= n; i++) {
            if (i < n && !(accepted[i] > 0) && valid[i] && !unassigned[i]) continue;
            if (i > start) { starts.add(start); ends.add(i - 1); }
            start = i;
        }
        int blocks = starts.size(), used = 0;
        double[] value = new double[blocks], weight = new double[blocks];
        int[] first = new int[blocks], last = new int[blocks];
        for (int b = 0; b < blocks; b++) {
            List<Double> observations = new ArrayList<>();
            for (int i = starts.get(b); i <= ends.get(b); i++)
                if (!transition[i] && Double.isFinite(levels[i])) observations.add(levels[i]);
            if (observations.isEmpty()) continue;
            value[used] = median(observations); weight[used] = observations.size();
            first[used] = b; last[used] = b; used++;
            while (used > 1 && value[used - 2] > value[used - 1]) {
                int a = used - 2, z = used - 1;
                value[a] = (value[a] * weight[a] + value[z] * weight[z]) / (weight[a] + weight[z]);
                weight[a] += weight[z]; last[a] = last[z]; used--;
            }
        }
        for (int b = 0; b < used; b++)
            for (int k = first[b]; k <= last[b]; k++)
                for (int i = starts.get(k); i <= ends.get(k); i++)
                    if (Double.isFinite(levels[i])) position[i] = value[b];
        int count = 0;
        for (int i = 1; i < n; i++) {
            if (accepted[i] > 0 && valid[i] && Double.isFinite(position[i]) && Double.isFinite(position[i - 1])) {
                net[i] = Math.max(0, position[i] - position[i - 1]);
                if (net[i] <= 1e-9) net[i] = 0;
                if (net[i] > 1e-9) count++;
            }
        }
        return new Detection(net, count, position, gaps);
    }

    private static void inspectGap(List<Gap> gaps, double[] levels, double[] times, boolean[] valid,
            List<double[]> controls, int first, int last, double automatic, double threshold) {
        if (last - first < 3) return;
        int flank = Math.min(3, (last - first + 1) / 2);
        double before = medianRange(levels, first, first + flank - 1);
        double after = medianRange(levels, last - flank + 1, last);
        int width = last - first - flank + 1;
        List<Double> background = new ArrayList<>();
        for (double[] control : controls)
            for (int a = 1; a + width <= control.length; a++) {
                double amount = sum(control, times, null, a, a + width - 1);
                if (Double.isFinite(amount)) background.add(amount);
            }
        background.sort(Double::compare);
        double empirical = background.size() < 30 ? Double.NaN
                : background.get(Math.min(background.size() - 1, (int) Math.ceil(.995 * background.size()) - 1));
        double unit = (times[last] - times[first]) / (last - first);
        double tolerance = Math.max(1, Math.max(Double.isFinite(empirical) ? empirical : 0,
                Math.max(.05, automatic) * unit) * threshold / Math.max(.05, automatic));
        if (!(after - before > tolerance)) return;
        boolean reliable = background.size() >= 30;
        for (int i = first + 1; i <= last; i++) reliable &= valid[i];
        gaps.add(new Gap(first, last, after - before, tolerance, reliable));
    }

    private static double sum(double[] rate, double[] times, boolean[] valid, int a, int b) {
        double sum = 0;
        for (int i = a; i <= b; i++) {
            if ((valid != null && !valid[i]) || !Double.isFinite(rate[i])) return Double.NaN;
            sum += rate[i] * (times[i] - times[i - 1]);
        }
        return sum;
    }

    private static double plateauContrast(double[] levels, double[] rate, boolean[] valid, int a, int b) {
        int first = a - 1, last = b;
        while (first > Math.max(0, a - 4) && usable(rate, valid, first)) first--;
        while (last < Math.min(levels.length - 1, b + 3) && usable(rate, valid, last + 1)) last++;
        double after = medianRange(levels, b, Math.min(last, b + 2));
        // Overlapping three-point flanks reject one/two-bin excursions without
        // adding every unrelated negative noise increment to the event's score.
        if (last >= b + 3) after = Math.min(after, medianRange(levels, b + 1, last));
        double before = medianRange(levels, Math.max(first, a - 3), a - 1);
        if (first <= a - 4) before = Math.max(before, medianRange(levels, first, a - 2));
        return after - before;
    }

    private static boolean usable(double[] rate, boolean[] valid, int i) {
        return (valid == null || valid[i]) && Double.isFinite(rate[i]);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2;
    }

    private static double medianRange(double[] levels, int a, int b) {
        List<Double> values = new ArrayList<>();
        for (int i = a; i <= b; i++) {
            if (!Double.isFinite(levels[i])) return Double.NaN;
            values.add(levels[i]);
        }
        return median(values);
    }
}
