package plugins.fmp.multiSPOTS.dlg.excel;

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

public class SpotsAreas extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1290058998782225526L;

	JButton exportToXLSButton = new JButton("save XLS (v1)");
	JButton exportToXLSButton2 = new JButton("save XLS");

	JCheckBox sumCheckBox = new JCheckBox("sum", true);
	JCheckBox nPixelsCheckBox = new JCheckBox("n pixels", true);
	JCheckBox t0CheckBox = new JCheckBox("(t0-t)/t0", true);

	JCheckBox lrPICheckBox = new JCheckBox("top+bottom & pref index", false);
	JLabel lrPILabel = new JLabel("compute PI only if top+bottom > ");
	JCheckBox sumPerCageCheckBox = new JCheckBox("sum/cage", false);
	JSpinner lrPIThresholdJSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0., 100., 0.01));

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout flowLayout0 = new FlowLayout(FlowLayout.LEFT);
		flowLayout0.setVgap(0);
		JPanel panel0 = new JPanel(flowLayout0);
		panel0.add(sumCheckBox);
		panel0.add(nPixelsCheckBox);
		panel0.add(t0CheckBox);
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
				firePropertyChange("EXPORT_SPOTSMEASURES", false, true);
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
