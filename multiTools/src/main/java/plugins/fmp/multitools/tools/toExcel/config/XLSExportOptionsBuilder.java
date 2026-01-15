package plugins.fmp.multitools.tools.toExcel.config;

import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.config.ExcelExportConstants.DefaultOptions;

/**
 * Builder pattern implementation for XLSExportOptions. Provides a fluent
 * interface for constructing export options with proper defaults.
 */
public class XLSExportOptionsBuilder {

	// Basic export options
	private boolean xyImage = DefaultOptions.XY_IMAGE;
	private boolean xyCage = DefaultOptions.XY_CAGE;
	private boolean ellipseAxes = DefaultOptions.ELLIPSE_AXES;

	// Movement and behavior options
	private boolean distance = DefaultOptions.DISTANCE;
	private boolean alive = DefaultOptions.ALIVE;
	private boolean sleep = DefaultOptions.SLEEP;
	private int sleepThreshold = ExcelExportConstants.DEFAULT_SLEEP_THRESHOLD;

	// Spot area options
	private boolean spotAreas = DefaultOptions.SPOT_AREAS;
	private boolean sum = DefaultOptions.SUM;
	private boolean sum2 = DefaultOptions.SUM2;
	private boolean nPixels = DefaultOptions.N_PIXELS;

	// Correlation options
	private boolean autocorrelation = DefaultOptions.AUTOCORRELATION;
	private boolean crosscorrelation = DefaultOptions.CROSSCORRELATION;
	private boolean crosscorrelationLR = DefaultOptions.CROSSCORRELATION_LR;
	private int nBinsCorrelation = DefaultOptions.N_BINS_CORRELATION;

	// Processing options
	private boolean sumPerCage = DefaultOptions.SUM_PER_CAGE;
	private boolean subtractT0 = DefaultOptions.SUBTRACT_T0;
	private boolean divideWithT0 = DefaultOptions.DIVIDE_T0;
	private boolean relativeToMaximum = DefaultOptions.RELATIVE_TO_MAX;
	private boolean relativeToMedianT0 = DefaultOptions.RELATIVE_TO_MEDIAN_T0;
	private int medianT0FromNPoints = ExcelExportConstants.DEFAULT_MEDIAN_T0_POINTS;
	private boolean onlyalive = DefaultOptions.ONLY_ALIVE;

	// Output format options
	private boolean transpose = DefaultOptions.TRANSPOSE;
	private boolean duplicateSeries = DefaultOptions.DUPLICATE_SERIES;
	private int buildExcelStepMs = DefaultOptions.BUILD_EXCEL_STEP_MS;
	private int buildExcelUnitMs = DefaultOptions.BUILD_EXCEL_UNIT_MS;
	private boolean fixedIntervals = DefaultOptions.FIXED_INTERVALS;
	private long startAll_Ms = DefaultOptions.START_ALL_MS;
	private long endAll_Ms = DefaultOptions.END_ALL_MS;
	private boolean exportAllFiles = DefaultOptions.EXPORT_ALL_FILES;
	private boolean absoluteTime = DefaultOptions.ABSOLUTE_TIME;
	private boolean collateSeries = DefaultOptions.COLLATE_SERIES;
	private boolean padIntervals = DefaultOptions.PAD_INTERVALS;

	// Range options
	private int experimentIndexFirst = DefaultOptions.EXPERIMENT_INDEX_FIRST;
	private int experimentIndexLast = DefaultOptions.EXPERIMENT_INDEX_LAST;
	private int cageIndexFirst = DefaultOptions.CAGE_INDEX_FIRST;
	private int cageIndexLast = DefaultOptions.CAGE_INDEX_LAST;
	private int seriesIndexFirst = DefaultOptions.SERIES_INDEX_FIRST;
	private int seriesIndexLast = DefaultOptions.SERIES_INDEX_LAST;

	// External references
	private JComboBoxExperimentLazy expList = null;

	// Internal processing options
	private boolean trim_alive = DefaultOptions.TRIM_ALIVE;
	private boolean compensateEvaporation = DefaultOptions.COMPENSATE_EVAPORATION;
	private EnumResults resultType = null;

	/**
	 * Creates a new builder with default values.
	 */
	public XLSExportOptionsBuilder() {
		// All fields are initialized with defaults above
	}

	/**
	 * Creates a new builder based on existing options.
	 * 
	 * @param existing The existing options to copy from
	 */
	public XLSExportOptionsBuilder(ResultsOptions existing) {
		this.xyImage = existing.xyImage;
		this.xyCage = existing.xyCage;
		this.ellipseAxes = existing.ellipseAxes;

		this.distance = existing.distance;
		this.alive = existing.alive;
		this.sleep = existing.sleep;
		this.sleepThreshold = existing.sleepThreshold;

		this.spotAreas = existing.spotAreas;
		this.sum = existing.sum;
		this.sum2 = existing.sum2;
		this.nPixels = existing.nPixels;

		this.autocorrelation = existing.autocorrelation;
		this.crosscorrelation = existing.crosscorrelation;
		this.crosscorrelationLR = existing.crosscorrelationLR;
		this.nBinsCorrelation = existing.nBinsCorrelation;

		this.sumPerCage = existing.sumPerCage;
		this.subtractT0 = existing.subtractT0;
		this.divideWithT0 = existing.divideWithT0;
		this.relativeToMedianT0 = existing.relativeToMedianT0;
		this.medianT0FromNPoints = existing.medianT0FromNPoints;
		this.onlyalive = existing.onlyalive;

		this.transpose = existing.transpose;
		this.duplicateSeries = existing.duplicateSeries;
		this.buildExcelStepMs = existing.buildExcelStepMs;
		this.buildExcelUnitMs = existing.buildExcelUnitMs;
		this.fixedIntervals = existing.fixedIntervals;
		this.startAll_Ms = existing.startAll_Ms;
		this.endAll_Ms = existing.endAll_Ms;
		this.exportAllFiles = existing.exportAllFiles;
		this.absoluteTime = existing.absoluteTime;
		this.collateSeries = existing.collateSeries;
		this.padIntervals = existing.padIntervals;

		this.experimentIndexFirst = existing.experimentIndexFirst;
		this.experimentIndexLast = existing.experimentIndexLast;
		this.cageIndexFirst = existing.cageIndexFirst;
		this.cageIndexLast = existing.cageIndexLast;
		this.seriesIndexFirst = existing.seriesIndexFirst;
		this.seriesIndexLast = existing.seriesIndexLast;
		this.expList = existing.expList;

		this.trim_alive = existing.trim_alive;
		this.compensateEvaporation = existing.compensateEvaporation;
		this.resultType = existing.resultType;
	}

	// Fluent interface methods

	public XLSExportOptionsBuilder withXyImage(boolean xyImage) {
		this.xyImage = xyImage;
		return this;
	}

	public XLSExportOptionsBuilder withXyCage(boolean xyCage) {
		this.xyCage = xyCage;
		return this;
	}

	public XLSExportOptionsBuilder withEllipseAxes(boolean ellipseAxes) {
		this.ellipseAxes = ellipseAxes;
		return this;
	}

	public XLSExportOptionsBuilder withDistance(boolean distance) {
		this.distance = distance;
		return this;
	}

	public XLSExportOptionsBuilder withAlive(boolean alive) {
		this.alive = alive;
		return this;
	}

	public XLSExportOptionsBuilder withSleep(boolean sleep) {
		this.sleep = sleep;
		return this;
	}

	public XLSExportOptionsBuilder withSleepThreshold(int sleepThreshold) {
		this.sleepThreshold = sleepThreshold;
		return this;
	}

//	public XLSExportOptionsBuilder withTopLevel(boolean topLevel) {
//		this.topLevel = topLevel;
//		return this;
//	}

	public XLSExportOptionsBuilder withSubtractT0(boolean b) {
		this.subtractT0 = b;
		return null;
	}

	public XLSExportOptionsBuilder withDivivdeWithT0(boolean b) {
		this.divideWithT0 = b;
		return null;
	}

	public XLSExportOptionsBuilder withSpotAreas(boolean spotAreas) {
		this.spotAreas = spotAreas;
		return this;
	}

	public XLSExportOptionsBuilder withTranspose(boolean transpose) {
		this.transpose = transpose;
		return this;
	}

	public XLSExportOptionsBuilder withBuildExcelStepMs(int buildExcelStepMs) {
		this.buildExcelStepMs = buildExcelStepMs;
		return this;
	}

	public XLSExportOptionsBuilder withExperimentRange(int first, int last) {
		this.experimentIndexFirst = first;
		this.experimentIndexLast = last;
		return this;
	}

	public XLSExportOptionsBuilder withCageRange(int first, int last) {
		this.cageIndexFirst = first;
		this.cageIndexLast = last;
		return this;
	}

	public XLSExportOptionsBuilder withSeriesRange(int first, int last) {
		this.seriesIndexFirst = first;
		this.seriesIndexLast = last;
		return this;
	}

	public XLSExportOptionsBuilder withExperimentList(JComboBoxExperimentLazy expList) {
		this.expList = expList;
		return this;
	}

	public XLSExportOptionsBuilder withExportType(EnumResults resultType) {
		this.resultType = resultType;
		return this;
	}

	public XLSExportOptionsBuilder withOnlyAlive(boolean onlyalive) {
		this.onlyalive = onlyalive;
		return this;
	}

	public XLSExportOptionsBuilder withCollateSeries(boolean collateSeries) {
		this.collateSeries = collateSeries;
		return this;
	}

	public XLSExportOptionsBuilder withTimeRange(long startMs, long endMs) {
		this.startAll_Ms = startMs;
		this.endAll_Ms = endMs;
		return this;
	}

	public XLSExportOptionsBuilder withFixedIntervals(boolean fixedIntervals) {
		this.fixedIntervals = fixedIntervals;
		return this;
	}

	public XLSExportOptionsBuilder withRelativeToMaximum(boolean relativeToMax) {
		this.relativeToMaximum = relativeToMax;
		return this;
	}

	public XLSExportOptionsBuilder withPadIntervals(boolean padIntervals) {
		this.padIntervals = padIntervals;
		return this;
	}

	public XLSExportOptionsBuilder withSum(boolean sum) {
		this.sum = sum;
		return this;
	}

	public XLSExportOptionsBuilder withAbsoluteTime(boolean absoluteTime) {
		this.absoluteTime = absoluteTime;
		return this;
	}

	public XLSExportOptionsBuilder withExportAllFiles(boolean exportAllFiles) {
		this.exportAllFiles = exportAllFiles;
		return this;
	}

	public ResultsOptions withBuildExcelUnitMs(int buildExcelUnitMs) {
		this.buildExcelUnitMs = buildExcelUnitMs;
		return null;
	}

	/**
	 * Builds the XLSExportOptions instance with the configured values.
	 * 
	 * @return A new XLSExportOptions instance
	 */
	public ResultsOptions build() {
		ResultsOptions resultsOptions = new ResultsOptions();

		// Copy all configured values
		resultsOptions.xyImage = this.xyImage;
		resultsOptions.xyCage = this.xyCage;
		resultsOptions.ellipseAxes = this.ellipseAxes;

		resultsOptions.distance = this.distance;
		resultsOptions.alive = this.alive;
		resultsOptions.sleep = this.sleep;
		resultsOptions.sleepThreshold = this.sleepThreshold;

		resultsOptions.spotAreas = this.spotAreas;
		resultsOptions.sum = this.sum;
		resultsOptions.sum2 = this.sum2;
		resultsOptions.nPixels = this.nPixels;

		resultsOptions.autocorrelation = this.autocorrelation;
		resultsOptions.crosscorrelation = this.crosscorrelation;
		resultsOptions.crosscorrelationLR = this.crosscorrelationLR;
		resultsOptions.nBinsCorrelation = this.nBinsCorrelation;

		resultsOptions.sumPerCage = this.sumPerCage;
		resultsOptions.subtractT0 = this.subtractT0;
		resultsOptions.divideWithT0 = this.divideWithT0;
		resultsOptions.relativeToMaximum = this.relativeToMaximum;
		resultsOptions.relativeToMedianT0 = this.relativeToMedianT0;
		resultsOptions.medianT0FromNPoints = this.medianT0FromNPoints;
		resultsOptions.onlyalive = this.onlyalive;

		resultsOptions.transpose = this.transpose;
		resultsOptions.duplicateSeries = this.duplicateSeries;
		resultsOptions.buildExcelStepMs = this.buildExcelStepMs;
		resultsOptions.buildExcelUnitMs = this.buildExcelUnitMs;
		resultsOptions.fixedIntervals = this.fixedIntervals;
		resultsOptions.startAll_Ms = this.startAll_Ms;
		resultsOptions.endAll_Ms = this.endAll_Ms;
		resultsOptions.exportAllFiles = this.exportAllFiles;
		resultsOptions.absoluteTime = this.absoluteTime;
		resultsOptions.collateSeries = this.collateSeries;
		resultsOptions.padIntervals = this.padIntervals;

		resultsOptions.experimentIndexFirst = this.experimentIndexFirst;
		resultsOptions.experimentIndexLast = this.experimentIndexLast;
		resultsOptions.cageIndexFirst = this.cageIndexFirst;
		resultsOptions.cageIndexLast = this.cageIndexLast;
		resultsOptions.seriesIndexFirst = this.seriesIndexFirst;
		resultsOptions.seriesIndexLast = this.seriesIndexLast;
		resultsOptions.expList = this.expList;

		resultsOptions.trim_alive = this.trim_alive;
		resultsOptions.compensateEvaporation = this.compensateEvaporation;
		resultsOptions.resultType = this.resultType;

		return resultsOptions;
	}

	/**
	 * Creates a builder with commonly used settings for spot area export.
	 * 
	 * @return A pre-configured builder
	 */
	public static XLSExportOptionsBuilder forSpotAreas() {
		return new XLSExportOptionsBuilder().withSpotAreas(true).withCollateSeries(false).withPadIntervals(false)
				.withAbsoluteTime(false).withOnlyAlive(false);
	}

	/**
	 * Creates a builder with commonly used settings for chart export.
	 * 
	 * @return A pre-configured builder
	 */
	public static XLSExportOptionsBuilder forChart() {
		return new XLSExportOptionsBuilder().withTranspose(false).withCollateSeries(false).withAlive(false);
	}

	// Additional fluent interface methods can be added as needed

}