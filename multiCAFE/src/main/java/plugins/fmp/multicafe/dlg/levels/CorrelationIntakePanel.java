package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCorrelationGulps;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;
import plugins.fmp.multitools.service.IntakeEpisodes;
import plugins.fmp.multitools.service.ManualIntakeEdits;
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
	private final IntakeJobs jobs = new IntakeJobs();
	private final IntakeOverlayController overlays = new IntakeOverlayController();
	private final IntakeControls controls = new IntakeControls();
	private boolean persistenceFailed;
	private JComboBox<?> experimentBrowser;

	private volatile boolean seriesStopping;
	private volatile int seriesSaved;
	private boolean browserWasEnabled;

	private Map<Capillary, AutomaticIntakeResult> batchResults = new IdentityHashMap<>();

	private double manualGulpContextMinutes = 8;
	private final IntakeSession session = new IntakeSession();

	private final IntakeMotionPlot plot = new IntakeMotionPlot(this);
	private final IntakeAlignmentView alignment = new IntakeAlignmentView(this);

	private final Map<Capillary, CorrelationGulpPreview> cache = session.previews;
	private final Timer sourceTimer = new Timer(750, e -> checkSources());
	private final Timer roiTimer = new Timer(120, e -> updateRois());
	private Consumer<Capillary> onCapillaryChosen;
	private Capillary pendingCapillary;
	private CorrelationGulpPreview preview;
	private CorrelationGulpPreview episodePreview;
	private double episodeThreshold = Double.NaN;
	private IntakeEpisodes.Detection cachedEpisodes;
	private IntakeEpisodes.Detection diagnosticDetection;
	private List<IntakeDiagnostics.Warning> cachedDiagnostics;
	private boolean diagnosticNoFly;
	private Capillary computedCapillary;
	private boolean released, suspended;
	private boolean waitingForSources;
	private Sequence previewSequence;
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
			controls.capillaries.addItem(cap);
		if (initial != null)
			controls.capillaries.setSelectedItem(initial);
		Capillary first = (Capillary) controls.capillaries.getSelectedItem();
		if (first != null && (first.getCorrelationGulps().getMethodOptions().startsWith("v3,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v4,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v5,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v6,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions().startsWith("v6.1,source=accepted_topraw")
				|| first.getCorrelationGulps().getMethodOptions()
						.startsWith(IntakeEpisodes.METHOD_VERSION + ",source=accepted_topraw"))) {
			String options = first.getCorrelationGulps().getMethodOptions();
			IntakeDetectionSettings settings = IntakeDetectionSettings.fromOptions(options);
			controls.persistentEpisodes.setSelected(settings.episodes);
			controls.localEvaporation.setSelected(settings.local);
		}
		add(controls.buildControls(), BorderLayout.NORTH);
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
		add(controls.buildFooter(), BorderLayout.SOUTH);
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
		controls.tasks.addChangeListener(e -> updateModeControls());
		controls.runSeriesSave.addActionListener(e -> startSeries(false));
		controls.runSeries.addActionListener(e -> startSeries(true));
		controls.stopSeries.addActionListener(e -> {
			seriesStopping = true;
			controls.stopSeries.setEnabled(false);
			controls.seriesStatus.setText("Stopping; any save already in progress will finish.");
		});
		controls.detectAll.addActionListener(e -> detectAll());
		controls.saveAll.addActionListener(e -> saveAll());
		controls.copyAllToGulps.addActionListener(e -> copyAllToGulps());
		controls.cancelAll.addActionListener(e -> cancelBatch());
		controls.save.addActionListener(e -> {
			if (controls.saveScope.getSelectedIndex() == 1) persistEditedIntake(false);
			else store();
		});
		controls.copyToGulps.addActionListener(e -> {
			if (controls.saveScope.getSelectedIndex() == 1) persistEditedIntake(true);
			else copyToGulps();
		});
		controls.capillaries.addActionListener(e -> {
			if (syncingSelection)
				return;
			Capillary cap = (Capillary) controls.capillaries.getSelectedItem();
			if (onCapillaryChosen != null && cap != null)
				onCapillaryChosen.accept(cap);
			analyzeSelected();
			updateModeControls();
		});
		controls.previousButton.addActionListener(e -> controls.capillaries.setSelectedIndex(controls.capillaries.getSelectedIndex() - 1));
		controls.nextButton.addActionListener(e -> controls.capillaries.setSelectedIndex(controls.capillaries.getSelectedIndex() + 1));
		controls.automatic.addActionListener(e -> {
			invalidateBatch();
			if (preview != null && Double.isFinite(preview.noise.automatic))
				setThreshold(preview.noise.automatic, false);
		});
		controls.showRois.addActionListener(e -> updateRois());
		controls.compensateEvaporation.addActionListener(e -> {
			alignment.repaint();
			updateRois();
		});
		controls.export.addActionListener(e -> export());
		controls.exportExperiment.addActionListener(e -> exportExperimentReview());
		controls.exportExperiment.setToolTipText(
				"Export one ZIP with before/after CSVs and kymographs for every capillary, including unsaved manual gulp edits.");
		controls.saveAllEdited.addActionListener(e -> persistEditedIntake(false));
		controls.saveAllEdited.setName("saveAllEditedIntake");
		controls.saveAllEdited.setToolTipText(
				"Save correlation intake for every capillary, using each capillary's threshold and manual edits. Ordinary gulps stay unchanged.");
		controls.copyAllEdited.addActionListener(e -> persistEditedIntake(true));
		controls.copyAllEdited.setName("copyAllEditedToGulps");
		controls.copyAllEdited.setToolTipText(
				"Save that edited intake, then replace ordinary gulps for every capillary. Existing gulp measures are replaced.");
		controls.export.setText("Export selected CSV...");
		controls.export.setToolTipText(
				"Export one CSV with automatic detection, current detector settings, edited intake and per-bin change labels for review.");
		controls.review.addActionListener(e -> reviewInterval());
		controls.resetManualGulps.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(this, "Discard manual intake overrides for this capillary?",
					"Reset edits", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
				return;
			resetManualEdits();
		});
		controls.addGulp.setToolTipText(
				"Select a region, then confirm its net intake (after evaporation subtraction) and bottom position in pixels. Overrides automatic detection, including x-marked bins. Save intake or Replace gulps to persist.");
		controls.resetManualGulps.setToolTipText(
				"Discard manual overrides on this capillary and return to automatic detection at the current threshold; save to persist the reset.");
		controls.showGaps.setToolTipText(
				"Amber: abrupt steps. Teal: gradual drift. Purple: initial-anchor or reconstruction offsets in either direction. Diagnostics are not intake.");
		controls.showGaps.addActionListener(e -> {
			refresh();
			updateRois();
		});
		controls.persistentEpisodes.setToolTipText(
				"Signed 1-6 bin movement must persist between robust level plateaus and remain above its limit after fitting. Controls use the same test. Groups keep their time span; bars within one group are time bins, not separately resolved gulps.");
		controls.localEvaporation.setToolTipText(
				"Optional scale of the common evaporation shape from quiet runs; uncertain estimates retain the common model.");
		controls.localEvaporation.addActionListener(e -> {
			invalidateBatch();
			cache.clear();
			clearPreview();
			analyzeSelected();
		});
		controls.persistentEpisodes.addActionListener(e -> {
			invalidateBatch();
			controls.review.setEnabled(preview != null && controls.persistentEpisodes.isSelected());
			refresh();
			overlays.invalidate();
			updateRois();
		});
	}

	private void resetManualEdits() {
		session.edits(computedCapillary, new ManualIntakeEdits());
		invalidateBatch();
		refresh();
	}

	private void defineChangeListeners() {
		controls.threshold.addChangeListener(e -> {
			if (!settingThreshold && preview != null) {
				invalidateBatch();
				manual = true;
				controls.thresholdMode.setText("Manual");
				session.threshold(computedCapillary, value());
			}
			refresh();
		});
	}

	private void defineToolTips() {
		controls.runSeriesSave.setToolTipText(
				"Detect every capillary with its automatic threshold and save correlation intake for each experiment in the selected range. Ordinary gulps are left as they are.");
		controls.runSeries.setToolTipText(
				"Detect every capillary with its automatic threshold, replace ordinary gulps (including manual edits), and save each experiment in the selected range.");
		controls.seriesRange.setToolTipText(
				"Current means the experiment selected when the batch starts; ranges include both endpoints. Progress uses the browser's zero-based absolute experiment index.");
		controls.stopSeries.setToolTipText(
				"Stop before saving the current experiment if detection is unfinished; a save already started is completed.");
		controls.detectAll.setToolTipText(
				"Detect every capillary in this experiment, including no-fly controls, using its own automatic threshold.");
		controls.saveAll.setToolTipText(
				"Save all prepared results as correlation gulps. Existing correlation results are replaced; classic gulps are kept.");
		controls.copyAllToGulps.setToolTipText(
				"Replace ordinary gulps for every capillary with the prepared intake, then save. These measures supply ordinary gulp graphs and exports.");
		controls.cancelAll.setToolTipText("Cancel detection without installing or saving batch results.");
		controls.compensateEvaporation.setToolTipText(
				"Checked: grey steps = max(0, stored amplitude minus the model's evaporation in that bin). Unchecked: full stored amplitudes. Blue bars, detection and saved measures are unchanged.");
		controls.showRois.setToolTipText(
				"Show intake amplitudes and evaporation plus cumulative intake, starting at raw t=0; later raw levels do not reset the grey curve.");
		controls.threshold.setToolTipText(
				"Sensitivity setting in pixels/min. In episode mode, control-calibrated thresholds and sustained plateau checks also apply; crossing the red line alone does not accept a gulp.");
		controls.automatic.setToolTipText("Quiet-period median + 3 robust noise SD, with a no-fly control noise floor.");
		controls.previousButton.setToolTipText("previous capillary");
		controls.nextButton.setToolTipText("select next capillary");
	}

	public Experiment getExperiment() {
		return experiment;
	}

	void onCapillaryChosen(Consumer<Capillary> callback) {
		onCapillaryChosen = callback;
	}

	boolean isSeriesRunning() {
		return jobs.series != null;
	}

	void suspendSession() {
		if (released || suspended)
			return;
		suspended = true;
		cancelBatch();
		jobs.cancelPreview();
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
		overlays.forget();
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
			controls.status.setText("Load this experiment's kymographs and accepted topraw to analyze intake.");
		} else {
			checkSources();
			analyzeSelected();
		}
		sourceTimer.start();
		updateModeControls();
		scheduleRoiUpdate();
	}

	private boolean hasAcceptedInput() {
		Capillary cap = (Capillary) controls.capillaries.getSelectedItem();
		return experiment.getSeqKymos() != null && experiment.getSeqKymos().getSequence() != null
				&& experiment.getSeqKymos().getSequence().getSizeT() > 0 && cap != null && cap.getTopRaw() != null
				&& cap.getTopRaw().polylineLevel != null && cap.getTopRaw().polylineLevel.npoints >= 8;
	}

	/**
	 * Reloading descriptors may replace capillary objects in the same experiment.
	 */
	private void refreshCapillaries() {
		List<Capillary> current = experiment.getCapillaries().getList();
		boolean same = current.size() == controls.capillaries.getItemCount();
		for (int i = 0; same && i < current.size(); i++)
			same = current.get(i) == controls.capillaries.getItemAt(i);
		if (same)
			return;
		Capillary old = (Capillary) controls.capillaries.getSelectedItem();
		Map<String, Double> thresholdsByName = new java.util.HashMap<>();
		session.forEachThreshold((cap, value) -> thresholdsByName.put(cap.getKymographPrefix(), value));
		cache.clear();
		session.clearThresholds();
		session.clearEdits();
		invalidateBatch();
		clearPreview();
		syncingSelection = true;
		try {
			controls.capillaries.removeAllItems();
			for (Capillary cap : current) {
				controls.capillaries.addItem(cap);
				Double previous = thresholdsByName.get(cap.getKymographPrefix());
				if (previous != null)
					session.threshold(cap, previous);
			}
			if (old != null)
				for (Capillary cap : current)
					if (java.util.Objects.equals(old.getKymographPrefix(), cap.getKymographPrefix())) {
						controls.capillaries.setSelectedItem(cap);
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
				|| cap == controls.capillaries.getSelectedItem())
			return;
		syncingSelection = true;
		try {
			controls.capillaries.setSelectedItem(cap);
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
		if (jobs.edited != null) jobs.edited.cancel(true);
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
		jobs.cancelPreview();
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
		return jobs.busy();
	}

	private void setResultControlsEnabled(boolean enabled) {
		boolean editable = enabled && !isBackgroundBusy();
		boolean reviewing = editable && controls.tasks.getSelectedIndex() == 0;
		controls.automatic.setEnabled(editable && preview != null && Double.isFinite(preview.noise.automatic));
		controls.export.setEnabled(enabled);
		controls.exportExperiment.setEnabled(enabled && !isBackgroundBusy());
		controls.saveAllEdited.setEnabled(editable);
		controls.copyAllEdited.setEnabled(editable);
		controls.review.setEnabled(enabled && controls.persistentEpisodes.isSelected());
		controls.threshold.setEnabled(reviewing);
		controls.save.setEnabled(editable);
		controls.copyToGulps.setEnabled(editable);
		controls.addGulp.setEnabled(reviewing);
		controls.resetManualGulps.setEnabled(editable);
		// Retain the user's editing mode while another capillary is loading.
	}

	private void checkSources() {
		if (released || suspended || jobs.series != null || jobs.export != null || jobs.edited != null || jobs.save != null)
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
			controls.batchStatus.setText("Levels changed; run Detect all again.");
		}
		if (preview != null && !preview.matchesSources(experiment)) {
			// Geometry selected against old levels must not be silently reused.
			for (Capillary cap : experiment.getCapillaries().getList())
				session.edits(cap, new ManualIntakeEdits());
			cache.clear();
			clearPreview();
			analyzeSelected();
		}
	}

	private void analyzeSelected() {
		if (isBackgroundBusy() || released || suspended)
			return;
		Capillary cap = (Capillary) controls.capillaries.getSelectedItem();
		if (cap == null) {
			controls.status.setText("No capillaries loaded.");
			return;
		}
		if (cap == computedCapillary && preview != null && preview.matchesSources(experiment))
			return;
		if (cap == pendingCapillary && jobs.preview != null && !jobs.preview.isDone())
			return;
		CorrelationGulpPreview cached = cache.get(cap);
		if (cached != null && !cached.matchesSources(experiment))
			for (Capillary current : experiment.getCapillaries().getList())
				session.edits(current, new ManualIntakeEdits());
		if (cached != null && cached.matchesSources(experiment)) {
			if (jobs.preview != null)
				jobs.preview.cancel(true);
			jobs.preview = null;
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
		controls.source.setText(" ");
		controls.warningSummary.setVisible(false);
		plot.repaint();
		alignment.revalidate();
		alignment.repaint();
		scheduleRoiUpdate();
	}

	private void startCompute(Capillary cap, boolean interactive) {
		if (jobs.preview != null)
			jobs.preview.cancel(true);
		clearPreview();
		boolean local = controls.localEvaporation.isSelected();
		pendingCapillary = cap;
		controls.status.setText("Computing accepted-level increments and no-fly control noise for "
				+ cap.getLast2ofCapillaryName() + "...");
		jobs.preview = new SwingWorker<CorrelationGulpPreview, Void>() {
			@Override
			protected CorrelationGulpPreview doInBackground() {
				return CorrelationGulpPreview.computeAccepted(experiment, cap, local);
			}

			@Override
			protected void done() {
				if (jobs.preview != this)
					return;
				jobs.preview = null;
				pendingCapillary = null;
				try {
					if (isCancelled())
						return;
					CorrelationGulpPreview result = get();
					if (!result.matchesSources(experiment) || result.localEvaporation != controls.localEvaporation.isSelected()) {
						startCompute(cap, false);
						return;
					}
					cache.put(cap, result);
					display(cap, result);
				} catch (CancellationException ignored) {
					controls.status.setText("Computation cancelled.");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					controls.status.setText("Computation interrupted.");
				} catch (ExecutionException | RuntimeException e) {
					clearPreview();
					Throwable cause = e.getCause() == null ? e : e.getCause();
					controls.status.setText(cap.getLast2ofCapillaryName() + ": " + cause.getMessage());
					if (interactive)
						JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(),
								"Correlation preview", JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		jobs.preview.execute();
	}

	private void display(Capillary cap, CorrelationGulpPreview result) {
		preview = result;
		previewSequence = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		computedCapillary = cap;
		selectedFrame = 0;

		alignment.revalidate();
		Double saved = session.threshold(cap);
		CapillaryCorrelationGulps stored = cap.getCorrelationGulps();
		if (!session.hasEdits(cap))
			session.edits(cap, ManualIntakeEdits.fromOptions(stored.getMethodOptions(), preview.raw.length));
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
			if (controls.seriesRange.getSelectedIndex() != 0)
				throw new IllegalStateException(
						"Open this panel from the experiment browser to run multiple experiments.");
			targets.add(experiment);
			return targets;
		}
		int current = experimentBrowser.getSelectedIndex();
		if (current < 0)
			throw new IllegalStateException("Select an experiment first.");
		int first = controls.seriesRange.getSelectedIndex() == 2 ? 0 : current;
		int last = controls.seriesRange.getSelectedIndex() == 0 ? current : experimentBrowser.getItemCount() - 1;
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

	private void startSeries(boolean replaceGulps) {
		if (released || suspended || isBackgroundBusy())
			return;
		final List<Experiment> targets;
		try {
			targets = seriesTargets();
		} catch (RuntimeException error) {
			controls.seriesStatus.setText(error.getMessage());
			return;
		}
		final int firstIndex = experimentBrowser == null || controls.seriesRange.getSelectedIndex() == 2 ? 0
				: experimentBrowser.getSelectedIndex();
		final int experimentCount = experimentBrowser == null ? 1 : experimentBrowser.getItemCount();
		jobs.cancelPreview();
		pendingCapillary = null;
		sourceTimer.stop();
		invalidateBatch();
		clearPreview();
		seriesSaved = 0;
		seriesStopping = false;
		boolean episodes = controls.persistentEpisodes.isSelected(), local = controls.localEvaporation.isSelected();
		if (experimentBrowser != null) {
			browserWasEnabled = experimentBrowser.isEnabled();
			experimentBrowser.setEnabled(false);
		}
		jobs.series = new SwingWorker<Integer, String>() {
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
						if (!processor.process(target, episodes, local, replaceGulps, () -> seriesStopping,
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
				if (!released && jobs.series == this)
					controls.seriesStatus.setText(messages.get(messages.size() - 1));
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
				jobs.series = null;
				if (experimentBrowser != null)
					experimentBrowser.setEnabled(browserWasEnabled);
				if (!released && attentionTarget != null)
					selectSeriesExperiment(attentionTarget);
				String outcome = result + seriesSaved + "/" + targets.size()
						+ (replaceGulps ? " experiment(s) copied to gulps and saved."
								: " experiment(s) detected and saved.");
				if (released) {
					CorrelationIntakePanel.this.firePropertyChange("intake_series_finished", null, outcome);
					return;
				}
				cache.clear();
				if (targets.contains(experiment)) {
					session.clearThresholds();
					session.clearEdits();
					refreshGulpRois();
				}
				updateModeControls();
				if (experimentBrowser == null || experimentBrowser.getSelectedItem() == experiment) {
					sourceTimer.start();
					analyzeSelected();
				}
				controls.seriesStatus.setText(outcome);
				CorrelationIntakePanel.this.firePropertyChange("intake_series_finished", null, outcome);
			}
		};
		controls.seriesStatus.setText((replaceGulps ? "Detecting, replacing gulps and saving " : "Detecting and saving ")
				+ targets.size() + " experiment(s), indices " + firstIndex + "-" + (firstIndex + targets.size() - 1)
				+ " / " + experimentCount + "...");
		updateModeControls();
		jobs.series.execute();
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
		controls.seriesStatus.setText(message);
		controls.tasks.setSelectedIndex(2);
	}

	private void updateDetectorLabel() {
		controls.detectorVersion.setText(
				controls.persistentEpisodes.isSelected() ? "Detector " + IntakeEpisodes.METHOD_VERSION + " - persistent episodes"
						: "Detector " + IntakeEpisodes.METHOD_VERSION + " - single-bin (persistence off)");
	}

	private void updateModeControls() {
		updateDetectorLabel();
		boolean busy = isBackgroundBusy();
		controls.experimentMode.setEnabled(!busy);
		controls.saveScope.setEnabled(!busy);
		controls.seriesRange.setEnabled(!busy);
		controls.runSeriesSave.setEnabled(!busy);
		controls.runSeries.setEnabled(!busy);
		controls.stopSeries.setEnabled(jobs.series != null && !seriesStopping);
		controls.detectAll.setEnabled(!busy && controls.capillaries.getItemCount() > 0);
		controls.cancelAll.setEnabled(jobs.batch != null);
		controls.saveAll.setEnabled(!busy && !batchResults.isEmpty());
		controls.copyAllToGulps.setEnabled(!busy && !batchResults.isEmpty());
		controls.capillaries.setEnabled(!busy);
		controls.previousButton.setEnabled(!busy && controls.capillaries.getSelectedIndex() > 0);
		controls.nextButton.setEnabled(!busy && controls.capillaries.getSelectedIndex() < controls.capillaries.getItemCount() - 1);
		controls.persistentEpisodes.setEnabled(!busy);
		controls.localEvaporation.setEnabled(!busy);
		setResultControlsEnabled(preview != null && !busy);
		revalidate();
	}

	private void invalidateBatch() {
		batchResults.clear();
		controls.saveAll.setEnabled(false);
		controls.copyAllToGulps.setEnabled(false);
		controls.batchStatus.setText("Run Detect all to prepare automatic results for this experiment.");
	}

	private boolean batchSourcesCurrent() {
		List<Capillary> current = experiment.getCapillaries().getList();
		return current.size() == batchResults.size() && current.stream().allMatch(
				cap -> batchResults.containsKey(cap) && batchResults.get(cap).preview.matchesSources(experiment));
	}

	private void cancelBatch() {
		if (jobs.batch == null)
			return;
		jobs.batch.cancel(true);
		jobs.batch = null;
		invalidateBatch();
		controls.batchStatus.setText("Cancelled; no batch results saved.");
		updateModeControls();
		if (!released)
			analyzeSelected();
	}

	private void detectAll() {
		if (isBackgroundBusy() || released || suspended)
			return;
		jobs.cancelPreview();
		pendingCapillary = null;
		invalidateBatch();
		clearPreview();
		List<Capillary> targets = new ArrayList<>(experiment.getCapillaries().getList());
		boolean episodes = controls.persistentEpisodes.isSelected(), local = controls.localEvaporation.isSelected();
		jobs.batch = new SwingWorker<Map<Capillary, AutomaticIntakeResult>, String>() {
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
				if (jobs.batch == this)
					controls.batchStatus.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (jobs.batch != this || released)
					return;
				jobs.batch = null;
				try {
					batchResults = get();
					if (!batchSourcesCurrent())
						throw new IllegalStateException("Levels changed; run Detect all again.");
					for (Map.Entry<Capillary, AutomaticIntakeResult> entry : batchResults.entrySet())
						cache.put(entry.getKey(), entry.getValue().preview);
					controls.batchStatus.setText("Ready: " + batchResults.size()
							+ " capillaries at automatic thresholds. Save intake or copy to gulps.");
					analyzeSelected();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					invalidateBatch();
					controls.batchStatus.setText("Detection interrupted; no batch results saved.");
				} catch (ExecutionException | RuntimeException e) {
					invalidateBatch();
					Throwable cause = e.getCause() == null ? e : e.getCause();
					controls.batchStatus.setText("Detection failed: " + cause.getMessage());
				}
				updateModeControls();
			}
		};
		updateModeControls();
		jobs.batch.execute();
	}

	private void saveAll() {
		persistBatch(false);
	}

	private void copyAllToGulps() {
		if (isBackgroundBusy() || batchResults.isEmpty())
			return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			controls.batchStatus.setText("Levels changed; run Detect all again.");
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
		if (isBackgroundBusy() || batchResults.isEmpty())
			return;
		if (!batchSourcesCurrent()) {
			invalidateBatch();
			controls.batchStatus.setText("Levels changed; run Detect all again.");
			return;
		}
		Map<Capillary, PreparedIntake> prepared = new IdentityHashMap<>();
		batchResults.forEach((cap, result) -> prepared.put(cap, new PreparedIntake(result, new ManualIntakeEdits())));
		IntakePersistence.Destination destination = copy ? IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE
				: IntakePersistence.Destination.SAVE_INTAKE;
		try {
			if (!IntakePersistence.install(experiment, new ArrayList<>(experiment.getCapillaries().getList()), prepared, destination)) {
				invalidateBatch();
				controls.batchStatus.setText("Levels changed; run Detect all again.");
				return;
			}
			for (Capillary cap : prepared.keySet()) {
				session.edits(cap, new ManualIntakeEdits());
				session.threshold(cap, null);
			}
			if (copy) refreshGulpRois();
			saveInstalledResults("Save experiment intake", saved -> controls.batchStatus.setText(saved
					? "Saved " + prepared.size() + " capillaries." : "Save failed; results remain in memory. Retry saving."));
		} catch (RuntimeException error) {
			showPersistenceError("Save experiment intake", error);
		}
	}

	private void refreshGulpRois() {
		Sequence seq = experiment.getSeqKymos() == null ? null : experiment.getSeqKymos().getSequence();
		if (seq != null && seq.getFirstViewer() != null) {
			experiment.getSeqKymos().replaceCapillaryMeasureRoisAtT(seq.getFirstViewer().getPositionT(),
					experiment.getCapillaries());
			overlays.forget();
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
		return detectionResult().amplitudes.clone();
	}

	private AutomaticIntakeResult detectionResult() {
		return new AutomaticIntakeResult(preview,
				new IntakeDetectionSettings(controls.persistentEpisodes.isSelected(), controls.localEvaporation.isSelected()),
				value(), manual, controls.persistentEpisodes.isSelected() ? episodeDetection() : null);
	}

	@Override
	public double[] candidateBottoms() {
		double[] positions = detectionResult().bottoms.clone();
		manualEdits().apply(new double[positions.length], positions);
		return positions;
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
		if (controls.persistentEpisodes.isSelected() || !manualEdits().isEmpty()) {
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

	/** Returns whether a save job was started; disk completion is asynchronous. */
	private boolean store() {
		checkSources();
		if (preview == null || isBackgroundBusy()) return false;
		return persistSelected(IntakePersistence.Destination.SAVE_INTAKE);
	}

	private boolean persistSelected(IntakePersistence.Destination destination) {
		Map<Capillary, PreparedIntake> prepared = new IdentityHashMap<>();
		prepared.put(computedCapillary, new PreparedIntake(detectionResult(), manualEdits()));
		try {
			if (!IntakePersistence.install(experiment, new ArrayList<>(experiment.getCapillaries().getList()), prepared, destination)) {
				checkSources();
				return false;
			}
			if (destination == IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE) refreshGulpRois();
			saveInstalledResults("Save intake", saved -> { });
			return true;
		} catch (RuntimeException error) {
			showPersistenceError("Save intake", error);
			return false;
		}
	}

	private void copyToGulps() {
		checkSources();
		if (preview == null || isBackgroundBusy())
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
		persistSelected(IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE);
	}

	private double value() {
		return ((Number) controls.threshold.getValue()).doubleValue();
	}

	private void setThreshold(double v, boolean user) {
		settingThreshold = true;
		controls.threshold.setValue(Math.max(0, Math.min(10000, v)));
		settingThreshold = false;
		manual = user;
		controls.thresholdMode.setText(user ? "Manual" : "Automatic");
		if (!user)
			session.threshold(computedCapillary, null);
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
		return new IntakeDetectionSettings(controls.persistentEpisodes.isSelected(), controls.localEvaporation.isSelected()).options()
				+ manualEdits().options();
	}

	private ManualIntakeEdits manualEdits() {
		if (computedCapillary == null)
			return new ManualIntakeEdits();
		return session.edits(computedCapillary);
	}

	private void editGulp(int first, int last) {
		if (!controls.addGulp.isEnabled() || !controls.addGulp.isSelected() || preview == null)
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
		String storage = jobs.save != null ? "saving..." : persistenceFailed ? "save failed; retry saving" : isStoredCurrent() ? "saved"
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
				+ (controls.persistentEpisodes.isSelected()
						? episodeDetection().episodes + " automatic episodes / " + count
								+ " intake bins (including overrides)"
						: count + " bins")
				+ (controls.persistentEpisodes.isSelected() ? " / " + diagnostics().size() + " diagnostics" : "") + " | net "
				+ format(net) + " px; raw minus evaporation " + format(observed) + " px"
				+ (Math.abs(net - observed) > Math.max(3, .2 * Math.abs(observed))
						? " | cumulative mismatch: inspect tracking/evaporation"
						: "")
				+ "<br>" + good + "/" + (preview.raw.length - 1) + " usable level increments | threshold "
				+ format(value()) + " px/min (" + (manual ? "manual" : "automatic") + ") | time "
				+ format(preview.minutes[selectedFrame]) + " min | method " + IntakeEpisodes.METHOD_VERSION
				+ " | bin " + selectedFrame + ": " + preview.motion.reason[selectedFrame]
				+ (!manualEdits().isEmpty() ? " | manual gulp overrides active" : "")
				+ (controls.persistentEpisodes.isSelected() ? " / persistent episodes" : " / single-bin (persistence off)")
				+ " | " + storage + "</html>";
	}


	private void refresh() {
		updateDetectorLabel();
		if (preview != null) {
			String auto = Double.isFinite(preview.noise.automatic) ? format(preview.noise.automatic) : "unavailable";
			controls.source.setText("<html>" + preview.referenceDescription.replace("; border:", "<br>Border:")
					+ "<br>Initial threshold: " + auto + " px/min | "
					+ preview.noise.source.replace("Empty-capillary", "No-fly control")
							.replace("empty-capillary", "no-fly control")
					+ " | quiet intervals: " + preview.noise.quietSamples
					+ (controls.persistentEpisodes.isSelected()
							? "<br>" + (diagnostics().isEmpty() ? "No diagnostic above current tolerances"
									: diagnostics().stream().limit(2)
											.map(w -> w.label() + " [" + w.first + "-" + w.last + "]")
											.collect(java.util.stream.Collectors.joining("; ")))
							: "")
					+ "</html>");
			controls.warningSummary.setText(controls.persistentEpisodes.isSelected() && !diagnostics().isEmpty()
					? "Warnings: " + diagnostics().size() + " tracking/control diagnostics; inspect the plot or open Details."
					: "");
			controls.warningSummary.setVisible(!controls.warningSummary.getText().isEmpty());
			controls.status.setText(statusText());
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
		Capillary selected = (Capillary) controls.capillaries.getSelectedItem();
		boolean active = !released && !suspended && preview != null && selected == computedCapillary;
		overlays.update(experiment, selected, active ? preview : null, released || suspended,
				controls.showRois.isSelected(), controls.compensateEvaporation.isSelected(), controls.showGaps.isSelected(),
				active ? candidateAmplitudes() : null, active ? candidateBottoms() : null);
	}

	private void persistEditedIntake(boolean copy) {
		if (preview == null || isBackgroundBusy())
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
		jobs.edited = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				snapshot.prepare(this::publish);
				return null;
			}

			@Override
			protected void process(List<String> messages) {
				if (jobs.edited == this)
					controls.status.setText(messages.get(messages.size() - 1));
			}

			@Override
			protected void done() {
				if (jobs.edited == this)
					jobs.edited = null;
				if (released)
					return;
				updateModeControls();
				try {
					get();
					if (!snapshot.install(copy ? IntakePersistence.Destination.REPLACE_GULPS_AND_SAVE
							: IntakePersistence.Destination.SAVE_INTAKE)) {
						controls.status.setText("Levels changed; edited intake was not saved.");
						return;
					}
					if (copy)
						refreshGulpRois();
					saveInstalledResults(title, saved -> {
						if (saved) controls.status.setText("Saved intake for " + snapshot.caps.size() + " capillaries.");
					});
				} catch (InterruptedException | ExecutionException | RuntimeException error) {
					if (error instanceof InterruptedException)
						Thread.currentThread().interrupt();
					Throwable cause = error.getCause() == null ? error : error.getCause();
					controls.status.setText(cause.getMessage());
					JOptionPane.showMessageDialog(CorrelationIntakePanel.this, cause.getMessage(), title,
							JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		updateModeControls();
		controls.status.setText((copy ? "Copying edited intake to gulps for " : "Saving edited intake for ") + count
				+ " capillaries...");
		jobs.edited.execute();
	}

	private void saveInstalledResults(String title, Consumer<Boolean> completion) {
		if (jobs.save != null) throw new IllegalStateException("A save is already running");
		jobs.save = new SwingWorker<Boolean, Void>() {
			@Override protected Boolean doInBackground() {
				return experiment.save_capillaries_description_and_measures();
			}
			@Override protected void done() {
				if (jobs.save != this) return;
				jobs.save = null;
				boolean saved = false;
				Throwable failure = null;
				try {
					saved = get();
				} catch (InterruptedException error) {
					Thread.currentThread().interrupt(); failure = error;
				} catch (ExecutionException | RuntimeException error) {
					failure = error.getCause() == null ? error : error.getCause();
				}
				persistenceFailed = !saved;
				if (released) return;
				updateModeControls();
				refresh();
				completion.accept(saved);
				if (!saved) showPersistenceError(title, failure == null
						? new IllegalStateException("Could not save capillary measures. Results remain in memory; retry saving.") : failure);
			}
		};
		updateModeControls();
		controls.status.setText("Saving intake...");
		jobs.save.execute();
	}

	private void showPersistenceError(String title, Throwable error) {
		persistenceFailed = true;
		controls.status.setText("Save failed: " + error.getMessage());
		if (!java.awt.GraphicsEnvironment.isHeadless())
			JOptionPane.showMessageDialog(this, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
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
			} else if ((session.threshold(cap) != null)) {
				chosenThresholds.put(cap, session.threshold(cap));
				manualThreshold.put(cap, true);
			} else if (stored.isManualThreshold() && stored.hasMeasures()
					&& Double.isFinite(stored.getThresholdPxPerMin())) {
				chosenThresholds.put(cap, stored.getThresholdPxPerMin());
				manualThreshold.put(cap, true);
			} else
				manualThreshold.put(cap, false);
			edits.put(cap, session.hasEdits(cap) ? session.edits(cap).options() : stored.getMethodOptions());
		}
		return new EditedIntakeSnapshot(experiment, caps, controls.persistentEpisodes.isSelected(), controls.localEvaporation.isSelected(),
				chosenThresholds, manualThreshold, edits, cache);
	}

	private void exportExperimentReview() {
		if (preview == null || isBackgroundBusy())
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
		jobs.export = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				snapshot.write(file);
				return null;
			}

			@Override
			protected void done() {
				jobs.export = null;
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
		controls.status.setText("Exporting review CSVs and kymographs for every capillary...");
		jobs.export.execute();
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
					controls.persistentEpisodes.isSelected(), controls.localEvaporation.isSelected(), manualEdits());
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
		return controls.threshold.isEnabled();
	}

	@Override
	public boolean addGulpArmed() {
		return controls.addGulp.isEnabled() && controls.addGulp.isSelected();
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
		controls.threshold.setValue(pixelsPerMinute);
	}

	@Override
	public void editGulpRange(int first, int last) {
		editGulp(first, last);
	}

	@Override
	public boolean persistentEpisodes() {
		return controls.persistentEpisodes.isSelected();
	}

	@Override
	public boolean showDiagnostics() {
		return controls.showGaps.isSelected();
	}

	@Override
	public boolean compensateEvaporation() {
		return controls.compensateEvaporation.isSelected();
	}

	@Override
	public String formatNumber(double value) {
		return format(value);
	}

	IntakeReviewExport captureReviewExport() {
		EditedIntakeSnapshot snapshot = captureEditedIntake();
		return new IntakeReviewExport(experiment, snapshot.caps, snapshot.episodes, snapshot.local,
				snapshot.chosenThresholds, snapshot.manualThreshold, snapshot.edits, snapshot.previews);
	}

}
