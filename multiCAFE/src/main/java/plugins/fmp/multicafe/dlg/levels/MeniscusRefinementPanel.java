package plugins.fmp.multicafe.dlg.levels;

import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.MeniscusRefinementPreview;
import plugins.fmp.multitools.tools.JComponents.RichTooltipLayoutGuard;
import plugins.fmp.multitools.tools.chart.ChartCagesFrame;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.results.EnumResults;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.tools.polyline.Level2D;

/** Independent phase-1 preview. Only Accept/Undo can change topraw. */
public final class MeniscusRefinementPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private final Experiment experiment;
	private final Runnable changed;
	private final JComboBox<Capillary> capillaries = new JComboBox<>();
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	private final JSpinner radius = new JSpinner(new SpinnerNumberModel(10, 3, 60, 1));
	private final JSpinner correction = new JSpinner(new SpinnerNumberModel(3., 1., 32., 1.));
	private final JCheckBox showOriginal = new JCheckBox("Original (orange)", true),
			showProposed = new JCheckBox("Proposed (magenta)", true);
	private final JCheckBox expand = new JCheckBox("Expand correction search", true);
	private final JButton accept = new JButton("Validate"),
			undo = new JButton("Undo validation");
	private final JLabel status = new JLabel("Preparing refinement preview...");
	private enum ValidationScope {
		CAPILLARY("Current capillary"), CAGE("Current cage"), EXPERIMENT("All cages of this experiment");
		private final String label;
		ValidationScope(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
	private final JComboBox<ValidationScope> validationScope = new JComboBox<>(ValidationScope.values());
	private MeniscusRefinement.Validation lastValidation;
	private final List<Capillary> computingCaps = new ArrayList<>();
	private final Map<Capillary, MeniscusRefinement.Proposal> proposals = new LinkedHashMap<>();
	private final Map<Capillary, BufferedImage> aligned = new LinkedHashMap<>();
	private final List<ROI> overlays = new ArrayList<>();
	private Sequence overlaySequence;
	private final RichTooltipLayoutGuard tooltipGuard = new RichTooltipLayoutGuard();
	private boolean closed;
	private JDialog comparisonDialog;
	private final JPanel comparisonView = new JPanel(new BorderLayout());
	private final javax.swing.Timer previewTimer = new javax.swing.Timer(200, e -> compute());
	private final javax.swing.Timer sourceTimer = new javax.swing.Timer(750, e -> {
		buttons();
		if (proposals.isEmpty() && aligned.keySet().stream().anyMatch(cap -> !MeniscusRefinement.canUndo(cap))) {
			requestPreview();
			return;
		}
		for (Map.Entry<Capillary, MeniscusRefinement.Proposal> entry : proposals.entrySet())
			if (!MeniscusRefinement.isCurrent(entry.getKey(), entry.getValue())) {
				requestPreview();
				break;
			}
	});
	private SwingWorker<MeniscusRefinement.Proposal, Void> worker;
	private MeniscusRefinement.Proposal proposal;
	private BufferedImage image;

	private final JPanel plot = new JPanel() {
		private static final long serialVersionUID = 1L;
		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			List<Capillary> visible = visibleCaps();
			visible.removeIf(cap -> !aligned.containsKey(cap));
			int count = visible.size();
			if (count == 0) {
				g.drawString("Preparing stabilized images...", 12, 24);
			} else {
				int rowHeight = getHeight() / count, row = 0;
				for (Capillary cap : visible) {
					int top = row++ * rowHeight;
					g.setColor(Color.DARK_GRAY);
					g.drawString(cap.getLast2ofCapillaryName()
							+ " - aligned to proposed meniscus (+/- 15 px); red ticks: uncertain registration", 10, top + 16);
					int y1 = top + 24, y2 = top + rowHeight - 8;
					g.drawImage(aligned.get(cap), 10, y1, getWidth() - 20, y2 - y1, null);
					g.setColor(Color.WHITE);
					g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 6}, 0));
					g.drawLine(10, (y1 + y2) / 2, getWidth() - 10, (y1 + y2) / 2);
					g.setStroke(new BasicStroke(1));
					MeniscusRefinement.Proposal p = proposals.get(cap);
					boolean[] quality = p == null ? cap.getTopRaw().getRefinementQuality() : p.motion.reliable;
					if (quality != null) {
						g.setColor(Color.RED);
						for (int i = 1; i < quality.length; i++)
							if (!quality[i]) {
								int x = 10 + (int) ((i + .5) * (getWidth() - 20.) / quality.length);
								g.drawLine(x, y1, x, y1 + 9);
							}
					}
				}
			}
			g.dispose();
		}
	};

	public static void open(Component owner, Experiment experiment, Capillary selected, Runnable changed) {
		if (experiment == null)
			return;
		MeniscusRefinementPanel panel = new MeniscusRefinementPanel(experiment, selected, changed);
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Phase 1 - refine meniscus from topraw",
				Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(panel);
		dialog.setSize(1100, 700);
		dialog.setLocationRelativeTo(owner);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				panel.close();
			}
		});
		dialog.setVisible(true);
	}

	public MeniscusRefinementPanel(Experiment experiment, Capillary selected, Runnable changed) {
		super(new BorderLayout(5, 5));
		this.experiment = experiment;
		this.changed = changed;
		validationScope.setSelectedItem(ValidationScope.CAGE);
		for (Capillary cap : experiment.getCapillaries().getList())
			capillaries.addItem(cap);
		capillaries.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus) {
				super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof Capillary) {
					Capillary cap = (Capillary) value;
					setText(cap.getLast2ofCapillaryName() + (cap.getNFlies() == 0 ? " (no-fly control)" : ""));
				}
				return this;
			}
		});
		if (selected != null)
			capillaries.setSelectedItem(selected);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		previousButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
		nextButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
		controls.add(previousButton);
		controls.add(capillaries);
		controls.add(nextButton);

		controls.add(new JLabel("Window +/- px"));
		controls.add(radius);
		controls.add(new JLabel("Correction around topraw shift +/- px"));
		controls.add(correction);
		controls.add(expand);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(new JLabel("Apply to"));
		actions.add(validationScope);
		actions.add(accept);
		actions.add(undo);
		actions.add(showOriginal);
		actions.add(showProposed);
		JButton graphs = new JButton("Cage graphs...");
		actions.add(graphs);
		graphs.addActionListener(e -> showGraphs());
		showOriginal.addActionListener(e -> updateOverlays());
		showProposed.addActionListener(e -> updateOverlays());
		JPanel header = new JPanel(new GridLayout(0, 1));
		header.add(controls);
		header.add(actions);
		header.add(new JLabel(
				"Kymograph: orange original / magenta proposed. Graphs: blue/red original L/R, magenta/orange proposed L/R. No evaporation subtraction."));
		add(header, BorderLayout.NORTH);
		add(plot, BorderLayout.CENTER);
		add(status, BorderLayout.SOUTH);
		accept.addActionListener(e -> apply(false));
		undo.addActionListener(e -> apply(true));
		capillaries.addActionListener(e -> {
			List<Capillary> targets = scope();
			boolean ready = !targets.isEmpty() && targets.stream().allMatch(c -> MeniscusRefinement.isCurrent(c, proposals.get(c)));
			boolean coveredByWorker = worker != null && computingCaps.containsAll(targets);
			if (!ready && !coveredByWorker) requestPreview();
			proposal = proposals.get(cap());
			buttons();
			if (ready) readyStatus();
			plot.repaint();
			refreshComparison();
			Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
			if (seq != null && seq.getFirstViewer() != null && cap() != null)
				seq.getFirstViewer().setPositionT(cap().getKymographIndex());
		});
		previousButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() - 1));
		nextButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() + 1));
		ChangeListener reset = e -> requestPreview();
		radius.addChangeListener(reset);
		correction.addChangeListener(reset);
		expand.addActionListener(e -> requestPreview());
		validationScope.addActionListener(e -> requestPreview());
		previewTimer.setRepeats(false);
		buttons();
		sourceTimer.start();
		requestPreview();
	}

	private Capillary cap() {
		return (Capillary) capillaries.getSelectedItem();
	}

	private void cancel() {
		if (worker != null)
			worker.cancel(true);
		worker = null;
		computingCaps.clear();
	}

	private List<Capillary> scope() {
		if (validationScope.getSelectedItem() == ValidationScope.EXPERIMENT)
			return new ArrayList<>(experiment.getCapillaries().getList());
		return visibleCaps();
	}

	/** Keep inspection readable even while preparing all cages for validation. */
	private List<Capillary> visibleCaps() {
		List<Capillary> caps = new ArrayList<>();
		Capillary selected = cap();
		if (selected != null) {
			if (validationScope.getSelectedItem() != ValidationScope.CAPILLARY && selected.getCageID() >= 0) {
				for (Capillary cap : experiment.getCapillaries().getList())
					if (cap.getCageID() == selected.getCageID()) caps.add(cap);
			} else caps.add(selected);
		}
		return caps;
	}

	private void buttons() {
		List<Capillary> caps = scope();
		boolean busy = worker != null || previewTimer.isRunning();
		accept.setEnabled(!closed && !busy && !caps.isEmpty()
				&& caps.stream().allMatch(cap -> MeniscusRefinement.isCurrent(cap, proposals.get(cap))));
		undo.setEnabled(!closed && !busy && MeniscusRefinement.canUndo(lastValidation));
		undo.setToolTipText(lastValidation == null ? "Undo the last validation"
				: "Restore the " + lastValidation.size() + " capillaries changed by the last validation");
		previousButton.setEnabled(capillaries.getSelectedIndex() > 0);
		nextButton.setEnabled(capillaries.getSelectedIndex() + 1 < capillaries.getItemCount());
	}

	private void readyStatus() {
		StringBuilder summary = new StringBuilder(proposals.size() + " capillaries ready (" + validationScope.getSelectedItem() + "). ");
		for (Capillary cap : visibleCaps()) {
			MeniscusRefinement.Proposal p = proposals.get(cap);
			if (p == null) continue;
			int good = 0;
			for (int i = 1; i < p.motion.reliable.length; i++) if (p.motion.reliable[i]) good++;
			summary.append(cap.getLast2ofCapillaryName()).append(": ").append(good).append("/")
					.append(p.motion.reliable.length - 1).append(" reliable; ");
		}
		status.setText(summary + "Preview only; Validate accepts the selected scope into topraw.");
	}

	private void reset() {
		previewTimer.stop();
		cancel();
		proposal = null;
		proposals.clear();
		aligned.clear();
		image = null;
		clearOverlays();
		MeniscusRefinementPreview.clear(this);
		ChartCagesFrame.refreshRefinement(experiment);
		refreshComparison();
		buttons();
		plot.repaint();
		status.setText("Preparing refinement preview...");
	}

	private void requestPreview() {
		if (closed) return;
		reset();
		previewTimer.restart();
		buttons();
	}

	public void close() {
		if (closed) return;
		closed = true;
		sourceTimer.stop();
		reset();
		tooltipGuard.close();
		if (comparisonDialog != null) comparisonDialog.dispose();
	}

	private void compute() {
		Capillary selected = cap();
		if (closed || selected == null) return;
		reset();
		try {
			if (experiment.getSeqKymos() == null || experiment.getSeqKymos().getSequence() == null)
				throw new IllegalStateException("Open the kymographs first.");
			final Map<Capillary, Level2D> inputs = new LinkedHashMap<>();
			final Map<Capillary, BufferedImage> images = new LinkedHashMap<>();
			for (Capillary cap : scope()) {
				Level2D source = cap.getTopRaw().polylineLevel;
				if (source == null || source.npoints < 2)
					throw new IllegalStateException("Detect topraw first for " + cap.getLast2ofCapillaryName());
				inputs.put(cap, new Level2D(Arrays.copyOf(source.xpoints, source.npoints),
						Arrays.copyOf(source.ypoints, source.npoints), source.npoints));
				images.put(cap, MeniscusRefinement.image(experiment.getSeqKymos().getSequence(), cap, source.npoints));
			}
			computingCaps.addAll(inputs.keySet());
			final int window = ((Number) radius.getValue()).intValue();
			final double range = ((Number) correction.getValue()).doubleValue();
			final boolean adaptive = expand.isSelected();
			image = images.get(selected);
			status.setText("Refining " + inputs.size() + " capillaries...");
			worker = new SwingWorker<MeniscusRefinement.Proposal, Void>() {
				private final Map<Capillary, MeniscusRefinement.Proposal> results = new LinkedHashMap<>();
				private final Map<Capillary, BufferedImage> strips = new LinkedHashMap<>();
				@Override
				protected MeniscusRefinement.Proposal doInBackground() {
					for (Capillary cap : inputs.keySet()) {
						if (isCancelled()) return null;
						MeniscusRefinement.Proposal p = MeniscusRefinement.propose(inputs.get(cap), images.get(cap), window, range, adaptive);
						results.put(cap, p);
						strips.put(cap, MeniscusRefinement.stabilized(images.get(cap), p.refined(), 15));
					}
					return results.get(selected);
				}
				@Override
				protected void done() {
					if (worker != this || closed) return;
					worker = null;
					computingCaps.clear();
					try {
						get();
						for (Capillary cap : results.keySet())
							if (!MeniscusRefinement.isCurrent(cap, results.get(cap))) {
								requestPreview();
								return;
							}
						proposals.putAll(results);
						aligned.putAll(strips);
						proposal = proposals.get(cap());
						MeniscusRefinementPreview.publish(MeniscusRefinementPanel.this, proposals);
						updateOverlays();
						ChartCagesFrame.refreshRefinement(experiment);
						readyStatus();
					} catch (Exception error) {
						status.setText(message(error));
					}
					buttons();
					plot.repaint();
					refreshComparison();
				}
			};
			worker.execute();
			buttons();
		} catch (RuntimeException error) {
			status.setText(message(error));
		}
	}

	private void clearOverlays() {
		if (overlaySequence != null && !overlays.isEmpty()) overlaySequence.removeROIs(overlays, false);
		overlays.clear();
		overlaySequence = null;
	}

	private void updateOverlays() {
		clearOverlays();
		if (experiment.getSeqKymos() == null) return;
		overlaySequence = experiment.getSeqKymos().getSequence();
		if (overlaySequence == null) return;
		for (Map.Entry<Capillary, MeniscusRefinement.Proposal> entry : proposals.entrySet()) {
			Capillary cap = entry.getKey();
			MeniscusRefinement.Proposal p = entry.getValue();
			if (!MeniscusRefinement.isCurrent(cap, p)) continue;
			if (showOriginal.isSelected()) addOverlay(cap, p.x(), p.original(), Color.ORANGE, "original");
			if (showProposed.isSelected()) addOverlay(cap, p.x(), p.refined(), Color.MAGENTA, "proposed");
		}
		overlaySequence.addROIs(overlays, false);
	}

	private void addOverlay(Capillary cap, double[] x, double[] y, Color color, String label) {
		ROI2DPolyLine roi = new ROI2DPolyLine(new Level2D(x, y, y.length));
		roi.setName(cap.getKymographPrefix() + "_xcorr_refinement_" + label);
		roi.setT(cap.getKymographIndex());
		roi.setColor(color);
		roi.setStroke(1.5);
		roi.setReadOnly(true);
		CapillaryCorrelationGulps.markOverlay(roi);
		roi.setProperty(MeniscusRefinementPreview.ROI_PROPERTY, "true");
		overlays.add(roi);
	}

	private void showGraphs() {
		if (comparisonDialog == null || !comparisonDialog.isDisplayable()) {
			comparisonDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Meniscus refinement comparison",
					Dialog.ModalityType.MODELESS);
			comparisonDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			comparisonDialog.setContentPane(comparisonView);
			comparisonDialog.setSize(1000, 760);
			comparisonDialog.setLocationRelativeTo(this);
		}
		refreshComparison();
		comparisonDialog.setVisible(true);
		comparisonDialog.toFront();
	}

	private void refreshComparison() {
		if (comparisonDialog == null || !comparisonDialog.isDisplayable()) return;
		comparisonDialog.setTitle("Meniscus comparison - cage " + (cap() == null ? "" : cap().getCageID()));
		comparisonView.removeAll();
		comparisonView.add(comparisonContent(), BorderLayout.CENTER);
		comparisonView.revalidate();
		comparisonView.repaint();
	}

	/** The lower plot exposes subpixel corrections hidden by the full volume scale. */
	private JPanel comparisonContent() {
		JPanel content = new JPanel(new BorderLayout(5, 5));
		if (proposals.isEmpty()) {
			content.add(new JLabel(aligned.isEmpty() ? status.getText()
					: "Refinement accepted into topraw. Comparison previews have been cleared."), BorderLayout.NORTH);
			return content;
		}
		Cage cage = new Cage();
		cage.getProperties().setCageID(cap().getCageID());
		for (Capillary cap : visibleCaps()) cage.addCapillaryIfUnique(cap);
		ResultsOptions options = new ResultsOptions();
		options.resultType = EnumResults.TOPRAW;
		options.buildExcelStepMs = (int) experiment.getKymoBin_ms();
		XYSeriesCollection levels = new CageCapillarySeriesBuilder().build(experiment, cage, options);
		for (int i = levels.getSeriesCount() - 1; i >= 0; i--) {
			String key = levels.getSeriesKey(i).toString();
			if (key.endsWith("_evaporation")) levels.removeSeries(i);
			else if (key.endsWith("proposed (preview)"))
				levels.getSeries(i).setKey(key.replace("proposed (preview)", "proposed"));
			else for (Capillary cap : visibleCaps())
				if (key.equals(plugins.fmp.multitools.tools.chart.builders.CapillaryChartSeriesKeys.key(cap)))
					levels.getSeries(i).setKey(cap.getLast2ofCapillaryName() + " original");
		}
		JFreeChart levelChart = ChartFactory.createXYLineChart("Cage " + cage.getCageID()
				+ " - original and proposed levels", "Elapsed time (minutes)", "Volume (ul)", levels);
		XYLineAndShapeRenderer levelRenderer = (XYLineAndShapeRenderer) levelChart.getXYPlot().getRenderer();
		SeriesStyleCodec.applySeriesPaintsFromDescription(levels, levelRenderer);
		for (int i = 0; i < levels.getSeriesCount(); i++) {
			boolean proposed = levels.getSeriesKey(i).toString().endsWith(" proposed");
			levelRenderer.setSeriesStroke(i, proposed
					? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0)
					: new BasicStroke(1f));
		}
		XYSeriesCollection corrections = new XYSeriesCollection();
		double maximum = 0;
		StringBuilder summary = new StringBuilder("Maximum absolute correction: ");
		List<Color> colors = new ArrayList<>();
		for (Capillary cap : visibleCaps()) {
			MeniscusRefinement.Proposal p = proposals.get(cap);
			if (p == null || !MeniscusRefinement.isCurrent(cap, p)) continue;
			double[] original = p.original(), refined = p.refined();
			XYSeries series = new XYSeries(cap.getLast2ofCapillaryName() + " correction", false);
			double capMaximum = 0;
			for (int i = 0; i < original.length; i++) {
				double correction = refined[i] - original[i];
				series.add(CageCapillarySeriesBuilder.displayTimeMinutes(experiment, i, experiment.getKymoBin_ms()), correction);
				if (Double.isFinite(correction)) capMaximum = Math.max(capMaximum, Math.abs(correction));
			}
			maximum = Math.max(maximum, capMaximum);
			summary.append(cap.getLast2ofCapillaryName()).append(" ")
					.append(String.format(java.util.Locale.ROOT, "%.2f px; ", capMaximum));
			corrections.addSeries(series);
			String side = cap.getCapillarySide();
			colors.add("L".equals(side) || "1".equals(side) ? Color.MAGENTA : new Color(210, 120, 0));
		}
		JFreeChart deltaChart = ChartFactory.createXYLineChart("Correction = proposed minus original (pixels)",
				"Elapsed time (minutes)", "Correction (pixels)", corrections);
		XYLineAndShapeRenderer deltaRenderer = (XYLineAndShapeRenderer) deltaChart.getXYPlot().getRenderer();
		for (int i = 0; i < colors.size(); i++) {
			deltaRenderer.setSeriesPaint(i, colors.get(i));
			deltaRenderer.setSeriesStroke(i, new BasicStroke(1.5f));
		}
		double limit = Math.max(.5, maximum * 1.15);
		deltaChart.getXYPlot().getRangeAxis().setRange(-limit, limit);
		deltaChart.getXYPlot().addRangeMarker(new ValueMarker(0, Color.GRAY, new BasicStroke(1f)));
		JPanel charts = new JPanel(new GridLayout(2, 1, 5, 5));
		charts.add(new ChartPanel(levelChart));
		charts.add(new ChartPanel(deltaChart));
		content.add(new JLabel(summary.toString()), BorderLayout.NORTH);
		content.add(charts, BorderLayout.CENTER);
		content.add(new JLabel("Positive correction: proposed meniscus is lower in the image. Drag a region to zoom; right-click for zoom options."), BorderLayout.SOUTH);
		return content;
	}

	private void apply(boolean undoing) {
		try {
			int count;
			Map<Capillary, BufferedImage> acceptedImages = new LinkedHashMap<>(aligned);
			if (undoing) {
				count = lastValidation == null ? 0 : lastValidation.size();
				MeniscusRefinement.undo(lastValidation);
				lastValidation = null;
			} else {
				Map<Capillary, MeniscusRefinement.Proposal> batch = new LinkedHashMap<>();
				for (Capillary cap : scope()) batch.put(cap, proposals.get(cap));
				lastValidation = MeniscusRefinement.validateAll(batch);
				count = lastValidation.size();
			}
			reset();
			if (!undoing) aligned.putAll(acceptedImages);
			if (cap() != null)
				experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(cap().getKymographIndex(), experiment.getCapillaries());
			status.setText((undoing ? "Restored previous topraw for " : "Validated refined topraw for ") + count
					+ " capillaries. Use Levels Save to persist. Recompute intake.");
			if (changed != null) changed.run();
			refreshComparison();
			if (undoing) requestPreview();
		} catch (RuntimeException error) {
			status.setText(message(error));
			buttons();
		}
	}

	private static String message(Throwable error) {
		while (error.getCause() != null)
			error = error.getCause();
		return error.getMessage() == null ? error.toString() : error.getMessage();
	}
}
