package plugins.fmp.multiSPOTS.tools.JComponents;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.cages.Cage;

public class TableModelCage extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3501225818220221949L;
	private JComboBoxExperiment expList = null;

	public TableModelCage(JComboBoxExperiment expList) {
		super();
		this.expList = expList;
	}

	@Override
	public int getColumnCount() {
		return 6;
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return String.class;
		case 1:
			return Integer.class;
		case 2:
			return String.class;
		case 3:
			return String.class;
		case 4:
			return Integer.class;
		case 5:
			return String.class;
		}
		return String.class;
	}

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case 0:
			return "Name";
		case 1:
			return "N flies";
		case 2:
			return "Strain";
		case 3:
			return "Sex";
		case 4:
			return "Age";
		case 5:
			return "Comment";
		}
		return "";
	}

	@Override
	public int getRowCount() {
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			return exp.cagesArray.cagesList.size();
		}
		return 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Cage cage = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			cage = exp.cagesArray.cagesList.get(rowIndex);
		}
		if (cage != null) {
			switch (columnIndex) {
			case 0:
				return cage.getRoi().getName();
			case 1:
				return cage.cageNFlies;
			case 2:
				return cage.strCageStrain;
			case 3:
				return cage.strCageSex;
			case 4:
				return cage.cageAge;
			case 5:
				return cage.strCageComment;
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
		Cage cage = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			cage = exp.cagesArray.cagesList.get(rowIndex);
		}
		if (cage != null) {
			switch (columnIndex) {
			case 0:
				cage.getRoi().setName(aValue.toString());
				break;
			case 1:
				cage.cageNFlies = (int) aValue;
				break;
			case 2:
				cage.strCageStrain = aValue.toString();
				break;
			case 3:
				cage.strCageSex = aValue.toString();
				break;
			case 4:
				cage.cageAge = (int) aValue;
				break;
			case 5:
				cage.strCageComment = aValue.toString();
				break;
			}
		}
	}

}
