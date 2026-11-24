package plugins.fmp.multitools.tools.chart.builders;

import java.awt.BasicStroke;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.chart.ChartCageBuild;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/** Builds each measure independently, retaining its native timestamps and null observations. */
public final class CageSummaryOverlay implements CageSeriesBuilder {
    private static final String TAG = ":summaryMeasure:";
    private final CageSeriesBuilder delegate;

    public CageSummaryOverlay(CageSeriesBuilder delegate) { this.delegate = delegate; }

    public static List<EnumResults> measures(ResultsOptions options) {
        if (options.cageSummaryMeasures == null || options.cageSummaryMeasures.isEmpty())
            return Collections.singletonList(options.resultType);
        return new ArrayList<>(new LinkedHashSet<>(options.cageSummaryMeasures));
    }

    @Override public XYSeriesCollection build(Experiment exp, Cage cage, ResultsOptions options) {
        List<EnumResults> measures = measures(options);
        if (measures.size() <= 1) return delegate.build(exp, cage, options);
        XYSeriesCollection combined = new XYSeriesCollection();
        try {
            for (EnumResults measure : measures) {
                if (measure == null || !measure.isCageSpotSummary())
                    throw new IllegalArgumentException("Only cage summaries can be overlaid");
                ResultsOptions single = new ResultsOptions();
                single.copy(options);
                single.resultType = measure;
                single.cageSummaryMeasures.clear();
                // Aggregate caches are measure-specific; never reuse the preceding measure's cache.
                exp.getCages().prepareComputations(exp, single);
                XYSeriesCollection source = delegate.build(exp, cage, single);
                for (int i = 0; i < source.getSeriesCount(); i++) {
                    XYSeries original = source.getSeries(i);
                    EnumResults actual = SpotChartSeriesKeys.isMedianRefSeriesKey(original.getKey().toString())
                            ? EnumResults.CAGE_GREY_MEDIAN : measure;
                    String key = original.getKey() + "::measure=" + actual.name();
                    if (combined.getSeriesIndex(key) >= 0) continue;
                    XYSeries copy = new XYSeries(key, false, original.getAllowDuplicateXValues());
                    copy.setDescription((original.getDescription() == null ? "" : original.getDescription()) + TAG + actual.name());
                    for (int j = 0; j < original.getItemCount(); j++) copy.add(original.getX(j), original.getY(j));
                    combined.addSeries(copy);
                }
            }
        } finally {
            exp.getCages().prepareComputations(exp, options);
        }
        ChartCageBuild.updateGlobalExtremaFromDataset(combined);
        return combined;
    }

    public static EnumResults measure(XYSeries series) {
        String text = series.getDescription();
        int start = text != null ? text.lastIndexOf(TAG) : -1;
        if (start < 0) return null;
        return EnumResults.valueOf(text.substring(start + TAG.length()));
    }

    public static String unit(EnumResults measure) {
        switch (measure) {
        case AGG_KYMO_HEIGHT_LOSS:
        case AGG_X_LINE_CONSO:
        case AGG_RIM_LINE_CONSO:
        case AGG_FILTER_DEPLETION:
        case AGG_COLOR_DEPLETION: return "Consumed spot equivalents";
        default: return measure.toUnit();
        }
    }

    public static String label(XYSeries series) {
        String key = series.getKey().toString();
        String category = key.substring(0, key.lastIndexOf("::measure="));
        if (SpotChartSeriesKeys.isAggregateSeriesKey(category)) category = category.substring(4, category.indexOf(')'));
        else if (category.contains("::")) category = category.substring(0, category.indexOf("::"));
        return measure(series).name() + " — " + category;
    }

    public static Stroke stroke(EnumResults measure) {
        List<EnumResults> catalog = new ArrayList<>();
        for (SpotChartMeasures.Family family : SpotChartMeasures.Family.values())
            Collections.addAll(catalog, family.measures(true));
        int index = catalog.indexOf(measure);
        if (index <= 0 || measure == EnumResults.AGG_KYMO_HEIGHT_LOSS) return new BasicStroke(1.3f);
        float a = 2 + index;
        return new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1,
                new float[] { a, 3, 2, 3 }, 0);
    }
}
