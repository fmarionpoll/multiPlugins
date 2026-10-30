package plugins.fmp.multitools.service;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Appearance consensus, sampled around levels rather than averaging their Y coordinates. */
final class MeniscusTemplateRefinement {
    private static final int REACH = 6, MIN_NEIGHBOURS = 5;
    private static final double STEP = .125;

    private MeniscusTemplateRefinement() { }

    static void refine(double[][][] color, double[] raw, int radius, double correction,
            boolean[] pinned, VerticalCorrelation.Result result) {
        int n = raw.length;
        if (n == 0) return;
        int h = color[0][0].length;
        // Only polish columns already supported by independent image pairs.
        // Conservative per-pass corrections cannot rescue an unsupported trace.
        double range = Math.min(3, correction);
        int margin = radius + (int) Math.ceil(range) + 1;
        double[] positions = result.refined.clone();
        boolean[] confirmed = new boolean[n];
        for (int round = 0; round < 3; round++) {
            double[] next = positions.clone();
            for (int t = 0; t < n; t++) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                if ((pinned != null && pinned[t]) || result.neighbourSupport[t] < 2
                        || !inside(positions[t], margin, h)) continue;
                int[] neighbours = new int[2 * REACH];
                int count = 0;
                for (int direction : new int[] {-1, 1}) {
                    for (int lag = 1; lag <= REACH; lag++) {
                        int j = t + direction * lag;
                        if (j < 0 || j >= n || !Double.isFinite(raw[j])) break;
                        if (result.neighbourSupport[j] >= 2 && inside(positions[j], margin, h))
                            neighbours[count++] = j;
                    }
                }
                if (count < MIN_NEIGHBOURS) continue;
                double[][] template = new double[2][2 * margin + 1];
                double[] spread = new double[2 * margin + 1];
                double[] values = new double[count];
                for (int c = 0; c < 2; c++) for (int row = -margin; row <= margin; row++) {
                    for (int j = 0; j < count; j++)
                        values[j] = sample(color[neighbours[j]][c], positions[neighbours[j]] + row);
                    Arrays.sort(values);
                    template[c][row + margin] = count % 2 == 0
                            ? (values[count / 2 - 1] + values[count / 2]) / 2 : values[count / 2];
                    for (int j = 0; j < count; j++) values[j] = Math.abs(values[j] - template[c][row + margin]);
                    Arrays.sort(values);
                    spread[row + margin] += (count % 2 == 0
                            ? (values[count / 2 - 1] + values[count / 2]) / 2 : values[count / 2]) / 2;
                }
                double[][] target = new double[2][2 * margin + 1];
                for (int c = 0; c < 2; c++) for (int row = -margin; row <= margin; row++)
                    target[c][row + margin] = sample(color[t][c], positions[t] + row);
                Match forward = match(template, target, spread, margin, radius, range);
                Match reverse = match(target, template, spread, margin, radius, range);
                if (!forward.valid || !reverse.valid || Math.abs(forward.shift + reverse.shift) > .75) continue;
                next[t] += (forward.shift - reverse.shift) / 2;
                confirmed[t] = true;
            }
            positions = next;
        }
        for (int t = 0; t < n; t++) if (confirmed[t]) {
            result.refined[t] = positions[t];
            result.reason[t] += "; median neighbouring profile confirmed";
        }
        for (int t = 1; t < n; t++) result.shift[t] = result.refined[t] - result.refined[t - 1];
    }

    private static boolean inside(double y, int margin, int h) {
        return Double.isFinite(y) && y >= margin && y <= h - 1 - margin;
    }

    private static final class Match {
        double shift;
        boolean valid;
    }

    private static Match match(double[][] template, double[][] target, double[] spread, int center, int radius, double range) {
        int steps = (int) Math.floor(range / STEP), size = 2 * radius + 1;
        double[] weights = new double[size];
        double sigma = Math.max(3, Math.min(10, radius / 2.0)), weightSum = 0;
        for (int j = 0; j < size; j++) {
            double distance = (j - radius) / sigma;
            double scatter = spread[center - radius + j];
            weights[j] = (.001 + Math.exp(-.5 * distance * distance)) / (4 + scatter * scatter);
            weightSum += weights[j];
        }
        double[][] normalized = new double[2][size];
        double energy = 0;
        for (int c = 0; c < 2; c++) {
            double mean = 0;
            for (int j = 0; j < size; j++) mean += weights[j] * template[c][center - radius + j];
            mean /= weightSum;
            for (int j = 0; j < size; j++) {
                double v = template[c][center - radius + j] - mean;
                normalized[c][j] = v;
                energy += weights[j] * v * v;
            }
        }
        Match match = new Match();
        if (Math.sqrt(energy / (2 * weightSum)) < 2) return match;
        double[] scores = new double[2 * steps + 1];
        int best = 0;
        for (int k = 0; k < scores.length; k++) {
            double shift = (k - steps) * STEP, covariance = 0, targetEnergy = 0;
            for (int c = 0; c < 2; c++) {
                double mean = 0;
                for (int j = 0; j < size; j++) mean += weights[j] * sample(target[c], center - radius + j + shift);
                mean /= weightSum;
                for (int j = 0; j < size; j++) {
                    double v = sample(target[c], center - radius + j + shift) - mean;
                    covariance += weights[j] * normalized[c][j] * v;
                    targetEnergy += weights[j] * v * v;
                }
            }
            scores[k] = targetEnergy > 1e-12 ? covariance / Math.sqrt(energy * targetEnergy) : -1;
            if (scores[k] > scores[best]) best = k;
        }
        double other = -1;
        for (int k = 0; k < scores.length; k++)
            // The broader consensus profile has a wider correlation peak than
            // the narrow pair profiles. Test competing positions two pixels away.
            if (Math.abs(k - best) * STEP >= Math.min(2, range)) other = Math.max(other, scores[k]);
        match.shift = (best - steps) * STEP;
        match.valid = best > 0 && best < scores.length - 1 && scores[best] >= .90 && scores[best] - other >= .005;
        return match;
    }

    private static double sample(double[] values, double row) {
        int lo = (int) Math.floor(row);
        return values[lo] + (row - lo) * (values[Math.min(lo + 1, values.length - 1)] - values[lo]);
    }
}
