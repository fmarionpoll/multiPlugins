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
        public final int firstColumn, lastColumn;
        public int fixedColumns, linearFallbackSegments;
        private Result(int n, int firstColumn, int lastColumn) {
            this.firstColumn = firstColumn;
            this.lastColumn = lastColumn;
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
        return analyze(image, levels, anchors, smoothRadius, fixed, model, 0, levels == null ? -1 : levels.length - 1);
    }

    /** Track and fit only the inclusive range, using anchors in that range. */
    public static Result analyze(BufferedImage image, double[] levels, Map<Integer, Double> anchors,
            int smoothRadius, boolean[] fixed, Model model, int first, int last) {
        if (image == null || levels == null || levels.length != image.getWidth()
                || anchors == null || anchors.isEmpty() || smoothRadius < 1 || model == null
                || (fixed != null && fixed.length != levels.length))
            throw new IllegalArgumentException("Select a reference feature first.");
        if (first < 0 || last < first || last >= levels.length)
            throw new IllegalArgumentException("Reference range must be within the record.");
        SortedMap<Integer, Double> seeds = new TreeMap<>();
        for (Map.Entry<Integer, Double> seed : anchors.entrySet()) {
            int x = seed.getKey(); double y = seed.getValue();
            if (x < 0 || x >= levels.length)
                throw new IllegalArgumentException("Reference point is outside the record.");
            if (x < first || x > last) continue;
            if (!inside(image, y) || !Double.isFinite(levels[x]))
                throw new IllegalArgumentException("Reference point is too close to the image edge or has no topraw.");
            if (contrast(profile(image, x, y)) < 2)
                throw new IllegalArgumentException("Reference point has too little contrast; click an edge or dark line.");
            seeds.put(x, y);
        }
        if (seeds.isEmpty())
            throw new IllegalArgumentException("Place at least one reference point within the selected range.");
        Result r = new Result(levels.length, first, last);
        Integer[] columns = seeds.keySet().toArray(new Integer[0]);
        for (int k = 0; k < columns.length; k++) {
            int seed = columns[k];
            r.tracked[seed] = seeds.get(seed); r.reliable[seed] = true;
            int left = k == 0 ? first : (columns[k - 1] + seed) / 2 + 1;
            int right = k + 1 == columns.length ? last : (seed + columns[k + 1]) / 2;
            follow(image, seed, seeds.get(seed), left, -1, levels, r);
            follow(image, seed, seeds.get(seed), right, 1, levels, r);
        }
        double[] offset = new double[levels.length];
        for (int i = first; i <= last; i++) offset[i] = r.tracked[i] - levels[i];
        // Never fit across a missing track or across a manual block boundary.
        for (int start = first; start <= last;) {
            if (!r.reliable[start] || !Double.isFinite(offset[start])) { start++; continue; }
            int end = start;
            while (end < last && r.reliable[end + 1] && Double.isFinite(offset[end + 1])
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
        System.arraycopy(levels, 0, r.refined, 0, levels.length);
        for (int i = first; i <= last; i++) {
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
            double position = match(image, x, y, SEARCH, template);
            if (!Double.isFinite(position)) break;
            y = position; r.tracked[x] = y; r.reliable[x] = true;
        }
    }

    /** Match independently in each column near the closest anchor of a fixed feature. */
    static double[] trackFixedFeature(BufferedImage image, Map<Integer, Double> anchors, int first, int last,
            int searchRadius, boolean useNearbyEdges) {
        SortedMap<Integer, Double> seeds = new TreeMap<>();
        for (Map.Entry<Integer, Double> anchor : anchors.entrySet()) {
            int x = anchor.getKey();
            if (x < first || x > last) continue;
            double y = anchor.getValue();
            if (!inside(image, y))
                throw new IllegalArgumentException("Place reference points at least four pixels from the top and bottom.");
            if (!useNearbyEdges && contrast(profile(image, x, y)) < 2)
                throw new IllegalArgumentException("Reference point has too little contrast. Click an edge or dark line.");
            seeds.put(x, y);
        }
        if (seeds.isEmpty())
            throw new IllegalArgumentException("Place at least one reference point within the selected range.");

        double[] tracked = new double[image.getWidth()];
        Arrays.fill(tracked, Double.NaN);
        int start = first;
        Integer[] columns = seeds.keySet().toArray(new Integer[0]);
        for (int i = 0; i < columns.length; i++) {
            int seed = columns[i];
            int end = i + 1 == columns.length ? last : seed + (columns[i + 1] - seed) / 2;
            double center = seeds.get(seed);
            double[] template = profile(image, seed, center);
            double[] rows = useNearbyEdges ? referenceRows(image, seed, center) : new double[] { center };
            double[][] templates = new double[rows.length][];
            for (int j = 0; j < rows.length; j++)
                templates[j] = rows[j] == center ? template : profile(image, seed, rows[j]);
            double[] votes = new double[rows.length];
            for (int x = start; x <= end; x++) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                double shift = x == seed ? 0 : consensusShift(image, x, searchRadius, rows, templates, votes);
                tracked[x] = center + shift;
            }
            start = end + 1;
        }
        return tracked;
    }

    private static double[] referenceRows(BufferedImage image, int x, double center) {
        // Use spatially distinct profiles so a change in one edge cannot outvote the rest.
        double[] rows = new double[7];
        double[] strengths = new double[rows.length];
        int count = 0;
        double primary = contrast(profile(image, x, center));
        if (primary >= 2) {
            rows[count] = center;
            strengths[count++] = primary;
        }
        for (int side : new int[] { -1, 1 }) {
            for (int band = 1; band <= 3; band++) {
                double strongest = 3;
                double row = Double.NaN;
                for (int dy = 10 * band - 2; dy <= 10 * band + 2; dy++) {
                    double candidate = center + side * dy;
                    if (!inside(image, candidate))
                        continue;
                    double energy = contrast(profile(image, x, candidate));
                    if (energy > strongest) {
                        strongest = energy;
                        row = candidate;
                    }
                }
                if (Double.isFinite(row) && (count == 0 || Math.abs(row - rows[count - 1]) >= 8)) {
                    rows[count] = row;
                    strengths[count++] = strongest;
                }
            }
        }
        if (count == 0)
            throw new IllegalArgumentException("Too little contrast near the reference. Click a fixed edge or dark band.");
        double minimumStrength = .25 * Arrays.stream(strengths).max().getAsDouble();
        int retained = 0;
        for (int j = 0; j < count; j++) {
            if (strengths[j] >= minimumStrength)
                rows[retained++] = rows[j];
        }
        return Arrays.copyOf(rows, retained);
    }

    private static double consensusShift(BufferedImage image, int x, int radius, double[] rows,
            double[][] templates, double[] votes) {
        int count = 0;
        for (int j = 0; j < rows.length; j++) {
            double position = match(image, x, rows[j], radius, templates[j]);
            if (Double.isFinite(position))
                votes[count++] = position - rows[j];
        }
        if (count == 0 || count * 2 <= rows.length)
            return Double.NaN;
        Arrays.sort(votes, 0, count);
        double median = (votes[(count - 1) / 2] + votes[count / 2]) / 2;
        int agreeing = 0;
        for (int j = 0; j < count; j++) {
            if (Math.abs(votes[j] - median) <= .5)
                agreeing++;
        }
        return agreeing * 2 > rows.length ? median : Double.NaN;
    }

    private static double match(BufferedImage image, int x, double center, int radius, double[] template) {
        double best = -2, position = center;
        for (int j = -4 * radius; j <= 4 * radius; j++) {
            double candidate = center + j * .25;
            double correlation = inside(image, candidate) ? score(template, profile(image, x, candidate)) : -2;
            if (correlation > best + 1e-9 || (Math.abs(correlation - best) <= 1e-9
                    && Math.abs(candidate - center) < Math.abs(position - center))) {
                best = correlation;
                position = candidate;
            }
        }
        return best >= .80 && Math.abs(position - center) < radius ? position : Double.NaN;
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
