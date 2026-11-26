package plugins.fmp.multiSPOTS.dlg.fractions;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.entity.XYItemEntity;

import icy.gui.component.PopupPanel;
import icy.gui.frame.IcyFrame;
import plugins.fmp.multiSPOTS.MultiSPOTS;
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
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceKind;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.ReferenceEstimation;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Review;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Settings;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.Tolerance;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionStore;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

/**
 * Common second stage: preview, calculate, review and save without running
 * image detection.
 */
public final class _DlgCalculateFractions_ extends JPanel {
	private static final long serialVersionUID = 1L;
	private MultiSPOTS parent = null;
	final JComboBox<SpotMeasurementMethod> method = new JComboBox<>(SpotMeasurementMethod.values());
	final JComboBox<String> quantity = new JComboBox<>();
	final JComboBox<GapPolicy> similar = new JComboBox<>(GapPolicy.values()),
			changing = new JComboBox<>(GapPolicy.values());
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
	final JSpinner first = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
	final JSpinner last = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
	final JComboBox<SpotFractionChart.View> view = new JComboBox<>(SpotFractionChart.fractionViews());
	final JComboBox<SpotFractionChart.View> diagnosticView = new JComboBox<>(SpotFractionChart.diagnosticViews());
	final JComboBox<Group> aggregateGroup = new JComboBox<>();
	final JComboBox<SpotFractionAggregation.Measure> aggregateMeasure = new JComboBox<>(
			SpotFractionAggregation.Measure.values());
	final JCheckBox connectSimilar = new JCheckBox("Connect similar endpoints visually", true);
	private final JLabel status = new JLabel("Load saved measurements to calculate spot fractions."),
			referenceStatus = new JLabel(" ");
	private final JLabel context = new JLabel("Experiment: none loaded in this workspace");
	private final JTextArea report = new JTextArea(4, 60);
	private final DefaultTableModel spotModel = tableModel("Spot", "Cage", "Reference", "Reference source",
			"State", "Marked gaps");
	private final DefaultTableModel reviewModel = tableModel("Experiment", "Spot", "Method", "Quantity", "Reason");
	final JTable spots = new JTable(spotModel), reviews = new JTable(reviewModel);
	private final ChartPanel chart = new ChartPanel(null);
	private final ChartPanel aggregateChart = new ChartPanel(null), diagnosticsChart = new ChartPanel(null);
	private final JTextField referenceValue = new JTextField(8), referenceTime = new JTextField(8),
			referenceSource = new JTextField(28);
	private final List<JButton> actionButtons = new ArrayList<>();
	private State current;
	private List<SavedReview> savedReviews = new ArrayList<>();
	private boolean busy, updating;
	private volatile boolean cancelRequested;
	private final JButton stop = new JButton("Stop after current experiment");
	private String reviewSpotToSelect;
	private IcyFrame workspace;

	public _DlgCalculateFractions_() {
		super(new BorderLayout(4, 4));
		method.setRenderer(renderer());
		quantity.setRenderer(renderer());
		similar.setRenderer(renderer());
		changing.setRenderer(renderer());
		tail.setRenderer(renderer());
		toleranceMode.setRenderer(renderer());
		illumination.setRenderer(renderer());
		view.setRenderer(renderer());
		diagnosticView.setRenderer(renderer());
		aggregateMeasure.setRenderer(renderer());
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.add(row(new JLabel("Measurements"), method, new JLabel("Quantity"), quantity));
		controls.add(context);
		controls.add(row(new JLabel("Estimate reference"), referenceEstimation));
		controls.add(row(new JLabel("Baseline period (minutes)"), baselineStart, new JLabel("to"), baselineEnd));
		referenceEstimation.setToolTipText("Reference estimation uses valid raw observations only, before gap filling or smoothing. Explicit and borrowed references remain in effect.");
		baselineStart.setToolTipText("Choose a verified period with no feeding. At least three unflagged observations and a positive median are required per spot.");
		baselineEnd.setToolTipText(baselineStart.getToolTipText());
		referenceEstimation.addActionListener(e -> syncBaselineControls());
		syncBaselineControls();
		controls.add(row(new JLabel("Similar endpoints"), similar, new JLabel("Changed endpoints"), changing));
		controls.add(row(new JLabel("Tolerance"), toleranceMode, tolerance, new JLabel("Maximum gap (minutes)"),
				maximumGap));
		controls.add(row(new JLabel("Final gap"), tail, new JLabel("Median radius (samples; 0 = off)"), median));
		controls.add(row(new JLabel("Common multiplicative drift"), illumination, spotOverride));
		illumination.setToolTipText(
				"Optional estimate: at most two changing spots per cage; requires a strict majority of valid aligned spots (at least five per cage). Inspect factors and supporting counts.");
		toleranceMode.setToolTipText(
				"Percentage is relative to the fixed initial reference. Robust noise uses three sigma from MAD of adjacent valid differences (at least five pairs).");
		controls.add(row(button("Load saved", () -> currentAction(false, false)),
				button("Preview current", () -> currentAction(true, false)),
				button("Calculate & save current", () -> currentAction(true, true))));
		controls.add(row(new JLabel("Experiment range (1-based)"), first, new JLabel("to"), last,
				button("Apply & save range", this::batch), button("Saved review list", this::loadReviewList)));
		stop.setEnabled(false);
		stop.addActionListener(e -> {
			cancelRequested = true;
			stop.setEnabled(false);
		});
		controls.add(row(stop));
		controls.add(status);
		add(controls, BorderLayout.NORTH);
		JTabbedPane tabs = new JTabbedPane();
		JPanel calculation = new JPanel(new BorderLayout(3, 3));
		spots.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		spots.setAutoCreateRowSorter(true);
		JScrollPane spotScroll = new JScrollPane(spots);
		spotScroll.setPreferredSize(new Dimension(800, 125));
		JPanel plot = new JPanel(new BorderLayout());
		plot.add(row(new JLabel("Display"), view, connectSimilar, button("Locate spot", this::locateSpot)),
				BorderLayout.NORTH);
		chart.setPreferredSize(new Dimension(800, 350));
		plot.add(chart, BorderLayout.CENTER);
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, spotScroll, plot);
		split.setResizeWeight(.25);
		calculation.add(split, BorderLayout.CENTER);
		JPanel reference = new JPanel();
		reference.setLayout(new BoxLayout(reference, BoxLayout.Y_AXIS));
		reference.add(referenceStatus);
		reference.add(row(new JLabel("Reference value"), referenceValue, new JLabel("Time (seconds)"), referenceTime,
				new JLabel("Source / justification"), referenceSource));
		reference.add(row(button("Approve reference", this::approveReference),
				button("Borrow initial reference", () -> borrow(false)),
				button("Mark pre-consumed", () -> borrow(true)), button("Exclude failed deposit", this::excludeDeposit),
				button("Remove spot override", this::removeOverride)));
		reference.add(new JLabel(
				"Reference decisions are staged until Calculate & save current. Borrowed references retain donor names."));
		calculation.add(reference, BorderLayout.SOUTH);
		tabs.addTab("Calculation and reference review", calculation);
		JPanel aggregates = new JPanel(new BorderLayout(3, 3));
		aggregates.add(row(new JLabel("Group"), aggregateGroup, new JLabel("Fraction aggregate"), aggregateMeasure),
				BorderLayout.NORTH);
		aggregates.add(aggregateChart, BorderLayout.CENTER);
		aggregates.add(new JLabel(
				"Fixed membership by cage, stimulus and concentration. Totals require every non-excluded spot; coverage is shown below."),
				BorderLayout.SOUTH);
		tabs.addTab("Aggregated fractions", aggregates);
		JPanel diagnostics = new JPanel(new BorderLayout(3, 3));
		diagnostics.add(row(new JLabel("Selected spot: correction diagnostic"), diagnosticView), BorderLayout.NORTH);
		diagnostics.add(diagnosticsChart, BorderLayout.CENTER);
		tabs.addTab("Correction diagnostics", diagnostics);
		JPanel pending = new JPanel(new BorderLayout());
		pending.add(new JScrollPane(reviews), BorderLayout.CENTER);
		pending.add(row(button("Open selected review item", this::openReview)), BorderLayout.SOUTH);
		tabs.addTab("Saved review list", pending);
		report.setEditable(false);
		report.setLineWrap(true);
		report.setWrapStyleWord(true);
		tabs.addTab("Batch report", new JScrollPane(report));
		add(tabs, BorderLayout.CENTER);
		method.addActionListener(e -> {
			if (!updating) {
				current = null;
				spotOverride.setSelected(false);
				setQuantities();
				clearRows();
			}
		});
		quantity.addActionListener(e -> {
			if (!updating) {
				current = null;
				clearRows();
			}
		});
		toleranceMode.addActionListener(
				e -> tolerance.setEnabled(toleranceMode.getSelectedItem() != Tolerance.ROBUST_NOISE));
		spots.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting() && !updating)
				selectionChanged();
		});
		spotOverride.addActionListener(e -> {
			Row selected = selectedRow();
			if (current != null)
				readControls(current.plan.settingsFor(
						spotOverride.isSelected() && selected != null ? selected.key : current.selection.forSpot("")));
		});
		view.addActionListener(e -> updateChart());
		connectSimilar.addActionListener(e -> updateChart());
		diagnosticView.addActionListener(e -> updateChart());
		aggregateGroup.addActionListener(e -> {
			if (!updating)
				updateAggregateChart();
		});
		aggregateMeasure.addActionListener(e -> updateAggregateChart());
		chart.addChartMouseListener(new ChartMouseListener() {
			@Override
			public void chartMouseMoved(ChartMouseEvent event) {
			}

			@Override
			public void chartMouseClicked(ChartMouseEvent event) {
				Row row = selectedRow();
				if (row == null || busy || !(event.getEntity() instanceof XYItemEntity))
					return;
				int sample = ((XYItemEntity) event.getEntity()).getItem();
				double raw = row.input.result.getQuantity(row.key.quantity).getRawValues()[sample];
				if (row.input.result.getQuantity(row.key.quantity).getEffectiveFlags()[sample] != 0
						|| !Double.isFinite(raw) || raw <= 0) {
					status.setText(
							"Choose a valid positive observation for a reference; occluded values cannot establish it.");
					return;
				}
				referenceValue.setText(Double.toString(raw));
				referenceTime.setText(Double.toString(row.input.result.getTimesSeconds()[sample]));
				referenceSource.setText("User-selected valid observation at sample " + sample);
				status.setText("Reference fields filled from the selected observation; approve and save to use it.");
			}
		});
		view.setSelectedItem(SpotFractionChart.View.FRACTION_CONSUMED);
		readControls(Settings.DEFAULT);
		setQuantities();
	}

	public void init(JPanel mainPanel, String title, MultiSPOTS parent0) {
		this.parent = parent0;
		PopupPanel popup = new PopupPanel(title);
		popup.getMainPanel().setLayout(new BorderLayout());
		JButton open = new JButton("Open calculation workspace");
		open.addActionListener(event -> {
			if (workspace == null || !workspace.isVisible()) {
				workspace = new IcyFrame("Calculate spot fractions", true, true, true, true);
				workspace.setLayout(new BorderLayout());
				workspace.add(this, BorderLayout.CENTER);
				workspace.setSize(1100, 850);
				workspace.addToDesktopPane();
				workspace.center();
			}
			workspace.setVisible(true);
			workspace.requestFocus();
		});
		popup.getMainPanel().add(row(new JLabel("Saved measurements → references → spot fractions"), open),
				BorderLayout.CENTER);
		popup.collapse();
		mainPanel.add(popup);
		parent.expListComboLazy.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED && !busy) {
				current = null;
				spotOverride.setSelected(false);
				clearRows();
				setQuantities();
				int index = Math.max(1, parent.expListComboLazy.getSelectedIndex() + 1);
				first.setValue(index);
				last.setValue(index);
				status.setText("Load saved or preview this experiment; other experiments keep their saved settings.");
			}
		});
		setQuantities();
	}

	private void setQuantities() {
		updating = true;
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
		updating = false;
	}

	Settings controls() {
		try {
			baselineStart.commitEdit();
			baselineEnd.commitEdit();
		} catch (java.text.ParseException ex) {
			throw new IllegalArgumentException("Enter a valid baseline period", ex);
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
		boolean enabled = referenceEstimation.getSelectedItem() == ReferenceEstimation.BASELINE_MEDIAN;
		baselineStart.setEnabled(enabled);
		baselineEnd.setEnabled(enabled);
	}

	private void readControls(Settings settings) {
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
	}

	private Key selection() {
		return new Key((SpotMeasurementMethod) method.getSelectedItem(), (String) quantity.getSelectedItem(), "");
	}

	private static final class Target {
		final int index;
		final String label;
		final Path results, measures;
		final Experiment experiment;

		Target(int index, String label, Path results, Path measures, Experiment experiment) {
			this.index = index;
			this.label = label;
			this.results = results;
			this.measures = measures;
			this.experiment = experiment;
		}
	}

	private Target target(int index, SpotMeasurementMethod method) {
		if (parent == null || index < 0 || index >= parent.expListComboLazy.getItemCount())
			throw new IllegalArgumentException("Choose an experiment");
		Experiment exp = parent.expListComboLazy.getItemAtNoLoad(index);
		if (exp == null || exp.getResultsDirectory() == null)
			throw new IllegalArgumentException("Experiment has no results directory");
		Path results = Paths.get(exp.getResultsDirectory());
		Path measurements = results;
		if (method != SpotMeasurementMethod.FILTER && method != SpotMeasurementMethod.COLOR) {
			String bin = parent.expListComboLazy.expListBinSubDirectory;
			if (bin != null && !bin.isEmpty())
				measurements = results.resolve(bin);
			else if (exp instanceof LazyExperiment)
				measurements = Paths.get(((LazyExperiment) exp).getMetadata().getBinDirectory());
			else if (exp.getKymosBinFullDirectory() != null)
				measurements = Paths.get(exp.getKymosBinFullDirectory());
		}
		return new Target(index, (index + 1) + ": " + results, results, measurements, exp);
	}

	private static final class State {
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

	private static List<Input> inputs(Target target, SpotMeasurementMethod method, String quantity, boolean current)
			throws Exception {
		if (current && target.experiment.getSpots().getSpotList().stream()
				.anyMatch(s -> s.getMethodMeasurements().get(method) != null
						&& s.getMethodMeasurements().get(method).getQuantities().containsKey(quantity)))
			return SpotFractionData.fromSpots(target.experiment.getSpots().getSpotList(), method);
		return SpotFractionData.read(target.results, target.measures, method, quantity);
	}

	private void currentAction(boolean applyControls, boolean save) {
		try {
			Key key = selection();
			Target target = target(parent == null ? -1 : parent.expListComboLazy.getSelectedIndex(), key.method);
			Settings chosen = controls();
			Row selected = selectedRow();
			if (applyControls && spotOverride.isSelected() && selected == null)
				throw new IllegalArgumentException("Select a spot for its override");
			Key settingKey = spotOverride.isSelected() && selected != null ? selected.key : key;
			if (selected != null)
				reviewSpotToSelect = selected.input.name;
			SpotFractionPlan staged = current != null && current.target.measures.equals(target.measures)
					&& current.selection.equals(key) ? current.plan.copy() : null;
			run(new Task() {
				@Override
				State execute() throws Exception {
					SpotFractionPlan plan = applyControls && staged != null ? staged
							: SpotFractionStore.read(target.measures);
					if (applyControls)
						plan.settings.put(settingKey, chosen);
					List<Input> inputs = inputs(target, key.method, key.quantity, true);
					List<Row> rows = SpotFractionCalculator.calculate(inputs, key, plan);
					if (save)
						SpotFractionStore.write(target.measures,
								mergeSelection(SpotFractionStore.read(target.measures), plan, key));
					return new State(target, key, plan, inputs, rows);
				}

				@Override
				void complete(State state) {
					show(state);
					status.setText(save
							? "Saved calculation choices, references and " + state.plan.review.size() + " review items."
							: "Preview only — choices and reference decisions are not saved.");
				}
			});
		} catch (Exception e) {
			error(e);
		}
	}

	static SpotFractionPlan mergeSelection(SpotFractionPlan saved, SpotFractionPlan preview, Key key) {
		saved.settings.keySet().removeIf(k -> k.method == key.method && k.quantity.equals(key.quantity));
		saved.references.keySet().removeIf(k -> k.method == key.method && k.quantity.equals(key.quantity));
		saved.review.removeIf(r -> r.key.method == key.method && r.key.quantity.equals(key.quantity));
		preview.settings.forEach((k, v) -> {
			if (k.method == key.method && k.quantity.equals(key.quantity))
				saved.settings.put(k, v);
		});
		preview.references.forEach((k, v) -> {
			if (k.method == key.method && k.quantity.equals(key.quantity))
				saved.references.put(k, v);
		});
		for (Review r : preview.review)
			if (r.key.method == key.method && r.key.quantity.equals(key.quantity))
				saved.review.add(r);
		return saved;
	}

	private List<Target> range(SpotMeasurementMethod method) {
		int lo = ((Number) first.getValue()).intValue() - 1, hi = ((Number) last.getValue()).intValue() - 1;
		if (parent == null || lo < 0 || hi < lo || hi >= parent.expListComboLazy.getItemCount())
			throw new IllegalArgumentException("Choose a valid inclusive experiment range");
		List<Target> targets = new ArrayList<>();
		for (int i = lo; i <= hi; i++)
			targets.add(target(i, method));
		return targets;
	}

	private void batch() {
		try {
			Key key = selection();
			Settings chosen = controls();
			List<Target> targets = range(key.method);
			if (spotOverride.isSelected())
				throw new IllegalArgumentException(
						"Turn off selected-spot settings before applying to an experiment range");
			run(new Task() {
				String summary;

				@Override
				State execute() {
					StringBuilder text = new StringBuilder();
					int saved = 0, pending = 0;
					for (Target target : targets) {
						if (cancelRequested)
							break;
						progress("Calculating " + target.label);
						try {
							SpotFractionPlan plan = SpotFractionStore.read(target.measures);
							plan.settings.put(key, chosen);
							SpotFractionCalculator.calculate(inputs(target, key.method, key.quantity, false), key,
									plan);
							SpotFractionStore.write(target.measures, plan);
							saved++;
							pending += plan.review.size();
							text.append(target.label).append(": saved; ").append(plan.review.size())
									.append(" pending items\n");
						} catch (Exception e) {
							text.append(target.label).append(": FAILED — ").append(e.getMessage()).append('\n');
						}
					}
					summary = "Saved " + saved + "/" + targets.size() + " experiments; " + pending + " review items.\n"
							+ text;
					return null;
				}

				@Override
				void complete(State unused) {
					report.setText(summary);
					status.setText(cancelRequested
							? "Stopped after the current experiment; completed work is saved. See Batch report."
							: "Batch completed. See Batch report and Saved review list.");
					current = null;
					clearRows();
				}
			});
		} catch (Exception e) {
			error(e);
		}
	}

	private static final class SavedReview {
		final Target target;
		final Review review;

		SavedReview(Target target, Review review) {
			this.target = target;
			this.review = review;
		}
	}

	private void loadReviewList() {
		try {
			List<Target> targets = range(SpotMeasurementMethod.RIM_LINES);
			List<SavedReview> collected = new ArrayList<>();
			StringBuilder failures = new StringBuilder();
			run(new Task() {
				@Override
				State execute() {
					Set<Path> seen = new HashSet<>();
					for (Target target : targets)
						for (Path directory : new Path[] { target.results, target.measures })
							if (seen.add(directory.toAbsolutePath().normalize())) {
								try {
									for (Review review : SpotFractionStore.read(directory).review)
										collected.add(new SavedReview(target, review));
								} catch (Exception e) {
									failures.append(directory).append(": ").append(e.getMessage()).append('\n');
								}
							}
					return null;
				}

				@Override
				void complete(State unused) {
					savedReviews = collected;
					reviewModel.setRowCount(0);
					for (SavedReview item : collected)
						reviewModel.addRow(new Object[] { item.target.label, item.review.key.spot,
								label(item.review.key.method), item.review.key.quantity, item.review.reason });
					status.setText(collected.size() + " saved review items; no images or primary series were loaded.");
					if (failures.length() > 0) {
						report.setText(failures.toString());
						status.setText("Review list loaded with read errors; see Batch report.");
					}
				}
			});
		} catch (Exception e) {
			error(e);
		}
	}

	private void openReview() {
		int index = reviews.getSelectedRow();
		if (index < 0)
			return;
		SavedReview selected = savedReviews.get(reviews.convertRowIndexToModel(index));
		parent.expListComboLazy.setSelectedIndex(selected.target.index);
		method.setSelectedItem(selected.review.key.method);
		if (((DefaultComboBoxModel<String>) quantity.getModel()).getIndexOf(selected.review.key.quantity) < 0)
			quantity.addItem(selected.review.key.quantity);
		quantity.setSelectedItem(selected.review.key.quantity);
		currentAction(false, false);
		reviewSpotToSelect = selected.review.key.spot;
	}

	private Row selectedRow() {
		int index = spots.getSelectedRow();
		return current == null || index < 0 ? null : current.rows.get(spots.convertRowIndexToModel(index));
	}

	private void show(State state) {
		updating = true;
		current = state;
		spotModel.setRowCount(0);
		context.setText(
				state.target.label + " | " + label(state.selection.method) + " | " + label(state.selection.quantity));
		context.setToolTipText(state.target.measures.toString());
		for (Row row : state.rows)
			spotModel.addRow(
					new Object[] { row.input.name, row.input.cage, row.reference == null ? null : row.reference.value,
							row.reference == null ? "Unresolved" : row.reference.source,
							row.excluded ? "Excluded deposit"
									: row.issues.isEmpty() ? "Ready" : String.join("; ", row.issues),
							row.markedChanges });
		readControls(state.plan.settingsFor(state.selection));
		aggregateGroup.removeAllItems();
		for (Group group : state.groups)
			aggregateGroup.addItem(group);
		updating = false;
		updateAggregateChart();
		if (!state.rows.isEmpty()) {
			int row = 0;
			if (reviewSpotToSelect != null)
				for (int i = 0; i < state.rows.size(); i++)
					if (state.rows.get(i).input.name.equals(reviewSpotToSelect)) {
						row = spots.convertRowIndexToView(i);
						break;
					}
			reviewSpotToSelect = null;
			spots.setRowSelectionInterval(row, row);
		} else {
			chart.setChart(null);
			referenceStatus.setText("No primary measurements for this selection; collect them first.");
		}
		selectionChanged();
	}

	private void selectionChanged() {
		Row row = selectedRow();
		if (row == null)
			return;
		if (spotOverride.isSelected())
			readControls(current.plan.settingsFor(row.key));
		Reference ref = row.reference;
		referenceValue.setText(ref == null ? "" : Double.toString(ref.value));
		referenceTime.setText(ref == null ? "0" : Double.toString(ref.timeSeconds));
		referenceSource.setText(ref == null ? "" : ref.source);
		referenceStatus.setText(ref == null ? "Initial reference unresolved"
				: "Reference: " + label(ref.kind) + "; "
						+ (row.issues.isEmpty() ? "approved" : "review: " + String.join("; ", row.issues)));
		referenceStatus.setToolTipText(ref == null ? null : ref.source);
		updateChart();
	}

	private void updateChart() {
		Row row = selectedRow();
		if (row != null) {
			chart.setChart(SpotFractionChart.create(row, (SpotFractionChart.View) view.getSelectedItem(),
					connectSimilar.isSelected()));
			diagnosticsChart.setChart(
					SpotFractionChart.create(row, (SpotFractionChart.View) diagnosticView.getSelectedItem(), false));
		}
	}

	private void updateAggregateChart() {
		Group group = (Group) aggregateGroup.getSelectedItem();
		aggregateChart.setChart(group == null ? null
				: SpotFractionAggregateChart.create(group,
						(SpotFractionAggregation.Measure) aggregateMeasure.getSelectedItem()));
	}

	private Map<String, Input> inputMap() {
		Map<String, Input> map = new LinkedHashMap<>();
		for (Input in : current.inputs)
			map.put(in.name, in);
		return map;
	}

	private void approveReference() {
		Row row = selectedRow();
		if (row == null)
			return;
		try {
			double value = Double.parseDouble(referenceValue.getText().trim()),
					time = Double.parseDouble(referenceTime.getText().trim());
			ReferenceKind kind = row.reference != null && value == row.reference.value
					&& time == row.reference.timeSeconds ? row.reference.kind : ReferenceKind.EXPLICIT;
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
			current.plan.references.put(row.key, SpotFractionCalculator.reference(row.input, row.key.quantity, kind,
					value, time, referenceSource.getText().trim(), true, donors, inputMap()));
			recalculateStaged();
		} catch (Exception e) {
			error(e);
		}
	}

	private void borrow(boolean preconsumed) {
		Row row = selectedRow();
		if (row == null)
			return;
		List<String> candidates = new ArrayList<>();
		for (Row other : current.rows)
			if (!other.input.name.equals(row.input.name) && other.input.cage == row.input.cage
					&& other.reference != null && other.reference.kind == ReferenceKind.INITIAL
					&& other.reference.approved && other.issues.isEmpty())
				candidates.add(other.input.name);
		if (candidates.isEmpty()) {
			error(new IllegalArgumentException("No approved initial-reference donors in this cage"));
			return;
		}
		List<String> descriptions = new ArrayList<>();
		for (String name : candidates)
			for (Row donor : current.rows)
				if (donor.input.name.equals(name))
					descriptions.add(name + " | reference " + donor.reference.value + " | " + donor.input.stimulus
							+ " / " + donor.input.concentration);
		JList<String> donorList = new JList<>(descriptions.toArray(new String[0]));
		for (int i = 0; i < candidates.size(); i++)
			for (Row donor : current.rows)
				if (donor.input.name.equals(candidates.get(i))
						&& Objects.equals(donor.input.stimulus, row.input.stimulus)
						&& Objects.equals(donor.input.concentration, row.input.concentration))
					donorList.addSelectionInterval(i, i);
		if (JOptionPane.showConfirmDialog(this, new JScrollPane(donorList), "Select comparable reference donors",
				JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
			return;
		List<String> chosen = new ArrayList<>();
		for (int index : donorList.getSelectedIndices())
			chosen.add(candidates.get(index));
		try {
			current.plan.references.put(row.key,
					SpotFractionCalculator.borrow(row.input, row.key, chosen, current.plan, inputMap(), preconsumed));
			recalculateStaged();
		} catch (Exception e) {
			error(e);
		}
	}

	private void excludeDeposit() {
		Row row = selectedRow();
		if (row == null)
			return;
		current.plan.references.put(row.key, new Reference(ReferenceKind.FAILED_DEPOSIT, Double.NaN, 0,
				"User identified failed deposit", true, "", Collections.emptyList()));
		recalculateStaged();
	}

	private void removeOverride() {
		Row row = selectedRow();
		if (row == null)
			return;
		current.plan.settings.remove(row.key);
		spotOverride.setSelected(false);
		readControls(current.plan.settingsFor(current.selection));
		recalculateStaged();
	}

	private void recalculateStaged() {
		State staged = current;
		String name = selectedRow() == null ? "" : selectedRow().input.name;
		run(new Task() {
			@Override
			State execute() {
				SpotFractionPlan plan = staged.plan.copy();
				return new State(staged.target, staged.selection, plan, staged.inputs,
						SpotFractionCalculator.calculate(staged.inputs, staged.selection, plan));
			}

			@Override
			void complete(State state) {
				show(state);
				for (int i = 0; i < state.rows.size(); i++)
					if (state.rows.get(i).input.name.equals(name)) {
						int visible = spots.convertRowIndexToView(i);
						spots.setRowSelectionInterval(visible, visible);
						break;
					}
				status.setText("Reference/override decision staged — Calculate & save current to retain it.");
			}
		});
	}

	private void locateSpot() {
		Row row = selectedRow();
		if (row == null || parent == null)
			return;
		Spot spot = current.target.experiment.getSpots().findSpotByName(row.input.name);
		if (spot != null)
			parent.dlgSpots.onMeasureChartSpotClicked(spot);
		else
			status.setText("Open the experiment's camera viewer to inspect this spot.");
	}

	private abstract class Task {
		abstract State execute() throws Exception;

		abstract void complete(State state);

		void progress(String message) {
			SwingUtilities.invokeLater(() -> status.setText(message));
		}
	}

	private void run(Task task) {
		if (busy)
			return;
		busy = true;
		for (JButton button : actionButtons)
			button.setEnabled(false);
		cancelRequested = false;
		stop.setEnabled(true);
		Map<JComponent, Boolean> enabled = new IdentityHashMap<>();
		for (JComponent component : Arrays.asList(method, quantity, similar, changing, tail, toleranceMode,
				illumination, tolerance, maximumGap, median, first, last, spotOverride, referenceValue, referenceTime,
				referenceSource, spots, reviews, aggregateGroup, aggregateMeasure)) {
			enabled.put(component, component.isEnabled());
			component.setEnabled(false);
		}
		boolean comboEnabled = parent != null && parent.expListComboLazy.isEnabled();
		if (parent != null)
			parent.expListComboLazy.setEnabled(false);
		status.setText("Calculating from saved measurements…");
		new SwingWorker<State, Void>() {
			@Override
			protected State doInBackground() throws Exception {
				return task.execute();
			}

			@Override
			protected void done() {
				busy = false;
				for (JButton button : actionButtons)
					button.setEnabled(true);
				if (parent != null)
					parent.expListComboLazy.setEnabled(comboEnabled);
				stop.setEnabled(false);
				enabled.forEach(JComponent::setEnabled);
				try {
					task.complete(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					error(e);
				} catch (ExecutionException e) {
					error(e.getCause());
				}
			}
		}.execute();
	}

	private void clearRows() {
		spotModel.setRowCount(0);
		chart.setChart(null);
		aggregateGroup.removeAllItems();
		aggregateChart.setChart(null);
		diagnosticsChart.setChart(null);
		referenceStatus.setText(" ");
		context.setText("Load or preview the selected experiment and measurement quantity.");
	}

	private void error(Throwable error) {
		status.setText("Cannot calculate: " + error.getMessage());
		report.append(error.toString() + "\n");
	}

	private JButton button(String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(e -> action.run());
		actionButtons.add(button);
		return button;
	}

	private static JPanel row(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		for (Component c : components)
			panel.add(c);
		return panel;
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
		if ("color_integral".equals(value))
			return "Color integral (vertical line)";
		if (value instanceof ReferenceEstimation)
			return value.toString();
		return String.valueOf(value).toLowerCase(Locale.ROOT).replace('_', ' ');
	}
}
