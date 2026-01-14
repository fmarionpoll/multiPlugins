package plugins.fmp.multiSPOTS96.dlg.b_spots;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import icy.gui.frame.IcyFrame;
import icy.roi.ROI;
import icy.roi.ROI2D;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.cages.CageTable;
import plugins.fmp.multiSPOTS96.experiment.cages.CagesArray;

public class InfosCageTable extends JPanel implements ListSelectionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7599620793495187279L;
	IcyFrame dialogFrame = null;
	private CageTable cageTable = null;
	private JButton copyButton = new JButton("Copy table");
	private JButton pasteButton = new JButton("Paste");
	private JButton selectedCageButton = new JButton("Locate selected cage");

	private JButton duplicateAllButton = new JButton("Cage to all");
	private MultiSPOTS96 parent0 = null;
	private CagesArray cagesArrayCopy = null;

	// -------------------------

	public void initialize(MultiSPOTS96 parent0) {
		this.parent0 = parent0;

		cageTable = new CageTable(parent0);
		cageTable.setPreferredScrollableViewportSize(new Dimension(500, 400));
		cageTable.setFillsViewportHeight(true);
		TableColumnModel columnModel = cageTable.getColumnModel();
		for (int i = 0; i < 2; i++)
			setFixedColumnProperties(columnModel.getColumn(i));
		JScrollPane scrollPane = new JScrollPane(cageTable);
		cageTable.getSelectionModel().addListSelectionListener(this);

		JPanel topPanel = new JPanel(new GridLayout(2, 1));
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(copyButton);
		panel1.add(pasteButton);
		panel1.add(selectedCageButton);
		topPanel.add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Duplicate:"));
		panel2.add(duplicateAllButton);
		topPanel.add(panel2);

		JPanel tablePanel = new JPanel();
		tablePanel.add(scrollPane);

		dialogFrame = new IcyFrame("Cages properties", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.add(tablePanel, BorderLayout.CENTER);

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);
		defineActionListeners();

		pasteButton.setEnabled(cagesArrayCopy != null);
	}

	private void defineActionListeners() {
		copyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					cagesArrayCopy = exp.cagesArray;
					pasteButton.setEnabled(true);
				}
			}
		});

		pasteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					for (Cage cageFrom : cagesArrayCopy.cagesList) {
						cageFrom.valid = false;
						for (Cage cageTo : exp.cagesArray.cagesList) {
							if (!cageFrom.getRoi().getName().equals(cageTo.getRoi().getName()))
								continue;
							cageFrom.valid = true;
							cageTo.getProperties().copy(cageFrom.prop);
						}
					}
					cageTable.cageTableModel.fireTableDataChanged();
				}
			}
		});

		duplicateAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					int rowIndex = cageTable.getSelectedRow();
					int columnIndex = cageTable.getSelectedColumn();
					if (rowIndex >= 0) {
						Object value = cageTable.cageTableModel.getValueAt(rowIndex, columnIndex);
						for (Cage cage : exp.cagesArray.cagesList) {
							int iID = cage.getProperties().getCageID();
							cageTable.cageTableModel.setValueAt(value, iID, columnIndex);
						}
					}
				}
			}
		});

		selectedCageButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					ArrayList<ROI> roiList = exp.seqCamData.getSequence().getSelectedROIs();
					if (roiList.size() > 0) {
						Cage cage = null;
						for (ROI roi : roiList) {
							String name = roi.getName();
							if (name.contains("cage")) {
								cage = exp.cagesArray.getCageFromName(name);
								break;
							}
							if (name.contains("spot")) {
								cage = exp.cagesArray.getCageFromSpotROIName(name);
								break;
							}
						}

						if (cage != null) {
							selectRowFromCage(cage);
						}
					}
				}
			}
		});

		cageTable.cageTableModel.fireTableDataChanged();
	}

	public void close() {
		dialogFrame.close();
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.saveSpotsArray_file();
		}
	}

	private void setFixedColumnProperties(TableColumn column) {
		column.setResizable(false);
		column.setPreferredWidth(50);
		column.setMaxWidth(50);
		column.setMinWidth(30);
	}

	void selectCage(int cageID) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			Cage cage = exp.cagesArray.getCageFromID(cageID);
			ROI2D roi = cage.getRoi();
			exp.seqCamData.getSequence().setFocusedROI(roi);
			exp.seqCamData.centerDisplayOnRoi(roi);
			roi.setSelected(true);
		}
	}

	public void selectRowFromCage(Cage cage) {
		String cageName = cage.getRoi().getName();
		int nrows = cageTable.getRowCount();
		int selectedRow = -1;
		for (int i = 0; i < nrows; i++) {
			String name = (String) cageTable.getValueAt(i, 0);
			if (name.equals(cageName)) {
				selectedRow = i;
				break;
			}
		}
		if (selectedRow >= 0) {
			cageTable.setRowSelectionInterval(selectedRow, selectedRow);
			Rectangle rect = new Rectangle(cageTable.getCellRect(selectedRow, 0, true));
			rect.height = rect.height * 2;
			cageTable.scrollRectToVisible(rect);
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting())
			return;

		ListSelectionModel lsm = (ListSelectionModel) e.getSource();
		int minIndex = lsm.getMinSelectionIndex();
		selectCage(minIndex);
	}

}
