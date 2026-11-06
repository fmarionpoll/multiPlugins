package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import plugins.fmp.multitools.experiment.cages.EvaporationCurveFitter;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** User-seeded tracking in native image coordinates; smoothing is in the editable strip. */
public final class MeniscusReferenceTracking {
    private MeniscusReferenceTracking() { }
    private static final int HALF = 4, SEARCH = 4;

    public enum Model {
        EXPONENTIAL("Evaporation exponential"), LOCAL("Local straight-line fit");
        private final String label;
        Model(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static final class Result {
        public final double[] tracked, target, refined;
        public final boolean[] reliable;
        public int fixedColumns, linearFallbackSegments;
        private Result(int n) {
            tracked = new double[n]; target = new double[n]; refined = new double[n];
            reliable = new boolean[n];
            Arrays.fill(tracked, Double.NaN); Arrays.fill(target, Double.NaN);
        }
    }

    public static Result analyze(BufferedImage image, double[] levels, Map<Integer, Double> anchors,
            int smoothRadius, boolean[] fixed) {
        return analyze(image, levels, anchors, smoothRadius, fixed, Model.LOCAL);
    }

    public static Result analyze(BufferedImage image, double[] levels, Map<Integer, Double> anchors,
            int smoothRadius, boolean[] fixed, Model model) {
        if (image == null || levels == null || levels.length != image.getWidth()
                || anchors == null || anchors.isEmpty() || smoothRadius < 1 || model == null
                || (fixed != null && fixed.length != levels.length))
            throw new IllegalArgumentException("Select a reference feature first.");
        SortedMap<Integer, Double> seeds = new TreeMap<>(anchors);
        for (Map.Entry<Integer, Double> seed : seeds.entrySet()) {
            int x = seed.getKey(); double y = seed.getValue();
            if (x < 0 || x >= levels.length || !inside(image, y) || !Double.isFinite(levels[x]))
                throw new IllegalArgumentException("Reference point is too close to the image edge or has no topraw.");
            if (contrast(profile(image, x, y)) < 2)
                throw new IllegalArgumentException("Reference point has too little contrast; click an edge or dark line.");
        }
        Result r = new Result(levels.length);
        Integer[] columns = seeds.keySet().toArray(new Integer[0]);
        for (int k = 0; k < columns.length; k++) {
            int seed = columns[k];
            r.tracked[seed] = seeds.get(seed); r.reliable[seed] = true;
            int left = k == 0 ? 0 : (columns[k - 1] + seed) / 2 + 1;
            int right = k + 1 == columns.length ? levels.length - 1 : (seed + columns[k + 1]) / 2;
            follow(image, seed, seeds.get(seed), left, -1, levels, r);
            follow(image, seed, seeds.get(seed), right, 1, levels, r);
        }
        double[] offset = new double[levels.length];
        for (int i = 0; i < levels.length; i++) offset[i] = r.tracked[i] - levels[i];
        // Never fit across a missing track or across a manual block boundary.
        for (int start = 0; start < levels.length;) {
            if (!r.reliable[start] || !Double.isFinite(offset[start])) { start++; continue; }
            int end = start;
            while (end + 1 < levels.length && r.reliable[end + 1] && Double.isFinite(offset[end + 1])
                    && (fixed == null || fixed[end + 1] == fixed[end])) end++;
            int fitRadius = smoothRadius;
            if (model == Model.EXPONENTIAL && (fixed == null || !fixed[start])) {
                Level2D fitted = EvaporationCurveFitter.fitWithOffset(Arrays.copyOfRange(offset, start, end+1));
                if (fitted != null) {
                    System.arraycopy(fitted.ypoints, 0, r.target, start, end-start+1);
                    start = end+1;
                    continue;
                }
                // Short segments still get one smooth line, never a wiggly moving fit.
                fitRadius = end-start;
                r.linearFallbackSegments++;
            }
            for (int i = start; i <= end; i++) {
                // Local linear regression preserves slopes, including at the ends.
                int lo = Math.max(start, i - fitRadius), hi = Math.min(end, i + fitRadius);
                double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
                for (int j = lo; j <= hi; j++) {
                    double x = j - i, w = 1.0; sw += w; sx += w*x;
                    sy += w*offset[j]; sxx += w*x*x; sxy += w*x*offset[j];
                }
                double det = sw*sxx - sx*sx;
                r.target[i] = det > 1e-10 ? (sy*sxx - sx*sxy)/det : offset[i];
            }
            start = end + 1;
        }
        for (int i = 0; i < levels.length; i++) {
            r.refined[i] = levels[i];
            if (fixed != null && fixed[i] && r.reliable[i]) { r.target[i] = offset[i]; r.fixedColumns++; }
            if (r.reliable[i] && Double.isFinite(r.target[i]) && (fixed == null || !fixed[i])) {
                double y = r.tracked[i] - r.target[i];
                if (y >= 0 && y <= image.getHeight() - 1) r.refined[i] = y;
                else r.reliable[i] = false;
            }
        }
        return r;
    }

    private static void follow(BufferedImage image, int seed, double y, int end, int direction,
            double[] levels, Result r) {
        double[] template = profile(image, seed, y);
        for (int x = seed + direction; direction > 0 ? x <= end : x >= end; x += direction) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (!Double.isFinite(levels[x])) break;
            double best = -2, position = y;
            for (int j = 0; j <= 2*SEARCH*4; j++) {
                double candidate = y - SEARCH + j*.25;
                double score = inside(image, candidate) ? score(template, profile(image, x, candidate)) : -2;
                if (score > best) { best = score; position = candidate; }
            }
            // Stop rather than transferring the lock to an unrelated feature.
            if (best < .80 || Math.abs(position - y) >= SEARCH) break;
            y = position; r.tracked[x] = y; r.reliable[x] = true;
        }
    }

    private static boolean inside(BufferedImage image, double y) {
        return Double.isFinite(y) && y >= HALF && y <= image.getHeight() - 1 - HALF;
    }

    private static double[] profile(BufferedImage image, int x, double y) {
        double[] p = new double[(2*HALF + 1)*3];
        for (int row = -HALF; row <= HALF; row++) {
            double pos = y + row; int lo = (int)Math.floor(pos), hi = Math.min(lo+1, image.getHeight()-1);
            int a = image.getRGB(x, lo), b = image.getRGB(x, hi);
            for (int c = 0; c < 3; c++) {
                int shift = c*8;
                p[(row+HALF)*3+c] = ((a>>shift)&255)*(1-(pos-lo)) + ((b>>shift)&255)*(pos-lo);
            }
        }
        // Remove per-channel offsets, retaining luminance edges as well as colour edges.
        for (int c = 0; c < 3; c++) {
            double mean = 0;
            for (int j = c; j < p.length; j += 3) mean += p[j];
            mean /= 2*HALF+1;
            for (int j = c; j < p.length; j += 3) p[j] -= mean;
        }
        return p;
    }

    private static double contrast(double[] a) {
        double sum = 0; for (double v : a) sum += v*v;
        return Math.sqrt(sum/a.length);
    }

    private static double score(double[] a, double[] b) {
        if (contrast(b) < 2) return -2;
        double ab = 0, aa = 0, bb = 0;
        for (int j = 0; j < a.length; j++) { ab += a[j]*b[j]; aa += a[j]*a[j]; bb += b[j]*b[j]; }
        return ab/Math.sqrt(aa*bb);
    }
}
