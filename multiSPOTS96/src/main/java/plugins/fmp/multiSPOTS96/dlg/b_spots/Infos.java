package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;

public class Infos extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4950182090521600937L;

	private JButton editCagesButton = new JButton("Edit cages infos...");
	private JButton editSpotsButton = new JButton("Edit spots infos...");
	private InfosCageTable infosCageTable = null;
	private InfosSpotTable infosSpotTable = null;
	private MultiSPOTS96 parent0 = null;

	void init(GridLayout gridLayout, MultiSPOTS96 parent0) {
		setLayout(gridLayout);
		this.parent0 = parent0;

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
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					editCagesInfos(exp);
			}
		});

		editSpotsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
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
		infosCageTable.initialize(parent0);
		infosCageTable.requestFocus();
	}

	void editSpotsInfos(Experiment exp) {
		if (infosSpotTable != null) {
			infosSpotTable.close();
		}
		infosSpotTable = new InfosSpotTable();
		infosSpotTable.initialize(parent0);
		infosSpotTable.requestFocus();
	}
	
	public void selectCage(Cage cage) {
		if (infosCageTable == null) return;
		infosCageTable.selectRowFromCage(cage);		
	}
	
	public void selectSpot(Spot spot) {
		if (infosSpotTable == null) return;
		infosSpotTable.selectRowFromSpot(spot);		
	}
}