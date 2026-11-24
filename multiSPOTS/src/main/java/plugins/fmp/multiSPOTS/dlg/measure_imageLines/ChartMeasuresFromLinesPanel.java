package plugins.fmp.multiSPOTS.dlg.measure_imageLines;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure.MeasureChartsPanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

/** Line-analysis entry point; chart state and navigation are shared with image measures. */
public class ChartMeasuresFromLinesPanel extends MeasureChartsPanel {
    private static final long serialVersionUID = 1L;
    private final MultiSPOTS owner;

    public ChartMeasuresFromLinesPanel(MultiSPOTS parent, KymoAnalysisPanel height,
            CrossAnalysisPanel cross, RimAnalysisPanel rim) {
        super(parent, Family.LINES);
        owner = parent;
        height.addKymoResultListener(e -> maybeRefreshVisibleCharts());
        if (cross != null) cross.addKymoResultListener(e -> maybeRefreshVisibleCharts());
        if (rim != null) rim.addKymoResultListener(e -> maybeRefreshVisibleCharts());
    }

    void maybeRefreshVisibleCharts() { refreshVisibleCharts(); }
    public void displayChartsOnExperimentOpen() {
        displayChartPanels((Experiment) owner.expListComboLazy.getSelectedItem());
    }
}
