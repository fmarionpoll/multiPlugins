package plugins.fmp.multiSPOTS.dlg.measure;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.*;
import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerEvent.ViewerEventType;
import icy.gui.viewer.ViewerListener;
import icy.roi.ROI2D;
import icy.sequence.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.labels.XYSeriesLabelGenerator;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.measure_imageFilters.AxisOptions;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.tools.ColorUtils;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts;
import plugins.fmp.multitools.tools.chart.interaction.ChartCamFrameNavigation;
import plugins.fmp.multitools.tools.chart.interaction.SpotChartRoiFocus;
import plugins.fmp.multitools.tools.chart.SpotAcquisitionCharts.Measure;
import plugins.fmp.multitools.tools.chart.SpotChartMeasures.Family;

/** Acquisition charts: valid primary observations and each method's independent fly evidence. */
public class MeasureChartsPanel extends JPanel implements SequenceListener, ViewerListener {
    private static final long serialVersionUID = 1L;
    private final MultiSPOTS owner;
    private final Family family;
    final JComboBox<Measure> measure;
    final JComboBox<String> scope = new JComboBox<>(new String[] { "Experiment (all cages)", "Cage", "Selected spot(s)" });
    final JComboBox<Integer> cage = new JComboBox<>();
    final JComboBox<Spot> spotChoice = new JComboBox<>();
    private final JLabel cageCaption = new JLabel("Cage");
    private final JLabel spotCaption = new JLabel("Spot");
    final JCheckBox flies = new JCheckBox("Show fly presence", true);
    private final JLabel status = new JLabel("Occlusions remain gaps. Calculate spot fractions provides ratios and aggregates.");
    private static final int CHART_BASE_WIDTH = 500;
    private static final int CHART_BASE_HEIGHT = 320;
    private static final int CHART_GAP = 4;
    static final int CHART_SCALE_MIN = 30;
    static final int CHART_SCALE_MAX = 100;
    private final JPanel plots = new JPanel(new GridLayout(0, 2, CHART_GAP, CHART_GAP));
    private int chartScalePercent = 40;
    private JSlider chartScale;
    private boolean honorChartScale;
    private JLabel measureBand;
    JPanel navRow;
    JComboBox<Measure> chartMeasure;
    JLabel chartCageCaption;
    JComboBox<Integer> chartCage;
    JComboBox<Spot> chartSpot;
    JLabel chartSpotCaption;
    private IcyFrame frame;
    private ChartPanel activeChart;
    private AxisOptions axes;
    private static final Stroke FRAME_CURSOR = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            1f, new float[] { 1f, 3f }, 0f);
    static final int CURSOR_GRAB_PX = 8;
    private final List<ValueMarker> frameCursors = new ArrayList<>();
    private boolean cursorGesture;
    private boolean draggingCursor;
    private boolean savedDomainZoom;
    private boolean savedRangeZoom;
    private Experiment current;
    private Sequence listenedSequence;
    private Viewer listenedViewer;
    private boolean updating;

    public MeasureChartsPanel(MultiSPOTS owner, Family family) {
        super(new BorderLayout()); this.owner = owner; this.family = family;
        measure = new JComboBox<>(SpotAcquisitionCharts.measures(family).toArray(new Measure[0]));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        controls.add(new JLabel("Measure")); controls.add(measure); controls.add(flies);
        JPanel views = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cage.setRenderer(cageRenderer());
        spotChoice.setRenderer(spotRenderer());
        spotChoice.setPreferredSize(new Dimension(240, 24));
        views.add(new JLabel("View")); views.add(scope);
        views.add(cageCaption); views.add(cage); views.add(spotCaption); views.add(spotChoice);
        cageCaption.setVisible(false); cage.setVisible(false); spotCaption.setVisible(false); spotChoice.setVisible(false);
        JButton display = new JButton("Display chart"), refresh = new JButton("Refresh"), axis = new JButton("Axis options");
        views.add(display); views.add(refresh); views.add(axis);
        JPanel rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS)); rows.add(controls); rows.add(views); rows.add(status); add(rows);
        display.addActionListener(e -> displayChartPanels(selectedExperiment())); refresh.addActionListener(e -> displayChartPanels(selectedExperiment()));
        measure.addActionListener(e -> { flies.setEnabled(!((Measure) measure.getSelectedItem()).flyOnly); refreshIfDisplayed(chartExperiment()); });
        flies.addActionListener(e -> refreshIfDisplayed(chartExperiment()));
        scope.addActionListener(e -> {
            boolean oneCage = scope.getSelectedIndex() == 1, oneSpot = scope.getSelectedIndex() == 2;
            cageCaption.setVisible(oneCage || oneSpot); cage.setVisible(oneCage || oneSpot);
            spotCaption.setVisible(oneSpot); spotChoice.setVisible(oneSpot);
            if (oneSpot) selectSpotForCurrentView(chartExperiment());
            refreshIfDisplayed(chartExperiment());
        });
        cage.addActionListener(e -> {
            if (updating) return;
            Spot chosen = null;
            if (scope.getSelectedIndex() == 2) {
                fillSpotsOfSelectedCage(chartExperiment());
                if (spotChoice.getSelectedItem() instanceof Spot) chosen = (Spot) spotChoice.getSelectedItem();
            }
            refreshIfDisplayed(chartExperiment());
            if (chosen != null) showChosenSpot(chosen);
        });
        spotChoice.addActionListener(e -> {
            if (updating || !(spotChoice.getSelectedItem() instanceof Spot)) return;
            refreshIfDisplayed(chartExperiment());
            showChosenSpot((Spot) spotChoice.getSelectedItem());
        });
        axis.addActionListener(e -> {
            if (activeChart == null) { status.setText("Display a chart first."); return; }
            if (axes != null) axes.close(); axes = new AxisOptions(); axes.initialize(owner, () -> activeChart);
        });
    }
    private Experiment selectedExperiment() { return owner == null ? null : owner.expListComboLazy.getSelectedItem(); }
    public void displayChartPanels(Experiment exp) {
        if (exp == null) { status.setText("No experiment selected."); return; }
        bindExperiment(exp);
        boolean single = scope.getSelectedIndex() == 2;
        boolean experimentView = scope.getSelectedIndex() == 0;
        if (single) selectSpotForCurrentView(exp);
        Measure chosen = (Measure) measure.getSelectedItem();
        List<Spot> selected = new ArrayList<>();
        if (single) {
            if (spotChoice.getSelectedItem() instanceof Spot) selected.add((Spot) spotChoice.getSelectedItem());
        } else {
            selected.addAll(exp.getSpots().getSpotList());
            if (scope.getSelectedIndex() == 1) selected.removeIf(s -> !Objects.equals(cage.getSelectedItem(), s.getProperties().getCageID()));
        }
        Map<Integer, List<Spot>> groups = new TreeMap<>();
        for (Spot spot : selected) if (spot.getMethodMeasurements().get(chosen.method) != null
                && (chosen.flyOnly || spot.getMethodMeasurements().get(chosen.method).getQuantities().containsKey(chosen.quantity)))
            groups.computeIfAbsent(spot.getProperties().getCageID(), key -> new ArrayList<>()).add(spot);
        Dimension screen = screenSize();
        CageChartGrid grid = experimentView ? experimentGrid(exp, groups.keySet()) : null;
        if (experimentView && grid.columns > 0 && !honorChartScale) {
            int fit = percentThatFits(grid.columns, grid.rows, chartScalePercent, screen);
            if (fit != chartScalePercent) {
                updating = true;
                chartScalePercent = fit;
                if (chartScale != null) chartScale.setValue(fit);
                updating = false;
            }
        }
        Dimension chartPx = experimentView ? experimentChartSize(chartScalePercent, screen) : chartSize(chartScalePercent);
        float titleSize = experimentView ? Math.max(8f, 16f * chartPx.width / (float) CHART_BASE_WIDTH)
                : Math.max(11f, 16f * chartScalePercent / 100f);
        plots.removeAll(); frameCursors.clear(); activeChart = null;
        Map<Integer, ChartPanel> charts = new TreeMap<>();
        for (Map.Entry<Integer, List<Spot>> group : groups.entrySet()) {
            List<Spot> spots = group.getValue(); spots.sort(Comparator.comparing(Spot::getName));
            XYSeriesCollection data = SpotAcquisitionCharts.data(spots, chosen, chosen.flyOnly);
            if (data.getSeriesCount() == 0) continue;
            NumberAxis time = new NumberAxis("Elapsed time (minutes)"); time.setAutoRangeIncludesZero(false);
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
            renderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
            paintByStimulus(renderer, spots, data.getSeriesCount(), true);
            XYPlot plot = new XYPlot(data, time, new NumberAxis(chosen.label + " (" + SpotAcquisitionCharts.unit(spots, chosen, chosen.flyOnly) + ")"), renderer);
            ValueMarker cursor = new ValueMarker(Double.NaN, Color.BLACK, FRAME_CURSOR);
            cursor.setAlpha(0f);
            plot.addDomainMarker(cursor);
            frameCursors.add(cursor);
            if (!chosen.flyOnly && flies.isSelected()) {
                XYSeriesCollection flyData = SpotAcquisitionCharts.data(spots, chosen, true);
                plot.setDataset(1, flyData);
                plot.setRangeAxis(1, new NumberAxis("Fly presence (" + SpotAcquisitionCharts.unit(spots, chosen, true) + ")"));
                XYLineAndShapeRenderer flyRenderer = new XYLineAndShapeRenderer(true, false);
                flyRenderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator());
                paintFlyPresence(flyRenderer, flyData.getSeriesCount());
                plot.setRenderer(1, flyRenderer); plot.mapDatasetToRangeAxis(1, 1);
            }
            ChartPanel panel = new ChartPanel(new JFreeChart(cageTitle(group.getKey()),
                    JFreeChart.DEFAULT_TITLE_FONT.deriveFont(Font.BOLD, titleSize), plot, true));
            if (experimentView) fitChartText(panel.getChart(), titleSize);
            Dimension size = chartPx;
            panel.setPreferredSize(size);
            panel.setMinimumDrawWidth(size.width); panel.setMinimumDrawHeight(size.height);
            panel.setMaximumDrawWidth(Math.max(size.width, 1024)); panel.setMaximumDrawHeight(Math.max(size.height, 768));
            installCursorDrag(panel, cursor);
            panel.addChartMouseListener(new ChartMouseListener() {
                @Override public void chartMouseMoved(ChartMouseEvent event) {
                    if (draggingCursor || event.getTrigger() == null) return;
                    boolean near = nearVerticalCursor(event.getTrigger().getX(), markerScreenX(panel, cursor));
                    panel.setCursor(Cursor.getPredefinedCursor(near ? Cursor.E_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
                }
                @Override public void chartMouseClicked(ChartMouseEvent event) {
                    activeChart = panel;
                    if (cursorGesture) return;
                    if (owner == null || event.getTrigger() == null || event.getTrigger().getButton() != MouseEvent.BUTTON1
                            || !(event.getEntity() instanceof XYItemEntity)) return;
                    XYItemEntity item = (XYItemEntity) event.getEntity();
                    String name = item.getDataset().getSeriesKey(item.getSeriesIndex()).toString();
                    for (Spot spot : spots) if (spot.getName().equals(name)) {
                        focusClickedSample(current != null ? current : selectedExperiment(), spot, chosen.method,
                                ChartCamFrameNavigation.getTimeMinutesFromXYItem(item));
                        owner.dlgSpots.onMeasureChartSpotClicked(spot);
                        break;
                    }
                }
            });
            charts.put(group.getKey(), panel); if (activeChart == null) activeChart = panel;
        }
        placeCharts(experimentView ? grid : null, charts, chartPx);
        if (activeChart == null && !single && (frame == null || !frame.isVisible())) {
            if (frame != null) frame.setVisible(false);
            status.setText("No primary measurements for this view. Collect or load the method's primary measurements."); return;
        }
        String empty = single ? "No primary measurements for this spot."
                : experimentView ? "No primary measurements for this experiment." : "No primary measurements for this cage.";
        if (activeChart == null) plots.add(new JLabel(empty));
        String band = parameterBand(chosen.label, SpotAcquisitionCharts.unit(selected, chosen, chosen.flyOnly));
        if (frame == null || !frame.isVisible()) {
            if (frame != null) frame.dispose();
            frame = new IcyFrame(family.label + " — Measurements with gaps", true, true, true, true);
            frame.setLayout(new BorderLayout());
            frame.add(chartHeader(band), BorderLayout.NORTH);
            frame.add(new JScrollPane(plots), BorderLayout.CENTER);
            frame.setSize(1050, 650);
            frame.addToDesktopPane(); frame.center();
        } else if (measureBand != null) measureBand.setText(band);
        mirrorNavigationToChart();
        frame.setVisible(true); plots.revalidate(); plots.repaint();
        if (activeChart == null) {
            status.setText(empty);
            listen(exp.getSeqCamData() == null ? null : exp.getSeqCamData().getSequence());
            return;
        }
        status.setText(chosen.flyOnly ? "Fly evidence for this method; unknown values remain missing."
                : flies.isSelected() ? "Valid observations only. Light gray dashed traces show this method's fly evidence."
                : "Valid observations only; no normalization, smoothing or gap filling.");
        listen(exp.getSeqCamData() == null ? null : exp.getSeqCamData().getSequence());
    }
    public void refreshIfDisplayed(Experiment exp) { if (frame != null && frame.isVisible()) displayChartPanels(exp); }
    public void refreshVisibleCharts() { refreshIfDisplayed(selectedExperiment()); }
    public void closeAllCharts() {
        if (axes != null) axes.close(); axes = null;
        if (frame != null) frame.dispose(); frame = null; measureBand = null; navRow = null;
        chartMeasure = null; chartCageCaption = null; chartCage = null; chartSpot = null; chartSpotCaption = null; chartScale = null; activeChart = null;
        cursorGesture = false; draggingCursor = false; listen(null);
    }
    private void listen(Sequence sequence) {
        if (sequence != listenedSequence) {
            detachViewer();
            if (listenedSequence != null) listenedSequence.removeListener(this);
            listenedSequence = sequence;
            if (sequence != null) sequence.addListener(this);
        }
        attachViewer(sequence);
    }
    private void attachViewer(Sequence sequence) {
        Viewer viewer = sequence == null ? null : sequence.getFirstViewer();
        if (viewer != listenedViewer) {
            detachViewer();
            listenedViewer = viewer;
            if (viewer != null) viewer.addListener(this);
        }
        refreshFrameCursor();
    }
    private void detachViewer() {
        if (listenedViewer != null) listenedViewer.removeListener(this);
        listenedViewer = null;
    }
    private void installCursorDrag(ChartPanel panel, ValueMarker cursor) {
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                cursorGesture = nearVerticalCursor(e.getX(), markerScreenX(panel, cursor));
                draggingCursor = cursorGesture;
                if (!cursorGesture) return;
                savedDomainZoom = panel.isDomainZoomable();
                savedRangeZoom = panel.isRangeZoomable();
                panel.setDomainZoomable(false);
                panel.setRangeZoomable(false);
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (!cursorGesture) return;
                draggingCursor = false;
                panel.setDomainZoomable(savedDomainZoom);
                panel.setRangeZoomable(savedRangeZoom);
                refreshFrameCursor();
                boolean near = nearVerticalCursor(e.getX(), markerScreenX(panel, cursor));
                panel.setCursor(Cursor.getPredefinedCursor(near ? Cursor.E_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
                SwingUtilities.invokeLater(() -> cursorGesture = false);
            }
        });
        panel.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (!draggingCursor) return;
                double minutes = domainMinutes(panel, e.getX(), e.getY());
                if (Double.isNaN(minutes)) return;
                for (ValueMarker marker : frameCursors) {
                    marker.setValue(minutes);
                    marker.setAlpha(1f);
                }
                seekViewer(minutes);
            }
        });
    }

    private void seekViewer(double minutes) {
        Experiment exp = current != null ? current : selectedExperiment();
        int frame = frameAtChartMinutes(exp, minutes);
        if (frame < 0) return;
        Viewer viewer = listenedViewer != null ? listenedViewer : listenedSequence == null ? null : listenedSequence.getFirstViewer();
        if (viewer == null) return;
        if (viewer.getSequence() != null && viewer.getSequence().getSizeT() > 0)
            frame = Math.min(frame, viewer.getSequence().getSizeT() - 1);
        if (frame != viewer.getPositionT()) viewer.setPositionT(frame);
    }

    private void refreshFrameCursor() {
        if (draggingCursor) return;
        Double minutes = listenedViewer == null ? null : minutesAtFrame(current, listenedViewer.getPositionT());
        for (ValueMarker marker : frameCursors) {
            if (minutes == null) marker.setAlpha(0f);
            else { marker.setValue(minutes.doubleValue()); marker.setAlpha(1f); }
        }
    }
    static Double minutesAtFrame(Experiment exp, int frame) {
        if (exp == null || frame < 0) return null;
        long[] times = exp.getCamImages_ms();
        if (times == null || frame >= times.length) return null;
        return Double.valueOf(times[frame] / 60000.0);
    }

    /** Nearest camera frame on the same clock that places the viewer cursor. */
    static int frameAtChartMinutes(Experiment exp, double minutes) {
        if (exp == null || !(minutes >= 0) || Double.isNaN(minutes)) return -1;
        long[] times = exp.getCamImages_ms();
        if (times == null || times.length == 0) return -1;
        return nearest(times, Math.round(minutes * 60000.0));
    }

    static boolean nearVerticalCursor(double mouseX, double markerX) {
        return !Double.isNaN(markerX) && Math.abs(mouseX - markerX) <= CURSOR_GRAB_PX;
    }

    private static double markerScreenX(ChartPanel panel, ValueMarker marker) {
        if (panel == null || marker == null || marker.getAlpha() <= 0f || Double.isNaN(marker.getValue())) return Double.NaN;
        if (panel.getChartRenderingInfo() == null || panel.getChart() == null) return Double.NaN;
        Rectangle2D area = panel.getScreenDataArea();
        XYPlot plot = panel.getChart().getXYPlot();
        if (area == null || plot == null || plot.getDomainAxis() == null) return Double.NaN;
        return plot.getDomainAxis().valueToJava2D(marker.getValue(), area, plot.getDomainAxisEdge());
    }

    private static double domainMinutes(ChartPanel panel, int mouseX, int mouseY) {
        if (panel.getChartRenderingInfo() == null || panel.getChart() == null) return Double.NaN;
        XYPlot plot = panel.getChart().getXYPlot();
        Rectangle2D area = panel.getScreenDataArea(mouseX, mouseY);
        if (area == null || plot == null || plot.getDomainAxis() == null) return Double.NaN;
        return Math.max(0, plot.getDomainAxis().java2DToValue(mouseX, area, plot.getDomainAxisEdge()));
    }
    @Override public void sequenceChanged(SequenceEvent event) { if (listenedViewer == null) attachViewer(listenedSequence); }
    @Override public void sequenceClosed(Sequence sequence) { if (sequence == listenedSequence) closeAllCharts(); }
    @Override public void viewerChanged(ViewerEvent event) {
        if (event.getType() != ViewerEventType.POSITION_CHANGED || event.getDim() != DimensionId.T) return;
        if (event.getSource() == listenedViewer) refreshFrameCursor();
    }
    @Override public void viewerClosed(Viewer viewer) {
        if (viewer != listenedViewer) return;
        listenedViewer = null;
        refreshFrameCursor();
    }
    JPanel chartHeader(String band) {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(new Color(230, 236, 242));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(180, 190, 200)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        measureBand = new JLabel(band, SwingConstants.LEADING);
        measureBand.setFont(measureBand.getFont().deriveFont(Font.BOLD, 16f));
        title.add(measureBand, BorderLayout.CENTER);
        chartScale = new JSlider(CHART_SCALE_MIN, CHART_SCALE_MAX, chartScalePercent);
        JSlider scale = chartScale;
        scale.setOpaque(false);
        scale.setPreferredSize(new Dimension(140, 24));
        JLabel percent = new JLabel(chartScalePercent + "%");
        scale.addChangeListener(e -> {
            percent.setText(scale.getValue() + "%");
            if (updating || scale.getValueIsAdjusting() || scale.getValue() == chartScalePercent) return;
            if (scope.getSelectedIndex() == 0) honorChartScale = true;
            chartScalePercent = scale.getValue();
            refreshIfDisplayed(chartExperiment());
        });
        JPanel size = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        size.setOpaque(false);
        size.add(new JLabel("Chart size")); size.add(scale); size.add(percent);
        title.add(size, BorderLayout.EAST);
        header.add(title);
        navRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navRow.setOpaque(false);
        navRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartMeasure = new JComboBox<>();
        for (int i = 0; i < measure.getItemCount(); i++) chartMeasure.addItem(measure.getItemAt(i));
        chartMeasure.setSelectedItem(measure.getSelectedItem());
        chartMeasure.addActionListener(e -> { if (!updating) measure.setSelectedItem(chartMeasure.getSelectedItem()); });
        chartCage = new JComboBox<>();
        chartCage.setRenderer(cageRenderer());
        chartCage.addActionListener(e -> { if (!updating && chartCage.getSelectedItem() != null) cage.setSelectedItem(chartCage.getSelectedItem()); });
        chartSpot = new JComboBox<>();
        chartSpot.setRenderer(spotRenderer());
        chartSpot.setPreferredSize(new Dimension(280, 24));
        chartSpot.addActionListener(e -> { if (!updating && chartSpot.getSelectedItem() instanceof Spot) spotChoice.setSelectedItem(chartSpot.getSelectedItem()); });
        JButton update = new JButton("Update");
        update.addActionListener(e -> displayChartPanels(chartExperiment()));
        chartCageCaption = new JLabel("Cage");
        chartSpotCaption = new JLabel("Spot");
        navRow.add(chartMeasure);
        navRow.add(chartCageCaption); navRow.add(chartCage);
        navRow.add(chartSpotCaption); navRow.add(chartSpot);
        navRow.add(update);
        applyChartPickers(navRow, chartCageCaption, chartCage, chartSpotCaption, chartSpot, scope.getSelectedIndex());
        header.add(navRow);
        return header;
    }

    private Experiment chartExperiment() { return current != null ? current : selectedExperiment(); }

    void bindExperiment(Experiment exp) {
        if (exp == null || exp == current) return;
        closeAllCharts(); current = exp; honorChartScale = false;
        updating = true;
        try {
            cage.removeAllItems(); spotChoice.removeAllItems();
            TreeSet<Integer> ids = new TreeSet<>();
            for (Spot spot : exp.getSpots().getSpotList())
                if (spot != null && spot.getProperties() != null) ids.add(spot.getProperties().getCageID());
            for (int id : ids) cage.addItem(id);
        } finally { updating = false; }
    }

    void selectSpotForCurrentView(Experiment exp) {
        if (exp == null) return;
        bindExperiment(exp);
        List<Spot> all = exp.getSpots().getSpotList();
        Spot prefer = spotChoice.getSelectedItem() instanceof Spot ? (Spot) spotChoice.getSelectedItem() : null;
        if (prefer != null && !all.contains(prefer)) prefer = null;
        if (prefer == null) {
            List<Spot> initial = initialSpots(exp);
            prefer = initial.isEmpty() ? null : initial.get(0);
        }
        updating = true;
        try {
            if (prefer != null && prefer.getProperties() != null) cage.setSelectedItem(prefer.getProperties().getCageID());
            Integer cageId = (Integer) cage.getSelectedItem();
            spotChoice.removeAllItems();
            if (cageId == null) return;
            List<Spot> spots = spotsInCage(all, cageId.intValue());
            for (Spot spot : spots) spotChoice.addItem(spot);
            if (prefer == null || !spots.contains(prefer)) prefer = spots.isEmpty() ? null : spots.get(0);
            if (prefer != null) spotChoice.setSelectedItem(prefer);
        } finally { updating = false; }
    }

    private void fillSpotsOfSelectedCage(Experiment exp) {
        Integer cageId = (Integer) cage.getSelectedItem();
        updating = true;
        try {
            spotChoice.removeAllItems();
            if (exp == null || cageId == null) return;
            for (Spot spot : spotsInCage(exp.getSpots().getSpotList(), cageId.intValue())) spotChoice.addItem(spot);
            if (spotChoice.getItemCount() > 0) spotChoice.setSelectedIndex(0);
        } finally { updating = false; }
    }

    private void showChosenSpot(Spot spot) {
        if (spot == null) return;
        Experiment exp = chartExperiment();
        if (exp != null) SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(exp.getSeqCamData(), spot);
        if (owner != null) owner.dlgSpots.onMeasureChartSpotClicked(spot);
    }

    void mirrorNavigationToChart() {
        if (chartMeasure == null || chartCage == null || chartSpot == null) return;
        updating = true;
        try {
            chartMeasure.setSelectedItem(measure.getSelectedItem());
            chartCage.removeAllItems();
            for (int i = 0; i < cage.getItemCount(); i++) chartCage.addItem(cage.getItemAt(i));
            chartCage.setSelectedItem(cage.getSelectedItem());
            chartSpot.removeAllItems();
            for (int i = 0; i < spotChoice.getItemCount(); i++) chartSpot.addItem(spotChoice.getItemAt(i));
            chartSpot.setSelectedItem(spotChoice.getSelectedItem());
            applyChartPickers(navRow, chartCageCaption, chartCage, chartSpotCaption, chartSpot, scope.getSelectedIndex());
        } finally { updating = false; }
    }

    static void applyChartPickers(JComponent row, JComponent cageCaption, JComponent cageChoice,
            JComponent spotCaption, JComponent spotChoice, int scopeIndex) {
        boolean spotView = scopeIndex == 2;
        boolean cageChoiceVisible = scopeIndex == 1 || spotView;
        row.setVisible(true);
        if (cageCaption != null) cageCaption.setVisible(cageChoiceVisible);
        if (cageChoice != null) cageChoice.setVisible(cageChoiceVisible);
        if (spotCaption != null) spotCaption.setVisible(spotView);
        if (spotChoice != null) spotChoice.setVisible(spotView);
        if (row.getParent() != null) row.getParent().revalidate();
    }

    private static DefaultListCellRenderer cageRenderer() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Object label = value instanceof Integer ? "Cage " + value : value;
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        };
    }

    private static DefaultListCellRenderer spotRenderer() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Object label = value instanceof Spot ? spotMenuLabel((Spot) value) : value;
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        };
    }

    static String spotMenuLabel(Spot spot) {
        if (spot == null) return "";
        StringBuilder label = new StringBuilder(spot.getName() == null ? "" : spot.getName());
        String stimulus = spot.getProperties() == null ? null : spot.getProperties().getStimulus();
        String concentration = spot.getProperties() == null ? null : spot.getProperties().getConcentration();
        appendMenuText(label, stimulus);
        appendMenuText(label, concentration);
        return label.toString();
    }

    private static void appendMenuText(StringBuilder label, String text) {
        if (text == null) return;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || "..".equals(trimmed)) return;
        if (label.length() > 0) label.append(' ');
        label.append(trimmed);
    }

    static List<Spot> spotsInCage(List<Spot> spots, int cageId) {
        List<Spot> found = new ArrayList<>();
        if (spots == null) return found;
        for (Spot spot : spots)
            if (spot != null && spot.getProperties() != null && spot.getProperties().getCageID() == cageId) found.add(spot);
        found.sort(Comparator.comparing(spot -> spot.getName() == null ? "" : spot.getName()));
        return found;
    }

    static void focusClickedSample(Experiment exp, Spot spot, double timeMinutes) {
        focusClickedSample(exp, spot, null, timeMinutes);
    }

    static void focusClickedSample(Experiment exp, Spot spot, SpotMeasurementMethod method, double timeMinutes) {
        if (exp == null || spot == null) return;
        int frame = frameAtMinutes(recordedSeconds(spot, method), exp.getCamImages_ms(), timeMinutes);
        if (frame < 0) frame = ChartCamFrameNavigation.getFrameIndexFromTimeMinutes(exp, timeMinutes);
        if (frame >= 0) spot.setSpotCamDataT(frame);
        SpotChartRoiFocus.moveViewerToSpotTAndSelectRoi(exp.getSeqCamData(), spot);
    }

    /** Sample index on the plotted acquisition clock. A one-minute bin must not be used as the frame step. */
    static int frameAtMinutes(double[] timesSeconds, long[] cameraMs, double minutes) {
        if (timesSeconds == null || timesSeconds.length == 0 || !(minutes >= 0) || Double.isNaN(minutes)) return -1;
        int sample = nearest(timesSeconds, minutes * 60.0);
        if (cameraMs == null || cameraMs.length == 0 || cameraMs.length == timesSeconds.length) return sample;
        return nearest(cameraMs, Math.round(timesSeconds[sample] * 1000.0));
    }

    private static double[] recordedSeconds(Spot spot, SpotMeasurementMethod method) {
        if (spot == null || method == null) return null;
        SpotMethodResult result = spot.getMethodMeasurements().get(method);
        if (result == null) return null;
        double[] times = result.getTimesSeconds();
        return times.length == 0 ? null : times;
    }

    private static int nearest(double[] values, double target) {
        int best = 0;
        double distance = Math.abs(values[0] - target);
        for (int i = 1; i < values.length; i++) {
            double next = Math.abs(values[i] - target);
            if (next < distance) { best = i; distance = next; }
        }
        return best;
    }

    private static int nearest(long[] values, long target) {
        int best = 0;
        long distance = Math.abs(values[0] - target);
        for (int i = 1; i < values.length; i++) {
            long next = Math.abs(values[i] - target);
            if (next < distance) { best = i; distance = next; }
        }
        return best;
    }

    static String cageTitle(int cageId) { return "Cage " + cageId; }

    static String parameterBand(String label, String unit) {
        if (unit == null || unit.trim().isEmpty()) return label;
        return label + " (" + unit.trim() + ")";
    }

    static Dimension chartSize(int percent) {
        int scale = Math.max(CHART_SCALE_MIN, Math.min(CHART_SCALE_MAX, percent));
        return new Dimension(CHART_BASE_WIDTH * scale / 100, CHART_BASE_HEIGHT * scale / 100);
    }

    private static final Stroke FLY_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 3f }, 0f);
    static final Color FLY_TRACE = Color.LIGHT_GRAY;

    static void paintFlyPresence(XYLineAndShapeRenderer renderer, int seriesCount) {
        for (int i = 0; i < seriesCount; i++) {
            renderer.setSeriesPaint(i, FLY_TRACE);
            renderer.setSeriesStroke(i, FLY_STROKE);
            renderer.setSeriesVisibleInLegend(i, false);
        }
    }

    static void paintByStimulus(XYLineAndShapeRenderer renderer, List<Spot> spots, int seriesCount, boolean legend) {
        int n = Math.min(seriesCount, spots.size());
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            renderer.setSeriesPaint(i, colorOf(spots.get(i)));
            renderer.setSeriesVisibleInLegend(i, legend && seen.add(legendKey(spots.get(i))));
        }
        if (legend) renderer.setLegendItemLabelGenerator((XYSeriesLabelGenerator) (dataset, series) -> legendLabel(spots.get(series)));
    }

    static Color colorOf(Spot spot) {
        Color color = spot.getProperties() == null ? null : spot.getProperties().getColor();
        return color == null ? Color.GRAY : color;
    }

    static String legendLabel(Spot spot) {
        String stimulus = meaningfulStimulus(spot);
        return stimulus != null ? stimulus : ColorUtils.getFriendlyColorName(colorOf(spot));
    }

    private static String legendKey(Spot spot) {
        String stimulus = meaningfulStimulus(spot);
        return (stimulus == null ? "" : stimulus) + "\n" + colorOf(spot).getRGB();
    }

    private static String meaningfulStimulus(Spot spot) {
        if (spot.getProperties() == null || spot.getProperties().getStimulus() == null) return null;
        String stimulus = spot.getProperties().getStimulus().trim();
        return stimulus.isEmpty() || "..".equals(stimulus) ? null : stimulus;
    }

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

    private void placeCharts(CageChartGrid grid, Map<Integer, ChartPanel> charts, Dimension chartPx) {
        if (grid != null && grid.rows > 0 && grid.columns > 0) {
            plots.setLayout(new GridLayout(grid.rows, grid.columns, CHART_GAP, CHART_GAP));
            for (int row = 0; row < grid.rows; row++) for (int column = 0; column < grid.columns; column++) {
                ChartPanel panel = charts.get(Integer.valueOf(grid.ids[row][column]));
                if (panel != null) plots.add(panel);
                else {
                    JPanel blank = new JPanel();
                    blank.setPreferredSize(chartPx);
                    blank.setOpaque(false);
                    plots.add(blank);
                }
            }
            return;
        }
        plots.setLayout(new GridLayout(0, charts.size() > 1 ? 2 : 1, CHART_GAP, CHART_GAP));
        for (ChartPanel panel : charts.values()) plots.add(panel);
    }

    private static void fitChartText(JFreeChart chart, float titleSize) {
        if (chart.getTitle() != null)
            chart.getTitle().setFont(JFreeChart.DEFAULT_TITLE_FONT.deriveFont(Font.BOLD, titleSize));
        float item = Math.max(7f, titleSize - 2f);
        if (chart.getLegend() != null) chart.getLegend().setItemFont(chart.getLegend().getItemFont().deriveFont(item));
        XYPlot plot = chart.getXYPlot();
        Font label = plot.getDomainAxis().getLabelFont().deriveFont(item);
        Font tick = plot.getDomainAxis().getTickLabelFont().deriveFont(Math.max(7f, item - 1f));
        for (int i = 0; i < plot.getDomainAxisCount(); i++) if (plot.getDomainAxis(i) != null) {
            plot.getDomainAxis(i).setLabelFont(label);
            plot.getDomainAxis(i).setTickLabelFont(tick);
        }
        for (int i = 0; i < plot.getRangeAxisCount(); i++) if (plot.getRangeAxis(i) != null) {
            plot.getRangeAxis(i).setLabelFont(label);
            plot.getRangeAxis(i).setTickLabelFont(tick);
        }
    }

    private static Dimension screenSize() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        if (screen == null || screen.width < 100 || screen.height < 100) return new Dimension(1920, 1080);
        return screen;
    }

    static Dimension experimentCell(Dimension screen) {
        int width = (int) Math.floor((screen.width * 0.8 - CHART_GAP * 11) / 12.0);
        int height = (int) Math.floor((screen.height * 0.8 - CHART_GAP * 7) / 8.0);
        return new Dimension(Math.max(1, Math.min(CHART_BASE_WIDTH, width)), Math.max(1, Math.min(CHART_BASE_HEIGHT, height)));
    }

    static Dimension experimentChartSize(int percent, Dimension screen) {
        int scale = Math.max(CHART_SCALE_MIN, Math.min(CHART_SCALE_MAX, percent));
        Dimension floor = experimentCell(screen);
        double t = (scale - CHART_SCALE_MIN) / (double) (CHART_SCALE_MAX - CHART_SCALE_MIN);
        int width = (int) Math.round(floor.width + t * (CHART_BASE_WIDTH - floor.width));
        int height = (int) Math.round(floor.height + t * (CHART_BASE_HEIGHT - floor.height));
        return new Dimension(Math.max(1, width), Math.max(1, height));
    }

    static int percentThatFits(int columns, int rows, int requested, Dimension screen) {
        int upper = Math.max(CHART_SCALE_MIN, Math.min(CHART_SCALE_MAX, requested));
        for (int percent = upper; percent >= CHART_SCALE_MIN; percent--) {
            Dimension size = experimentChartSize(percent, screen);
            int width = size.width * Math.max(1, columns) + CHART_GAP * Math.max(0, columns - 1);
            int height = size.height * Math.max(1, rows) + CHART_GAP * Math.max(0, rows - 1);
            if (width <= screen.width * 0.8 && height <= screen.height * 0.8) return percent;
        }
        return CHART_SCALE_MIN;
    }

    static CageChartGrid experimentGrid(Experiment exp, Set<Integer> chartIds) {
        boolean physical = exp.getCages() != null && exp.getCages().cagesList != null && !exp.getCages().cagesList.isEmpty();
        List<CageBox> boxes = boxesFor(exp, physical ? null : chartIds);
        CageChartGrid grid = cageGrid(boxes);
        if (!chartIds.isEmpty() && !grid.containsAny(chartIds)) grid = cageGrid(boxesFor(exp, chartIds));
        List<CageBox> missing = new ArrayList<>();
        for (Integer id : chartIds) if (id != null && !grid.contains(id.intValue()))
            missing.add(new CageBox(id.intValue(), 0, 0, 0, 0, -1, -1));
        return missing.isEmpty() ? grid : appendUnplaced(grid, missing);
    }

    static List<CageBox> boxesFor(Experiment exp, Set<Integer> limit) {
        Map<Integer, CageBox> byId = new LinkedHashMap<>();
        if (exp.getCages() != null && exp.getCages().cagesList != null) {
            for (Cage cage : exp.getCages().cagesList) {
                if (cage == null || (limit != null && !limit.contains(cage.getCageID()))) continue;
                CageBox box = boxOf(cage);
                CageBox previous = byId.get(box.cageId);
                if (previous == null || (previous.width <= 0 && box.width > 0)) byId.put(box.cageId, box);
            }
        }
        if (limit != null) for (Integer id : limit) if (id != null) byId.putIfAbsent(id, new CageBox(id, 0, 0, 0, 0, -1, -1));
        return new ArrayList<>(byId.values());
    }

    static CageBox boxOf(Cage cage) {
        double x = 0, y = 0, width = 0, height = 0;
        if (cage.getRoi() != null && cage.getRoi().getBounds2D() != null) {
            Rectangle2D bounds = cage.getRoi().getBounds2D();
            if (bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                x = bounds.getCenterX(); y = bounds.getCenterY(); width = bounds.getWidth(); height = bounds.getHeight();
            }
        }
        int row = cage.getProperties() == null ? -1 : cage.getProperties().getArrayRow();
        int column = cage.getProperties() == null ? -1 : cage.getProperties().getArrayColumn();
        return new CageBox(cage.getCageID(), x, y, width, height, row, column);
    }

    static CageChartGrid cageGrid(List<CageBox> boxes) {
        List<CageBox> roi = new ArrayList<>();
        List<CageBox> rest = new ArrayList<>();
        if (boxes != null) for (CageBox box : boxes) {
            if (box == null) continue;
            if (box.width > 0 && box.height > 0) roi.add(box);
            else rest.add(box);
        }
        if (!roi.isEmpty()) {
            CageChartGrid grid = gridFromCenters(roi);
            return rest.isEmpty() ? grid : appendUnplaced(grid, rest);
        }
        boolean indexed = !rest.isEmpty();
        for (CageBox box : rest) if (box.arrayRow < 0 || box.arrayColumn < 0) indexed = false;
        if (indexed) return gridFromIndices(rest);
        return gridById(rest);
    }

    private static CageChartGrid gridFromCenters(List<CageBox> boxes) {
        double[] x = new double[boxes.size()], y = new double[boxes.size()];
        List<Double> widths = new ArrayList<>(), heights = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            x[i] = boxes.get(i).x; y[i] = boxes.get(i).y;
            widths.add(boxes.get(i).width); heights.add(boxes.get(i).height);
        }
        int[] columns = cluster(x, Math.max(1, median(widths) * 0.5));
        int[] rows = cluster(y, Math.max(1, median(heights) * 0.5));
        int rowCount = max(rows) + 1, columnCount = max(columns) + 1;
        int[][] ids = new int[rowCount][columnCount];
        for (int[] row : ids) Arrays.fill(row, -1);
        List<CageBox> lost = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (ids[rows[i]][columns[i]] >= 0) lost.add(boxes.get(i));
            else ids[rows[i]][columns[i]] = boxes.get(i).cageId;
        }
        CageChartGrid grid = new CageChartGrid(rowCount, columnCount, ids);
        return lost.isEmpty() ? grid : appendUnplaced(grid, lost);
    }

    private static CageChartGrid gridFromIndices(List<CageBox> boxes) {
        int rowCount = 0, columnCount = 0;
        for (CageBox box : boxes) {
            rowCount = Math.max(rowCount, box.arrayRow + 1);
            columnCount = Math.max(columnCount, box.arrayColumn + 1);
        }
        int[][] ids = new int[rowCount][columnCount];
        for (int[] row : ids) Arrays.fill(row, -1);
        List<CageBox> lost = new ArrayList<>();
        for (CageBox box : boxes) {
            if (ids[box.arrayRow][box.arrayColumn] >= 0) lost.add(box);
            else ids[box.arrayRow][box.arrayColumn] = box.cageId;
        }
        CageChartGrid grid = new CageChartGrid(rowCount, columnCount, ids);
        return lost.isEmpty() ? grid : appendUnplaced(grid, lost);
    }

    private static CageChartGrid gridById(List<CageBox> boxes) {
        List<CageBox> ordered = new ArrayList<>(boxes);
        ordered.sort(Comparator.comparingInt(box -> box.cageId));
        int count = ordered.size();
        int columns = count > 1 ? 2 : Math.max(1, count);
        int rows = count == 0 ? 0 : (count + columns - 1) / columns;
        int[][] ids = rows == 0 ? new int[0][0] : new int[rows][columns];
        for (int[] row : ids) Arrays.fill(row, -1);
        for (int i = 0; i < count; i++) ids[i / columns][i % columns] = ordered.get(i).cageId;
        return new CageChartGrid(rows, columns, ids);
    }

    private static CageChartGrid appendUnplaced(CageChartGrid grid, List<CageBox> rest) {
        List<CageBox> ordered = new ArrayList<>(rest);
        ordered.sort(Comparator.comparingInt(box -> box.cageId));
        int columns = Math.max(grid.columns, ordered.size());
        int rows = grid.rows + 1;
        int[][] ids = new int[rows][columns];
        for (int[] row : ids) Arrays.fill(row, -1);
        for (int row = 0; row < grid.rows; row++)
            for (int column = 0; column < grid.columns; column++) ids[row][column] = grid.ids[row][column];
        for (int i = 0; i < ordered.size(); i++) ids[grid.rows][i] = ordered.get(i).cageId;
        return new CageChartGrid(rows, columns, ids);
    }

    private static int[] cluster(double[] values, double threshold) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < values.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(index -> values[index]));
        int[] clusters = new int[values.length];
        if (values.length == 0) return clusters;
        int current = 0;
        double anchor = values[order[0]];
        clusters[order[0]] = 0;
        for (int i = 1; i < order.length; i++) {
            if (values[order[i]] - anchor > threshold) {
                current++;
                anchor = values[order[i]];
            }
            clusters[order[i]] = current;
        }
        return clusters;
    }

    private static int max(int[] values) {
        int best = 0;
        for (int value : values) best = Math.max(best, value);
        return best;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    static final class CageBox {
        final int cageId;
        final double x, y, width, height;
        final int arrayRow, arrayColumn;
        CageBox(int cageId, double x, double y, double width, double height, int arrayRow, int arrayColumn) {
            this.cageId = cageId; this.x = x; this.y = y; this.width = width; this.height = height;
            this.arrayRow = arrayRow; this.arrayColumn = arrayColumn;
        }
    }

    static final class CageChartGrid {
        final int rows, columns;
        final int[][] ids;
        CageChartGrid(int rows, int columns, int[][] ids) {
            this.rows = rows; this.columns = columns; this.ids = ids;
        }
        boolean containsAny(Set<Integer> cageIds) {
            for (int[] row : ids) for (int id : row) if (cageIds.contains(id)) return true;
            return false;
        }
        boolean contains(int cageId) {
            for (int[] row : ids) for (int id : row) if (id == cageId) return true;
            return false;
        }
    }
}
