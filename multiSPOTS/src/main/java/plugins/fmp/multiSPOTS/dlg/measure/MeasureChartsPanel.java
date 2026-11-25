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
    static final int CHART_SCALE_MIN = 30;
    static final int CHART_SCALE_MAX = 100;
    private final JPanel plots = new JPanel(new GridLayout(0, 2, 4, 4));
    private int chartScalePercent = 40;
    private JLabel measureBand;
    private JPanel navRow;
    private JComboBox<Measure> chartMeasure;
    private JComboBox<Integer> chartCage;
    private JComboBox<Spot> chartSpot;
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
        plots.setLayout(new GridLayout(0, groups.size() > 1 ? 2 : 1, 4, 4));
        plots.removeAll(); frameCursors.clear(); activeChart = null;
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
            float titleSize = Math.max(11f, 16f * chartScalePercent / 100f);
            ChartPanel panel = new ChartPanel(new JFreeChart(cageTitle(group.getKey()),
                    JFreeChart.DEFAULT_TITLE_FONT.deriveFont(Font.BOLD, titleSize), plot, true));
            Dimension size = chartSize(chartScalePercent);
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
            plots.add(panel); if (activeChart == null) activeChart = panel;
        }
        if (activeChart == null && !single) {
            if (frame != null) frame.setVisible(false);
            status.setText("No primary measurements for this view. Collect or load the method's primary measurements."); return;
        }
        if (activeChart == null) plots.add(new JLabel("No primary measurements for this spot."));
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
            status.setText("No primary measurements for this spot.");
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
        chartMeasure = null; chartCage = null; chartSpot = null; activeChart = null;
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
    private JPanel chartHeader(String band) {
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
        JSlider scale = new JSlider(CHART_SCALE_MIN, CHART_SCALE_MAX, chartScalePercent);
        scale.setOpaque(false);
        scale.setPreferredSize(new Dimension(140, 24));
        JLabel percent = new JLabel(chartScalePercent + "%");
        scale.addChangeListener(e -> {
            percent.setText(scale.getValue() + "%");
            if (updating || scale.getValueIsAdjusting() || scale.getValue() == chartScalePercent) return;
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
        navRow.add(chartMeasure);
        navRow.add(new JLabel("Cage")); navRow.add(chartCage);
        navRow.add(new JLabel("Spot")); navRow.add(chartSpot);
        navRow.add(update);
        navRow.setVisible(scope.getSelectedIndex() == 2);
        header.add(navRow);
        return header;
    }

    private Experiment chartExperiment() { return current != null ? current : selectedExperiment(); }

    void bindExperiment(Experiment exp) {
        if (exp == null || exp == current) return;
        closeAllCharts(); current = exp;
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

    private void mirrorNavigationToChart() {
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
            if (navRow != null) navRow.setVisible(scope.getSelectedIndex() == 2);
        } finally { updating = false; }
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
}
