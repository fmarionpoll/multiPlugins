package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Experimental, signed image registration. Positive displacement is down. */
public final class VerticalCorrelation {
    private VerticalCorrelation() { }

    public static final class Result {
        public final double[] shift, correlation, refined, searchRange;
        public final boolean[] reliable;
        public final String[] reason;

        private Result(int n) {
            searchRange = new double[n];
            shift = new double[n];
            correlation = new double[n];
            refined = new double[n];
            reliable = new boolean[n];
            reason = new String[n];
            Arrays.fill(shift, Double.NaN);
            Arrays.fill(correlation, Double.NaN);
            Arrays.fill(reason, "No previous sample");
        }
    }

    private static final class Match {
        double shift, correlation, contrast, separation;
        boolean atLimit, imageLimit;
    }

    public static final double DEFAULT_MAX_SHIFT = 6;
    private static final double SHIFT_STEP = .1;

    public static Result analyze(BufferedImage image, double[] raw, int radius) {
        return analyze(image, raw, radius, DEFAULT_MAX_SHIFT);
    }

    /** maxShift: largest displacement searched between two columns (pixels); larger steps are flagged "Search limit". */
    public static Result analyze(BufferedImage image, double[] raw, int radius, double maxShift) {
        return analyze(image, raw, radius, maxShift, false);
    }

    /** Expand boundary matches up to four times the initial range (at most 64 pixels). */
    public static Result analyze(BufferedImage image, double[] raw, int radius, double maxShift, boolean adaptive) {
        if (image == null || raw == null || image.getWidth() != raw.length || radius < 2)
            throw new IllegalArgumentException("Native kymograph columns must match the saved levels.");
        if (!Double.isFinite(maxShift) || !(maxShift >= 1))
            throw new IllegalArgumentException("Maximum shift must be a finite value of at least 1 pixel.");
        int n = raw.length, h = image.getHeight();
        double[][][] color = new double[n][2][h];
        for (int t = 0; t < n; t++) {
            for (int y = 0; y < h; y++) {
                int rgb = image.getRGB(t, y);
                int red = (rgb >> 16) & 255;
                color[t][0][y] = ((rgb >> 8) & 255) - red;
                color[t][1][y] = (rgb & 255) - red;
            }
        }
        Result result = new Result(n);
        for (int t = 1; t < n; t++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (!Double.isFinite(raw[t - 1]) || !Double.isFinite(raw[t])) {
                result.reason[t] = "Missing meniscus position";
                continue;
            }
            Match forward = match(color[t - 1], color[t], raw[t - 1], radius, maxShift);
            Match reverse = match(color[t], color[t - 1], raw[t], radius, maxShift);
            double range = maxShift;
            double ceiling = Math.max(maxShift, Math.min(64, 4 * maxShift));
            while (adaptive && forward != null && reverse != null && range < ceiling
                    && ((forward.atLimit && !forward.imageLimit) || (reverse.atLimit && !reverse.imageLimit))) {
                range = Math.min(ceiling, range * 1.5);
                forward = match(color[t - 1], color[t], raw[t - 1], radius, range);
                reverse = match(color[t], color[t - 1], raw[t], radius, range);
            }
            result.searchRange[t] = range;
            if (forward == null || reverse == null) {
                result.reason[t] = "Image border or flat profile";
                continue;
            }
            result.shift[t] = (forward.shift - reverse.shift) / 2;
            result.correlation[t] = Math.min(forward.correlation, reverse.correlation);
            String reason = "";
            if (forward.imageLimit || reverse.imageLimit) reason = "Image border";
            else if (forward.atLimit || reverse.atLimit) reason = "Search limit";
            else if (Math.min(forward.contrast, reverse.contrast) < 2) reason = "Low contrast";
            else if (result.correlation[t] < .90) reason = "Weak correlation";
            else if (Math.min(forward.separation, reverse.separation) < .005) reason = "Broad correlation peak";
            else if (Math.abs(forward.shift + reverse.shift) > .75) reason = "Forward/reverse disagreement";
            result.reason[t] = reason;
            result.reliable[t] = reason.isEmpty();
        }
        double[] refined = refine(raw, result.shift, result.reliable, .05);
        System.arraycopy(refined, 0, result.refined, 0, n);
        return result;
    }

    private static Match match(double[][] previous, double[][] current, double center, int radius, double maxShift) {
        int h = previous[0].length, count = 2 * radius + 1;
        // Only the unshifted template must fit in full. A wide requested search
        // must not reject a valid small displacement near an image border.
        if (center - radius < 0 || center + radius > h - 1) return null;
        int requestedSteps = (int) Math.floor(maxShift / SHIFT_STEP + 1e-9);
        int firstStep = Math.max(-requestedSteps, (int) Math.ceil((radius - center) / SHIFT_STEP - 1e-9));
        int lastStep = Math.min(requestedSteps, (int) Math.floor((h - 1 - radius - center) / SHIFT_STEP + 1e-9));
        if (lastStep <= firstStep) return null;
        double[][] template = new double[2][count];
        double energy = 0;
        for (int c = 0; c < 2; c++) {
            double mean = 0;
            for (int j = 0; j < count; j++) mean += template[c][j] = sample(previous[c], center - radius + j);
            mean /= count;
            for (int j = 0; j < count; j++) {
                template[c][j] -= mean;
                energy += template[c][j] * template[c][j];
            }
        }
        if (!(energy > 1e-12)) return null;
        double[] scores = new double[lastStep - firstStep + 1];
        int best = 0;
        double[] values = new double[count];
        for (int k = 0; k < scores.length; k++) {
            double shift = (firstStep + k) * SHIFT_STEP, numerator = 0, targetEnergy = 0;
            for (int c = 0; c < 2; c++) {
                double mean = 0;
                for (int j = 0; j < count; j++) mean += values[j] = sample(current[c], center - radius + j + shift);
                mean /= count;
                for (int j = 0; j < count; j++) {
                    double v = values[j] - mean;
                    numerator += v * template[c][j];
                    targetEnergy += v * v;
                }
            }
            scores[k] = targetEnergy > 1e-12 ? numerator / Math.sqrt(energy * targetEnergy) : -1;
            if (scores[k] > scores[best]) best = k;
        }
        Match m = new Match();
        m.shift = (firstStep + best) * SHIFT_STEP;
        m.correlation = scores[best];
        m.contrast = Math.sqrt(energy / (2 * count));
        m.atLimit = best == 0 || best == scores.length - 1;
        m.imageLimit = (best == 0 && firstStep > -requestedSteps)
                || (best == scores.length - 1 && lastStep < requestedSteps);
        double other = -1;
        int onePixel = (int) Math.round(1 / SHIFT_STEP);
        for (int k = 0; k < scores.length; k++)
            if (Math.abs(k - best) >= onePixel) other = Math.max(other, scores[k]);
        m.separation = m.correlation - other;
        return m;
    }

    private static double sample(double[] values, double row) {
        // Clamp only floating-point roundoff at valid grid endpoints.
        row = Math.max(0, Math.min(values.length - 1, row));
        int lo = (int) Math.floor(row);
        int hi = Math.min(values.length - 1, lo + 1);
        return values[lo] + (row - lo) * (values[hi] - values[lo]);
    }

    /** Robust absolute anchors plus accepted motion constraints; no smoothing penalty. */
    public static double[] refine(double[] raw, double[] shift, boolean[] valid, double anchorWeight) {
        int n = raw.length;
        if (shift.length != n || valid.length != n || !(anchorWeight > 0))
            throw new IllegalArgumentException("Invalid motion constraints");
        double[] x = raw.clone();
        for (int round = 0; round < 8; round++) {
            double[] diagonal = new double[n], off = new double[n], rhs = new double[n];
            for (int i = 0; i < n; i++) {
                if (Double.isFinite(raw[i])) {
                    double residual = Math.abs(x[i] - raw[i]);
                    double w = anchorWeight * Math.min(1, 1 / Math.max(residual, 1e-12));
                    diagonal[i] = w;
                    rhs[i] = w * raw[i];
                } else {
                    // Keep missing samples isolated; never bridge them in registration.
                    diagonal[i] = 1;
                }
                if (i > 0 && valid[i] && Double.isFinite(shift[i])
                        && Double.isFinite(raw[i]) && Double.isFinite(raw[i - 1])) {
                    diagonal[i] += 1;
                    diagonal[i - 1] += 1;
                    off[i] = -1;
                    rhs[i] += shift[i];
                    rhs[i - 1] -= shift[i];
                }
            }
            for (int i = 1; i < n; i++) {
                double factor = off[i] / diagonal[i - 1];
                diagonal[i] -= factor * off[i];
                rhs[i] -= factor * rhs[i - 1];
            }
            if (n > 0) x[n - 1] = rhs[n - 1] / diagonal[n - 1];
            for (int i = n - 2; i >= 0; i--) x[i] = (rhs[i] - off[i + 1] * x[i + 1]) / diagonal[i];
            for (int i = 0; i < n; i++) if (!Double.isFinite(raw[i])) x[i] = Double.NaN;
        }
        return x;
    }

    public static final class Noise {
        public final double automatic, median, sigma;
        public final int quietSamples;
        public final String source;

        private Noise(double automatic, double median, double sigma, int quietSamples, String source) {
            this.automatic = automatic;
            this.median = median;
            this.sigma = sigma;
            this.quietSamples = quietSamples;
            this.source = source;
        }
    }

    /** Estimate from runs of >=4 quiet intervals; controls supply a conservative floor. */
    public static Noise estimateNoise(double[] residual, boolean[] valid, double[] controls) {
        double[] control = finite(controls);
        double[] record = new double[residual.length];
        Arrays.fill(record, Double.NaN);
        for (int i = 1; i < residual.length; i++) if (valid[i]) record[i] = residual[i];
        double[] seed = control.length >= 12 ? control : finite(record);
        if (seed.length < 12) return new Noise(Double.NaN, Double.NaN, Double.NaN, 0,
                "Insufficient reliable noise samples; choose a threshold manually");
        double center = median(seed), scale = scale(seed, center);
        double[] quiet = new double[record.length];
        Arrays.fill(quiet, Double.NaN);
        int run = 0;
        for (int i = 1; i <= record.length; i++) {
            if (i < record.length && Double.isFinite(record[i]) && Math.abs(record[i] - center) <= 2 * scale) run++;
            else {
                if (run >= 4) System.arraycopy(record, i - run, quiet, i - run, run);
                run = 0;
            }
        }
        quiet = finite(quiet);
        double floor = control.length >= 12 ? Math.max(0, median(control) + 3 * scale(control, median(control))) : 0;
        if (quiet.length < Math.max(12, finite(record).length / 10)) {
            return control.length >= 12
                    ? new Noise(floor, center, scale, quiet.length, "Empty-capillary noise; too few quiet stretches (special case)")
                    : new Noise(Double.NaN, center, scale, quiet.length, "Too few quiet stretches and no control noise; choose manually");
        }
        double m = median(quiet), s = scale(quiet, m);
        return new Noise(Math.max(floor, Math.max(0, m + 3 * s)), m, s, quiet.length,
                control.length >= 12 ? "Quiet stretches; empty-capillary noise floor" : "Quiet stretches only; no empty-capillary noise reference");
    }

    public static boolean[] candidates(double[] residual, boolean[] valid, double threshold) {
        boolean[] selected = new boolean[residual.length];
        if (!Double.isFinite(threshold) || threshold < 0) return selected;
        for (int i = 1; i < residual.length; i++) selected[i] = valid[i] && residual[i] > threshold;
        return selected;
    }

    private static double[] finite(double[] values) {
        return Arrays.stream(values).filter(Double::isFinite).toArray();
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        Arrays.sort(copy);
        int n = copy.length;
        return n % 2 == 0 ? (copy[n / 2 - 1] + copy[n / 2]) / 2 : copy[n / 2];
    }

    private static double scale(double[] values, double center) {
        double[] deviations = Arrays.stream(values).map(v -> Math.abs(v - center)).toArray();
        return Math.max(.05, 1.4826 * median(deviations));
    }
}
