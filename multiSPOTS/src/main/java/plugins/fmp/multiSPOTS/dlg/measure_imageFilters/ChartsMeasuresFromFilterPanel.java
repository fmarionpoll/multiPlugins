package plugins.fmp.multiSPOTS.dlg.measure_imageFilters;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JPanel;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure.MeasureChartsPanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

/** Image-filter entry point for the shared spot and cage-summary chart UI. */
public class ChartsMeasuresFromFilterPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private MeasureChartsPanel charts;

    void init(GridLayout layout, MultiSPOTS parent) {
        setLayout(new BorderLayout());
        charts = new MeasureChartsPanel(parent, Family.FILTER);
        add(charts, BorderLayout.CENTER);
    }

    public void displayChartPanels(Experiment exp) { charts.displayChartPanels(exp); }
    public void closeAllCharts() { if (charts != null) charts.closeAllCharts(); }
}
