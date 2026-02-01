package plugins.fmp.multitools.experiment.cages;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumnModel;

import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.TableCellColorEditor;
import plugins.fmp.multitools.tools.JComponents.TableCellColorRenderer;

public class CageTable extends JTable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public CageTableModelForSpots cageTableModel = null;

	Color cellsOrigBackColor;
	Color cellsOrigForeColor;

	public CageTable(JComboBoxExperimentLazy expListComboLazy) {
		cellsOrigBackColor = this.getBackground();
		cellsOrigForeColor = this.getForeground();
		cageTableModel = new CageTableModelForSpots(expListComboLazy);
		setModel(cageTableModel);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		setPreferredScrollableViewportSize(new Dimension(500, 400));
		setFillsViewportHeight(true);

		setDefaultRenderer(Color.class, new TableCellColorRenderer(true));
		setDefaultEditor(Color.class, new TableCellColorEditor());

		TableColumnModel columnModel = getColumnModel();

		columnModel.getColumn(1).setPreferredWidth(15);
		columnModel.getColumn(2).setPreferredWidth(15);
		columnModel.getColumn(3).setPreferredWidth(15);
		columnModel.getColumn(4).setPreferredWidth(25);
		columnModel.getColumn(5).setPreferredWidth(15);
		columnModel.getColumn(6).setPreferredWidth(15);
		columnModel.getColumn(7).setPreferredWidth(10);
	}
}
