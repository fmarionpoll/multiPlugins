package plugins.fmp.multiSPOTS.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.results.AggSumCleanPolicy;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * V4: evaluation policies for {@code AGG_SUMCLEAN} and {@code AGG_SUMCLEAN_V5}
 * (baseline skip, common-mode drift, fly guard, reference-stimulus drift).
 * Charts read policy via {@link #applyPolicyInto(ResultsOptions)}.
 */
public class ConsumptionAggV4Panel extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private MultiSPOTS parent0 = null;
	private JComboBox<AggSumCleanPolicy> policyCombo = new JComboBox<>(AggSumCleanPolicy.values());
	private JSpinner baselineSkipSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
	private JSpinner flyGuardPercentSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 100, 1));
	private JTextField refStimulusField = new JTextField(12);
	private JTextField refConcField = new JTextField(8);
	private JButton applyButton = new JButton("Apply & refresh AGG chart");
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.setBorder(BorderFactory.createTitledBorder("AGG V4 (evaluation)"));
		panel0.add(new JLabel("Policy:"));
		panel0.add(policyCombo);
		panel0.add(applyButton);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Baseline skip (min or bins):"));
		panel1.add(baselineSkipSpinner);
		panel1.add(new JLabel("Fly guard max % ROI:"));
		panel1.add(flyGuardPercentSpinner);
		panel1.add(new JLabel("Ref stimulus:"));
		panel1.add(refStimulusField);
		panel1.add(new JLabel("Ref conc:"));
		panel1.add(refConcField);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(new JLabel(
				"<html><body style='width:560px'><b>Baseline+</b>: skips the first N minutes (or N bins if no camera time axis) when taking the per-spot baseline max.<br/>"
						+ "<b>Common mode</b>: subtracts cage-wide median <code>sumClean</code> drift from each spot before depletion (baseline max still from uncorrected <code>sumClean</code>).<br/>"
						+ "<b>Fly guard</b>: sets per-spot depletion to 0 in bins where fly occupancy exceeds the % threshold (needs fly mask).<br/>"
						+ "<b>Ref stimulus</b>: subtracts median drift of spots matching reference (stimulus, conc) in the same cage; if none match, behaves like Legacy for drift.</body></html>"));

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(statusLabel);
		SpotsMeasuresUi.layoutStackedRows(this, panel0, panel1, panel2, panel3);

		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onApplyClicked();
			}
		});
	}

	/**
	 * Copies current V4 UI into {@code o} (typically the chart
	 * {@link ResultsOptions}).
	 */
	public void applyPolicyInto(ResultsOptions o) {
		if (o == null) {
			return;
		}
		o.aggSumCleanPolicy = (AggSumCleanPolicy) policyCombo.getSelectedItem();
		if (o.aggSumCleanPolicy == null) {
			o.aggSumCleanPolicy = AggSumCleanPolicy.LEGACY;
		}
		o.aggBaselineSkipMinutes = ((Number) baselineSkipSpinner.getValue()).intValue();
		double pct = ((Number) flyGuardPercentSpinner.getValue()).doubleValue();
		o.aggFlyGuardMaxFraction = Math.max(0.0, Math.min(1.0, pct / 100.0));
		o.aggRefStimulus = refStimulusField.getText() != null ? refStimulusField.getText().trim() : "";
		o.aggRefConcentration = refConcField.getText() != null ? refConcField.getText().trim() : "";
	}

	private void onApplyClicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		if (parent0.dlgMeasure == null || parent0.dlgMeasure.chartsPanel == null) {
			statusLabel.setText("Open Charts tab and select AGG_SUMCLEAN or AGG_SUMCLEAN_V5, then Display results.");
			return;
		}
		parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
		statusLabel.setText("Chart refresh requested (policy=" + policyCombo.getSelectedItem() + ").");
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}
}
