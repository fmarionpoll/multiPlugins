package plugins.fmp.multiSPOTS96.tools.toExcel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multiSPOTS96.experiment.cages.CageProperties;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.experiment.spots.SpotProperties;

public class XLSResults {
	private String name = null;
	private String stimulus = null;
	private String concentration = null;
	private int nflies = 1;
	private int cageID = 0;
	private int cagePosition = 0;
	private Color color;
	private ArrayList<Double> dataValues = null;
	private int valuesOutLength = 0;
	private double[] valuesOut = null;

	public XLSResults(String name, int nflies, int cageID, int cagePos, EnumXLSExport exportType) {
		this.name = name;
		this.nflies = nflies;
		this.cageID = cageID;
		this.cagePosition = cagePos;
	}

	public XLSResults(CageProperties cageProperties, SpotProperties spotProperties, int nFrames) {
		this.name = spotProperties.getName();
		this.color = spotProperties.getColor();
		this.nflies = cageProperties.getCageNFlies();
		this.cageID = cageProperties.getCageID();
		this.cagePosition = spotProperties.getCagePosition();
		this.stimulus = spotProperties.getStimulus();
		this.concentration = spotProperties.getConcentration();
		initValuesArray(nFrames);
	}

	// ---------------------------
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStimulus() {
		return this.stimulus;
	}

	public void setStimulus(String stimulus) {
		this.stimulus = stimulus;
	}

	public String getConcentration() {
		return this.concentration;
	}

	public void setConcentration(String concentration) {
		this.concentration = concentration;
	}

	public int getNflies() {
		return this.nflies;
	}

	public void setNflies(int nFlies) {
		this.nflies = nFlies;
	}

	public int getCageID() {
		return this.cageID;
	}

	public void setCageID(int cageID) {
		this.cageID = cageID;
	}

	public int getCagePosition() {
		return this.cagePosition;
	}

	public void getCagePosition(int cagePosition) {
		this.cagePosition = cagePosition;
	}

	public Color getColor() {
		return this.color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public ArrayList<Double> getDataValues() {
		return this.dataValues;
	}

	public void setDataValues(ArrayList<Double> dataValues) {
		this.dataValues = dataValues;
	}

	public int getValuesOutLength() {
		return this.valuesOutLength;
	}

	public double[] getValuesOut() {
		return valuesOut;
	}

	public void setValuesOut(double[] valuesOut) {
		this.valuesOut = valuesOut;
	}

	// ---------------------------

	void initValuesOutArray(int dimension, Double val) {
		this.valuesOutLength = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, val);
	}

	private void initValuesArray(int dimension) {
		this.valuesOutLength = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, Double.NaN);
	}

	void clearValues(int fromindex) {
		int toindex = valuesOut.length;
		if (fromindex > 0 && fromindex < toindex) {
			Arrays.fill(valuesOut, fromindex, toindex, Double.NaN);
		}
	}

	void clearAll() {
		dataValues = null;
		valuesOut = null;
		nflies = 0;
	}

	public void getDataFromSpot(Spot spot, long binData, long binExcel, XLSExportOptions xlsExportOptions) {
		dataValues = (ArrayList<Double>) spot.getMeasuresForExcelPass1(xlsExportOptions.exportType, binData, binExcel);
		if (xlsExportOptions.relativeToT0 && xlsExportOptions.exportType != EnumXLSExport.AREA_FLYPRESENT) {
			relativeToMaximum();
		}
	}

	public void transferDataValuesToValuesOut(double scalingFactorToPhysicalUnits, EnumXLSExport xlsExport) {
		if (valuesOutLength == 0 || dataValues == null || dataValues.size() < 1)
			return;

		boolean removeZeros = false;
		int len = Math.min(valuesOutLength, dataValues.size());
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
			this.valuesOutLength = sourceRow.valuesOutLength;
			valuesOut = new double[valuesOutLength];
		}
		for (int i = 0; i < valuesOutLength; i++)
			valuesOut[i] = sourceRow.valuesOut[i];
	}

	public List<Double> relativeToMaximum() {
		if (dataValues == null || dataValues.size() < 1)
			return null;

		double value0 = getMaximum();
		relativeToValue(value0);
		return dataValues;
	}

	public double getMaximum() {
		double maximum = 0.;
		if (dataValues == null || dataValues.size() < 1)
			return maximum;

		maximum = dataValues.get(0);
		for (int index = 0; index < dataValues.size(); index++) {
			double value = dataValues.get(index);
			maximum = Math.max(maximum, value);
		}

		return maximum;
	}

	private void relativeToValue(double value0) {
		for (int index = 0; index < dataValues.size(); index++) {
			double value = dataValues.get(index);
			// dataValues.set(index, ((value0 - value) / value0));
			dataValues.set(index, value / value0);
		}
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

}
