package plugins.fmp.multitools.tools.chart;

import java.util.*;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

/** Acquisition-only catalog and series. Never normalizes, fills gaps or substitutes a legacy shared fly series. */
public final class SpotAcquisitionCharts {
    public static final class Measure {
        public final SpotMeasurementMethod method;
        public final String quantity, label;
        public final boolean flyOnly;
        private Measure(SpotMeasurementMethod method, String quantity, String label, boolean flyOnly) {
            this.method = method; this.quantity = quantity; this.label = label; this.flyOnly = flyOnly;
        }
        @Override public String toString() { return label; }
    }
    private SpotAcquisitionCharts() { }
    public static List<Measure> measures(Family family) {
        List<Measure> measures = new ArrayList<>();
        if (family == Family.FILTER) {
            add(measures, SpotMeasurementMethod.FILTER, "thresholded_signal", "Filtered signal");
            add(measures, SpotMeasurementMethod.FILTER, "fly_pixel_excluded_signal", "Signal on visible pixels");
            fly(measures, SpotMeasurementMethod.FILTER, "Fly presence");
        } else if (family == Family.COLORS) {
            add(measures, SpotMeasurementMethod.COLOR, "grey_signal", "Color-selected grey signal");
            add(measures, SpotMeasurementMethod.COLOR, "selected_area", "Color-selected area");
            fly(measures, SpotMeasurementMethod.COLOR, "Fly presence");
        } else {
            add(measures, SpotMeasurementMethod.VERTICAL_LINE, "green_height", "Vertical line: measured height");
            add(measures, SpotMeasurementMethod.VERTICAL_LINE, "color_integral", "Vertical line: measured color integral");
            add(measures, SpotMeasurementMethod.VERTICAL_LINE, "background", "Vertical line: measured background");
            add(measures, SpotMeasurementMethod.VERTICAL_LINE, "row_fraction", "Vertical line: measured row coverage");
            fly(measures, SpotMeasurementMethod.VERTICAL_LINE, "Vertical line: fly presence");
            add(measures, SpotMeasurementMethod.X_LINES, "signal_fraction", "X lines: measured coverage");
            add(measures, SpotMeasurementMethod.X_LINES, "visible_signal_fraction", "X lines: coverage on visible samples");
            fly(measures, SpotMeasurementMethod.X_LINES, "X lines: fly presence");
            add(measures, SpotMeasurementMethod.RIM_LINES, "signal_fraction", "Rim lines: measured coverage");
            add(measures, SpotMeasurementMethod.RIM_LINES, "visible_signal_fraction", "Rim lines: coverage on visible samples");
            fly(measures, SpotMeasurementMethod.RIM_LINES, "Rim lines: fly presence");
        }
        return Collections.unmodifiableList(measures);
    }
    private static void add(List<Measure> list, SpotMeasurementMethod method, String quantity, String label) {
        list.add(new Measure(method, quantity, label, false));
    }
    private static void fly(List<Measure> list, SpotMeasurementMethod method, String label) {
        list.add(new Measure(method, "", label, true));
    }
    public static XYSeriesCollection data(List<Spot> spots, Measure measure, boolean fly) {
        fly |= measure.flyOnly;
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (Spot spot : spots) {
            SpotMethodResult result = spot.getMethodMeasurements().get(measure.method);
            if (result == null || (!fly && !result.getQuantities().containsKey(measure.quantity))) continue;
            double[] values = fly ? result.getFlyEvidence() : result.getQuantity(measure.quantity).getRawValues();
            int[] flags = fly ? null : result.getQuantity(measure.quantity).getEffectiveFlags();
            double[] times = result.getTimesSeconds();
            XYSeries series = new XYSeries(spot.getName(), false);
            for (int i = 0; i < times.length; i++) series.add(times[i] / 60,
                    Double.isFinite(values[i]) && (flags == null || flags[i] == 0) ? Double.valueOf(values[i]) : null);
            dataset.addSeries(series);
        }
        return dataset;
    }
    public static String unit(List<Spot> spots, Measure measure, boolean fly) {
        fly |= measure.flyOnly;
        for (Spot spot : spots) {
            SpotMethodResult result = spot.getMethodMeasurements().get(measure.method);
            if (result != null && (fly || result.getQuantities().containsKey(measure.quantity)))
                return (fly ? result.getFlyEvidenceUnit() : result.getQuantity(measure.quantity).getUnit()).replace('_', ' ');
        }
        return "";
    }
}
