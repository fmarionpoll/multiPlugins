package plugins.fmp.multitools.tools.JComponents;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;

public class CapillaryTableModel extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6325792669154093747L;
	private JComboBoxExperimentLazy expList = null;
	String columnNames[] = { "Name", "Cage", "N flies", "Volume", "Stimulus", "Concentration" };

	public CapillaryTableModel(JComboBoxExperimentLazy expList) {
		super();
		this.expList = expList;
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return String.class;
		case 1:
			return Integer.class;
		case 2:
			return Integer.class;
		case 3:
			return Double.class;
		case 4:
			return String.class;
		case 5:
			return String.class;
		}
		return String.class;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public int getRowCount() {
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			return exp.getCapillaries().getList().size();
		}
		return 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Capillary cap = getCapillaryAt(rowIndex);
		if (cap != null) {
			switch (columnIndex) {
			case 0:
				return cap.getRoiName();
			case 1:
				return cap.getCageID();
			case 2:
				return cap.getNFlies();
			case 3:
				return cap.getVolume();
			case 4:
				return cap.getStimulus();
			case 5:
				return cap.getConcentration();
			}
		}
		return null;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		switch (columnIndex) {
		case 0:
			return false;
		default:
			return true;
		}
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Capillary cap = getCapillaryAt(rowIndex);
		if (cap != null) {
			switch (columnIndex) {
			case 0:
				cap.setRoiName(aValue.toString());
				break;
			case 1:
				cap.setCageID((int) aValue);
				break;
			case 2:
				cap.setNFlies((int) aValue);
				break;
			case 3:
				cap.setVolume((double) aValue);
				break;
			case 4:
				cap.setStimulus(aValue.toString());
				break;
			case 5:
				cap.setConcentration(aValue.toString());
				break;
			}
		}
	}

	private Capillary getCapillaryAt(int rowIndex) {
		Capillary cap = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			cap = exp.getCapillaries().getList().get(rowIndex);
		}
		return cap;
	}

}
