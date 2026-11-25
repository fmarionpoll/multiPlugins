package plugins.fmp.multiSPOTS.dlg.measure;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import icy.gui.frame.IcyFrame;
import icy.roi.ROI2D;
import icy.sequence.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.AxisOptions;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts.Measure;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

/** Acquisition charts: valid primary observations and each method's independent fly evidence. */
public class MeasureChartsPanel extends JPanel implements SequenceListener {
    private static final long serialVersionUID = 1L;
    private final MultiSPOTS owner;
    private final Family family;
    final JComboBox<Measure> measure;
    final JComboBox<String> scope = new JComboBox<>(new String[] { "Experiment (all cages)", "Cage", "Selected spot(s)" });
    final JComboBox<Integer> cage = new JComboBox<>();
    final JCheckBox flies = new JCheckBox("Show fly presence", true);
    private final JLabel status = new JLabel("Occlusions remain gaps. Calculate spot fractions provides ratios and aggregates.");
    private final JPanel plots = new JPanel(new GridLayout(0, 2, 4, 4));
    private IcyFrame frame;
    private ChartPanel activeChart;
    private AxisOptions axes;
    private Experiment current;
    private Sequence listenedSequence;
    private boolean updating;

    public MeasureChartsPanel(MultiSPOTS owner, Family family) {
        super(new BorderLayout()); this.owner = owner; this.family = family;
        measure = new JComboBox<>(SpotAcquisitionCharts.measures(family).toArray(new Measure[0]));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        controls.add(new JLabel("Measure")); controls.add(measure); controls.add(flies);
        JPanel views = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        views.add(new JLabel("View")); views.add(scope); views.add(cage); cage.setVisible(false);
        JButton display = new JButton("Display chart"), refresh = new JButton("Refresh"), axis = new JButton("Axis options");
        views.add(display); views.add(refresh); views.add(axis);
        JPanel rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS)); rows.add(controls); rows.add(views); rows.add(status); add(rows);
        display.addActionListener(e -> displayChartPanels(selectedExperiment())); refresh.addActionListener(e -> displayChartPanels(selectedExperiment()));
        measure.addActionListener(e -> { flies.setEnabled(!((Measure) measure.getSelectedItem()).flyOnly); refreshIfDisplayed(selectedExperiment()); });
        flies.addActionListener(e -> refreshIfDisplayed(selectedExperiment()));
        scope.addActionListener(e -> { cage.setVisible(scope.getSelectedIndex() == 1); refreshIfDisplayed(selectedExperiment()); });
        cage.addActionListener(e -> { if (!updating) refreshIfDisplayed(selectedExperiment()); });
        axis.addActionListener(e -> {
            if (activeChart == null) { status.setText("Display a chart first."); return; }
            if (axes != null) axes.close(); axes = new AxisOptions(); axes.initialize(owner, () -> activeChart);
        });
    }
    private Experiment selectedExperiment() { return owner == null ? null : owner.expListComboLazy.getSelectedItem(); }
    public void displayChartPanels(Experiment exp) {
        if (exp == null) { status.setText("No experiment selected."); return; }
        if (current != exp) {
            closeAllCharts(); current = exp; updating = true; cage.removeAllItems();
            TreeSet<Integer> ids = new TreeSet<>(); for (Spot spot : exp.getSpots().getSpotList()) ids.add(spot.getProperties().getCageID());
            for (int id : ids) cage.addItem(id); updating = false;
        }
        Measure chosen = (Measure) measure.getSelectedItem();
        List<Spot> selected = scope.getSelectedIndex() == 2 ? initialSpots(exp) : new ArrayList<>(exp.getSpots().getSpotList());
        if (scope.getSelectedIndex() == 1) selected.removeIf(s -> !Objects.equals(cage.getSelectedItem(), s.getProperties().getCageID()));
        Map<Integer, List<Spot>> groups = new TreeMap<>();
        for (Spot spot : selected) if (spot.getMethodMeasurements().get(chosen.method) != null
                && (chosen.flyOnly || spot.getMethodMeasurements().get(chosen.method).getQuantities().containsKey(chosen.quantity)))
            groups.computeIfAbsent(spot.getProperties().getCageID(), key -> new ArrayList<>()).add(spot);
        plots.setLayout(new GridLayout(0, groups.size() > 1 ? 2 : 1, 4, 4));
        plots.removeAll(); activeChart = null;
        for (Map.Entry<Integer, List<Spot>> group : groups.entrySet()) {
            List<Spot> spots = group.getValue(); spots.sort(Comparator.comparing(Spot::getName));
            XYSeriesCollection data = SpotAcquisitionCharts.data(spots, chosen, chosen.flyOnly);
            if (data.getSeriesCount() == 0) continue;
            NumberAxis time = new NumberAxis("Elapsed time (minutes)"); time.setAutoRangeIncludesZero(false);
            Paint[] colors = ChartColor.createDefaultPaintArray();
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            renderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
            for (int i = 0; i < data.getSeriesCount(); i++) renderer.setSeriesPaint(i, colors[i % colors.length]);
            XYPlot plot = new XYPlot(data, time, new NumberAxis(chosen.label + " (" + SpotAcquisitionCharts.unit(spots, chosen, chosen.flyOnly) + ")"), renderer);
            if (!chosen.flyOnly && flies.isSelected()) {
                plot.setDataset(1, SpotAcquisitionCharts.data(spots, chosen, true));
                plot.setRangeAxis(1, new NumberAxis("Fly presence (" + SpotAcquisitionCharts.unit(spots, chosen, true) + ")"));
                XYLineAndShapeRenderer flyRenderer = new XYLineAndShapeRenderer(true, false);
                flyRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
                for (int i = 0; i < spots.size(); i++) {
                    flyRenderer.setSeriesPaint(i, colors[i % colors.length]);
                    flyRenderer.setSeriesStroke(i, new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {3, 3}, 0));
                    flyRenderer.setSeriesVisibleInLegend(i, false);
                }
                plot.setRenderer(1, flyRenderer); plot.mapDatasetToRangeAxis(1, 1);
            }
            ChartPanel panel = new ChartPanel(new JFreeChart("Cage " + group.getKey() + " — " + chosen.label,
                    JFreeChart.DEFAULT_TITLE_FONT, plot, true)); panel.setPreferredSize(new Dimension(500, 320));
            panel.addChartMouseListener(new ChartMouseListener() {
                @Override public void chartMouseMoved(ChartMouseEvent event) { }
                @Override public void chartMouseClicked(ChartMouseEvent event) {
                    activeChart = panel;
                    if (owner != null && event.getEntity() instanceof XYItemEntity) {
                        XYItemEntity item = (XYItemEntity) event.getEntity(); String name = item.getDataset().getSeriesKey(item.getSeriesIndex()).toString();
                        for (Spot spot : spots) if (spot.getName().equals(name)) owner.dlgSpots.onMeasureChartSpotClicked(spot);
                    }
                }
            });
            plots.add(panel); if (activeChart == null) activeChart = panel;
        }
        if (activeChart == null) {
            if (frame != null) frame.setVisible(false);
            status.setText("No primary measurements for this view. Collect or load the method's primary measurements."); return;
        }
        if (frame == null || !frame.isVisible()) {
            frame = new IcyFrame(family.label + " — Measurements with gaps", true, true, true, true);
            frame.setLayout(new BorderLayout()); frame.add(new JScrollPane(plots)); frame.setSize(1050, 650);
            frame.addToDesktopPane(); frame.center();
        }
        frame.setVisible(true); plots.revalidate(); plots.repaint();
        status.setText(chosen.flyOnly ? "Fly evidence for this method; unknown values remain missing."
                : flies.isSelected() ? "Valid observations only. Matching dashed traces show this method's fly evidence."
                : "Valid observations only; no normalization, smoothing or gap filling.");
        if (family != Family.LINES) listen(exp.getSeqCamData() == null ? null : exp.getSeqCamData().getSequence());
    }
    public void refreshIfDisplayed(Experiment exp) { if (frame != null && frame.isVisible()) displayChartPanels(exp); }
    public void refreshVisibleCharts() { refreshIfDisplayed(selectedExperiment()); }
    public void closeAllCharts() {
        if (axes != null) axes.close(); axes = null;
        if (frame != null) frame.dispose(); frame = null; activeChart = null; listen(null);
    }
    private void listen(Sequence sequence) {
        if (sequence == listenedSequence) return;
        if (listenedSequence != null) listenedSequence.removeListener(this); listenedSequence = sequence;
        if (sequence != null) sequence.addListener(this);
    }
    @Override public void sequenceChanged(SequenceEvent event) { }
    @Override public void sequenceClosed(Sequence sequence) { if (sequence == listenedSequence) closeAllCharts(); }
    static List<Spot> initialSpots(Experiment exp) {
        List<Spot> selected = new ArrayList<>();
        if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
            for (ROI2D roi : exp.getSeqCamData().getSequence().getROI2Ds()) if (roi.isSelected())
                for (Spot spot : exp.getSpots().getSpotList()) if (spot.getName().equals(roi.getName()) && !selected.contains(spot)) selected.add(spot);
        }
        selected.sort(Comparator.comparing(Spot::getName));
        if (selected.isEmpty() && !exp.getSpots().getSpotList().isEmpty()) {
            List<Spot> all = new ArrayList<>(exp.getSpots().getSpotList()); all.sort(Comparator.comparing(Spot::getName)); selected.add(all.get(0));
        }
        return selected;
    }
}
