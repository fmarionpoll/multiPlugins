package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.VerticalCorrelation;

/** Standalone preview: drag the threshold and inspect aligned original pixels. */
public final class CorrelationGulpPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Experiment experiment;
    private final JComboBox<Capillary> capillaries = new JComboBox<>();
    private final JSpinner radius = new JSpinner(new SpinnerNumberModel(10, 3, 20, 1));
    private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 10000.0, .05));
    private final JButton compute = new JButton("Compute"), automatic = new JButton("Automatic"), export = new JButton("Export CSV...");
    private final JLabel source = new JLabel(" "), status = new JLabel("Select a capillary and compute its correlation motion.");
    private final MotionPlot plot = new MotionPlot();
    private final AlignmentView alignment = new AlignmentView();
    private final Map<Capillary, Double> manualThresholds = new IdentityHashMap<>();
    private SwingWorker<CorrelationGulpPreview, Void> worker;
    private CorrelationGulpPreview preview;
    private Capillary computedCapillary;
    private BufferedImage rawStrip, refinedStrip;
    private int selectedFrame;
    private boolean settingThreshold, manual;

    public static void open(Component owner, Experiment exp, int initialKymo) {
        CorrelationGulpPanel panel = new CorrelationGulpPanel(exp, initialKymo);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Gulp correlation prototype", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panel);
        dialog.setSize(1180, 880);
        dialog.setMinimumSize(new Dimension(920, 700));
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { if (panel.worker != null) panel.worker.cancel(true); }
        });
        dialog.setVisible(true);
        panel.compute();
    }

    public CorrelationGulpPanel(Experiment exp, int initialKymo) {
        super(new BorderLayout(6, 6));
        experiment = exp;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (Capillary cap : exp.getCapillaries().getList()) {
            capillaries.addItem(cap);
            if (cap.getKymographIndex() == initialKymo) capillaries.setSelectedItem(cap);
        }
        capillaries.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Capillary) {
                    Capillary cap = (Capillary) value;
                    setText(cap.getLast2ofCapillaryName() + (cap.getNFlies() == 0 ? " (empty)" : ""));
                }
                return this;
            }
        });
        JPanel header = new JPanel(new BorderLayout());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Capillary")); controls.add(capillaries);
        controls.add(new JLabel("Window +/- pixels")); controls.add(radius); controls.add(compute);
        JPanel thresholdControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        thresholdControls.add(new JLabel("Threshold (pixels/min)")); thresholdControls.add(threshold);
        thresholdControls.add(automatic); thresholdControls.add(export);
        JPanel rows = new JPanel(new GridLayout(0, 1));
        rows.add(controls); rows.add(thresholdControls);
        header.add(rows, BorderLayout.NORTH);
        header.add(source, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, plot, alignment);
        split.setResizeWeight(.43);
        split.setDividerLocation(315);
        add(split, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(4, 4));
        footer.add(status, BorderLayout.NORTH);
        footer.add(new JLabel("Preview only. Drag the red bar to adjust the threshold; click the graph to inspect a time point. Export saves this preview."), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        automatic.setEnabled(false); export.setEnabled(false); threshold.setEnabled(false);
        threshold.setToolTipText("Threshold on downward motion minus expected evaporation, divided by interval duration.");
        automatic.setToolTipText("Quiet-period median + 3 robust noise SD, with an empty-capillary noise floor.");
        compute.addActionListener(e -> compute());
        capillaries.addActionListener(e -> clearPreview());
        radius.addChangeListener(e -> clearPreview());
        automatic.addActionListener(e -> {
            if (preview != null && Double.isFinite(preview.noise.automatic)) setThreshold(preview.noise.automatic, false);
        });
        threshold.addChangeListener(e -> {
            if (!settingThreshold && preview != null) {
                manual = true;
                manualThresholds.put(computedCapillary, value());
            }
            refresh();
        });
        export.addActionListener(e -> export());
    }

    private void clearPreview() {
        preview = null; computedCapillary = null;
        rawStrip = null; refinedStrip = null;
        automatic.setEnabled(false); export.setEnabled(false); threshold.setEnabled(false);
        source.setText(" "); status.setText("Click Compute to analyze the selected capillary and window.");
        plot.repaint(); alignment.repaint();
    }

    private void compute() {
        if (worker != null && !worker.isDone()) return;
        Capillary cap = (Capillary) capillaries.getSelectedItem();
        if (cap == null) { status.setText("No capillaries loaded."); return; }
        clearPreview();
        int window = ((Number) radius.getValue()).intValue();
        compute.setEnabled(false); capillaries.setEnabled(false); radius.setEnabled(false);
        status.setText("Computing image motion and empty-capillary noise...");
        worker = new SwingWorker<CorrelationGulpPreview, Void>() {
            @Override protected CorrelationGulpPreview doInBackground() {
                return CorrelationGulpPreview.compute(experiment, cap, window);
            }
            @Override protected void done() {
                compute.setEnabled(true); capillaries.setEnabled(true); radius.setEnabled(true);
                try {
                    if (isCancelled()) return;
                    preview = get(); computedCapillary = cap; selectedFrame = 0;
                    BufferedImage initialStrip = aligned(preview.image, preview.raw, 0);
                    double offset = displayOffset(initialStrip);
                    rawStrip = aligned(preview.image, preview.raw, offset);
                    refinedStrip = aligned(preview.image, preview.motion.refined, offset);
                    Double saved = manualThresholds.get(cap);
                    double initial = saved != null ? saved : preview.noise.automatic;
                    threshold.setEnabled(true);
                    automatic.setEnabled(Double.isFinite(preview.noise.automatic));
                    export.setEnabled(true);
                    setThreshold(Double.isFinite(initial) ? initial : 1, saved != null || !Double.isFinite(initial));
                } catch (CancellationException ignored) {
                    status.setText("Computation cancelled.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); status.setText("Computation interrupted.");
                } catch (ExecutionException | RuntimeException e) {
                    clearPreview();
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    status.setText(cause.getMessage());
                    JOptionPane.showMessageDialog(CorrelationGulpPanel.this, cause.getMessage(), "Correlation preview", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private double value() { return ((Number) threshold.getValue()).doubleValue(); }

    private void setThreshold(double v, boolean user) {
        settingThreshold = true;
        threshold.setValue(Math.max(0, Math.min(10000, v)));
        settingThreshold = false;
        manual = user;
        if (!user) manualThresholds.remove(computedCapillary);
        refresh();
    }

    private boolean[] candidates() {
        return VerticalCorrelation.candidates(preview.residualRate, preview.motion.reliable, value());
    }

    private void refresh() {
        if (preview != null) {
            int count = 0, good = 0;
            for (boolean v : candidates()) if (v) count++;
            for (boolean v : preview.motion.reliable) if (v) good++;
            String auto = Double.isFinite(preview.noise.automatic) ? format(preview.noise.automatic) : "unavailable";
            source.setText("<html>" + preview.referenceDescription + "<br>Initial threshold: " + auto
                    + " px/min | " + preview.noise.source + " | quiet intervals: " + preview.noise.quietSamples + "</html>");
            status.setText(count + " candidate intervals | " + good + "/" + (preview.raw.length - 1)
                    + " reliable correlations | threshold " + format(value()) + " px/min (" + (manual ? "manual" : "automatic")
                    + ") | time " + format(preview.minutes[selectedFrame]) + " min");
        }
        plot.repaint(); alignment.repaint();
    }

    private void export() {
        if (preview == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("correlation-" + computedCapillary.getLast2ofCapillaryName() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file.getFileName() + "?", "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { writeCsv(file); }
        catch (IOException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE); }
    }

    private void writeCsv(Path file) throws IOException {
        boolean[] selected = candidates();
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# prototype=vertical_correlation;capillary=" + computedCapillary.getLast2ofCapillaryName() + ";window_radius=" + radius.getValue() + "\n");
            writer.write("# experiment=" + experiment.getResultsDirectory() + ";reference=" + preview.referenceDescription + "\n");
            writer.write("# threshold_source=" + preview.noise.source + ";manual=" + manual + ";automatic_threshold_px_per_min=" + preview.noise.automatic + "\n");
            writer.write("frame;elapsed_min;raw_px;refined_px;shift_px;evaporation_increment_px;excess_px_per_min;correlation;reliable;reason;automatic_threshold_px_per_min;chosen_threshold_px_per_min;candidate;candidate_amplitude_px\n");
            for (int i = 0; i < preview.raw.length; i++) {
                double evap = i == 0 ? Double.NaN : preview.evaporation[i] - preview.evaporation[i - 1];
                writer.write(i + ";" + preview.minutes[i] + ";" + preview.raw[i] + ";" + preview.motion.refined[i] + ";"
                        + preview.motion.shift[i] + ";" + evap + ";" + preview.residualRate[i] + ";" + preview.motion.correlation[i]
                        + ";" + preview.motion.reliable[i] + ";" + preview.motion.reason[i] + ";" + preview.noise.automatic
                        + ";" + value() + ";" + selected[i] + ";" + (selected[i] ? Math.max(0, preview.motion.shift[i]) : 0) + "\n");
            }
        }
    }

    private static String format(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private final class MotionPlot extends JPanel {
        private static final long serialVersionUID = 1L;
        private double low, high;
        private boolean dragging;
        MotionPlot() {
            setBackground(Color.WHITE); setPreferredSize(new Dimension(1050, 300));
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (preview == null) return;
                    dragging = Math.abs(e.getY() - y(value())) < 12;
                    if (!dragging) {
                        double time = preview.minutes[0] + (e.getX() - 65.) / Math.max(1, getWidth() - 90) * duration();
                        selectedFrame = closest(time); refresh();
                    }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (preview != null && dragging) threshold.setValue(Math.max(0, Math.min(10000, high - (e.getY() - 35.) / Math.max(1, getHeight() - 85) * (high - low))));
                }
                @Override public void mouseReleased(MouseEvent e) { dragging = false; repaint(); }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }
        private double duration() { return Math.max(1e-6, preview.minutes[preview.minutes.length - 1] - preview.minutes[0]); }
        private int x(int i) { return 65 + (int) ((getWidth() - 90) * (preview.minutes[i] - preview.minutes[0]) / duration()); }
        private int y(double v) { return 35 + (int) ((getHeight() - 85) * (high - v) / (high - low)); }
        private int closest(double time) {
            int best = 0;
            for (int i = 1; i < preview.minutes.length; i++) if (Math.abs(preview.minutes[i] - time) < Math.abs(preview.minutes[best] - time)) best = i;
            return best;
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.DARK_GRAY); g.drawString("Downward motion minus evaporation (pixels/min); red points = candidate intervals", 65, 20);
            if (preview == null) { g.dispose(); return; }
            if (!dragging) {
                low = -.5; high = Math.max(1, value() * 1.2);
                for (double v : preview.residualRate) if (Double.isFinite(v)) { low = Math.min(low, v - .1); high = Math.max(high, v + .2); }
            }
            for (int tick = 0; tick <= 4; tick++) {
                double v = low + (high - low) * tick / 4;
                int yy = y(v); g.setColor(new Color(225, 225, 225)); g.drawLine(65, yy, getWidth() - 25, yy);
                g.setColor(Color.DARK_GRAY); g.drawString(format(v), 12, yy + 4);
            }
            boolean[] chosen = candidates();
            for (int i = 1; i < preview.raw.length; i++) {
                if (!preview.motion.reliable[i] || !Double.isFinite(preview.residualRate[i])) {
                    g.setColor(Color.GRAY); g.drawString("x", x(i) - 3, y(0)); continue;
                }
                g.setColor(new Color(35, 100, 165));
                if (i > 1 && preview.motion.reliable[i - 1] && Double.isFinite(preview.residualRate[i - 1]))
                    g.drawLine(x(i - 1), y(preview.residualRate[i - 1]), x(i), y(preview.residualRate[i]));
                g.setColor(chosen[i] ? Color.RED : new Color(35, 100, 165));
                g.fillOval(x(i) - 3, y(preview.residualRate[i]) - 3, 6, 6);
            }
            g.setColor(new Color(180, 30, 30)); g.setStroke(new BasicStroke(2));
            g.drawLine(65, y(value()), getWidth() - 25, y(value()));
            g.drawString("Drag threshold: " + format(value()), 75, Math.max(35, y(value()) - 6));
            g.setColor(Color.GRAY); g.setStroke(new BasicStroke(1)); g.drawLine(x(selectedFrame), 35, x(selectedFrame), getHeight() - 50);
            for (int tick = 0; tick <= 5; tick++) {
                double t = preview.minutes[0] + duration() * tick / 5;
                g.setColor(Color.DARK_GRAY); g.drawString(format(t), Math.min(getWidth() - 60, 65 + (getWidth() - 90) * tick / 5 - 8), getHeight() - 28);
            }
            g.drawString("Elapsed time (minutes)", getWidth() / 2 - 65, getHeight() - 7);
            g.dispose();
        }
    }

    private final class AlignmentView extends JPanel {
        private static final long serialVersionUID = 1L;
        AlignmentView() { setBackground(Color.WHITE); setPreferredSize(new Dimension(1050, 420)); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (preview == null) return;
            int left = 65, width = Math.max(1, getWidth() - 90), panelHeight = Math.max(35, (getHeight() - 110) / 3);
            int min = preview.image.getHeight() - 1, max = 0;
            for (double v : preview.raw) if (Double.isFinite(v)) { min = Math.min(min, (int) v - 8); max = Math.max(max, (int) v + 9); }
            min = Math.max(0, min); max = Math.min(preview.image.getHeight(), max);
            String[] labels = {"Original kymograph crop", "Aligned using saved topraw (+/-10 pixels)", "Aligned using refined levels (+/-10 pixels); red ticks = candidate intervals"};
            boolean[] chosen = candidates();
            for (int p = 0; p < 3; p++) {
                int top = 25 + p * (panelHeight + 25);
                g.setColor(Color.DARK_GRAY); g.drawString(labels[p], left, top - 7);
                if (p == 0) g.drawImage(preview.image, left, top, left + width, top + panelHeight, 0, min, preview.image.getWidth(), max, null);
                else {
                    g.drawImage(p == 1 ? rawStrip : refinedStrip, left, top, width, panelHeight, null);
                    for (int x = left; x < left + width; x += 40) { g.setColor(Color.WHITE); g.drawLine(x, top + panelHeight / 2, x + 8, top + panelHeight / 2); }
                }
                for (int i = 0; i < chosen.length; i++) if (chosen[i]) {
                    int x = left + (int) (width * (i + .5) / chosen.length);
                    g.setColor(Color.RED); g.drawLine(x, top, x, top + 7);
                }
                int x = left + (int) (width * (selectedFrame + .5) / chosen.length);
                g.setColor(Color.YELLOW); g.drawLine(x, top, x, top + panelHeight);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawString("Horizontal axis: native image columns (time). Grey columns: missing level or image border. Evaporation retained in images.", left, getHeight() - 7);
        }
    }

    private static double displayOffset(BufferedImage strip) {
        double best = Double.NEGATIVE_INFINITY, offset = 0;
        for (int j = 24; j < 56; j++) {
            double gradient = 0;
            for (int t = 0; t < strip.getWidth(); t++) {
                int a = strip.getRGB(t, j), b = strip.getRGB(t, j + 1);
                gradient += (((b >> 8) & 255) - ((b >> 16) & 255)) - (((a >> 8) & 255) - ((a >> 16) & 255));
            }
            if (gradient > best) { best = gradient; offset = (j + .5 - 40) / 4.; }
        }
        return offset;
    }

    private static BufferedImage aligned(BufferedImage image, double[] levels, double offset) {
        BufferedImage out = new BufferedImage(image.getWidth(), 81, BufferedImage.TYPE_INT_RGB);
        for (int t = 0; t < levels.length; t++) for (int j = 0; j < 81; j++) {
            double row = levels[t] + offset + (j - 40) / 4.;
            if (!Double.isFinite(row) || row < 0 || row > image.getHeight() - 1) { out.setRGB(t, j, 0xdddddd); continue; }
            int lo = (int) Math.floor(row), hi = Math.min(image.getHeight() - 1, lo + 1);
            double f = row - lo; int rgb = 0;
            for (int c = 2; c >= 0; c--) {
                int a = (image.getRGB(t, lo) >> (8 * c)) & 255, b = (image.getRGB(t, hi) >> (8 * c)) & 255;
                rgb = (rgb << 8) | (int) Math.round(a * (1 - f) + b * f);
            }
            out.setRGB(t, j, rgb);
        }
        return out;
    }
}
