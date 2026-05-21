package plugins.fmp.multiSPOTS96.dlg.spotsMeasures;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;

/**
 * Tier A V3: experiment-median residual on {@code sumClean}. Layout matches {@code _DlgSpots_} tabs:
 * {@link GridLayout}(4,1) on the tab root and one {@link FlowLayout} row panel per line with {@code vgap 0}.
 */
public class ConsumptionV3Panel extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private MultiSPOTS96 parent0 = null;
	private JButton rebuildV3Button = new JButton("Rebuild cleanV3");
	private JSpinner v3SmoothBinsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 501, 2));
	private JSpinner v3LambdaSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 99.0, 0.1));
	private JSpinner v3StepBinsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 60, 1));
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.setBorder(BorderFactory.createTitledBorder("Consumption V3 (Tier A)"));
		panel0.add(new JLabel("Smooth W (bins):"));
		panel0.add(v3SmoothBinsSpinner);
		panel0.add(new JLabel("\u03bb (reserved):"));
		panel0.add(v3LambdaSpinner);
		panel0.add(new JLabel("step bins (reserved):"));
		panel0.add(v3StepBinsSpinner);
		panel0.add(rebuildV3Button);
		add(panel0);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("<html><body style='width:520px'>Per-spot median over the first "
				+ Spots.SUMCLEAN_V3_BASELINE_PREFIX_BINS
				+ " bins of <code>sumClean</code> removes most heterogeneous background before the experiment-wide median; residual can still go negative when a spot drops faster than the cohort.</body></html>"));
		add(panel1);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		add(panel2);

		JPanel panel3 = new JPanel(layoutLeft);
		add(panel3);

		rebuildV3Button.setToolTipText(
				"Recomputes cleanV3: shift each spot by early-bin median of sumClean, take experiment-wide median per bin, smooth with running median (odd W), subtract from shifted sumClean.");
		v3LambdaSpinner.setToolTipText("Reserved for automated step detection (Tier B).");
		v3StepBinsSpinner.setToolTipText("Reserved for automated step detection (Tier B).");

		rebuildV3Button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onRebuildV3Clicked();
			}
		});
	}

	private void onRebuildV3Clicked() {
		if (parent0 == null) {
			return;
		}
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSpots() == null) {
			statusLabel.setText("No experiment.");
			return;
		}
		int w = ((Number) v3SmoothBinsSpinner.getValue()).intValue();
		exp.getSpots().rebuildV3ResidualFromSumCleanExperimentMedian(w);
		exp.getSpots().transferMeasuresToLevel2D();
		statusLabel.setText("cleanV3 rebuilt (W=" + w + ").");
		refreshChartsIfPresent(exp);
	}

	private void refreshChartsIfPresent(Experiment exp) {
		if (parent0.dlgMeasure != null && parent0.dlgMeasure.chartsPanel != null) {
			parent0.dlgMeasure.chartsPanel.displayChartPanels(exp);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
	}
}
