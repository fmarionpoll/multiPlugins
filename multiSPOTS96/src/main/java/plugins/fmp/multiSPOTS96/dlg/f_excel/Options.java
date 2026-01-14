package plugins.fmp.multiSPOTS96.dlg.f_excel;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxMs;

public class Options extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1814896922714679663L;
	JCheckBox exportAllFilesCheckBox = new JCheckBox("all experiments", true);
	JCheckBox transposeCheckBox = new JCheckBox("transpose", true);

	JSpinner binSize = new JSpinner(new SpinnerNumberModel(1., 1., 1000., 1.));
	JComboBoxMs binUnit = new JComboBoxMs();

	JRadioButton isFloatingFrameButton = new JRadioButton("all", true);
	JRadioButton isFixedFrameButton = new JRadioButton("from ", false);
	JSpinner startJSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 10000., 1.));
	JSpinner endJSpinner = new JSpinner(new SpinnerNumberModel(240., 1., 99999999., 1.));
	JComboBoxMs intervalsUnit = new JComboBoxMs();

	void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(0);

		JPanel panel0 = new JPanel(layout1);
		panel0.add(exportAllFilesCheckBox);
		panel0.add(transposeCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(new JLabel("Analyze "));
		panel1.add(isFloatingFrameButton);
		panel1.add(isFixedFrameButton);
		panel1.add(startJSpinner);
		panel1.add(new JLabel(" to "));
		panel1.add(endJSpinner);
		panel1.add(intervalsUnit);
		intervalsUnit.setSelectedIndex(2);
		add(panel1);

		JPanel panel2 = new JPanel(layout1);
		panel2.add(new JLabel("bin size "));
		panel2.add(binSize);
		panel2.add(binUnit);
		binUnit.setSelectedIndex(2);
		add(panel2);

		enableIntervalButtons(false);
		ButtonGroup group = new ButtonGroup();
		group.add(isFloatingFrameButton);
		group.add(isFixedFrameButton);

		defineActionListeners();
	}

	private void defineActionListeners() {

		isFixedFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(true);
			}
		});

		isFloatingFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(false);
			}
		});
	}

	private void enableIntervalButtons(boolean isSelected) {
		startJSpinner.setEnabled(isSelected);
		endJSpinner.setEnabled(isSelected);
		intervalsUnit.setEnabled(isSelected);
	}

	int getExcelBuildStep() {
		double binValue = (double) binSize.getValue();
		double buildStep = binValue * binUnit.getMsUnitValue();
		return (int) buildStep;
	}

	long getStartAllMs() {
		long startAll = (long) (((double) startJSpinner.getValue()) * intervalsUnit.getMsUnitValue());
		return startAll;
	}

	long getEndAllMs() {
		long endAll = (long) (((double) endJSpinner.getValue()) * intervalsUnit.getMsUnitValue());
		return endAll;
	}

	public boolean getIsFixedFrame() {
		return isFixedFrameButton.isSelected();
	}

	public long getStartMs() {
		return (long) ((double) startJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	public long getEndMs() {
		return (long) ((double) endJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	public long getBinMs() {
		return (long) ((double) binSize.getValue() * (double) binUnit.getMsUnitValue());
	}
}
