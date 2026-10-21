package plugins.fmp.multicafe.dlg.kymos;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.image.IcyBufferedImage;
import icy.painter.Overlay;
import icy.roi.ROI2D;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import icy.type.point.Point5D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.inspection.EventInspectorTimeAxis;
import plugins.fmp.multitools.experiment.inspection.EventReview;
import plugins.fmp.multitools.experiment.inspection.EventReviewCsv;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.tools.ViewerFMP;
import plugins.fmp.multitools.tools.chart.interaction.ChartCamFrameNavigation;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Diagnostic controls for the existing kymograph viewer. X is a zero-based time
 * column; viewer T selects a capillary, not an acquisition frame. Review metadata
 * and detached, read-only measurement overlays never enter the analysis pipeline.
 */
public final class EventInspector extends JPanel implements ViewerListener {
    private static final long serialVersionUID = 1L;
    private final JComboBox<CapillaryChoice> capillaries = new JComboBox<>();
    private final JSpinner columnInput = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
    private final JSpinner timeInput = new JSpinner(new SpinnerNumberModel(0.0, null, null, 0.1));
    private final JSpinner halfWindow = new JSpinner(new SpinnerNumberModel(10.0, 0.01, 14400.0, 1.0));
    private final JSlider slider = new JSlider(0, 0, 0);
    private final JCheckBox showMeasures = new JCheckBox("Menisci / detected gulps", true);
    private final JCheckBox showCursor = new JCheckBox("Cursor", true);
    private final JComboBox<String> classification = new JComboBox<>(EventReview.CLASSIFICATIONS);
    private final JTextField comment = new JTextField(25);
    private final JLabel status = new JLabel("Open kymographs, then select a capillary.");
    private final JLabel position = new JLabel(" ");
    private final JLabel saveStatus = new JLabel("Reviews are saved separately from measurements.");
    private final JButton save = new JButton("Save review CSV...");
    private final XYSeriesCollection dataset = new XYSeriesCollection();
    private final JFreeChart chart = ChartFactory.createXYLineChart("Meniscus positions", "Time (min)",
            "Stored position (px)", dataset, PlotOrientation.VERTICAL, true, true, false);
    private final ValueMarker marker = new ValueMarker(0, Color.MAGENTA, new BasicStroke(1));
    private final InspectorOverlay overlay = new InspectorOverlay();
    private final Map<Layer, Boolean> hiddenLayers = new LinkedHashMap<>();
    private MultiCAFE parent;
    private Experiment experiment;
    private Sequence sequence;
    private Viewer viewer;
    private Capillary capillary;
    private EventInspectorTimeAxis axis;
    private List<ROI2D> detachedMeasures = new ArrayList<>();
    private int column;
    private int kymoT = -1;
    private int imageHeight;
    private int windowLeft;
    private int windowRight;
    private int entryT = -1;
    private boolean active;
    private boolean updating;
    private boolean saving;
    private IcyFrame chartFrame;
    private Path reviewPath;

    public void init(MultiCAFE owner) {
        parent = owner;
        setLayout(new GridLayout(0, 1));
        JButton refresh = new JButton("Inspect / refresh");
        add(row(new JLabel("Capillary"), capillaries, refresh));
        JButton previous = new JButton("<");
        JButton next = new JButton(">");
        add(row(previous, next, new JLabel("Column (0-based)"), columnInput));
        add(slider);
        JButton goTime = new JButton("Go to time");
        add(row(new JLabel("Minutes"), timeInput, goTime, new JLabel("Window +/- min"), halfWindow));
        JButton previousGulp = new JButton("Previous gulp");
        JButton nextGulp = new JButton("Next gulp");
        add(row(previousGulp, nextGulp, showMeasures, showCursor));
        add(position);
        add(status);
        add(row(new JLabel("Classification"), classification));
        add(row(new JLabel("Comment"), comment));
        JButton loadReviews = new JButton("Load reviews...");
        add(row(save, loadReviews));
        add(saveStatus);
        classification.setSelectedItem("Uncertain");
        chart.getXYPlot().addDomainMarker(marker);
        chart.getXYPlot().getRangeAxis().setInverted(true); // Same downward-positive orientation as the kymograph.
        columnInput.addChangeListener(e -> { if (!updating) navigate(((Number) columnInput.getValue()).intValue()); });
        slider.addChangeListener(e -> { if (!updating) navigate(slider.getValue()); });
        halfWindow.addChangeListener(e -> { if (!updating) navigate(column); });
        defineActionListeners(refresh, previous, next, goTime, previousGulp, nextGulp, loadReviews);
        comment.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rememberDraft(); }
            @Override public void removeUpdate(DocumentEvent e) { rememberDraft(); }
            @Override public void changedUpdate(DocumentEvent e) { rememberDraft(); }
        });
        owner.mainFrame.addFrameListener(new IcyFrameAdapter() {
            @Override public void icyFrameClosed(IcyFrameEvent e) { deactivate(); }
        });
        defineToolTips();
    }

    private void defineActionListeners(JButton refresh, JButton previous, JButton next, JButton goTime, JButton previousGulp, JButton nextGulp, JButton loadReviews) {
        refresh.addActionListener(e -> activate());
        capillaries.addActionListener(e -> { if (active && !updating) selectCapillary(); });
        previous.addActionListener(e -> navigate(column - 1));
        next.addActionListener(e -> navigate(column + 1));
        goTime.addActionListener(e -> {
            if (axis != null) navigate(axis.nearestColumn(((Number) timeInput.getValue()).doubleValue()));
        });
        showMeasures.addActionListener(e -> { if (sequence != null) overlay.painterChanged(); });
        showCursor.addActionListener(e -> { marker.setAlpha(showCursor.isSelected() ? 1f : 0f); overlay.painterChanged(); });
        previousGulp.addActionListener(e -> navigateGulp(-1));
        nextGulp.addActionListener(e -> navigateGulp(1));
        save.addActionListener(e -> saveReview());
        loadReviews.addActionListener(e -> loadReviews());
        classification.addActionListener(e -> rememberDraft());
    }

    private void defineToolTips() {
        columnInput.setToolTipText("Zero-based kymograph column; not the viewer T index");
        slider.setToolTipText("Kymograph time column");
    }

    private static JPanel row(java.awt.Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (java.awt.Component c : components) row.add(c);
        return row;
    }

    public boolean isInspecting(Sequence seq) { return active && sequence == seq; }

    /** The tab only launches this independent controls-and-chart window. */
    public void openWindow() {
        if (chartFrame == null) {
            activate();
            // Also show controls and the explanatory status when no data is loaded yet.
            showChart();
        }
        chartFrame.setVisible(true);
        chartFrame.toFront();
        chartFrame.requestFocus();
    }

    public void experimentChanged(Experiment exp) {
        if (experiment != exp) {
            deactivate();
            experiment = exp;
            reviewPath = null;
            updating = true;
            capillaries.removeAllItems();
            updating = false;
        }
    }

    public void activate() {
        Experiment selected = (Experiment) parent.expListComboLazy.getSelectedItem();
        experimentChanged(selected);
        if (selected == null || selected.getSeqKymos() == null
                || selected.getSeqKymos().getSequence() == null
                || selected.getSeqKymos().getSequence().getSizeT() == 0) {
            status.setText("Load this experiment's kymographs and saved measurements first.");
            return;
        }
        Sequence target = selected.getSeqKymos().getSequence();
        if (sequence != target) {
            deactivate();
            sequence = target;
        }
        active = true;
        viewer = sequence.getFirstViewer();
        if (viewer == null) viewer = new ViewerFMP(sequence, true, true);
        if (entryT < 0) entryT = viewer.getPositionT();
        viewer.removeListener(this);
        viewer.addListener(this);
        if (!sequence.getOverlays().contains(overlay)) sequence.addOverlay(overlay);
        int selectedT = viewer.getPositionT();
        updating = true;
        capillaries.removeAllItems();
        for (int t = 0; t < sequence.getSizeT(); t++) {
            Capillary cap = selected.getSeqKymos().getCapillaryForFrame(t, selected.getCapillaries());
            if (cap != null) {
                CapillaryChoice item = new CapillaryChoice(cap, t);
                capillaries.addItem(item);
                if (t == selectedT) capillaries.setSelectedItem(item);
            }
        }
        updating = false;
        selectCapillary();
    }

    public void deactivate() {
        // The existing viewer may have unsaved editable ROIs for its entry capillary.
        // Return to that capillary before restoring those layers, without validating them.
        if (active && viewer != null && entryT >= 0) {
            updating = true;
            viewer.setPositionT(entryT);
            updating = false;
        }
        active = false;
        entryT = -1;
        if (viewer != null) viewer.removeListener(this);
        if (sequence != null) sequence.removeOverlay(overlay);
        restoreLayers();
        if (chartFrame != null) {
            IcyFrame closing = chartFrame;
            chartFrame = null;
            closing.dispose();
        }
        viewer = null;
        sequence = null;
        capillary = null;
        axis = null;
        detachedMeasures.clear();
        dataset.removeAllSeries();
        status.setText("Open kymographs, then select a capillary.");
    }

    private void selectCapillary() {
        axis = null;
        restoreLayers();
        dataset.removeAllSeries();
        detachedMeasures.clear();
        CapillaryChoice choice = (CapillaryChoice) capillaries.getSelectedItem();
        if (!active || choice == null) return;
        capillary = choice.capillary;
        kymoT = choice.t;
        try {
            if (experiment.getKymoSubsampleFactor() != 1)
                throw new IllegalArgumentException("Only one-frame-per-column kymographs are supported.");
            IcyBufferedImage image = sequence.getImage(kymoT, 0);
            if (image == null) throw new IllegalArgumentException("Kymograph image is unavailable.");
            imageHeight = image.getHeight();
            // Do not call getMeasureTimeMinutes(): it may rebuild and persist the timescale.
            EventInspectorTimeAxis resolvedAxis = EventInspectorTimeAxis.create(image.getWidth(),
                    experiment.getKymoSubsampleFactor(), experiment.getCamImages_ms(),
                    experiment.getKymoFirst_ms(), experiment.getKymoBin_ms());
            validateMeasure(capillary.getTopRaw(), image.getWidth());
            validateMeasure(capillary.getBottomRaw(), image.getWidth());
            if (capillary.getTopRaw().getNPoints() == 0 && capillary.getBottomRaw().getNPoints() == 0)
                throw new IllegalArgumentException("No saved meniscus measurements for this capillary.");
            axis = resolvedAxis;
            restoreLayers();
            updating = true;
            viewer.setPositionT(kymoT);
            updating = false;
            // Reuse the existing ROI construction, but never insert inspector ROIs into the sequence.
            detachedMeasures = capillary.getROIsForCapillaryAtT(kymoT);
            for (ROI2D roi : detachedMeasures) { roi.setT(kymoT); roi.setReadOnly(true); }
            hideMeasurementLayers();
            addSeries("Upper meniscus", capillary.getTopRaw());
            addSeries("Lower meniscus", capillary.getBottomRaw());
            updating = true;
            columnInput.setModel(new SpinnerNumberModel(Math.min(column, axis.size() - 1), 0, axis.size() - 1, 1));
            slider.setMaximum(axis.size() - 1);
            updating = false;
            status.setText(axis.hasAcquisitionTimes() ? "Frame timestamps; stored kymograph pixels." :
                    "Nominal timing only; camera frame mapping unavailable.");
            showChart();
            navigate(Math.min(column, axis.size() - 1));
        } catch (IllegalArgumentException ex) {
            axis = null;
            status.setText(ex.getMessage());
            overlay.painterChanged();
        } finally {
            updating = false;
        }
    }

    private static void validateMeasure(CapillaryMeasure measure, int width) {
        Level2D p = measure.getPolylineLevel();
        for (int i = 0; p != null && i < p.npoints; i++)
            if (!Double.isFinite(p.xpoints[i]) || p.xpoints[i] != Math.rint(p.xpoints[i])
                    || p.xpoints[i] < 0 || p.xpoints[i] >= width
                    || (i > 0 && p.xpoints[i] <= p.xpoints[i - 1]))
                throw new IllegalArgumentException("Measurement X coordinates do not match this kymograph.");
    }

    private void addSeries(String label, CapillaryMeasure measure) {
        XYSeries series = new XYSeries(label, false, false);
        Level2D p = measure.getPolylineLevel();
        for (int i = 0; p != null && i < p.npoints; i++)
            series.add(axis.minutes((int) p.xpoints[i]), p.ypoints[i]);
        dataset.addSeries(series);
    }

    private void showChart() {
        if (chartFrame == null) {
            ChartPanel panel = new ChartPanel(chart);
            panel.addChartMouseListener(new ChartMouseListener() {
                @Override public void chartMouseMoved(ChartMouseEvent e) { }
                @Override public void chartMouseClicked(ChartMouseEvent e) {
                    if (axis != null && e.getTrigger().getButton() == MouseEvent.BUTTON1
                            && panel.getScreenDataArea().contains(e.getTrigger().getPoint()))
                        navigate(axis.nearestColumn(ChartCamFrameNavigation.getTimeMinutesFromEvent(
                                e, panel, chart.getXYPlot())));
                }
            });
            chartFrame = new IcyFrame("Event Inspector", true, true, true, true);
            chartFrame.setLayout(new BorderLayout());
            JScrollPane controls = new JScrollPane(this);
            controls.setPreferredSize(new Dimension(760, Math.min(360, getPreferredSize().height + 8)));
            chartFrame.add(controls, BorderLayout.NORTH);
            chartFrame.add(panel, BorderLayout.CENTER);
            chartFrame.setSize(820, 720);
            chartFrame.addToDesktopPane();
            final IcyFrame opened = chartFrame;
            chartFrame.addFrameListener(new IcyFrameAdapter() {
                @Override public void icyFrameClosed(IcyFrameEvent e) {
                    if (chartFrame == opened) {
                        chartFrame = null;
                        deactivate();
                    }
                }
            });
        }
        chartFrame.setVisible(true);
    }

    private void navigate(int requested) {
        if (!active || axis == null) return;
        column = Math.max(0, Math.min(axis.size() - 1, requested));
        updating = true;
        columnInput.setValue(column);
        slider.setValue(column);
        timeInput.setValue(axis.minutes(column));
        updating = false;
        double half = ((Number) halfWindow.getValue()).doubleValue();
        XYPlot plot = chart.getXYPlot();
        plot.getDomainAxis().setRange(axis.minutes(column) - half, axis.minutes(column) + half);
        marker.setValue(axis.minutes(column));
        int left = axis.nearestColumn(axis.minutes(column) - half);
        int right = axis.nearestColumn(axis.minutes(column) + half);
        windowLeft = left;
        windowRight = right;
        if (viewer.getCanvas() instanceof Canvas2D) {
            Canvas2D canvas = (Canvas2D) viewer.getCanvas();
            canvas.setFitToCanvas(false);
            Rectangle window = new Rectangle(left, 0, Math.max(1, right - left + 1), imageHeight);
            canvas.centerOn(window);
        }
        position.setText(String.format(java.util.Locale.ROOT, "Column %d | %.4f min | upper %s | lower %s | gulp %s px",
                column, axis.minutes(column), number(value(capillary.getTopRaw().getPolylineLevel(), column)),
                number(value(capillary.getBottomRaw().getPolylineLevel(), column)),
                number(value(capillary.getGulps().getHeightSeries(), column))));
        updating = true;
        classification.setSelectedItem("Uncertain");
        comment.setText("");
        for (EventReview review : experiment.getEventReviews())
            if (review.get(3).equals(sourceKymograph()) && review.get(4).equals(Integer.toString(column))) {
                classification.setSelectedItem(review.get(11));
                comment.setText(review.get(12));
            }
        updating = false;
        overlay.painterChanged();
    }

    private void rememberDraft() {
        if (!updating && active && axis != null) {
            experiment.putEventReview(observation());
            saveStatus.setText("Review draft in memory; use Save review CSV to persist it.");
        }
    }

    private void navigateGulp(int direction) {
        if (axis == null) return;
        Level2D gulps = capillary.getGulps().getHeightSeries();
        for (int i = column + direction; i >= 0 && i < axis.size(); i += direction) {
            double amplitude = value(gulps, i);
            if (Double.isFinite(amplitude) && amplitude != 0) { navigate(i); return; }
        }
        saveStatus.setText("No further detected gulp in that direction.");
    }

    private void hideMeasurementLayers() {
        if (viewer == null || viewer.getCanvas() == null) return;
        for (Layer layer : viewer.getCanvas().getLayers(false)) {
            if (layer.getAttachedROI() == null) continue;
            String name = layer.getAttachedROI().getName();
            if (name != null && (name.contains("toplevel") || name.contains("bottomlevel")
                    || name.contains("derivative") || name.contains("gulp"))) {
                hiddenLayers.putIfAbsent(layer, layer.isVisible());
                layer.setVisible(false);
            }
        }
    }

    private void restoreLayers() {
        for (Map.Entry<Layer, Boolean> entry : hiddenLayers.entrySet()) entry.getKey().setVisible(entry.getValue());
        hiddenLayers.clear();
    }

    static double value(Level2D p, int x) {
        if (p == null || x < 0) return Double.NaN;
        // Preserve missing columns; do not interpolate a measurement during inspection.
        int low = 0, high = p.npoints - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (p.xpoints[mid] == x) return p.ypoints[mid];
            if (p.xpoints[mid] < x) low = mid + 1; else high = mid - 1;
        }
        return Double.NaN;
    }

    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : ""; }
    private String sourceKymograph() {
        String source = experiment.getSeqKymos().getFileNameFromImageList(kymoT);
        if (source == null) source = capillary.getKymographFileName();
        return source == null ? "" : source;
    }

    private EventReview observation() {
        int frame = axis.cameraFrame(column);
        FrameTimeScale scale = experiment.getFrameTimeScale();
        String acquisition = "", sourceImage = "", original = "";
        if (frame >= 0 && scale != null && frame < scale.size()) {
            sourceImage = scale.getImageRelativePath(frame);
            original = Long.toString(scale.getAbsoluteFirstIndex() + frame);
            if (scale.getT0EpochMs() >= 0)
                acquisition = Instant.ofEpochMilli(scale.getT0EpochMs() + axis.timeMs(column)).toString();
        } else if (frame >= 0 && experiment.getSeqCamData() != null) {
            sourceImage = experiment.getSeqCamData().getFileNameFromImageList(frame);
            original = Long.toString(experiment.getSeqCamData().getImageLoader().getAbsoluteIndexFirstImage() + frame);
        }
        double upper = value(capillary.getTopRaw().getPolylineLevel(), column);
        double lower = value(capillary.getBottomRaw().getPolylineLevel(), column);
        return new EventReview(experiment.getExperimentDirectory(), Integer.toString(capillary.getCageID()),
                capillary.getRoiName() != null ? capillary.getRoiName() : capillary.getKymographName(),
                sourceKymograph(), Integer.toString(column), frame >= 0 ? Integer.toString(frame) : "",
                original, Long.toString(axis.timeMs(column)), acquisition,
                axis.hasAcquisitionTimes() ? "frame_timestamps" : "nominal", sourceImage,
                (String) classification.getSelectedItem(), comment.getText(), number(upper), number(lower),
                number(upper - value(capillary.getTopRaw().getPolylineLevel(), column - 1)),
                number(lower - value(capillary.getBottomRaw().getPolylineLevel(), column - 1)),
                number(value(capillary.getGulps().getHeightSeries(), column)));
    }

    private void saveReview() {
        if (axis == null || saving) return;
        JFileChooser chooser = new JFileChooser();
        File dir = experiment.getResultsDirectory() == null ? null : new File(experiment.getResultsDirectory());
        chooser.setSelectedFile(reviewPath != null ? reviewPath.toFile() : new File(dir, "EventInspectorReviews.csv"));
        chooser.setDialogTitle("Save / update diagnostic review CSV");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final Path path = chooser.getSelectedFile().toPath();
        final EventReview review = observation();
        final Experiment reviewedExperiment = experiment;
        reviewedExperiment.putEventReview(review);
        saving = true;
        save.setEnabled(false);
        saveStatus.setText("Saving...");
        new SwingWorker<List<EventReview>, Void>() {
            @Override protected List<EventReview> doInBackground() throws Exception {
                EventReviewCsv.save(path, review);
                return EventReviewCsv.read(path);
            }
            @Override protected void done() {
                saving = false;
                save.setEnabled(true);
                try {
                    for (EventReview row : get())
                        if (row.get(0).equals(review.get(0)) && reviewedExperiment.getEventReviews().stream()
                                .noneMatch(draft -> draft.key().equals(row.key()))) reviewedExperiment.putEventReview(row);
                    if (experiment == reviewedExperiment) {
                        reviewPath = path;
                        saveStatus.setText("Saved column " + review.get(4));
                        saveStatus.setToolTipText(path.toAbsolutePath().toString());
                    }
                } catch (Exception ex) {
                    saveStatus.setText("Review was not saved.");
                    JOptionPane.showMessageDialog(EventInspector.this, ex.getCause() != null ? ex.getCause().getMessage()
                            : ex.getMessage(), "Cannot save review", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadReviews() {
        if (experiment == null || saving) return;
        JFileChooser chooser = new JFileChooser();
        if (experiment.getResultsDirectory() != null)
            chooser.setCurrentDirectory(new File(experiment.getResultsDirectory()));
        chooser.setDialogTitle("Load EventInspector annotations (not an event queue)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final Path path = chooser.getSelectedFile().toPath();
        final Experiment target = experiment;
        new SwingWorker<List<EventReview>, Void>() {
            @Override protected List<EventReview> doInBackground() throws Exception { return EventReviewCsv.read(path); }
            @Override protected void done() {
                try {
                    int count = 0;
                    for (EventReview review : get())
                        if (review.get(0).equals(target.getExperimentDirectory())) {
                            target.putEventReview(review);
                            count++;
                        }
                    if (experiment == target) {
                        reviewPath = path;
                        navigate(column);
                        saveStatus.setText("Loaded " + count + " reviews for this experiment.");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EventInspector.this,
                            ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage(),
                            "Cannot load reviews", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    @Override public void viewerChanged(ViewerEvent event) {
        if (!active || event.getSource() != viewer || updating) return;
        if (event.getType() == ViewerEvent.ViewerEventType.POSITION_CHANGED && event.getDim() == DimensionId.T) {
            int t = viewer.getPositionT();
            for (int i = 0; i < capillaries.getItemCount(); i++)
                if (capillaries.getItemAt(i).t == t) {
                    updating = true;
                    capillaries.setSelectedIndex(i);
                    updating = false;
                    selectCapillary();
                    break;
                }
        }
    }

    @Override public void viewerClosed(Viewer closed) {
        if (viewer == closed) { entryT = -1; deactivate(); }
    }

    private static final class CapillaryChoice {
        final Capillary capillary;
        final int t;
        CapillaryChoice(Capillary cap, int index) { capillary = cap; t = index; }
        @Override public String toString() { return "Cage " + capillary.getCageID() + " / " + capillary.getRoiName(); }
    }

    private final class InspectorOverlay extends Overlay {
        InspectorOverlay() { super("EventInspector (diagnostic only)"); }
        @Override public void paint(Graphics2D graphics, Sequence seq, IcyCanvas canvas) {
            if (!active || axis == null || canvas.getPositionT() != kymoT || graphics == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                // The canvas may include extra context to preserve image aspect ratio.
                // Shade the exact selected column window without cropping the source image.
                g.setColor(new Color(100, 160, 255, 25));
                g.fillRect(windowLeft, 0, windowRight - windowLeft + 1, imageHeight);
                if (showMeasures.isSelected())
                    for (ROI2D roi : detachedMeasures)
                        if (!roi.getName().contains("derivative")) roi.getOverlay().paint(g, seq, canvas);
                if (showCursor.isSelected()) {
                    g.setColor(Color.MAGENTA);
                    g.setStroke(new BasicStroke(1));
                    g.drawLine(column, 0, column, imageHeight);
                }
            } finally { g.dispose(); }
        }
        @Override public void mouseClick(MouseEvent event, Point5D.Double point, IcyCanvas canvas) {
            if (active && axis != null && point != null && canvas.getPositionT() == kymoT
                    && event.getButton() == MouseEvent.BUTTON1 && point.x >= 0 && point.x < axis.size()) {
                SwingUtilities.invokeLater(() -> navigate((int) Math.floor(point.x)));
                event.consume();
            }
        }
    }
}
