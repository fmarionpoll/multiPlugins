package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.EvaporationCurveFitter;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** Read-only adapter for the experimental gulp panel. No saved measures are replaced. */
public final class CorrelationGulpPreview {
    public final BufferedImage image;
    public final List<double[]> controlRates;
    public final IntakeEpisodes.EvaporationScale localScale;
    public final boolean adaptiveSearch, localEvaporation;
    public final double[] raw, minutes, evaporation, residualRate, correctedLevels;
    public final VerticalCorrelation.Result motion;
    public final VerticalCorrelation.Noise noise;
    public final int controlCount;
    public final boolean hasEvaporationReference;
    public final String referenceDescription;

    private CorrelationGulpPreview(BufferedImage image, double[] raw, double[] minutes,
            double[] evaporation, VerticalCorrelation.Result motion, double[] controls,
            int controlCount, boolean hasEvaporationReference, String referenceDescription,
            List<double[]> controlRates, IntakeEpisodes.EvaporationScale localScale, boolean adaptiveSearch, boolean localEvaporation) {
        this.controlRates = controlRates;
        this.localScale = localScale;
        this.adaptiveSearch = adaptiveSearch;
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

    public static CorrelationGulpPreview compute(Experiment exp, Capillary selected, int radius) {
        return compute(exp, selected, radius, VerticalCorrelation.DEFAULT_MAX_SHIFT);
    }

    public static CorrelationGulpPreview compute(Experiment exp, Capillary selected, int radius, double maxShift) {
        return compute(exp, selected, radius, maxShift, false, false);
    }

    public static CorrelationGulpPreview compute(Experiment exp, Capillary selected, int radius,
            double maxShift, boolean adaptive, boolean localEvaporation) {
        Sequence sequence = exp.getSeqKymos() == null ? null : exp.getSeqKymos().getSequence();
        if (sequence == null) throw new IllegalArgumentException("Open the experiment's kymographs first.");
        boolean resampled = exp.getActiveBinDescription() != null && exp.getActiveBinDescription().isKymoFromNormedBlue();
        double[] raw = levels(selected);
        if (raw == null || raw.length < 8) throw new IllegalArgumentException("Detect the top meniscus first (at least 8 samples).");
        FrameTimeScale scale = exp.getFrameTimeScale();
        if (scale == null || scale.isEmpty()) scale = FrameTimeScale.load(exp.getResultsDirectory());
        if (scale == null || scale.size() != raw.length)
            throw new IllegalArgumentException("Native frame timestamps must match the saved levels; downsampled kymographs are unsupported.");
        double[] minutes = scale.toMinutes();
        for (int i = 1; i < minutes.length; i++)
            if (!(minutes[i] > minutes[i - 1])) throw new IllegalArgumentException("Frame times must increase strictly.");
        BufferedImage image = image(sequence, selected, raw.length);
        VerticalCorrelation.Result motion = VerticalCorrelation.analyze(image, raw, radius, maxShift, adaptive);
        List<Capillary> controls = new ArrayList<>();
        List<double[]> curves = new ArrayList<>();
        double targetScale = pixelScale(exp, selected);
        for (Capillary cap : exp.getCapillaries().getList()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (cap == selected || cap.getNFlies() != 0) continue;
            double[] y = levels(cap);
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
            BufferedImage controlImage = image(sequence, cap, raw.length);
            VerticalCorrelation.Result controlMotion = VerticalCorrelation.analyze(controlImage, levels(cap), radius, maxShift, adaptive);
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
        if (resampled) description += "; resampled RGB: pixels are kymograph rows";
        if (localEvaporation) description += local.reliable ? String.format("; local evaporation x%.2f (%d quiet bins)", local.factor, local.quietIntervals)
                : "; local evaporation uncertain: common model retained";
        return new CorrelationGulpPreview(image, raw, minutes, reference, motion,
                controlResiduals.stream().mapToDouble(Double::doubleValue).toArray(), usableControls, !curves.isEmpty(), description, controlRates, local, adaptive, localEvaporation);
    }

    private static double[] levels(Capillary cap) {
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

    private static BufferedImage image(Sequence seq, Capillary cap, int width) {
        int index = cap.getKymographIndex();
        if (index < 0 || index >= seq.getSizeT()) throw new IllegalArgumentException("Kymograph not loaded: " + cap.getKymographName());
        IcyBufferedImage input = seq.getImage(index, 0);
        if (input == null || input.getSizeX() != width || input.getSizeC() < 3)
            throw new IllegalArgumentException("Expected a native RGB kymograph for " + cap.getKymographName());
        double[][] channels = new double[3][];
        for (int c = 0; c < 3; c++) channels[c] = Array1DUtil.arrayToDoubleArray(input.getDataXY(c), input.isSignedDataType());
        BufferedImage output = new BufferedImage(width, input.getSizeY(), BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[width * input.getSizeY()];
        for (int i = 0; i < pixels.length; i++) {
            int rgb = 0;
            for (int c = 0; c < 3; c++) {
                double v = channels[c][i];
                if (!Double.isFinite(v) || v < 0 || v > 255)
                    throw new IllegalArgumentException("This prototype requires RGB values between 0 and 255.");
                rgb = (rgb << 8) | (int) Math.round(v);
            }
            pixels[i] = rgb;
        }
        output.setRGB(0, 0, width, input.getSizeY(), pixels, 0, width);
        return output;
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
