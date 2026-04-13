package plugins.fmp.multicafe.dlg.cages;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageProperties;
import plugins.fmp.multitools.experiment.cage.FoodSide;
import plugins.fmp.multitools.experiment.cages.CageTableModel;

public class InfosCagesTable extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7599620793495187279L;
	IcyFrame dialogFrame = null;
	private JTable tableView = new JTable();
	private CageTableModel cageTableModel = null;
	private JButton copyButton = new JButton("Copy table");
	private JButton pasteButton = new JButton("Paste");
	private JButton duplicateAllButton = new JButton("Duplicate to all");
	private JButton duplicatePreviousButton = new JButton("Duplicate previous");
	private JButton duplicateNextButton = new JButton("Duplicate next");
	private JButton duplicateForwardButton = new JButton("Duplicate forward");
	private JButton duplicateBackwardButton = new JButton("Duplicate backward");
	private JButton noFliesButton = new JButton("Cage 0/9: no flies");
	private MultiCAFE parent0 = null;
	private List<Cage> cageArrayCopy = null;

	// -------------------------

	public void initialize(MultiCAFE parent0, List<Cage> cageCopy) {
		this.parent0 = parent0;
		cageArrayCopy = cageCopy;

		cageTableModel = new CageTableModel(parent0.expListComboLazy);
		tableView.setModel(cageTableModel);
		tableView.setPreferredScrollableViewportSize(new Dimension(500, 400));
		tableView.setFillsViewportHeight(true);
		TableColumnModel columnModel = tableView.getColumnModel();
		for (int i = 0; i < 2; i++)
			setFixedColumnProperties(columnModel.getColumn(i));
		JComboBox<FoodSide> foodSideCombo = new JComboBox<>(FoodSide.values());
		columnModel.getColumn(6).setCellEditor(new DefaultCellEditor(foodSideCombo));
		JScrollPane scrollPane = new JScrollPane(tableView);

		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(copyButton);
		panel1.add(pasteButton);
		panel1.add(noFliesButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(duplicateAllButton);
		panel2.add(duplicatePreviousButton);
		panel2.add(duplicateNextButton);
		panel2.add(duplicateForwardButton);
		panel2.add(duplicateBackwardButton);
		topPanel.add(panel2);

		JPanel tablePanel = new JPanel();
		tablePanel.add(scrollPane);

		dialogFrame = new IcyFrame("Cell properties", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(tablePanel, BorderLayout.CENTER);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);
		defineActionListeners();
		pasteButton.setEnabled(cageArrayCopy.size() > 0);
	}

	private void defineActionListeners() {
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					cageArrayCopy.clear();
					for (Cage cage : exp.getCages().getCageList()) {
						cageArrayCopy.add(cage);
					}
					pasteButton.setEnabled(true);
				}
			}
		});

		pasteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					for (Cage cageFrom : cageArrayCopy) {
						for (Cage cageTo : exp.getCages().getCageList()) {
							if (!cageFrom.getCageRoi2D().getName().equals(cageTo.getCageRoi2D().getName()))
								continue;
							cageTo.setCageNFlies(cageFrom.getCageNFlies());
							cageTo.prop.setFlyAge(cageFrom.prop.getFlyAge());
							cageTo.prop.setComment(cageFrom.prop.getComment());
							cageTo.prop.setFlySex(cageFrom.prop.getFlySex());
							cageTo.prop.setFlyStrain(cageFrom.prop.getFlyStrain());
							cageTo.prop.setFoodSide(cageFrom.prop.getFoodSide());
						}
					}
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = tableView.getSelectedRow();
					int columnIndex = tableView.getSelectedColumn();
					if (rowIndex >= 0)
						duplicateColumnFromSourceToAllOtherCages(exp, rowIndex, columnIndex);
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicatePreviousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = tableView.getSelectedRow();
					int columnIndex = tableView.getSelectedColumn();
					List<Cage> list = exp.getCages().getCageList();
					if (rowIndex > 0)
						copyColumnFromSourceToTarget(list.get(rowIndex), list.get(rowIndex - 1), columnIndex);
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateNextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = tableView.getSelectedRow();
					int columnIndex = tableView.getSelectedColumn();
					List<Cage> list = exp.getCages().getCageList();
					if (rowIndex >= 0 && rowIndex < list.size() - 1)
						copyColumnFromSourceToTarget(list.get(rowIndex), list.get(rowIndex + 1), columnIndex);
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateForwardButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = tableView.getSelectedRow();
					int columnIndex = tableView.getSelectedColumn();
					if (rowIndex >= 0)
						duplicateColumnFromSourceToCageRange(exp, rowIndex, columnIndex, rowIndex + 1,
								exp.getCages().getCageList().size());
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateBackwardButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = tableView.getSelectedRow();
					int columnIndex = tableView.getSelectedColumn();
					if (rowIndex >= 0)
						duplicateColumnFromSourceToCageRange(exp, rowIndex, columnIndex, 0, rowIndex);
					cageTableModel.fireTableDataChanged();
				}
			}
		});

		noFliesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.getCages().setFirstAndLastCageToZeroFly();
					cageTableModel.fireTableDataChanged();
				}
			}
		});

	}

	private void copyColumnFromSourceToTarget(Cage target, Cage source, int columnIndex) {
		CageProperties prop = target.getProperties();
		CageProperties prop0 = source.prop;
		switch (columnIndex) {
		case 1:
			target.setCageNFlies(source.getCageNFlies());
			break;
		case 2:
			prop.setFlyStrain(prop0.getFlyStrain());
			break;
		case 3:
			prop.setFlySex(prop0.getFlySex());
			break;
		case 4:
			prop.setFlyAge(prop0.getFlyAge());
			break;
		case 5:
			prop.setComment(prop0.getComment());
			break;
		case 6:
			prop.setFoodSide(prop0.getFoodSide());
			break;
		default:
			break;
		}
	}

	private void duplicateColumnFromSourceToAllOtherCages(Experiment exp, int sourceRowIndex, int columnIndex) {
		List<Cage> list = exp.getCages().getCageList();
		Cage sourceCage = list.get(sourceRowIndex);
		for (Cage cage : list) {
			if (cage.getCageRoi2D().getName().equals(sourceCage.getCageRoi2D().getName()))
				continue;
			copyColumnFromSourceToTarget(cage, sourceCage, columnIndex);
		}
	}

	private void duplicateColumnFromSourceToCageRange(Experiment exp, int sourceRowIndex, int columnIndex,
			int fromIndexInclusive, int toIndexExclusive) {
		List<Cage> list = exp.getCages().getCageList();
		Cage sourceCage = list.get(sourceRowIndex);
		for (int i = fromIndexInclusive; i < toIndexExclusive; i++)
			copyColumnFromSourceToTarget(list.get(i), sourceCage, columnIndex);
	}

	void close() {
		dialogFrame.close();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.getCages().transferNFliesFromCagesToCapillaries(exp.getCapillaries().getList());
			parent0.paneCapillaries.tabFile.saveCapillaries_file(exp);
		}
	}

	private void setFixedColumnProperties(TableColumn column) {
		column.setResizable(false);
		column.setPreferredWidth(50);
		column.setMaxWidth(50);
		column.setMinWidth(30);
	}

}
