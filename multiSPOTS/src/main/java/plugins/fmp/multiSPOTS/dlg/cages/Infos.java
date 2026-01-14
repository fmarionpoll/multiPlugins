package plugins.fmp.multiSPOTS.dlg.cages;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3325915033686366985L;
	private JButton editCagesButton = new JButton("Edit cages infos...");
	private MultiSPOTS parent0 = null;
	private InfosTable dialog = null;
	private List<Cage> cagesArrayCopy = new ArrayList<Cage>();

	JRadioButton useCages = new JRadioButton("cages");
	JRadioButton useManual = new JRadioButton("manual entry");
	ButtonGroup useGroup = new ButtonGroup();

	private JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(78., 0., 100., 1.));
	private JSpinner pixelsSpinner = new JSpinner(new SpinnerNumberModel(5, 0, 1000, 1));
	String measureString = "get span between 1rst and last cage";
	private JButton measureButton = new JButton(measureString);

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0a = new JPanel(flowLayout);
		panel0a.add(useManual);
		panel0a.add(useCages);

		panel0a.add(measureButton);
		add(panel0a);
		useGroup.add(useCages);
		useGroup.add(useManual);
		useCages.setSelected(true);

		JPanel panel00 = new JPanel(flowLayout);
		panel00.add(new JLabel("length in mm:", SwingConstants.RIGHT));
		panel00.add(lengthSpinner);
		panel00.add(new JLabel("length in pixels:", SwingConstants.RIGHT));
		panel00.add(pixelsSpinner);

		add(panel00);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(editCagesButton);
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		editCagesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
				if (exp != null) {
//					exp.spotsArray.transferDescriptionToSpots();
					exp.cagesArray.transferNFliesFromSpotsToCages(exp.spotsArray);
					dialog = new InfosTable();
					dialog.initialize(parent0, cagesArrayCopy);
				}
			}
		});

		useCages.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				lengthSpinner.setValue(78.);
				measureButton.setText(measureString);
				measureButton.setVisible(true);
			}
		});

		useManual.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				measureButton.setVisible(false);
			}
		});

		measureButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				measureCagesSpan();
			}
		});
	}

	void measureCagesSpan() {
		Experiment exp = (Experiment) parent0.expListCombo.getSelectedItem();
		if (exp != null) {
			exp.spotsArray.transferROIsFromSequenceToSpots(exp.seqCamData.seq);
			if (exp.spotsArray.spotsList.size() > 0) {
				int npixels = exp.cagesArray.getHorizontalSpanOfCages();
				if (npixels > 0)
					pixelsSpinner.setValue(npixels);
			}
		}
	}

}
