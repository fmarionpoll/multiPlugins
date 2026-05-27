package plugins.fmp.multitools.tools.results;

import java.util.List;

import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public class ResultsOptions {
	public boolean xyImage = true;
	/** Export distance from fly center to food side (uses per-cage FoodSide). */
	public boolean yVsFood = true;

	public boolean ellipseAxes = false;

	public boolean distance = false;
	public boolean alive = true;
	public boolean sleep = true;
	public boolean illumPhase = false;
	public int sleepThreshold = 5;

	public boolean spotAreas = true;
	public boolean sum = true;
	public boolean sum2 = true;
	public boolean nPixels = true;
	/** Spot Excel export: AREA_SUMNOFLY. */
	public boolean spotSumNoFly = false;
	/** Spot Excel export: AREA_SUMCLEAN. */
	public boolean spotSumClean = false;
	/** Spot Excel export (V2 channel): AREA_SUM_V2. */
	public boolean sumV2 = false;
	/** Spot Excel export (V2 channel): AREA_SUMNOFLY_V2. */
	public boolean spotSumNoFlyV2 = false;
	/** Spot Excel export (V2 channel): AREA_SUMCLEAN_V2. */
	public boolean spotSumCleanV2 = false;
	/**
	 * Spot Excel export: aggregate spots by (stimulus, concentration) at cage level.
	 * When enabled, the export iterates cages × distinct (stim,conc) instead of cages × spots.
	 */
	public boolean spotAggregateByStimulusConc = false;

	/** Baseline window length (minutes) used to compute per-spot max reference. Default: 2. */
	public int spotBaselineWindowMinutes = 2;
	/**
	 * Optional baseline mode: scan bins from 0 upward and stop early when the running
	 * maximum has not changed for {@link #spotBaselineStableBins} consecutive bins.
	 */
	public boolean spotBaselineStopWhenStable = false;
	/** Number of consecutive bins without change to consider baseline max stable. */
	public int spotBaselineStableBins = 3;

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
	/**
	 * Excel export output grid step in ms
	 * ({@link plugins.fmp.multitools.experiment.timebase.MeasureTimebase#EXPORT_RESAMPLE_STEP}).
	 */
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

	/**
	 * Kymograph chart: which fraction trace to plot when {@link plugins.fmp.multitools.service.KymoAnalysisResult.SpotKymoSeries}
	 * includes diagnostic arrays. Ignored for non-fraction kymograph measures (e.g. {@link EnumResults#KYMO_ABS_DELTA}).
	 */
	public KymoFractionTraceMode kymoFractionTraceMode = KymoFractionTraceMode.FINAL;

	/**
	 * When charting {@link EnumResults#AGG_SUMCLEAN}, filled by {@link plugins.fmp.multitools.tools.chart.ChartCagesFrame}
	 * so each cage subplot uses the same (stimulus,conc) color index.
	 */
	public List<StimulusConcKey> spotAggregateGlobalKeyOrder = null;

	/** V4 / evaluation: how per-spot depletion is formed before summing into {@code AGG_SUMCLEAN}. */
	public AggSumCleanPolicy aggSumCleanPolicy = AggSumCleanPolicy.LEGACY;
	/**
	 * Minutes from t=0 excluded when scanning for baseline max (only used with
	 * {@link AggSumCleanPolicy#V4_BASELINE_PLUS}). If camera time is unavailable, interpreted as bins to skip.
	 */
	public int aggBaselineSkipMinutes = 0;
	/** Fly occupancy fraction above which depletion is forced to 0 for that bin ({@link AggSumCleanPolicy#V4_FLY_GUARD}). */
	public double aggFlyGuardMaxFraction = 0.2;
	/** Reference stimulus label for {@link AggSumCleanPolicy#V4_REF_STIM} (trimmed; empty disables correction). */
	public String aggRefStimulus = "";
	/** Reference concentration label for {@link AggSumCleanPolicy#V4_REF_STIM}. */
	public String aggRefConcentration = "";
	/**
	 * Native-bin window for temporal smoothing of {@link EnumResults#AGG_MEDIANREF} (centered mean
	 * over finite samples in the window). Use {@code 1} for no smoothing.
	 */
	public int aggMedianRefSmoothWindowBins = 5;

	/** Spot Excel export: {@code AREA_COUNT_V5}. */
	public boolean spotAreaCountV5 = false;
	/** Spot Excel export: {@code GREY_SUM_V5}. */
	public boolean spotGreySumV5 = false;
	/** Spot Excel export: {@code GREY_SUM_CLEAN_V5}. */
	public boolean spotGreySumCleanV5 = false;
	/** Spot Excel export: on-disk {@code AREA_COUNT_V6}. */
	public boolean spotAreaCountColor = false;
	/** Spot Excel export: on-disk {@code GREY_SUM_V6}. */
	public boolean spotGreySumColor = false;
	/** Spot Excel export: on-disk {@code GREY_SUM_CLEAN_V6}. */
	public boolean spotGreySumCleanColor = false;
	/** Spot Excel export: {@code KYMO_CHROMA_FRACT}. */
	public boolean spotKymoFract = false;
	/** Spot Excel export: {@code KYMO_CHROMA_ABS_DELTA}. */
	public boolean spotKymoAbsDelta = false;
	/** Spot Excel export: {@code KYMO_GREEN_HEIGHT}. */
	public boolean spotKymoGreenHeight = false;
	/** Spot Excel export: {@code KYMO_GREEN_HEIGHT_RATIO}. */
	public boolean spotKymoGreenHeightRatio = false;

	public void copy(ResultsOptions resultsOptions) {
		this.xyImage = resultsOptions.xyImage;
		this.yVsFood = resultsOptions.yVsFood;
		this.ellipseAxes = resultsOptions.ellipseAxes;

		this.distance = resultsOptions.distance;
		this.alive = resultsOptions.alive;
		this.sleep = resultsOptions.sleep;
		this.illumPhase = resultsOptions.illumPhase;
		this.sleepThreshold = resultsOptions.sleepThreshold;

		this.spotAreas = resultsOptions.spotAreas;
		this.sum = resultsOptions.sum;
		this.sum2 = resultsOptions.sum2;
		this.nPixels = resultsOptions.nPixels;
		this.spotSumNoFly = resultsOptions.spotSumNoFly;
		this.spotSumClean = resultsOptions.spotSumClean;
		this.sumV2 = resultsOptions.sumV2;
		this.spotSumNoFlyV2 = resultsOptions.spotSumNoFlyV2;
		this.spotSumCleanV2 = resultsOptions.spotSumCleanV2;
		this.spotAggregateByStimulusConc = resultsOptions.spotAggregateByStimulusConc;
		this.spotAreaCountV5 = resultsOptions.spotAreaCountV5;
		this.spotGreySumV5 = resultsOptions.spotGreySumV5;
		this.spotGreySumCleanV5 = resultsOptions.spotGreySumCleanV5;
		this.spotAreaCountColor = resultsOptions.spotAreaCountColor;
		this.spotGreySumColor = resultsOptions.spotGreySumColor;
		this.spotGreySumCleanColor = resultsOptions.spotGreySumCleanColor;
		this.spotKymoFract = resultsOptions.spotKymoFract;
		this.spotKymoAbsDelta = resultsOptions.spotKymoAbsDelta;
		this.spotKymoGreenHeight = resultsOptions.spotKymoGreenHeight;
		this.spotKymoGreenHeightRatio = resultsOptions.spotKymoGreenHeightRatio;
		this.spotBaselineWindowMinutes = resultsOptions.spotBaselineWindowMinutes;
		this.spotBaselineStopWhenStable = resultsOptions.spotBaselineStopWhenStable;
		this.spotBaselineStableBins = resultsOptions.spotBaselineStableBins;

		this.autocorrelation = resultsOptions.autocorrelation;
		this.crosscorrelation = resultsOptions.crosscorrelation;
		this.crosscorrelationLR = resultsOptions.crosscorrelationLR;
		this.nBinsCorrelation = resultsOptions.nBinsCorrelation;

		this.sumPerCage = resultsOptions.sumPerCage;
		this.subtractT0 = resultsOptions.subtractT0;
		this.divideWithT0 = resultsOptions.divideWithT0;
		this.relativeToMaximum = resultsOptions.relativeToMaximum;
		this.relativeToMedianT0 = resultsOptions.relativeToMedianT0;
		this.medianT0FromNPoints = resultsOptions.medianT0FromNPoints;
		this.onlyalive = resultsOptions.onlyalive;
		this.correctEvaporation = resultsOptions.correctEvaporation;

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
		this.kymoFractionTraceMode = resultsOptions.kymoFractionTraceMode;
		this.spotAggregateGlobalKeyOrder = resultsOptions.spotAggregateGlobalKeyOrder;

		this.aggSumCleanPolicy = resultsOptions.aggSumCleanPolicy;
		this.aggBaselineSkipMinutes = resultsOptions.aggBaselineSkipMinutes;
		this.aggFlyGuardMaxFraction = resultsOptions.aggFlyGuardMaxFraction;
		this.aggRefStimulus = resultsOptions.aggRefStimulus;
		this.aggRefConcentration = resultsOptions.aggRefConcentration;
		this.aggMedianRefSmoothWindowBins = resultsOptions.aggMedianRefSmoothWindowBins;
	}
}
