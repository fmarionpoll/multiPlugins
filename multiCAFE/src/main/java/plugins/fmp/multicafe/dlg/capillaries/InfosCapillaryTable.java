package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCagePositionSwap;
import plugins.fmp.multitools.tools.JComponents.CapillaryTableColumn;
import plugins.fmp.multitools.tools.JComponents.CapillaryTableModel;

public class InfosCapillaryTable extends JPanel {
	private static final long serialVersionUID = -8611587540329642259L;
	IcyFrame dialogFrame = null;
	private JTable tableView = new JTable();
	private CapillaryTableModel capillaryTableModel = null;
	private JButton copyButton = new JButton("Copy table");
	private JButton pasteButton = new JButton("Paste");
	private JButton duplicateLRButton = new JButton("Duplicate to L/R");
	private JButton duplicateCageButton = new JButton("Duplicate cage");

	private JButton exchangeLRButton = new JButton("Exchg L/R");

	private JButton duplicateAllButton = new JButton("Duplicate to all");
	private JButton getNfliesButton = new JButton("Get n flies from cage");
	private JButton getCageNoButton = new JButton("Set cage n#");
	private JButton noFliesButton = new JButton("Cages 0/9: no flies");
	private MultiCAFE parent0 = null;
	private List<Capillary> capillariesArrayCopy = null;

	public void initialize(MultiCAFE parent0, List<Capillary> capCopy) {
		this.parent0 = parent0;
		capillariesArrayCopy = capCopy;

		capillaryTableModel = new CapillaryTableModel(parent0.expListComboLazy);
		tableView.setModel(capillaryTableModel);
		tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tableView.setPreferredScrollableViewportSize(new Dimension(500, 400));
		tableView.setFillsViewportHeight(true);
		TableColumnModel columnModel = tableView.getColumnModel();
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		for (int i = 0; i < capillaryTableModel.getColumnCount(); i++) {
			TableColumn col = columnModel.getColumn(i);
			if (i <= CapillaryTableColumn.VOLUME.ordinal())
				setFixedColumnProperties(col);
			col.setCellRenderer(centerRenderer);
		}
		installSideComboEditor(columnModel);
		JScrollPane scrollPane = new JScrollPane(tableView);

		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(copyButton);
		panel1.add(pasteButton);
		panel1.add(duplicateLRButton);
		panel1.add(duplicateAllButton);
		panel1.add(exchangeLRButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(getCageNoButton);
		panel2.add(getNfliesButton);
		panel2.add(noFliesButton);
		panel2.add(duplicateCageButton);
		topPanel.add(panel2);

		JPanel tablePanel = new JPanel();
		tablePanel.add(scrollPane);

		dialogFrame = new IcyFrame("Capillaries properties", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(tablePanel, BorderLayout.CENTER);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);
		defineActionListeners();
		pasteButton.setEnabled(capillariesArrayCopy.size() > 0);
	}

	private void installSideComboEditor(TableColumnModel columnModel) {
		TableColumn sideCol = columnModel.getColumn(CapillaryTableColumn.POSITION.ordinal());
		JComboBox<String> combo = new JComboBox<>();
		combo.setEditable(false);
		sideCol.setCellEditor(new DefaultCellEditor(combo) {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
					int column) {
				@SuppressWarnings("unchecked")
				JComboBox<String> cb = (JComboBox<String>) getComponent();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null || row < 0 || row >= exp.getCapillaries().getList().size()) {
					cb.setModel(new DefaultComboBoxModel<>());
					cb.setEditable(false);
					return super.getTableCellEditorComponent(table, value, isSelected, row, column);
				}
				Capillary cap = exp.getCapillaries().getList().get(row);
				String[] opts = CapillaryCagePositionSwap.comboOptionsForCage(exp.getCapillaries().getList(),
						cap.getCageID());
				cb.setModel(new DefaultComboBoxModel<>(opts));
				cb.setEditable(false);
				return super.getTableCellEditorComponent(table, value, isSelected, row, column);
			}
		});
	}

	private void defineActionListeners() {
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					copyInfos(exp);
			}
		});

		pasteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					pasteInfos(exp);
				capillaryTableModel.fireTableDataChanged();
			}
		});

		noFliesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					setFliesNumbers(exp);
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateLRButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					duplicateLR(exp);
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateCageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					duplicateCage(exp);
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});

		exchangeLRButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null || exp.getCapillaries().getCapillariesDescription().getGrouping() != 2)
					return;
				exchangeLR(exp);
				capillaryTableModel.fireTableDataChanged();
			}
		});

		duplicateAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					duplicateAll(exp);
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});

		getNfliesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && exp.getCages().getCageList().size() > 0) {
					exp.getCages().transferNFliesFromCagesToCapillaries(exp.getCapillaries().getList());
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});

		getCageNoButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.getCages().setCageNbFromName(exp.getCapillaries().getList());
					capillaryTableModel.fireTableDataChanged();
				}
			}
		});
	}

	void close() {
		dialogFrame.close();
	}

	private void setFixedColumnProperties(TableColumn column) {
		column.setResizable(false);
		column.setPreferredWidth(50);
		column.setMaxWidth(50);
		column.setMinWidth(30);
	}

	private void exchangeLR(Experiment exp) {
		int columnIndex = tableView.getSelectedColumn();
		if (columnIndex < 0)
			columnIndex = CapillaryTableColumn.CONCENTRATION.ordinal();
		if (columnIndex < 0 || columnIndex >= capillaryTableModel.getColumnCount())
			return;
		String side0 = exp.getCapillaries().getList().get(0).getCapillarySide();
		Capillary cap0 = new Capillary();
		storeCapillaryValues(exp.getCapillaries().getList().get(0), cap0);
		Capillary cap1 = new Capillary();
		storeCapillaryValues(exp.getCapillaries().getList().get(1), cap1);

		for (Capillary cap : exp.getCapillaries().getList()) {
			if ((cap.getCapillarySide().equals(side0)))
				switchCapillaryValue(cap1, cap, columnIndex);
			else
				switchCapillaryValue(cap0, cap, columnIndex);
		}
		if (CapillaryTableColumn.fromIndex(columnIndex) == CapillaryTableColumn.POSITION) {
			exp.refreshAfterCapillaryRoiIdentityChange(
					exp.getCapillaries().getList().toArray(new Capillary[0]));
		}
	}

	private void storeCapillaryValues(Capillary sourceCapillary, Capillary destinationCapillary) {
		destinationCapillary.getProperties().setNFlies(sourceCapillary.getProperties().getNFlies());
		destinationCapillary.getProperties().setVolume(sourceCapillary.getProperties().getVolume());
		destinationCapillary.setStimulus(sourceCapillary.getStimulus());
		destinationCapillary.getProperties().setConcentration(sourceCapillary.getProperties().getConcentration());
		destinationCapillary.setSide(sourceCapillary.getSide());
	}

	private void switchCapillaryValue(Capillary sourceCapillary, Capillary destinationCapillary, int columnIndex) {
		switch (CapillaryTableColumn.fromIndex(columnIndex)) {
		case N_FLIES:
			destinationCapillary.setNFlies(sourceCapillary.getNFlies());
			break;
		case VOLUME:
			destinationCapillary.setVolume(sourceCapillary.getVolume());
			break;
		case STIMULUS:
			destinationCapillary.setStimulus(sourceCapillary.getStimulus());
			break;
		case CONCENTRATION:
			destinationCapillary.setConcentration(sourceCapillary.getConcentration());
			break;
		case POSITION:
			if (sourceCapillary.getRoi() != null)
				CapillaryCagePositionSwap.copyPositionOnto(destinationCapillary, sourceCapillary);
			else
				destinationCapillary.setSide(sourceCapillary.getSide());
			break;
		default:
			break;
		}
	}

	private void copyInfos(Experiment exp) {
		capillariesArrayCopy.clear();
		for (Capillary cap : exp.getCapillaries().getList())
			capillariesArrayCopy.add(cap);
		pasteButton.setEnabled(true);
	}

	private void pasteInfos(Experiment exp) {
		for (Capillary capFrom : capillariesArrayCopy) {
			capFrom.getProperties().setValid(false);
			for (Capillary capTo : exp.getCapillaries().getList()) {
				if (!capFrom.getRoiName().equals(capTo.getRoiName()))
					continue;
				capFrom.getProperties().setValid(true);
				capTo.setCageID(capFrom.getCageID());
				capTo.setNFlies(capFrom.getNFlies());
				capTo.setVolume(capFrom.getVolume());
				capTo.setStimulus(capFrom.getStimulus());
				capTo.setConcentration(capFrom.getConcentration());
				CapillaryCagePositionSwap.copyPositionOnto(capTo, capFrom);
				exp.refreshAfterCapillaryRoiIdentityChange(capTo);
			}
		}
	}

	private void setFliesNumbers(Experiment exp) {
		int ncapillaries = exp.getCapillaries().getList().size();
		for (int i = 0; i < ncapillaries; i++) {
			Capillary cap = exp.getCapillaries().getList().get(i);
			if (i < 2 || i >= ncapillaries - 2) {
				cap.getProperties().setNFlies(0);
			} else {
				cap.getProperties().setNFlies(1);
			}
		}
	}

	private void duplicateLR(Experiment exp) {
		int rowIndex = tableView.getSelectedRow();
		int columnIndex = tableView.getSelectedColumn();
		if (rowIndex < 0)
			return;
		if (columnIndex < 0 || columnIndex >= capillaryTableModel.getColumnCount())
			return;

		Capillary cap0 = exp.getCapillaries().getList().get(rowIndex);
		String side = cap0.getCapillarySide();
		int modulo2 = 0;
		if (side.equals("L"))
			modulo2 = 0;
		else if (side.equals("R"))
			modulo2 = 1;
		else
			modulo2 = Integer.valueOf(cap0.getCapillarySide()) % 2;

		CapillaryTableColumn col = CapillaryTableColumn.fromIndex(columnIndex);
		if (col == CapillaryTableColumn.POSITION)
			return;

		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap.getKymographName().equals(cap0.getKymographName()))
				continue;
			if ((exp.getCapillaries().getCapillariesDescription().getGrouping() == 2)
					&& (!cap.getCapillarySide().equals(side)))
				continue;
			else {
				try {
					int mod = Integer.valueOf(cap.getCapillarySide()) % 2;
					if (mod != modulo2)
						continue;
				} catch (NumberFormatException nfe) {
					if (!cap.getCapillarySide().equals(side))
						continue;
				}
			}
			switch (col) {
			case N_FLIES:
				cap.setNFlies(cap0.getNFlies());
				break;
			case VOLUME:
				cap.setVolume(cap0.getVolume());
				break;
			case STIMULUS:
				cap.setStimulus(cap0.getStimulus());
				break;
			case CONCENTRATION:
				cap.setConcentration(cap0.getConcentration());
				break;
			default:
				break;
			}
		}
	}

	private void duplicateAll(Experiment exp) {
		int rowIndex = tableView.getSelectedRow();
		int columnIndex = tableView.getSelectedColumn();
		if (rowIndex < 0)
			return;
		if (columnIndex < 0 || columnIndex >= capillaryTableModel.getColumnCount())
			return;

		Capillary cap0 = exp.getCapillaries().getList().get(rowIndex);
		CapillaryTableColumn col = CapillaryTableColumn.fromIndex(columnIndex);
		if (col == CapillaryTableColumn.POSITION)
			return;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap.getKymographName().equals(cap0.getKymographName()))
				continue;
			switch (col) {
			case N_FLIES:
				cap.setNFlies(cap0.getNFlies());
				break;
			case VOLUME:
				cap.setVolume(cap0.getVolume());
				break;
			case STIMULUS:
				cap.setStimulus(cap0.getStimulus());
				break;
			case CONCENTRATION:
				cap.setConcentration(cap0.getConcentration());
				break;
			default:
				break;
			}
		}
	}

	private void duplicateCage(Experiment exp) {
		int rowIndex = tableView.getSelectedRow();
		int columnIndex = tableView.getSelectedColumn();
		if (rowIndex < 0)
			return;
		if (columnIndex < 0 || columnIndex >= capillaryTableModel.getColumnCount())
			return;

		Capillary capFrom = exp.getCapillaries().getList().get(rowIndex);
		int cageFrom = capFrom.getCageID();
		int cageTo = -1;

		int nCapillariesPerCage = getCageNCapillaries(exp, cageFrom);
		int indexFirstCapillaryOfCageFrom = getIndexFirstCapillaryOfCage(exp, cageFrom);
		int indexFirstCapillaryOfCageTo = -1;

		CapillaryTableColumn col = CapillaryTableColumn.fromIndex(columnIndex);
		if (col == CapillaryTableColumn.POSITION)
			return;

		for (int i = 0; i < exp.getCapillaries().getList().size(); i++) {
			Capillary cap = exp.getCapillaries().getList().get(i);
			if (cap.getCageID() == cageFrom)
				continue;

			if (cap.getCageID() != cageTo) {
				cageTo = cap.getCageID();
				indexFirstCapillaryOfCageTo = getIndexFirstCapillaryOfCage(exp, cageTo);
			}

			if (getCageNCapillaries(exp, cap.getCageID()) != nCapillariesPerCage)
				continue;

			int indexFrom = i - indexFirstCapillaryOfCageTo + indexFirstCapillaryOfCageFrom;
			Capillary cap0 = exp.getCapillaries().getList().get(indexFrom);

			switch (col) {
			case N_FLIES:
				cap.setNFlies(cap0.getNFlies());
				break;
			case VOLUME:
				cap.setVolume(cap0.getVolume());
				break;
			case STIMULUS:
				cap.setStimulus(cap0.getStimulus());
				break;
			case CONCENTRATION:
				cap.setConcentration(cap0.getConcentration());
				break;
			default:
				break;
			}
		}
	}

	private int getCageNCapillaries(Experiment exp, int cageID) {
		int nCapillaries = 0;
		for (Capillary cap : exp.getCapillaries().getList()) {
			if (cap.getCageID() == cageID)
				nCapillaries++;
		}

		return nCapillaries;
	}

	private int getIndexFirstCapillaryOfCage(Experiment exp, int cageID) {
		int index = -1;
		for (int i = 0; i < exp.getCapillaries().getList().size(); i++) {
			Capillary cap = exp.getCapillaries().getList().get(i);
			if (cap.getCageID() == cageID) {
				index = i;
				break;
			}
		}
		return index;
	}
}
