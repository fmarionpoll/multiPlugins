package plugins.fmp.multiSPOTS.tools.toExcel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XLSResults {
	public String name = null;
	String stimulus = null;
	String concentration = null;
	int nadded = 1;
	boolean[] padded_out = null;

	public int dimension = 0;
	public int nflies = 1;
	public int cageID = 0;
	public int cagePosition = 0;
	public EnumXLSExportType exportType = null;
	public ArrayList<Double> dataValues = null;
	public double[] valuesOut = null;

	public XLSResults(String name, int nflies, int cageID, int cagePos, EnumXLSExportType exportType) {
		this.name = name;
		this.nflies = nflies;
		this.cageID = cageID;
		this.cagePosition = cagePos;
		this.exportType = exportType;
	}

	public XLSResults(String name, int nflies, int cageID, int cagePos, EnumXLSExportType exportType, int nFrames) {
		this.name = name;
		this.nflies = nflies;
		this.cageID = cageID;
		this.cagePosition = cagePos;
		this.exportType = exportType;
		initValuesArray(nFrames);
	}

	void initValuesOutArray(int dimension, Double val) {
		this.dimension = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, val);
	}

	private void initValuesArray(int dimension) {
		this.dimension = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, Double.NaN);
		padded_out = new boolean[dimension];
		Arrays.fill(padded_out, false);
	}

	void clearValues(int fromindex) {
		int toindex = valuesOut.length;
		if (fromindex > 0 && fromindex < toindex) {
			Arrays.fill(valuesOut, fromindex, toindex, Double.NaN);
			Arrays.fill(padded_out, fromindex, toindex, false);
		}
	}

	void clearAll() {
		dataValues = null;
		valuesOut = null;
		nflies = 0;
	}

	public void transferMeasuresToValuesOut(double scalingFactorToPhysicalUnits, EnumXLSExportType xlsExport) {
		if (dimension == 0 || dataValues == null || dataValues.size() < 1)
			return;

		boolean removeZeros = false;
		int len = Math.min(dimension, dataValues.size());
		if (removeZeros) {
			for (int i = 0; i < len; i++) {
				double ivalue = dataValues.get(i);
				valuesOut[i] = (ivalue == 0 ? Double.NaN : ivalue) * scalingFactorToPhysicalUnits;
			}
		} else {
			for (int i = 0; i < len; i++)
				valuesOut[i] = dataValues.get(i) * scalingFactorToPhysicalUnits;
		}
	}

	public void copyValuesOut(XLSResults sourceRow) {
		if (sourceRow.valuesOut.length != valuesOut.length) {
			this.dimension = sourceRow.dimension;
			valuesOut = new double[dimension];
		}
		for (int i = 0; i < dimension; i++)
			valuesOut[i] = sourceRow.valuesOut[i];
	}

	public List<Double> relativeToT0() {
		if (dataValues == null || dataValues.size() < 1)
			return null;

		double value0 = dataValues.get(0);
		if (value0 > 0 && value0 > dataValues.get(1) && dataValues.get(1) > dataValues.get(2))
			relativeToValue(value0);
		else {
			value0 = median(0, 3);
			double value1 = median(0, 5);
			if (value0 > 0 && value0 > value1)
				relativeToValue(value0);
			else if (value1 > 0)
				relativeToValue(value1);
		}
		return dataValues;
	}

	private void relativeToValue(double value0) {
		for (int index = 0; index < dataValues.size(); index++) {
			double value = dataValues.get(index);
			dataValues.set(index, ((value0 - value) / value0));
		}
	}

//	public List<Double> relativeToT(int t) {
//		if (dataValues == null || dataValues.size() < 1)
//			return null;
//
//		double value0 = dataValues.get(t);
//		if (value0 == 0.) {
//			for (int index = 0; index < dataValues.size(); index++) {
//				if (dataValues.get(index) > 0) {
//					value0 = dataValues.get(index);
//					break;
//				}
//			}
//		}
//
//		relativeToValue(value0);
//		return dataValues;
//	}
//	
//
//	public List<Double> relativeToMedianT(int t, int npoints) {
//		if (dataValues == null || dataValues.size() < 1)
//			return null;
//
//		double value0 = median(t, npoints);
//		if (value0 == 0.) {
//			for (int index = 0; index < dataValues.size(); index++) {
//				if (dataValues.get(index) > 0) {
//					value0 = dataValues.get(index);
//					break;
//				}
//			}
//		}
//
//		relativeToValue(value0);
//		return dataValues;
//	}

	private double median(int t, int npoints) {
		double[] numArray = new double[npoints];
		for (int i = 0; i < npoints; i++) {
			numArray[i] = dataValues.get(t + i);
		}
		Arrays.sort(numArray);
		int middle = numArray.length / 2;
		double medianValue = 0; // declare variable
		if (numArray.length % 2 == 1)
			medianValue = numArray[middle];
		else
			medianValue = (numArray[middle - 1] + numArray[middle]) / 2;

		return medianValue;
	}

	boolean subtractDeltaT(int arrayStep, int binStep) {
		if (valuesOut == null || valuesOut.length < 2)
			return false;
		for (int index = 0; index < valuesOut.length; index++) {
			int timeIndex = index * arrayStep + binStep;
			int indexDelta = (int) (timeIndex / arrayStep);
			if (indexDelta < valuesOut.length)
				valuesOut[index] = valuesOut[indexDelta] - valuesOut[index];
			else
				valuesOut[index] = Double.NaN;
		}
		return true;
	}

	void addDataToValOutEvap(XLSResults result) {
		if (result.valuesOut.length > valuesOut.length) {
			System.out.println("XLSResults:addDataToValOutEvap() Error: from len=" + result.valuesOut.length
					+ " to len=" + valuesOut.length);
			return;
		}
		for (int i = 0; i < result.valuesOut.length; i++)
			valuesOut[i] += result.valuesOut[i];
		nflies++;
	}

	void averageEvaporation() {
		if (nflies == 0)
			return;
		for (int i = 0; i < valuesOut.length; i++)
			valuesOut[i] = valuesOut[i] / nflies;
		nflies = 1;
	}

	void subtractEvap(XLSResults evap) {
		if (valuesOut == null)
			return;
		int len = Math.min(valuesOut.length, evap.valuesOut.length);
		for (int i = 0; i < len; i++)
			valuesOut[i] -= evap.valuesOut[i];
	}

	void sumValues_out(XLSResults dataToAdd) {
		int len = Math.min(valuesOut.length, dataToAdd.valuesOut.length);
		for (int i = 0; i < len; i++)
			valuesOut[i] += dataToAdd.valuesOut[i];
		nadded += 1;
	}

}
