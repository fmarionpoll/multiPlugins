package plugins.fmp.multitools.fmp_experiment.spots;

import java.awt.Color;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.JComponents.JComboBoxExperimentLazy;

public class SpotTableModel extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6325792669154093747L;
	private JComboBoxExperimentLazy expList = null;
	String columnNames[] = { "Spot name", "pixels", "uL", "IDCage", "Pos", "Row", "Col", "Stimulus", "Concentration",
			"Color" };

	public SpotTableModel(JComboBoxExperimentLazy expList) {
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
			return exp.getCages().getTotalNumberOfSpots(exp.getSpots());
		}
		return 0;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
		case 7:
		case 8:
			return String.class;
		case 2:
			return Double.class;
		case 9:
			return Color.class;
		default:
			return Integer.class;
		}
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex > 0;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		Spot spot = getSpotAt(rowIndex);
		if (spot != null) { // && spot.prop != null
			switch (columnIndex) {
			case 0:
				return spot.getRoi().getName(); // string
			case 1:
				return spot.getProperties().getSpotNPixels();
			case 2:
				return spot.getProperties().getSpotVolume();
			case 3:
				return spot.getProperties().getCageID();
			case 4:
				return spot.getProperties().getCagePosition();
			case 5:
				return spot.getProperties().getCageRow();
			case 6:
				return spot.getProperties().getCageColumn();
			case 7:
				return spot.getProperties().getStimulus(); // string
			case 8:
				return spot.getProperties().getConcentration(); // string
			case 9:
				return spot.getProperties().getColor();
			}
		}
		return null;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Spot spot = getSpotAt(rowIndex);
		if (spot != null && spot.getProperties() != null) {
			switch (columnIndex) {

			case 0:
				spot.getRoi().setName(aValue.toString());
				break;
			case 1:
				spot.getProperties().setSpotNPixels((int) aValue);
				break;
			case 2:
				spot.getProperties().setSpotVolume((double) aValue);
				break;
			case 3:
				spot.getProperties().setCageID((int) aValue);
				break;
			case 4:
				spot.getProperties().setCagePosition((int) aValue);
				break;
			case 5:
				spot.getProperties().setCageRow((int) aValue);
				break;
			case 6:
				spot.getProperties().setCageColumn((int) aValue);
				break;
			case 7:
				spot.getProperties().setStimulus(aValue.toString());
				break;
			case 8:
				spot.getProperties().setConcentration(aValue.toString());
				break;
			case 9:
				spot.getProperties().setColor((Color) aValue);
				spot.getRoi().setColor(spot.getProperties().getColor());
				break;
			}
		}
	}

	public Spot getSpotAt(int rowIndex) {
		Spot spot = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			spot = exp.getCages().getSpotAtGlobalIndex(rowIndex, exp.getSpots());
		}
		return spot;
	}
}
