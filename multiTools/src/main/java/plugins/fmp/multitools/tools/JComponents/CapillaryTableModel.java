package plugins.fmp.multitools.tools.JComponents;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryCagePositionSwap;

public class CapillaryTableModel extends javax.swing.table.AbstractTableModel {
	private static final long serialVersionUID = 6325792669154093747L;
	private JComboBoxExperimentLazy expList = null;

	public CapillaryTableModel(JComboBoxExperimentLazy expList) {
		super();
		this.expList = expList;
	}

	@Override
	public int getColumnCount() {
		return CapillaryTableColumn.countColumns();
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (CapillaryTableColumn.fromIndex(columnIndex)) {
		case NAME:
		case POSITION:
		case STIMULUS:
		case CONCENTRATION:
			return String.class;
		case CAGE_ID:
		case N_FLIES:
			return Integer.class;
		case VOLUME:
			return Double.class;
		default:
			return String.class;
		}
	}

	@Override
	public String getColumnName(int column) {
		return CapillaryTableColumn.fromIndex(column).getHeader();
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
		if (cap == null)
			return null;
		switch (CapillaryTableColumn.fromIndex(columnIndex)) {
		case NAME:
			return cap.getRoiName();
		case CAGE_ID:
			return cap.getCageID();
		case POSITION:
			return CapillaryCagePositionSwap.positionLabel(cap);
		case N_FLIES:
			return cap.getNFlies();
		case VOLUME:
			return cap.getVolume();
		case STIMULUS:
			return cap.getStimulus();
		case CONCENTRATION:
			return cap.getConcentration();
		default:
			return null;
		}
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		CapillaryTableColumn col = CapillaryTableColumn.fromIndex(columnIndex);
		if (!col.isEditable())
			return false;
		if (col == CapillaryTableColumn.POSITION) {
			Capillary cap = getCapillaryAt(rowIndex);
			if (cap == null || cap.getRoi() == null)
				return false;
			if (expList == null || expList.getSelectedIndex() < 0)
				return false;
			Experiment exp = (Experiment) expList.getSelectedItem();
			return CapillaryCagePositionSwap.countCapillariesInCage(exp.getCapillaries().getList(),
					cap.getCageID()) >= 2;
		}
		return true;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		Capillary cap = getCapillaryAt(rowIndex);
		if (cap == null)
			return;
		switch (CapillaryTableColumn.fromIndex(columnIndex)) {
		case NAME:
			cap.setRoiName(aValue.toString());
			if (expList != null && expList.getSelectedIndex() >= 0) {
				Experiment exp = (Experiment) expList.getSelectedItem();
				if (exp != null)
					exp.refreshAfterCapillaryRoiIdentityChange(cap);
			}
			break;
		case CAGE_ID:
			cap.setCageID((int) aValue);
			break;
		case POSITION: {
			if (expList == null || expList.getSelectedIndex() < 0 || aValue == null)
				break;
			Experiment exp = (Experiment) expList.getSelectedItem();
			int[] rows = CapillaryCagePositionSwap.applyPositionSelection(exp, rowIndex, aValue.toString());
			if (rows.length > 0) {
				int lo = rows[0];
				int hi = rows[0];
				for (int r : rows) {
					lo = Math.min(lo, r);
					hi = Math.max(hi, r);
				}
				fireTableRowsUpdated(lo, hi);
			}
			return;
		}
		case N_FLIES:
			cap.setNFlies((int) aValue);
			syncCageNFliesFromCapillaries();
			break;
		case VOLUME:
			cap.setVolume((double) aValue);
			break;
		case STIMULUS:
			cap.setStimulus(aValue.toString());
			break;
		case CONCENTRATION:
			cap.setConcentration(aValue.toString());
			break;
		default:
			break;
		}
	}

	private void syncCageNFliesFromCapillaries() {
		if (expList == null || expList.getSelectedIndex() < 0)
			return;
		Experiment exp = (Experiment) expList.getSelectedItem();
		if (exp != null && exp.getCages() != null && exp.getCapillaries() != null)
			exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());
	}

	private Capillary getCapillaryAt(int rowIndex) {
		if (expList == null || expList.getSelectedIndex() < 0)
			return null;
		Experiment exp = (Experiment) expList.getSelectedItem();
		return exp.getCapillaries().getList().get(rowIndex);
	}
}
