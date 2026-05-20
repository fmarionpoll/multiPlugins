package plugins.fmp.multitools.experiment.cages;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class CageTableModelForSpots extends AbstractTableModel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3501225818220221949L;
	private static final char STIM_CONC_SEP = '\u0000';

	private JComboBoxExperimentLazy expList = null;
	String columnNames[] = { "Name", "N flies", "Strain", "Sex", "Age", "Comment", "Color", "Fly?", "N spots",
			"N stimuli" };
	public Color colorTable[] = { Color.GRAY, Color.WHITE };

	public CageTableModelForSpots(JComboBoxExperimentLazy expList) {
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
			return exp.getCages().cagesList.size();
		}
		return 0;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		if (expList == null || expList.getSelectedIndex() < 0) {
			return null;
		}
		Experiment exp = (Experiment) expList.getSelectedItem();
		List<Cage> cages = exp.getCages().cagesList;
		if (rowIndex < 0 || rowIndex >= cages.size()) {
			return null;
		}
		Cage cage = cages.get(rowIndex);
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
		case 8: {
			Spots spots = exp.getSpots();
			return cage.getSpotList(spots).size();
		}
		case 9: {
			Spots spots = exp.getSpots();
			return countDistinctStimulusConc(cage.getSpotList(spots));
		}
		default:
			return null;
		}
	}

	private static int countDistinctStimulusConc(List<Spot> spots) {
		Set<String> keys = new HashSet<>();
		for (Spot spot : spots) {
			if (spot == null || spot.getProperties() == null) {
				continue;
			}
			String s = Objects.toString(spot.getProperties().getStimulus(), "");
			String c = Objects.toString(spot.getProperties().getConcentration(), "");
			keys.add(s + STIM_CONC_SEP + c);
		}
		return keys.size();
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
		case 8:
		case 9:
			return Integer.class;
		default:
			return String.class;
		}
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex > 0 && columnIndex < 8;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Cage cage = getCageAt(rowIndex);
		setValueAt(aValue, cage, columnIndex);
	}

	public void setValueAt(Object aValue, Cage cage, int columnIndex) {

		if (cage != null && columnIndex < 8) {
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
			default:
				break;
			}
		}
	}

	public Cage getCageAt(int rowIndex) {
		Cage cage = null;
		if (expList != null && expList.getSelectedIndex() >= 0) {
			Experiment exp = (Experiment) expList.getSelectedItem();
			List<Cage> cages = exp.getCages().getCageList();
			if (rowIndex >= 0 && rowIndex < cages.size()) {
				cage = cages.get(rowIndex);
			}
		}
		return cage;
	}

}
