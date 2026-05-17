package plugins.fmp.multitools.experiment.spots;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.TableCellColorEditor;
import plugins.fmp.multitools.tools.JComponents.TableCellColorRenderer;

public class SpotTable extends JTable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public SpotTableModel spotTableModel = null;
	int lastSelectedRow = -1;

	Color cellsOrigBackColor;
	Color cellsOrigForeColor;

	public SpotTable(JComboBoxExperimentLazy expListComboLazy) {
		cellsOrigBackColor = this.getBackground();
		cellsOrigForeColor = this.getForeground();
		spotTableModel = new SpotTableModel(expListComboLazy);
		setModel(spotTableModel);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		setPreferredScrollableViewportSize(new Dimension(500, 400));
		setFillsViewportHeight(true);

		setDefaultRenderer(Color.class, new TableCellColorRenderer(true));
		setDefaultEditor(Color.class, new TableCellColorEditor());

		TableColumnModel columnModel = getColumnModel();

		columnModel.getColumn(0).setPreferredWidth(75);
		columnModel.getColumn(1).setPreferredWidth(6);
		columnModel.getColumn(2).setPreferredWidth(6);
		columnModel.getColumn(3).setPreferredWidth(6);
		columnModel.getColumn(4).setPreferredWidth(6);
		columnModel.getColumn(5).setPreferredWidth(6);
		columnModel.getColumn(6).setPreferredWidth(6);
		columnModel.getColumn(7).setPreferredWidth(25);
		columnModel.getColumn(8).setPreferredWidth(25);
		columnModel.getColumn(9).setPreferredWidth(8);

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ListSelectionModel selectionModel = getSelectionModel();
		selectionModel.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				handleSelectionEvent(e);
			}
		});
	}

	protected void handleSelectionEvent(ListSelectionEvent e) {
		if (e.getValueIsAdjusting()) {
			return;
		}
		ListSelectionModel lsm = (ListSelectionModel) e.getSource();
		if (lastSelectedRow >= 0) {
			Spot prev = spotTableModel.getSpotAt(lastSelectedRow);
			if (prev != null && prev.getRoi() != null) {
				prev.getRoi().setSelected(false);
			}
		}
		if (lsm.isSelectionEmpty()) {
			lastSelectedRow = -1;
			return;
		}
		int newIndex = lsm.getMinSelectionIndex();
		if (newIndex < 0) {
			lastSelectedRow = -1;
			return;
		}
		Spot spot = spotTableModel.getSpotAt(newIndex);
		if (spot != null && spot.getRoi() != null) {
			spot.getRoi().setSelected(true);
		}
		lastSelectedRow = newIndex;
	}

}
