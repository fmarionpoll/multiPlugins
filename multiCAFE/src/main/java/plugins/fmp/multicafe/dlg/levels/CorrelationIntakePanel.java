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
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
import javax.swing.BoxLayout;
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

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.experiment.capillary.IntakeEvaporationOverlay;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.ManualIntakeEdits;
import plugins.fmp.multitools.service.VerticalCorrelation;
import plugins.fmp.multitools.tools.JComponents.RichTooltipLayoutGuard;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Inspect and edit intake for one capillary, or prepare automatic detection for
 * every capillary in the current experiment.
 */
public final class CorrelationIntakePanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private final Experiment experiment;
	private JComboBox<?> experimentBrowser;
	private final JComboBox<String> seriesRange = new JComboBox<>(new String[] {
			"Current experiment", "Current experiment to end", "All experiments (0 to end)" });
	private final JButton runSeries = new JButton("Detect, copy to gulps & save"), stopSeries = new JButton("Stop");
	private final JLabel seriesStatus = new JLabel("Uses automatic thresholds for every capillary.");
	private final JPanel seriesControls = new JPanel(new BorderLayout());
	private SwingWorker<Integer, String> seriesWorker;
	private volatile boolean seriesStopping;
	private volatile int seriesSaved;
	private boolean browserWasEnabled;

	void followExperimentBrowser(JComboBox<?> browser) {
		experimentBrowser = browser;
	}
	private final JComboBox<Capillary> capillaries = new JComboBox<>();
	private final JComboBox<String> runMode = new JComboBox<>(new String[] {
			"Manual - single capillary", "Automatic - all capillaries", "Batch - detect, copy & save" });
	private final JButton detectAll = new JButton("Detect all"), saveAll = new JButton("Save all intake"),
			cancelAll = new JButton("Cancel");
	private final JButton copyAllToGulps = new JButton("Copy all to gulps...");
	private final JLabel batchStatus = new JLabel(" ");
	private final JPanel batchControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private SwingWorker<Map<Capillary, AutomaticResult>, String> batchWorker;
	private Map<Capillary, AutomaticResult> batchResults = new IdentityHashMap<>();
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 10000.0, .05));
	private final JButton automatic = new JButton("Automatic"),
			export = new JButton("Export CSV...");
	private final JButton review = new JButton("Review interval...");
	private final JButton exportExperiment = new JButton("Export experiment review...");
	private SwingWorker<Void, Void> reviewExportWorker;
	private final JCheckBox addGulp = new JCheckBox("Add/edit gulp");
	private final JButton resetManualGulps = new JButton("Reset manual gulps");
	private final Map<Capillary, ManualIntakeEdits> manualGulps = new IdentityHashMap<>();
	private final JButton save = new JButton("Save intake"), copyToGulps = new JButton("Copy to gulps...");
	private final JCheckBox persistentEpisodes = new JCheckBox("Persistent episodes (1-6 bins)", true);
	private final JCheckBox showGaps = new JCheckBox("Show diagnostics", true);
	private final JCheckBox localEvaporation = new JCheckBox("Local evaporation", false);
	private final JCheckBox showRois = new JCheckBox("Show on kymograph", true);
	private final JCheckBox compensateEvaporation = new JCheckBox("Subtract evaporation", true);
	private final JLabel source = new JLabel(" "),
			status = new JLabel("Select a capillary to analyze its accepted topraw levels.");
	private final JLabel detectorVersion = new JLabel();
	private final MotionPlot plot = new MotionPlot();
	private final AlignmentView alignment = new AlignmentView();
	private final Map<Capillary, Double> manualThresholds = new IdentityHashMap<>();
	private final Map<Capillary, CorrelationGulpPreview> cache = new IdentityHashMap<>();
	private final Timer sourceTimer = new Timer(750, e -> checkSources());
	private final Timer roiTimer = new Timer(120, e -> updateRois());
	private Consumer<Capillary> onCapillaryChosen;
	private SwingWorker<CorrelationGulpPreview, Void> worker;
	private Capillary pendingCapillary;
	private CorrelationGulpPreview preview;
	private CorrelationGulpPreview episodePreview;
	private double episodeThreshold = Double.NaN;
	private IntakeEpisodes.Detection cachedEpisodes;
	private IntakeEpisodes.Detection diagnosticDetection;
	private List<IntakeDiagnostics.Warning> cachedDiagnostics;
	private boolean diagnosticNoFly;
	private Capillary computedCapillary, roiCapillary;
	private boolean[] roiCandidates;
	private double[] roiAmplitudes, roiBottoms;
	private boolean roiShowGaps;
	private boolean roiStored, released, suspended;
	private boolean waitingForSources;
	private Sequence previewSequence;
	private boolean roiCompensated;
	private CorrelationGulpPreview roiPreview;
	private int selectedFrame;
	private int revealedFrame = -1;
	private boolean settingThreshold, manual, syncingSelection;
	private RichTooltipLayoutGuard tooltipLayoutGuard;
	private JDialog reviewDialog;

	/**
	 * onCapillaryChosen is notified when the user picks a capillary in this panel
	 * (e.g. to move the kymograph viewer).
	 */
	public static CorrelationIntakePanel open(Component owner, Experiment exp, Capillary initial,
			Consumer<Capillary> onCapillaryChosen) {
		CorrelationIntakePanel panel = new CorrelationIntakePanel(exp, initial);
		panel.onCapillaryChosen = onCapillaryChosen;
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Intake as measured from topraw",
				Dialog.ModalityType.MODELESS);
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
		if (first != null && (first.getCorrelationGulps().getMethodOptions().startsWith("v3,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v4,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v5,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v6,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v6.1,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith(IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw"))) {
			String options = first.getCorrelationGulps().getMethodOptions();
			persistentEpisodes.setSelected(options.contains("persistent=true"));
			localEvaporation.setSelected(options.contains("local=true"));
		}
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
		JPanel header = new JPanel(new BorderLayout());
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		controls.add(new JLabel("Capillary"));
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		previousButton.setMargin(new Insets(0, 0, 0, 0));
		nextButton.setMargin(new Insets(0, 0, 0, 0));
		controls.add(previousButton);
		controls.add(capillaries);
		controls.add(nextButton);

		JPanel thresholdControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		thresholdControls.add(new JLabel("Threshold setting (pixels/min)"));
		thresholdControls.add(threshold);
		thresholdControls.add(automatic);
		thresholdControls.add(save);
		thresholdControls.add(copyToGulps);
		thresholdControls.add(export);
		thresholdControls.add(review);
		JPanel editControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		editControls.add(addGulp);
		editControls.add(resetManualGulps);
		editControls.add(exportExperiment);
		editControls.add(new JLabel("Select a point or drag a region on the graph or kymograph."));
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		JPanel modeControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		modeControls.add(new JLabel("Mode"));
		modeControls.add(runMode);
		detectorVersion.setToolTipText("Persistent episodes uses the " + IntakeEpisodes.METHOD_VERSION
				+ " plateau checks and image-confirmed local step recovery. With persistence off, detection uses only the single-bin threshold rule.");
		modeControls.add(detectorVersion);
		rows.add(modeControls);
		rows.add(controls);
		JPanel methodControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		methodControls.add(persistentEpisodes);
		methodControls.add(localEvaporation);
		methodControls.add(showGaps);
		methodControls.add(showRois);
		methodControls.add(compensateEvaporation);
		rows.add(methodControls);
		rows.add(thresholdControls);
		rows.add(editControls);
		batchControls.add(detectAll);
		batchControls.add(cancelAll);
		batchControls.add(saveAll);
		batchControls.add(copyAllToGulps);
		batchControls.add(batchStatus);
		rows.add(batchControls);
		JPanel seriesActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		seriesActions.add(new JLabel("Experiments"));
		seriesActions.add(seriesRange);
		seriesActions.add(runSeries);
		seriesActions.add(stopSeries);
		seriesControls.add(seriesActions, BorderLayout.NORTH);
		seriesControls.add(seriesStatus, BorderLayout.SOUTH);
		rows.add(seriesControls);
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
				"<html>Blue bars: intake displacement (fitted meniscus steps; amber: downward steps; teal: drift; purple: anchor/offset/upward warnings). Grey: evaporation + cumulative intake.<br>"
						+ "Drag the red bar to adjust the threshold; click the graph to inspect a time point. "
						+ "Save intake keeps separate prototype results. Copy to gulps (or Copy all to gulps) installs them in the ordinary gulp measures and graphs.</html>"),
				BorderLayout.SOUTH);
		add(footer, BorderLayout.SOUTH);
		setResultControlsEnabled(false);
		roiTimer.setRepeats(false);

		defineToolTips();
		defineActionListeners();
		defineChangeListeners();
		updateModeControls();
		tooltipLayoutGuard = new RichTooltipLayoutGuard();
		sourceTimer.start();
	}

	private void defineActionListeners() {
		runSeries.addActionListener(e -> startSeries());
		stopSeries.addActionListener(e -> {
			seriesStopping = true;
			stopSeries.setEnabled(false);
			seriesStatus.setText("Stopping; any save already in progress will finish.");
		});
		runMode.addActionListener(e -> {
			updateModeControls();
			if (preview != null) display(computedCapillary, preview);
		});
		detectAll.addActionListener(e -> detectAll());
		saveAll.addActionListener(e -> saveAll());
		copyAllToGulps.addActionListener(e -> copyAllToGulps());
		cancelAll.addActionListener(e -> cancelBatch());
		save.addActionListener(e -> store());
		copyToGulps.addActionListener(e -> copyToGulps());
		capillaries.addActionListener(e -> {
			if (syncingSelection)
				return;
			Capillary cap = (Capillary) capillaries.getSelectedItem();
			if (onCapillaryChosen != null && cap != null)
				onCapillaryChosen.accept(cap);
			analyzeSelected();
			updateModeControls();
		});
		previousButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() - 1));
		nextButton.addActionListener(e -> capillaries.setSelectedIndex(capillaries.getSelectedIndex() + 1));
		automatic.addActionListener(e -> {
			invalidateBatch();
			if (preview != null && Double.isFinite(preview.noise.automatic))
				setThreshold(preview.noise.automatic, false);
		});
		showRois.addActionListener(e -> updateRois());
		compensateEvaporation.addActionListener(e -> {
			alignment.repaint();
			updateRois();
		});
		export.addActionListener(e -> export());
		exportExperiment.addActionListener(e -> exportExperimentReview());
		exportExperiment.setToolTipText("Export one ZIP with before/after CSVs and kymographs for every capillary, including unsaved manual gulp edits.");
		export.setText("Export selected CSV...");
		export.setToolTipText("Export one CSV with automatic detection, current detector settings, edited intake and per-bin change labels for review.");
		review.addActionListener(e -> reviewInterval());
		resetManualGulps.addActionListener(e -> {
			manualGulps.put(computedCapillary, new ManualIntakeEdits());
			invalidateBatch();
			refresh();
		});
		addGulp.setToolTipText("Select a region, then confirm its net intake (after evaporation subtraction) and bottom position in pixels. Overrides automatic detection, including x-marked bins. Save intake or Copy to gulps to persist.");
		resetManualGulps.setToolTipText("Discard manual overrides on this capillary and return to automatic detection at the current threshold; save to persist the reset.");
		showGaps.setToolTipText(
				"Amber: abrupt steps. Teal: gradual drift. Purple: initial-anchor or reconstruction offsets in either direction. Diagnostics are not intake.");
		showGaps.addActionListener(e -> {
			refresh();
			updateRois();
		});
		persistentEpisodes.setToolTipText(
				"Signed 1-6 bin movement must persist between robust level plateaus and remain above its limit after fitting. Controls use the same test. Groups keep their time span; bars within one group are time bins, not separately resolved gulps.");
		localEvaporation.setToolTipText(
				"Optional scale of the common evaporation shape from quiet runs; uncertain estimates retain the common model.");
		localEvaporation.addActionListener(e -> {
			invalidateBatch();
			cache.clear();
			clearPreview();
			analyzeSelected();
		});
		persistentEpisodes.addActionListener(e -> {
			invalidateBatch();
			review.setEnabled(preview != null && persistentEpisodes.isSelected());
			refresh();
			roiCandidates = null;
			updateRois();
		});
	}

	private void defineChangeListeners() {
		threshold.addChangeListener(e -> {
			if (!settingThreshold && preview != null) {
				invalidateBatch();
				manual = true;
				manualThresholds.put(computedCapillary, value());
			}
			refresh();
		});
	}

	private void defineToolTips() {
		runSeries.setToolTipText("Detect every capillary with its automatic threshold, replace ordinary gulps (including manual edits), and save each experiment in the selected range.");
		seriesRange.setToolTipText("Current means the experiment selected when the batch starts; ranges include both endpoints. Progress uses the browser's zero-based absolute experiment index.");
		stopSeries.setToolTipText("Stop before copying the current experiment if detection is unfinished; a save already started is completed.");
		detectAll.setToolTipText("Detect every capillary in this experiment, including no-fly controls, using its own automatic threshold.");
		saveAll.setToolTipText("Save all prepared results as correlation gulps. Existing correlation results are replaced; classic gulps are kept.");
		copyAllToGulps.setToolTipText("Replace ordinary gulps for every capillary with the prepared intake, then save. These measures supply ordinary gulp graphs and exports.");
		cancelAll.setToolTipText("Cancel detection without installing or saving batch results.");
		compensateEvaporation.setToolTipText(
				"Checked: grey steps = max(0, stored amplitude minus the model's evaporation in that bin). Unchecked: full stored amplitudes. Blue bars, detection and saved measures are unchanged.");
		showRois.setToolTipText(
				"Show intake amplitudes and evaporation plus cumulative intake, starting at raw t=0; later raw levels do not reset the grey curve.");
		threshold.setToolTipText(
				"Sensitivity setting in pixels/min. In episode mode, control-calibrated thresholds and sustained plateau checks also apply; crossing the red line alone does not accept a gulp.");
		automatic.setToolTipText("Quiet-period median + 3 robust noise SD, with a no-fly control noise floor.");
		save.setToolTipText(
				"Store the candidates of this capillary as correlation gulps (saved with the capillary measures).");
		copyToGulps.setToolTipText("Save, then replace the gulps of this capillary with the correlation gulps.");
		previousButton.setToolTipText("previous capillary");
		nextButton.setToolTipText("select next capillary");
	}

	public Experiment getExperiment() {
		return experiment;
	}

	void onCapillaryChosen(Consumer<Capillary> callback) {
		onCapillaryChosen = callback;
	}

	boolean isSeriesRunning() {
		return seriesWorker != null;
	}

	void suspendSession() {
		if (released || suspended) return;
		suspended = true;
		cancelBatch();
		if (worker != null) worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		sourceTimer.stop();
		roiTimer.stop();
		if (reviewDialog != null) {
			reviewDialog.dispose();
			reviewDialog = null;
		}
		if (tooltipLayoutGuard != null) {
			tooltipLayoutGuard.close();
			tooltipLayoutGuard = null;
		}
		updateRois();
		roiCapillary = null;
		roiPreview = null;
		roiCandidates = null;
	}

	void resumeSession() {
		if (released) return;
		suspended = false;
		if (tooltipLayoutGuard == null) tooltipLayoutGuard = new RichTooltipLayoutGuard();
		refreshCapillaries();
		Sequence currentSequence = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (preview != null && previewSequence != currentSequence) {
			cache.clear();
			clearPreview();
		}
		waitingForSources = !hasAcceptedInput();
		if (waitingForSources) {
			clearPreview();
			status.setText("Load this experiment's kymographs and accepted topraw to analyze intake.");
		} else {
			checkSources();
			analyzeSelected();
		}
		sourceTimer.start();
		updateModeControls();
		scheduleRoiUpdate();
	}

	private boolean hasAcceptedInput() {
		Capillary cap = (Capillary) capillaries.getSelectedItem();
		return experiment.getSeqKymos() != null && experiment.getSeqKymos().getSequence() != null
				&& experiment.getSeqKymos().getSequence().getSizeT() > 0 && cap != null
				&& cap.getTopRaw() != null && cap.getTopRaw().polylineLevel != null
				&& cap.getTopRaw().polylineLevel.npoints >= 8;
	}

	/** Reloading descriptors may replace capillary objects in the same experiment. */
	private void refreshCapillaries() {
		List<Capillary> current = experiment.getCapillaries().getList();
		boolean same = current.size() == capillaries.getItemCount();
		for (int i = 0; same && i < current.size(); i++) same = current.get(i) == capillaries.getItemAt(i);
		if (same) return;
		Capillary old = (Capillary) capillaries.getSelectedItem();
		Map<String, Double> thresholdsByName = new java.util.HashMap<>();
		manualThresholds.forEach((cap, value) -> thresholdsByName.put(cap.getKymographPrefix(), value));
		cache.clear();
		manualThresholds.clear();
		manualGulps.clear();
		invalidateBatch();
		clearPreview();
		syncingSelection = true;
		try {
			capillaries.removeAllItems();
			for (Capillary cap : current) {
				capillaries.addItem(cap);
				Double previous = thresholdsByName.get(cap.getKymographPrefix());
				if (previous != null) manualThresholds.put(cap, previous);
			}
			if (old != null) for (Capillary cap : current)
				if (java.util.Objects.equals(old.getKymographPrefix(), cap.getKymographPrefix())) {
					capillaries.setSelectedItem(cap);
					break;
				}
		} finally {
			syncingSelection = false;
		}
	}

	void disposeSession() {
		release();
	}

	/**
	 * Follows a capillary selected elsewhere (kymograph viewer); analyzes it if not
	 * cached.
	 */
	public void showCapillary(Capillary cap) {
		if (released || suspended || cap == null || !experiment.getCapillaries().getList().contains(cap)
				|| cap == capillaries.getSelectedItem())
			return;
		syncingSelection = true;
		try {
			capillaries.setSelectedItem(cap);
		} finally {
			syncingSelection = false;
		}
		analyzeSelected();
		updateModeControls();
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
		if (released) return;
		released = true;
		seriesStopping = true;
		cancelBatch();
		sourceTimer.stop();
		if (reviewDialog != null) {
			reviewDialog.dispose();
			reviewDialog = null;
		}
		if (tooltipLayoutGuard != null) {
			tooltipLayoutGuard.close();
			tooltipLayoutGuard = null;
		}
		if (worker != null)
			worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		roiTimer.stop();
		released = true;
		cachedDiagnostics = null;
		diagnosticDetection = null;
		cachedEpisodes = null;
		episodePreview = null;
		preview = null;
		computedCapillary = null;
		cache.clear();
		updateRois();
	}

	private void setResultControlsEnabled(boolean enabled) {
		boolean editable = enabled && !automaticMode() && batchWorker == null && seriesWorker == null && reviewExportWorker == null;
		automatic.setEnabled(editable && preview != null && Double.isFinite(preview.noise.automatic));
		export.setEnabled(enabled);
		exportExperiment.setEnabled(enabled && reviewExportWorker == null);
		review.setEnabled(enabled && persistentEpisodes.isSelected());
		threshold.setEnabled(editable);
		save.setEnabled(editable);
		copyToGulps.setEnabled(editable);
		addGulp.setEnabled(editable);
		resetManualGulps.setEnabled(editable);
		// Retain the user's editing mode while another capillary is loading.
	}

	private void checkSources() {
		if (released || suspended || seriesWorker != null || reviewExportWorker != null) return;
		if (waitingForSources) {
			refreshCapillaries();
			if (hasAcceptedInput()) {
				waitingForSources = false;
				analyzeSelected();
			}
			return;
		}
		if (!batchResults.isEmpty() && !batchSourcesCurrent()) {
			invalidateBatch();
			batchStatus.setText("Levels changed; run Detect all again.");
		}
		if (preview != null && !preview.matchesSources(experiment)) {
			// Geometry selected against old levels must not be silently reused.
			for (Capillary cap : experiment.getCapillaries().getList())
				manualGulps.put(cap, new ManualIntakeEdits());
			cache.clear();
			clearPreview();
			analyzeSelected();
		}
	}

	private void analyzeSelected() {
		if (batchWorker != null || seriesWorker != null || released || suspended) return;
		Capillary cap = (Capillary) capillaries.getSelectedItem();
		if (cap == null) {
			status.setText("No capillaries loaded.");
			return;
		}
		if (cap == computedCapillary && preview != null && preview.matchesSources(experiment))
			return;
		if (cap == pendingCapillary && worker != null && !worker.isDone())
			return;
		CorrelationGulpPreview cached = cache.get(cap);
		if (cached != null && !cached.matchesSources(experiment))
			for (Capillary current : experiment.getCapillaries().getList())
				manualGulps.put(current, new ManualIntakeEdits());
		if (cached != null && cached.matchesSources(experiment)) {
			if (worker != null)
				worker.cancel(true);
			worker = null;
			pendingCapillary = null;
			display(cap, cached);
		} else
			startCompute(cap, false);
	}

	private void clearPreview() {
		cachedDiagnostics = null;
		diagnosticDetection = null;
		cachedEpisodes = null;
		episodePreview = null;
		preview = null;
		computedCapillary = null;
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
		boolean local = localEvaporation.isSelected();
		pendingCapillary = cap;
		status.setText("Computing accepted-level increments and no-fly control noise for "
				+ cap.getLast2ofCapillaryName() + "...");
		worker = new SwingWorker<CorrelationGulpPreview, Void>() {
			@Override
			protected CorrelationGulpPreview doInBackground() {
				return CorrelationGulpPreview.computeAccepted(experiment, cap, local);
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
					if (!result.matchesSources(experiment)) {
						startCompute(cap, false);
						return;
					}
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
		previewSequence = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		computedCapillary = cap;
		selectedFrame = 0;

		alignment.revalidate();
		Double saved = automaticMode() ? null : manualThresholds.get(cap);
		CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
		if (!manualGulps.containsKey(cap))
			manualGulps.put(cap, ManualIntakeEdits.fromOptions(stored.getMethodOptions(), preview.raw.length));
		if (!automaticMode() && saved == null && stored.hasMeasures() && stored.isManualThreshold() && sameSettings(stored)
				&& Double.isFinite(stored.getThresholdPxPerMin()))
			saved = stored.getThresholdPxPerMin();
		double initial = saved != null ? saved : preview.noise.automatic;
		setResultControlsEnabled(true);
		setThreshold(Double.isFinite(initial) ? initial : 1, saved != null || !Double.isFinite(initial));
	}

	private boolean automaticMode() {
		return runMode.getSelectedIndex() != 0;
	}

	private List<Experiment> seriesTargets() {
		List<Experiment> targets = new ArrayList<>();
		if (experimentBrowser == null) {
			if (seriesRange.getSelectedIndex() != 0)
				throw new IllegalStateException("Open this panel from the experiment browser to run multiple experiments.");
			targets.add(experiment);
			return targets;
		}
		int current = experimentBrowser.getSelectedIndex();
		if (current < 0) throw new IllegalStateException("Select an experiment first.");
		int first = seriesRange.getSelectedIndex() == 2 ? 0 : current;
		int last = seriesRange.getSelectedIndex() == 0 ? current : experimentBrowser.getItemCount() - 1;
		for (int i = first; i <= last; i++) {
			// Snapshot identities without loading every experiment on the UI thread.
			Object item = experimentBrowser instanceof JComboBoxExperimentLazy
					? ((JComboBoxExperimentLazy) experimentBrowser).getItemAtNoLoad(i)
					: experimentBrowser.getItemAt(i);
			if (!(item instanceof Experiment)) throw new IllegalStateException("Missing experiment at index " + i);
			targets.add((Experiment) item);
		}
		return targets;
	}

	private void startSeries() {
		if (released || suspended || seriesWorker != null || batchWorker != null) return;
		final List<Experiment> targets;
		try {
			targets = seriesTargets();
		} catch (RuntimeException error) {
			seriesStatus.setText(error.getMessage());
			return;
		}
		final int firstIndex = experimentBrowser == null || seriesRange.getSelectedIndex() == 2
				? 0 : experimentBrowser.getSelectedIndex();
		final int experimentCount = experimentBrowser == null ? 1 : experimentBrowser.getItemCount();
		if (worker != null) worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		sourceTimer.stop();
		invalidateBatch();
		clearPreview();
		seriesSaved = 0;
		seriesStopping = false;
		boolean episodes = persistentEpisodes.isSelected(), local = localEvaporation.isSelected();
		if (experimentBrowser != null) {
			browserWasEnabled = experimentBrowser.isEnabled();
			experimentBrowser.setEnabled(false);
		}
		seriesWorker = new SwingWorker<Integer, String>() {
			private Experiment attentionTarget;
			private String attentionLabel;
			@Override
			protected Integer doInBackground() throws Exception {
				IntakeSeriesProcessor processor = new IntakeSeriesProcessor();
				for (int i = 0; i < targets.size(); i++) {
					Experiment target = targets.get(i);
					String label = "Experiment " + (firstIndex + i) + "/" + experimentCount + ": " + target;
					attentionTarget = target;
					attentionLabel = label;
					if (seriesStopping) break;
					publish(label + " - loading...");
					try {
						if (!processor.process(target, episodes, local, () -> seriesStopping,
								message -> publish(label + " - " + message))) break;
						seriesSaved++;
						attentionTarget = null;
						attentionLabel = null;
					} catch (Exception error) {
						Throwable cause = error;
						while (cause.getCause() != null) cause = cause.getCause();
						throw new IllegalStateException(label + ": " + cause.getMessage(), error);
					}
				}
				return seriesSaved;
			}

			@Override
			protected void process(List<String> messages) {
				if (!released && seriesWorker == this) seriesStatus.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				String result;
				try {
					get();
					result = seriesStopping ? (attentionLabel == null ? "Stopped. " : "Stopped at " + attentionLabel + ". ") : "Complete. ";
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					result = "Interrupted. ";
				} catch (ExecutionException error) {
					result = "Failed at " + error.getCause().getMessage() + ". ";
				}
				seriesWorker = null;
				if (experimentBrowser != null) experimentBrowser.setEnabled(browserWasEnabled);
				if (!released && attentionTarget != null) selectSeriesExperiment(attentionTarget);
				String outcome = result + seriesSaved + "/" + targets.size() + " experiment(s) copied to gulps and saved.";
				if (released) {
					CorrelationIntakePanel.this.firePropertyChange("intake_series_finished", null, outcome);
					return;
				}
				cache.clear();
				if (targets.contains(experiment)) {
					manualThresholds.clear();
					manualGulps.clear();
					refreshGulpRois();
				}
				updateModeControls();
				if (experimentBrowser == null || experimentBrowser.getSelectedItem() == experiment) {
					sourceTimer.start();
					analyzeSelected();
				}
				seriesStatus.setText(outcome);
				CorrelationIntakePanel.this.firePropertyChange("intake_series_finished", null, outcome);
			}
		};
		seriesStatus.setText("Detecting, replacing gulps and saving " + targets.size() + " experiment(s), indices "
				+ firstIndex + "-" + (firstIndex + targets.size() - 1) + " / " + experimentCount + "...");
		updateModeControls();
		seriesWorker.execute();
	}

	private void selectSeriesExperiment(Experiment target) {
		if (experimentBrowser == null) return;
		for (int i = 0; i < experimentBrowser.getItemCount(); i++) {
			Object item = experimentBrowser instanceof JComboBoxExperimentLazy
					? ((JComboBoxExperimentLazy) experimentBrowser).getItemAtNoLoad(i) : experimentBrowser.getItemAt(i);
			if (item == target) { experimentBrowser.setSelectedIndex(i); return; }
		}
	}

	void showSeriesOutcome(String message) {
		if (released) return;
		runMode.setSelectedIndex(2);
		seriesStatus.setText(message);
	}

	private void updateDetectorLabel() {
		detectorVersion.setText(persistentEpisodes.isSelected()
				? "Detector " + IntakeEpisodes.METHOD_VERSION + " - persistent episodes"
				: "Detector " + IntakeEpisodes.METHOD_VERSION + " - single-bin (persistence off)");
	}

	private void updateModeControls() {
		updateDetectorLabel();
		boolean busy = batchWorker != null || seriesWorker != null || reviewExportWorker != null;
		batchControls.setVisible(runMode.getSelectedIndex() == 1);
		seriesControls.setVisible(runMode.getSelectedIndex() == 2);
		seriesRange.setEnabled(!busy);
		runSeries.setEnabled(!busy);
		stopSeries.setEnabled(seriesWorker != null && !seriesStopping);
		runMode.setEnabled(!busy);
		detectAll.setEnabled(!busy && capillaries.getItemCount() > 0);
		cancelAll.setEnabled(busy);
		saveAll.setEnabled(!busy && !batchResults.isEmpty());
		copyAllToGulps.setEnabled(!busy && !batchResults.isEmpty());
		automatic.setVisible(!automaticMode());
		save.setVisible(!automaticMode());
		copyToGulps.setVisible(!automaticMode());
		capillaries.setEnabled(!busy);
		previousButton.setEnabled(!busy && capillaries.getSelectedIndex() > 0);
		nextButton.setEnabled(!busy && capillaries.getSelectedIndex() < capillaries.getItemCount() - 1);
		persistentEpisodes.setEnabled(!busy);
		localEvaporation.setEnabled(!busy);
		setResultControlsEnabled(preview != null && !busy);
		revalidate();
	}

	private void invalidateBatch() {
		batchResults.clear();
		saveAll.setEnabled(false);
		copyAllToGulps.setEnabled(false);
		batchStatus.setText("Run Detect all to prepare results for this experiment.");
	}

	private boolean batchSourcesCurrent() {
		List<Capillary> current = experiment.getCapillaries().getList();
		return current.size() == batchResults.size() && current.stream().allMatch(cap ->
				batchResults.containsKey(cap) && batchResults.get(cap).preview.matchesSources(experiment));
	}

	private void cancelBatch() {
		if (batchWorker == null) return;
		batchWorker.cancel(true);
		batchWorker = null;
		invalidateBatch();
		batchStatus.setText("Cancelled; no batch results saved.");
		updateModeControls();
		if (!released) analyzeSelected();
	}

	/** Captures the same amplitudes and fitted geometry used by manual detection. */
	static final class AutomaticResult {
		final CorrelationGulpPreview preview;
		final double[] amplitudes, bottoms;
		final String options;

		AutomaticResult(CorrelationGulpPreview p, boolean episodes, boolean local) {
			this(p, episodes, local, p.noise.automatic);
		}

		AutomaticResult(CorrelationGulpPreview p, boolean episodes, boolean local, double threshold) {
			preview = p;
			if (!Double.isFinite(threshold) || threshold < 0)
				throw new IllegalArgumentException("Detection threshold unavailable");
			if (episodes) {
				IntakeEpisodes.Detection detection = IntakeEpisodes.detect(p.residualRate, p.minutes,
						p.motion.reliable, p.controlRates, p.noise.automatic, threshold, p.correctedLevels,
						p::supportsIntakeStep);
				amplitudes = detection.net.clone();
				bottoms = detection.position.clone();
				for (int i = 0; i < bottoms.length; i++) {
					bottoms[i] += p.evaporation[i];
					if (i > 0 && amplitudes[i] > 0)
						amplitudes[i] += p.evaporation[i] - p.evaporation[i - 1];
				}
			} else {
				bottoms = null;
				boolean[] chosen = VerticalCorrelation.candidates(p.residualRate, p.motion.reliable, threshold);
				amplitudes = new double[chosen.length];
				for (int i = 0; i < chosen.length; i++)
					if (chosen[i]) amplitudes[i] = Math.max(0, p.motion.shift[i]);
			}
			options = IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + episodes + ",local=" + local
					+ ",scale=" + (local ? p.localScale.factor : 1);
		}

		void install(Capillary cap) {
			cap.getCorrelationGulps().set(amplitudes, 0, 0, preview.noise.automatic, false);
			cap.getCorrelationGulps().setBottomPositions(bottoms);
			cap.getCorrelationGulps().setMethodOptions(options);
		}
	}

	private void detectAll() {
		if (batchWorker != null || seriesWorker != null || released || suspended) return;
		if (worker != null) worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		invalidateBatch();
		clearPreview();
		List<Capillary> targets = new ArrayList<>(experiment.getCapillaries().getList());
		boolean episodes = persistentEpisodes.isSelected(), local = localEvaporation.isSelected();
		batchWorker = new SwingWorker<Map<Capillary, AutomaticResult>, String>() {
			@Override
			protected Map<Capillary, AutomaticResult> doInBackground() {
				Map<Capillary, AutomaticResult> results = new IdentityHashMap<>();
				for (Capillary cap : targets) {
					if (isCancelled()) throw new CancellationException();
					publish("Detecting " + (results.size() + 1) + "/" + targets.size() + ": " + cap.getLast2ofCapillaryName());
					try {
						results.put(cap, new AutomaticResult(CorrelationGulpPreview.computeAccepted(experiment, cap, local), episodes, local));
					} catch (RuntimeException e) {
						throw new IllegalArgumentException(cap.getLast2ofCapillaryName() + ": " + e.getMessage(), e);
					}
				}
				return results;
			}

			@Override
			protected void process(List<String> messages) {
				if (batchWorker == this) batchStatus.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (batchWorker != this || released) return;
				batchWorker = null;
				try {
					batchResults = get();
					if (!batchSourcesCurrent()) throw new IllegalStateException("Levels changed; run Detect all again.");
					for (Map.Entry<Capillary, AutomaticResult> entry : batchResults.entrySet())
						cache.put(entry.getKey(), entry.getValue().preview);
					batchStatus.setText("Ready: " + batchResults.size() + " capillaries. Save intake or copy to gulps.");
					analyzeSelected();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					invalidateBatch();
					batchStatus.setText("Detection interrupted; no batch results saved.");
				} catch (ExecutionException | RuntimeException e) {
					invalidateBatch();
					Throwable cause = e.getCause() == null ? e : e.getCause();
					batchStatus.setText("Detection failed: " + cause.getMessage());
				}
				updateModeControls();
			}
		};
		updateModeControls();
		batchWorker.execute();
	}

	private void saveAll() {
		persistBatch(false);
	}

	private void copyAllToGulps() {
		if (batchWorker != null || batchResults.isEmpty()) return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			batchStatus.setText("Levels changed; run Detect all again.");
			return;
		}
		Map<Capillary, AutomaticResult> confirmed = batchResults;
		if (JOptionPane.showConfirmDialog(this,
				"Replace ordinary gulps for all " + batchResults.size() + " capillaries with the detected intake?"
				+ "\nExisting gulps, including unsaved manual edits, will be replaced."
				+ "\nThe new gulp measures will be saved for graphs and exports.",
				"Copy all to gulps", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		if (batchResults == confirmed) persistBatch(true);
	}

	private void persistBatch(boolean copy) {
		if (batchWorker != null || batchResults.isEmpty()) return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			batchStatus.setText("Levels changed; run Detect all again.");
			return;
		}
		for (Map.Entry<Capillary, AutomaticResult> entry : batchResults.entrySet()) {
			entry.getValue().install(entry.getKey());
			manualGulps.put(entry.getKey(), new ManualIntakeEdits());
			if (copy) entry.getKey().copyCorrelationGulpsToGulps();
			manualThresholds.remove(entry.getKey());
		}
		if (copy) refreshGulpRois();
		boolean saved = experiment.save_capillaries_description_and_measures();
		batchStatus.setText(saved ? "Saved " + (copy ? "ordinary gulps" : "separate intake") + " for " + batchResults.size() + " capillaries."
				: "Save failed; results remain in memory. Retry saving.");
		refresh();
	}

	private void refreshGulpRois() {
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq != null && seq.getFirstViewer() != null) {
			experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(seq.getFirstViewer().getPositionT(), experiment.getCapillaries());
			roiCapillary = null;
			updateRois();
		}
	}

	private boolean sameSettings(CapillaryCorrelationGulps stored) {
		return stored.getMethodOptions().startsWith(methodOptions());
	}

	private double[] candidateAmplitudes() {
		double[] amplitudes = detectedAmplitudes();
		manualEdits().apply(amplitudes, null);
		return amplitudes;
	}

	/** Detector output at the chosen threshold, before any manual event edits. */
	private double[] detectedAmplitudes() {
		if (persistentEpisodes.isSelected()) {
			double[] amplitudes = episodeDetection().net.clone();
			for (int i = 1; i < amplitudes.length; i++)
				if (amplitudes[i] > 0)
					amplitudes[i] += preview.evaporation[i] - preview.evaporation[i - 1];
			return amplitudes;
		}
		boolean[] chosen = VerticalCorrelation.candidates(preview.residualRate, preview.motion.reliable, value());
		double[] amplitudes = new double[chosen.length];
		for (int i = 0; i < chosen.length; i++)
			amplitudes[i] = chosen[i] ? Math.max(0, preview.motion.shift[i]) : 0;
		return amplitudes;
	}

	private double[] candidateBottoms() {
		double[] positions = persistentEpisodes.isSelected() ? episodeDetection().position.clone() : preview.raw.clone();
		if (persistentEpisodes.isSelected())
			for (int i = 0; i < positions.length; i++)
				positions[i] += preview.evaporation[i];
		manualEdits().apply(new double[positions.length], positions);
		return positions;
	}

	private void storeGeometry(Capillary cap) {
		cap.getCorrelationGulps().setBottomPositions(candidateBottoms());
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
		if (persistentEpisodes.isSelected() || !manualEdits().isEmpty()) {
			if (!stored.getEvents().hasBottomPositions())
				return false;
			double[] bottoms = candidateBottoms();
			for (int i = 0; i < current.length; i++)
				if (current[i] > 0) {
					double savedBottom = stored.bottomAt(i, Double.NaN);
					if (!Double.isFinite(savedBottom) || Math.abs(savedBottom - bottoms[i]) > 1e-4)
						return false;
				}
		}
		for (int i = 0; i < current.length; i++)
			if (Math.abs(heights.ypoints[i] - current[i]) > 1e-4)
				return false;
		return true;
	}

	private boolean store() {
		checkSources();
		if (preview == null)
			return false;
		computedCapillary.getCorrelationGulps().set(candidateAmplitudes(), 0, 0, value(), manual);
		storeGeometry(computedCapillary);
		computedCapillary.getCorrelationGulps().setMethodOptions(
				methodOptions() + ",scale=" + (localEvaporation.isSelected() ? preview.localScale.factor : 1));
		boolean ok = experiment.save_capillaries_description_and_measures();
		if (!ok)
			JOptionPane.showMessageDialog(this, "Could not save the capillary measures.", "Correlation gulps",
					JOptionPane.ERROR_MESSAGE);
		refresh();
		return ok;
	}

	private void copyToGulps() {
		checkSources();
		if (preview == null)
			return;
		Capillary cap = computedCapillary;
		CorrelationGulpPreview confirmedPreview = preview;
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
		if (preview != confirmedPreview || !confirmedPreview.matchesSources(experiment)) {
			checkSources();
			return;
		}
		cap.getCorrelationGulps().set(candidateAmplitudes(), 0, 0, value(), manual);
		storeGeometry(cap);
		cap.getCorrelationGulps().setMethodOptions(
				methodOptions() + ",scale=" + (localEvaporation.isSelected() ? preview.localScale.factor : 1));
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
		if (!user && !automaticMode())
			manualThresholds.remove(computedCapillary);
		refresh();
	}

	private List<IntakeDiagnostics.Warning> diagnostics() {
		IntakeEpisodes.Detection detection = episodeDetection();
		boolean noFly = computedCapillary.getNFlies() == 0;
		if (diagnosticDetection != detection || cachedDiagnostics == null || diagnosticNoFly != noFly) {
			cachedDiagnostics = IntakeDiagnostics.analyze(preview.raw, preview.correctedLevels, preview.evaporation,
					preview.minutes, preview.motion.reliable, detection, preview.controlRates, value(), noFly);
			diagnosticDetection = detection;
			diagnosticNoFly = noFly;
		}
		return cachedDiagnostics;
	}

	private Color diagnosticColor(IntakeDiagnostics.Warning warning) {
		return warning.kind == IntakeDiagnostics.Kind.STEP && warning.excess > 0 ? new Color(210, 135, 0)
				: warning.kind == IntakeDiagnostics.Kind.DRIFT ? new Color(0, 140, 155) : new Color(165, 65, 180);
	}

	private void reviewInterval() {
		if (preview == null)
			return;
		if (reviewDialog != null)
			reviewDialog.dispose();
		reviewDialog = new JDialog(SwingUtilities.getWindowAncestor(this),
				"Review " + computedCapillary.getLast2ofCapillaryName() + " - snapshot, estimates only",
				Dialog.ModalityType.MODELESS);
		reviewDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		JPanel content = new JPanel(new BorderLayout(4, 4));
		content.add(reviewPanel(), BorderLayout.CENTER);
		JButton close = new JButton("Close");
		close.addActionListener(e -> reviewDialog.dispose());
		content.add(close, BorderLayout.SOUTH);
		reviewDialog.setContentPane(content);
		reviewDialog.setSize(800, 550);
		reviewDialog.setLocationRelativeTo(this);
		reviewDialog.setVisible(true);
	}

	private JPanel reviewPanel() {
		CorrelationGulpPreview reviewed = preview;
		IntakeEpisodes.Detection reviewedDetection = episodeDetection();
		boolean reviewedControl = computedCapillary.getNFlies() == 0;
		int n = reviewed.raw.length;
		int first = Math.max(1, selectedFrame), last = Math.min(n - 1, first + 2);
		List<IntakeDiagnostics.Warning> warnings = diagnostics();
		IntakeDiagnostics.Warning nearest = null;
		int distance = Integer.MAX_VALUE;
		for (IntakeDiagnostics.Warning warning : warnings) {
			int delta = Math.max(0, Math.max(warning.first - selectedFrame, selectedFrame - warning.last));
			if (delta < distance) {
				distance = delta;
				nearest = warning;
			}
		}
		if (nearest != null) {
			first = Math.max(1, nearest.first);
			last = Math.max(first, nearest.last);
		}
		JSpinner from = new JSpinner(new SpinnerNumberModel(first, 1, n - 1, 1));
		JSpinner to = new JSpinner(new SpinnerNumberModel(last, 1, n - 1, 1));
		javax.swing.JTextArea details = new javax.swing.JTextArea(14, 65);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		Runnable estimate = () -> {
			int a = ((Number) from.getValue()).intValue(), b = ((Number) to.getValue()).intValue();
			try {
				IntakeDiagnostics.Estimate result = IntakeDiagnostics.estimate(reviewed.correctedLevels,
						reviewed.evaporation, reviewed.minutes, reviewed.motion.reliable, a, b);
				double alreadyDetected = 0;
				for (int i = a; i <= b; i++)
					alreadyDetected += reviewedDetection.net[i];
				StringBuilder text = new StringBuilder();
				if (reviewedControl)
					text.append("NO-FLY CONTROL: excess loss is not labelled feeding.\n\n");
				text.append("Interval: frames ").append(a).append(" to ").append(b)
						.append(" (bin i represents the transition i-1 to i).\n").append("Before position: ")
						.append(format(result.before)).append(" px; after: ").append(format(result.after))
						.append(" px\nExpected evaporation: ").append(format(result.evaporation))
						.append(" px\nNet persistent displacement: ").append(format(result.net))
						.append(result.reliable ? " px\n\n" : " px (UNCERTAIN flanks)\n\n")
						.append(result.localReliable ? "Alternative using local flanking slopes: "
								: "UNCERTAIN alternative using local flanks: ")
						.append(format(result.localNet)).append(" px; local baseline rate ")
						.append(format(result.localRate)).append(" px/min. ")
						.append(result.localReliable ? "Flanking slopes are consistent."
								: "UNCERTAIN: local slopes are poorly supported or inconsistent.")
						.append("\n\nAlready detected net in interval: ").append(format(alreadyDetected))
						.append(" px\n").append("Unassigned difference: active-model estimate ")
						.append(format(result.net - alreadyDetected)).append(" px; local-flank alternative ")
						.append(format(result.localNet - alreadyDetected)).append(" px\n\n")
						.append("Flanking plateau samples: ").append(result.beforeSamples).append(" before, ")
						.append(result.afterSamples).append(" after. ")
						.append(result.reliable
								? "Flanking positions have usable level support (see source quality status)."
								: "UNCERTAIN: insufficient reliable flanking positions.")
						.append("\n\nEstimate only: no events, saved measures or consumption totals are changed. ")
						.append("Choose boundaries on stable meniscus stretches using the original image; neighbouring feeding, low contrast or evaporation mismatch can bias this estimate.\n\n");
				for (IntakeDiagnostics.Warning warning : warnings)
					if (warning.last >= a - 1 && warning.first <= b)
						text.append(warning.label()).append("; frames ").append(warning.first).append('-')
								.append(warning.last).append("; displacement ").append(format(warning.excess))
								.append(" px\n");
				details.setText(text.toString());
				details.setCaretPosition(0);
			} catch (IllegalArgumentException error) {
				details.setText(error.getMessage());
			}
		};
		from.addChangeListener(e -> estimate.run());
		to.addChangeListener(e -> estimate.run());
		JPanel bounds = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bounds.add(new JLabel("First interval bin"));
		bounds.add(from);
		bounds.add(new JLabel("Last interval bin"));
		bounds.add(to);
		JPanel selection = new JPanel(new java.awt.GridLayout(0, 1));
		javax.swing.JComboBox<String> warningChoice = new javax.swing.JComboBox<>();
		warningChoice.addItem("Manual interval");
		for (IntakeDiagnostics.Warning warning : warnings)
			warningChoice.addItem(warning.label() + " [frames " + warning.first + "-" + warning.last + "]");
		warningChoice.addActionListener(e -> {
			int index = warningChoice.getSelectedIndex() - 1;
			if (index >= 0) {
				IntakeDiagnostics.Warning warning = warnings.get(index);
				from.setValue(Math.max(1, warning.first));
				to.setValue(Math.max(1, warning.last));
			}
		});
		if (nearest != null)
			warningChoice.setSelectedIndex(warnings.indexOf(nearest) + 1);
		selection.add(warningChoice);
		selection.add(bounds);
		JPanel content = new JPanel(new BorderLayout());
		content.add(selection, BorderLayout.NORTH);
		content.add(new javax.swing.JScrollPane(details), BorderLayout.CENTER);
		estimate.run();
		return content;
	}

	private String methodOptions() {
		return IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + persistentEpisodes.isSelected() + ",local="
				+ localEvaporation.isSelected() + manualEdits().options();
	}

	private ManualIntakeEdits manualEdits() {
		if (automaticMode() || computedCapillary == null) return new ManualIntakeEdits();
		return manualGulps.computeIfAbsent(computedCapillary, cap -> new ManualIntakeEdits());
	}

	private void editGulp(int first, int last) {
		if (!addGulp.isEnabled() || !addGulp.isSelected() || preview == null) return;
		checkSources();
		if (preview == null) return;
		first = Math.max(1, first);
		last = Math.max(first, Math.min(preview.raw.length - 1, last));
		final CorrelationGulpPreview edited = preview;
		final Capillary cap = computedCapillary;
		double bottom = candidateBottoms()[last];
		if (!Double.isFinite(bottom)) bottom = preview.image.getHeight() / 2.;
		double binEvaporation = preview.evaporation[last] - preview.evaporation[last - 1];
		double amount = preview.raw[last] - preview.raw[first - 1]
				- preview.evaporation[last] + preview.evaporation[first - 1];
		if (first == last && candidateAmplitudes()[last] > 0)
			amount = Math.max(0, candidateAmplitudes()[last] - binEvaporation);
		if (!Double.isFinite(amount) || amount <= 0) amount = 1;
		JSpinner amplitude = new JSpinner(new SpinnerNumberModel(amount, 0., Double.MAX_VALUE, .1));
		JSpinner position = new JSpinner(new SpinnerNumberModel(bottom, -Double.MAX_VALUE, Double.MAX_VALUE, .1));
		JPanel fields = manualGulpEditor(edited, first, last, amplitude, position);
		Object[] actions = { "OK", "Delete intake event", "Cancel" };
		int action = JOptionPane.showOptionDialog(this, fields, "Manual gulp (evaporation subtracted)",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
		if (action != 0 && action != 1) return;
		boolean delete = action == 1;
		try {
			if (!delete) {
				amplitude.commitEdit(); position.commitEdit();
			}
			if (preview != edited || computedCapillary != cap || !edited.matchesSources(experiment)) {
				checkSources(); return;
			}
			double netAmount = delete ? 0 : ((Number) amplitude.getValue()).doubleValue();
			manualEdits().replace(first, last, netAmount > 0 ? netAmount + binEvaporation : 0,
					delete ? bottom : ((Number) position.getValue()).doubleValue());
			invalidateBatch();
			selectedFrame = last;
			refresh();
		} catch (java.text.ParseException | IllegalArgumentException error) {
			JOptionPane.showMessageDialog(this, error.getMessage(), "Invalid manual gulp", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static JPanel manualGulpFields(int first, int last, JSpinner amplitude, JSpinner position) {
		// NumberEditor otherwise reserves enough columns for Double.MAX_VALUE.
		// Limit the visible width, without restricting the values users can enter.
		((JSpinner.NumberEditor) amplitude.getEditor()).getTextField().setColumns(10);
		((JSpinner.NumberEditor) position.getEditor()).getTextField().setColumns(10);
		JPanel fields = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
		fields.add(new JLabel("Selected bins")); fields.add(new JLabel(first + " - " + last));
		fields.add(new JLabel("Net intake (px; 0 removes)")); fields.add(amplitude);
		fields.add(new JLabel("Bottom meniscus position (px)")); fields.add(position);
		fields.add(new JLabel("Replaces intake in this region")); fields.add(new JLabel("One gulp at bin " + last));
		return fields;
	}

	private static JPanel manualGulpEditor(CorrelationGulpPreview preview, int first, int last,
			JSpinner amplitude, JSpinner position) {
		JPanel editor = new JPanel(new BorderLayout(4, 6));
		editor.add(manualGulpFields(first, last, amplitude, position), BorderLayout.NORTH);
		ManualGulpZoom image = new ManualGulpZoom(preview, first, last, amplitude, position);
		JScrollPane scroll = new JScrollPane(image);
		scroll.setPreferredSize(new Dimension(560, 260));
		scroll.getVerticalScrollBar().setUnitIncrement(12);
		scroll.getHorizontalScrollBar().setUnitIncrement(12);
		editor.add(scroll, BorderLayout.CENTER);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		controls.add(new JLabel("Zoom"));
		JComboBox<Integer> zoom = new JComboBox<>(new Integer[] { 4, 8, 12, 16, 24 });
		zoom.setName("manualIntakeZoomScale");
		zoom.setSelectedItem(12);
		zoom.addActionListener(e -> {
			image.scale = (Integer) zoom.getSelectedItem();
			image.revalidate(); image.repaint();
			SwingUtilities.invokeLater(image::revealLine);
		});
		controls.add(zoom);
		controls.add(new JLabel("Blue: intake. Orange: saved level. Drag blue endpoints."));
		editor.add(controls, BorderLayout.SOUTH);
		amplitude.addChangeListener(e -> image.repaint());
		position.addChangeListener(e -> image.repaint());
		SwingUtilities.invokeLater(image::revealLine);
		return editor;
	}

	/** Live geometry preview only; OK installs edits, Cancel leaves measures untouched. */
	private static final class ManualGulpZoom extends JPanel {
		private static final long serialVersionUID = 1L;
		private static final int MARGIN = 12;
		private final CorrelationGulpPreview preview;
		private final int left, right, last;
		private final JSpinner amplitude, position;
		private int scale = 12;
		private int dragging;
		private double fixedTop;

		ManualGulpZoom(CorrelationGulpPreview preview, int first, int last, JSpinner amplitude, JSpinner position) {
			this.preview = preview; this.last = last;
			this.amplitude = amplitude; this.position = position;
			left = Math.max(0, first - 8); right = Math.min(preview.image.getWidth(), last + 9);
			setName("manualIntakeZoom");
			setBackground(Color.DARK_GRAY);
			setToolTipText("Drag the upper or lower blue endpoint to adjust intake; scroll for surrounding image pixels.");
			MouseAdapter mouse = new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) {
					if (!SwingUtilities.isLeftMouseButton(e) || Math.abs(e.getX() - lineX()) > 10) return;
					double bottom = number(position), top = bottom - fullAmplitude();
					int topDistance = Math.abs(e.getY() - y(top)), bottomDistance = Math.abs(e.getY() - y(bottom));
					if (topDistance <= 9 && topDistance <= bottomDistance) dragging = 1;
					else if (bottomDistance <= 9) { dragging = 2; fixedTop = top; }
				}
				@Override public void mouseDragged(MouseEvent e) {
					if (dragging == 0) return;
					double row = Math.max(0, Math.min(preview.image.getHeight() - 1, (e.getY() - MARGIN) / (double) scale));
					if (dragging == 1)
						amplitude.setValue(Math.max(0, number(position) - row - evaporation()));
					else {
						position.setValue(row);
						amplitude.setValue(Math.max(0, row - fixedTop - evaporation()));
					}
				}
				@Override public void mouseReleased(MouseEvent e) { dragging = 0; }
			};
			addMouseListener(mouse); addMouseMotionListener(mouse);
		}

		private static double number(JSpinner spinner) { return ((Number) spinner.getValue()).doubleValue(); }
		private double evaporation() { return preview.evaporation[last] - preview.evaporation[last - 1]; }
		private double fullAmplitude() { return number(amplitude) > 0 ? number(amplitude) + evaporation() : 0; }
		private int lineX() { return MARGIN + (int) Math.round((last - left + .5) * scale); }
		private int y(double row) { return MARGIN + (int) Math.round(row * scale); }
		@Override public Dimension getPreferredSize() {
			return new Dimension((right - left) * scale + 2 * MARGIN, preview.image.getHeight() * scale + 2 * MARGIN);
		}
		void revealLine() {
			int center = y(number(position) - fullAmplitude() / 2);
			Rectangle visible = getVisibleRect();
			scrollRectToVisible(new Rectangle(Math.max(0, lineX() - visible.width / 2),
					Math.max(0, center - visible.height / 2), Math.max(1, visible.width), Math.max(1, visible.height)));
		}
		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(preview.image, MARGIN, MARGIN, MARGIN + (right - left) * scale,
					MARGIN + preview.image.getHeight() * scale, left, 0, right, preview.image.getHeight(), null);
			g.setStroke(new BasicStroke(1)); g.setColor(Color.ORANGE);
			for (int i = left + 1; i < right; i++)
				if (Double.isFinite(preview.raw[i - 1]) && Double.isFinite(preview.raw[i]))
					g.drawLine(MARGIN + (int) ((i - left - .5) * scale), y(preview.raw[i - 1]),
							MARGIN + (int) ((i - left + .5) * scale), y(preview.raw[i]));
			int x = lineX(), bottom = y(number(position)), top = y(number(position) - fullAmplitude());
			g.setColor(CapillaryCorrelationGulps.ROI_COLOR); g.setStroke(new BasicStroke(3));
			g.drawLine(x, bottom, x, top);
			g.fillOval(x - 5, top - 5, 10, 10); g.fillOval(x - 5, bottom - 5, 10, 10);
			g.dispose();
		}
	}

	private IntakeEpisodes.Detection episodeDetection() {
		if (episodePreview != preview || episodeThreshold != value() || cachedEpisodes == null) {
			cachedEpisodes = IntakeEpisodes.detect(preview.residualRate, preview.minutes, preview.motion.reliable,
					preview.controlRates, preview.noise.automatic, value(), preview.correctedLevels,
					preview::supportsIntakeStep);
			episodePreview = preview;
			episodeThreshold = value();
		}
		return cachedEpisodes;
	}

	private boolean[] candidates() {
		double[] amplitudes = candidateAmplitudes();
		boolean[] selected = new boolean[amplitudes.length];
		for (int i = 1; i < amplitudes.length; i++) selected[i] = amplitudes[i] > 0;
		return selected;
	}

	private void refresh() {
		updateDetectorLabel();
		if (preview != null) {
			int count = 0, good = 0;
			for (boolean v : candidates())
				if (v)
					count++;
			for (boolean v : preview.motion.reliable)
				if (v)
					good++;
			String auto = Double.isFinite(preview.noise.automatic) ? format(preview.noise.automatic) : "unavailable";
			source.setText("<html>" + preview.referenceDescription.replace("; border:", "<br>Border:")
					+ "<br>Initial threshold: " + auto + " px/min | "
					+ preview.noise.source.replace("Empty-capillary", "No-fly control")
							.replace("empty-capillary", "no-fly control")
					+ " | quiet intervals: " + preview.noise.quietSamples
					+ (persistentEpisodes.isSelected()
							? "<br>" + (diagnostics().isEmpty() ? "No diagnostic above current tolerances"
									: diagnostics().stream().limit(2)
											.map(w -> w.label() + " [" + w.first + "-" + w.last + "]")
											.collect(java.util.stream.Collectors.joining("; ")))
							: "")
					+ "</html>");
			CapillaryCorrelationGulps stored = computedCapillary.getCorrelationGulps();
			String storage = isStoredCurrent() ? "saved"
					: stored.hasMeasures()
							? "not saved (stored: " + stored.countEvents() + " events, window "
									+ stored.getWindowRadius() + ", max shift " + format(stored.getMaxShiftPx())
									+ ", threshold " + format(stored.getThresholdPxPerMin()) + ")"
							: "not saved";
			double net = 0;
			double[] amplitudes = candidateAmplitudes();
			for (int i = 1; i < amplitudes.length; i++)
				if (amplitudes[i] > 0)
					net += Math.max(0, amplitudes[i] - preview.evaporation[i] + preview.evaporation[i - 1]);
			double observed = preview.raw[preview.raw.length - 1] - preview.raw[0]
					- preview.evaporation[preview.evaporation.length - 1] + preview.evaporation[0];
			status.setText("<html>"
					+ (persistentEpisodes.isSelected() ? episodeDetection().episodes + " automatic episodes / " + count + " intake bins (including overrides)" : count + " bins")
					+ (persistentEpisodes.isSelected() ? " / " + diagnostics().size() + " diagnostics" : "")
					+ " | net " + format(net) + " px; raw minus evaporation " + format(observed) + " px"
					+ (Math.abs(net - observed) > Math.max(3, .2 * Math.abs(observed))
							? " | cumulative mismatch: inspect tracking/evaporation"
							: "")
					+ "<br>" + good + "/" + (preview.raw.length - 1) + " usable level increments | threshold "
					+ format(value()) + " px/min (" + (manual ? "manual" : "automatic") + ") | time "
					+ format(preview.minutes[selectedFrame]) + " min | method " + IntakeEpisodes.METHOD_VERSION
					+ " | bin " + selectedFrame + ": " + preview.motion.reason[selectedFrame]
					+ (!manualEdits().isEmpty() ? " | manual gulp overrides active" : "")
					+ (persistentEpisodes.isSelected() ? " / persistent episodes" : " / single-bin (persistence off)")
					+ " | " + storage + "</html>");
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
		if (!released && !suspended) roiTimer.restart();
	}

	/**
	 * Live candidates replace the stored correlation gulps of the displayed
	 * capillary; closing restores them.
	 */
	private void updateRois() {
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq == null)
			return;
		boolean inactive = released || suspended;
		Capillary cap = inactive ? null : (Capillary) capillaries.getSelectedItem();
		boolean show = showRois.isSelected();
		boolean[] chosen = !inactive && show && preview != null && cap == computedCapillary ? candidates() : null;
		boolean stored = inactive || (show && chosen == null);
		double[] heights = chosen == null ? null : candidateAmplitudes();
		double[] bottoms = chosen == null ? null : candidateBottoms();
		if (!inactive && cap == roiCapillary && stored == roiStored
				&& roiCompensated == compensateEvaporation.isSelected() && Arrays.equals(chosen, roiCandidates)
				&& roiShowGaps == showGaps.isSelected() && Arrays.equals(bottoms, roiBottoms)
				&& Arrays.equals(heights, roiAmplitudes) && (chosen == null || roiPreview == preview))
			return;
		List<Integer> frames = new ArrayList<>();
		if (roiCapillary != null)
			frames.add(roiCapillary.getKymographIndex());
		if (cap != null)
			frames.add(cap.getKymographIndex());
		List<ROI> old = new ArrayList<>();
		for (ROI2D roi : seq.getROI2Ds())
			if (CapillaryCorrelationGulps.isOverlay(roi) && frames.contains(roi.getT()) && !"true"
					.equals(roi.getProperty(plugins.fmp.multitools.service.MeniscusRefinementPreview.ROI_PROPERTY)))
				old.add(roi);
		List<ROI2D> rois = new ArrayList<>();
		if (chosen != null) {
			int t = cap.getKymographIndex();
			for (int i = 1; i < chosen.length; i++)
				if (chosen[i] && Double.isFinite(bottoms[i]))
					rois.add(CapillaryCorrelationGulps.createRoi(cap.getKymographPrefix(), i, bottoms[i], heights[i],
							t));
			if (preview.hasEvaporationReference)
				rois.addAll(IntakeEvaporationOverlay.createRois(cap.getKymographPrefix(), candidateAmplitudes(),
						preview.raw, preview.evaporation, t, compensateEvaporation.isSelected()));
		} else if (stored) {
			Capillary owner = inactive ? roiCapillary : cap;
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
		roiAmplitudes = heights;
		roiBottoms = bottoms;
		roiShowGaps = showGaps.isSelected();
		roiStored = stored;
		roiCompensated = compensateEvaporation.isSelected();
		roiPreview = chosen == null ? null : preview;
	}

	private void exportExperimentReview() {
		if (preview == null || reviewExportWorker != null) return;
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("intake-experiment-review.zip"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		Path file = chooser.getSelectedFile().toPath();
		if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file.getFileName() + "?",
				"Export experiment review", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
		ReviewExport snapshot = new ReviewExport(this);
		reviewExportWorker = new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception { snapshot.write(file); return null; }
			@Override protected void done() {
				reviewExportWorker = null;
				if (released) return;
				updateModeControls();
				checkSources();
				try {
					get();
					JOptionPane.showMessageDialog(CorrelationIntakePanel.this,
							"Exported all " + snapshot.caps.size() + " capillaries, including retained manual edits.\n" + file,
							"Experiment review exported", JOptionPane.INFORMATION_MESSAGE);
				} catch (InterruptedException | ExecutionException | CancellationException error) {
					if (error instanceof InterruptedException) Thread.currentThread().interrupt();
					Throwable cause = error.getCause() == null ? error : error.getCause();
					JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		updateModeControls();
		status.setText("Exporting review CSVs and kymographs for every capillary...");
		reviewExportWorker.execute();
	}

	/** Capture UI settings and unsaved edits on the EDT; write without changing selection or measures. */
	private static final class ReviewExport {
		final Experiment exp;
		final List<Capillary> caps;
		final boolean episodes, local;
		final Map<Capillary, Double> thresholds = new IdentityHashMap<>();
		final Map<Capillary, String> edits = new IdentityHashMap<>();
		final Map<Capillary, CorrelationGulpPreview> previews;
		ReviewExport(CorrelationIntakePanel panel) {
			exp = panel.experiment;
			caps = new ArrayList<>(exp.getCapillaries().getList());
			episodes = panel.persistentEpisodes.isSelected(); local = panel.localEvaporation.isSelected();
			previews = new IdentityHashMap<>(panel.cache);
			for (Capillary cap : caps) {
				CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
				Double setting = panel.manualThresholds.get(cap);
				if (setting == null && stored.isManualThreshold() && stored.hasMeasures())
					setting = stored.getThresholdPxPerMin();
				if (cap == panel.computedCapillary && !panel.automaticMode()) setting = panel.value();
				if (setting != null && Double.isFinite(setting)) thresholds.put(cap, setting);
				edits.put(cap, panel.manualGulps.containsKey(cap) ? panel.manualGulps.get(cap).options() : stored.getMethodOptions());
			}
		}
		void write(Path destination) throws IOException {
			Path temp = Files.createTempFile(destination.toAbsolutePath().getParent(), "intake-review-", ".zip");
			try {
				try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(temp))) {
					StringBuilder summary = new StringBuilder("Intake review: " + exp.getResultsDirectory()
							+ "\nAll capillaries; current detector settings; includes unsaved manual event edits."
							+ "\nEach CSV compares automatic, chosen-threshold and edited intake. PNGs are original kymographs."
							+ "\nUnchanged bins are not assumed reviewed. No experiment measures were saved by this export.\n\n");
					for (Capillary cap : caps) {
						CorrelationGulpPreview p = previews.get(cap);
						if (p == null || p.localEvaporation != local || !p.matchesSources(exp))
							p = CorrelationGulpPreview.computeAccepted(exp, cap, local);
						previews.put(cap, p);
						double setting = thresholds.containsKey(cap) ? thresholds.get(cap)
								: Double.isFinite(p.noise.automatic) ? p.noise.automatic : 1;
						ManualIntakeEdits manual = ManualIntakeEdits.fromOptions(edits.get(cap), p.raw.length);
						String id = cap.getLast2ofCapillaryName().replaceAll("[^A-Za-z0-9_-]", "_");
						java.io.StringWriter text = new java.io.StringWriter();
						java.io.BufferedWriter writer = new java.io.BufferedWriter(text);
						writeReviewCsv(writer, exp, cap, p, setting, thresholds.containsKey(cap), episodes, local, manual);
						writer.flush();
						zip.putNextEntry(new java.util.zip.ZipEntry("correlation-" + id + ".csv"));
						zip.write(text.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
						zip.putNextEntry(new java.util.zip.ZipEntry("kymograph-" + id + ".png"));
						javax.imageio.ImageIO.write(p.image, "png", zip); zip.closeEntry();
						summary.append(id).append(cap.getNFlies() == 0 ? " (no-fly control)" : "")
								.append(manual.isEmpty() ? ": no manual event overrides\n" : ": manual event overrides included\n");
					}
					for (CorrelationGulpPreview p : previews.values())
						if (!p.matchesSources(exp)) throw new IOException("Levels changed during export; export the review again.");
					zip.putNextEntry(new java.util.zip.ZipEntry("README.txt"));
					zip.write(summary.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
				}
				Files.move(temp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			} finally { Files.deleteIfExists(temp); }
		}
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
		if (preview == null || !preview.matchesSources(experiment))
			throw new IOException("Levels changed; recompute intake before export.");
		try (java.io.BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writeReviewCsv(writer, experiment, computedCapillary, preview, value(), manual,
					persistentEpisodes.isSelected(), localEvaporation.isSelected(), manualEdits());
		}
	}

	private static void writeReviewCsv(java.io.BufferedWriter writer, Experiment experiment,
			Capillary computedCapillary, CorrelationGulpPreview preview, double threshold, boolean manual,
			boolean episodes, boolean local, ManualIntakeEdits edits) throws IOException {
		AutomaticResult detected = new AutomaticResult(preview, episodes, local, threshold);
		double[] beforeEdits = detected.amplitudes;
		double[] amplitudes = beforeEdits.clone();
		double[] bottoms = detected.bottoms == null ? preview.raw.clone() : detected.bottoms.clone();
		edits.apply(amplitudes, bottoms);
		boolean[] selected = new boolean[amplitudes.length];
		for (int i = 1; i < selected.length; i++) selected[i] = amplitudes[i] > 0;
		List<IntakeDiagnostics.Warning> warnings = episodes ? IntakeDiagnostics.analyze(preview.raw,
				preview.correctedLevels, preview.evaporation, preview.minutes, preview.motion.reliable,
				IntakeEpisodes.detect(preview.residualRate, preview.minutes, preview.motion.reliable,
						preview.controlRates, preview.noise.automatic, threshold, preview.correctedLevels, preview::supportsIntakeStep),
				preview.controlRates, threshold, computedCapillary.getNFlies() == 0) : java.util.Collections.emptyList();
		double[] automaticAmplitudes = Double.isFinite(preview.noise.automatic)
				? new AutomaticResult(preview, episodes, local).amplitudes : null;
			writer.write("# prototype=accepted_level_intake;capillary=" + computedCapillary.getLast2ofCapillaryName()
					+ ";source=accepted_topraw\n");
			writer.write("# experiment=" + experiment.getResultsDirectory() + ";reference="
					+ preview.referenceDescription + ";method_options=" + IntakeEpisodes.METHOD_VERSION
					+ ",source=accepted_topraw,persistent=" + episodes + ",local=" + local + edits.options() + "\n");
			writer.write("# threshold_source=" + preview.noise.source + ";manual=" + manual
					+ ";automatic_threshold_px_per_min=" + preview.noise.automatic + "\n");
			writer.write("# comparison=detector recomputed from current accepted levels and method settings;"
					+ "edit_action compares chosen threshold before/after event edits;"
					+ "automatic_net_intake_px uses automatic threshold (NaN if unavailable);"
					+ "unchanged does not imply user reviewed\n");
			writer.write(
					"frame;elapsed_min;raw_px;refined_px;shift_px;evaporation_increment_px;excess_px_per_min;correlation;reliable;reason;automatic_threshold_px_per_min;chosen_threshold_px_per_min;candidate;candidate_amplitude_px;searched_range_px;fitted_meniscus_px;intake_top_px;intake_bottom_px;diagnostic_excess_px;diagnostic_reliable;diagnostic_types;image_profile;topraw_fallback;automatic_net_intake_px;detector_net_before_edits_px;edited_net_intake_px;manual_override;edit_action\n");
			for (int i = 0; i < preview.raw.length; i++) {
				double evap = i == 0 ? Double.NaN : preview.evaporation[i] - preview.evaporation[i - 1];
				String change = !edits.covers(i) ? "unchanged"
						: !(beforeEdits[i] > 0) && amplitudes[i] > 0 ? "added"
						: beforeEdits[i] > 0 && !(amplitudes[i] > 0) ? "removed"
						: Math.abs(amplitudes[i] - beforeEdits[i]) > 1e-4 ? "resized" : "manual_override";
				String gapAmount = "", gapReliable = "", diagnosticTypes = "";
				if (episodes)
					for (IntakeDiagnostics.Warning gap : warnings)
						if (gap.last == i) {
							String separator = diagnosticTypes.isEmpty() ? "" : "|";
							gapAmount += separator + gap.excess;
							gapReliable += separator + gap.reliable;
							diagnosticTypes += separator + (gap.noFly ? "CONTROL_" : "") + gap.kind.name();
						}
				writer.write(i + ";" + preview.minutes[i] + ";" + preview.raw[i] + ";" + preview.motion.refined[i] + ";"
						+ preview.motion.shift[i] + ";" + evap + ";" + preview.residualRate[i] + ";"
						+ preview.motion.correlation[i] + ";" + preview.motion.reliable[i] + ";"
						+ "\"" + preview.motion.reason[i].replace("\"", "\"\"") + "\";"
						+ preview.noise.automatic + ";" + threshold + ";" + selected[i]
						+ ";" + amplitudes[i] + ";" + preview.motion.searchRange[i] + ";" + bottoms[i] + ";"
						+ (selected[i] ? bottoms[i] - amplitudes[i] : Double.NaN) + ";"
						+ (selected[i] ? bottoms[i] : Double.NaN) + ";" + gapAmount + ";" + gapReliable + ";"
						+ diagnosticTypes + ";" + preview.motion.profile[i] + ";" + preview.motion.toprawFallback[i]
						+ ";" + (automaticAmplitudes == null ? Double.NaN : netIntake(automaticAmplitudes[i], evap))
						+ ";" + netIntake(beforeEdits[i], evap) + ";" + netIntake(amplitudes[i], evap)
						+ ";" + edits.covers(i) + ";" + change + "\n");
			}
	}

	private static double netIntake(double amplitude, double evaporation) {
		return amplitude > 0 ? Math.max(0, amplitude - evaporation) : 0;
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
		private int editStart = -1;

		MotionPlot() {
			setBackground(Color.WHITE);
			setPreferredSize(new Dimension(1050, 300));
			MouseAdapter mouse = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (preview == null)
						return;
					if (addGulp.isEnabled() && addGulp.isSelected() && SwingUtilities.isLeftMouseButton(e)
							&& e.getX() >= 65 && e.getX() <= getWidth() - 25) {
						editStart = frameAt(e.getX());
						return;
					}
					dragging = threshold.isEnabled() && Math.abs(e.getY() - y(value())) < 12;
					if (!dragging) {
						double time = preview.minutes[0] + (e.getX() - 65.) / Math.max(1, getWidth() - 90) * duration();
						selectedFrame = closest(time);
						refresh();
					}
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (preview != null && dragging && threshold.isEnabled())
						threshold.setValue(Math.max(0, Math.min(10000,
								high - (e.getY() - 35.) / Math.max(1, getHeight() - 85) * (high - low))));
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (editStart >= 0 && preview != null) {
						int end = frameAt(e.getX()), start = editStart;
						editStart = -1;
						editGulp(Math.min(start, end), Math.max(start, end));
					}
					editStart = -1;
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

		private int frameAt(int x) {
			return closest(preview.minutes[0] + (x - 65.) / Math.max(1, getWidth() - 90) * duration());
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
			g.drawString(persistentEpisodes.isSelected()
					? "Blue: motion minus evaporation; red: detected intake bins; line: threshold setting (controls and persistence also apply)"
					: "Blue: accepted-level motion minus evaporation; grey: evaporation model (pixels/min); red: candidate intervals",
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
			if (persistentEpisodes.isSelected() && showGaps.isSelected())
				for (IntakeDiagnostics.Warning warning : diagnostics()) {
					Color color = diagnosticColor(warning);
					g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
					g.fillRect(x(warning.first), 35, Math.max(2, x(warning.last) - x(warning.first)), getHeight() - 85);
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
					g.setColor(chosen[i] ? Color.RED : Color.GRAY);
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
			g.drawString((automaticMode() ? "Automatic setting: " : "Drag threshold setting: ") + format(value()),
					75, Math.max(35, y(value()) - 6));
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
			MouseAdapter mouse = new MouseAdapter() {
				private int start = -1;
				@Override public void mousePressed(MouseEvent e) {
					if (preview == null || !SwingUtilities.isLeftMouseButton(e)
							|| e.getX() < LEFT || e.getX() >= getWidth() - RIGHT || e.getY() < 25
							|| e.getY() >= 25 + (cropBounds()[1] - cropBounds()[0]) * COLUMN_PX) return;
					selectedFrame = frameAt(e.getX());
					if (addGulp.isEnabled() && addGulp.isSelected()) start = selectedFrame;
					refresh();
				}
				@Override public void mouseReleased(MouseEvent e) {
					if (start < 0 || preview == null) { start = -1; return; }
					int end = frameAt(e.getX()), first = start;
					start = -1;
					editGulp(Math.min(first, end), Math.max(first, end));
				}
			};
			addMouseListener(mouse);
		}

		private int frameAt(int x) {
			return Math.max(0, Math.min(preview.raw.length - 1,
					(int) ((x - LEFT) * (double) preview.image.getWidth() / imageWidth())));
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
			return new Dimension(LEFT + imageWidth + RIGHT, 25 + cropRows * COLUMN_PX + 36);
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
			double[] amplitudes = candidateAmplitudes(), bottoms = candidateBottoms();
			for (int i = 1; i < amplitudes.length; i++)
				if (amplitudes[i] > 0 && Double.isFinite(bottoms[i])) {
					min = Math.min(min, (int) Math.floor(bottoms[i] - amplitudes[i]) - 2);
					max = Math.max(max, (int) Math.ceil(bottoms[i]) + 3);
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
			boolean[] chosen = candidates();
			g.setColor(Color.DARK_GRAY);
			g.drawString("Kymograph: blue = intake; grey = evaporation + cumulative intake, anchored at t=0", LEFT, 18);
			g.drawImage(preview.image, LEFT, 25, LEFT + width, 25 + cropHeight, 0, crop[0], preview.image.getWidth(),
					crop[1], null);
			drawIntakeOverlay(g, chosen, crop, 25, cropHeight);
			g.setColor(Color.YELLOW);
			g.drawLine(xOf(selectedFrame), 25, xOf(selectedFrame), 25 + cropHeight);
			g.setColor(Color.DARK_GRAY);
			g.drawString(
					"Time axis: at least 4 pixels per column; scroll horizontally for longer kymographs. Grey columns: missing level or image border.",
					LEFT, getHeight() - 7);
		}

		private void drawIntakeOverlay(Graphics graphics, boolean[] chosen, int[] crop, int top, int height) {
			double[] amplitudes = candidateAmplitudes();
			double[] bottoms = candidateBottoms();
			Graphics2D g = (Graphics2D) graphics.create();
			g.clipRect(LEFT, top, imageWidth(), height);
			g.setColor(IntakeEvaporationOverlay.COLOR);
			g.setStroke(new BasicStroke(1));
			List<List<java.awt.geom.Point2D>> paths = preview.hasEvaporationReference
					? IntakeEvaporationOverlay.paths(candidateAmplitudes(), preview.raw, preview.evaporation,
							compensateEvaporation.isSelected())
					: java.util.Collections.emptyList();
			for (List<java.awt.geom.Point2D> points : paths) {
				java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
				for (int i = 0; i < points.size(); i++) {
					java.awt.geom.Point2D point = points.get(i);
					double x = LEFT + imageWidth() * point.getX() / preview.image.getWidth();
					double y = top + height * (point.getY() - crop[0]) / (crop[1] - crop[0]);
					if (i == 0)
						path.moveTo(x, y);
					else
						path.lineTo(x, y);
				}
				g.draw(path);
			}
			g.setColor(CapillaryCorrelationGulps.ROI_COLOR);
			g.setStroke(new BasicStroke(2));
			for (int i = 1; i < chosen.length; i++)
				if (chosen[i] && Double.isFinite(bottoms[i])) {
					double bottom = top + height * (bottoms[i] - crop[0]) / (crop[1] - crop[0]);
					double tip = bottom - height * amplitudes[i] / (crop[1] - crop[0]);
					g.draw(new java.awt.geom.Line2D.Double(xOf(i), bottom, xOf(i), tip));
				}
			g.dispose();
		}
	}

}
