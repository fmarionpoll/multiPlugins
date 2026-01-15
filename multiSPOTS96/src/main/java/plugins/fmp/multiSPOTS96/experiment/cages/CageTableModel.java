package plugins.fmp.multitools.experiment.cages;

import java.awt.Color;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class CageTableModel extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3501225818220221949L;
	private JComboBoxExperimentLazy expList = null;
	String columnNames[] = { "Name", "N flies", "Strain", "Sex", "Age", "Comment", "Color", "Fly?" };
	public Color colorTable[] = { Color.GRAY, Color.WHITE };

	public CageTableModel(JComboBoxExperimentLazy expList) {
		super();
		this.expList = expList;
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
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
	public String getColumnName(int column) {
		return columnNames[column];
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
				return cage.getProperties().getCageNFlies();
			case 2:
				return cage.getProperties().getFlyStrain();
			case 3:
				return cage.getProperties().getFlySex();
			case 4:
				return cage.getProperties().getFlyAge();
			case 5:
				return cage.getProperties().getComment();
			case 6:
				return cage.getProperties().getColor();
			case 7:
				return cage.getProperties().isSelected();
			}
		}
		return null;
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
		case 6:
			return Color.class;
		case 7:
			return Boolean.class;
		}
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex > 0;
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
			case 1: {
				cage.getProperties().setCageNFlies((int) aValue);
				int ivalue = (int) aValue;
				Color color = ivalue >= 0 ? colorTable[((int) aValue) % 2] : Color.yellow;
				cage.getProperties().setColor(color);
				cage.getRoi().setColor(color);
			}
				break;
			case 2:
				cage.getProperties().setFlyStrain(aValue.toString());
				break;
			case 3:
				cage.getProperties().setFlySex(aValue.toString());
				break;
			case 4:
				cage.getProperties().setFlyAge((int) aValue);
				break;
			case 5:
				cage.getProperties().setComment(aValue.toString());
				break;
			case 6:
				cage.getProperties().setColor((Color) aValue);
				break;
			case 7:
				cage.getProperties().setSelected(Boolean.valueOf(aValue.toString()));
				int ivalue = cage.getProperties().isSelected() ? 1 : 0;
				Color color = ivalue >= 0 ? colorTable[ivalue % 2] : Color.yellow;
				cage.getProperties().setColor(color);
				cage.getRoi().setColor(color);
				break;
			}
		}
	}

}
