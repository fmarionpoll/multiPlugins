package plugins.fmp.multiSPOTS.dlg.export;

import java.awt.FlowLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Excel and CSV export for spot series grouped by stimulus and concentration
 * within each cage.
 */
public class AggregatedSpotsAreasPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	JButton exportAggregatedButton = new JButton("Save filter depletion XLS");
	JButton exportKymoAggButton = new JButton("Save kymograph height loss XLS");
	JButton exportCsvButton = new JButton("Save CSV");
	JSpinner baselineMinutesSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 120, 1));
	JCheckBox stopWhenStableCheckBox = new JCheckBox("stop when max stable", false);
	JSpinner stableBinsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 60, 1));
	JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);

	void init(GridLayout capLayout) {
		setLayout(capLayout);
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);

		JPanel panel0 = new JPanel(flow);
		panel0.add(new JLabel("baseline (min)"));
		panel0.add(baselineMinutesSpinner);
		panel0.add(stopWhenStableCheckBox);
		panel0.add(new JLabel("stable bins"));
		panel0.add(stableBinsSpinner);
		add(panel0);

		JPanel panel1 = new JPanel(flow);
		panel1.add(discardNoFlyCageCheckBox);
		add(panel1);

		FlowLayout actionsLayout = new FlowLayout(FlowLayout.RIGHT);
		actionsLayout.setVgap(0);
		JPanel panel2 = new JPanel(actionsLayout);
		exportAggregatedButton.setToolTipText("Group spots by compound and concentration within each cage.");
		exportKymoAggButton.setToolTipText("Group spots by compound and concentration within each cage.");
		panel2.add(exportAggregatedButton);
		panel2.add(exportKymoAggButton);
		exportCsvButton.setToolTipText("Save all grouped aggregates: filter, V5, color, green height, X-line and rim, with SUM, PI and PInorm.");
		panel2.add(exportCsvButton);
		add(panel2);
		defineActionListeners();
		setCsvMode(false);
	}

	void setCsvMode(boolean csv) {
		exportAggregatedButton.setEnabled(!csv);
		exportKymoAggButton.setEnabled(!csv);
		exportCsvButton.setVisible(csv);
		stableBinsSpinner.setEnabled(stopWhenStableCheckBox.isSelected());
	}

	private void defineActionListeners() {

		exportAggregatedButton.addActionListener(e -> firePropertyChange("EXPORT_AGGREGATED_SPOTSMEASURES", false, true));
		exportKymoAggButton.addActionListener(e -> firePropertyChange("EXPORT_AGGREGATED_KYMO_SPOTSMEASURES", false, true));
		exportCsvButton.addActionListener(e -> firePropertyChange("EXPORT_AGGREGATED_CSV_SPOTSMEASURES", false, true));
		stopWhenStableCheckBox.addActionListener(e -> stableBinsSpinner.setEnabled(stopWhenStableCheckBox.isSelected()));
	}
}
