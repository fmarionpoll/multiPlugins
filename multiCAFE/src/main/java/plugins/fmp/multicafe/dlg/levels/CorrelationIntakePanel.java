package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.RichTooltipLayoutGuard;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Inspect and edit intake for one capillary, or prepare automatic detection for
 * every capillary in the current experiment.
 */
public final class CorrelationIntakePanel extends JPanel implements IntakeChartHost {
	private static final long serialVersionUID = 1L;
	private final Experiment experiment;
	private JComboBox<?> experimentBrowser;

	private final JComboBox<String> seriesRange = new JComboBox<>(
			new String[] { "Current experiment", "Current experiment to end", "All experiments (0 to end)" });
	private final JButton runSeriesSave = new JButton("Detect & save");
	private final JButton runSeries = new JButton("Detect, copy to gulps & save"), stopSeries = new JButton("Stop");
	private final JLabel seriesStatus = new JLabel("Uses automatic thresholds for every capillary.");
	private final JPanel seriesControls = new JPanel(new BorderLayout());
	private SwingWorker<Integer, String> seriesWorker;
	private volatile boolean seriesStopping;
	private volatile int seriesSaved;
	private boolean browserWasEnabled;

	private final JButton detectAll = new JButton("Detect all"), saveAll = new JButton("Save all intake"),
			cancelAll = new JButton("Cancel");
	private final JButton copyAllToGulps = new JButton("Copy all to gulps...");
	private final JLabel batchStatus = new JLabel(" ");
	private final JPanel batchControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private SwingWorker<Map<Capillary, AutomaticIntakeResult>, String> batchWorker;
	private Map<Capillary, AutomaticIntakeResult> batchResults = new IdentityHashMap<>();

	private final JComboBox<Capillary> capillaries = new JComboBox<>();
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");
	private final JSpinner threshold = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 10000.0, .05));
	private final JButton automatic = new JButton("Automatic"), export = new JButton("Export CSV...");
	private final JButton review = new JButton("Review interval...");
	private final JButton exportExperiment = new JButton("Export experiment review...");
	private final JButton saveAllEdited = new JButton("Save all edited intake");
	private final JButton copyAllEdited = new JButton("Copy all edited to gulps...");
	private final JButton save = new JButton("Save intake"), copyToGulps = new JButton("Copy to gulps...");
	private final JCheckBox addGulp = new JCheckBox("Add/edit gulp");
	private final JButton resetManualGulps = new JButton("Reset manual gulps");
	private double manualGulpContextMinutes = 8;
	private final Map<Capillary, ManualIntakeEdits> manualGulps = new IdentityHashMap<>();
	private final Map<Capillary, Double> manualThresholds = new IdentityHashMap<>();
	private SwingWorker<Void, String> editedPersistWorker;

	private final JCheckBox persistentEpisodes = new JCheckBox("Persistent episodes (1-6 bins)", true);
	private final JCheckBox localEvaporation = new JCheckBox("Local evaporation", false);
	private final JCheckBox showGaps = new JCheckBox("Show diagnostics", true);
	private final JCheckBox showRois = new JCheckBox("Show on kymograph", true);
	private final JCheckBox compensateEvaporation = new JCheckBox("Subtract evaporation", true);
	private final JLabel detectorVersion = new JLabel();
	private final IntakeMotionPlot plot = new IntakeMotionPlot(this);
	private final IntakeAlignmentView alignment = new IntakeAlignmentView(this);
	private SwingWorker<Void, Void> reviewExportWorker;

	private final JLabel source = new JLabel(" "),
			status = new JLabel("Select a capillary to analyze its accepted topraw levels.");
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

	void followExperimentBrowser(JComboBox<?> browser) {
		experimentBrowser = browser;
	}

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
		dialog.setSize(1180, 980);
		dialog.setMinimumSize(new Dimension(920, 780));
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
				|| first.getCorrelationGulps().getMethodOptions()
						.startsWith(IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw"))) {
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
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));
		previousButton.setMargin(new Insets(0, 0, 0, 0));
		nextButton.setMargin(new Insets(0, 0, 0, 0));
		detectorVersion.setToolTipText("Persistent episodes uses the " + IntakeEpisodes.METHOD_VERSION
				+ " plateau checks and image-confirmed local step recovery. With persistence off, detection uses only the single-bin threshold rule.");
		JPanel capillaryRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		capillaryRow.setBorder(BorderFactory.createTitledBorder("Capillary"));
		capillaryRow.add(previousButton);
		capillaryRow.add(capillaries);
		capillaryRow.add(nextButton);
		capillaryRow.add(detectorVersion);
		JPanel detectionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		detectionRow.setBorder(BorderFactory.createTitledBorder("Detection"));
		detectionRow.add(persistentEpisodes);
		detectionRow.add(localEvaporation);
		JPanel displayRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		displayRow.setBorder(BorderFactory.createTitledBorder("Display"));
		displayRow.add(showGaps);
		displayRow.add(showRois);
		displayRow.add(compensateEvaporation);
		JPanel capillaryActions = new JPanel();
		capillaryActions.setLayout(new BoxLayout(capillaryActions, BoxLayout.Y_AXIS));
		capillaryActions.setBorder(BorderFactory.createTitledBorder("This capillary"));
		JPanel thresholdControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		thresholdControls.add(new JLabel("Threshold (pixels/min)"));
		thresholdControls.add(threshold);
		thresholdControls.add(automatic);
		thresholdControls.add(save);
		thresholdControls.add(copyToGulps);
		thresholdControls.add(review);
		thresholdControls.add(export);
		JPanel editControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		editControls.add(addGulp);
		editControls.add(resetManualGulps);
		editControls.add(saveAllEdited);
		editControls.add(copyAllEdited);
		editControls.add(exportExperiment);
		capillaryActions.add(thresholdControls);
		capillaryActions.add(editControls);
		batchControls.setBorder(BorderFactory.createTitledBorder("This experiment (automatic thresholds)"));
		batchControls.add(detectAll);
		batchControls.add(saveAll);
		batchControls.add(copyAllToGulps);
		batchControls.add(cancelAll);
		batchControls.add(batchStatus);
		JPanel seriesActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		seriesActions.add(seriesRange);
		seriesActions.add(runSeriesSave);
		seriesActions.add(runSeries);
		seriesActions.add(stopSeries);
		seriesControls.setBorder(BorderFactory.createTitledBorder("Experiment range (automatic thresholds)"));
		seriesControls.add(seriesActions, BorderLayout.NORTH);
		seriesControls.add(seriesStatus, BorderLayout.SOUTH);
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.add(capillaryRow);
		rows.add(detectionRow);
		rows.add(displayRow);
		rows.add(capillaryActions);
		rows.add(batchControls);
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
		runSeriesSave.addActionListener(e -> startSeries(false));
		runSeries.addActionListener(e -> startSeries(true));
		stopSeries.addActionListener(e -> {
			seriesStopping = true;
			stopSeries.setEnabled(false);
			seriesStatus.setText("Stopping; any save already in progress will finish.");
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
		exportExperiment.setToolTipText(
				"Export one ZIP with before/after CSVs and kymographs for every capillary, including unsaved manual gulp edits.");
		saveAllEdited.addActionListener(e -> persistEditedIntake(false));
		saveAllEdited.setName("saveAllEditedIntake");
		saveAllEdited.setToolTipText(
				"Save correlation intake for every capillary, using each capillary's threshold and manual edits. Ordinary gulps stay unchanged.");
		copyAllEdited.addActionListener(e -> persistEditedIntake(true));
		copyAllEdited.setName("copyAllEditedToGulps");
		copyAllEdited.setToolTipText(
				"Save that edited intake, then replace ordinary gulps for every capillary. Existing gulp measures are replaced.");
		export.setText("Export selected CSV...");
		export.setToolTipText(
				"Export one CSV with automatic detection, current detector settings, edited intake and per-bin change labels for review.");
		review.addActionListener(e -> reviewInterval());
		resetManualGulps.addActionListener(e -> {
			manualGulps.put(computedCapillary, new ManualIntakeEdits());
			invalidateBatch();
			refresh();
		});
		addGulp.setToolTipText(
				"Select a region, then confirm its net intake (after evaporation subtraction) and bottom position in pixels. Overrides automatic detection, including x-marked bins. Save intake or Copy to gulps to persist.");
		resetManualGulps.setToolTipText(
				"Discard manual overrides on this capillary and return to automatic detection at the current threshold; save to persist the reset.");
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
		runSeriesSave.setToolTipText(
				"Detect every capillary with its automatic threshold and save correlation intake for each experiment in the selected range. Ordinary gulps are left as they are.");
		runSeries.setToolTipText(
				"Detect every capillary with its automatic threshold, replace ordinary gulps (including manual edits), and save each experiment in the selected range.");
		seriesRange.setToolTipText(
				"Current means the experiment selected when the batch starts; ranges include both endpoints. Progress uses the browser's zero-based absolute experiment index.");
		stopSeries.setToolTipText(
				"Stop before saving the current experiment if detection is unfinished; a save already started is completed.");
		detectAll.setToolTipText(
				"Detect every capillary in this experiment, including no-fly controls, using its own automatic threshold.");
		saveAll.setToolTipText(
				"Save all prepared results as correlation gulps. Existing correlation results are replaced; classic gulps are kept.");
		copyAllToGulps.setToolTipText(
				"Replace ordinary gulps for every capillary with the prepared intake, then save. These measures supply ordinary gulp graphs and exports.");
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
		if (released || suspended)
			return;
		suspended = true;
		cancelBatch();
		if (worker != null)
			worker.cancel(true);
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
		if (released)
			return;
		suspended = false;
		if (tooltipLayoutGuard == null)
			tooltipLayoutGuard = new RichTooltipLayoutGuard();
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
				&& experiment.getSeqKymos().getSequence().getSizeT() > 0 && cap != null && cap.getTopRaw() != null
				&& cap.getTopRaw().polylineLevel != null && cap.getTopRaw().polylineLevel.npoints >= 8;
	}

	/**
	 * Reloading descriptors may replace capillary objects in the same experiment.
	 */
	private void refreshCapillaries() {
		List<Capillary> current = experiment.getCapillaries().getList();
		boolean same = current.size() == capillaries.getItemCount();
		for (int i = 0; same && i < current.size(); i++)
			same = current.get(i) == capillaries.getItemAt(i);
		if (same)
			return;
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
				if (previous != null)
					manualThresholds.put(cap, previous);
			}
			if (old != null)
				for (Capillary cap : current)
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
		if (released)
			return;
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

	private boolean isBackgroundBusy() {
		return batchWorker != null || seriesWorker != null || reviewExportWorker != null || editedPersistWorker != null;
	}

	private void setResultControlsEnabled(boolean enabled) {
		boolean editable = enabled && !isBackgroundBusy();
		automatic.setEnabled(editable && preview != null && Double.isFinite(preview.noise.automatic));
		export.setEnabled(enabled);
		exportExperiment.setEnabled(enabled && !isBackgroundBusy());
		saveAllEdited.setEnabled(editable);
		copyAllEdited.setEnabled(editable);
		review.setEnabled(enabled && persistentEpisodes.isSelected());
		threshold.setEnabled(editable);
		save.setEnabled(editable);
		copyToGulps.setEnabled(editable);
		addGulp.setEnabled(editable);
		resetManualGulps.setEnabled(editable);
		// Retain the user's editing mode while another capillary is loading.
	}

	private void checkSources() {
		if (released || suspended || seriesWorker != null || reviewExportWorker != null || editedPersistWorker != null)
			return;
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
		if (batchWorker != null || seriesWorker != null || released || suspended)
			return;
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
		Double saved = manualThresholds.get(cap);
		CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
		if (!manualGulps.containsKey(cap))
			manualGulps.put(cap, ManualIntakeEdits.fromOptions(stored.getMethodOptions(), preview.raw.length));
		if (saved == null && stored.hasMeasures() && stored.isManualThreshold()
				&& sameSettings(stored) && Double.isFinite(stored.getThresholdPxPerMin()))
			saved = stored.getThresholdPxPerMin();
		double initial = saved != null ? saved : preview.noise.automatic;
		setResultControlsEnabled(true);
		setThreshold(Double.isFinite(initial) ? initial : 1, saved != null || !Double.isFinite(initial));
	}

	private List<Experiment> seriesTargets() {
		List<Experiment> targets = new ArrayList<>();
		if (experimentBrowser == null) {
			if (seriesRange.getSelectedIndex() != 0)
				throw new IllegalStateException(
						"Open this panel from the experiment browser to run multiple experiments.");
			targets.add(experiment);
			return targets;
		}
		int current = experimentBrowser.getSelectedIndex();
		if (current < 0)
			throw new IllegalStateException("Select an experiment first.");
		int first = seriesRange.getSelectedIndex() == 2 ? 0 : current;
		int last = seriesRange.getSelectedIndex() == 0 ? current : experimentBrowser.getItemCount() - 1;
		for (int i = first; i <= last; i++) {
			// Snapshot identities without loading every experiment on the UI thread.
			Object item = experimentBrowser instanceof JComboBoxExperimentLazy
					? ((JComboBoxExperimentLazy) experimentBrowser).getItemAtNoLoad(i)
					: experimentBrowser.getItemAt(i);
			if (!(item instanceof Experiment))
				throw new IllegalStateException("Missing experiment at index " + i);
			targets.add((Experiment) item);
		}
		return targets;
	}

	private void startSeries(boolean copyToGulps) {
		if (released || suspended || seriesWorker != null || batchWorker != null)
			return;
		final List<Experiment> targets;
		try {
			targets = seriesTargets();
		} catch (RuntimeException error) {
			seriesStatus.setText(error.getMessage());
			return;
		}
		final int firstIndex = experimentBrowser == null || seriesRange.getSelectedIndex() == 2 ? 0
				: experimentBrowser.getSelectedIndex();
		final int experimentCount = experimentBrowser == null ? 1 : experimentBrowser.getItemCount();
		if (worker != null)
			worker.cancel(true);
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
					if (seriesStopping)
						break;
					publish(label + " - loading...");
					try {
						if (!processor.process(target, episodes, local, copyToGulps, () -> seriesStopping,
								message -> publish(label + " - " + message)))
							break;
						seriesSaved++;
						attentionTarget = null;
						attentionLabel = null;
					} catch (Exception error) {
						Throwable cause = error;
						while (cause.getCause() != null)
							cause = cause.getCause();
						throw new IllegalStateException(label + ": " + cause.getMessage(), error);
					}
				}
				return seriesSaved;
			}

			@Override
			protected void process(List<String> messages) {
				if (!released && seriesWorker == this)
					seriesStatus.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				String result;
				try {
					get();
					result = seriesStopping
							? (attentionLabel == null ? "Stopped. " : "Stopped at " + attentionLabel + ". ")
							: "Complete. ";
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					result = "Interrupted. ";
				} catch (ExecutionException error) {
					result = "Failed at " + error.getCause().getMessage() + ". ";
				}
				seriesWorker = null;
				if (experimentBrowser != null)
					experimentBrowser.setEnabled(browserWasEnabled);
				if (!released && attentionTarget != null)
					selectSeriesExperiment(attentionTarget);
				String outcome = result + seriesSaved + "/" + targets.size()
						+ (copyToGulps ? " experiment(s) copied to gulps and saved."
								: " experiment(s) detected and saved.");
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
		seriesStatus.setText((copyToGulps ? "Detecting, replacing gulps and saving " : "Detecting and saving ")
				+ targets.size() + " experiment(s), indices " + firstIndex + "-" + (firstIndex + targets.size() - 1)
				+ " / " + experimentCount + "...");
		updateModeControls();
		seriesWorker.execute();
	}

	private void selectSeriesExperiment(Experiment target) {
		if (experimentBrowser == null)
			return;
		for (int i = 0; i < experimentBrowser.getItemCount(); i++) {
			Object item = experimentBrowser instanceof JComboBoxExperimentLazy
					? ((JComboBoxExperimentLazy) experimentBrowser).getItemAtNoLoad(i)
					: experimentBrowser.getItemAt(i);
			if (item == target) {
				experimentBrowser.setSelectedIndex(i);
				return;
			}
		}
	}

	void showSeriesOutcome(String message) {
		if (released)
			return;
		seriesStatus.setText(message);
	}

	private void updateDetectorLabel() {
		detectorVersion.setText(
				persistentEpisodes.isSelected() ? "Detector " + IntakeEpisodes.METHOD_VERSION + " - persistent episodes"
						: "Detector " + IntakeEpisodes.METHOD_VERSION + " - single-bin (persistence off)");
	}

	private void updateModeControls() {
		updateDetectorLabel();
		boolean busy = isBackgroundBusy();
		seriesRange.setEnabled(!busy);
		runSeriesSave.setEnabled(!busy);
		runSeries.setEnabled(!busy);
		stopSeries.setEnabled(seriesWorker != null && !seriesStopping);
		detectAll.setEnabled(!busy && capillaries.getItemCount() > 0);
		cancelAll.setEnabled(batchWorker != null);
		saveAll.setEnabled(!busy && !batchResults.isEmpty());
		copyAllToGulps.setEnabled(!busy && !batchResults.isEmpty());
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
		batchStatus.setText("Run Detect all to prepare automatic results for this experiment.");
	}

	private boolean batchSourcesCurrent() {
		List<Capillary> current = experiment.getCapillaries().getList();
		return current.size() == batchResults.size() && current.stream().allMatch(
				cap -> batchResults.containsKey(cap) && batchResults.get(cap).preview.matchesSources(experiment));
	}

	private void cancelBatch() {
		if (batchWorker == null)
			return;
		batchWorker.cancel(true);
		batchWorker = null;
		invalidateBatch();
		batchStatus.setText("Cancelled; no batch results saved.");
		updateModeControls();
		if (!released)
			analyzeSelected();
	}

	private void detectAll() {
		if (batchWorker != null || seriesWorker != null || released || suspended)
			return;
		if (worker != null)
			worker.cancel(true);
		worker = null;
		pendingCapillary = null;
		invalidateBatch();
		clearPreview();
		List<Capillary> targets = new ArrayList<>(experiment.getCapillaries().getList());
		boolean episodes = persistentEpisodes.isSelected(), local = localEvaporation.isSelected();
		batchWorker = new SwingWorker<Map<Capillary, AutomaticIntakeResult>, String>() {
			@Override
			protected Map<Capillary, AutomaticIntakeResult> doInBackground() {
				Map<Capillary, AutomaticIntakeResult> results = new IdentityHashMap<>();
				for (Capillary cap : targets) {
					if (isCancelled())
						throw new CancellationException();
					publish("Detecting " + (results.size() + 1) + "/" + targets.size() + ": "
							+ cap.getLast2ofCapillaryName());
					try {
						results.put(cap, new AutomaticIntakeResult(
								CorrelationGulpPreview.computeAccepted(experiment, cap, local), episodes, local));
					} catch (RuntimeException e) {
						throw new IllegalArgumentException(cap.getLast2ofCapillaryName() + ": " + e.getMessage(), e);
					}
				}
				return results;
			}

			@Override
			protected void process(List<String> messages) {
				if (batchWorker == this)
					batchStatus.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (batchWorker != this || released)
					return;
				batchWorker = null;
				try {
					batchResults = get();
					if (!batchSourcesCurrent())
						throw new IllegalStateException("Levels changed; run Detect all again.");
					for (Map.Entry<Capillary, AutomaticIntakeResult> entry : batchResults.entrySet())
						cache.put(entry.getKey(), entry.getValue().preview);
					batchStatus.setText("Ready: " + batchResults.size()
							+ " capillaries at automatic thresholds. Save intake or copy to gulps.");
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
		if (batchWorker != null || batchResults.isEmpty())
			return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			batchStatus.setText("Levels changed; run Detect all again.");
			return;
		}
		Map<Capillary, AutomaticIntakeResult> confirmed = batchResults;
		if (JOptionPane.showConfirmDialog(this,
				"Replace ordinary gulps for all " + batchResults.size() + " capillaries with the detected intake?"
						+ "\nExisting gulps, including unsaved manual edits, will be replaced."
						+ "\nThe new gulp measures will be saved for graphs and exports.",
				"Copy all to gulps", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		if (batchResults == confirmed)
			persistBatch(true);
	}

	private void persistBatch(boolean copy) {
		if (batchWorker != null || batchResults.isEmpty())
			return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			batchStatus.setText("Levels changed; run Detect all again.");
			return;
		}
		for (Map.Entry<Capillary, AutomaticIntakeResult> entry : batchResults.entrySet()) {
			entry.getValue().install(entry.getKey());
			manualGulps.put(entry.getKey(), new ManualIntakeEdits());
			if (copy)
				entry.getKey().copyCorrelationGulpsToGulps();
			manualThresholds.remove(entry.getKey());
		}
		if (copy)
			refreshGulpRois();
		boolean saved = experiment.save_capillaries_description_and_measures();
		batchStatus.setText(saved ? "Saved " + (copy ? "ordinary gulps" : "separate intake") + " for "
				+ batchResults.size() + " capillaries." : "Save failed; results remain in memory. Retry saving.");
		refresh();
	}

	private void refreshGulpRois() {
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq != null && seq.getFirstViewer() != null) {
			experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(seq.getFirstViewer().getPositionT(),
					experiment.getCapillaries());
			roiCapillary = null;
			updateRois();
		}
	}

	private boolean sameSettings(CapillaryCorrelationGulps stored) {
		return stored.getMethodOptions().startsWith(methodOptions());
	}

	@Override
	public double[] candidateAmplitudes() {
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

	@Override
	public double[] candidateBottoms() {
		double[] positions = persistentEpisodes.isSelected() ? episodeDetection().position.clone()
				: preview.raw.clone();
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
		if (!user)
			manualThresholds.remove(computedCapillary);
		refresh();
	}

	@Override
	public List<IntakeDiagnostics.Warning> diagnostics() {
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

	@Override
	public Color diagnosticColor(IntakeDiagnostics.Warning warning) {
		return warning.kind == IntakeDiagnostics.Kind.STEP && warning.excess > 0 ? new Color(210, 135, 0)
				: warning.kind == IntakeDiagnostics.Kind.DRIFT ? new Color(0, 140, 155) : new Color(165, 65, 180);
	}

	private void reviewInterval() {
		if (preview == null)
			return;
		if (reviewDialog != null)
			reviewDialog.dispose();
		reviewDialog = IntakeReviewDialog.open(this, computedCapillary.getLast2ofCapillaryName(), preview,
				episodeDetection(), computedCapillary.getNFlies() == 0, selectedFrame, diagnostics());
	}

	private String methodOptions() {
		return IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw,persistent=" + persistentEpisodes.isSelected()
				+ ",local=" + localEvaporation.isSelected() + manualEdits().options();
	}

	private ManualIntakeEdits manualEdits() {
		if (computedCapillary == null)
			return new ManualIntakeEdits();
		return manualGulps.computeIfAbsent(computedCapillary, cap -> new ManualIntakeEdits());
	}

	private void editGulp(int first, int last) {
		if (!addGulp.isEnabled() || !addGulp.isSelected() || preview == null)
			return;
		checkSources();
		if (preview == null)
			return;
		first = Math.max(1, first);
		last = Math.max(first, Math.min(preview.raw.length - 1, last));
		final CorrelationGulpPreview edited = preview;
		final Capillary cap = computedCapillary;
		double bottom = candidateBottoms()[last];
		if (!Double.isFinite(bottom))
			bottom = preview.image.getHeight() / 2.;
		double binEvaporation = preview.evaporation[last] - preview.evaporation[last - 1];
		double amount = preview.raw[last] - preview.raw[first - 1] - preview.evaporation[last]
				+ preview.evaporation[first - 1];
		if (first == last && candidateAmplitudes()[last] > 0)
			amount = Math.max(0, candidateAmplitudes()[last] - binEvaporation);
		if (!Double.isFinite(amount) || amount <= 0)
			amount = 1;
		ManualGulpDialog.Result result = ManualGulpDialog.show(this, edited, first, last, amount, bottom,
				candidateAmplitudes(), candidateBottoms(), manualGulpContextMinutes,
				() -> preview == edited && computedCapillary == cap && edited.matchesSources(experiment));
		manualGulpContextMinutes = result.contextMinutes;
		if (result.stale) {
			checkSources();
			return;
		}
		if (result.accepted == null)
			return;
		ManualGulpDialog.Result.Accepted accepted = result.accepted;
		if (accepted.selectedFirst != first || accepted.selectedLast != last)
			manualEdits().replace(first, last, 0, bottom);
		for (int i = accepted.selectedFirst; i <= accepted.selectedLast; i++)
			manualEdits().replace(i, i, accepted.delete ? 0 : accepted.amplitudes[i],
					accepted.delete ? bottom : accepted.bottoms[i]);
		invalidateBatch();
		selectedFrame = accepted.active;
		refresh();
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

	@Override
	public boolean[] candidates() {
		double[] amplitudes = candidateAmplitudes();
		boolean[] selected = new boolean[amplitudes.length];
		for (int i = 1; i < amplitudes.length; i++)
			selected[i] = amplitudes[i] > 0;
		return selected;
	}

	private String statusText() {
		int count = 0, good = 0;
		for (boolean v : candidates())
			if (v)
				count++;
		for (boolean v : preview.motion.reliable)
			if (v)
				good++;
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
		return "<html>"
				+ (persistentEpisodes.isSelected()
						? episodeDetection().episodes + " automatic episodes / " + count
								+ " intake bins (including overrides)"
						: count + " bins")
				+ (persistentEpisodes.isSelected() ? " / " + diagnostics().size() + " diagnostics" : "") + " | net "
				+ format(net) + " px; raw minus evaporation " + format(observed) + " px"
				+ (Math.abs(net - observed) > Math.max(3, .2 * Math.abs(observed))
						? " | cumulative mismatch: inspect tracking/evaporation"
						: "")
				+ "<br>" + good + "/" + (preview.raw.length - 1) + " usable level increments | threshold "
				+ format(value()) + " px/min (" + (manual ? "manual" : "automatic") + ") | time "
				+ format(preview.minutes[selectedFrame]) + " min | method " + IntakeEpisodes.METHOD_VERSION
				+ " | bin " + selectedFrame + ": " + preview.motion.reason[selectedFrame]
				+ (!manualEdits().isEmpty() ? " | manual gulp overrides active" : "")
				+ (persistentEpisodes.isSelected() ? " / persistent episodes" : " / single-bin (persistence off)")
				+ " | " + storage + "</html>";
	}


	private void refresh() {
		updateDetectorLabel();
		if (preview != null) {
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
			status.setText(statusText());
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
		if (!released && !suspended)
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

	private void persistEditedIntake(boolean copy) {
		if (preview == null || editedPersistWorker != null)
			return;
		int count = experiment.getCapillaries().getList().size();
		if (count == 0)
			return;
		String title = copy ? "Copy all edited to gulps" : "Save all edited intake";
		String message = copy ? "Replace ordinary gulps for all " + count
				+ " capillaries with their edited intake?\nExisting gulps will be replaced and the measures will be saved."
				: "Save correlation intake for all " + count
						+ " capillaries, including each capillary's manual edits?\nOrdinary gulps stay unchanged.";
		if (JOptionPane.showConfirmDialog(this, message, title, JOptionPane.OK_CANCEL_OPTION,
				copy ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		EditedIntakeSnapshot snapshot = captureEditedIntake();
		editedPersistWorker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				snapshot.prepare(this::publish);
				return null;
			}

			@Override
			protected void process(List<String> messages) {
				if (editedPersistWorker == this)
					status.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (editedPersistWorker == this)
					editedPersistWorker = null;
				if (released)
					return;
				updateModeControls();
				try {
					get();
					if (!snapshot.install(copy)) {
						status.setText("Levels changed; edited intake was not saved.");
						return;
					}
					if (copy)
						refreshGulpRois();
					boolean saved = experiment.save_capillaries_description_and_measures();
					refresh();
					if (!saved)
						JOptionPane.showMessageDialog(CorrelationIntakePanel.this,
								"Could not save the capillary measures.", title, JOptionPane.ERROR_MESSAGE);
					else
						JOptionPane.showMessageDialog(CorrelationIntakePanel.this,
								(copy ? "Copied edited intake to gulps for " : "Saved edited intake for ")
										+ snapshot.caps.size() + " capillaries.",
								title, JOptionPane.INFORMATION_MESSAGE);
				} catch (InterruptedException | ExecutionException | RuntimeException error) {
					if (error instanceof InterruptedException)
						Thread.currentThread().interrupt();
					Throwable cause = error.getCause() == null ? error : error.getCause();
					status.setText(cause.getMessage());
					JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(), title,
							JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		updateModeControls();
		status.setText((copy ? "Copying edited intake to gulps for " : "Saving edited intake for ") + count
				+ " capillaries...");
		editedPersistWorker.execute();
	}

	private EditedIntakeSnapshot captureEditedIntake() {
		List<Capillary> caps = new ArrayList<>(experiment.getCapillaries().getList());
		Map<Capillary, Double> chosenThresholds = new IdentityHashMap<>();
		Map<Capillary, Boolean> manualThreshold = new IdentityHashMap<>();
		Map<Capillary, String> edits = new IdentityHashMap<>();
		for (Capillary cap : caps) {
			CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
			if (cap == computedCapillary) {
				chosenThresholds.put(cap, value());
				manualThreshold.put(cap, manual);
			} else if (manualThresholds.containsKey(cap)) {
				chosenThresholds.put(cap, manualThresholds.get(cap));
				manualThreshold.put(cap, true);
			} else if (stored.isManualThreshold() && stored.hasMeasures()
					&& Double.isFinite(stored.getThresholdPxPerMin())) {
				chosenThresholds.put(cap, stored.getThresholdPxPerMin());
				manualThreshold.put(cap, true);
			} else
				manualThreshold.put(cap, false);
			edits.put(cap, manualGulps.containsKey(cap) ? manualGulps.get(cap).options() : stored.getMethodOptions());
		}
		return new EditedIntakeSnapshot(experiment, caps, persistentEpisodes.isSelected(), localEvaporation.isSelected(),
				chosenThresholds, manualThreshold, edits, cache);
	}

	private void exportExperimentReview() {
		if (preview == null || reviewExportWorker != null)
			return;
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("intake-experiment-review.zip"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		Path file = chooser.getSelectedFile().toPath();
		if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Replace " + file.getFileName() + "?",
				"Export experiment review", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		IntakeReviewExport snapshot = captureReviewExport();
		reviewExportWorker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				snapshot.write(file);
				return null;
			}

			@Override
			protected void done() {
				reviewExportWorker = null;
				if (released)
					return;
				updateModeControls();
				checkSources();
				try {
					get();
					JOptionPane.showMessageDialog(
							CorrelationIntakePanel.this, "Exported all " + snapshot.caps.size()
									+ " capillaries, including retained manual edits.\n" + file,
							"Experiment review exported", JOptionPane.INFORMATION_MESSAGE);
				} catch (InterruptedException | ExecutionException | CancellationException error) {
					if (error instanceof InterruptedException)
						Thread.currentThread().interrupt();
					Throwable cause = error.getCause() == null ? error : error.getCause();
					JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(), "Export failed",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		updateModeControls();
		status.setText("Exporting review CSVs and kymographs for every capillary...");
		reviewExportWorker.execute();
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
			IntakeReviewExport.writeReviewCsv(writer, experiment, computedCapillary, preview, value(), manual,
					persistentEpisodes.isSelected(), localEvaporation.isSelected(), manualEdits());
		}
	}

	private static String format(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	@Override
	public CorrelationGulpPreview preview() {
		return preview;
	}

	@Override
	public double thresholdValue() {
		return value();
	}

	@Override
	public boolean thresholdEditable() {
		return threshold.isEnabled();
	}

	@Override
	public boolean addGulpArmed() {
		return addGulp.isEnabled() && addGulp.isSelected();
	}

	@Override
	public int selectedFrame() {
		return selectedFrame;
	}

	@Override
	public void selectFrame(int frame) {
		selectedFrame = frame;
		refresh();
	}

	@Override
	public void dragThreshold(double pixelsPerMinute) {
		threshold.setValue(pixelsPerMinute);
	}

	@Override
	public void editGulpRange(int first, int last) {
		editGulp(first, last);
	}

	@Override
	public boolean persistentEpisodes() {
		return persistentEpisodes.isSelected();
	}

	@Override
	public boolean showDiagnostics() {
		return showGaps.isSelected();
	}

	@Override
	public boolean compensateEvaporation() {
		return compensateEvaporation.isSelected();
	}

	@Override
	public String formatNumber(double value) {
		return format(value);
	}

	IntakeReviewExport captureReviewExport() {
		List<Capillary> caps = new ArrayList<>(experiment.getCapillaries().getList());
		Map<Capillary, Double> thresholds = new IdentityHashMap<>();
		Map<Capillary, String> edits = new IdentityHashMap<>();
		for (Capillary cap : caps) {
			CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
			Double setting = manualThresholds.get(cap);
			if (setting == null && stored.isManualThreshold() && stored.hasMeasures())
				setting = stored.getThresholdPxPerMin();
			if (cap == computedCapillary)
				setting = value();
			if (setting != null && Double.isFinite(setting))
				thresholds.put(cap, setting);
			edits.put(cap, manualGulps.containsKey(cap) ? manualGulps.get(cap).options() : stored.getMethodOptions());
		}
		return new IntakeReviewExport(experiment, caps, persistentEpisodes.isSelected(), localEvaporation.isSelected(),
				thresholds, edits, cache);
	}

}
