package plugins.fmp.multiSPOTS96.dlg.spots;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;

import icy.gui.frame.IcyFrame;
import icy.roi.ROI;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotProperties;
import plugins.fmp.multitools.experiment.spots.SpotTable;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.RefreshGlyphButtonFactory;

public class InfosSpotTable extends JPanel implements ListSelectionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8611587540329642259L;
	private static final String TITLE_PREFIX = "Spots properties (n=";

	IcyFrame dialogFrame = null;
	private ItemListener experimentItemListener = null;
	private TableModelListener spotCountTableListener = null;
	private SpotTable spotTable = null;
	private JButton copyButton = new JButton("Copy table");
	private JButton pasteButton = new JButton("Paste");
	private JButton updateButton = RefreshGlyphButtonFactory
			.createTableRefreshButton("Update table from current experiment");
	private JButton getNPixelsButton = new JButton("Get n pixels");
	private JButton selectedSpotButton = new JButton("Locate selected spot");

	private JButton duplicateRowAtCagePositionButton = new JButton("Row at cage pos");
	private JButton duplicatePreviousButton = new JButton("Row above");
	private JButton duplicateNextButton = new JButton("Row below");
	private JButton duplicateCagetoAllButton = new JButton("Cage to all");
	private JButton duplicateCellToAllButton = new JButton("Cell to all");

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
		panel1.add(updateButton);
		panel1.add(selectedSpotButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Duplicate:"));
		panel2.add(duplicateRowAtCagePositionButton);
		panel2.add(duplicateCellToAllButton);
		panel2.add(duplicateCagetoAllButton);
		panel2.add(duplicatePreviousButton);
		panel2.add(duplicateNextButton);
		topPanel.add(panel2);

		JPanel tablePanel = new JPanel();
		spotTable = new SpotTable(expListComboLazy);
		tablePanel.add(new JScrollPane(spotTable));
		spotTable.getSelectionModel().addListSelectionListener(this);
		spotCountTableListener = e -> updateFrameTitle();
		spotTable.spotTableModel.addTableModelListener(spotCountTableListener);
		experimentItemListener = e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				refreshTable();
			}
		};
		expListComboLazy.addItemListener(experimentItemListener);

		dialogFrame = new IcyFrame(formatFrameTitle(getDisplayedSpotCount()), true, true);
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

	SpotTable getSpotTable() {
		return spotTable;
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

		updateButton.addActionListener(e -> refreshTable());

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

		duplicateCagetoAllButton.addActionListener(new ActionListener() {
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

		duplicateCellToAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
				if (exp != null) {
					duplicateCellToAll(exp);
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

		refreshTable();
	}

	private void refreshTable() {
		if (spotTable != null && spotTable.spotTableModel != null) {
			spotTable.clearSelection();
			spotTable.spotTableModel.fireTableDataChanged();
		}
		updateFrameTitle();
	}

	private static String formatFrameTitle(int spotCount) {
		return TITLE_PREFIX + spotCount + ")";
	}

	private int getDisplayedSpotCount() {
		if (spotTable == null || spotTable.spotTableModel == null) {
			return 0;
		}
		return spotTable.spotTableModel.getRowCount();
	}

	private void updateFrameTitle() {
		if (dialogFrame != null) {
			dialogFrame.setTitle(formatFrameTitle(getDisplayedSpotCount()));
		}
	}

	void close() {
		if (expListComboLazy != null && experimentItemListener != null) {
			expListComboLazy.removeItemListener(experimentItemListener);
			experimentItemListener = null;
		}
		if (spotTable != null && spotTable.spotTableModel != null && spotCountTableListener != null) {
			spotTable.spotTableModel.removeTableModelListener(spotCountTableListener);
			spotCountTableListener = null;
		}
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
		for (Spot spot : allSpots.getSpotList()) {
			try {
				int value = (int) spot.getRoi().getNumberOfPoints();
				spotTable.spotTableModel.setValueAt(value, spot, columnIndex);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		spotTable.spotTableModel.fireTableDataChanged();
	}

	private void transferFromSpot(Spot spotTo, Spot spotFrom) {
		int columnIndex = 2;
		SpotProperties prop = spotFrom.getProperties();
		spotTable.spotTableModel.setValueAt(prop.getSpotVolume(), spotTo, columnIndex);
		columnIndex = 5;
		spotTable.spotTableModel.setValueAt(prop.getCageRow(), spotTo, columnIndex);
		columnIndex = 7;
		spotTable.spotTableModel.setValueAt(prop.getStimulus(), spotTo, columnIndex);
		columnIndex = 8;
		spotTable.spotTableModel.setValueAt(prop.getConcentration(), spotTo, columnIndex);
		columnIndex = 9;
		spotTable.spotTableModel.setValueAt(prop.getColor(), spotTo, columnIndex);
	}

	private void duplicatePos(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0)
			return;

		Spot spotFrom = spotTable.spotTableModel.getSpotAt(rowIndex);
		if (spotFrom == null) {
			System.out.println("spot not found at row: " + rowIndex);
			return;
		}

		SpotProperties prop = spotFrom.getProperties();

		int cagePosition = prop.getCagePosition();
		int cageID = prop.getCageID();

		for (Spot spot : allSpots.getSpotList()) {
			SpotProperties spotProp = spot.getProperties();
			if (spotProp.getCageID() == cageID)
				continue;
			if (spotProp.getCagePosition() != cagePosition)
				continue;
			transferFromSpot(spot, spotFrom);
		}
		spotTable.spotTableModel.fireTableDataChanged();
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
		transferFromSpot(spotTo, spotFrom);
		spotTable.spotTableModel.fireTableDataChanged();
	}

	private void duplicateCellToAll(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int columnIndex = spotTable.getSelectedColumn();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0 || columnIndex < 0)
			return;

		Object value = spotTable.spotTableModel.getValueAt(rowIndex, columnIndex);
		for (Spot spot : allSpots.getSpotList()) {
			spotTable.spotTableModel.setValueAt(value, spot, columnIndex);
		}
		spotTable.spotTableModel.fireTableDataChanged();
	}

	private void duplicateCage(Experiment exp) {
		Spots allSpots = exp.getSpots();
		int rowIndex = spotTable.getSelectedRow();
		if (rowIndex < 0)
			return;

		Spot spotFromSelectedRow = spotTable.spotTableModel.getSpotAt(rowIndex);
		if (spotFromSelectedRow == null)
			return;

		int cageIDFrom = spotFromSelectedRow.getProperties().getCageID();
		List<Spot> cageFromSpots = getSpotsForCage(allSpots, cageIDFrom);

		for (Spot spot : allSpots.getSpotList()) {
			if (spot.getProperties().getCageID() == cageIDFrom)
				continue;

			Spot spotFrom = getSpotAtCagePosition(cageFromSpots, spot.getProperties().getCagePosition());
			if (spotFrom == null)
				continue;
			transferFromSpot(spot, spotFrom);
		}
		spotTable.spotTableModel.fireTableDataChanged();
	}

	private List<Spot> getSpotsForCage(Spots allSpots, int cageID) {
		List<Spot> spots = new ArrayList<Spot>();
		for (Spot spot : allSpots.getSpotList()) {
			if (spot.getProperties().getCageID() == cageID) {
				spots.add(spot);
			}
		}
		return spots;
	}

	private Spot getSpotAtCagePosition(List<Spot> spots, int cagePosition) {
		for (Spot spot : spots) {
			if (spot.getProperties().getCagePosition() == cagePosition) {
				return spot;
			}
		}
		return null;
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
		if (e.getValueIsAdjusting()) {
			return;
		}
		ListSelectionModel lsm = (ListSelectionModel) e.getSource();
		if (lsm.isSelectionEmpty()) {
			return;
		}
		int minIndex = lsm.getMinSelectionIndex();
		if (minIndex < 0) {
			return;
		}
		Spot spot = spotTable.spotTableModel.getSpotAt(minIndex);
		if (spot != null) {
			selectSpot(spot);
		}
	}

}
