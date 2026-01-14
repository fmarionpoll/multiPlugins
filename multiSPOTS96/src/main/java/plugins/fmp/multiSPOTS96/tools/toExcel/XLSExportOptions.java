package plugins.fmp.multiSPOTS96.tools.toExcel;

import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;

public class XLSExportOptions {
	public boolean xyImage = true;
	public boolean xyCage = true;
	public boolean ellipseAxes = false;

	public boolean distance = false;
	public boolean alive = true;
	public boolean sleep = true;
	public int sleepThreshold = 5;

	public boolean spotAreas = true;
	public boolean sum = true;
	public boolean sum2 = true;
	public boolean nPixels = true;

	public boolean autocorrelation = false;
	public boolean crosscorrelation = false;
	public boolean crosscorrelationLR = false;
	public int nBinsCorrelation = 40;

	public boolean sumPerCage = true;
	public boolean subtractT0 = true;
	public boolean relativeToT0 = true;
	public boolean relativeToMedianT0 = false;
	public int medianT0FromNPoints = 5;
	public boolean onlyalive = true;

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
	public EnumXLSExport exportType;

	public void copy(XLSExportOptions xlsExportOptions) {
		this.xyImage = xlsExportOptions.xyImage;
		this.xyCage = xlsExportOptions.xyCage;
		this.ellipseAxes = xlsExportOptions.ellipseAxes;

		this.distance = xlsExportOptions.distance;
		this.alive = xlsExportOptions.alive;
		this.sleep = xlsExportOptions.sleep;
		this.sleepThreshold = xlsExportOptions.sleepThreshold;

		this.spotAreas = xlsExportOptions.spotAreas;
		this.sum = xlsExportOptions.sum;
		this.sum2 = xlsExportOptions.sum2;
		this.nPixels = xlsExportOptions.nPixels;

		this.autocorrelation = xlsExportOptions.autocorrelation;
		this.crosscorrelation = xlsExportOptions.crosscorrelation;
		this.crosscorrelationLR = xlsExportOptions.crosscorrelationLR;
		this.nBinsCorrelation = xlsExportOptions.nBinsCorrelation;

		this.sumPerCage = xlsExportOptions.sumPerCage;
		this.subtractT0 = xlsExportOptions.subtractT0;
		this.relativeToT0 = xlsExportOptions.relativeToT0;
		this.relativeToMedianT0 = xlsExportOptions.relativeToMedianT0;
		this.medianT0FromNPoints = xlsExportOptions.medianT0FromNPoints;
		this.onlyalive = xlsExportOptions.onlyalive;

		this.transpose = xlsExportOptions.transpose;
		this.duplicateSeries = xlsExportOptions.duplicateSeries;
		this.buildExcelStepMs = xlsExportOptions.buildExcelStepMs;
		this.buildExcelUnitMs = xlsExportOptions.buildExcelUnitMs;
		this.fixedIntervals = xlsExportOptions.fixedIntervals;
		this.startAll_Ms = xlsExportOptions.startAll_Ms;
		this.endAll_Ms = xlsExportOptions.endAll_Ms;
		this.exportAllFiles = xlsExportOptions.exportAllFiles;
		this.absoluteTime = xlsExportOptions.absoluteTime;
		this.collateSeries = xlsExportOptions.collateSeries;
		this.padIntervals = xlsExportOptions.padIntervals;

		this.experimentIndexFirst = xlsExportOptions.experimentIndexFirst;
		this.experimentIndexLast = xlsExportOptions.experimentIndexLast;
		this.cageIndexFirst = xlsExportOptions.cageIndexFirst;
		this.cageIndexLast = xlsExportOptions.cageIndexLast;
		this.seriesIndexFirst = xlsExportOptions.seriesIndexFirst;
		this.seriesIndexLast = xlsExportOptions.seriesIndexLast;
		this.expList = xlsExportOptions.expList;

		this.trim_alive = xlsExportOptions.trim_alive;
		this.compensateEvaporation = xlsExportOptions.compensateEvaporation;
		this.exportType = xlsExportOptions.exportType;
	}
}
