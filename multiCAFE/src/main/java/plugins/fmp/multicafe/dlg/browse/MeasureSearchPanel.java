package plugins.fmp.multicafe.dlg.browse;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

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

	private JButton scanButton = new JButton("Scan");
	private JButton prevHitButton = new JButton("< hit");
	private JButton nextHitButton = new JButton("hit >");
	private JButton applyListButton = new JButton("Apply to list");
	private JButton clearListButton = new JButton("Clear list");
	private JLabel hitLabel = new JLabel("Find: --");

	private MultiCAFE parent0 = null;
	private List<MeasureFilterHit> hits = new ArrayList<>();
	private int hitIndex = -1;
	private boolean suppressUi = false;

	void init(MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(new GridLayout(3, 1));
		FlowLayout left = new FlowLayout(FlowLayout.LEFT);
		left.setVgap(0);

		JPanel row0 = new JPanel(left);
		row0.add(new JLabel("preset"));
		row0.add(presetCombo);
		scanButton.setToolTipText("Scan the current browse list (honors metadata Filter) for matching capillaries.");
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
		row2.add(prevHitButton);
		row2.add(nextHitButton);
		row2.add(hitLabel);
		applyListButton.setToolTipText("Keep only experiments that contain at least one hit.");
		clearListButton.setToolTipText("Restore the full experiment list from Filter's master copy.");
		row2.add(applyListButton);
		row2.add(clearListButton);
		add(row2);

		defineListeners();
		applyPreset(Preset.BOTTOM_MAD_HIGH);
		updateFieldEnablement();
		updateHitLabel();
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
		prevHitButton.addActionListener(e -> navigateHit(-1));
		nextHitButton.addActionListener(e -> navigateHit(1));
		applyListButton.addActionListener(e -> applyHitsToBrowseList());
		clearListButton.addActionListener(e -> clearBrowseListNarrow());
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

	private void runScan() {
		if (parent0 == null || parent0.expListComboLazy == null)
			return;
		List<Experiment> experiments = parent0.expListComboLazy.getExperimentsAsListNoLoad();
		if (experiments.isEmpty()) {
			hits.clear();
			hitIndex = -1;
			updateHitLabel();
			Logger.info("Find: no experiments in browse list");
			return;
		}
		MeasureFilterRule rule = buildRuleFromUi();
		ProgressFrame progress = new ProgressFrame("Find: scanning measures...");
		try {
			hits = CapillaryMeasureFilter.scan(experiments, rule);
			hitIndex = hits.isEmpty() ? -1 : 0;
			updateHitLabel();
			Logger.info("Find: " + hits.size() + " hit(s)");
			if (!hits.isEmpty())
				navigateHit(0);
		} finally {
			progress.close();
		}
	}

	private void navigateHit(int delta) {
		if (hits.isEmpty())
			return;
		if (hitIndex < 0)
			hitIndex = 0;
		else if (delta != 0)
			hitIndex = (hitIndex + delta + hits.size()) % hits.size();
		updateHitLabel();
		MeasureFilterHit hit = hits.get(hitIndex);
		openHit(hit);
	}

	private void openHit(MeasureFilterHit hit) {
		if (hit == null || parent0 == null)
			return;
		int n = parent0.expListComboLazy.getItemCount();
		if (hit.experimentIndex < 0 || hit.experimentIndex >= n) {
			Logger.warn("Find: stale hit experiment index " + hit.experimentIndex);
			return;
		}
		int current = parent0.expListComboLazy.getSelectedIndex();
		if (current != hit.experimentIndex) {
			parent0.expListComboLazy.setSelectedIndex(hit.experimentIndex);
		}
		SwingUtilities.invokeLater(() -> selectCapillaryForHit(hit));
	}

	private void selectCapillaryForHit(MeasureFilterHit hit) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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

	private void applyHitsToBrowseList() {
		if (parent0 == null || hits.isEmpty())
			return;
		LinkedHashSet<Integer> idxs = new LinkedHashSet<>();
		for (MeasureFilterHit h : hits)
			idxs.add(h.experimentIndex);
		List<Experiment> narrowed = new ArrayList<>();
		List<Experiment> current = parent0.expListComboLazy.getExperimentsAsListNoLoad();
		for (Integer i : idxs) {
			if (i != null && i >= 0 && i < current.size())
				narrowed.add(current.get(i));
		}
		if (narrowed.isEmpty())
			return;
		parent0.expListComboLazy.setExperimentsFromList(narrowed);
		parent0.paneBrowse.browsePanel.setListFiltered(true);
		if (parent0.expListComboLazy.getItemCount() > 0)
			parent0.expListComboLazy.setSelectedIndex(0);
		hits.clear();
		hitIndex = -1;
		updateHitLabel();
		Logger.info("Find: browse list narrowed to " + narrowed.size() + " experiment(s)");
	}

	private void clearBrowseListNarrow() {
		if (parent0 == null)
			return;
		parent0.paneBrowse.filterPanel.filterExperimentList(false);
		hits.clear();
		hitIndex = -1;
		updateHitLabel();
	}

	private void updateHitLabel() {
		if (hits == null || hits.isEmpty()) {
			hitLabel.setText("Find: 0 hits");
			return;
		}
		if (hitIndex < 0 || hitIndex >= hits.size()) {
			hitLabel.setText("Find: " + hits.size() + " hits");
			return;
		}
		hitLabel.setText(String.format("Find: %d/%d — %s", hitIndex + 1, hits.size(), hits.get(hitIndex).formatLabel()));
	}
}