package plugins.fmp.multiSPOTS.dlg.fractions;

import java.awt.BasicStroke;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.*;

/** Only complete-cohort calculated totals and means; the supporting count remains visible. */
public final class SpotFractionAggregateChart {
    private SpotFractionAggregateChart() { }
    public static XYSeriesCollection data(Group group, Measure measure) {
        double[] values = group.values(measure), times = group.timesSeconds(); boolean[] estimated = group.estimatedSamples();
        XYSeries observed = new XYSeries("Complete group", false), estimates = new XYSeries("Includes estimated values/reference", false);
        for (int i = 0; i < times.length; i++) {
            observed.add(times[i] / 60, !estimated[i] && Double.isFinite(values[i]) ? Double.valueOf(values[i]) : null);
            boolean anchor = estimated[i] || (i > 0 && estimated[i - 1]) || (i + 1 < times.length && estimated[i + 1]);
            estimates.add(times[i] / 60, anchor && Double.isFinite(values[i]) ? Double.valueOf(values[i]) : null);
        }
        XYSeriesCollection data = new XYSeriesCollection(); data.addSeries(observed); data.addSeries(estimates); return data;
    }
    public static JFreeChart create(Group group, Measure measure) {
        boolean sum = measure == Measure.SUM_CONSUMED || measure == Measure.SUM_REMAINING;
        String label = (measure == Measure.SUM_CONSUMED || measure == Measure.MEAN_CONSUMED ? "Consumed" : "Remaining")
                + (sum ? " (spot equivalents)" : " (mean fraction)");
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setSeriesStroke(1, new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {5, 4}, 0));
        XYPlot totals = new XYPlot(data(group, measure), null, new NumberAxis(label), renderer);
        XYSeries available = new XYSeries("Available spots", false), expected = new XYSeries("Expected spots", false);
        double[] times = group.timesSeconds(); int[] counts = group.availableSpots();
        for (int i = 0; i < times.length; i++) { available.add(times[i] / 60, counts[i]); expected.add(times[i] / 60, group.expectedSpots); }
        XYSeriesCollection coverage = new XYSeriesCollection(); coverage.addSeries(available); coverage.addSeries(expected);
        XYPlot support = new XYPlot(coverage, null, new NumberAxis("Spots (count)"), new XYLineAndShapeRenderer(true, false));
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(new NumberAxis("Elapsed time (minutes)")); plot.add(totals, 3); plot.add(support, 1);
        return new JFreeChart(group.toString(), JFreeChart.DEFAULT_TITLE_FONT, plot, true);
    }
}
