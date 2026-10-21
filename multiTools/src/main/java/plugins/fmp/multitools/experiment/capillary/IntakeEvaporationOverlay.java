package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/** Display-only evaporation plus cumulative intake, anchored once at the first raw level. */
public final class IntakeEvaporationOverlay {
    // Shares the preview ROI token so viewer synchronization removes these too.
    // Contains no measure-import token such as "gulp", "level", or "derivative".
    public static final String ROI_TOKEN = CapillaryCorrelationGulps.ROI_TOKEN + "_evap";
    public static final Color COLOR = new Color(145, 145, 145);

    private IntakeEvaporationOverlay() { }

    public static List<List<Point2D>> paths(double[] amplitudes, double[] raw, double[] evaporation) {
        return paths(amplitudes, raw, evaporation, false);
    }

    /** Compensation affects this display only; amplitudes remain unchanged. */
    public static List<List<Point2D>> paths(double[] amplitudes, double[] raw, double[] evaporation,
            boolean subtractIntakeEvaporation) {
        if (amplitudes.length != raw.length || raw.length != evaporation.length)
            throw new IllegalArgumentException("Intake, level and evaporation arrays must have equal lengths.");
        List<List<Point2D>> paths = new ArrayList<>();
        if (raw.length == 0 || !Double.isFinite(raw[0]) || !Double.isFinite(evaporation[0])) return paths;
        List<Point2D> points = new ArrayList<>();
        double cumulative = 0;
        for (int i = 0; i < amplitudes.length; i++) {
            if (!Double.isFinite(amplitudes[i]) || amplitudes[i] < 0)
                throw new IllegalArgumentException("Intake amplitudes must be finite and nonnegative.");
            // No displacement interval precedes sample 0. Its coordinate is
            // exactly raw[0], even if an imported series has a nonzero bin 0.
            double amplitude = i == 0 ? 0 : amplitudes[i];
            if (subtractIntakeEvaporation && amplitude > 0) {
                // An unknown interval evaporation makes the cumulative net
                // intake unknown: do not silently resume with an invented offset.
                if (!Double.isFinite(evaporation[i]) || !Double.isFinite(evaporation[i - 1])) {
                    if (points.size() > 1) paths.add(points);
                    return paths;
                }
                amplitude = Math.max(0, amplitude - (evaporation[i] - evaporation[i - 1]));
            }
            if (Double.isFinite(evaporation[i])) {
                double before = raw[0] + (evaporation[i] - evaporation[0]) + cumulative;
                points.add(new Point2D.Double(i + .5, before));
                // A repeated X draws the complete downward step at the intake
                // bin; never move the model back onto a later measured level.
                if (amplitude > 0) points.add(new Point2D.Double(i + .5, before + amplitude));
            } else {
                if (points.size() > 1) paths.add(points);
                points = new ArrayList<>();
            }
            cumulative += amplitude;
        }
        if (points.size() > 1) paths.add(points);
        return paths;
    }

    public static List<ROI2D> createRois(String prefix, double[] amplitudes, double[] raw, double[] evaporation, int t) {
        return createRois(prefix, amplitudes, raw, evaporation, t, false);
    }

    public static List<ROI2D> createRois(String prefix, double[] amplitudes, double[] raw, double[] evaporation, int t,
            boolean subtractIntakeEvaporation) {
        List<ROI2D> rois = new ArrayList<>();
        for (List<Point2D> points : paths(amplitudes, raw, evaporation, subtractIntakeEvaporation)) {
            ROI2DPolyLine roi = new ROI2DPolyLine(new Level2D(points));
            int start = (int) Math.floor(points.get(0).getX());
            roi.setName(prefix + "_" + ROI_TOKEN + String.format(java.util.Locale.ROOT, "%07d", start));
            roi.setColor(COLOR);
            roi.setStroke(1);
            roi.setT(t);
            roi.setReadOnly(true);
            rois.add(roi);
        }
        return rois;
    }
}
