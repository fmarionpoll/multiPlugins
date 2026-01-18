package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import icy.gui.frame.IcyFrame;
import icy.roi.ROI;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.spots.spot.Spot;
import plugins.fmp.multitools.experiment.spots.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.spots.SpotTable;
import plugins.fmp.multitools.experiment.spots.spots.Spots;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class InfosSpotTable extends JPanel implements ListSelectionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8611587540329642259L;
	IcyFrame dialogFrame = null;
	private SpotTable spotTable = null;
	private JButton copyButton = new JButton("Copy table");
	private JButton pasteButton = new JButton("Paste");
	private JButton getNPixelsButton = new JButton("Get n pixels");
	private JButton selectedSpotButton = new JButton("Locate selected spot");

	private JButton duplicateRowAtCagePositionButton = new JButton("Row at cage pos");
	private JButton duplicatePreviousButton = new JButton("Row above");
	private JButton duplicateNextButton = new JButton("Row below");
	private JButton duplicateCageButton = new JButton("Cage to all");
	private JButton duplicateAllButton = new JButton("Cell to all");

	private JComboBoxExperimentLazy expListComboLazy = null;
	private Spots allSpotsCopy = null;

	public void initialize(JComboBoxExperimentLazy expListComboLazy) {
		this.expListComboLazy = expListComboLazy;

		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(copyButton);
		panel1.add(pasteButton);
		panel1.add(getNPixelsButton);
		panel1.add(selectedSpotButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Duplicate:"));
		panel2.add(duplicateRowAtCagePositionButton);
		panel2.add(duplicateAllButton);
		panel2.add(duplicateCageButton);
		panel2.add(duplicatePreviousButton);
		panel2.add(duplicateNextButton);
		topPanel.add(panel2);

		JPanel tablePanel = new JPanel();
		spotTable = new SpotTable(expListComboLazy);
		tablePanel.add(new JScrollPane(spotTable));
		spotTable.getSelectionModel().addListSelectionListener(this);

		dialogFrame = new IcyFrame("Spots properties", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(tablePanel, BorderLayout.CENTER);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.setLocation(new Point(5, 5));
		dialogFrame.setVisible(true);
		defineActionListeners();

		pasteButton.setEnabled(false);
	}

	private void defineActionListeners() {
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null) {
					Spots allSpots = exp.getSpots();
					allSpotsCopy = exp.getCages().getAllSpotsArray(allSpots);
					pasteButton.setEnabled(true);
				}
			}
		});

		pasteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null) {
					Spots allSpots = exp.getSpots();
					Spots spotsArray = exp.getCages().getAllSpotsArray(allSpots);
					allSpotsCopy.pasteSpotsInfo(spotsArray);
				}
			}
		});

		getNPixelsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					measureNPixelsForAllSpots(exp);
			}
		});

		duplicateRowAtCagePositionButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					duplicatePos(exp);
			}
		});

		duplicateCageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					duplicateCage(exp);
			}
		});

		duplicatePreviousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					duplicateRelativeRow(exp, -1);
			}
		});

		duplicateNextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					duplicateRelativeRow(exp, 1);
			}
		});

		duplicateAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null) {
					duplicateAll(exp);
				}
			}
		});

		selectedSpotButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null)
					locateSelectedROI(exp);
			}
		});

		spotTable.spotTableModel.fireTableDataChanged();
	}

	void close() {
		dialogFrame.close();
	}

	private void locateSelectedROI(Experiment exp) {
		ArrayList<ROI> roiList = exp.getSeqCamData().getSequence().getSelectedROIs();
		if (roiList.size() > 0) {
			Spots allSpots = exp.getSpots();
			Spot spot = null;
			for (ROI roi : roiList) {
				String name = roi.getName();
				if (name.contains("spot")) {
					spot = exp.getCages().getSpotFromROIName(name, allSpots);
					continue;
				}
				if (name.contains("cage")) {
					Cage cage = exp.getCages().getCageFromName(name);
					java.util.List<Spot> cageSpots = cage.getSpotList(allSpots);
					if (!cageSpots.isEmpty()) {
						spot = cageSpots.get(0);
					}
					break;
				}
			}
			if (spot != null)
				selectRowFromSpot(spot);
		}
	}

	private void measureNPixelsForAllSpots(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int columnIndex = 1;
		for (Cage cage : exp.getCages().cagesList) {
			List<Spot> cageSpots = cage.getSpotList(allSpots);
			for (Spot spot : cageSpots) {
				try {
					int value = (int) spot.getRoi().getNumberOfPoints();
					int iID = exp.getCages().getSpotGlobalPosition(spot, allSpots);
					spotTable.spotTableModel.setValueAt(value, iID, columnIndex);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private void transferFromSpot(Experiment exp, Spot spotTo, Spot spotFrom) {
		Spots allSpots = exp.getSpots();
		int iID = exp.getCages().getSpotGlobalPosition(spotTo, allSpots);
		int columnIndex = 2;
		SpotProperties prop = spotFrom.getProperties();
		spotTable.spotTableModel.setValueAt(prop.getSpotVolume(), iID, columnIndex);
		columnIndex = 5;
		spotTable.spotTableModel.setValueAt(prop.getCageRow(), iID, columnIndex);
		columnIndex = 7;
		spotTable.spotTableModel.setValueAt(prop.getStimulus(), iID, columnIndex);
		columnIndex = 8;
		spotTable.spotTableModel.setValueAt(prop.getConcentration(), iID, columnIndex);
		columnIndex = 9;
		spotTable.spotTableModel.setValueAt(prop.getColor(), iID, columnIndex);
	}

	private void duplicatePos(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0)
			return;

		String spotName = (String) spotTable.getValueAt(rowIndex, 0);
		Spot spotFrom = exp.getCages().getSpotFromROIName(spotName, allSpots);
		if (spotFrom == null) {
			System.out.println("spot not found: " + spotName);
			return;
		}

		SpotProperties prop = spotFrom.getProperties();

		int cagePosition = prop.getCagePosition();
		int cageID = prop.getCageID();

		for (Cage cage : exp.getCages().cagesList) {
			if (cage.getProperties().getCageID() == cageID)
				continue;
			List<Spot> cageSpots = cage.getSpotList(allSpots);
			for (Spot spot : cageSpots) {
				if (spot.getProperties().getCagePosition() != cagePosition)
					continue;
				transferFromSpot(exp, spot, spotFrom);
			}
		}
	}

	private void duplicateRelativeRow(Experiment exp, int delta) {
		Spots allSpots = exp.getSpots();
		int rowTo = spotTable.getSelectedRow();
		if (rowTo < 0)
			return;

		int rowFrom = rowTo + delta;
		if (rowFrom < 0 || rowFrom > spotTable.getRowCount())
			return;

		String spotName = (String) spotTable.getValueAt(rowFrom, 0);
		Spot spotFrom = exp.getCages().getSpotFromROIName(spotName, allSpots);
		if (spotFrom == null) {
			System.out.println("spot not found or invalid: " + spotName);
			return;
		}

		spotName = (String) spotTable.getValueAt(rowTo, 0);
		Spot spotTo = exp.getCages().getSpotFromROIName(spotName, allSpots);
		if (spotTo == null) {
			System.out.println("spot not found or invalid: " + spotName);
			return;
		}
		transferFromSpot(exp, spotTo, spotFrom);
	}

	private void duplicateAll(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int columnIndex = spotTable.getSelectedColumn();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0)
			return;

		Object value = spotTable.spotTableModel.getValueAt(rowIndex, columnIndex);
		for (Cage cage : exp.getCages().cagesList) {
			List<Spot> cageSpots = cage.getSpotList(allSpots);
			for (Spot spot : cageSpots) {
				int iID = exp.getCages().getSpotGlobalPosition(spot, allSpots);
				spotTable.spotTableModel.setValueAt(value, iID, columnIndex);
			}
		}
	}

	private void duplicateCage(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0)
			return;

		Spot spotFromSelectedRow = exp.getCages().getSpotAtGlobalIndex(rowIndex, allSpots);
		int cageIDFrom = spotFromSelectedRow.getProperties().getCageID();
		Cage cageFrom = exp.getCages().getCageFromSpotName(spotFromSelectedRow.getRoi().getName());

		List<Spot> cageFromSpots = cageFrom.getSpotList(allSpots);
		for (Cage cage : exp.getCages().cagesList) {
			if (cage.getProperties().getCageID() == cageIDFrom)
				continue;

			List<Spot> cageSpots = cage.getSpotList(allSpots);
			for (int i = 0; i < cageSpots.size(); i++) {
				Spot spot = cageSpots.get(i);
				if (i >= cageFromSpots.size())
					continue;
				Spot spotFrom = cageFromSpots.get(i);
				transferFromSpot(exp, spot, spotFrom);
			}
		}
	}

	public void selectRowFromSpot(Spot spot) {
		String spotName = spot.getRoi().getName();
		int nrows = spotTable.getRowCount();
		int selectedRow = -1;
		for (int i = 0; i < nrows; i++) {
			String name = (String) spotTable.getValueAt(i, 0);
			if (name.equals(spotName)) {
				selectedRow = i;
				break;
			}
		}
		if (selectedRow >= 0) {
			spotTable.setRowSelectionInterval(selectedRow, selectedRow);
			Rectangle rect = new Rectangle(spotTable.getCellRect(selectedRow, 0, true));
			rect.height = rect.height * 2;
			spotTable.scrollRectToVisible(rect);
		}
	}

	void selectSpot(Spot spot) {
		Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
		if (exp != null) {
			Spots allSpots = exp.getSpots();
			String name = spot.getName();
			ROI2D roiSpot = spot.getRoi();
			if (name == null)
				name = roiSpot.getName();
			Cage cage = exp.getCages().getCageFromSpotROIName(name, allSpots);
			if (cage != null) {
				ROI2D cageRoi = cage.getRoi();
				if (cageRoi != null)
					exp.getSeqCamData().centerDisplayOnRoi(cageRoi);
				else
					System.out.println("cage roi not found");
			} else
				System.out.println("cage is null");

			exp.getSeqCamData().getSequence().setFocusedROI(roiSpot);
			// exp.getSeqCamData().centerOnRoi(roi);
			roiSpot.setSelected(true);
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting())
			return;

		ListSelectionModel lsm = (ListSelectionModel) e.getSource();
		int minIndex = lsm.getMinSelectionIndex();
		selectSpot(spotTable.spotTableModel.getSpotAt(minIndex));
	}

}
