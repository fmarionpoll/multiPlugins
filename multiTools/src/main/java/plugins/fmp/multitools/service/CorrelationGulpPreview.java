package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.EvaporationCurveFitter;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** Read-only adapter for the experimental gulp panel. No saved measures are replaced. */
public final class CorrelationGulpPreview {
    private final java.util.Map<Capillary,double[]> sourceLevels = new java.util.IdentityHashMap<>();
    private final java.util.Map<Capillary,boolean[]> sourceQuality = new java.util.IdentityHashMap<>();
    public boolean matchesSources(Experiment experiment) {
        if(sourceLevels.size()!=experiment.getCapillaries().getList().size())return false;
        for(Capillary cap:experiment.getCapillaries().getList())
            if(!sourceLevels.containsKey(cap) || !Arrays.equals(sourceLevels.get(cap),levels(cap))
                    || !Arrays.equals(sourceQuality.get(cap),MeniscusRefinement.quality(cap))) return false;
        return true;
    }
    public final BufferedImage image;
    public final List<double[]> controlRates;
    public final IntakeEpisodes.EvaporationScale localScale;
    public final boolean localEvaporation;
    public final double[] raw, minutes, evaporation, residualRate, correctedLevels;
    public final VerticalCorrelation.Result motion;
    public final VerticalCorrelation.Noise noise;
    public final int controlCount;
    public final boolean hasEvaporationReference;
    public final String referenceDescription;
    private VerticalCorrelation.Result intakeImageSupport;

    /** Confirm a rescued boundary independently of its saved refinement flag.
     * For uncertain boundaries, also register across the short interval rather
     * than requiring the failing adjacent pair to succeed again.
     */
    public synchronized boolean supportsIntakeStep(int boundary) {
        if(intakeImageSupport==null) intakeImageSupport=VerticalCorrelation.analyze(image,raw,10,3,true);
        if(matchesIntakeShift(intakeImageSupport,boundary,raw[boundary]-raw[boundary-1])) return true;
        if(motion.reliable[boundary]) return false;
        int confirmed=0;
        for(int offset=1;offset<=3;offset++) {
            int a=boundary-1-offset,b=boundary+offset;
            if(a<0 || b>=raw.length || !motion.reliable[a] || !motion.reliable[b]) continue;
            BufferedImage pair=new BufferedImage(2,image.getHeight(),BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<image.getHeight();y++) {
                pair.setRGB(0,y,image.getRGB(a,y));pair.setRGB(1,y,image.getRGB(b,y));
            }
            VerticalCorrelation.Result support=VerticalCorrelation.analyze(pair,new double[]{raw[a],raw[b]},10,3,true);
            if(matchesIntakeShift(support,1,raw[b]-raw[a])) confirmed++;
        }
        return confirmed>=2;
    }

    private static boolean matchesIntakeShift(VerticalCorrelation.Result support,int index,double measured) {
        return support.reliable[index] && support.shift[index]>0
                && Math.abs(support.shift[index]-measured)<=Math.max(.75,.3*Math.abs(measured));
    }

    private CorrelationGulpPreview(BufferedImage image, double[] raw, double[] minutes,
            double[] evaporation, VerticalCorrelation.Result motion, double[] controls,
            int controlCount, boolean hasEvaporationReference, String referenceDescription,
            List<double[]> controlRates, IntakeEpisodes.EvaporationScale localScale, boolean localEvaporation) {
        this.controlRates = controlRates;
        this.localScale = localScale;
        this.localEvaporation = localEvaporation;
        this.image = image;
        this.raw = raw;
        this.minutes = minutes;
        this.evaporation = evaporation;
        this.motion = motion;
        this.controlCount = controlCount;
        this.hasEvaporationReference = hasEvaporationReference;
        this.referenceDescription = referenceDescription;
        correctedLevels = motion.refined.clone();
        for (int i = 0; i < correctedLevels.length; i++) correctedLevels[i] -= evaporation[i];
        residualRate = residualRate(motion, evaporation, minutes, 1);
        noise = VerticalCorrelation.estimateNoise(residualRate, motion.reliable, controls);
    }

    /** Phase 2 consumes accepted levels; refinement parameters do not participate. */
    public static CorrelationGulpPreview computeAccepted(Experiment exp, Capillary selected, boolean localEvaporation) {
        java.util.Map<Capillary,double[]> inputs=new java.util.IdentityHashMap<>();
        java.util.Map<Capillary,boolean[]> qualities=new java.util.IdentityHashMap<>();
        for(Capillary cap:exp.getCapillaries().getList()) {
            inputs.put(cap,levels(cap)); qualities.put(cap,MeniscusRefinement.quality(cap));
        }
        Sequence sequence = exp.getSeqKymos() == null ? null : exp.getSeqKymos().getSequence();
        if (sequence == null) throw new IllegalArgumentException("Open the experiment's kymographs first.");
        boolean resampled = exp.getActiveBinDescription() != null && exp.getActiveBinDescription().isKymoFromNormedBlue();
        double[] raw = inputs.get(selected);
        if (raw == null || raw.length < 8) throw new IllegalArgumentException("Detect the top meniscus first (at least 8 samples).");
        FrameTimeScale scale = exp.getFrameTimeScale();
        if (scale == null || scale.isEmpty()) scale = FrameTimeScale.load(exp.getResultsDirectory());
        if (scale == null || scale.size() != raw.length)
            throw new IllegalArgumentException("Native frame timestamps must match the saved levels; downsampled kymographs are unsupported.");
        double[] minutes = scale.toMinutes();
        for (int i = 1; i < minutes.length; i++)
            if (!(minutes[i] > minutes[i - 1])) throw new IllegalArgumentException("Frame times must increase strictly.");
        BufferedImage image = image(sequence, selected, raw.length);
        VerticalCorrelation.Result motion = VerticalCorrelation.fromLevels(raw, qualities.get(selected));
        List<Capillary> controls = new ArrayList<>();
        List<double[]> curves = new ArrayList<>();
        double targetScale = pixelScale(exp, selected);
        for (Capillary cap : exp.getCapillaries().getList()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (cap == selected || cap.getNFlies() != 0) continue;
            double[] y = inputs.get(cap);
            if(y!=null)y=y.clone();
            if (y == null || y.length != raw.length || !Double.isFinite(y[0])) continue;
            double factor = pixelScale(exp, cap) / targetScale;
            for (int i = y.length - 1; i >= 0; i--) y[i] = (y[i] - y[0]) * factor;
            controls.add(cap);
            curves.add(y);
        }
        double[] reference = reference(curves, minutes, raw.length);
        List<Double> controlResiduals = new ArrayList<>();
        List<double[]> controlRates = new ArrayList<>();
        int usableControls = 0;
        for (int c = 0; c < controls.size(); c++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            Capillary cap = controls.get(c);
            VerticalCorrelation.Result controlMotion = VerticalCorrelation.fromLevels(inputs.get(cap), qualities.get(cap));
            // A held-out control never supplies its own evaporation curve.
            List<double[]> others = new ArrayList<>(curves);
            others.remove(c);
            if (others.isEmpty()) continue;
            double[] ownReference = reference(others, minutes, raw.length);
            double[] residuals = residualRate(controlMotion, ownReference, minutes, pixelScale(exp, cap) / targetScale);
            if (localEvaporation) {
                double[] scaledRaw = curves.get(c).clone();
                double[] scaledMotion = controlMotion.shift.clone();
                double factor = pixelScale(exp, cap) / targetScale;
                for (int i = 0; i < scaledMotion.length; i++) scaledMotion[i] *= factor;
                VerticalCorrelation.Noise preliminary = VerticalCorrelation.estimateNoise(residuals, controlMotion.reliable, new double[0]);
                IntakeEpisodes.EvaporationScale local = IntakeEpisodes.evaporationScale(scaledRaw, scaledMotion,
                        ownReference, minutes, controlMotion.reliable, preliminary.sigma);
                for (int i = 0; i < ownReference.length; i++) ownReference[i] *= local.factor;
                residuals = residualRate(controlMotion, ownReference, minutes, factor);
            }
            controlRates.add(residuals.clone());
            int before = controlResiduals.size();
            for (int i = 1; i < residuals.length; i++)
                if (controlMotion.reliable[i] && Double.isFinite(residuals[i])) controlResiduals.add(residuals[i]);
            if (controlResiduals.size() > before) usableControls++;
        }
        double[] pooled = controlResiduals.stream().mapToDouble(Double::doubleValue).toArray();
        double[] preliminaryRate = residualRate(motion, reference, minutes, 1);
        VerticalCorrelation.Noise preliminary = VerticalCorrelation.estimateNoise(preliminaryRate, motion.reliable, pooled);
        IntakeEpisodes.EvaporationScale local = IntakeEpisodes.evaporationScale(raw, motion.shift, reference,
                minutes, motion.reliable, preliminary.sigma);
        if (localEvaporation) for (int i = 0; i < reference.length; i++) reference[i] *= local.factor;
        String description = controls.isEmpty() ? "No evaporation reference from no-fly controls (uncorrected motion)"
                : "Evaporation model from " + controls.size() + " no-fly controls; noise from " + usableControls;
        description += "; accepted topraw increments (no image correlation)";
        if (MeniscusRefinement.quality(selected) == null) description += "; correlation quality unavailable";
        if (resampled) description += "; resampled RGB: pixels are kymograph rows";
        if (localEvaporation) description += local.reliable ? String.format("; local evaporation x%.2f (%d quiet bins)", local.factor, local.quietIntervals)
                : "; local evaporation uncertain: common model retained";
        CorrelationGulpPreview result = new CorrelationGulpPreview(image, raw, minutes, reference, motion,
                controlResiduals.stream().mapToDouble(Double::doubleValue).toArray(), usableControls, !curves.isEmpty(), description, controlRates, local, localEvaporation);
        result.sourceLevels.putAll(inputs);result.sourceQuality.putAll(qualities);
        return result;
    }

    public static double[] levels(Capillary cap) {
        if (cap.getTopRaw() == null || cap.getTopRaw().polylineLevel == null) return null;
        Level2D level = cap.getTopRaw().polylineLevel;
        return Arrays.copyOf(level.ypoints, level.npoints);
    }

    public static double normalizedUlPerPixel(double volume, double expansion, int imageHeight) {
        if (!(volume > 0) || !Double.isFinite(volume) || expansion < 0 || !Double.isFinite(expansion) || imageHeight < 2)
            throw new IllegalArgumentException("Invalid calibration for resampled RGB kymograph.");
        // sampleArc includes both endpoints: there are height-1 spatial intervals.
        return volume * (1 + expansion) / (imageHeight - 1.0);
    }

    private static double pixelScale(Experiment exp, Capillary cap) {
        if (exp.getActiveBinDescription() != null && exp.getActiveBinDescription().isKymoFromNormedBlue()) {
            IcyBufferedImage input = exp.getSeqKymos().getSequence().getImage(cap.getKymographIndex(), 0);
            if (input == null) throw new IllegalArgumentException("Missing resampled kymograph: " + cap.getKymographName());
            return normalizedUlPerPixel(cap.getVolume(), CapillaryPixelScale.expansionRatioOf(exp), input.getSizeY());
        }
        double scale = CapillaryPixelScale.ulPerNativePixel(cap, 0, CapillaryPixelScale.sourceOf(exp),
                CapillaryPixelScale.expansionRatioOf(exp));
        if (!(scale > 0) || !Double.isFinite(scale)) throw new IllegalArgumentException("Invalid capillary pixel calibration.");
        return scale;
    }

    public static BufferedImage image(Sequence seq, Capillary cap, int width) {
        return MeniscusRefinement.image(seq,cap,width);
    }

    private static double[] residualRate(VerticalCorrelation.Result motion, double[] reference, double[] times, double factor) {
        double[] values = new double[times.length];
        Arrays.fill(values, Double.NaN);
        for (int i = 1; i < values.length; i++)
            if (motion.reliable[i]) values[i] = (factor * motion.shift[i] - (reference[i] - reference[i - 1])) / (times[i] - times[i - 1]);
        return values;
    }

    /** Fit on a uniform time grid, then interpolate back to actual acquisition times. */
    private static double[] reference(List<double[]> curves, double[] times, int n) {
        double[] mean = new double[n];
        if (curves.isEmpty()) return mean;
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (double[] curve : curves) if (Double.isFinite(curve[i])) { mean[i] += curve[i]; count++; }
            mean[i] = count == 0 ? Double.NaN : mean[i] / count;
        }
        double[] uniform = new double[n], grid = new double[n];
        for (int i = 0; i < n; i++) {
            grid[i] = times[0] + (times[n - 1] - times[0]) * i / (n - 1.0);
            uniform[i] = interpolate(times, mean, grid[i]);
        }
        Level2D fitted = EvaporationCurveFitter.fit(uniform);
        if (fitted == null) return mean;
        double[] result = new double[n];
        for (int i = 0; i < n; i++) result[i] = interpolate(grid, fitted.ypoints, times[i]);
        return result;
    }

    private static double interpolate(double[] x, double[] y, double t) {
        int hi = Arrays.binarySearch(x, t);
        if (hi >= 0) return y[hi];
        hi = -hi - 1;
        if (hi <= 0) return y[0];
        if (hi >= x.length) return y[x.length - 1];
        double f = (t - x[hi - 1]) / (x[hi] - x[hi - 1]);
        return y[hi - 1] * (1 - f) + y[hi] * f;
    }
}
