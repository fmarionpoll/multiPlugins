package plugins.fmp.multitools.experiment.capillaries;

import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class ReferenceMeasures {

	private static final String ID_EVAPORATION = "_ref_evaporation";
	private static final String ID_EVAPORATION_L = "_ref_evaporationL";
	private static final String ID_EVAPORATION_R = "_ref_evaporationR";
	private static final String ID_THRESHOLD = "_ref_threshold";

	private CapillaryMeasure evaporation = new CapillaryMeasure(ID_EVAPORATION);
	private CapillaryMeasure evaporationL = new CapillaryMeasure(ID_EVAPORATION_L);
	private CapillaryMeasure evaporationR = new CapillaryMeasure(ID_EVAPORATION_R);
	private CapillaryMeasure derivativeThreshold = new CapillaryMeasure(ID_THRESHOLD);

	public CapillaryMeasure getEvaporation() {
		return evaporation;
	}

	public void setEvaporation(CapillaryMeasure m) {
		if (m != null)
			evaporation.copy(m);
		else
			evaporation.clear();
	}

	public CapillaryMeasure getEvaporationL() {
		return evaporationL;
	}

	public void setEvaporationL(CapillaryMeasure m) {
		if (m != null)
			evaporationL.copy(m);
		else
			evaporationL.clear();
	}

	public CapillaryMeasure getEvaporationR() {
		return evaporationR;
	}

	public void setEvaporationR(CapillaryMeasure m) {
		if (m != null)
			evaporationR.copy(m);
		else
			evaporationR.clear();
	}

	public CapillaryMeasure getDerivativeThreshold() {
		return derivativeThreshold;
	}

	public void setDerivativeThreshold(CapillaryMeasure m) {
		if (m != null)
			derivativeThreshold.copy(m);
		else
			derivativeThreshold.clear();
	}

	public void clear() {
		evaporation.clear();
		evaporationL.clear();
		evaporationR.clear();
		derivativeThreshold.clear();
	}

	public void copy(ReferenceMeasures other) {
		if (other == null)
			return;
		if (other.evaporation.isThereAnyMeasuresDone())
			evaporation.copy(other.evaporation);
		if (other.evaporationL.isThereAnyMeasuresDone())
			evaporationL.copy(other.evaporationL);
		if (other.evaporationR.isThereAnyMeasuresDone())
			evaporationR.copy(other.evaporationR);
		if (other.derivativeThreshold.isThereAnyMeasuresDone())
			derivativeThreshold.copy(other.derivativeThreshold);
	}

	public boolean hasAnyData() {
		return evaporation.isThereAnyMeasuresDone() || evaporationL.isThereAnyMeasuresDone()
				|| evaporationR.isThereAnyMeasuresDone() || derivativeThreshold.isThereAnyMeasuresDone();
	}

	public String csvExportRow(String id, CapillaryMeasure measure, String sep) {
		if (measure == null || !measure.isThereAnyMeasuresDone())
			return "";
		StringBuffer sbf = new StringBuffer();
		sbf.append(id).append(sep).append(0).append(sep);
		measure.cvsExportYDataToRow(sbf, sep);
		sbf.append("\n");
		return sbf.toString();
	}

	public boolean csvImportRow(String[] data, String sep) {
		if (data == null || data.length < 2)
			return false;
		String id = data[0];
		if (ID_EVAPORATION.equals(id))
			return evaporation.csvImportYDataFromRow(data, 2);
		if (ID_EVAPORATION_L.equals(id))
			return evaporationL.csvImportYDataFromRow(data, 2);
		if (ID_EVAPORATION_R.equals(id))
			return evaporationR.csvImportYDataFromRow(data, 2);
		if (ID_THRESHOLD.equals(id))
			return derivativeThreshold.csvImportYDataFromRow(data, 2);
		return false;
	}

	public CapillaryMeasure getEvaporationForDisplay() {
		if (evaporation.isThereAnyMeasuresDone())
			return evaporation;
		if (evaporationL.isThereAnyMeasuresDone() && evaporationR.isThereAnyMeasuresDone())
			return averageMeasures(evaporationL, evaporationR);
		if (evaporationL.isThereAnyMeasuresDone())
			return evaporationL;
		if (evaporationR.isThereAnyMeasuresDone())
			return evaporationR;
		return null;
	}

	private static CapillaryMeasure averageMeasures(CapillaryMeasure a, CapillaryMeasure b) {
		if (a == null || b == null || a.polylineLevel == null || b.polylineLevel == null)
			return null;
		int n = Math.min(a.getNPoints(), b.getNPoints());
		if (n == 0)
			return null;
		CapillaryMeasure avg = new CapillaryMeasure(ID_EVAPORATION);
		double[] x = new double[n];
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = i;
			y[i] = (a.getValueAt(i) + b.getValueAt(i)) / 2.0;
		}
		avg.polylineLevel = new Level2D(x, y, n);
		return avg;
	}
}
