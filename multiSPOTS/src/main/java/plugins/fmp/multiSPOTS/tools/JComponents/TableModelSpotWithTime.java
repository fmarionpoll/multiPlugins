package plugins.fmp.multiSPOTS.tools.JComponents;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;
import plugins.fmp.multiSPOTS.experiment.Experiment;

public class TableModelSpotWithTime extends AbstractTableModel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JComboBoxExperiment expList = null;
	private final String columnNames[] = { "Starting at frame", "End frame" };
	private ArrayList<Long[]> intervals = null;

	public TableModelSpotWithTime(JComboBoxExperiment expList) {
		super();
		this.expList = expList;
	}

	@Override
	public int getRowCount() {
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			intervals = exp.spotsArray.getKymoIntervalsFromSpots().intervals;
			return intervals.size();
		}
		return 0;
	}

	@Override
	public int getColumnCount() {
		return 1;
//		return columnNames.length;
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
//    	switch (columnIndex) {
//    	case 0: return Integer.class;
//    	case 1: return Integer.class;
//        }
		return Integer.class;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Long[] interval = intervals.get(rowIndex);
		return interval[columnIndex];
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return true;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Long[] interval = intervals.get(rowIndex);
//		switch (columnIndex) {
//		case 0:  
//		case 1: 
		interval[columnIndex] = (long) aValue;
//			break;
//	    }
	}

}