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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.experiment.capillary.IntakeEvaporationOverlay;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.VerticalCorrelation;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Drag the threshold, inspect aligned original pixels, see candidates on the
 * kymograph, store them as a measure.
 */
public final class CorrelationIntakePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String ROI_NAME_PART = "_" + CapillaryCorrelationGulps.ROI_TOKEN;
	private final Experiment experiment;
	private final JComboBox<Capillary> capillaries = new JComboBox<>();
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	private final JSpinner radius = new JSpinner(new SpinnerNumberModel(10, 3, 60, 1));
	private final JSpinner maxShift = new JSpinner(
			new SpinnerNumberModel(CapillaryCorrelationGulps.DEFAULT_MAX_SHIFT_PX, 1.0, 60.0, 1.0));
	private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 10000.0, .05));
	private final JButton compute = new JButton("Recompute"), automatic = new JButton("Automatic"),
			export = new JButton("Export CSV...");
	private final JButton save = new JButton("Save"), copyToGulps = new JButton("Copy to gulps...");
	private final JCheckBox showRois = new JCheckBox("Show on kymograph", true);
	private final JCheckBox compensateEvaporation = new JCheckBox("Subtract evaporation from intake steps", true);
	private final JLabel source = new JLabel(" "),
			status = new JLabel("Select a capillary to analyze its correlation motion.");
	private final MotionPlot plot = new MotionPlot();
	private final AlignmentView alignment = new AlignmentView();
	private final Map<Capillary, Double> manualThresholds = new IdentityHashMap<>();
	private final Map<Capillary, CorrelationGulpPreview> cache = new IdentityHashMap<>();
	private final Timer roiTimer = new Timer(120, e -> updateRois());
	private Consumer<Capillary> onCapillaryChosen;
	private SwingWorker<CorrelationGulpPreview, Void> worker;
	private Capillary pendingCapillary;
	private CorrelationGulpPreview preview;
	private Capillary computedCapillary, roiCapillary;
	private boolean[] roiCandidates;
	private boolean roiStored, released;
	private boolean roiCompensated;
	private CorrelationGulpPreview roiPreview;
	private BufferedImage refinedStrip;
	private int selectedFrame;
	private int revealedFrame = -1;
	private boolean settingThreshold, manual, syncingSelection;

	/**
	 * onCapillaryChosen is notified when the user picks a capillary in this panel
	 * (e.g. to move the kymograph viewer).
	 */
	public static CorrelationIntakePanel open(Component owner, Experiment exp, Capillary initial,
			Consumer<Capillary> onCapillaryChosen) {
		CorrelationIntakePanel panel = new CorrelationIntakePanel(exp, initial);
		panel.onCapillaryChosen = onCapillaryChosen;
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
				"Intake detection - vertical correlation prototype", Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(panel);
		dialog.setSize(1180, 880);
		dialog.setMinimumSize(new Dimension(920, 700));
		dialog.setLocationRelativeTo(owner);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				panel.release();
			}
		});
		dialog.setVisible(true);
		panel.analyzeSelected();
		return panel;
	}

	public CorrelationIntakePanel(Experiment exp, Capillary initial) {
		super(new BorderLayout(6, 6));
		experiment = exp;
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		for (Capillary cap : exp.getCapillaries().getList())
			capillaries.addItem(cap);
		if (initial != null)
			capillaries.setSelectedItem(initial);
		Capillary first = (Capillary) capillaries.getSelectedItem();
		if (first != null && first.getCorrelationGulps().hasMeasures()) {
			setIfInRange(radius, first.getCorrelationGulps().getWindowRadius());
			setIfInRange(maxShift, first.getCorrelationGulps().getMaxShiftPx());
		}
		capillaries.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus) {
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
		controls.add(new JLabel("Capillary"));
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		controls.add(previousButton);
		controls.add(capillaries);
		controls.add(nextButton);

		controls.add(new JLabel("Window +/- pixels"));
		controls.add(radius);
		controls.add(new JLabel("Max shift +/- pixels"));
		controls.add(maxShift);
		controls.add(compute);
		controls.add(compensateEvaporation);
		JPanel thresholdControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		thresholdControls.add(new JLabel("Threshold (pixels/min)"));
		thresholdControls.add(threshold);
		thresholdControls.add(automatic);
		thresholdControls.add(showRois);
		thresholdControls.add(save);
		thresholdControls.add(copyToGulps);
		thresholdControls.add(export);
		JPanel rows = new JPanel(new GridLayout(0, 1));
		rows.add(controls);
		rows.add(thresholdControls);
		header.add(rows, BorderLayout.NORTH);
		header.add(source, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);
		JScrollPane alignmentScroll = new JScrollPane(alignment);
		alignmentScroll.setBorder(null);
		alignmentScroll.getViewport().setBackground(Color.WHITE);
		alignmentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		alignmentScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		alignmentScroll.getViewport().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				alignment.revalidate();
			}
		});
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, plot, alignmentScroll);
		split.setResizeWeight(.43);
		split.setDividerLocation(315);
		add(split, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(4, 4));
		footer.add(status, BorderLayout.NORTH);
		footer.add(new JLabel(
				"<html>Blue bars show total displacement; grey = evaporation + cumulative intake. Checkbox subtracts evaporation from grey intake steps only.<br>"
						+ "Drag the red bar to adjust the threshold; click the graph to inspect a time point. "
						+ "Save stores them as correlation gulps, kept apart from the gulps. Copy to gulps replaces the gulps of this capillary.</html>"),
				BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		setResultControlsEnabled(false);
		roiTimer.setRepeats(false);

		defineToolTips();
		defineActionListeners();
		defineChangeListeners();
	}

	private void defineActionListeners() {
		save.addActionListener(e -> store());
		copyToGulps.addActionListener(e -> copyToGulps());
		compute.addActionListener(e -> {
			Capillary cap = (Capillary) capillaries.getSelectedItem();
			if (cap != null) {
				cache.remove(cap);
				startCompute(cap, true);
			}
		});
		capillaries.addActionListener(e -> {
			if (syncingSelection)
				return;
			Capillary cap = (Capillary) capillaries.getSelectedItem();
			if (onCapillaryChosen != null && cap != null)
				onCapillaryChosen.accept(cap);
			analyzeSelected();
		});
		previousButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() - 1));
		nextButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() + 1));
		automatic.addActionListener(e -> {
			if (preview != null && Double.isFinite(preview.noise.automatic))
				setThreshold(preview.noise.automatic, false);
		});
		showRois.addActionListener(e -> updateRois());
		compensateEvaporation.addActionListener(e -> {
			alignment.repaint();
			updateRois();
		});
		export.addActionListener(e -> export());
	}

	private void defineChangeListeners() {
		ChangeListener settingsChanged = e -> {
			cache.clear();
			clearPreview();
			analyzeSelected();
		};
		radius.addChangeListener(settingsChanged);
		maxShift.addChangeListener(settingsChanged);
		threshold.addChangeListener(e -> {
			if (!settingThreshold && preview != null) {
				manual = true;
				manualThresholds.put(computedCapillary, value());
			}
			refresh();
		});
	}

	private void defineToolTips() {
		compensateEvaporation.setToolTipText("Checked: grey steps = max(0, stored amplitude minus the model's evaporation in that bin). Unchecked: full stored amplitudes. Blue bars, detection and saved measures are unchanged.");
		showRois.setToolTipText("Show intake amplitudes and evaporation plus cumulative intake, starting at raw t=0; later raw levels do not reset the grey curve.");
		radius.setToolTipText(
				"Half-height of the image profile matched around the meniscus. Scale with the camera resolution.");
		maxShift.setToolTipText(
				"Largest meniscus displacement searched between two frames. Larger steps are rejected as 'Search limit'.");
		threshold.setToolTipText(
				"Threshold on downward motion minus expected evaporation, divided by interval duration.");
		automatic.setToolTipText("Quiet-period median + 3 robust noise SD, with an empty-capillary noise floor.");
		compute.setToolTipText("Discard the cached result for this capillary and analyze it again.");
		save.setToolTipText(
				"Store the candidates of this capillary as correlation gulps (saved with the capillary measures).");
		copyToGulps.setToolTipText("Save, then replace the gulps of this capillary with the correlation gulps.");
		previousButton.setToolTipText("previous capillary");
		nextButton.setToolTipText("select next capillary");
	}

	public Experiment getExperiment() {
		return experiment;
	}

	/**
	 * Follows a capillary selected elsewhere (kymograph viewer); analyzes it if not
	 * cached.
	 */
	public void showCapillary(Capillary cap) {
		if (cap == null || cap == capillaries.getSelectedItem())
			return;
		syncingSelection = true;
		try {
			capillaries.setSelectedItem(cap);
		} finally {
			syncingSelection = false;
		}
		analyzeSelected();
	}

	public void bringToFront() {
		Window window = SwingUtilities.getWindowAncestor(this);
		if (window != null)
			window.toFront();
	}

	public void close() {
		Window window = SwingUtilities.getWindowAncestor(this);
		if (window != null)
			window.dispose();
		else
			release();
	}

	private void release() {
		if (worker != null)
			worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		roiTimer.stop();
		released = true;
		preview = null;
		computedCapillary = null;
		cache.clear();
		updateRois();
	}

	private void setResultControlsEnabled(boolean enabled) {
		automatic.setEnabled(enabled && preview != null && Double.isFinite(preview.noise.automatic));
		export.setEnabled(enabled);
		threshold.setEnabled(enabled);
		save.setEnabled(enabled);
		copyToGulps.setEnabled(enabled);
	}

	private void analyzeSelected() {
		Capillary cap = (Capillary) capillaries.getSelectedItem();
		if (cap == null) {
			status.setText("No capillaries loaded.");
			return;
		}
		if (cap == computedCapillary && preview != null)
			return;
		if (cap == pendingCapillary && worker != null && !worker.isDone())
			return;
		CorrelationGulpPreview cached = cache.get(cap);
		if (cached != null && matchesSavedLevels(cached, cap)) {
			if (worker != null)
				worker.cancel(true);
			worker = null;
			pendingCapillary = null;
			display(cap, cached);
		} else
			startCompute(cap, false);
	}

	private static boolean matchesSavedLevels(CorrelationGulpPreview p, Capillary cap) {
		if (cap.getTopRaw() == null || cap.getTopRaw().polylineLevel == null)
			return false;
		Level2D level = cap.getTopRaw().polylineLevel;
		return level.npoints == p.raw.length && Arrays.equals(Arrays.copyOf(level.ypoints, level.npoints), p.raw);
	}

	private void clearPreview() {
		preview = null;
		computedCapillary = null;
		refinedStrip = null;
		revealedFrame = -1;
		setResultControlsEnabled(false);
		source.setText(" ");
		plot.repaint();
		alignment.revalidate();
		alignment.repaint();
		scheduleRoiUpdate();
	}

	private void startCompute(Capillary cap, boolean interactive) {
		if (worker != null)
			worker.cancel(true);
		clearPreview();
		int window = windowRadius();
		double shift = maxShiftValue();
		pendingCapillary = cap;
		status.setText("Computing image motion and empty-capillary noise for " + cap.getLast2ofCapillaryName() + "...");
		worker = new SwingWorker<CorrelationGulpPreview, Void>() {
			@Override
			protected CorrelationGulpPreview doInBackground() {
				return CorrelationGulpPreview.compute(experiment, cap, window, shift);
			}

			@Override
			protected void done() {
				if (worker != this)
					return;
				worker = null;
				pendingCapillary = null;
				try {
					if (isCancelled())
						return;
					CorrelationGulpPreview result = get();
					cache.put(cap, result);
					display(cap, result);
				} catch (CancellationException ignored) {
					status.setText("Computation cancelled.");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					status.setText("Computation interrupted.");
				} catch (ExecutionException | RuntimeException e) {
					clearPreview();
					Throwable cause = e.getCause() == null ? e : e.getCause();
					status.setText(cap.getLast2ofCapillaryName() + ": " + cause.getMessage());
					if (interactive)
						JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(),
								"Correlation preview", JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		worker.execute();
	}

	private void display(Capillary cap, CorrelationGulpPreview result) {
		preview = result;
		computedCapillary = cap;
		selectedFrame = 0;
		BufferedImage initialStrip = aligned(preview.image, preview.raw, 0);
		double offset = displayOffset(initialStrip);
		refinedStrip = aligned(preview.image, preview.motion.refined, offset);
		alignment.revalidate();
		Double saved = manualThresholds.get(cap);
		CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
		if (saved == null && stored.hasMeasures() && stored.isManualThreshold() && sameSettings(stored)
				&& Double.isFinite(stored.getThresholdPxPerMin()))
			saved = stored.getThresholdPxPerMin();
		double initial = saved != null ? saved : preview.noise.automatic;
		setResultControlsEnabled(true);
		setThreshold(Double.isFinite(initial) ? initial : 1, saved != null || !Double.isFinite(initial));
	}

	private int windowRadius() {
		return ((Number) radius.getValue()).intValue();
	}

	private double maxShiftValue() {
		return ((Number) maxShift.getValue()).doubleValue();
	}

	private boolean sameSettings(CapillaryCorrelationGulps stored) {
		return stored.getWindowRadius() == windowRadius() && Math.abs(stored.getMaxShiftPx() - maxShiftValue()) < 1e-9;
	}

	private static void setIfInRange(JSpinner spinner, double v) {
		SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
		if (v < ((Number) model.getMinimum()).doubleValue() || v > ((Number) model.getMaximum()).doubleValue())
			return;
		spinner.setValue(
				model.getValue() instanceof Integer ? Integer.valueOf((int) Math.round(v)) : Double.valueOf(v));
	}

	private double[] candidateAmplitudes() {
		boolean[] chosen = candidates();
		double[] amplitudes = new double[chosen.length];
		for (int i = 0; i < chosen.length; i++)
			amplitudes[i] = chosen[i] ? Math.max(0, preview.motion.shift[i]) : 0;
		return amplitudes;
	}

	private boolean isStoredCurrent() {
		if (preview == null)
			return false;
		CapillaryCorrelationGulps stored = computedCapillary.getCorrelationGulps();
		if (!stored.hasMeasures() || !sameSettings(stored))
			return false;
		Level2D heights = stored.getEvents().getHeightSeries();
		double[] current = candidateAmplitudes();
		if (heights.npoints != current.length)
			return false;
		for (int i = 0; i < current.length; i++)
			if (Math.abs(heights.ypoints[i] - current[i]) > 1e-4)
				return false;
		return true;
	}

	private boolean store() {
		if (preview == null)
			return false;
		computedCapillary.getCorrelationGulps().set(candidateAmplitudes(), windowRadius(), maxShiftValue(), value(),
				manual);
		boolean ok = experiment.save_capillaries_description_and_measures();
		if (!ok)
			JOptionPane.showMessageDialog(this, "Could not save the capillary measures.", "Correlation gulps",
					JOptionPane.ERROR_MESSAGE);
		refresh();
		return ok;
	}

	private void copyToGulps() {
		if (preview == null)
			return;
		Capillary cap = computedCapillary;
		int current = 0, replacement = 0;
		Level2D heights = cap.getGulps() == null ? null : cap.getGulps().getHeightSeries();
		if (heights != null)
			for (int i = 0; i < heights.npoints; i++)
				if (heights.ypoints[i] > 0)
					current++;
		for (boolean v : candidates())
			if (v)
				replacement++;
		String message = "Replace the " + current + " gulps of capillary " + cap.getLast2ofCapillaryName() + " with "
				+ replacement + " correlation gulps?"
				+ (cap.isGulpMeasuresDirty() ? "\nUnsaved gulp edits on this capillary will be lost." : "");
		if (JOptionPane.showConfirmDialog(this, message, "Copy to gulps", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		cap.getCorrelationGulps().set(candidateAmplitudes(), windowRadius(), maxShiftValue(), value(), manual);
		cap.copyCorrelationGulpsToGulps();
		int t = cap.getKymographIndex();
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq != null && seq.getFirstViewer() != null && seq.getFirstViewer().getPositionT() == t) {
			experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(t, experiment.getCapillaries());
			roiCapillary = null;
		}
		if (!experiment.save_capillaries_description_and_measures())
			JOptionPane.showMessageDialog(this, "Could not save the capillary measures.", "Copy to gulps",
					JOptionPane.ERROR_MESSAGE);
		refresh();
	}

	private double value() {
		return ((Number) threshold.getValue()).doubleValue();
	}

	private void setThreshold(double v, boolean user) {
		settingThreshold = true;
		threshold.setValue(Math.max(0, Math.min(10000, v)));
		settingThreshold = false;
		manual = user;
		if (!user)
			manualThresholds.remove(computedCapillary);
		refresh();
	}

	private boolean[] candidates() {
		return VerticalCorrelation.candidates(preview.residualRate, preview.motion.reliable, value());
	}

	private void refresh() {
		if (preview != null) {
			int count = 0, good = 0;
			for (boolean v : candidates())
				if (v)
					count++;
			for (boolean v : preview.motion.reliable)
				if (v)
					good++;
			String auto = Double.isFinite(preview.noise.automatic) ? format(preview.noise.automatic) : "unavailable";
			source.setText("<html>" + preview.referenceDescription + "<br>Initial threshold: " + auto + " px/min | "
					+ preview.noise.source + " | quiet intervals: " + preview.noise.quietSamples + "</html>");
			CapillaryCorrelationGulps stored = computedCapillary.getCorrelationGulps();
			String storage = isStoredCurrent() ? "saved"
					: stored.hasMeasures()
							? "not saved (stored: " + stored.countEvents() + " events, window "
									+ stored.getWindowRadius() + ", max shift " + format(stored.getMaxShiftPx())
									+ ", threshold " + format(stored.getThresholdPxPerMin()) + ")"
							: "not saved";
			status.setText(count + " candidate intervals | " + good + "/" + (preview.raw.length - 1)
					+ " reliable correlations | threshold " + format(value()) + " px/min ("
					+ (manual ? "manual" : "automatic") + ") | time " + format(preview.minutes[selectedFrame])
					+ " min | " + storage);
		}
		plot.repaint();
		alignment.repaint();
		if (preview != null && selectedFrame != revealedFrame) {
			revealedFrame = selectedFrame;
			SwingUtilities.invokeLater(alignment::revealSelection);
		}
		scheduleRoiUpdate();
	}

	private void scheduleRoiUpdate() {
		roiTimer.restart();
	}

	/**
	 * Live candidates replace the stored correlation gulps of the displayed
	 * capillary; closing restores them.
	 */
	private void updateRois() {
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq == null)
			return;
		Capillary cap = released ? null : (Capillary) capillaries.getSelectedItem();
		boolean show = showRois.isSelected();
		boolean[] chosen = !released && show && preview != null && cap == computedCapillary ? candidates() : null;
		boolean stored = released || (show && chosen == null);
		if (!released && cap == roiCapillary && stored == roiStored && roiCompensated == compensateEvaporation.isSelected()
				&& Arrays.equals(chosen, roiCandidates)
				&& (chosen == null || roiPreview == preview))
			return;
		List<Integer> frames = new ArrayList<>();
		if (roiCapillary != null)
			frames.add(roiCapillary.getKymographIndex());
		if (cap != null)
			frames.add(cap.getKymographIndex());
		List<ROI> old = new ArrayList<>();
		for (ROI2D roi : seq.getROI2Ds())
			if (roi.getName() != null && roi.getName().contains(ROI_NAME_PART) && frames.contains(roi.getT()))
				old.add(roi);
		List<ROI2D> rois = new ArrayList<>();
		if (chosen != null) {
			int t = cap.getKymographIndex();
			for (int i = 1; i < chosen.length; i++)
				if (chosen[i] && Double.isFinite(preview.raw[i]))
					rois.add(CapillaryCorrelationGulps.createRoi(cap.getKymographPrefix(), i, preview.raw[i],
							preview.motion.shift[i], t));
			if (preview.hasEvaporationReference)
				rois.addAll(IntakeEvaporationOverlay.createRois(cap.getKymographPrefix(), candidateAmplitudes(),
						preview.raw, preview.evaporation, t, compensateEvaporation.isSelected()));
		} else if (stored) {
			Capillary owner = released ? roiCapillary : cap;
			if (owner != null)
				rois.addAll(owner.getCorrelationGulpROIsForCapillaryAtT(owner.getKymographIndex()));
		}
		seq.beginUpdate();
		try {
			if (!old.isEmpty())
				seq.removeROIs(old, false);
			if (!rois.isEmpty())
				seq.addROIs(rois, false);
		} finally {
			seq.endUpdate();
		}
		roiCapillary = cap;
		roiCandidates = chosen;
		roiStored = stored;
		roiCompensated = compensateEvaporation.isSelected();
		roiPreview = chosen == null ? null : preview;
	}

	private void export() {
		if (preview == null)
			return;
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(
				new java.io.File("correlation-" + computedCapillary.getLast2ofCapillaryName() + ".csv"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		Path file = chooser.getSelectedFile().toPath();
		if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file.getFileName() + "?", "Export",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		try {
			writeCsv(file);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void writeCsv(Path file) throws IOException {
		boolean[] selected = candidates();
		try (java.io.BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writer.write("# prototype=vertical_correlation;capillary=" + computedCapillary.getLast2ofCapillaryName()
					+ ";window_radius=" + radius.getValue() + ";max_shift_px=" + maxShift.getValue() + "\n");
			writer.write("# experiment=" + experiment.getResultsDirectory() + ";reference="
					+ preview.referenceDescription + ";grey_subtract_intake_evaporation=" + compensateEvaporation.isSelected() + "\n");
			writer.write("# threshold_source=" + preview.noise.source + ";manual=" + manual
					+ ";automatic_threshold_px_per_min=" + preview.noise.automatic + "\n");
			writer.write(
					"frame;elapsed_min;raw_px;refined_px;shift_px;evaporation_increment_px;excess_px_per_min;correlation;reliable;reason;automatic_threshold_px_per_min;chosen_threshold_px_per_min;candidate;candidate_amplitude_px\n");
			for (int i = 0; i < preview.raw.length; i++) {
				double evap = i == 0 ? Double.NaN : preview.evaporation[i] - preview.evaporation[i - 1];
				writer.write(i + ";" + preview.minutes[i] + ";" + preview.raw[i] + ";" + preview.motion.refined[i] + ";"
						+ preview.motion.shift[i] + ";" + evap + ";" + preview.residualRate[i] + ";"
						+ preview.motion.correlation[i] + ";" + preview.motion.reliable[i] + ";"
						+ preview.motion.reason[i] + ";" + preview.noise.automatic + ";" + value() + ";" + selected[i]
						+ ";" + (selected[i] ? Math.max(0, preview.motion.shift[i]) : 0) + "\n");
			}
		}
	}

	private double evaporationRate(int i) {
		if (preview == null || i <= 0 || i >= preview.minutes.length || i >= preview.evaporation.length)
			return Double.NaN;
		double dt = preview.minutes[i] - preview.minutes[i - 1];
		if (!(dt > 0))
			return Double.NaN;
		double increment = preview.evaporation[i] - preview.evaporation[i - 1];
		return Double.isFinite(increment) ? increment / dt : Double.NaN;
	}

	private static String format(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private final class MotionPlot extends JPanel {
		private static final long serialVersionUID = 1L;
		private double low, high;
		private boolean dragging;

		MotionPlot() {
			setBackground(Color.WHITE);
			setPreferredSize(new Dimension(1050, 300));
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (preview == null)
						return;
					dragging = Math.abs(e.getY() - y(value())) < 12;
					if (!dragging) {
						double time = preview.minutes[0] + (e.getX() - 65.) / Math.max(1, getWidth() - 90) * duration();
						selectedFrame = closest(time);
						refresh();
					}
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (preview != null && dragging)
						threshold.setValue(Math.max(0, Math.min(10000,
								high - (e.getY() - 35.) / Math.max(1, getHeight() - 85) * (high - low))));
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					dragging = false;
					repaint();
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private double duration() {
			return Math.max(1e-6, preview.minutes[preview.minutes.length - 1] - preview.minutes[0]);
		}

		private int x(int i) {
			return 65 + (int) ((getWidth() - 90) * (preview.minutes[i] - preview.minutes[0]) / duration());
		}

		private int y(double v) {
			return 35 + (int) ((getHeight() - 85) * (high - v) / (high - low));
		}

		private int closest(double time) {
			int best = 0;
			for (int i = 1; i < preview.minutes.length; i++)
				if (Math.abs(preview.minutes[i] - time) < Math.abs(preview.minutes[best] - time))
					best = i;
			return best;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Color.DARK_GRAY);
			g.drawString(
					"Blue: motion minus evaporation; grey: evaporation model (pixels/min); red points = candidate intervals",
					65, 20);
			if (preview == null) {
				g.dispose();
				return;
			}
			if (!dragging) {
				low = -.5;
				high = Math.max(1, value() * 1.2);
				for (double v : preview.residualRate)
					if (Double.isFinite(v)) {
						low = Math.min(low, v - .1);
						high = Math.max(high, v + .2);
					}
				for (int i = 1; i < preview.raw.length; i++) {
					double rate = evaporationRate(i);
					if (Double.isFinite(rate)) {
						low = Math.min(low, rate - .1);
						high = Math.max(high, rate + .2);
					}
				}
			}
			for (int tick = 0; tick <= 4; tick++) {
				double v = low + (high - low) * tick / 4;
				int yy = y(v);
				g.setColor(new Color(225, 225, 225));
				g.drawLine(65, yy, getWidth() - 25, yy);
				g.setColor(Color.DARK_GRAY);
				g.drawString(format(v), 12, yy + 4);
			}
			g.setColor(Color.GRAY);
			for (int i = 2; i < preview.raw.length; i++) {
				double previous = evaporationRate(i - 1);
				double current = evaporationRate(i);
				if (Double.isFinite(previous) && Double.isFinite(current))
					g.drawLine(x(i - 1), y(previous), x(i), y(current));
			}
			boolean[] chosen = candidates();
			for (int i = 1; i < preview.raw.length; i++) {
				if (!preview.motion.reliable[i] || !Double.isFinite(preview.residualRate[i])) {
					g.setColor(Color.GRAY);
					g.drawString("x", x(i) - 3, y(0));
					continue;
				}
				g.setColor(new Color(35, 100, 165));
				if (i > 1 && preview.motion.reliable[i - 1] && Double.isFinite(preview.residualRate[i - 1]))
					g.drawLine(x(i - 1), y(preview.residualRate[i - 1]), x(i), y(preview.residualRate[i]));
				g.setColor(chosen[i] ? Color.RED : new Color(35, 100, 165));
				g.fillOval(x(i) - 3, y(preview.residualRate[i]) - 3, 6, 6);
			}
			g.setColor(new Color(180, 30, 30));
			g.setStroke(new BasicStroke(2));
			g.drawLine(65, y(value()), getWidth() - 25, y(value()));
			g.drawString("Drag threshold: " + format(value()), 75, Math.max(35, y(value()) - 6));
			g.setColor(Color.GRAY);
			g.setStroke(new BasicStroke(1));
			g.drawLine(x(selectedFrame), 35, x(selectedFrame), getHeight() - 50);
			for (int tick = 0; tick <= 5; tick++) {
				double t = preview.minutes[0] + duration() * tick / 5;
				g.setColor(Color.DARK_GRAY);
				g.drawString(format(t), Math.min(getWidth() - 60, 65 + (getWidth() - 90) * tick / 5 - 8),
						getHeight() - 28);
			}
			g.drawString("Elapsed time (minutes)", getWidth() / 2 - 65, getHeight() - 7);
			g.dispose();
		}
	}

	private final class AlignmentView extends JPanel {
		private static final long serialVersionUID = 1L;
		private static final int COLUMN_PX = 4;
		private static final int LEFT = 65;
		private static final int RIGHT = 25;

		AlignmentView() {
			setBackground(Color.WHITE);
		}

		@Override
		public Dimension getPreferredSize() {
			int columns = preview == null ? 0 : preview.image.getWidth();
			int viewport = getParent() instanceof JViewport ? ((JViewport) getParent()).getWidth() : 1050;
			int imageWidth = Math.max(columns * COLUMN_PX, Math.max(1, viewport - LEFT - RIGHT));
			int cropRows = 17;
			if (preview != null) {
				int[] crop = cropBounds();
				cropRows = Math.max(1, crop[1] - crop[0]);
			}
			return new Dimension(LEFT + imageWidth + RIGHT, 25 + cropRows * COLUMN_PX + 25 + 81 + 36);
		}

		void revealSelection() {
			if (preview == null)
				return;
			int x = xOf(selectedFrame);
			scrollRectToVisible(new Rectangle(Math.max(0, x - 40), 0, 80, Math.max(1, getHeight())));
		}

		private int[] cropBounds() {
			int height = preview.image.getHeight();
			int min = height - 1;
			int max = 0;
			for (double v : preview.raw)
				if (Double.isFinite(v)) {
					min = Math.min(min, (int) v - 8);
					max = Math.max(max, (int) v + 9);
				}
			min = Math.max(0, min);
			max = Math.min(height, Math.max(min + 1, max));
			return new int[] { min, max };
		}

		private int imageWidth() {
			return Math.max(1, getWidth() - LEFT - RIGHT);
		}

		private int xOf(int frame) {
			return LEFT + (int) (imageWidth() * (frame + .5) / preview.image.getWidth());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (preview == null)
				return;
			int[] crop = cropBounds();
			int cropHeight = Math.max(1, crop[1] - crop[0]) * COLUMN_PX;
			int width = imageWidth();
			int[] tops = { 25, 25 + cropHeight + 25 };
			int[] heights = { cropHeight, 81 };
			String[] labels = { "Raw kymograph: grey = evaporation + cumulative "
					+ (compensateEvaporation.isSelected() ? "net intake" : "total intake displacement") + ", anchored at t=0",
					"Aligned using refined levels (+/-10 pixels); red ticks = candidate intervals" };
			boolean[] chosen = candidates();
			for (int p = 0; p < 2; p++) {
				int top = tops[p];
				g.setColor(Color.DARK_GRAY);
				g.drawString(labels[p], LEFT, top - 7);
				if (p == 0)
					g.drawImage(preview.image, LEFT, top, LEFT + width, top + heights[p], 0, crop[0],
							preview.image.getWidth(), crop[1], null);
				else if (refinedStrip != null) {
					g.drawImage(refinedStrip, LEFT, top, width, heights[p], null);
					g.setColor(Color.WHITE);
					for (int x = LEFT; x < LEFT + width; x += 40)
						g.drawLine(x, top + heights[p] / 2, x + 8, top + heights[p] / 2);
				}
				if (p == 0) drawIntakeOverlay(g, chosen, crop, top, heights[p]);
				for (int i = 0; i < chosen.length; i++)
					if (p != 0 && chosen[i]) {
						g.setColor(Color.RED);
						g.drawLine(xOf(i), top, xOf(i), top + 7);
					}
				g.setColor(Color.YELLOW);
				g.drawLine(xOf(selectedFrame), top, xOf(selectedFrame), top + heights[p]);
			}
			g.setColor(Color.DARK_GRAY);
			g.drawString(
					"Time axis: at least 4 pixels per column; scroll horizontally for longer kymographs. Grey columns: missing level or image border.",
					LEFT, getHeight() - 7);
		}

		private void drawIntakeOverlay(Graphics graphics, boolean[] chosen, int[] crop, int top, int height) {
			Graphics2D g = (Graphics2D) graphics.create();
			g.clipRect(LEFT, top, imageWidth(), height);
			g.setColor(IntakeEvaporationOverlay.COLOR);
			g.setStroke(new BasicStroke(1));
			List<List<java.awt.geom.Point2D>> paths = preview.hasEvaporationReference
					? IntakeEvaporationOverlay.paths(candidateAmplitudes(), preview.raw, preview.evaporation,
							compensateEvaporation.isSelected()) : java.util.Collections.emptyList();
			for (List<java.awt.geom.Point2D> points : paths) {
				java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
				for (int i = 0; i < points.size(); i++) {
					java.awt.geom.Point2D point = points.get(i);
					double x = LEFT + imageWidth() * point.getX() / preview.image.getWidth();
					double y = top + height * (point.getY() - crop[0]) / (crop[1] - crop[0]);
					if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
				}
				g.draw(path);
			}
			g.setColor(CapillaryCorrelationGulps.ROI_COLOR);
			g.setStroke(new BasicStroke(2));
			for (int i = 1; i < chosen.length; i++) if (chosen[i] && Double.isFinite(preview.raw[i])) {
				double bottom = top + height * (preview.raw[i] - crop[0]) / (crop[1] - crop[0]);
				double tip = bottom - height * Math.max(0, preview.motion.shift[i]) / (crop[1] - crop[0]);
				g.draw(new java.awt.geom.Line2D.Double(xOf(i), bottom, xOf(i), tip));
			}
			g.dispose();
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
			if (gradient > best) {
				best = gradient;
				offset = (j + .5 - 40) / 4.;
			}
		}
		return offset;
	}

	private static BufferedImage aligned(BufferedImage image, double[] levels, double offset) {
		BufferedImage out = new BufferedImage(image.getWidth(), 81, BufferedImage.TYPE_INT_RGB);
		for (int t = 0; t < levels.length; t++)
			for (int j = 0; j < 81; j++) {
				double row = levels[t] + offset + (j - 40) / 4.;
				if (!Double.isFinite(row) || row < 0 || row > image.getHeight() - 1) {
					out.setRGB(t, j, 0xdddddd);
					continue;
				}
				int lo = (int) Math.floor(row), hi = Math.min(image.getHeight() - 1, lo + 1);
				double f = row - lo;
				int rgb = 0;
				for (int c = 2; c >= 0; c--) {
					int a = (image.getRGB(t, lo) >> (8 * c)) & 255, b = (image.getRGB(t, hi) >> (8 * c)) & 255;
					rgb = (rgb << 8) | (int) Math.round(a * (1 - f) + b * f);
				}
				out.setRGB(t, j, rgb);
			}
		return out;
	}
}
