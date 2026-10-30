package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Experimental, signed image registration. Positive displacement is down. */
public final class VerticalCorrelation {
    private VerticalCorrelation() { }
    public enum Profile { NONE, FULL, BELOW_MENISCUS, ABOVE_MENISCUS }

    public static final class Result {
        public final double[] shift, correlation, refined, searchRange;
        /** Image confidence for analyze(); increment usability for fromLevels().
         * toprawFallback distinguishes unconfirmed but usable saved increments. */
        public final boolean[] reliable;
        /** Image support is separate from usability of saved topraw increments. */
        public final Profile[] profile;
        public final boolean[] toprawFallback;
        public final String[] reason;
        public final int[] neighbourSupport;
        public final double[] fitResidual;

        Result(int n) {
            searchRange = new double[n];
            shift = new double[n];
            correlation = new double[n];
            refined = new double[n];
            reliable = new boolean[n];
            profile = new Profile[n];
            Arrays.fill(profile,Profile.NONE);
            toprawFallback = new boolean[n];
            reason = new String[n];
            neighbourSupport=new int[n];
            fitResidual=new double[n];Arrays.fill(fitResidual,Double.NaN);
            Arrays.fill(shift, Double.NaN);
            Arrays.fill(correlation, Double.NaN);
            Arrays.fill(reason, "No previous sample");
        }
    }

    /** Phase 2: increments of one accepted trajectory, without image registration. */
    public static Result fromLevels(double[] levels, boolean[] quality) {
        Result result = new Result(levels.length);
        Arrays.fill(result.searchRange,Double.NaN);
        System.arraycopy(levels, 0, result.refined, 0, levels.length);
        for (int i=1; i<levels.length; i++) {
            result.shift[i] = levels[i] - levels[i-1];
            result.reliable[i] = Double.isFinite(result.shift[i]) && (quality == null || quality[i]);
            result.reason[i] = !Double.isFinite(result.shift[i]) ? "Missing accepted level"
                    : quality == null ? "Saved level; correlation quality unavailable"
                    : quality[i] ? "Accepted refinement" : "Uncertain refinement";
        }
        return result;
    }

    /** Border-only revalidation of old uncertain flags. Never changes saved levels
     * or flags. An unconfirmed but finite topraw increment remains usable with an
     * explicit fallback reason; reliable contradictory image motion still vetoes it.
     */
    public static Result fromLevels(double[] levels, boolean[] quality, BufferedImage image, int radius) {
        if(image==null || image.getWidth()!=levels.length || radius<2)
            throw new IllegalArgumentException("Native kymograph columns must match the saved levels.");
        Result result=fromLevels(levels,quality);
        Result evidence=null;
        for(int i=1;i<levels.length;i++) {
            if(result.reliable[i] || !inside(levels[i-1],image.getHeight())
                    || !inside(levels[i],image.getHeight())
                    || !nearBorder(levels[i-1],levels[i],radius,image.getHeight())) continue;
            if(evidence==null) evidence=analyzeSeeded(image,levels,radius,3,true);
            result.profile[i]=evidence.profile[i];
            result.correlation[i]=evidence.correlation[i];
            result.searchRange[i]=evidence.searchRange[i];
            if(evidence.reliable[i]) {
                // Two integer level positions can differ from subpixel motion
                // by one rounding pixel, plus one registration grid step.
                boolean integerLevels=levels[i]==Math.rint(levels[i]) && levels[i-1]==Math.rint(levels[i-1]);
                double tolerance=integerLevels?1+SHIFT_STEP:.75;
                boolean agrees=Math.abs(evidence.shift[i]-result.shift[i])
                        <=Math.max(tolerance,.3*Math.abs(result.shift[i]));
                result.reliable[i]=agrees;
                result.reason[i]=agrees ? "Saved topraw: " + (evidence.reason[i].isEmpty()
                        ? "full correlation confirmed" : evidence.reason[i])
                        : "Image motion contradicts saved topraw";
            } else {
                result.reliable[i]=true;
                result.toprawFallback[i]=true;
                result.reason[i]="Saved topraw fallback at image border (image unconfirmed): "+evidence.reason[i];
            }
        }
        return result;
    }

    private static boolean inside(double center,int height) {
        return Double.isFinite(center) && center>=0 && center<=height-1;
    }

    private static boolean nearBorder(double a,double b,int radius,int height) {
        return Math.min(a,b)<=radius || Math.max(a,b)>=height-1-radius;
    }

    private static final class Match {
        double shift, correlation, contrast, separation;
        boolean atLimit, imageLimit;
    }

    static final class Registration {
        Match forward,reverse;
        double range;
        String reason;
        Profile profile=Profile.FULL;
        double shift() {return (forward.shift-reverse.shift)/2;}
        double correlation() {return Math.min(forward.correlation,reverse.correlation);}
        double separation() {return Math.min(forward.separation,reverse.separation);}
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
        return analyze(image, raw, radius, maxShift, adaptive, false);
    }

    /** Search for a correction around the displacement predicted by saved levels. */
    public static Result analyzeSeeded(BufferedImage image, double[] raw, int radius, double correction, boolean adaptive) {
        return analyze(image, raw, radius, correction, adaptive, true);
    }

    private static Result analyze(BufferedImage image, double[] raw, int radius, double maxShift, boolean adaptive, boolean seeded) {
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
            double prediction = seeded ? raw[t] - raw[t-1] : 0;
            Registration registration=register(color[t-1],color[t],raw[t-1],raw[t],
                    -radius,radius,maxShift,prediction,adaptive);
            result.profile[t]=Profile.FULL;
            boolean border=inside(raw[t-1],h) && inside(raw[t],h) && nearBorder(raw[t-1],raw[t],radius,h);
            if(!registration.reason.isEmpty() && border) {
                boolean below=Math.min(raw[t-1],raw[t])<=radius;
                // Keep two available rows across the meniscus when possible.
                // A pure half of a smooth edge can have an ambiguous correlation
                // peak. Leave one border row free so the true match is not
                // forced onto the image-boundary end of the search.
                int across=below ? (int)Math.floor(Math.min(raw[t-1],raw[t]))-1
                        : (int)Math.floor(h-1-Math.max(raw[t-1],raw[t]))-1;
                across=Math.max(0,Math.min(2,across));
                int low=below?-across:-radius, high=below?radius:across;
                registration=register(color[t-1],color[t],raw[t-1],raw[t],
                        low,high,maxShift,prediction,adaptive);
                result.profile[t]=below?Profile.BELOW_MENISCUS:Profile.ABOVE_MENISCUS;
            }
            result.searchRange[t]=registration.range;
            if(registration.forward!=null && registration.reverse!=null) {
                result.shift[t]=(registration.forward.shift-registration.reverse.shift)/2;
                result.correlation[t]=Math.min(registration.forward.correlation,registration.reverse.correlation);
            }
            result.reliable[t]=registration.reason.isEmpty();
            result.reason[t]=registration.reason;
            if(result.reliable[t] && result.profile[t]!=Profile.FULL)
                result.reason[t]="One-sided correlation " +(result.profile[t]==Profile.BELOW_MENISCUS
                        ? "below meniscus" : "above meniscus");
            if(!result.reliable[t] && border) {
                result.toprawFallback[t]=true;
                result.reason[t]="Saved topraw fallback at image border: "+registration.reason;
            }
        }
        double[] refined = refine(raw, result.shift, result.reliable, .05);
        System.arraycopy(refined, 0, result.refined, 0, n);
        for(int i=1;i<n;i++) if(result.toprawFallback[i]) {
            result.refined[i]=raw[i];
            if(i==1) result.refined[0]=raw[0];
        }
        return result;
    }

    private static Registration register(double[][] previous,double[][] current,double a,double b,
            int low,int high,double maxShift,double prediction,boolean adaptive) {
        return register(previous,current,a,b,low,high,maxShift,prediction,adaptive,false);
    }

    /** Independent, centre-weighted image evidence for the refinement graph. */
    static Registration neighbourPair(double[][] previous,double[][] current,double a,double b,
            int radius,double correction,boolean adaptive) {
        Registration r=register(previous,current,a,b,-radius,radius,correction,b-a,adaptive,true);
        int h=previous[0].length;
        if(!r.reason.isEmpty() && inside(a,h) && inside(b,h) && nearBorder(a,b,radius,h)) {
            boolean below=Math.min(a,b)<=radius;
            int across=below?(int)Math.floor(Math.min(a,b))-1:(int)Math.floor(h-1-Math.max(a,b))-1;
            across=Math.max(0,Math.min(2,across));
            r=register(previous,current,a,b,below?-across:-radius,below?radius:across,
                    correction,b-a,adaptive,true);
            r.profile=below?Profile.BELOW_MENISCUS:Profile.ABOVE_MENISCUS;
        }
        return r;
    }

    private static Registration register(double[][] previous,double[][] current,double a,double b,
            int low,int high,double maxShift,double prediction,boolean adaptive,boolean weighted) {
        Registration r=new Registration();r.range=maxShift;
        double ceiling=Math.max(maxShift,Math.min(64,4*maxShift));
        do {
            r.forward=match(previous,current,a,low,high,r.range,prediction,weighted);
            r.reverse=match(current,previous,b,low,high,r.range,-prediction,weighted);
            if(!adaptive || r.forward==null || r.reverse==null || r.range>=ceiling
                    || !((r.forward.atLimit&&!r.forward.imageLimit)||(r.reverse.atLimit&&!r.reverse.imageLimit))) break;
            r.range=Math.min(ceiling,r.range*1.5);
        } while(true);
        Match f=r.forward,v=r.reverse;
        r.reason=f==null || v==null ? "Image border or flat profile"
                : f.imageLimit || v.imageLimit ? "Image border"
                : f.atLimit || v.atLimit ? "Search limit"
                : Math.min(f.contrast,v.contrast)<2 ? "Low contrast"
                : Math.min(f.correlation,v.correlation)<.90 ? "Weak correlation"
                : Math.min(f.separation,v.separation)<.005 ? "Broad correlation peak"
                : Math.abs(f.shift+v.shift)>.75 ? "Forward/reverse disagreement" : "";
        return r;
    }

    private static Match match(double[][] previous, double[][] current, double center, int low, int high,
            double maxShift, double prediction,boolean weighted) {
        int h = previous[0].length, count = high-low+1;
        // Only the unshifted template must fit in full. A wide requested search
        // must not reject a valid small displacement near an image border.
        if (count<5 || center+low<0 || center+high>h-1) return null;
        int requestedFirst = (int) Math.ceil((prediction-maxShift) / SHIFT_STEP - 1e-9);
        int requestedLast = (int) Math.floor((prediction+maxShift) / SHIFT_STEP + 1e-9);
        int firstStep = Math.max(requestedFirst, (int) Math.ceil((-low - center) / SHIFT_STEP - 1e-9));
        int lastStep = Math.min(requestedLast, (int) Math.floor((h - 1 - high - center) / SHIFT_STEP + 1e-9));
        if (lastStep <= firstStep) return null;
        double[] weights=new double[count];double weightSum=0;
        double sigma=Math.max(2,Math.min(3,(high-low)/6.0));
        for(int j=0;j<count;j++) {
            double distance=(low+j)/sigma;
            weights[j]=weighted?.001+.999*Math.exp(-.5*distance*distance):1;
            weightSum+=weights[j];
        }
        double[][] template = new double[2][count];
        double energy = 0;
        for (int c = 0; c < 2; c++) {
            double mean = 0;
            for (int j = 0; j < count; j++) {template[c][j]=sample(previous[c],center+low+j);mean+=weights[j]*template[c][j];}
            mean /= weightSum;
            for (int j = 0; j < count; j++) {
                template[c][j] -= mean;
                energy += weights[j]*template[c][j] * template[c][j];
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
                for (int j = 0; j < count; j++) {values[j]=sample(current[c],center+low+j+shift);mean+=weights[j]*values[j];}
                mean /= weightSum;
                for (int j = 0; j < count; j++) {
                    double v = values[j] - mean;
                    numerator += weights[j]*v * template[c][j];
                    targetEnergy += weights[j]*v * v;
                }
            }
            scores[k] = targetEnergy > 1e-12 ? numerator / Math.sqrt(energy * targetEnergy) : -1;
            if (scores[k] > scores[best]) best = k;
        }
        Match m = new Match();
        m.shift = (firstStep + best) * SHIFT_STEP;
        m.correlation = scores[best];
        m.contrast = Math.sqrt(energy / (2 * weightSum));
        m.atLimit = best == 0 || best == scores.length - 1;
        m.imageLimit = (best == 0 && firstStep > requestedFirst)
                || (best == scores.length - 1 && lastStep < requestedLast);
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
