package plugins.fmp.multiSPOTS96.tools.results;

import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class ResultsOptions {
	public boolean xyImage = true;
	public boolean xyCage = true;
	public boolean xyCapillaries = true;
	public boolean ellipseAxes = false;

	public boolean distance = false;
	public boolean alive = true;
	public boolean sleep = true;
	public int sleepThreshold = 5;

	public boolean spotAreas = true;
	public boolean sum = true;
	public boolean sum2 = true;
	public boolean nPixels = true;

	public boolean topLevel = true;
	public boolean topLevelDelta = false;
	public boolean bottomLevel = false;
	public boolean derivative = false;
	public boolean lrPI = true;
	public double lrPIThreshold = 0.;

	public boolean sumGulps = false;
	public boolean nbGulps = false;
	public boolean amplitudeGulps = false;
	public boolean tToNextGulp = false;
	public boolean tToNextGulp_LR = false;

	public boolean markovChain = false;
	public boolean autocorrelation = false;
	public boolean crosscorrelation = false;
	public boolean crosscorrelationLR = false;
	public int nBinsCorrelation = 40;

	public boolean sumPerCage = true;
	public boolean subtractT0 = true;
	public boolean divideWithT0 = false;
	public boolean relativeToMaximum = false;
	public boolean relativeToMedianT0 = false;
	public int medianT0FromNPoints = 5;
	public boolean onlyalive = true;
	public boolean correctEvaporation = false;

	public boolean transpose = false;
	public boolean duplicateSeries = true;
	public int buildExcelStepMs = 1;
	public int buildExcelUnitMs = 1;
	public boolean fixedIntervals = false;
	public long startAll_Ms = 0;
	public long endAll_Ms = 999999;
	public boolean exportAllFiles = true;
	public boolean absoluteTime = false;
	public boolean collateSeries = false;
	public boolean padIntervals = true;

	public int firstExp = -1;
	public int lastExp = -1;
	public int experimentIndexFirst = -1;
	public int experimentIndexLast = -1;
	public int cageIndexFirst = -1;
	public int cageIndexLast = -1;
	public int seriesIndexFirst = -1;
	public int seriesIndexLast = -1;
	public JComboBoxExperimentLazy expList = null;

	// internal parameters
	public boolean trim_alive = false;
	public boolean compensateEvaporation = false;
	public EnumResults resultType = null;

	public void copy(ResultsOptions resultsOptions) {
		this.xyImage = resultsOptions.xyImage;
		this.xyCage = resultsOptions.xyCage;
		this.ellipseAxes = resultsOptions.ellipseAxes;

		this.distance = resultsOptions.distance;
		this.alive = resultsOptions.alive;
		this.sleep = resultsOptions.sleep;
		this.sleepThreshold = resultsOptions.sleepThreshold;

		this.spotAreas = resultsOptions.spotAreas;
		this.sum = resultsOptions.sum;
		this.sum2 = resultsOptions.sum2;
		this.nPixels = resultsOptions.nPixels;

		this.autocorrelation = resultsOptions.autocorrelation;
		this.crosscorrelation = resultsOptions.crosscorrelation;
		this.crosscorrelationLR = resultsOptions.crosscorrelationLR;
		this.nBinsCorrelation = resultsOptions.nBinsCorrelation;

		this.sumPerCage = resultsOptions.sumPerCage;
		this.subtractT0 = resultsOptions.subtractT0;
		this.relativeToMaximum = resultsOptions.relativeToMaximum;
		this.relativeToMedianT0 = resultsOptions.relativeToMedianT0;
		this.medianT0FromNPoints = resultsOptions.medianT0FromNPoints;
		this.onlyalive = resultsOptions.onlyalive;

		this.transpose = resultsOptions.transpose;
		this.duplicateSeries = resultsOptions.duplicateSeries;
		this.buildExcelStepMs = resultsOptions.buildExcelStepMs;
		this.buildExcelUnitMs = resultsOptions.buildExcelUnitMs;
		this.fixedIntervals = resultsOptions.fixedIntervals;
		this.startAll_Ms = resultsOptions.startAll_Ms;
		this.endAll_Ms = resultsOptions.endAll_Ms;
		this.exportAllFiles = resultsOptions.exportAllFiles;
		this.absoluteTime = resultsOptions.absoluteTime;
		this.collateSeries = resultsOptions.collateSeries;
		this.padIntervals = resultsOptions.padIntervals;

		this.experimentIndexFirst = resultsOptions.experimentIndexFirst;
		this.experimentIndexLast = resultsOptions.experimentIndexLast;
		this.cageIndexFirst = resultsOptions.cageIndexFirst;
		this.cageIndexLast = resultsOptions.cageIndexLast;
		this.seriesIndexFirst = resultsOptions.seriesIndexFirst;
		this.seriesIndexLast = resultsOptions.seriesIndexLast;
		this.expList = resultsOptions.expList;

		this.trim_alive = resultsOptions.trim_alive;
		this.compensateEvaporation = resultsOptions.compensateEvaporation;
		this.resultType = resultsOptions.resultType;
	}
}
