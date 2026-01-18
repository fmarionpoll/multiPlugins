package plugins.fmp.multitools.tools.results;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.CageProperties;
import plugins.fmp.multitools.experiment.cages.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cages.cage.FlyPositions;
import plugins.fmp.multitools.experiment.capillaries.capillary.Capillary;
import plugins.fmp.multitools.experiment.spots.spot.Spot;
import plugins.fmp.multitools.experiment.spots.spot.SpotProperties;

public class Results {
	private String name = null;
	private String stimulus = null;
	private String concentration = null;
	int nadded = 1;
	boolean[] padded_out = null;

	public int dimension = 0;
	private int nflies = 1;
	private int cageID = 0;
	private int cagePosition = 0;
	private String capSide = "..";
	private Color color;
	public EnumResults exportType = null;
	public ArrayList<Integer> dataInt = null;
	private ArrayList<Double> dataValues = null;
	private int valuesOutLength = 0;
	public double[] valuesOut = null;

	public Results(String name, int nflies, int cellID, EnumResults exportType) {
		this.name = name;
		this.nflies = nflies;
		this.cageID = cellID;
		this.exportType = exportType;
	}

	public Results(String name, int nflies, int cageID, int cagePos, EnumResults exportType) {
		this.name = name;
		this.nflies = nflies;
		this.cageID = cageID;
		this.cagePosition = cagePos;
	}

	public Results(CageProperties cageProperties, SpotProperties spotProperties, int nFrames) {
		this.name = spotProperties.getName();
		this.color = spotProperties.getColor();
		this.nflies = cageProperties.getCageNFlies();
		this.cageID = cageProperties.getCageID();
		this.cagePosition = spotProperties.getCagePosition();
		this.stimulus = spotProperties.getStimulus();
		this.concentration = spotProperties.getConcentration();
		initValuesOutArray(nFrames);
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

	public String getCapSide() {
		return this.capSide;
	}

	public void setCapSide(String capSide) {
		this.capSide = capSide;
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

	public void initValuesOutArray(int dimension, Double val) {
		this.valuesOutLength = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, val);
	}

	private void initValuesOutArray(int dimension) {
		this.valuesOutLength = dimension;
		valuesOut = new double[dimension];
		Arrays.fill(valuesOut, Double.NaN);
	}

	void clearValuesOut(int fromindex) {
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

	public void getDataFromSpot(Spot spot, long binData, long binExcel, ResultsOptions resultsOptions) {
		dataValues = (ArrayList<Double>) spot.getMeasuresForExcelPass1(resultsOptions.resultType, binData, binExcel);
		if (resultsOptions.relativeToMaximum && resultsOptions.resultType != EnumResults.AREA_FLYPRESENT) {
			relativeToMaximum();
		}
	}

	/**
	 * Gets data from a capillary and converts it to dataValues (with computation support).
	 * Capillary.getCapillaryMeasuresForXLSPass1() returns ArrayList<Integer>, so we
	 * convert to ArrayList<Double>.
	 * 
	 * @param exp            The experiment (required if computation is needed)
	 * @param capillary      The capillary to get data from
	 * @param binData        The bin duration for the data
	 * @param binExcel       The bin duration for Excel output
	 * @param resultsOptions The export options
	 * @param subtractT0     Whether to subtract T0 value (for TOPLEVEL, TOPRAW,
	 *                       etc.)
	 */
	public void getDataFromCapillary(Experiment exp, Capillary capillary, long binData, long binExcel,
			ResultsOptions resultsOptions, boolean subtractT0) {
		ArrayList<Integer> intData = capillary.getCapillaryMeasuresForXLSPass1(resultsOptions.resultType, binData,
				binExcel, exp, resultsOptions);

		if (intData == null || intData.isEmpty()) {
			dataValues = new ArrayList<>();
			return;
		}

		// Convert Integer to Double
		dataValues = new ArrayList<>(intData.size());
		int t0Value = 0;

		if (subtractT0 && intData.size() > 0) {
			t0Value = intData.get(0);
		}

		for (Integer intValue : intData) {
			if (subtractT0) {
				dataValues.add((double) (intValue - t0Value));
			} else {
				dataValues.add(intValue.doubleValue());
			}
		}

		if (resultsOptions.relativeToMaximum && resultsOptions.resultType != EnumResults.AREA_FLYPRESENT) {
			relativeToMaximum();
		}
	}

	/**
	 * Gets data from fly positions and converts it to dataValues.
	 * 
	 * @param flyPositions   The fly positions to get data from
	 * @param binData        The bin duration for the data
	 * @param binExcel       The bin duration for Excel output
	 * @param resultsOptions The export options
	 */
	public void getDataFromFlyPositions(FlyPositions flyPositions, long binData, long binExcel,
			ResultsOptions resultsOptions) {
		if (flyPositions == null || flyPositions.flyPositionList == null || flyPositions.flyPositionList.isEmpty()) {
			dataValues = new ArrayList<>();
			return;
		}

		dataValues = new ArrayList<>();
		EnumResults resultType = resultsOptions.resultType;

		switch (resultType) {
		case XYIMAGE:
		case XYTOPCAGE:
		case XYTIPCAPS:
			// Extract X or Y coordinate based on export type
			for (FlyPosition pos : flyPositions.flyPositionList) {
				Point2D center = pos.getCenterRectangle();
				if (resultType == EnumResults.XYIMAGE || resultType == EnumResults.XYTOPCAGE) {
					// For XYIMAGE and XYTOPCAGE, we might need to extract X or Y
					// Defaulting to Y coordinate (vertical position)
					dataValues.add(center.getY());
				} else {
					// XYTIPCAPS - could be X coordinate
					dataValues.add(center.getX());
				}
			}
			break;

		case DISTANCE:
			// Compute distance between consecutive points
			flyPositions.computeDistanceBetweenConsecutivePoints();
			for (FlyPosition pos : flyPositions.flyPositionList) {
				dataValues.add(pos.distance);
			}
			break;

		case ISALIVE:
			// Get alive status as double array
			flyPositions.computeIsAlive();
			for (FlyPosition pos : flyPositions.flyPositionList) {
				dataValues.add(pos.bAlive ? 1.0 : 0.0);
			}
			break;

		case SLEEP:
			// Get sleep status as double array
			flyPositions.computeSleep();
			for (FlyPosition pos : flyPositions.flyPositionList) {
				dataValues.add(pos.bSleep ? 1.0 : 0.0);
			}
			break;

		case ELLIPSEAXES:
			// Get ellipse axes
			flyPositions.computeEllipseAxes();
			for (FlyPosition pos : flyPositions.flyPositionList) {
				// Use axis1 (major axis) or could combine both
				dataValues.add(pos.axis1);
			}
			break;

		default:
			// Default: extract Y coordinate
			for (FlyPosition pos : flyPositions.flyPositionList) {
				Point2D center = pos.getCenterRectangle();
				dataValues.add(center.getY());
			}
			break;
		}

		// Apply relative to T0 if needed (not applicable for boolean types)
		if (resultsOptions.relativeToMaximum && resultType != EnumResults.ISALIVE && resultType != EnumResults.SLEEP) {
			relativeToMaximum();
		}
	}

	public void transferDataValuesToValuesOut(double scalingFactorToPhysicalUnits, EnumResults resultType) {
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

	public void copyValuesOut(Results sourceRow) {
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

	// ------------------------------

	public void addDataToValOutEvap(Results result) {
		if (result.valuesOut.length > valuesOut.length) {
			System.out.println("XLSResults:addDataToValOutEvap() Error: from len=" + result.valuesOut.length
					+ " to len=" + valuesOut.length);
			return;
		}

		for (int i = 0; i < result.valuesOut.length; i++) {
			valuesOut[i] += result.valuesOut[i];
		}
		nflies++;
	}

	public void averageEvaporation() {
		if (nflies == 0)
			return;

		for (int i = 0; i < valuesOut.length; i++)
			valuesOut[i] = valuesOut[i] / nflies;
		nflies = 1;
	}

	public void subtractEvap(Results evap) {
		if (valuesOut == null)
			return;
		int len = Math.min(valuesOut.length, evap.valuesOut.length);
		for (int i = 0; i < len; i++) {
			valuesOut[i] -= evap.valuesOut[i];
		}
	}

	void sumValues_out(Results dataToAdd) {
		int len = Math.min(valuesOut.length, dataToAdd.valuesOut.length);
		for (int i = 0; i < len; i++) {
			valuesOut[i] += dataToAdd.valuesOut[i];
		}
		nadded += 1;
	}

	public double padWithLastPreviousValue(long to_first_index) {
		double dvalue = 0;
		if (to_first_index >= valuesOut.length)
			return dvalue;

		int index = getIndexOfFirstNonEmptyValueBackwards(to_first_index);
		if (index >= 0) {
			dvalue = valuesOut[index];
			for (int i = index + 1; i < to_first_index; i++) {
				valuesOut[i] = dvalue;
				padded_out[i] = true;
			}
		}
		return dvalue;
	}

	private int getIndexOfFirstNonEmptyValueBackwards(long fromindex) {
		int index = -1;
		int ifrom = (int) fromindex;
		for (int i = ifrom; i >= 0; i--) {
			if (!Double.isNaN(valuesOut[i])) {
				index = i;
				break;
			}
		}
		return index;
	}

	public static Results getResultsArrayWithThatName(String testname, ResultsArray resultsArrayList) {
		Results resultsFound = null;
		for (Results results : resultsArrayList.resultsList) {
			if (results.name.equals(testname)) {
				resultsFound = results;
				break;
			}
		}
		return resultsFound;
	}

	public void transferDataIntToValuesOut(double scalingFactorToPhysicalUnits, EnumResults resultType) {
		if (dimension == 0 || dataInt == null || dataInt.size() < 1)
			return;

		boolean removeZeros = false;
		if (resultType == EnumResults.AMPLITUDEGULPS)
			removeZeros = true;

		int len = Math.min(dimension, dataInt.size());
		if (removeZeros) {
			for (int i = 0; i < len; i++) {
				int ivalue = dataInt.get(i);
				valuesOut[i] = (ivalue == 0 ? Double.NaN : ivalue) * scalingFactorToPhysicalUnits;
			}
		} else {
			for (int i = 0; i < len; i++)
				valuesOut[i] = dataInt.get(i) * scalingFactorToPhysicalUnits;
		}
	}

	public List<Integer> subtractT0() {
		if (dataInt == null || dataInt.size() < 1)
			return null;
		int valueAtT0 = dataInt.get(0);
		for (int index = 0; index < dataInt.size(); index++) {
			int value = dataInt.get(index);
			dataInt.set(index, value - valueAtT0);
		}
		return dataInt;
	}

}
