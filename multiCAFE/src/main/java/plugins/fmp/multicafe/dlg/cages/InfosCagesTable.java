package plugins.fmp.multicafe.dlg.cages;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import icy.gui.frame.IcyFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.cages.cage.CageProperties;
import plugins.fmp.multitools.tools.JComponents.CageTableModel;

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
	private JButton duplicateAllButton = new JButton("Duplicate cage to all");
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
		JScrollPane scrollPane = new JScrollPane(tableView);

		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(copyButton);
		panel1.add(pasteButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(duplicateAllButton);
		panel2.add(noFliesButton);
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
					if (rowIndex >= 0) {
						Cage cage0 = exp.getCages().getCageList().get(rowIndex);
						CageProperties prop0 = cage0.prop;
						for (Cage cage : exp.getCages().getCageList()) {
							CageProperties prop = cage.getProperties();
							if (cage.getCageRoi2D().getName().equals(cage0.getCageRoi2D().getName()))
								continue;
							switch (columnIndex) {
							case 1:
								cage.setCageNFlies(cage0.getCageNFlies());
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
							default:
								break;
							}
						}
					}
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
