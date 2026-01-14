package plugins.fmp.multicafe.dlg.excel;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class Levels extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton = new JButton("save XLS (v1)");
	JButton exportToXLSButton2 = new JButton("save XLS");

	JCheckBox topLevelCheckBox = new JCheckBox("top", true);
	// JCheckBox topLevelDeltaCheckBox = new JCheckBox("delta top", false);
	JCheckBox bottomLevelCheckBox = new JCheckBox("bottom", false);
	JCheckBox subtractEvaporationCheckBox = new JCheckBox("subtract evaporation", true);
	JCheckBox lrPICheckBox = new JCheckBox("L+R & pref index", true);
	JLabel lrPILabel = new JLabel("compute PI only if L+R > ");
	JCheckBox sumPerCageCheckBox = new JCheckBox("sum/cage", false);
	JSpinner lrPIThresholdJSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0., 100., 0.01));

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(topLevelCheckBox);
//		panel0.add(topLevelDeltaCheckBox);
		panel0.add(bottomLevelCheckBox);
		panel0.add(subtractEvaporationCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout0);
		panel1.add(sumPerCageCheckBox);
		panel1.add(lrPICheckBox);
		panel1.add(lrPILabel);
		panel1.add(lrPIThresholdJSpinner);
		add(panel1);

		FlowLayout flowLayout2 = new FlowLayout(FlowLayout.RIGHT);
		flowLayout2.setVgap(0);
		JPanel panel2 = new JPanel(flowLayout2);
		panel2.add(exportToXLSButton2);
		add(panel2);

		defineActionListeners();
	}

	private void defineActionListeners() {
		exportToXLSButton2.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				firePropertyChange("EXPORT_KYMOSDATA", false, true);
			}
		});

		lrPICheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (lrPICheckBox.isSelected())
					enablePI(true);
			}
		});

		sumPerCageCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (sumPerCageCheckBox.isSelected())
					enablePI(false);
			}
		});
	}

	private void enablePI(boolean yes) {
		sumPerCageCheckBox.setSelected(!yes);
		lrPICheckBox.setSelected(yes);
		lrPIThresholdJSpinner.setEnabled(yes);
		lrPILabel.setEnabled(yes);
	}

}
