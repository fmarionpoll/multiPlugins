package plugins.fmp.multiSPOTS.dlg.define_spots;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.RefreshGlyphButtonFactory;

/**
 * Lists spots present in the global {@link Spots} list but not referenced by any cage
 * {@code spotIDs} (see {@link plugins.fmp.multitools.experiment.cages.Cages#getOrphanSpots}).
 */
public final class InfosOrphanSpotsDialog implements ListSelectionListener {
	private static final String TITLE_PREFIX = "Orphan spots (n=";

	private final JComboBoxExperimentLazy expListComboLazy;
	private IcyFrame frame;
	private JTable table;
	private OrphanSpotTableModel tableModel;
	private ItemListener experimentItemListener;
	private JButton refreshButton;
	private JButton locateButton;
	private final IcyFrameAdapter frameCloseListener = new IcyFrameAdapter() {
		@Override
		public void icyFrameClosed(IcyFrameEvent e) {
			onOrphanFrameClosed();
		}
	};

	public InfosOrphanSpotsDialog(JComboBoxExperimentLazy expListComboLazy) {
		this.expListComboLazy = expListComboLazy;
	}

	public void showOrBringToFront() {
		if (frame == null) {
			buildUi();
		}
		refreshFromExperiment();
		frame.setVisible(true);
		frame.toFront();
		frame.requestFocus();
	}

	public void dispose() {
		if (frame != null) {
			frame.removeFrameListener(frameCloseListener);
			frame.close();
		}
		onOrphanFrameClosed();
	}

	private void onOrphanFrameClosed() {
		if (expListComboLazy != null && experimentItemListener != null) {
			expListComboLazy.removeItemListener(experimentItemListener);
			experimentItemListener = null;
		}
		if (table != null) {
			table.getSelectionModel().removeListSelectionListener(this);
			table = null;
		}
		tableModel = null;
		refreshButton = null;
		locateButton = null;
		frame = null;
	}

	private void buildUi() {
		tableModel = new OrphanSpotTableModel(expListComboLazy);
		table = new JTable(tableModel);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setPreferredScrollableViewportSize(new Dimension(560, 220));
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(this);

		refreshButton = RefreshGlyphButtonFactory.createTableRefreshButton("Refresh orphan list");
		refreshButton.addActionListener(e -> refreshFromExperiment());

		locateButton = new JButton("Locate selected");
		locateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				locateSelectedOrphan();
			}
		});

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(refreshButton);
		top.add(locateButton);

		frame = new IcyFrame(formatTitle(0), true, true);
		frame.addFrameListener(frameCloseListener);
		frame.add(top, BorderLayout.NORTH);
		frame.add(new JScrollPane(table), BorderLayout.CENTER);
		frame.pack();
		frame.addToDesktopPane();
		frame.setLocation(new Point(24, 420));

		experimentItemListener = e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				refreshFromExperiment();
			}
		};
		expListComboLazy.addItemListener(experimentItemListener);
	}

	private static String formatTitle(int n) {
		return TITLE_PREFIX + n + ")";
	}

	private void refreshFromExperiment() {
		if (tableModel != null) {
			tableModel.refresh();
		}
		if (frame != null) {
			frame.setTitle(formatTitle(tableModel != null ? tableModel.getRowCount() : 0));
		}
	}

	private void locateSelectedOrphan() {
		Spot spot = getSelectedSpot();
		if (spot == null) {
			return;
		}
		focusOrphanSpotOnSequence(spot);
	}

	private Spot getSelectedSpot() {
		if (table == null || tableModel == null) {
			return null;
		}
		int row = table.getSelectedRow();
		if (row < 0) {
			return null;
		}
		return tableModel.getSpotAt(row);
	}

	private void focusOrphanSpotOnSequence(Spot spot) {
		Experiment exp = (Experiment) expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			return;
		}
		ROI2D roiSpot = spot.getRoi();
		if (roiSpot == null) {
			return;
		}
		Spots allSpots = exp.getSpots();
		String roiName = roiSpot.getName();
		Cage cage = null;
		if (roiName != null && !roiName.isEmpty()) {
			cage = exp.getCages().getCageFromSpotROIName(roiName, allSpots);
		}
		if (cage == null) {
			cage = exp.getCages().getCageFromID(spot.getProperties().getCageID());
		}
		if (cage != null) {
			ROI2D cageRoi = cage.getRoi();
			if (cageRoi != null) {
				exp.getSeqCamData().centerDisplayOnRoi(cageRoi);
			}
		} else {
			exp.getSeqCamData().centerDisplayOnRoi(roiSpot);
		}
		exp.getSeqCamData().getSequence().setFocusedROI(roiSpot);
		roiSpot.setSelected(true);
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
		int row = lsm.getMinSelectionIndex();
		if (row < 0 || tableModel == null) {
			return;
		}
		Spot spot = tableModel.getSpotAt(row);
		if (spot != null) {
			focusOrphanSpotOnSequence(spot);
		}
	}

	private static final class OrphanSpotTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final JComboBoxExperimentLazy expList;
		private final ArrayList<Spot> rows = new ArrayList<>();
		private final String[] columnNames = { "Spot ID", "ROI name", "Cage ID", "Center X", "Center Y" };

		OrphanSpotTableModel(JComboBoxExperimentLazy expList) {
			this.expList = expList;
		}

		void refresh() {
			rows.clear();
			if (expList != null && expList.getSelectedIndex() >= 0) {
				Experiment exp = (Experiment) expList.getSelectedItem();
				if (exp != null) {
					rows.addAll(exp.getCages().getOrphanSpots(exp.getSpots()));
				}
			}
			fireTableDataChanged();
		}

		Spot getSpotAt(int rowIndex) {
			if (rowIndex < 0 || rowIndex >= rows.size()) {
				return null;
			}
			return rows.get(rowIndex);
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(int column) {
			return columnNames[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0 || columnIndex == 2 || columnIndex == 3 || columnIndex == 4) {
				return Integer.class;
			}
			return String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Spot s = getSpotAt(rowIndex);
			if (s == null) {
				return null;
			}
			switch (columnIndex) {
			case 0:
				return s.getSpotUniqueID() != null ? Integer.valueOf(s.getSpotUniqueID().getId()) : Integer.valueOf(-1);
			case 1: {
				if (s.getRoi() != null && s.getRoi().getName() != null) {
					return s.getRoi().getName();
				}
				String n = s.getName();
				return n != null ? n : "";
			}
			case 2:
				return Integer.valueOf(s.getProperties().getCageID());
			case 3: {
				if (s.getRoi() != null && s.getRoi().getBounds() != null) {
					return Integer.valueOf((int) Math.round(s.getRoi().getBounds().getCenterX()));
				}
				return Integer.valueOf(s.getProperties().getSpotXCoord());
			}
			case 4: {
				if (s.getRoi() != null && s.getRoi().getBounds() != null) {
					return Integer.valueOf((int) Math.round(s.getRoi().getBounds().getCenterY()));
				}
				return Integer.valueOf(s.getProperties().getSpotYCoord());
			}
			default:
				return null;
			}
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}
	}
}
