package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/** Display-only evaporation projections, restarted at each intake's lower endpoint. */
public final class IntakeEvaporationOverlay {
    // Shares the preview ROI token so viewer synchronization removes these too.
    // Contains no measure-import token such as "gulp", "level", or "derivative".
    public static final String ROI_TOKEN = CapillaryCorrelationGulps.ROI_TOKEN + "_evap";
    public static final Color COLOR = new Color(145, 145, 145);

    private IntakeEvaporationOverlay() { }

    public static List<List<Point2D>> paths(boolean[] intakes, double[] raw, double[] evaporation) {
        if (intakes.length != raw.length || raw.length != evaporation.length)
            throw new IllegalArgumentException("Intake, level and evaporation arrays must have equal lengths.");
        List<List<Point2D>> paths = new ArrayList<>();
        for (int start = 0; start < intakes.length; start++) {
            if (!intakes[start] || !Double.isFinite(raw[start]) || !Double.isFinite(evaporation[start])) continue;
            int end = start + 1;
            while (end < intakes.length && !intakes[end]) end++;
            end = Math.min(end, intakes.length - 1);
            List<Point2D> points = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                if (!Double.isFinite(evaporation[i])) break;
                // Increasing image Y is downward. Use model increments rather
                // than a constant slope so actual frame timing/curvature remain.
                points.add(new Point2D.Double(i + .5, raw[start] + evaporation[i] - evaporation[start]));
            }
            if (points.size() > 1) paths.add(points);
        }
        return paths;
    }

    public static List<ROI2D> createRois(String prefix, boolean[] intakes, double[] raw, double[] evaporation, int t) {
        List<ROI2D> rois = new ArrayList<>();
        for (List<Point2D> points : paths(intakes, raw, evaporation)) {
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
