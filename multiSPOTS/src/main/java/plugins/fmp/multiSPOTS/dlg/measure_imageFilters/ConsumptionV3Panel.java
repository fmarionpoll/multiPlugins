package plugins.fmp.multiSPOTS.dlg.measure_imageFilters;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;

/**
 * Tier A V3: experiment-median residual on {@code sumClean}. Tab layout uses
 * {@link SpotsMeasuresUi} so rows stay compact (no fixed-height {@link GridLayout} stretch).
 */
public class ConsumptionV3Panel extends JPanel implements PropertyChangeListener {
	private static final long serialVersionUID = 1L;

	private MultiSPOTS parent0 = null;
	private JButton rebuildV3Button = new JButton("Rebuild cleanV3");
	private JSpinner v3SmoothBinsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 501, 2));
	private JSpinner v3LambdaSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 99.0, 0.1));
	private JSpinner v3StepBinsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 60, 1));
	private JCheckBox perCageMedianCheckBox = new JCheckBox("Per-cage median (36 spots per cage)", true);
	private JLabel statusLabel = new JLabel(" ", SwingConstants.LEFT);

	void init(GridLayout capLayout, MultiSPOTS parent0) {
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
		panel0.add(perCageMedianCheckBox);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("<html><body style='width:520px'>Each spot is shifted by the median of its first "
				+ Spots.SUMCLEAN_V3_BASELINE_PREFIX_BINS
				+ " <code>sumClean</code> bins (handles darker t0). The reference curve is the median of shifted spots per time bin &mdash; within each cage if the box above is checked (recommended when cages differ in brightness), otherwise across the whole experiment.</body></html>"));

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(statusLabel);
		SpotsMeasuresUi.layoutStackedRows(this, panel0, panel1, panel2);

		perCageMedianCheckBox.setToolTipText(
				"When on, the reference median at each time bin uses only spots in the same cage (same lighting region). When off, all ready spots on the experiment are pooled (can bias dark cages negative if other cages are brighter).");
		rebuildV3Button.setToolTipText(
				"Recomputes cleanV3: shift each spot by early-bin median of sumClean, take median of shifted sumClean per bin (per cage or whole experiment), smooth with running median (odd W), subtract from shifted sumClean.");
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
		Spots.SumCleanV3MedianRebuildSummary sum = exp.getSpots().rebuildV3ResidualFromSumCleanMedian(exp, w,
				perCageMedianCheckBox.isSelected());
		exp.getSpots().transferMeasuresToLevel2D();
		statusLabel.setText("cleanV3 rebuilt (W=" + w + ")" + sum.statusSuffix());
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
