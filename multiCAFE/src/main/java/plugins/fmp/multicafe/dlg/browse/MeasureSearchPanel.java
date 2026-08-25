package plugins.fmp.multicafe.dlg.browse;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

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
import plugins.fmp.multitools.tools.Logger;

/**
 * Find capillaries matching a measure rule. Scan narrows the browse list to
 * matching experiments; browse {@code <}/{@code >} then skips good recordings.
 * Selecting an experiment silently opens its first matching capillary.
 */
public class MeasureSearchPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private enum Preset {
		BOTTOM_MAD_HIGH("Bottom MAD high"),
		BOTTOM_BASELINE_MISSING("Bottom baseline missing"),
		BOTTOM_SERIES_NOISY("Bottom series RANGE"),
		TOP_RUNAWAY("Top RANGE high"),
		TOP_JUMP("Derivative ABSMAX");

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
	private JButton restoreListButton = new JButton("Restore list");
	private JLabel hitLabel = new JLabel("Find: --");

	private MultiCAFE parent0 = null;
	private List<MeasureFilterHit> hits = new ArrayList<>();
	private boolean suppressUi = false;
	private boolean scanRunning = false;
	private boolean suppressBrowseFollow = false;

	void init(MultiCAFE parent0) {
		this.parent0 = parent0;
		setBorder(javax.swing.BorderFactory.createTitledBorder("Find measure outliers"));
		setLayout(new GridLayout(3, 1));
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel row0 = new JPanel(left);
		row0.add(new JLabel("preset"));
		row0.add(presetCombo);
		allCheckBox.setToolTipText(
				"Checked: scan every experiment in the master list. Unchecked: scan only the currently selected experiment.");
		row0.add(allCheckBox);
		scanButton.setToolTipText(
				"Scan for matching capillaries, keep only those experiments in the browse list, then open the first hit.");
		row0.add(scanButton);
		add(row0);

		JPanel row1 = new JPanel(left);
		row1.add(sourceCombo);
		row1.add(statCombo);
		row1.add(opCombo);
		row1.add(thresholdSpinner);
		row1.add(threshold2Label);
		row1.add(threshold2Spinner);
		add(row1);

		JPanel row2 = new JPanel(left);
		row2.add(hitLabel);
		restoreListButton.setToolTipText("Restore the full experiment list (undo Find’s filter).");
		row2.add(restoreListButton);
		add(row2);

		defineListeners();
		applyPreset(Preset.BOTTOM_MAD_HIGH);
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
		scanButton.addActionListener(e -> runScan());
		restoreListButton.addActionListener(e -> restoreBrowseList());
		parent0.expListComboLazy.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED || suppressBrowseFollow || hits.isEmpty())
				return;
			followBrowseSelection();
		});
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
			default:
				break;
			}
		} finally {
			suppressUi = false;
		}
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
						narrowBrowseListToHits();
						MeasureFilterHit first = hits.get(0);
						updateHitLabel(first);
						openHit(first);
					}
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

	private void narrowBrowseListToHits() {
		LinkedHashSet<Experiment> keep = new LinkedHashSet<>();
		for (MeasureFilterHit h : hits) {
			if (h.experiment != null)
				keep.add(h.experiment);
		}
		if (keep.isEmpty())
			return;
		List<Experiment> narrowed = new ArrayList<>(keep);
		suppressBrowseFollow = true;
		try {
			parent0.expListComboLazy.setExperimentsFromList(narrowed);
			parent0.paneBrowse.browsePanel.setListFiltered(true);
			if (parent0.expListComboLazy.getItemCount() > 0)
				parent0.expListComboLazy.setSelectedIndex(0);
		} finally {
			suppressBrowseFollow = false;
		}
		Logger.info("Find: browse list narrowed to " + narrowed.size() + " experiment(s)");
	}

	private void restoreBrowseList() {
		if (parent0 == null)
			return;
		hits.clear();
		parent0.paneBrowse.filterPanel.filterExperimentList(false);
		updateHitLabel(null);
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
