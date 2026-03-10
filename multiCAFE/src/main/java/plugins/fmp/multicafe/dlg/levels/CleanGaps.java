package plugins.fmp.multicafe.dlg.levels;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;



public class CleanGaps extends JPanel {
	private static final long serialVersionUID = 6031521157029550040L;

	private JRadioButton useKymograph = new JRadioButton("kymographs", true);
	private JRadioButton useRawImages = new JRadioButton("raw images", false);
	private JButton runButton = new JButton("Run...");
	
	private MultiCAFE parent0 = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(0);
		panel0.add(new JLabel("Detect black zones from:"));
		panel0.add(useRawImages);
		panel0.add(useKymograph);
		ButtonGroup group = new ButtonGroup ();
		group.add(useKymograph);
		group.add(useRawImages);
		add(panel0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(runButton);
		add(panel01);

		JPanel panel1 = new JPanel(layoutLeft);
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null) 
					return;
				if (useKymograph.isSelected())
					runFromKymos(exp);
				else
					runFromMeasures(exp);
			}});

	}


	private void runFromKymos(Experiment exp) {
	}

	private void runFromMeasures(Experiment exp) {
	}


}
