package plugins.fmp.multitools.tools.JComponents;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;

public class CageTableModel extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3501225818220221949L;
	private JComboBoxExperimentLazy expList = null;

	public CageTableModel(JComboBoxExperimentLazy expList) {
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
			return exp.getCages().getCageList().size();
		}
		return 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Cage cage = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			cage = exp.getCages().getCageList().get(rowIndex);
		}
		if (cage != null) {
			switch (columnIndex) {
			case 0:
				if (cage.getCageRoi2D() != null)
					return cage.getCageRoi2D().getName();
				else {
					return "cage_" + cage.formatCageNumberToString(cage.getCageID());
				}
			case 1:
				return cage.getCageNFlies();
			case 2:
				return cage.prop.getFlyStrain();
			case 3:
				return cage.prop.getFlySex();
			case 4:
				return cage.prop.getFlyAge();
			case 5:
				return cage.prop.getComment();
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
			cage = exp.getCages().getCageList().get(rowIndex);
		}
		if (cage != null) {
			switch (columnIndex) {
			case 0: {
				String name = aValue.toString();
				if (cage.getCageRoi2D() != null) {
					cage.getCageRoi2D().setName(name);
				}
				if (name.length() >= 3) {
					int cageID = Integer.parseInt(name.substring(name.length() - 3));
					cage.setCageID(cageID);
				}
			}
				break;
			case 1:
				cage.setCageNFlies((int) aValue);
				break;
			case 2:
				cage.prop.setFlyStrain(aValue.toString());
				break;
			case 3:
				cage.prop.setFlySex(aValue.toString());
				break;
			case 4:
				cage.prop.setFlyAge((int) aValue);
				break;
			case 5:
				cage.prop.setComment(aValue.toString());
				break;
			}
		}
	}

}
