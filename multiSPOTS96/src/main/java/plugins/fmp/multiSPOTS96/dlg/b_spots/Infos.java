package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4950182090521600937L;

	private JButton editCagesButton = new JButton("Edit cages infos...");
	private JButton editSpotsButton = new JButton("Edit spots infos...");
	private InfosCageTable infosCageTable = null;
	private InfosSpotTable infosSpotTable = null;
	private JComboBoxExperimentLazy expListComboLazy = null;

	void init(GridLayout gridLayout, JComboBoxExperimentLazy expListComboLazy) {
		setLayout(gridLayout);
		this.expListComboLazy = expListComboLazy;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(editCagesButton);
		add(panel01);

		JPanel panel02 = new JPanel(layoutLeft);
		panel02.add(editSpotsButton);
		add(panel02);

		declareListeners();
	}

	private void declareListeners() {
		editCagesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					editCagesInfos(exp);
			}
		});

		editSpotsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					editSpotsInfos(exp);
			}
		});
	}

	void editCagesInfos(Experiment exp) {
		if (infosCageTable != null) {
			infosCageTable.close();
		}
		infosCageTable = new InfosCageTable();
		infosCageTable.initialize(expListComboLazy);
		infosCageTable.requestFocus();
	}

	void editSpotsInfos(Experiment exp) {
		if (infosSpotTable != null) {
			infosSpotTable.close();
		}
		infosSpotTable = new InfosSpotTable();
		infosSpotTable.initialize(expListComboLazy);
		infosSpotTable.requestFocus();
	}

	public void selectCage(Cage cage) {
		if (infosCageTable == null)
			return;
		infosCageTable.selectRowFromCage(cage);
	}

	public void selectSpot(Spot spot) {
		if (infosSpotTable == null)
			return;
		infosSpotTable.selectRowFromSpot(spot);
	}
}