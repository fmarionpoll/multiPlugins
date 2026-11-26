package plugins.fmp.multiSPOTS.dlg.fractions;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.entity.XYItemEntity;

import icy.gui.component.PopupPanel;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.dlg.fractions.FractionSession.ChartPick;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionAggregation.Group;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Input;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Row;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionData;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Illumination;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Key;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Reference;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceEstimation;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Review;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Settings;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Tolerance;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionStore;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

/**
 * Fraction workspace. Preview and reference actions update one in-memory plan;
 * Load/save writes that plan to SpotFractionSettings.xml. Primary measurements
 * and SpotsFractionMeasurements.csv are not written here.
 */
public final class _DlgCalculateFractions_ extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String FUTURE_CORRECTIONS = "Targeted corrections for an exceptional spot or image range are not in this workspace yet. "
			+ "They will supplement the general rules in Handle occlusions, following multiCAFE Edit and fly-detection Edit. "
			+ "SpotsFractionMeasurements.csv is not written by this version.";

	enum Step {
		OVERVIEW, REFERENCE, OCCLUSION, REVIEW
	}

	enum UnsavedChoice {
		SAVE, DISCARD, CANCEL
	}

	private MultiSPOTS parent;
	final JTabbedPane mainTabs = new JTabbedPane();
	final JComboBox<SpotMeasurementMethod> method = new JComboBox<>(SpotMeasurementMethod.values());
	final JComboBox<String> quantity = new JComboBox<>();
	final JComboBox<String> spotPicker = new JComboBox<>();
	final JComboBox<GapPolicy> similar = new JComboBox<>(GapPolicy.values());
	final JComboBox<GapPolicy> changing = new JComboBox<>(GapPolicy.values());
	final JComboBox<TailPolicy> tail = new JComboBox<>(TailPolicy.values());
	final JComboBox<Tolerance> toleranceMode = new JComboBox<>(Tolerance.values());
	final JComboBox<Illumination> illumination = new JComboBox<>(Illumination.values());
	final JComboBox<ReferenceEstimation> referenceEstimation = new JComboBox<>(ReferenceEstimation.values());
	final JSpinner baselineStart = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100000.0, 1.0));
	final JSpinner baselineEnd = new JSpinner(new SpinnerNumberModel(20.0, 0.0, 100000.0, 1.0));
	final JSpinner tolerance = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 1000000.0, .5));
	final JSpinner maximumGap = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 1000000.0, 1.0));
	final JSpinner median = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
	final JCheckBox spotOverride = new JCheckBox("Settings for selected spot only");
	final JCheckBox showAdvancedReference = new JCheckBox("Show advanced reference estimators");
	final JCheckBox showAdvancedOcclusion = new JCheckBox("Show advanced gap options");
	final JSpinner first = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
	final JSpinner last = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
	final JComboBox<SpotFractionChart.View> view = new JComboBox<>(SpotFractionChart.fractionViews());
	final JComboBox<SpotFractionChart.View> diagnosticView = new JComboBox<>(SpotFractionChart.diagnosticViews());
	final JComboBox<Group> aggregateGroup = new JComboBox<>();
	final JComboBox<SpotFractionAggregation.Measure> aggregateMeasure = new JComboBox<>(
			SpotFractionAggregation.Measure.values());
	final JCheckBox connectSimilar = new JCheckBox("Connect similar endpoints visually", true);
	final JTextField referenceValue = new JTextField(8);
	final JTextField referenceTime = new JTextField(8);
	final JTextField referenceSource = new JTextField(28);
	final JTable spots;
	final JTable reviews;
	final JTable issues;

	private final JLabel unsaved = new JLabel("No unsaved changes");
	private final JTextArea inputStatus = note(2);
	private final JTextArea spotStatus = note(2);
	private final JTextArea status = note(2);
	private final JLabel referenceStatus = new JLabel(" ");
	private final JTextArea context = note(2);
	private final JTextArea report = new JTextArea(6, 40);
	private final DefaultTableModel spotModel = tableModel("Spot", "Cage", "Reference", "Reference source", "State",
			"Marked gaps");
	private final DefaultTableModel reviewModel = tableModel("Experiment", "Spot", "Method", "Quantity", "Reason");
	private final DefaultTableModel issueModel = tableModel("Spot", "Issue");
	private final ChartPanel individualChart = new ChartPanel(null);
	private final ChartPanel aggregateChart = new ChartPanel(null);
	private final ChartPanel diagnosticsChart = new ChartPanel(null);
	private final CardLayout stepLayout = new CardLayout();
	private final JPanel stepPanel = new JPanel(stepLayout);
	private final JPanel advancedReference = new JPanel();
	private final JPanel advancedOcclusion = new JPanel();
	private final List<JButton> actionButtons = new ArrayList<>();
	private final JButton previewButton = button("Preview calculation", this::preview);
	private final JButton backButton = button("Back to the three steps", () -> showStep(Step.OVERVIEW));
	private final JButton approveButton = button("Approve reference", this::approveReference);
	private final JButton borrowButton = button("Borrow initial reference", () -> borrow(false));
	private final JButton preconsumedButton = button("Mark pre-consumed", () -> borrow(true));
	private final JButton excludeButton = button("Exclude failed deposit", this::excludeDeposit);
	private final JButton removeOverrideButton = button("Remove spot override", this::removeOverride);
	private final JButton restoreButton = button("Restore saved fraction settings and references", this::restoreSaved);
	private final JButton saveButton = button("Save fraction settings and references", this::save);
	private final JButton batchButton = button("Apply & save range", this::batch);
	private final JButton reviewListButton = button("Saved review list", this::loadReviewList);
	private final JButton openReviewButton = button("Open selected review item", this::openReview);
	private final JButton locateButton = button("Locate spot", this::locateSpot);
	private final JButton openReferenceButton = button("Open Find hmax - initial reference", this::openReferenceFromChart);
	private final JButton referenceIssueButton = button("Open reference step for this issue",
			() -> openIssue(Step.REFERENCE));
	private final JButton occlusionIssueButton = button("Open occlusion step for this issue",
			() -> openIssue(Step.OCCLUSION));
	private final JButton stop = new JButton("Stop after current experiment");

	private State current;
	private SpotFractionPlan savedPlan;
	private Settings formBaseline = Settings.DEFAULT;
	private final Map<Key, Settings> pendingSettings = new LinkedHashMap<>();
	private final Map<String, Draft> referenceDrafts = new LinkedHashMap<>();
	private List<SavedReview> savedReviews = new ArrayList<>();
	private Key controlsKey;
	private String referenceSpot;
	private Step step = Step.OVERVIEW;
	private boolean busy;
	private boolean updating;
	private boolean refreshing;
	private volatile boolean cancelRequested;
	private boolean suppressClose;
	private String reviewSpotToSelect;
	private ChartPick pendingChartPick;
	private IcyFrame workspace;
	private int acceptedExperimentIndex = -1;
	private SpotMeasurementMethod acceptedMethod;
	private String acceptedQuantity;
	boolean synchronousForTest;
	UnsavedChoice unsavedChoiceForTest;
	private Target testTarget;
	private String testBin = "";
	private List<Input> testInputs;
	private SpotMeasurementMethod testInputMethod;
	private String testInputQuantity;

	public _DlgCalculateFractions_() {
		super(new BorderLayout(4, 4));
		spots = new JTable(spotModel);
		reviews = new JTable(reviewModel);
		issues = new JTable(issueModel);
		for (JComboBox<?> combo : Arrays.asList(method, quantity, spotPicker, similar, changing, tail, toleranceMode,
				illumination, referenceEstimation, view, diagnosticView, aggregateMeasure, aggregateGroup))
			combo.setRenderer(renderer());
		add(buildHeader(), BorderLayout.NORTH);
		mainTabs.addTab("Calculate fractions", buildCalculateTab());
		mainTabs.addTab("Charts", buildChartsTab());
		mainTabs.addTab("Load/save", buildLoadSaveTab());
		add(mainTabs, BorderLayout.CENTER);
		add(status, BorderLayout.SOUTH);
		setMinimumSize(new Dimension(760, 520));
		readControls(Settings.DEFAULT);
		setQuantities();
		formBaseline = Settings.DEFAULT;
		controlsKey = selection();
		wireListeners();
		view.setSelectedItem(SpotFractionChart.View.FRACTION_CONSUMED);
		showStep(Step.OVERVIEW);
		refreshInputStatus();
		refreshActionStates();
	}

	public void init(JPanel mainPanel, String title, MultiSPOTS parent0) {
		this.parent = parent0;
		PopupPanel popup = new PopupPanel(title);
		popup.getMainPanel().setLayout(new BorderLayout());
		JButton open = new JButton("Open calculation workspace");
		open.addActionListener(event -> openWorkspace());
		popup.getMainPanel().add(row(new JLabel("Saved measurements, then references, gaps and fractions"), open),
				BorderLayout.CENTER);
		popup.collapse();
		mainPanel.add(popup);
		parent.expListComboLazy.addItemListener(e -> {
			if (updating || busy || e.getStateChange() != ItemEvent.SELECTED)
				return;
			int now = parent.expListComboLazy.getSelectedIndex();
			if (now == acceptedExperimentIndex)
				return;
			int previous = acceptedExperimentIndex;
			Key key = current != null ? current.selection : new Key(acceptedMethod, acceptedQuantity, "");
			Target oldTarget = current != null ? current.target : safeTarget(previous, acceptedMethod);
			if (!resolveUnsaved("Change experiment", key, oldTarget, false)) {
				updating = true;
				if (previous >= 0)
					parent.expListComboLazy.setSelectedIndex(previous);
				updating = false;
				return;
			}
			acceptedExperimentIndex = now;
			clearForNewSelection(true);
			int shown = Math.max(1, now + 1);
			first.setValue(shown);
			last.setValue(shown);
			status.setText("Experiment changed. Restore saved settings or preview to calculate.");
		});
		acceptedExperimentIndex = parent.expListComboLazy.getSelectedIndex();
		setQuantities();
		int shown = Math.max(1, acceptedExperimentIndex + 1);
		first.setValue(shown);
		last.setValue(shown);
		refreshInputStatus();
	}

	private void openWorkspace() {
		if (workspace == null || !workspace.isVisible()) {
			workspace = new IcyFrame("Calculate spot fractions", true, true, true, true);
			workspace.setLayout(new BorderLayout());
			workspace.add(this, BorderLayout.CENTER);
			workspace.setSize(1100, 850);
			workspace.setMinimumSizeInternal(new Dimension(780, 560));
			workspace.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			workspace.addFrameListener(new IcyFrameAdapter() {
				@Override
				public void icyFrameClosing(IcyFrameEvent event) {
					if (suppressClose)
						return;
					if (!attemptClose())
						return;
					suppressClose = true;
					workspace.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
					workspace.close();
				}
			});
			workspace.addToDesktopPane();
			workspace.center();
		}
		suppressClose = false;
		workspace.setVisible(true);
		workspace.requestFocus();
	}

	/** @return false when the workspace must stay open */
	boolean attemptClose() {
		if (suppressClose)
			return true;
		Key key = current != null ? current.selection : selection();
		if (!resolveUnsaved("Close the calculation workspace", key, saveTarget(), false))
			return false;
		return true;
	}

	private JPanel buildHeader() {
		JPanel header = column();
		header.add(row(new JLabel("Measurements"), method, new JLabel("Quantity"), quantity, new JLabel("Spot"),
				spotPicker, unsaved));
		header.add(inputStatus);
		header.add(spotStatus);
		inputStatus.setText(
				"No experiment selected. This workspace reads existing primary files and does not acquire images or rewrite them.");
		spotPicker.setToolTipText("The selected spot stays in effect on every tab and calculation step.");
		return header;
	}

	private JPanel buildCalculateTab() {
		JPanel tab = new JPanel(new BorderLayout(3, 3));
		tab.add(row(previewButton, backButton), BorderLayout.NORTH);
		stepPanel.add(buildOverview(), Step.OVERVIEW.name());
		stepPanel.add(scroll(buildReference()), Step.REFERENCE.name());
		stepPanel.add(scroll(buildOcclusion()), Step.OCCLUSION.name());
		stepPanel.add(buildReview(), Step.REVIEW.name());
		spots.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		spots.setAutoCreateRowSorter(true);
		JScrollPane spotScroll = new JScrollPane(spots);
		spotScroll.setPreferredSize(new Dimension(760, 120));
		JPanel center = new JPanel(new BorderLayout());
		center.add(context, BorderLayout.NORTH);
		center.add(stepPanel, BorderLayout.CENTER);
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, spotScroll);
		split.setResizeWeight(.75);
		tab.add(split, BorderLayout.CENTER);
		return tab;
	}

	private JPanel buildOverview() {
		JPanel panel = column();
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.add(note(
				"Establish the initial reference, then the general gap rules. Preview updates the working calculation without writing a file. "
						+ "Return here with Back to the three steps. Saving is on the Load/save tab."));
		panel.add(Box.createVerticalStrut(8));
		panel.add(stepButton("1. Find hmax - initial reference",
				"hmax is the original unconsumed deposit for this spot, method and quantity. It is not the largest measurement in the recording.",
				Step.REFERENCE));
		panel.add(stepButton("2. Handle occlusions",
				"General rules for gaps: similar endpoints, changed endpoints, tolerance, duration and the final gap.",
				Step.OCCLUSION));
		panel.add(stepButton("3. Edit/view fractions",
				"Review calculated status and open the step that can resolve an issue. Targeted corrections are later work.",
				Step.REVIEW));
		panel.add(Box.createVerticalStrut(8));
		panel.add(note(FUTURE_CORRECTIONS));
		return panel;
	}

	private JPanel stepButton(String title, String explanation, Step destination) {
		JPanel panel = column();
		JButton open = button(title, () -> showStep(destination));
		open.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(open);
		panel.add(note(explanation));
		panel.add(Box.createVerticalStrut(6));
		return panel;
	}

	private JPanel buildReference() {
		JPanel panel = column();
		panel.add(note(
				"hmax is the original, unconsumed deposit for the selected spot, method and quantity. It is not the largest measurement anywhere in the recording."));
		panel.add(note(
				"Initial: the first sample is a valid positive deposit. Delayed: the first usable view is later; approving it does not recover earlier consumption. "
						+ "Borrowed: median of donor references you choose; donor names are kept. External: a value you type or pick from a valid observation, with a justification. "
						+ "Unresolved: no reference yet. Excluded: a failed deposit, left out of aggregates rather than treated as consumption."));
		panel.add(referenceStatus);
		panel.add(row(new JLabel("Reference value"), referenceValue, new JLabel("Time (seconds)"), referenceTime));
		panel.add(row(new JLabel("Source / justification"), referenceSource));
		panel.add(row(approveButton, borrowButton, preconsumedButton, excludeButton, removeOverrideButton));
		approveButton.setToolTipText(
				"Validate this reference into the working calculation. Approving a delayed observation does not recover consumption before that time. Saving to disk is separate.");
		borrowButton.setToolTipText(
				"Use the median of approved initial references from the same cage. Donor names are stored. Failed deposits are not donors.");
		preconsumedButton.setToolTipText(
				"The first clear view is already partly consumed. Estimate hmax from comparable approved donors in the same cage, stimulus and concentration.");
		excludeButton.setToolTipText(
				"Mark a failed deposit. The spot is omitted from aggregates and is not converted into apparent consumption.");
		removeOverrideButton.setToolTipText(
				"Delete the selected spot's settings override. It then inherits the experiment settings.");
		showAdvancedReference.setToolTipText(
				"Maximum-observation and baseline-median estimators are optional. Saved experiments keep whichever estimator was stored.");
		advancedReference.setLayout(new BoxLayout(advancedReference, BoxLayout.Y_AXIS));
		advancedReference.add(note(
				"Maximum valid observation is the largest valid sample, not hmax. Earlier consumption is ignored, and a later peak can overstate the original deposit."));
		advancedReference.add(note(
				"Baseline median uses the chosen no-feeding period. It needs at least three valid samples and a positive median. It does not replace a missing first frame."));
		advancedReference.add(row(new JLabel("Estimate reference"), referenceEstimation));
		advancedReference.add(row(new JLabel("Baseline period (minutes)"), baselineStart, new JLabel("to"), baselineEnd));
		referenceEstimation.setToolTipText(
				"These estimators use valid raw observations only, before gap filling or smoothing. Explicit and borrowed references remain in effect.");
		baselineStart.setToolTipText(
				"Choose a verified period with no feeding. At least three unflagged observations and a positive median are required per spot.");
		baselineEnd.setToolTipText(baselineStart.getToolTipText());
		panel.add(showAdvancedReference);
		panel.add(advancedReference);
		advancedReference.setVisible(false);
		return panel;
	}

	private JPanel buildOcclusion() {
		JPanel panel = column();
		panel.add(note(
				"Similar endpoints lie within tolerance. Linear interpolation fills that gap with an estimate, not a measurement. "
						+ "Changed endpoints differ by more than the tolerance. A fill is still only an estimate, and the interval stays marked."));
		panel.add(note(
				"An initial gap stays missing. The series is never extended backward. A gap is eligible only when every missing sample is an ordinary fly occlusion, both brackets are usable, and the bracket duration is within the maximum. "
						+ "Missing images and failed detections are not fly visits and are not filled by these rules."));
		panel.add(row(new JLabel("Similar endpoints"), similar, new JLabel("Changed endpoints"), changing));
		panel.add(row(new JLabel("Tolerance"), toleranceMode, tolerance, new JLabel("Maximum gap (minutes)"), maximumGap));
		panel.add(row(new JLabel("Final gap"), tail, spotOverride));
		similar.setToolTipText(
				"Policy used only when the bracketing values agree within tolerance. Linear interpolation is an estimate.");
		changing.setToolTipText(
				"Policy used when the bracketing values disagree. The interval remains marked as a change.");
		tail.setToolTipText(
				"A final gap can stay missing, hold the last valid value as an explicit estimate, or request review. It is not an ordinary internal occlusion.");
		maximumGap.setToolTipText("Longer brackets stay missing, including when the endpoints would otherwise qualify.");
		toleranceMode.setToolTipText(
				"Percentage is relative to the fixed initial reference. Robust noise uses three sigma from the MAD of adjacent valid differences and needs at least five pairs.");
		spotOverride.setToolTipText(
				"Store these gap settings on the selected spot. Other spots keep the experiment settings. References are never copied by a range update.");
		showAdvancedOcclusion.setToolTipText(
				"Smoothing and common multiplicative drift are optional. Both stay out of the reference itself.");
		advancedOcclusion.setLayout(new BoxLayout(advancedOcclusion, BoxLayout.Y_AXIS));
		advancedOcclusion.add(note(
				"Median smoothing runs inside finite stretches of real samples. It does not create a reference and does not fill gaps. Zero turns it off."));
		advancedOcclusion.add(note(
				"Common multiplicative drift is off by default. It estimates a shared factor and does not model additive or nonlinear illumination. Inspect the diagnostic chart before using it as a biological correction."));
		advancedOcclusion.add(row(new JLabel("Median radius (samples; 0 = off)"), median));
		advancedOcclusion.add(row(new JLabel("Common multiplicative drift"), illumination));
		illumination.setToolTipText(
				"Optional estimate: at most two changing spots per cage, and a strict majority of valid aligned spots (at least five in one cage). Insufficient support leaves the factor unknown.");
		median.setToolTipText(advancedOcclusion.getComponent(0) instanceof JTextArea
				? ((JTextArea) advancedOcclusion.getComponent(0)).getText()
				: "Median radius in samples. Zero disables smoothing.");
		panel.add(showAdvancedOcclusion);
		panel.add(advancedOcclusion);
		advancedOcclusion.setVisible(false);
		return panel;
	}

	private JPanel buildReview() {
		JPanel panel = new JPanel(new BorderLayout(3, 3));
		JPanel north = column();
		north.add(note(
				"This step shows the working calculation: ready spots, excluded deposits, and issues still open. It does not edit individual samples."));
		north.add(note(FUTURE_CORRECTIONS));
		north.add(row(referenceIssueButton, occlusionIssueButton));
		panel.add(north, BorderLayout.NORTH);
		issues.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		panel.add(new JScrollPane(issues), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildChartsTab() {
		JTabbedPane charts = new JTabbedPane();
		JPanel individual = new JPanel(new BorderLayout(3, 3));
		individual.add(column(row(new JLabel("Display"), view, connectSimilar, locateButton, openReferenceButton), note(
				"Solid marks are observations. Dashed marks are estimates or a display-only connection. Missing values and negative consumption are kept. "
						+ "A visual connection does not fill the underlying value. Click a valid positive observation to copy its original measurement into the reference editor.")),
				BorderLayout.NORTH);
		individualChart.setPreferredSize(new Dimension(760, 360));
		individual.add(individualChart, BorderLayout.CENTER);
		charts.addTab("Individual fractions", individual);
		JPanel aggregates = new JPanel(new BorderLayout(3, 3));
		aggregates.add(row(new JLabel("Group"), aggregateGroup, new JLabel("Fraction aggregate"), aggregateMeasure),
				BorderLayout.NORTH);
		aggregates.add(aggregateChart, BorderLayout.CENTER);
		aggregates.add(note(
				"Membership is fixed by cage, stimulus and concentration. Totals and means stay missing until every non-excluded spot contributes. The lower plot is available versus expected spots. Visual connections are not contributions."),
				BorderLayout.SOUTH);
		charts.addTab("Aggregated fractions", aggregates);
		JPanel diagnostics = new JPanel(new BorderLayout(3, 3));
		diagnostics.add(row(new JLabel("Selected spot"), diagnosticView), BorderLayout.NORTH);
		diagnostics.add(diagnosticsChart, BorderLayout.CENTER);
		diagnostics.add(note(
				"Drift factor and the number of spots that supported it. This is a diagnostic for the optional common-drift estimate, not a fraction."),
				BorderLayout.SOUTH);
		charts.addTab("Correction diagnostics", diagnostics);
		connectSimilar.setToolTipText(
				"Draws a dashed line across similar-endpoint gaps that were left missing. It does not replace those NaNs.");
		locateButton.setToolTipText("Show this spot in the camera viewer when that viewer is open.");
		openReferenceButton.setToolTipText(
				"Go to Find hmax. A chart click fills the reference from the original measurement, not from the plotted fraction.");
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(charts, BorderLayout.CENTER);
		return wrap;
	}

	private JComponent buildLoadSaveTab() {
		JPanel panel = column();
		panel.add(section("This experiment", note(
				"Restore and Save move SpotFractionSettings.xml: processing settings, reference decisions and the saved review list. "
						+ "They do not write SpotsFractionMeasurements.csv and they do not modify primary measurement files."),
				row(restoreButton, saveButton)));
		restoreButton.setToolTipText(
				"Replace the working settings and references with the file on disk. Unsaved work is saved, discarded, or left as it is.");
		saveButton.setToolTipText(
				"Write the working settings and references to SpotFractionSettings.xml. A failed save keeps the edits and leaves this workspace open.");
		panel.add(section("Experiment range", note(
				"The inclusive 1-based range receives the experiment-level settings now in effect. Approved references are not copied between experiments. "
						+ "Spot overrides already in each file are kept. Stop finishes the experiment in progress and leaves the rest for a later range."),
				row(new JLabel("Experiments"), first, new JLabel("to"), last, batchButton, stop)));
		stop.setToolTipText("Finish the experiment in progress, keep what was saved, and do not start the next one.");
		panel.add(section("Saved review list", note(
				"These are the pending items stored in the last saved calculation files for the range, including the active vertical bin. "
						+ "Opening an item loads that experiment, method, quantity and spot. It does not load images."),
				new JScrollPane(reviews), row(reviewListButton, openReviewButton)));
		reviews.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JScrollPane reportScroll = new JScrollPane(report);
		report.setEditable(false);
		report.setLineWrap(true);
		report.setWrapStyleWord(true);
		panel.add(section("Batch report", note("Per-experiment results from the latest range update or review-list read."),
				reportScroll));
		return scroll(panel);
	}

	private void wireListeners() {
		method.addActionListener(e -> {
			if (updating)
				return;
			SpotMeasurementMethod chosen = (SpotMeasurementMethod) method.getSelectedItem();
			if (chosen == null || chosen == acceptedMethod)
				return;
			Key key = new Key(acceptedMethod, acceptedQuantity, "");
			Target oldTarget = current != null ? current.target : safeTarget(acceptedExperimentIndex, acceptedMethod);
			if (!resolveUnsaved("Change measurement method", key, oldTarget, false)) {
				updating = true;
				method.setSelectedItem(acceptedMethod);
				updating = false;
				return;
			}
			acceptedMethod = chosen;
			clearForNewSelection(true);
			status.setText("Method changed. Restore saved settings or preview to calculate.");
		});
		quantity.addActionListener(e -> {
			if (updating)
				return;
			String chosen = (String) quantity.getSelectedItem();
			if (chosen == null || chosen.equals(acceptedQuantity))
				return;
			Key key = new Key(acceptedMethod, acceptedQuantity, "");
			Target oldTarget = current != null ? current.target : saveTarget();
			if (!resolveUnsaved("Change measured quantity", key, oldTarget, false)) {
				updating = true;
				quantity.setSelectedItem(acceptedQuantity);
				updating = false;
				return;
			}
			acceptedQuantity = chosen;
			clearForNewSelection(false);
			status.setText("Quantity changed. References and settings for this quantity are separate.");
		});
		spotPicker.addActionListener(e -> {
			if (!updating)
				selectSpotByName((String) spotPicker.getSelectedItem());
		});
		spots.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && !updating)
				selectionChanged();
		});
		issues.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && !updating)
				refreshActionStates();
		});
		reviews.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && !updating)
				refreshActionStates();
		});
		spotOverride.addActionListener(e -> {
			if (!updating)
				retargetSettings(activeSettingsKey());
		});
		for (JComboBox<?> combo : Arrays.asList(similar, changing, tail, illumination))
			combo.addActionListener(e -> {
				if (!updating)
					refreshActionStates();
			});
		toleranceMode.addActionListener(e -> {
			syncTolerance();
			if (!updating)
				refreshActionStates();
		});
		referenceEstimation.addActionListener(e -> {
			syncBaselineControls();
			if (!updating)
				refreshActionStates();
		});
		for (JSpinner spinner : Arrays.asList(tolerance, maximumGap, median, baselineStart, baselineEnd))
			spinner.addChangeListener(e -> {
				if (!updating)
					refreshActionStates();
			});
		DocumentListener documents = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				documentEdited();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				documentEdited();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				documentEdited();
			}
		};
		referenceValue.getDocument().addDocumentListener(documents);
		referenceTime.getDocument().addDocumentListener(documents);
		referenceSource.getDocument().addDocumentListener(documents);
		showAdvancedReference.addActionListener(e -> {
			advancedReference.setVisible(showAdvancedReference.isSelected());
			revalidate();
		});
		showAdvancedOcclusion.addActionListener(e -> {
			advancedOcclusion.setVisible(showAdvancedOcclusion.isSelected());
			revalidate();
		});
		view.addActionListener(e -> updateChart());
		connectSimilar.addActionListener(e -> updateChart());
		diagnosticView.addActionListener(e -> updateChart());
		aggregateGroup.addActionListener(e -> {
			if (!updating)
				updateAggregateChart();
		});
		aggregateMeasure.addActionListener(e -> updateAggregateChart());
		mainTabs.addChangeListener(e -> {
			if (!updating)
				updateChart();
		});
		stop.addActionListener(e -> {
			cancelRequested = true;
			stop.setEnabled(false);
		});
		individualChart.addChartMouseListener(new ChartMouseListener() {
			@Override
			public void chartMouseMoved(ChartMouseEvent event) {
			}

			@Override
			public void chartMouseClicked(ChartMouseEvent event) {
				Row row = selectedRow();
				if (row == null || busy || !(event.getEntity() instanceof XYItemEntity))
					return;
				int sample = ((XYItemEntity) event.getEntity()).getItem();
				if (!fillReferenceFromSample(row, sample))
					status.setText(
							"Choose a valid positive observation. Occluded, missing and non-positive samples cannot set hmax, and the plotted fraction is not used.");
			}
		});
		syncTolerance();
		syncBaselineControls();
	}

	private void documentEdited() {
		if (!updating)
			refreshActionStates();
	}

	void showStep(Step next) {
		step = next;
		stepLayout.show(stepPanel, next.name());
		backButton.setVisible(next != Step.OVERVIEW);
		refreshActionStates();
	}

	Step testingStep() {
		return step;
	}

	boolean testingUnsaved() {
		return isUnsaved();
	}

	Row testingRow(String name) {
		return rowByName(name);
	}

	SpotFractionPlan testingPlan() {
		return current == null ? null : current.plan;
	}

	void useTestingTarget(Path results, String bin, List<Input> inputs, SpotMeasurementMethod inputMethod,
			String inputQuantity) {
		testBin = bin == null ? "" : bin;
		testInputs = inputs;
		testInputMethod = inputMethod;
		testInputQuantity = inputQuantity;
		testTarget = new Target(0, "1: " + results, results,
				FractionSession.measurementsDirectory(results, inputMethod == null ? acceptedMethod : inputMethod, testBin),
				null);
		refreshInputStatus();
		refreshActionStates();
	}

	private void setQuantities() {
		boolean was = updating;
		updating = true;
		String previous = (String) quantity.getSelectedItem();
		quantity.removeAllItems();
		SpotMeasurementMethod selected = (SpotMeasurementMethod) method.getSelectedItem();
		Set<String> ids = new LinkedHashSet<>();
		if (parent != null && parent.expListComboLazy.getSelectedIndex() >= 0) {
			Experiment exp = parent.expListComboLazy.getItemAtNoLoad(parent.expListComboLazy.getSelectedIndex());
			if (exp != null)
				for (Spot spot : exp.getSpots().getSpotList()) {
					SpotMethodResult result = spot.getMethodMeasurements().get(selected);
					if (result != null)
						ids.addAll(result.getQuantities().keySet());
				}
		}
		ids.remove("background");
		if (ids.isEmpty()) {
			if (selected == SpotMeasurementMethod.FILTER)
				ids.addAll(Arrays.asList("thresholded_signal", "fly_pixel_excluded_signal"));
			else if (selected == SpotMeasurementMethod.COLOR)
				ids.addAll(Arrays.asList("grey_signal", "selected_area"));
			else if (selected == SpotMeasurementMethod.VERTICAL_LINE)
				ids.addAll(Arrays.asList("green_height", "row_fraction"));
			else
				ids.addAll(Arrays.asList("signal_fraction", "visible_signal_fraction"));
		}
		if (selected == SpotMeasurementMethod.VERTICAL_LINE)
			ids.add("color_integral");
		for (String id : ids)
			quantity.addItem(id);
		if (previous != null && ids.contains(previous))
			quantity.setSelectedItem(previous);
		acceptedMethod = selected;
		acceptedQuantity = (String) quantity.getSelectedItem();
		updating = was;
	}

	Settings controls() {
		try {
			baselineStart.commitEdit();
			baselineEnd.commitEdit();
			tolerance.commitEdit();
			maximumGap.commitEdit();
			median.commitEdit();
		} catch (java.text.ParseException ex) {
			throw new IllegalArgumentException("Enter a valid number in the settings fields", ex);
		}
		return new Settings((GapPolicy) similar.getSelectedItem(), (GapPolicy) changing.getSelectedItem(),
				(TailPolicy) tail.getSelectedItem(), ((Number) maximumGap.getValue()).doubleValue() * 60,
				(Tolerance) toleranceMode.getSelectedItem(), ((Number) tolerance.getValue()).doubleValue(),
				((Number) median.getValue()).intValue(), (Illumination) illumination.getSelectedItem(),
				(ReferenceEstimation) referenceEstimation.getSelectedItem(),
				((Number) baselineStart.getValue()).doubleValue() * 60,
				((Number) baselineEnd.getValue()).doubleValue() * 60);
	}

	private void syncBaselineControls() {
		boolean enabled = !busy && referenceEstimation.getSelectedItem() == ReferenceEstimation.BASELINE_MEDIAN;
		baselineStart.setEnabled(enabled);
		baselineEnd.setEnabled(enabled);
		String why = enabled ? baselineStart.getToolTipText()
				: "The baseline period is used only by the baseline-median estimator.";
		baselineStart.setToolTipText(why);
		baselineEnd.setToolTipText(why);
	}

	private void syncTolerance() {
		boolean robust = toleranceMode.getSelectedItem() == Tolerance.ROBUST_NOISE;
		tolerance.setEnabled(!busy && !robust);
		tolerance.setToolTipText(robust
				? "Disabled while tolerance is estimated from adjacent valid samples (at least five pairs)."
				: "Percentage is relative to the fixed initial reference. Absolute tolerance uses measurement units.");
	}

	private void readControls(Settings settings) {
		boolean was = updating;
		updating = true;
		similar.setSelectedItem(settings.similarGap);
		changing.setSelectedItem(settings.changingGap);
		tail.setSelectedItem(settings.tail);
		maximumGap.setValue(settings.maximumGapSeconds / 60);
		toleranceMode.setSelectedItem(settings.toleranceMode);
		tolerance.setValue(settings.tolerance);
		median.setValue(settings.medianRadius);
		illumination.setSelectedItem(settings.illumination);
		referenceEstimation.setSelectedItem(settings.referenceEstimation);
		baselineStart.setValue(settings.baselineStartSeconds / 60);
		baselineEnd.setValue(settings.baselineEndSeconds / 60);
		if (settings.referenceEstimation != ReferenceEstimation.INITIAL_OBSERVATION)
			showAdvancedReference.setSelected(true);
		if (settings.medianRadius > 0 || settings.illumination != Illumination.NONE)
			showAdvancedOcclusion.setSelected(true);
		advancedReference.setVisible(showAdvancedReference.isSelected());
		advancedOcclusion.setVisible(showAdvancedOcclusion.isSelected());
		syncBaselineControls();
		syncTolerance();
		updating = was;
	}

	private Key selection() {
		String selectedQuantity = (String) quantity.getSelectedItem();
		if (selectedQuantity == null)
			selectedQuantity = acceptedQuantity == null ? "signal" : acceptedQuantity;
		SpotMeasurementMethod selectedMethod = (SpotMeasurementMethod) method.getSelectedItem();
		if (selectedMethod == null)
			selectedMethod = acceptedMethod == null ? SpotMeasurementMethod.FILTER : acceptedMethod;
		return new Key(selectedMethod, selectedQuantity, "");
	}

	static final class Target {
		final int index;
		final String label;
		final Path results;
		final Path measures;
		final Experiment experiment;

		Target(int index, String label, Path results, Path measures, Experiment experiment) {
			this.index = index;
			this.label = label;
			this.results = results;
			this.measures = measures;
			this.experiment = experiment;
		}
	}

	Target target(int index, SpotMeasurementMethod selected) {
		if (parent == null) {
			if (testTarget == null)
				throw new IllegalArgumentException("Choose an experiment");
			return new Target(testTarget.index, testTarget.label, testTarget.results,
					FractionSession.measurementsDirectory(testTarget.results, selected, testBin), testTarget.experiment);
		}
		if (index < 0 || index >= parent.expListComboLazy.getItemCount())
			throw new IllegalArgumentException("Choose an experiment");
		Experiment exp = parent.expListComboLazy.getItemAtNoLoad(index);
		if (exp == null || exp.getResultsDirectory() == null)
			throw new IllegalArgumentException("Experiment has no results directory");
		Path results = Paths.get(exp.getResultsDirectory());
		String bin = "";
		if (selected.isKymographDerived()) {
			bin = parent.expListComboLazy.expListBinSubDirectory;
			if (bin == null || bin.isEmpty()) {
				if (exp instanceof LazyExperiment)
					bin = ((LazyExperiment) exp).getMetadata().getBinDirectory();
				else if (exp.getKymosBinFullDirectory() != null)
					bin = exp.getKymosBinFullDirectory();
			}
		}
		return new Target(index, (index + 1) + ": " + results, results,
				FractionSession.measurementsDirectory(results, selected, bin), exp);
	}

	private Target safeTarget(int index, SpotMeasurementMethod selected) {
		try {
			if (selected == null)
				return null;
			return target(index, selected);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private Target saveTarget() {
		if (current != null)
			return current.target;
		return safeTarget(parent == null ? 0 : acceptedExperimentIndex,
				acceptedMethod == null ? (SpotMeasurementMethod) method.getSelectedItem() : acceptedMethod);
	}

	private Target targetForSelection() {
		return target(parent == null ? 0 : parent.expListComboLazy.getSelectedIndex(),
				(SpotMeasurementMethod) method.getSelectedItem());
	}

	static final class State {
		final Target target;
		final Key selection;
		final SpotFractionPlan plan;
		final List<Input> inputs;
		final List<Row> rows;
		final List<Group> groups;

		State(Target target, Key selection, SpotFractionPlan plan, List<Input> inputs, List<Row> rows) {
			this.target = target;
			this.selection = selection;
			this.plan = plan;
			this.inputs = inputs;
			this.rows = rows;
			this.groups = SpotFractionAggregation.build(inputs, rows, selection, plan);
		}
	}

	private List<Input> inputs(Target target, SpotMeasurementMethod selected, String selectedQuantity, boolean current)
			throws Exception {
		if (testInputs != null && selected == testInputMethod && selectedQuantity.equals(testInputQuantity))
			return testInputs;
		if (current && target.experiment != null && target.experiment.getSpots().getSpotList().stream()
				.anyMatch(s -> s.getMethodMeasurements().get(selected) != null
						&& s.getMethodMeasurements().get(selected).getQuantities().containsKey(selectedQuantity)))
			return SpotFractionData.fromSpots(target.experiment.getSpots().getSpotList(), selected);
		return SpotFractionData.read(target.results, target.measures, selected, selectedQuantity);
	}

	private void preview() {
		run(new Task() {
			@Override
			State execute() throws Exception {
				previewNow();
				return current;
			}

			@Override
			void complete(State state) {
			}
		}, false);
	}

	private void save() {
		run(new Task() {
			@Override
			State execute() throws Exception {
				saveNow(selection(), saveTarget());
				return current;
			}

			@Override
			void complete(State state) {
			}
		}, false);
	}

	private void previewNow() throws Exception {
		Key key = selection();
		Computed computed = compute(key, targetForSelection());
		pendingSettings.clear();
		show(computed.state);
		status.setText("Preview updated the working calculation. Nothing was written to disk.");
	}

	private void saveNow(Key key, Target target) throws Exception {
		Computed computed = compute(key, target);
		SpotFractionStore.write(target.measures,
				FractionSession.mergeSelection(SpotFractionStore.read(target.measures), computed.plan, key));
		savedPlan = SpotFractionStore.read(target.measures);
		pendingSettings.clear();
		referenceDrafts.clear();
		formBaseline = computed.plan.settingsFor(key);
		show(computed.state);
		status.setText("Saved fraction settings and references. Primary measurements were not modified.");
	}

	private Computed compute(Key key, Target target) throws Exception {
		if (target == null || key == null)
			throw new IllegalArgumentException("Choose an experiment before calculating fractions");
		Row selected = selectedRow();
		if (spotOverride.isSelected() && selected == null)
			throw new IllegalArgumentException("Select a spot before using settings for that spot only");
		Settings chosen = controls();
		Key settingKey = key.forSpot(spotOverride.isSelected() && selected != null ? selected.input.name : "");
		if (controlsKey != null && !controlsKey.equals(settingKey))
			pendingSettings.remove(controlsKey);
		controlsKey = settingKey;
		SpotFractionPlan base = current != null && current.target.measures.equals(target.measures)
				&& current.selection.equals(key) ? current.plan.copy() : SpotFractionStore.read(target.measures);
		for (Map.Entry<Key, Settings> entry : pendingSettings.entrySet())
			if (entry.getKey().method == key.method && entry.getKey().quantity.equals(key.quantity)
					&& !entry.getKey().equals(settingKey))
				base.settings.put(entry.getKey(), entry.getValue());
		base.settings.put(settingKey, chosen);
		List<Input> loaded = inputs(target, key.method, key.quantity, true);
		List<Row> rows = SpotFractionCalculator.calculate(loaded, key, base);
		return new Computed(new State(target, key, base, loaded, rows), base);
	}

	private static final class Computed {
		final State state;
		final SpotFractionPlan plan;

		Computed(State state, SpotFractionPlan plan) {
			this.state = state;
			this.plan = plan;
		}
	}

	static SpotFractionPlan mergeSelection(SpotFractionPlan saved, SpotFractionPlan preview, Key key) {
		return FractionSession.mergeSelection(saved, preview, key);
	}

	private void restoreSaved() {
		Key key = selection();
		Target target = saveTarget();
		if (!resolveUnsaved("Restore saved fraction settings and references", key, target, false))
			return;
		run(new Task() {
			@Override
			State execute() throws Exception {
				loadFromDisk(target, key);
				return current;
			}

			@Override
			void complete(State state) {
			}
		}, false);
	}

	private void loadFromDisk(Target target, Key key) throws Exception {
		SpotFractionPlan disk = SpotFractionStore.read(target.measures);
		List<Input> loaded = inputs(target, key.method, key.quantity, true);
		SpotFractionPlan plan = disk.copy();
		List<Row> rows = SpotFractionCalculator.calculate(loaded, key, plan);
		savedPlan = disk.copy();
		pendingSettings.clear();
		referenceDrafts.clear();
		pendingChartPick = null;
		formBaseline = plan.settingsFor(key);
		show(new State(target, key, plan, loaded, rows));
		status.setText(isUnsaved()
				? "Restored the file. The working calculation still differs from it until you save."
				: "Restored saved fraction settings and references.");
	}

	private List<Target> range(SpotMeasurementMethod selected) {
		int lo = ((Number) first.getValue()).intValue();
		int hi = ((Number) last.getValue()).intValue();
		if (parent == null) {
			if (testTarget != null && lo == 1 && hi == 1)
				return Collections.singletonList(target(0, selected));
			throw new IllegalArgumentException("Choose a valid inclusive experiment range");
		}
		if (lo < 1 || hi < lo || hi > parent.expListComboLazy.getItemCount())
			throw new IllegalArgumentException("Choose a valid inclusive experiment range");
		List<Target> targets = new ArrayList<>();
		for (int i = lo - 1; i <= hi - 1; i++)
			targets.add(target(i, selected));
		return targets;
	}

	private void batch() {
		try {
			if (spotOverride.isSelected())
				throw new IllegalArgumentException(
						"Turn off selected-spot settings before applying to an experiment range");
			Key key = selection();
			if (!resolveUnsaved("Apply settings to the experiment range", key, saveTarget(), true))
				return;
			Settings chosen = controls();
			List<Target> targets = range(key.method);
			run(new Task() {
				String summary;

				@Override
				State execute() {
					summary = executeBatch(targets, key, chosen, () -> cancelRequested);
					return null;
				}

				@Override
				void complete(State unused) {
					report.setText(summary);
					mainTabs.setSelectedIndex(2);
					status.setText(summary.startsWith("Stopped")
							? "Stopped after the current experiment; completed work is saved."
							: "Batch completed. See the batch report and saved review list.");
					reloadBatchSelection(targets, key);
				}
			}, true);
		} catch (RuntimeException ex) {
			error(ex);
		}
	}

	String executeBatch(List<Target> targets, Key key, Settings chosen, BooleanSupplier stopRequested) {
		StringBuilder text = new StringBuilder();
		int saved = 0;
		int pending = 0;
		boolean stopped = false;
		for (Target target : targets) {
			if (stopRequested.getAsBoolean()) {
				stopped = true;
				break;
			}
			try {
				SpotFractionPlan plan = SpotFractionStore.read(target.measures);
				plan.settings.put(key, chosen);
				SpotFractionCalculator.calculate(inputs(target, key.method, key.quantity, false), key, plan);
				SpotFractionStore.write(target.measures, plan);
				saved++;
				pending += plan.review.size();
				text.append(target.label).append(": saved; ").append(plan.review.size()).append(" pending items\n");
			} catch (Exception ex) {
				text.append(target.label).append(": FAILED - ").append(ex.getMessage()).append('\n');
			}
		}
		String prefix = stopped ? "Stopped after the current experiment; completed work is saved.\n"
				: "Batch completed.\n";
		return prefix + "Saved " + saved + "/" + targets.size() + " experiments; " + pending + " review items.\n" + text;
	}

	private void reloadBatchSelection(List<Target> targets, Key key) {
		try {
			Target selected = targetForSelection();
			for (Target target : targets)
				if (target.measures.equals(selected.measures)) {
					loadFromDisk(selected, key);
					return;
				}
		} catch (Exception ex) {
			error(ex);
		}
	}

	static final class SavedReview {
		final Target target;
		final Review review;

		SavedReview(Target target, Review review) {
			this.target = target;
			this.review = review;
		}
	}

	private void loadReviewList() {
		try {
			List<Target> targets = range((SpotMeasurementMethod) method.getSelectedItem());
			run(new Task() {
				List<SavedReview> collected;
				String failures;

				@Override
				State execute() {
					StringBuilder errors = new StringBuilder();
					collected = collectReviews(targets, errors);
					failures = errors.toString();
					return null;
				}

				@Override
				void complete(State unused) {
					savedReviews = collected;
					reviewModel.setRowCount(0);
					for (SavedReview item : collected)
						reviewModel.addRow(new Object[] { item.target.label, item.review.key.spot,
								label(item.review.key.method), item.review.key.quantity, item.review.reason });
					status.setText(collected.size()
							+ " saved review items. No images or primary series were loaded.");
					if (!failures.isEmpty()) {
						report.setText(failures);
						status.setText("Review list loaded with read errors. See the batch report.");
					}
					mainTabs.setSelectedIndex(2);
				}
			}, false);
		} catch (RuntimeException ex) {
			error(ex);
		}
	}

	List<SavedReview> collectReviews(List<Target> targets, StringBuilder failures) {
		List<SavedReview> collected = new ArrayList<>();
		Set<Path> seen = new LinkedHashSet<>();
		for (Target target : targets)
			for (Path directory : new Path[] { target.results, target.measures })
				if (seen.add(directory.toAbsolutePath().normalize())) {
					try {
						for (Review review : SpotFractionStore.read(directory).review)
							collected.add(new SavedReview(target, review));
					} catch (Exception ex) {
						failures.append(directory).append(": ").append(ex.getMessage()).append('\n');
					}
				}
		return collected;
	}

	private void openReview() {
		int index = reviews.getSelectedRow();
		if (index < 0)
			return;
		SavedReview selected = savedReviews.get(reviews.convertRowIndexToModel(index));
		Key previous = current != null ? current.selection : selection();
		if (!resolveUnsaved("Open a saved review item", previous, saveTarget(), false))
			return;
		if (parent != null)
			parent.expListComboLazy.setSelectedIndex(selected.target.index);
		updating = true;
		method.setSelectedItem(selected.review.key.method);
		if (((DefaultComboBoxModel<String>) quantity.getModel()).getIndexOf(selected.review.key.quantity) < 0)
			quantity.addItem(selected.review.key.quantity);
		quantity.setSelectedItem(selected.review.key.quantity);
		acceptedMethod = selected.review.key.method;
		acceptedQuantity = selected.review.key.quantity;
		acceptedExperimentIndex = parent == null ? selected.target.index : parent.expListComboLazy.getSelectedIndex();
		updating = false;
		reviewSpotToSelect = selected.review.key.spot;
		Key key = new Key(selected.review.key.method, selected.review.key.quantity, "");
		run(new Task() {
			@Override
			State execute() throws Exception {
				loadFromDisk(selected.target, key);
				return current;
			}

			@Override
			void complete(State state) {
				showStep(Step.REVIEW);
				mainTabs.setSelectedIndex(0);
			}
		}, false);
	}

	private Row selectedRow() {
		int index = spots.getSelectedRow();
		return current == null || index < 0 ? null : current.rows.get(spots.convertRowIndexToModel(index));
	}

	private Row rowByName(String name) {
		if (current == null || name == null)
			return null;
		for (Row row : current.rows)
			if (row.input.name.equals(name))
				return row;
		return null;
	}

	private void show(State state) {
		String keep = reviewSpotToSelect != null ? reviewSpotToSelect : referenceSpot;
		reviewSpotToSelect = null;
		updating = true;
		current = state;
		spotModel.setRowCount(0);
		issueModel.setRowCount(0);
		context.setText(state.target.label + " | " + label(state.selection.method) + " | " + state.selection.quantity
				+ " | " + state.target.measures);
		for (Row row : state.rows) {
			spotModel.addRow(new Object[] { row.input.name, row.input.cage,
					row.reference == null ? null : row.reference.value,
					row.reference == null ? "Unresolved" : row.reference.source, spotState(row), row.markedChanges });
			for (String issue : row.issues)
				issueModel.addRow(new Object[] { row.input.name, issue });
		}
		spotPicker.removeAllItems();
		for (Row row : state.rows)
			spotPicker.addItem(row.input.name);
		aggregateGroup.removeAllItems();
		for (Group group : state.groups)
			aggregateGroup.addItem(group);
		int modelRow = 0;
		if (keep != null)
			for (int i = 0; i < state.rows.size(); i++)
				if (state.rows.get(i).input.name.equals(keep)) {
					modelRow = i;
					break;
				}
		updating = false;
		updateAggregateChart();
		if (!state.rows.isEmpty()) {
			int viewRow = spots.convertRowIndexToView(modelRow);
			boolean same = spots.getSelectedRow() == viewRow;
			spots.setRowSelectionInterval(viewRow, viewRow);
			if (same)
				selectionChanged();
		} else {
			referenceSpot = null;
			individualChart.setChart(null);
			diagnosticsChart.setChart(null);
			referenceStatus.setText("No primary measurements for this selection. Collect them before calculating.");
			spotStatus.setText("No spot selected");
			refreshActionStates();
		}
		refreshInputStatus();
	}

	private void selectionChanged() {
		Row row = selectedRow();
		String name = row == null ? null : row.input.name;
		if (referenceSpot != null && !referenceSpot.equals(name))
			stashReference(referenceSpot);
		referenceSpot = name;
		retargetSettings(activeSettingsKey());
		loadReferenceFields(row);
		boolean was = updating;
		updating = true;
		spotPicker.setSelectedItem(name);
		updating = was;
		updateChart();
		describeSelection(row);
		refreshActionStates();
	}

	private void describeSelection(Row row) {
		if (row == null) {
			spotStatus.setText("No spot selected");
			referenceStatus.setText(" ");
			return;
		}
		String state = spotState(row);
		spotStatus.setText(row.input.name + " | cage " + row.input.cage + " | " + state
				+ (row.issues.isEmpty() ? "" : " | " + String.join("; ", row.issues)));
		referenceStatus.setText(describeReference(row));
		referenceStatus.setToolTipText(row.reference == null ? null : row.reference.source);
	}

	private static String spotState(Row row) {
		if (row.excluded)
			return "Excluded deposit";
		if (!row.issues.isEmpty())
			return "Needs review";
		return "Ready";
	}

	private static String describeReference(Row row) {
		Reference ref = row.reference;
		if (row.excluded)
			return "Excluded - failed deposit. It is omitted from aggregates and is not consumption.";
		if (ref == null)
			return "Unresolved - no initial reference yet.";
		String approval = ref.approved ? "approved" : "not approved";
		switch (ref.kind) {
		case INITIAL:
			return "Initial reference, " + approval + ". The first sample is the unconsumed deposit.";
		case DELAYED:
			return "Delayed reference at " + ref.timeSeconds + " s, " + approval
					+ ". Approving it does not recover consumption before that time.";
		case BORROWED:
			return "Borrowed reference, " + approval + ". Donors: " + ref.donors + ".";
		case PRECONSUMED:
			return "Pre-consumed estimate, " + approval + ". Donors: " + ref.donors + ".";
		case EXPLICIT:
			return "External reference, " + approval + ". Justification: " + ref.source;
		case OBSERVED_MAXIMUM:
			return "Maximum-observation estimate, " + approval + ". This is not the original unconsumed deposit.";
		case BASELINE_MEDIAN:
			return "Baseline-median estimate, " + approval + ". It uses the chosen period, not the first frame.";
		default:
			return "Failed deposit.";
		}
	}

	private void loadReferenceFields(Row row) {
		boolean was = updating;
		updating = true;
		Draft draft = row == null ? null : referenceDrafts.get(row.input.name);
		if (draft != null) {
			referenceValue.setText(draft.value);
			referenceTime.setText(draft.time);
			referenceSource.setText(draft.source);
		} else if (row == null || row.reference == null || row.reference.kind == ReferenceKind.FAILED_DEPOSIT) {
			referenceValue.setText("");
			referenceTime.setText(row == null || row.reference == null ? "0" : Double.toString(row.reference.timeSeconds));
			referenceSource.setText(row == null || row.reference == null ? "" : row.reference.source);
		} else {
			referenceValue.setText(Double.toString(row.reference.value));
			referenceTime.setText(Double.toString(row.reference.timeSeconds));
			referenceSource.setText(row.reference.source);
		}
		updating = was;
		if (draft != null)
			referenceStatus.setText(referenceStatus.getText()
					+ " Reference fields differ from the calculation until you approve them.");
	}

	private void stashReference(String name) {
		Row row = rowByName(name);
		Draft edited = new Draft(referenceValue.getText(), referenceTime.getText(), referenceSource.getText());
		if (row != null && edited.matches(row))
			referenceDrafts.remove(name);
		else
			referenceDrafts.put(name, edited);
	}

	private void updateChart() {
		Row row = selectedRow();
		if (row == null)
			return;
		individualChart.setChart(SpotFractionChart.create(row, (SpotFractionChart.View) view.getSelectedItem(),
				connectSimilar.isSelected()));
		diagnosticsChart.setChart(
				SpotFractionChart.create(row, (SpotFractionChart.View) diagnosticView.getSelectedItem(), false));
	}

	private void updateAggregateChart() {
		Group group = (Group) aggregateGroup.getSelectedItem();
		aggregateChart.setChart(group == null ? null
				: SpotFractionAggregateChart.create(group,
						(SpotFractionAggregation.Measure) aggregateMeasure.getSelectedItem()));
	}

	private Map<String, Input> inputMap() {
		Map<String, Input> map = new LinkedHashMap<>();
		for (Input input : current.inputs)
			map.put(input.name, input);
		return map;
	}

	private void approveReference() {
		Row row = selectedRow();
		if (row == null || current == null)
			return;
		try {
			double value = Double.parseDouble(referenceValue.getText().trim());
			double time = Double.parseDouble(referenceTime.getText().trim());
			ReferenceKind kind = row.reference != null && value == row.reference.value && time == row.reference.timeSeconds
					? row.reference.kind
					: ReferenceKind.EXPLICIT;
			if (kind == ReferenceKind.FAILED_DEPOSIT || kind == ReferenceKind.OBSERVED_MAXIMUM
					|| kind == ReferenceKind.BASELINE_MEDIAN)
				kind = ReferenceKind.EXPLICIT;
			if (kind == ReferenceKind.INITIAL
					&& (row.input.result.getQuantity(row.key.quantity).getRawValues()[0] != value
							|| row.input.result.getQuantity(row.key.quantity).getEffectiveFlags()[0] != 0))
				kind = ReferenceKind.EXPLICIT;
			List<String> donors = row.reference != null
					&& (kind == ReferenceKind.BORROWED || kind == ReferenceKind.PRECONSUMED) ? row.reference.donors
							: Collections.emptyList();
			current.plan.references.put(row.key, SpotFractionCalculator.reference(row.input, row.key.quantity, kind, value,
					time, referenceSource.getText().trim(), true, donors, inputMap()));
			referenceDrafts.remove(row.input.name);
			recalculateStaged("Reference validated in the working calculation. Save on Load/save to write it.");
		} catch (RuntimeException ex) {
			error(ex);
		}
	}

	private void borrow(boolean preconsumed) {
		Row row = selectedRow();
		if (row == null || current == null)
			return;
		List<String> candidates = new ArrayList<>();
		for (Row other : current.rows)
			if (!other.input.name.equals(row.input.name) && other.input.cage == row.input.cage && other.reference != null
					&& other.reference.kind == ReferenceKind.INITIAL && other.reference.approved && other.issues.isEmpty())
				candidates.add(other.input.name);
		if (candidates.isEmpty()) {
			error(new IllegalArgumentException("No approved initial-reference donors in this cage"));
			return;
		}
		List<String> descriptions = new ArrayList<>();
		for (String name : candidates)
			for (Row donor : current.rows)
				if (donor.input.name.equals(name))
					descriptions.add(name + " | reference " + donor.reference.value + " | " + donor.input.stimulus + " / "
							+ donor.input.concentration);
		JList<String> donorList = new JList<>(descriptions.toArray(new String[0]));
		for (int i = 0; i < candidates.size(); i++)
			for (Row donor : current.rows)
				if (donor.input.name.equals(candidates.get(i)) && Objects.equals(donor.input.stimulus, row.input.stimulus)
						&& Objects.equals(donor.input.concentration, row.input.concentration))
					donorList.addSelectionInterval(i, i);
		if (JOptionPane.showConfirmDialog(this, new JScrollPane(donorList), "Select comparable reference donors",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		List<String> chosen = new ArrayList<>();
		for (int index : donorList.getSelectedIndices())
			chosen.add(candidates.get(index));
		applyBorrow(row, chosen, preconsumed);
	}

	void applyBorrow(Row row, List<String> donors, boolean preconsumed) {
		try {
			current.plan.references.put(row.key,
					SpotFractionCalculator.borrow(row.input, row.key, donors, current.plan, inputMap(), preconsumed));
			referenceDrafts.remove(row.input.name);
			recalculateStaged("Borrowed reference validated in the working calculation. Donor names are kept. Save to write them.");
		} catch (RuntimeException ex) {
			error(ex);
		}
	}

	private void excludeDeposit() {
		Row row = selectedRow();
		if (row == null || current == null)
			return;
		current.plan.references.put(row.key, new Reference(ReferenceKind.FAILED_DEPOSIT, Double.NaN, 0,
				"User identified failed deposit", true, "", Collections.emptyList()));
		referenceDrafts.remove(row.input.name);
		recalculateStaged("Failed deposit excluded in the working calculation. Save to write that decision.");
	}

	private void removeOverride() {
		Row row = selectedRow();
		if (row == null || current == null)
			return;
		current.plan.settings.remove(row.key);
		pendingSettings.remove(row.key);
		updating = true;
		spotOverride.setSelected(false);
		updating = false;
		controlsKey = current.selection;
		readControls(current.plan.settingsFor(current.selection));
		recalculateStaged("Spot override removed from the working calculation. The spot uses the experiment settings.");
	}

	private void recalculateStaged(String message) {
		State staged = current;
		String name = selectedRow() == null ? "" : selectedRow().input.name;
		run(new Task() {
			@Override
			State execute() {
				SpotFractionPlan plan = staged.plan.copy();
				List<Row> rows = SpotFractionCalculator.calculate(staged.inputs, staged.selection, plan);
				return new State(staged.target, staged.selection, plan, staged.inputs, rows);
			}

			@Override
			void complete(State state) {
				reviewSpotToSelect = name;
				show(state);
				status.setText(message);
			}
		}, false);
	}

	boolean fillReferenceFromSample(Row row, int sample) {
		ChartPick pick = FractionSession.chartPick(row, sample);
		if (pick == null) {
			pendingChartPick = null;
			refreshActionStates();
			return false;
		}
		boolean was = updating;
		updating = true;
		referenceValue.setText(Double.toString(pick.raw));
		referenceTime.setText(Double.toString(pick.timeSeconds));
		referenceSource.setText(pick.source);
		updating = was;
		if (row.input != null)
			referenceDrafts.put(row.input.name, new Draft(referenceValue.getText(), referenceTime.getText(), pick.source));
		pendingChartPick = pick;
		status.setText("Reference fields hold the original measurement at sample " + sample
				+ ". Open Find hmax and approve it to use it. The plotted fraction was not copied.");
		refreshActionStates();
		return true;
	}

	private void openReferenceFromChart() {
		mainTabs.setSelectedIndex(0);
		showStep(Step.REFERENCE);
	}

	private void openIssue(Step destination) {
		int index = issues.getSelectedRow();
		if (index < 0 || current == null)
			return;
		String name = String.valueOf(issueModel.getValueAt(issues.convertRowIndexToModel(index), 0));
		String reason = String.valueOf(issueModel.getValueAt(issues.convertRowIndexToModel(index), 1));
		selectSpotByName(name);
		showStep(FractionSession.occlusionIssue(reason) && destination == Step.OCCLUSION ? Step.OCCLUSION
				: destination);
		mainTabs.setSelectedIndex(0);
	}

	private void selectSpotByName(String name) {
		if (current == null || name == null)
			return;
		for (int i = 0; i < current.rows.size(); i++)
			if (current.rows.get(i).input.name.equals(name)) {
				int viewRow = spots.convertRowIndexToView(i);
				if (spots.getSelectedRow() != viewRow)
					spots.setRowSelectionInterval(viewRow, viewRow);
				return;
			}
	}

	private void locateSpot() {
		Row row = selectedRow();
		if (row == null || parent == null || current == null)
			return;
		Spot spot = current.target.experiment.getSpots().findSpotByName(row.input.name);
		if (spot != null)
			parent.dlgSpots.onMeasureChartSpotClicked(spot);
		else
			status.setText("Open the experiment camera viewer to inspect this spot.");
	}

	private Key activeSettingsKey() {
		Key key = current != null ? current.selection : selection();
		Row row = selectedRow();
		if (spotOverride.isSelected() && row != null)
			return key.forSpot(row.input.name);
		return key.forSpot("");
	}

	private void retargetSettings(Key newKey) {
		if (newKey == null)
			return;
		if (controlsKey != null && !controlsKey.equals(newKey)) {
			capturePending(controlsKey);
			Settings next = pendingSettings.containsKey(newKey) ? pendingSettings.get(newKey)
					: current != null ? current.plan.settingsFor(newKey) : formBaseline;
			readControls(next);
		}
		controlsKey = newKey;
		refreshActionStates();
	}

	private void capturePending(Key key) {
		if (key == null || current == null)
			return;
		try {
			Settings edited = controls();
			if (!FractionSession.sameSettings(edited, current.plan.settingsFor(key)))
				pendingSettings.put(key, edited);
			else
				pendingSettings.remove(key);
		} catch (RuntimeException ex) {
			pendingSettings.put(key, formBaseline);
		}
	}

	private boolean isUnsaved() {
		try {
			if (current == null) {
				if (!FractionSession.sameSettings(controls(), formBaseline))
					return true;
			} else if (controlsKey != null && !FractionSession.sameSettings(controls(), current.plan.settingsFor(controlsKey)))
				return true;
		} catch (RuntimeException ex) {
			return true;
		}
		if (!pendingSettings.isEmpty() || !referenceDrafts.isEmpty())
			return true;
		Row row = selectedRow();
		if (row != null && !new Draft(referenceValue.getText(), referenceTime.getText(), referenceSource.getText()).matches(row))
			return true;
		if (current == null)
			return false;
		if (savedPlan == null)
			return true;
		return !FractionSession.sameSlice(current.plan, savedPlan, current.selection);
	}

	private boolean resolveUnsaved(String action, Key key, Target target, boolean reloadOnDiscard) {
		if (!isUnsaved())
			return true;
		UnsavedChoice choice = unsavedChoiceForTest;
		unsavedChoiceForTest = null;
		if (choice == null)
			choice = prompt(action);
		if (choice == UnsavedChoice.CANCEL)
			return false;
		if (choice == UnsavedChoice.DISCARD) {
			try {
				discardUnsaved(reloadOnDiscard);
				return true;
			} catch (Exception ex) {
				error(ex);
				return false;
			}
		}
		try {
			saveNow(key, target);
			return true;
		} catch (Exception ex) {
			error(ex);
			status.setText("Save failed; edits are still in this workspace. " + ex.getMessage());
			return false;
		}
	}

	private UnsavedChoice prompt(String action) {
		int choice = JOptionPane.showOptionDialog(this,
				action + " would discard unsaved fraction settings or reference decisions.\n"
						+ "Save writes SpotFractionSettings.xml for this experiment. Primary measurements are not modified.",
				"Unsaved fraction changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
				new Object[] { "Save", "Discard", "Cancel" }, "Save");
		if (choice == 0)
			return UnsavedChoice.SAVE;
		if (choice == 1)
			return UnsavedChoice.DISCARD;
		return UnsavedChoice.CANCEL;
	}

	private void discardUnsaved(boolean reloadSavedCalculation) throws Exception {
		pendingSettings.clear();
		referenceDrafts.clear();
		pendingChartPick = null;
		if (reloadSavedCalculation && savedPlan != null && current != null) {
			SpotFractionPlan file = SpotFractionStore.read(current.target.measures);
			SpotFractionPlan plan = file.copy();
			List<Row> rows = SpotFractionCalculator.calculate(current.inputs, current.selection, plan);
			savedPlan = file;
			show(new State(current.target, current.selection, plan, current.inputs, rows));
			readControls(current.plan.settingsFor(controlsKey == null ? current.selection : controlsKey));
			status.setText("Discarded unsaved changes and restored the last loaded calculation.");
			refreshActionStates();
			return;
		}
		current = null;
		savedPlan = null;
		clearRows();
		readControls(formBaseline);
		controlsKey = selection();
		status.setText("Discarded unsaved changes.");
		refreshActionStates();
		refreshInputStatus();
	}

	private void clearForNewSelection(boolean reloadQuantities) {
		current = null;
		savedPlan = null;
		pendingSettings.clear();
		referenceDrafts.clear();
		referenceSpot = null;
		pendingChartPick = null;
		clearRows();
		if (reloadQuantities)
			setQuantities();
		Settings loaded = Settings.DEFAULT;
		try {
			Target target = targetForSelection();
			loaded = SpotFractionStore.read(target.measures).settingsFor(selection());
		} catch (Exception ex) {
			status.setText("Saved fraction settings could not be shown: " + ex.getMessage());
		}
		formBaseline = loaded;
		readControls(loaded);
		controlsKey = selection();
		refreshInputStatus();
		refreshActionStates();
	}

	private void refreshInputStatus() {
		try {
			SpotMeasurementMethod selected = (SpotMeasurementMethod) method.getSelectedItem();
			Target target = targetForSelection();
			Path file = target.measures.resolve(selected.fileName());
			boolean available = java.nio.file.Files.exists(file);
			String binNote = selected.isKymographDerived()
					? "Vertical measurements use the selected kymograph bin."
					: "This method is read from the results directory and does not follow kymograph bin selection.";
			inputStatus.setText((available ? "Available" : "Missing") + " input | " + label(selected) + " | "
					+ quantity.getSelectedItem() + " | " + file + " | " + binNote
					+ " This workspace does not acquire images or rewrite primary files.");
			inputStatus.setToolTipText(file.toString());
		} catch (RuntimeException ex) {
			inputStatus.setText(
					"No experiment selected. This workspace reads existing primary files and does not acquire images or rewrite them.");
		}
	}

	private void refreshActionStates() {
		if (refreshing)
			return;
		refreshing = true;
		try {
			boolean experimentReady = parent != null && parent.expListComboLazy.getSelectedIndex() >= 0 || testTarget != null;
			boolean rowReady = selectedRow() != null;
			setAction(previewButton, !busy && experimentReady,
					"Apply the settings and reference decisions shown here to the working calculation. Does not write a file.",
					"Choose an experiment. This workspace does not acquire images.");
			setAction(saveButton, !busy && experimentReady && isUnsaved(),
					"Write SpotFractionSettings.xml for this experiment. Primary files and fraction-result CSVs are not written.",
					isUnsaved() ? "Choose an experiment before saving." : "There are no unsaved fraction settings or references.");
			setAction(restoreButton, !busy && experimentReady,
					"Load SpotFractionSettings.xml into the working calculation.",
					"Choose an experiment before restoring saved settings.");
			setAction(approveButton, !busy && rowReady,
					"Validate the reference fields into the working calculation. A delayed approval does not recover earlier consumption.",
					"Select a spot in a preview before approving a reference.");
			setAction(borrowButton, !busy && rowReady,
					"Use the median of approved initial references from the same cage. Donor names are stored. Failed deposits are not donors.",
					"Select a spot in a preview before borrowing a reference.");
			setAction(preconsumedButton, !busy && rowReady,
					"The first clear view is already partly consumed. Estimate hmax from comparable approved donors in the same cage, stimulus and concentration.",
					"Select a spot in a preview before marking it pre-consumed.");
			setAction(excludeButton, !busy && rowReady,
					"Mark a failed deposit. The spot is omitted from aggregates and is not converted into apparent consumption.",
					"Select a spot in a preview before excluding a failed deposit.");
			boolean override = rowReady && current != null && current.plan.settings.containsKey(selectedRow().key);
			setAction(removeOverrideButton, !busy && override,
					"Delete the selected spot's settings override. It then inherits the experiment settings.",
					"This spot has no settings override to remove.");
			setAction(locateButton, !busy && rowReady && parent != null,
					"Show this spot in the camera viewer when that viewer is open.",
					rowReady ? "Open the experiment camera viewer before locating a spot." : "Select a spot before locating it.");
			setAction(openReferenceButton, pendingChartPick != null,
					"Go to Find hmax. A chart click fills the reference from the original measurement, not from the plotted fraction.",
					"Click a valid positive observation on the individual chart first.");
			boolean issue = issues.getSelectedRow() >= 0;
			setAction(referenceIssueButton, !busy && issue, "Return to Find hmax for the selected issue.",
					"Select an issue first.");
			setAction(occlusionIssueButton, !busy && issue, "Return to Handle occlusions for the selected issue.",
					"Select an issue first.");
			setAction(batchButton, !busy && experimentReady,
					"Write the current experiment-level settings to each experiment in the range. References are not copied.",
					"Choose an experiment range before applying settings.");
			setAction(reviewListButton, !busy && experimentReady,
					"Read pending items from saved calculation files in the range. Images and primary series stay unloaded.",
					"Choose an experiment range before reading saved review items.");
			setAction(openReviewButton, !busy && reviews.getSelectedRow() >= 0,
					"Load the experiment, method, quantity and spot named by this saved item.",
					"Select a saved review item first.");
			backButton.setVisible(step != Step.OVERVIEW);
			if (!busy) {
				syncTolerance();
				syncBaselineControls();
			}
			if (!isUnsaved()) {
				unsaved.setText("No unsaved changes");
				unsaved.setToolTipText("Working settings and references match the last file loaded or saved for this selection.");
			} else if (current != null && controlsDiffer()) {
				unsaved.setText("Unsaved changes - preview to update results");
				unsaved.setToolTipText(
						"The controls differ from the displayed calculation. Preview applies them in memory. Save writes the file.");
			} else {
				unsaved.setText("Unsaved changes");
				unsaved.setToolTipText(
						"Reference decisions or settings are in memory only. Save writes SpotFractionSettings.xml.");
			}
		} finally {
			refreshing = false;
		}
	}

	private boolean controlsDiffer() {
		try {
			return controlsKey != null && current != null
					&& !FractionSession.sameSettings(controls(), current.plan.settingsFor(controlsKey));
		} catch (RuntimeException ex) {
			return true;
		}
	}

	private static void setAction(JButton button, boolean enabled, String enabledTip, String disabledTip) {
		button.setEnabled(enabled);
		button.setToolTipText(enabled ? enabledTip : disabledTip);
	}

	private abstract class Task {
		abstract State execute() throws Exception;

		abstract void complete(State state);

	}

	private void run(Task task, boolean stoppable) {
		if (busy)
			return;
		busy = true;
		cancelRequested = false;
		stop.setEnabled(stoppable);
		Map<JComponent, Boolean> enabled = new IdentityHashMap<>();
		for (JComponent component : editors()) {
			enabled.put(component, component.isEnabled());
			component.setEnabled(false);
		}
		boolean comboEnabled = parent != null && parent.expListComboLazy.isEnabled();
		if (parent != null)
			parent.expListComboLazy.setEnabled(false);
		refreshActionStates();
		status.setText("Calculating from saved measurements...");
		Runnable finish = () -> {
			busy = false;
			if (parent != null)
				parent.expListComboLazy.setEnabled(comboEnabled);
			stop.setEnabled(false);
			cancelRequested = false;
			enabled.forEach(JComponent::setEnabled);
			refreshActionStates();
		};
		if (synchronousForTest) {
			try {
				task.complete(task.execute());
			} catch (Exception ex) {
				error(ex);
			} finally {
				finish.run();
			}
			return;
		}
		new SwingWorker<State, Void>() {
			@Override
			protected State doInBackground() throws Exception {
				return task.execute();
			}

			@Override
			protected void done() {
				try {
					task.complete(get());
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					error(ex);
				} catch (ExecutionException ex) {
					error(ex.getCause() == null ? ex : ex.getCause());
				} finally {
					finish.run();
				}
			}
		}.execute();
	}

	private List<JComponent> editors() {
		return Arrays.asList(method, quantity, spotPicker, similar, changing, tail, toleranceMode, illumination,
				referenceEstimation, tolerance, maximumGap, median, baselineStart, baselineEnd, first, last, spotOverride,
				referenceValue, referenceTime, referenceSource, spots, reviews, issues, aggregateGroup, aggregateMeasure,
				showAdvancedReference, showAdvancedOcclusion);
	}

	private void clearRows() {
		boolean was = updating;
		updating = true;
		spotModel.setRowCount(0);
		issueModel.setRowCount(0);
		spotPicker.removeAllItems();
		aggregateGroup.removeAllItems();
		updating = was;
		individualChart.setChart(null);
		aggregateChart.setChart(null);
		diagnosticsChart.setChart(null);
		referenceStatus.setText(" ");
		spotStatus.setText("No spot selected");
		context.setText("No calculation is in memory for this selection.");
	}

	private void error(Throwable error) {
		String message = error.getMessage() == null ? error.toString() : error.getMessage();
		status.setText("Cannot update fractions: " + message);
		report.append(error.toString() + "\n");
	}

	private JButton button(String label, Runnable action) {
		JButton created = new JButton(label);
		created.addActionListener(e -> action.run());
		actionButtons.add(created);
		return created;
	}

	private static JPanel row(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component component : components)
			panel.add(component);
		return panel;
	}

	private static JPanel column(Component... components) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component component : components)
			panel.add(component);
		return panel;
	}

	private static JPanel section(String title, Component... components) {
		JPanel panel = column(components);
		panel.setBorder(BorderFactory.createTitledBorder(title));
		return panel;
	}

	private static JScrollPane scroll(Component component) {
		JScrollPane pane = new JScrollPane(component);
		pane.setBorder(null);
		pane.getVerticalScrollBar().setUnitIncrement(16);
		return pane;
	}

	private static JTextArea note(int rows) {
		JTextArea area = note("");
		area.setRows(rows);
		return area;
	}

	private static JTextArea note(String text) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setColumns(56);
		area.setRows(Math.min(5, Math.max(2, text.isEmpty() ? 2 : (text.length() / 110) + 1)));
		area.setBorder(null);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		return area;
	}

	private static DefaultTableModel tableModel(String... names) {
		return new DefaultTableModel(names, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	private static DefaultListCellRenderer renderer() {
		return new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
					boolean focus) {
				return super.getListCellRendererComponent(list, label(value), index, selected, focus);
			}
		};
	}

	static String label(Object value) {
		if (value == SpotMeasurementMethod.FILTER)
			return "Image filters";
		if (value == SpotMeasurementMethod.COLOR)
			return "Color distance";
		if (value == SpotMeasurementMethod.VERTICAL_LINE)
			return "Vertical line";
		if (value == SpotMeasurementMethod.X_LINES)
			return "X lines";
		if (value == SpotMeasurementMethod.RIM_LINES)
			return "Rim lines";
		if (value == GapPolicy.NONE || value == TailPolicy.KEEP_MISSING)
			return "Keep missing (NaN)";
		if (value == GapPolicy.HOLD_PREVIOUS || value == TailPolicy.HOLD_PREVIOUS)
			return "Hold previous";
		if (value == GapPolicy.LINEAR)
			return "Linear interpolation";
		if (value == TailPolicy.REVIEW)
			return "Request review";
		if (value == Tolerance.PERCENT_REFERENCE)
			return "% of reference";
		if (value == Tolerance.ABSOLUTE)
			return "Measurement units";
		if (value == Tolerance.ROBUST_NOISE)
			return "Estimated noise (3 sigma)";
		if (value == Illumination.NONE)
			return "Off";
		if (value == Illumination.CAGE)
			return "Per cage";
		if (value == Illumination.EXPERIMENT)
			return "Whole experiment";
		if (value == ReferenceKind.INITIAL)
			return "Initial";
		if (value == ReferenceKind.DELAYED)
			return "Delayed";
		if (value == ReferenceKind.EXPLICIT)
			return "External";
		if (value == ReferenceKind.BORROWED)
			return "Borrowed";
		if (value == ReferenceKind.PRECONSUMED)
			return "Pre-consumed";
		if (value == ReferenceKind.FAILED_DEPOSIT)
			return "Excluded";
		if (value == ReferenceKind.OBSERVED_MAXIMUM)
			return "Maximum observation";
		if (value == ReferenceKind.BASELINE_MEDIAN)
			return "Baseline median";
		if ("color_integral".equals(value))
			return "Color integral (vertical line)";
		if (value instanceof ReferenceEstimation || value instanceof Group)
			return String.valueOf(value);
		if (value == null)
			return "";
		return String.valueOf(value).toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private static final class Draft {
		final String value;
		final String time;
		final String source;

		Draft(String value, String time, String source) {
			this.value = value == null ? "" : value;
			this.time = time == null ? "" : time;
			this.source = source == null ? "" : source;
		}

		boolean matches(Row row) {
			Reference ref = row.reference;
			if (ref == null || ref.kind == ReferenceKind.FAILED_DEPOSIT)
				return value.trim().isEmpty() && (time.trim().isEmpty() || "0".equals(time.trim()) || "NaN".equals(time.trim()))
						&& (ref == null ? source.trim().isEmpty() : source.equals(ref.source));
			return value.trim().equals(Double.toString(ref.value)) && time.trim().equals(Double.toString(ref.timeSeconds))
					&& source.equals(ref.source);
		}
	}
}
