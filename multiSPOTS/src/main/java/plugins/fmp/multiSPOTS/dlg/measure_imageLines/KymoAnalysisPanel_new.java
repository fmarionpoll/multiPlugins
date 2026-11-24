package plugins.fmp.multiSPOTS.dlg.measure_imageLines;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Group;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Result;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Row;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Settings;
import plugins.fmp.multitools.service.KymoAnalysisCsvExport_new;
import plugins.fmp.multitools.tools.Logger;

/** Trial controls plus the complete old panel for direct comparison. */
public class KymoAnalysisPanel_new extends KymoAnalysisPanel_old {
    private static final long serialVersionUID = 1L;
    private final MultiSPOTS owner;
    private final JButton analyzeNew = new JButton("Analyze new (current)");
    private final JButton showComparison = new JButton("Old/new comparison");
    private final JSpinner baselineStart = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100000.0, 1.0));
    private final JSpinner baselineEnd = new JSpinner(new SpinnerNumberModel(20.0, 0.0, 100000.0, 1.0));
    private final JLabel trialStatus = new JLabel("Choose a baseline period with no feeding.");
    private Result trial;
    private String trialExperiment;
    private boolean trialRunning;
    private IcyFrame comparison;

    public KymoAnalysisPanel_new(MultiSPOTS parent) {
        super(parent);
        owner = parent;
        setLayout(new GridLayout(0, 1));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        controls.add(analyzeNew);
        controls.add(new JLabel("Baseline (min)"));
        controls.add(baselineStart);
        controls.add(new JLabel("to"));
        controls.add(baselineEnd);
        controls.add(showComparison);
        add(controls, 0);
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        status.add(trialStatus);
        add(status, 1);
        analyzeNew.setToolTipText("Current experiment only. Integrate color above the strip-edge background; normalize to the chosen median baseline. The existing Analyze button runs the old method.");
        baselineStart.setToolTipText("Start of a verified, unobscured baseline with no feeding. At least three observed columns per spot are required.");
        baselineEnd.setToolTipText(baselineStart.getToolTipText());
        showComparison.setEnabled(false);
        analyzeNew.addActionListener(e -> startTrial());
        showComparison.addActionListener(e -> showComparison());
    }

    private void startTrial() {
        if (trialRunning || isLegacyAnalysisRunning()) {
            trialStatus.setText("Wait for the current analysis to finish.");
            return;
        }
        Experiment exp = (Experiment) owner.expListComboLazy.getSelectedItem();
        if (exp == null) { trialStatus.setText("Select an experiment first."); return; }
        final Settings settings;
        try {
            baselineStart.commitEdit();
            baselineEnd.commitEdit();
            settings = new Settings(((Number) baselineStart.getValue()).doubleValue(), ((Number) baselineEnd.getValue()).doubleValue());
        } catch (Exception ex) { trialStatus.setText("Baseline end must be later than start."); return; }
        final Params params = readParams();
        final String experimentLabel = exp.getResultsDirectory();
        String bin = exp.getKymosBinFullDirectory();
        if (bin == null) { trialStatus.setText("Build/load cage kymographs first."); return; }
        final Path output = Paths.get(bin);
        final boolean experimentSelectorEnabled = owner.expListComboLazy.isEnabled();
        trialRunning = true;
        owner.expListComboLazy.setEnabled(false);
        analyzeNew.setEnabled(false);
        analyzeButton.setEnabled(false);
        showComparison.setEnabled(false);
        baselineStart.setEnabled(false);
        baselineEnd.setEnabled(false);
        trialStatus.setText("Analyzing old and new methods…");
        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() throws Exception {
                if (exp.getSeqKymos() == null || exp.getSeqKymos().getSequence() == null) {
                    if (!exp.loadCageSpotKymographs()) throw new IllegalStateException("Build/load cage kymographs first.");
                }
                Result result = CageKymoAnalyzer_new.analyze(exp, params, settings);
                if (result.rows.isEmpty()) throw new IllegalStateException("No spot strips were found.");
                KymoAnalysisCsvExport_new.write(output, result, params);
                return result;
            }

            @Override protected void done() {
                trialRunning = false;
                owner.expListComboLazy.setEnabled(experimentSelectorEnabled);
                analyzeNew.setEnabled(true);
                analyzeButton.setEnabled(true);
                baselineStart.setEnabled(true);
                baselineEnd.setEnabled(true);
                try {
                    trial = get();
                    trialExperiment = experimentLabel;
                    long valid = trial.rows.stream().filter(row -> Double.isFinite(row.signal.baseline)).count();
                    trialStatus.setText("Saved new CSVs: " + valid + "/" + trial.rows.size() + " spots with valid baseline.");
                    showComparison.setEnabled(true);
                    showComparison();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    trialStatus.setText("Trial interrupted.");
                } catch (ExecutionException ex) {
                    trialStatus.setText("Trial failed: " + ex.getCause().getMessage());
                    Logger.error("Kymograph trial failed", ex.getCause());
                    showComparison.setEnabled(trial != null);
                }
            }
        }.execute();
    }

    private void showComparison() {
        if (trial == null) return;
        if (comparison != null) comparison.close();
        comparison = new IcyFrame("Kymograph old/new — " + trialExperiment, true, true, true, true);
        JPanel content = new JPanel(new BorderLayout());
        JComboBox<String> selection = new JComboBox<>();
        for (Group group : trial.groups) selection.addItem(group.toString());
        for (Row row : trial.rows) selection.addItem(row.spot.getName());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Trace"));
        top.add(selection);
        JCheckBox completeTotalsOnly = new JCheckBox("Require all baseline spots", false);
        top.add(completeTotalsOnly);
        content.add(top, BorderLayout.NORTH);
        XYLineAndShapeRenderer lossRenderer = new XYLineAndShapeRenderer(true, false);
        XYPlot lossPlot = new XYPlot(new XYSeriesCollection(), null,
                new NumberAxis("Relative loss (spot equivalents)"), lossRenderer);
        NumberAxis missingAxis = new NumberAxis("Missing spots");
        missingAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        XYLineAndShapeRenderer missingRenderer = new XYLineAndShapeRenderer(true, false);
        missingRenderer.setSeriesPaint(0, Color.DARK_GRAY);
        XYPlot missingPlot = new XYPlot(new XYSeriesCollection(), null, missingAxis, missingRenderer);
        CombinedDomainXYPlot combined = new CombinedDomainXYPlot(new NumberAxis("Time (min)"));
        combined.add(lossPlot, 4);
        combined.add(missingPlot, 1);
        JFreeChart chart = new JFreeChart("", JFreeChart.DEFAULT_TITLE_FONT, combined, true);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(1000, 560));
        content.add(chartPanel, BorderLayout.CENTER);
        content.add(new JLabel("New: integral / median baseline " + trial.settings.baselineStartMinutes + "–"
                + trial.settings.baselineEndMinutes + " min. Green subtotal excludes missing spots; line breaks mark changes in contributing spots."), BorderLayout.SOUTH);
        Runnable update = () -> {
            int index = selection.getSelectedIndex();
            if (index < 0) return;
            XYSeriesCollection data = new XYSeriesCollection();
            XYSeriesCollection coverage = new XYSeriesCollection();
            if (index < trial.groups.size()) {
                completeTotalsOnly.setEnabled(true);
                Group group = trial.groups.get(index);
                chart.setTitle(group + " (" + group.baselineSpots + "/" + group.expectedSpots + " spots in baseline cohort)");
                addTrace(data, "Old: Σ(1 − h/h_max), all spots", trial.minutes, group.oldLoss, false);
                if (group.baselineSpots != group.expectedSpots)
                    addTrace(data, "Old: same baseline cohort", trial.minutes, group.oldMatchedLoss, false);
                if (completeTotalsOnly.isSelected())
                    addTrace(data, "New: complete baseline-cohort total", trial.minutes, group.newLoss, false);
                else
                    addObservedTrace(data, trial.minutes, group.newObservedLoss, group.newMembershipChanged);
                double[] counts = new double[trial.minutes.length];
                for (int x = 0; x < counts.length; x++) counts[x] = group.newMissing[x];
                addTrace(coverage, "Missing within baseline cohort", trial.minutes, counts, false);
            } else {
                completeTotalsOnly.setEnabled(false);
                Row row = trial.rows.get(index - trial.groups.size());
                chart.setTitle(row.spot.getName() + " — baseline valid columns: " + row.signal.baselineSamples);
                addTrace(data, "Old: 1 − h/h_max", trial.minutes, row.oldHeightRatio, true);
                addTrace(data, "New: 1 − integral/baseline", trial.minutes, row.signal.ratio, true);
                double[] counts = new double[trial.minutes.length];
                for (int x = 0; x < counts.length; x++) counts[x] = Double.isFinite(row.signal.ratio[x]) ? 0 : 1;
                addTrace(coverage, "Spot unavailable", trial.minutes, counts, false);
            }
            lossPlot.setDataset(data);
            lossRenderer.setSeriesPaint(0, Color.RED);
            if (data.getSeriesCount() == 3) lossRenderer.setSeriesPaint(1, Color.BLUE);
            lossRenderer.setSeriesPaint(data.getSeriesCount() - 1, new Color(0, 128, 0));
            missingPlot.setDataset(coverage);
        };
        selection.addActionListener(e -> update.run());
        completeTotalsOnly.addActionListener(e -> update.run());
        update.run();
        comparison.getContentPane().add(content);
        comparison.pack();
        comparison.addToDesktopPane();
        comparison.setVisible(true);
    }

    static void addObservedTrace(XYSeriesCollection data, double[] minutes, double[] values, boolean[] changed) {
        XYSeries series = new XYSeries("New: observed subtotal (excludes missing spots)", false);
        for (int x = 0; x < minutes.length; x++) {
            // Equal counts can conceal a change in which spots are visible. Break those joins too.
            if (x > 0 && changed[x]) series.add((minutes[x - 1] + minutes[x]) / 2, (Number) null);
            series.add(minutes[x], Double.isFinite(values[x]) ? Double.valueOf(values[x]) : null);
        }
        data.addSeries(series);
    }

    static void addTrace(XYSeriesCollection data, String name, double[] minutes, double[] values, boolean lossOfRatio) {
        XYSeries series = new XYSeries(name, false);
        for (int x = 0; x < minutes.length; x++) {
            double value = lossOfRatio ? 1 - values[x] : values[x];
            // Keep null samples so charts do not join across occlusions or invalid baselines.
            series.add(minutes[x], Double.isFinite(value) ? Double.valueOf(value) : null);
        }
        data.addSeries(series);
    }
}
