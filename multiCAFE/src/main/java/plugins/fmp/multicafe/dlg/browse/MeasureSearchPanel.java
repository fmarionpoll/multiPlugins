package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.measurefilter.CapillaryMeasureFilter;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterHit;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterOp;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterRule;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterSource;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterStat;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner.Result;
import plugins.fmp.multitools.service.tracking.ExperimentMovementPrescanner.TrackingStatus;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;

/**
 * Find capillaries matching a measure rule. Scan narrows the browse list to
 * matching experiments; browse {@code <}/{@code >} then skips good recordings.
 * Selecting an experiment silently opens its first matching capillary.
 */
public class MeasureSearchPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private enum Preset {
		IMAGE_MOVEMENT("Image movement"),
		BOTTOM_MAD_HIGH("Bottom MAD high"),
		BOTTOM_BASELINE_MISSING("Bottom baseline missing"),
		BOTTOM_SERIES_NOISY("Bottom series RANGE"),
		TOP_RUNAWAY("Top RANGE high"),
		TOP_JUMP("Derivative ABSMAX"),
		T00_GT_T0_EARLY_DRINK("t00 > t0 (early drink)"),
		T0_GT_T00_ARTEFACT("t0 > t00 (artefact?)");

		final String label;

		Preset(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private JComboBox<Preset> presetCombo = new JComboBox<Preset>(Preset.values());
	private JComboBox<MeasureFilterSource> sourceCombo = new JComboBox<MeasureFilterSource>(MeasureFilterSource.values());
	private JComboBox<MeasureFilterStat> statCombo = new JComboBox<MeasureFilterStat>(MeasureFilterStat.values());
	private JComboBox<MeasureFilterOp> opCombo = new JComboBox<MeasureFilterOp>(MeasureFilterOp.values());
	private JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(3.0, -1e6, 1e6, 0.5));
	private JSpinner threshold2Spinner = new JSpinner(new SpinnerNumberModel(10.0, -1e6, 1e6, 0.5));
	private JLabel threshold2Label = new JLabel("and");
	private JCheckBox allCheckBox = new JCheckBox("All", true);

	private JButton scanButton = new JButton("Scan");
	private JButton selectionButton = new JButton("Select found experiments");
	private JLabel hitLabel = new JLabel("Find: --");
	private JButton movementCancelButton = new JButton("Stop scan");
	private JSpinner movementSamplesSpinner = new JSpinner(new SpinnerNumberModel(10, 3, 30, 1));
	private JSpinner movementThresholdSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.5, 30.0, 0.5));
	private JCheckBox excludeTrackedCheckBox = new JCheckBox("Exclude already tracked", true);
	private JLabel movementLabel = new JLabel("Movement: not scanned");
	private JPanel searchOptionsPanel = new JPanel(new CardLayout());
	private static final String CARD_MOVEMENT = "movement";
	private static final String CARD_MEASURE = "measure";

	private MultiCAFE parent0 = null;
	private List<MeasureFilterHit> hits = new ArrayList<>();
	private boolean suppressUi = false;
	private boolean scanRunning = false;
	private boolean suppressBrowseFollow = false;
	private List<Result> movementResults = new ArrayList<>();
	private List<Result> movementCandidates = new ArrayList<>();
	private IcyFrame movementResultsFrame;
	private JButton movementResultsSelectionButton;
	private volatile boolean movementCancelRequested;
	private double movementThresholdUsed = 2.0;
	private JCheckBox includeUncertainMovementCheckBox = new JCheckBox("Include uncertain registrations", false);
	private boolean findSelectionActive = false;
	private boolean lastResultsWereMovement = true;
	private List<Experiment> listBeforeFindSelection = new ArrayList<Experiment>();

	void init(MultiCAFE parent0) {
		this.parent0 = parent0;
		setBorder(javax.swing.BorderFactory.createTitledBorder("Find experiments"));
		setLayout(new GridLayout(3, 1));
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel row0 = new JPanel(left);
		row0.add(new JLabel("Search for:"));
		row0.add(presetCombo);
		allCheckBox.setToolTipText(
				"Checked: scan every experiment in the master list. Unchecked: scan only the currently selected experiment.");
		row0.add(allCheckBox);
		scanButton.setToolTipText("Run the selected experiment search.");
		row0.add(scanButton);
		selectionButton.setEnabled(false);
		selectionButton.setToolTipText("Limit browsing to the experiments found by the last scan.");
		row0.add(selectionButton);
		movementCancelButton.setEnabled(false);
		movementCancelButton.setVisible(false);
		movementCancelButton.setForeground(Color.RED.darker());
		movementCancelButton.setToolTipText("Stop after the current sampled frame and keep completed results.");
		row0.add(movementCancelButton);
		add(row0);

		JPanel measureOptions = new JPanel(left);
		measureOptions.add(sourceCombo);
		measureOptions.add(statCombo);
		measureOptions.add(opCombo);
		measureOptions.add(thresholdSpinner);
		measureOptions.add(threshold2Label);
		measureOptions.add(threshold2Spinner);
		searchOptionsPanel.add(measureOptions, CARD_MEASURE);

		JPanel movementOptions = new JPanel(left);
		movementOptions.add(new JLabel("sampled frames"));
		movementOptions.add(movementSamplesSpinner);
		movementOptions.add(new JLabel("candidate >="));
		movementOptions.add(movementThresholdSpinner);
		movementOptions.add(includeUncertainMovementCheckBox);
		includeUncertainMovementCheckBox.setToolTipText("Include isolated movement estimates and poor matches; these are not confirmed movement.");
		movementOptions.add(new JLabel("px"));
		excludeTrackedCheckBox.setToolTipText("Exclude experiments with saved time-dependent geometry or manual tracking segments from the results.");
		movementOptions.add(excludeTrackedCheckBox);
		searchOptionsPanel.add(movementOptions, CARD_MOVEMENT);
		add(searchOptionsPanel);

		JPanel row2 = new JPanel(left);
		row2.add(hitLabel);
		row2.add(movementLabel);
		add(row2);

		defineListeners();
		presetCombo.setSelectedItem(Preset.IMAGE_MOVEMENT);
		applyPreset(Preset.IMAGE_MOVEMENT);
		updateFieldEnablement();
		updateHitLabel(null);
	}

	private void defineListeners() {
		presetCombo.addActionListener(e -> {
			if (suppressUi)
				return;
			Preset p = (Preset) presetCombo.getSelectedItem();
			if (p != null)
				applyPreset(p);
		});
		sourceCombo.addActionListener(e -> {
			if (!suppressUi)
				updateFieldEnablement();
		});
		opCombo.addActionListener(e -> {
			if (!suppressUi)
				updateFieldEnablement();
		});
		scanButton.addActionListener(e -> {
			if (presetCombo.getSelectedItem() == Preset.IMAGE_MOVEMENT)
				runMovementPrescan();
			else
				runScan();
		});
		selectionButton.addActionListener(e -> toggleFoundExperimentSelection());
		movementCancelButton.addActionListener(e -> {
			movementCancelRequested = true;
			movementCancelButton.setEnabled(false);
			movementLabel.setText("Movement: stopping after current frame...");
		});
		parent0.expListComboLazy.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED || suppressBrowseFollow)
				return;
			if (!hits.isEmpty())
				followBrowseSelection();
			updateMovementLabelForSelection();
		});
	}

	private void runMovementPrescan() {
		if (parent0 == null || scanRunning)
			return;
		prepareForNewScan(true);
		List<Experiment> experiments = resolveScanScope();
		if (experiments.isEmpty())
			return;
		final int samples = ((Number) movementSamplesSpinner.getValue()).intValue();
		final double threshold = ((Number) movementThresholdSpinner.getValue()).doubleValue();
		final boolean excludeTracked = excludeTrackedCheckBox.isSelected();
		final boolean includeUncertain = includeUncertainMovementCheckBox.isSelected();
		movementCancelRequested = false;
		scanRunning = true;
		scanButton.setEnabled(false);
		movementCancelButton.setEnabled(true);
		movementCancelButton.setVisible(true);
		movementLabel.setText("Movement: scanning...");
		final ProgressFrame progress = new ProgressFrame("Prescan movement...");
		progress.setLength(experiments.size());
		SwingWorker<List<Result>, Void> worker = new SwingWorker<List<Result>, Void>() {
			@Override
			protected List<Result> doInBackground() {
				ExperimentMovementPrescanner scanner = new ExperimentMovementPrescanner();
				List<Result> results = new ArrayList<Result>();
				int skippedTracked = 0;
				for (int i = 0; i < experiments.size(); i++) {
					if (movementCancelRequested)
						break;
					Experiment exp = experiments.get(i);
					progress.setMessage("Movement " + (i + 1) + " / " + experiments.size() + " — " + exp);
					Result result = scanner.scan(exp, samples, () -> movementCancelRequested);
					if (excludeTracked && result.trackingStatus != TrackingStatus.NOT_TRACKED) {
						skippedTracked++;
					} else if (result.sampledFrames > 0 || !movementCancelRequested) {
						results.add(result);
					}
					progress.setPosition((double) (i + 1) / experiments.size());
				}
				Logger.info("Movement prescan: skipped " + skippedTracked + " already tracked experiment(s)");
				return results;
			}

			@Override
			protected void done() {
				try {
					movementResults = get();
					movementThresholdUsed = threshold;
					hits.clear();
					movementCandidates = new ArrayList<Result>();
					int unscored = 0;
					for (Result result : movementResults) {
						if (!result.succeeded()) unscored++;
						else if (result.isCandidate(threshold) || (includeUncertain && result.assessment(threshold)
								== ExperimentMovementPrescanner.Assessment.UNCERTAIN)) movementCandidates.add(result);
					}
					lastResultsWereMovement = true;
					updateSelectionControls();
					updateMovementLabelForSelection();
					showMovementResults();
					Logger.info("Movement prescan" + (movementCancelRequested ? " (stopped)" : "") + ": "
							+ movementCandidates.size() + " candidate(s), " + unscored + " unscored");
				} catch (Exception ex) {
					movementResults.clear();
					movementLabel.setText("Movement: scan failed");
					Logger.warn("Movement prescan failed: " + ex.getMessage());
					JOptionPane.showMessageDialog(MeasureSearchPanel.this,
							"Movement prescan failed: " + ex.getMessage(), "Find", JOptionPane.ERROR_MESSAGE);
				} finally {
					progress.close();
					scanRunning = false;
					scanButton.setEnabled(true);
					movementCancelButton.setEnabled(false);
					movementCancelButton.setVisible(false);
					movementCancelRequested = false;
				}
			}
		};
		worker.execute();
	}

	private void showMovementResults() {
		if (movementResultsFrame != null)
			movementResultsFrame.close();
		String[] columns = { "Record", "Tracking status", "Priority", "Move px", "Rotation deg", "Scale %",
				"Residual px", "Frame", "Confidence %", "Detected pattern", "Path", "Assessment" };
		DefaultTableModel model = new DefaultTableModel(columns, 0) {
			private static final long serialVersionUID = 1L;
			@Override public boolean isCellEditable(int row, int column) { return false; }
		};
		for (Result result : movementCandidates)
			model.addRow(new Object[] { recordId(result.experiment), result.trackingStatus.toString(),
					result.reviewPriorityLabel(movementThresholdUsed), String.format("%.1f", result.maxDisplacementPx),
					String.format("%.3f", result.maxRotationDeg),
					String.format("%.3f", result.maxScalePercent), String.format("%.1f", result.maxResidualPx),
					result.worstFrame, String.format("%.0f", result.confidence * 100),
					result.detectedPattern(movementThresholdUsed), experimentPath(result.experiment),
					result.assessment(movementThresholdUsed) });
		JTable table = new JTable(model);
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(model);
		sorter.setComparator(2, (a, b) -> Integer.compare(priorityRank(a), priorityRank(b)));
		sorter.setSortKeys(java.util.Arrays.asList(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
		table.setRowSorter(sorter);
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(1).setPreferredWidth(170);
		table.getColumnModel().getColumn(2).setPreferredWidth(125);
		table.getColumnModel().getColumn(9).setPreferredWidth(300);
		table.getColumnModel().getColumn(10).setPreferredWidth(500);
		movementResultsSelectionButton = new JButton("Select found experiments");
		movementResultsSelectionButton.setEnabled(!movementCandidates.isEmpty());
		movementResultsSelectionButton.addActionListener(e -> toggleFoundExperimentSelection());
		JButton exportButton = new JButton("Export all assessments...");
		exportButton.setEnabled(!movementResults.isEmpty());
		exportButton.addActionListener(e -> exportMovementCandidatesCsv());
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bottom.add(movementResultsSelectionButton);
		bottom.add(exportButton);
		long uncertainCount = movementResults.stream().filter(r -> r.assessment(movementThresholdUsed)
				== ExperimentMovementPrescanner.Assessment.UNCERTAIN).count();
		bottom.add(new JLabel(uncertainCount + " uncertain registration(s); available in export"));
		bottom.add(new JLabel(movementCandidates.size() + " flagged record(s)"
				+ (movementCancelRequested ? " — partial scan (stopped by user)" : "")));
		movementResultsFrame = new IcyFrame("Movement prescan results", true, true);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(950, 360));
		movementResultsFrame.add(scroll, BorderLayout.CENTER);
		movementResultsFrame.add(bottom, BorderLayout.SOUTH);
		movementResultsFrame.pack();
		movementResultsFrame.addToDesktopPane();
		movementResultsFrame.setVisible(true);
	}

	private void exportMovementCandidatesCsv() {
		if (movementResults.isEmpty())
			return;
		String startDirectory = experimentPath(movementResults.get(0).experiment);
		java.io.File start = startDirectory.isEmpty() ? null : new java.io.File(startDirectory);
		if (start != null && !start.isDirectory()) start = start.getParentFile();
		try {
			String filename = Dialog.saveFileAs("movement_prescan.csv",
					start == null ? null : start.getAbsolutePath(), "csv");
			if (filename == null)
				return;
			List<Result> ordered = new ArrayList<Result>(movementResults);
			ordered.sort((a, b) -> {
				int priority = Integer.compare(b.reviewPriority(movementThresholdUsed),
						a.reviewPriority(movementThresholdUsed));
				return priority != 0 ? priority : Double.compare(b.maxDisplacementPx, a.maxDisplacementPx);
			});
			CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("Path", "Record", "Tracking_status", "Priority", "Move_px",
					"Rotation_deg", "Scale_percent", "Residual_px", "Frame", "Confidence_percent",
					"Detected_pattern", "Assessment", "Threshold_px", "Sampled_frames", "Failed_samples").setSkipHeaderRecord(false).build();
			try (CSVPrinter printer = new CSVPrinter(
					Files.newBufferedWriter(Paths.get(filename), StandardCharsets.UTF_8), format)) {
				for (Result result : ordered)
					printer.printRecord(experimentPath(result.experiment), recordId(result.experiment),
							result.trackingStatus.toString(), result.reviewPriorityLabel(movementThresholdUsed), result.maxDisplacementPx,
							result.maxRotationDeg, result.maxScalePercent, result.maxResidualPx, result.worstFrame,
							result.confidence * 100, result.detectedPattern(movementThresholdUsed),
							result.assessment(movementThresholdUsed), movementThresholdUsed, result.sampledFrames, result.failedSamples);
			}
			JOptionPane.showMessageDialog(this, "Exported " + ordered.size() + " assessed records to:\n" + filename,
					"Movement prescan", JOptionPane.INFORMATION_MESSAGE);
		} catch (FileDialogException | IOException ex) {
			Logger.warn("Movement CSV export failed: " + ex.getMessage());
			JOptionPane.showMessageDialog(this, "CSV export failed: " + ex.getMessage(), "Movement prescan",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private String experimentPath(Experiment experiment) {
		if (experiment == null)
			return "";
		String path = experiment.getImagesDirectory();
		return path == null || path.trim().isEmpty() ? experiment.toString() : path;
	}

	private static int priorityRank(Object value) {
		String text = value == null ? "" : value.toString();
		if (text.startsWith("Very high")) return 3;
		if (text.startsWith("High")) return 2;
		if (text.startsWith("Moderate")) return 1;
		return 0;
	}

	private int recordId(Experiment experiment) {
		if (experiment == null)
			return -1;
		List<Experiment> master = parent0.paneBrowse.filterPanel.filterExpList.getExperimentsAsListNoLoad();
		for (int i = 0; i < master.size(); i++)
			if (master.get(i) == experiment || master.get(i).toString().equals(experiment.toString()))
				return i;
		for (int i = 0; i < parent0.expListComboLazy.getItemCount(); i++) {
			Experiment listed = parent0.expListComboLazy.getItemAtNoLoad(i);
			if (listed == experiment || (listed != null && listed.toString().equals(experiment.toString())))
				return i;
		}
		return -1;
	}

	private void updateMovementLabelForSelection() {
		Experiment selected = parent0.expListComboLazy.getItemAtNoLoad(parent0.expListComboLazy.getSelectedIndex());
		for (Result result : movementResults) {
			if (result.experiment == selected || (selected != null && result.experiment != null
					&& selected.toString().equals(result.experiment.toString()))) {
				movementLabel.setText("Movement: " + result.format());
				return;
			}
		}
		if (!movementResults.isEmpty())
			movementLabel.setText("Movement: not in scan results");
	}

	private void applyPreset(Preset preset) {
		suppressUi = true;
		try {
			switch (preset) {
			case BOTTOM_MAD_HIGH:
				sourceCombo.setSelectedItem(MeasureFilterSource.BOTTOM_BASELINE_MAD);
				statCombo.setSelectedItem(MeasureFilterStat.VALUE);
				opCombo.setSelectedItem(MeasureFilterOp.GE);
				thresholdSpinner.setValue(3.0);
				break;
			case BOTTOM_BASELINE_MISSING:
				sourceCombo.setSelectedItem(MeasureFilterSource.BOTTOM_BASELINE_Y);
				statCombo.setSelectedItem(MeasureFilterStat.VALUE);
				opCombo.setSelectedItem(MeasureFilterOp.IS_NAN);
				break;
			case BOTTOM_SERIES_NOISY:
				sourceCombo.setSelectedItem(MeasureFilterSource.BOTTOMLEVEL);
				statCombo.setSelectedItem(MeasureFilterStat.RANGE);
				opCombo.setSelectedItem(MeasureFilterOp.GE);
				thresholdSpinner.setValue(20.0);
				break;
			case TOP_RUNAWAY:
				sourceCombo.setSelectedItem(MeasureFilterSource.TOPRAW);
				statCombo.setSelectedItem(MeasureFilterStat.RANGE);
				opCombo.setSelectedItem(MeasureFilterOp.GE);
				thresholdSpinner.setValue(50.0);
				break;
			case TOP_JUMP:
				sourceCombo.setSelectedItem(MeasureFilterSource.DERIVEDVALUES);
				statCombo.setSelectedItem(MeasureFilterStat.ABSMAX);
				opCombo.setSelectedItem(MeasureFilterOp.GE);
				thresholdSpinner.setValue(10.0);
				break;
			case T00_GT_T0_EARLY_DRINK:
				sourceCombo.setSelectedItem(MeasureFilterSource.T00_MINUS_T0_FILL_PX);
				statCombo.setSelectedItem(MeasureFilterStat.VALUE);
				opCombo.setSelectedItem(MeasureFilterOp.GT);
				thresholdSpinner.setValue(0.0);
				break;
			case T0_GT_T00_ARTEFACT:
				sourceCombo.setSelectedItem(MeasureFilterSource.T00_MINUS_T0_FILL_PX);
				statCombo.setSelectedItem(MeasureFilterStat.VALUE);
				opCombo.setSelectedItem(MeasureFilterOp.LT);
				thresholdSpinner.setValue(0.0);
				break;
			default:
				break;
			}
		} finally {
			suppressUi = false;
		}
		CardLayout cards = (CardLayout) searchOptionsPanel.getLayout();
		cards.show(searchOptionsPanel, preset == Preset.IMAGE_MOVEMENT ? CARD_MOVEMENT : CARD_MEASURE);
		hitLabel.setVisible(preset != Preset.IMAGE_MOVEMENT);
		movementLabel.setVisible(preset == Preset.IMAGE_MOVEMENT);
		updateFieldEnablement();
	}

	private void updateFieldEnablement() {
		MeasureFilterSource src = (MeasureFilterSource) sourceCombo.getSelectedItem();
		MeasureFilterOp op = (MeasureFilterOp) opCombo.getSelectedItem();
		boolean scalar = src != null && src.isScalar();
		statCombo.setEnabled(!scalar);
		if (scalar)
			statCombo.setSelectedItem(MeasureFilterStat.VALUE);
		boolean needThresh = op != MeasureFilterOp.IS_NAN;
		thresholdSpinner.setEnabled(needThresh);
		boolean between = op == MeasureFilterOp.BETWEEN;
		threshold2Label.setVisible(between);
		threshold2Spinner.setVisible(between);
		threshold2Spinner.setEnabled(between);
	}

	private MeasureFilterRule buildRuleFromUi() {
		MeasureFilterRule rule = new MeasureFilterRule();
		rule.source = (MeasureFilterSource) sourceCombo.getSelectedItem();
		rule.stat = (MeasureFilterStat) statCombo.getSelectedItem();
		rule.op = (MeasureFilterOp) opCombo.getSelectedItem();
		rule.threshold = ((Number) thresholdSpinner.getValue()).doubleValue();
		rule.threshold2 = ((Number) threshold2Spinner.getValue()).doubleValue();
		if (rule.source != null && rule.source.isScalar())
			rule.stat = MeasureFilterStat.VALUE;
		return rule;
	}

	private List<Experiment> resolveScanScope() {
		if (!allCheckBox.isSelected()) {
			Experiment selected = parent0.expListComboLazy.getItemAtNoLoad(parent0.expListComboLazy.getSelectedIndex());
			List<Experiment> one = new ArrayList<>(1);
			if (selected != null)
				one.add(selected);
			return one;
		}
		FilterPanel filter = parent0.paneBrowse != null ? parent0.paneBrowse.filterPanel : null;
		if (filter != null && filter.filterExpList != null && filter.filterExpList.getItemCount() > 0)
			return filter.filterExpList.getExperimentsAsListNoLoad();
		return parent0.expListComboLazy.getExperimentsAsListNoLoad();
	}

	private void runScan() {
		if (parent0 == null || parent0.expListComboLazy == null || scanRunning)
			return;
		prepareForNewScan(false);
		List<Experiment> experiments = resolveScanScope();
		if (experiments.isEmpty()) {
			hits.clear();
			updateHitLabel(null);
			JOptionPane.showMessageDialog(this, "None found — no experiments to scan.", "Find",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		MeasureFilterRule rule = buildRuleFromUi();
		scanRunning = true;
		scanButton.setEnabled(false);
		hitLabel.setText("Find: scanning...");

		final ProgressFrame progress = new ProgressFrame("Find: examining experiments...");
		progress.setLength(experiments.size());

		SwingWorker<List<MeasureFilterHit>, Void> worker = new SwingWorker<List<MeasureFilterHit>, Void>() {
			@Override
			protected List<MeasureFilterHit> doInBackground() {
				return CapillaryMeasureFilter.scan(experiments, rule, (index, total, exp) -> {
					String label = exp != null ? exp.toString() : "?";
					progress.setMessage("Find: examining experiment " + (index + 1) + " / " + total + " — " + label);
					progress.setPosition((double) (index + 1) / Math.max(1, total));
				});
			}

			@Override
			protected void done() {
				try {
					hits = get();
					if (hits == null)
						hits = new ArrayList<>();
					if (hits.isEmpty()) {
						updateHitLabel(null);
						JOptionPane.showMessageDialog(MeasureSearchPanel.this,
								"None found — no capillaries matched the criteria.", "Find",
								JOptionPane.INFORMATION_MESSAGE);
						Logger.info("Find: none found");
					} else {
						Logger.info("Find: " + hits.size() + " hit(s) in " + countHitExperiments() + " experiment(s)");
						updateHitLabel(null);
					}
					lastResultsWereMovement = false;
					updateSelectionControls();
				} catch (Exception ex) {
					Logger.warn("Find scan failed: " + ex.getMessage());
					hits.clear();
					updateHitLabel(null);
					JOptionPane.showMessageDialog(MeasureSearchPanel.this, "Find scan failed: " + ex.getMessage(),
							"Find", JOptionPane.ERROR_MESSAGE);
				} finally {
					progress.close();
					scanRunning = false;
					scanButton.setEnabled(true);
				}
			}
		};
		worker.execute();
	}

	/** When browse {@code <}/{@code >} changes experiment, open that experiment's first hit. */
	private void followBrowseSelection() {
		Experiment selected = parent0.expListComboLazy.getItemAtNoLoad(parent0.expListComboLazy.getSelectedIndex());
		MeasureFilterHit hit = firstHitForExperiment(selected);
		updateHitLabel(hit);
		if (hit != null)
			SwingUtilities.invokeLater(() -> selectCapillaryForHit(hit));
	}

	private MeasureFilterHit firstHitForExperiment(Experiment exp) {
		if (exp == null)
			return null;
		for (MeasureFilterHit h : hits) {
			if (h.experiment == exp)
				return h;
			if (h.experiment != null && exp.toString().equals(h.experiment.toString()))
				return h;
		}
		return null;
	}

	private void openHit(MeasureFilterHit hit) {
		if (hit == null || parent0 == null)
			return;
		int idx = indexOfExperimentInBrowseList(hit.experiment);
		if (idx < 0 && hit.experiment != null)
			idx = parent0.expListComboLazy.getExperimentIndexFromExptName(hit.experiment.toString());
		if (idx < 0) {
			Logger.warn("Find: experiment not in browse list for hit " + hit.formatLabel());
			return;
		}
		int current = parent0.expListComboLazy.getSelectedIndex();
		if (current != idx) {
			suppressBrowseFollow = true;
			try {
				parent0.expListComboLazy.setSelectedIndex(idx);
			} finally {
				suppressBrowseFollow = false;
			}
		}
		SwingUtilities.invokeLater(() -> selectCapillaryForHit(hit));
	}

	private int indexOfExperimentInBrowseList(Experiment target) {
		if (target == null)
			return -1;
		int n = parent0.expListComboLazy.getItemCount();
		for (int i = 0; i < n; i++) {
			Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
			if (exp == target)
				return i;
		}
		return -1;
	}

	private void selectCapillaryForHit(MeasureFilterHit hit) {
		Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(parent0.expListComboLazy.getSelectedIndex());
		if (exp == null)
			exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getCapillaries() == null)
			return;
		Capillary cap = findCapillary(exp, hit);
		if (cap == null) {
			Logger.warn("Find: capillary not found: " + hit.capillaryName);
			return;
		}
		int t = cap.getKymographIndex();
		if (t < 0)
			t = hit.kymographIndex;
		if (t >= 0 && parent0.paneKymos != null && parent0.paneKymos.tabIntervals != null)
			parent0.paneKymos.tabIntervals.selectKymographImage(t, false);
		if (parent0.paneLevels != null) {
			parent0.paneLevels.selectCapillaryForDetectionDialogs(cap);
			if (hit.rule != null && hit.rule.source != null && hit.rule.source.isBottomRelated())
				parent0.paneLevels.selectBottomTab();
		}
		if (exp.getSeqKymos() != null) {
			plugins.fmp.multitools.experiment.capillaries.CapillariesSequenceMapper.transferMeasuresToKymos(
					exp.getCapillaries(), exp.getSeqKymos());
		}
		Logger.info("Find -> " + hit.formatLabel());
	}

	private Capillary findCapillary(Experiment exp, MeasureFilterHit hit) {
		List<Capillary> list = exp.getCapillaries().getList();
		if (list == null)
			return null;
		if (hit.kymographIndex >= 0) {
			for (Capillary cap : list) {
				if (cap != null && cap.getKymographIndex() == hit.kymographIndex)
					return cap;
			}
		}
		for (Capillary cap : list) {
			if (cap == null)
				continue;
			String n2 = cap.getLast2ofCapillaryName();
			String roi = cap.getRoiName();
			if (hit.capillaryName.equals(n2) || hit.capillaryName.equals(roi))
				return cap;
		}
		return null;
	}

	private List<Experiment> foundExperiments() {
		LinkedHashSet<Experiment> keep = new LinkedHashSet<>();
		if (lastResultsWereMovement) {
			for (Result result : movementCandidates)
				if (result.experiment != null)
					keep.add(result.experiment);
		} else {
			for (MeasureFilterHit hit : hits)
				if (hit.experiment != null)
					keep.add(hit.experiment);
		}
		return new ArrayList<Experiment>(keep);
	}

	private void toggleFoundExperimentSelection() {
		if (findSelectionActive)
			restoreBrowseList();
		else
			selectFoundExperiments();
	}

	private void selectFoundExperiments() {
		List<Experiment> found = foundExperiments();
		if (found.isEmpty())
			return;
		listBeforeFindSelection = parent0.expListComboLazy.getExperimentsAsListNoLoad();
		suppressBrowseFollow = true;
		try {
			parent0.expListComboLazy.setExperimentsFromList(found);
			if (parent0.expListComboLazy.getItemCount() > 0)
				parent0.expListComboLazy.setSelectedIndex(0);
		} finally {
			suppressBrowseFollow = false;
		}
		findSelectionActive = true;
		updateSelectionControls();
		if (lastResultsWereMovement)
			updateMovementLabelForSelection();
		else if (!hits.isEmpty()) {
			MeasureFilterHit first = hits.get(0);
			updateHitLabel(first);
			openHit(first);
		}
		Logger.info("Find: browse list narrowed to " + found.size() + " experiment(s)");
	}

	private void restoreBrowseList() {
		if (parent0 == null || !findSelectionActive)
			return;
		suppressBrowseFollow = true;
		try {
			parent0.expListComboLazy.setExperimentsFromList(listBeforeFindSelection);
			if (parent0.expListComboLazy.getItemCount() > 0)
				parent0.expListComboLazy.setSelectedIndex(0);
		} finally {
			suppressBrowseFollow = false;
		}
		findSelectionActive = false;
		listBeforeFindSelection.clear();
		updateSelectionControls();
		if (lastResultsWereMovement)
			updateMovementLabelForSelection();
		else
			updateHitLabel(null);
	}

	private void prepareForNewScan(boolean movement) {
		if (findSelectionActive)
			restoreBrowseList();
		hits.clear();
		movementResults.clear();
		movementCandidates.clear();
		if (movementResultsFrame != null) {
			movementResultsFrame.close();
			movementResultsFrame = null;
			movementResultsSelectionButton = null;
		}
		lastResultsWereMovement = movement;
		updateHitLabel(null);
		movementLabel.setText("Movement: not scanned");
		updateSelectionControls();
	}

	private void updateSelectionControls() {
		boolean hasResults = !foundExperiments().isEmpty();
		String text = findSelectionActive ? "Restore full list" : "Select found experiments";
		selectionButton.setText(text);
		selectionButton.setEnabled(findSelectionActive || hasResults);
		if (movementResultsSelectionButton != null) {
			movementResultsSelectionButton.setText(text);
			movementResultsSelectionButton.setEnabled(findSelectionActive || hasResults);
		}
		if (parent0 != null && parent0.paneBrowse != null)
			parent0.paneBrowse.browsePanel.setFindSelectionActive(findSelectionActive);
	}

	private int countHitExperiments() {
		LinkedHashSet<Experiment> set = new LinkedHashSet<>();
		for (MeasureFilterHit h : hits) {
			if (h.experiment != null)
				set.add(h.experiment);
		}
		return set.size();
	}

	private void updateHitLabel(MeasureFilterHit current) {
		if (hits == null || hits.isEmpty()) {
			hitLabel.setText("Find: —");
			return;
		}
		int nExp = countHitExperiments();
		if (current == null) {
			hitLabel.setText(String.format("Find: %d exp / %d caps — use browse < >", nExp, hits.size()));
			return;
		}
		hitLabel.setText(String.format("Find: %d exp / %d caps — %s", nExp, hits.size(), current.formatLabel()));
	}
}
