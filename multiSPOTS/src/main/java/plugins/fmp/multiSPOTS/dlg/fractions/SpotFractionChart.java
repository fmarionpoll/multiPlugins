package plugins.fmp.multiSPOTS.dlg.fractions;

import java.awt.BasicStroke;
import java.awt.Color;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Row;
import plugins.fmp.multitools.experiment.spot.measurement.*;

/** Fraction charts preserve missing values, negative consumption, and explicit estimated segments. */
public final class SpotFractionChart {
    public enum View { RAW_MEASUREMENT, VALID_MEASUREMENTS, FRACTION_REMAINING, FRACTION_CONSUMED, ILLUMINATION_FACTOR, SUPPORTING_SPOTS }
    private SpotFractionChart() { }

    public static XYSeriesCollection data(Row row, View view, boolean connectSimilar) {
        double[] values;
        switch (view) {
        case RAW_MEASUREMENT: values = row.input.result.getQuantity(row.key.quantity).getRawValues(); break;
        case VALID_MEASUREMENTS:
            values = row.input.result.getQuantity(row.key.quantity).getRawValues();
            int[] flags = row.input.result.getQuantity(row.key.quantity).getEffectiveFlags();
            for (int i = 0; i < values.length; i++) if (flags[i] != 0) values[i] = Double.NaN;
            break;
        case FRACTION_REMAINING: values = row.remaining(); break;
        case FRACTION_CONSUMED: values = row.consumed(); break;
        case ILLUMINATION_FACTOR: values = row.factors(); break;
        default:
            int[] support = row.support(); values = new double[support.length];
            for (int i = 0; i < values.length; i++) values[i] = support[i];
        }
        boolean fraction = view == View.FRACTION_REMAINING || view == View.FRACTION_CONSUMED;
        boolean[] estimated = fraction && row.fractions != null ? row.fractions.getEstimatedSamples() : new boolean[values.length];
        double[] visual = values.clone();
        if (fraction && connectSimilar && row.fractions != null) {
            double[] times = row.input.result.getTimesSeconds();
            for (SpotDerivedSeries.Gap gap : row.fractions.getGaps()) {
                if (!gap.connectableForDisplay) continue;
                int left = gap.firstMissing - 1, right = gap.lastMissing + 1;
                for (int i = gap.firstMissing; i <= gap.lastMissing; i++) if (!Double.isFinite(visual[i])) {
                    visual[i] = values[left] + (values[right] - values[left]) * (times[i] - times[left]) / (times[right] - times[left]);
                    estimated[i] = true; // Display metadata only; the calculation's NaNs remain unchanged.
                }
            }
        }
        XYSeries observed = new XYSeries("Observations", false), filled = new XYSeries("Estimated / visual connection", false);
        double[] times = row.input.result.getTimesSeconds();
        for (int i = 0; i < values.length; i++) {
            observed.add(times[i] / 60, estimated[i] || !Double.isFinite(values[i]) ? null : Double.valueOf(values[i]));
            boolean anchor = estimated[i] || (i > 0 && estimated[i - 1]) || (i + 1 < estimated.length && estimated[i + 1]);
            filled.add(times[i] / 60, anchor && Double.isFinite(visual[i]) ? Double.valueOf(visual[i]) : null);
        }
        XYSeriesCollection data = new XYSeriesCollection(); data.addSeries(observed); data.addSeries(filled); return data;
    }

    public static JFreeChart create(Row row, View view, boolean connectSimilar) {
        String unit = row.input.result.getQuantity(row.key.quantity).getUnit();
        String label = view == View.FRACTION_CONSUMED ? "Fraction consumed (1 - signal/reference)"
                : view == View.FRACTION_REMAINING ? "Fraction remaining (signal/reference)"
                : view == View.ILLUMINATION_FACTOR ? "Estimated multiplicative drift factor"
                : view == View.SUPPORTING_SPOTS ? "Supporting spots (count)" : "Measurement (" + unit.replace('_', ' ') + ")";
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesPaint(0, new Color(40, 80, 120)); renderer.setSeriesPaint(1, new Color(160, 95, 30));
        renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] { 5, 4 }, 0));
        int[] flags = row.input.result.getQuantity(row.key.quantity).getEffectiveFlags();
        renderer.setDefaultToolTipGenerator((dataset, series, item) -> "Time " + dataset.getXValue(series, item)
                + " min; value " + dataset.getY(series, item) + "; " + validity(flags[item])
                + (series == 1 ? "; estimated or display-only connection" : ""));
        XYPlot measures = new XYPlot(data(row, view, connectSimilar), null, new NumberAxis(label), renderer);
        XYSeries fly = new XYSeries("Fly evidence", false); double[] times = row.input.result.getTimesSeconds(), evidence = row.input.result.getFlyEvidence();
        for (int i = 0; i < times.length; i++) fly.add(times[i] / 60, Double.isFinite(evidence[i]) ? Double.valueOf(evidence[i]) : null);
        XYPlot occupancy = new XYPlot(new XYSeriesCollection(fly), null,
                new NumberAxis("Fly (" + row.input.result.getFlyEvidenceUnit().replace('_', ' ') + ")"), new XYLineAndShapeRenderer(true, false));
        CombinedDomainXYPlot combined = new CombinedDomainXYPlot(new NumberAxis("Elapsed time (minutes)"));
        combined.add(measures, 3); combined.add(occupancy, 1);
        String status = row.excluded ? " — excluded deposit" : !row.issues.isEmpty() ? " — review required" : "";
        if (row.reference != null && !row.excluded) {
            if (row.reference.kind == plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind.DELAYED)
                status += " — delayed reference at " + row.reference.timeSeconds + " s";
            else if (row.reference.kind == plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind.BORROWED
                    || row.reference.kind == plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind.PRECONSUMED)
                status += " — estimated initial reference";
        }
        return new JFreeChart(row.input.name + status, JFreeChart.DEFAULT_TITLE_FONT, combined, true);
    }
    private static String validity(int flags) {
        if (flags == 0) return "valid observation";
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if ((flags & SpotSampleFlags.OCCLUDED) != 0) reasons.add("occluded");
        if ((flags & SpotSampleFlags.MISSING_IMAGE) != 0) reasons.add("missing image");
        if ((flags & SpotSampleFlags.DETECTION_FAILED) != 0) reasons.add("detection failed");
        if ((flags & SpotSampleFlags.INVALID_BACKGROUND) != 0) reasons.add("background unreliable");
        if ((flags & SpotSampleFlags.MANUALLY_REJECTED) != 0) reasons.add("manually rejected");
        if ((flags & SpotSampleFlags.QUALITY_UNKNOWN) != 0) reasons.add("quality unknown");
        return String.join(", ", reasons);
    }
}
