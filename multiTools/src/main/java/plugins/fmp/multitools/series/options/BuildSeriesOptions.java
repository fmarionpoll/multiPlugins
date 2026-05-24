package plugins.fmp.multitools.series.options;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.file.xml.XMLPersistent;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class BuildSeriesOptions implements XMLPersistent {
	public boolean isFrameFixed = false;
	public long t_Ms_First = 0;
	public long t_Ms_Last = 0;
	public long t_Ms_BinDuration = 1;

	public ArrayList<ROI2D> listROIStoBuildKymos = new ArrayList<ROI2D>();
	public JComboBoxExperimentLazy expList;

	public Rectangle parent0Rect = null;
	public String binSubDirectory = null;
	public int diskRadius = 5;
	/** When true (default), profile uses segment perpendicular to capillary; when false, horizontal line at each point. */
	public boolean profilePerpendicular = true;
	public boolean doRegistration = false;
	public int referenceFrame = 0;
	public int fromFrame = 0;
	public int toFrame = -1;
	public boolean doCreateBinDir = false;

	/**
	 * Set by kymograph builders: true when pre-flight rename detected at least one
	 * locked kymograph file in the current bin directory.
	 */
	public boolean kymoPreflightDetectedLockedFiles = false;

	public boolean loopRunning = false;

	public boolean detectTop = true;
	public boolean detectBottom = true;
	public int detectCage = -1;
	public boolean detectL = true;
	public boolean detectR = true;
	public boolean detectSelectedKymo = true;
	public int kymoFirst = 0;
	public int kymoLast = 0;
	public double detectGulpsThreshold_uL = .3;
	public static final int Z_INDEX_FILTERED_FOR_GULPS = 1;
	public ImageTransformEnums transformForGulps = ImageTransformEnums.XDIFFN;
	public boolean buildGulps = true;

	public GulpThresholdMethod thresholdMethod = GulpThresholdMethod.MEAN_PLUS_SD;
	public double thresholdSdMultiplier = 3.0;
	public GulpThresholdSmoothing thresholdSmoothing = GulpThresholdSmoothing.NONE;
	public int thresholdSmoothingWindow = 5;
	public double thresholdSmoothingAlpha = 0.3;

	public boolean detectSelectedROIs = false;
	public ArrayList<Integer> selectedIndexes = null;
	public boolean detectAllSeries = true;
	public int seriesFirst = 0;
	public int seriesLast = 0;
	public boolean runBackwards = false;
	public boolean analyzePartOnly = false;

	public int spotThreshold = 35;

	public int threshold = -1;
	public int flyThreshold = 60;
	/** Percent of spot ROI mask pixels (flyPresent counts) required to treat a bin as fly-occluded when reconstructing sumNoFly. */
	public double flyOccupancyPercentForSpotSumNoFly = 8.0;
	public int backgroundThreshold = 40;
	public int overlayThreshold = 0;
	public boolean compensateBackground = false;

	public ImageTransformEnums transform01 = ImageTransformEnums.R_RGB;
	public ImageTransformEnums transform02 = ImageTransformEnums.L1DIST_TO_1RSTCOL;
	public ImageTransformEnums overlayTransform = ImageTransformEnums.NONE;
	public ImageTransformEnums transformop = ImageTransformEnums.NONE;
	/** Applied to raw frame first (e.g. SUBTRACT_T0); NONE skips this step. */
	public ImageTransformEnums flyDetectBackgroundTransform = ImageTransformEnums.NONE;
	/** Applied after background step (e.g. G_RGB); NONE skips this step. */
	public ImageTransformEnums flyDetectSourceTransform = ImageTransformEnums.NONE;

	public boolean overlayIfGreater = true;
	public boolean spotThresholdUp = true;
	public boolean flyThresholdUp = true;
	public boolean btrackWhite = false;
	public boolean blimitLow = false;
	public boolean blimitUp = false;
	/** When true, reject blobs whose length/width exceeds {@link #limitRatio}. */
	public boolean blimitRatio = true;
	/** When true, reject blobs whose centroid moved more than {@link #jitter} px vs. previous frame. */
	public boolean bjitter = false;
	public boolean forceBuildBackground = false;
	public boolean detectFlies = true;
	/** When true, compute and persist Detect2 lighting phase (illumPhase). */
	public boolean detectIllumPhase = true;
	public boolean backgroundSubstraction = false;
	public boolean buildDerivative = true;
	public boolean pass1 = true;
	public boolean pass2 = false;
	/** When true, detect levels directly from cam images (no kymograph build/load). */
	public boolean sourceCamDirect = false;
	public boolean directionUp2 = true;
	public int detectLevel2Threshold = 35;
	public int jitter2 = 5;
	public boolean concurrentDisplay = true;

	public boolean directionUp1 = true;
	public int detectLevel1Threshold = 35;

	public Rectangle searchArea = new Rectangle();
	public int spanDiffTop = 3;

	public int backgroundNFrames = 60;
	public int backgroundFirst = 0;

	public int thresholdDiff = 100;
	public int limitLow = 0;
	public int limitUp = 1;
	public double limitRatio = 4.;
	public int jitter = 10;
	public int nFliesPresent = 1;
	/**
	 * When true, keep at most {@link #nFliesPresent} largest blobs per cage per frame. When false, keep all
	 * blobs that pass the other filters.
	 */
	public boolean blimitMaxBlobsPerCage = true;

	public int videoChannel = 0;
	public int background_delta = 50;
	public int background_jitter = 1;
	public int spotRadius = 5;

	// Detect2 dual-background switching
	public boolean dualBackground = false;
	public double rednessThreshold = 0.42;

	// Memory optimization options
	public int batchSize = 10; // Number of frames to process in each batch
	public int maxConcurrentTasks = 4; // Maximum number of concurrent processing tasks
	public boolean enableMemoryCleanup = true; // Enable explicit memory cleanup
	public boolean usePrimitiveArrays = true; // Use primitive arrays instead of Point objects
	public boolean enableGarbageCollection = true; // Force GC between batches
	public boolean enableMemoryProfiling = false;

	/** Backend to use for spot-level detection from camera images. */
	public SpotDetectionMode spotDetectionMode = SpotDetectionMode.AUTO;

	/** When true, try to use GPU-backed image transforms when available. */
	public boolean useGpuTransforms = false;

	/**
	 * V5 only: when true and {@link #transform01} is {@code RGB_DIFFS_LOCAL_MEAN}, spot metrics use a
	 * CPU path where each channel's local mean is restricted to the spot disk (no full-frame box filter).
	 */
	public boolean v5SpotLocalMeanRestrictedToRoi = false;

	/**
	 * V5 only: max distance (bins) from a fly-gated NaN for which a finite sample may be tested as a border spike;
	 * {@code 0} disables adaptive trim. Not a uniform dilation: only outliers vs. a local median are cleared.
	 */
	public int v5FlyNaNDilationBins = 1;

	/**
	 * V5 only: half-width (bins) of the window used to compute the local median grey (excluding the center bin
	 * and NaNs) when testing border spikes. Ignored when {@link #v5FlyNaNDilationBins} is {@code 0}.
	 */
	public int v5FlyNaNBorderMedianHalfWidth = 2;

	/**
	 * V5 only: when a finite bin lies within {@link #v5FlyNaNDilationBins} of a fly NaN, it is cleared only if
	 * {@code GREY_SUM_V5} is an <strong>upward</strong> outlier: strictly greater than the previous finite grey
	 * (if any) and greater than a local median × this ratio. Genuine downward steps (fly eating) are never cleared.
	 * Invalid values ({@code <= 1}) fall back to {@code 1.35}.
	 */
	public double v5FlyNaNBorderSpikeRatio = 1.35;

	/**
	 * V5 only: controls lookback for upward spike suppression before {@code GREY_SUM_CLEAN_V5}: the median baseline
	 * uses up to {@code 2 × halfWidth + 1} <strong>prior</strong> finite bins only (current bin excluded), so wide
	 * spikes are not mistaken for a raised local level. {@code 0} disables this pre-pass (running median only).
	 */
	public int v5GreySumCleanSpikeMedianHalfWidth = 5;

	/**
	 * V5 only: a finite bin strictly above the previous finite grey and strictly above {@code localMedian × this}
	 * is pulled down to that median before running median. Values {@code <= 1} disable the ratio test (and the
	 * pre-pass has no effect when combined with half-width {@code 0}).
	 */
	public double v5GreySumCleanSpikeRatio = 1.12;

	/**
	 * V5 only: number of upward spike passes (1–5). Multiple passes erode wider spike plateaus.
	 */
	public int v5GreySumCleanSpikePasses = 2;

	// Spot detection backend options
	/** When true, per-spot computations may run in parallel on the CPU. */
	public boolean enableSpotParallelism = false;
	/**
	 * Desired parallelism level for spot processing.
	 * 0 = use runtime default, 1 = force single-threaded, >1 = requested number of threads.
	 */
	public int spotParallelism = 0;

	/**
	 * Fraction in (0, 1] for sumNoFly reconstruction: a bin is fly-masked when flyPresent count
	 * {@code >= ceil(fraction * ROI mask pixel count)}.
	 */
	public double getFlyOccupancyFractionForSpotSumNoFly() {
		double p = flyOccupancyPercentForSpotSumNoFly;
		if (!Double.isFinite(p) || p <= 0)
			p = 8.0;
		if (p > 100)
			p = 100;
		return p / 100.0;
	}

	void copyTo(BuildSeriesOptions destination) {
		destination.detectTop = detectTop;
		destination.detectBottom = detectBottom;
		destination.transform01 = transform01;
		destination.spotThresholdUp = spotThresholdUp;
		destination.spotThreshold = spotThreshold;
		destination.detectAllSeries = detectAllSeries;
		destination.v5SpotLocalMeanRestrictedToRoi = v5SpotLocalMeanRestrictedToRoi;

	}

	public void copyFrom(BuildSeriesOptions destination) {
		detectTop = destination.detectTop;
		detectBottom = destination.detectBottom;
		transform01 = destination.transform01;
		spotThresholdUp = destination.spotThresholdUp;
		spotThreshold = destination.spotThreshold;
		detectAllSeries = destination.detectAllSeries;
		v5SpotLocalMeanRestrictedToRoi = destination.v5SpotLocalMeanRestrictedToRoi;
	}

	public void copyParameters(BuildSeriesOptions det) {
		threshold = det.threshold;
		backgroundThreshold = det.backgroundThreshold;
		thresholdDiff = det.thresholdDiff;
		btrackWhite = det.btrackWhite;
		blimitLow = det.blimitLow;
		blimitUp = det.blimitUp;
		blimitRatio = det.blimitRatio;
		bjitter = det.bjitter;
		limitLow = det.limitLow;
		limitUp = det.limitUp;
		limitRatio = det.limitRatio;
		jitter = det.jitter;
		nFliesPresent = det.nFliesPresent;
		blimitMaxBlobsPerCage = det.blimitMaxBlobsPerCage;
		forceBuildBackground = det.forceBuildBackground;
		detectFlies = det.detectFlies;
		transformop = det.transformop;
		videoChannel = det.videoChannel;
		backgroundSubstraction = det.backgroundSubstraction;
		isFrameFixed = det.isFrameFixed;
		v5GreySumCleanSpikeMedianHalfWidth = det.v5GreySumCleanSpikeMedianHalfWidth;
		v5GreySumCleanSpikeRatio = det.v5GreySumCleanSpikeRatio;
		v5GreySumCleanSpikePasses = det.v5GreySumCleanSpikePasses;
	}

	@Override
	public boolean loadFromXML(Node node) {
		final Node nodeMeta = XMLUtil.getElement(node, "LimitsOptions");
		if (nodeMeta != null) {
			detectTop = XMLUtil.getElementBooleanValue(nodeMeta, "detectTop", detectTop);
			detectBottom = XMLUtil.getElementBooleanValue(nodeMeta, "detectBottom", detectBottom);
			detectAllSeries = XMLUtil.getElementBooleanValue(nodeMeta, "detectAllImages", detectAllSeries);
			spotThresholdUp = XMLUtil.getElementBooleanValue(nodeMeta, "directionUp", spotThresholdUp);
			seriesFirst = XMLUtil.getElementIntValue(nodeMeta, "firstImage", seriesFirst);
			spotThreshold = XMLUtil.getElementIntValue(nodeMeta, "detectLevelThreshold", spotThreshold);
			transform01 = ImageTransformEnums
					.findByText(XMLUtil.getElementValue(nodeMeta, "Transform", transform01.toString()));
			v5SpotLocalMeanRestrictedToRoi = XMLUtil.getElementBooleanValue(nodeMeta,
					"v5SpotLocalMeanRestrictedToRoi", v5SpotLocalMeanRestrictedToRoi);
			v5FlyNaNDilationBins = XMLUtil.getElementIntValue(nodeMeta, "v5FlyNaNDilationBins", v5FlyNaNDilationBins);
			v5FlyNaNBorderMedianHalfWidth = XMLUtil.getElementIntValue(nodeMeta, "v5FlyNaNBorderMedianHalfWidth",
					v5FlyNaNBorderMedianHalfWidth);
			v5FlyNaNBorderSpikeRatio = XMLUtil.getElementDoubleValue(nodeMeta, "v5FlyNaNBorderSpikeRatio",
					v5FlyNaNBorderSpikeRatio);
			v5GreySumCleanSpikeMedianHalfWidth = XMLUtil.getElementIntValue(nodeMeta,
					"v5GreySumCleanSpikeMedianHalfWidth", v5GreySumCleanSpikeMedianHalfWidth);
			v5GreySumCleanSpikeRatio = XMLUtil.getElementDoubleValue(nodeMeta, "v5GreySumCleanSpikeRatio",
					v5GreySumCleanSpikeRatio);
			v5GreySumCleanSpikePasses = XMLUtil.getElementIntValue(nodeMeta, "v5GreySumCleanSpikePasses",
					v5GreySumCleanSpikePasses);

			buildDerivative = XMLUtil.getElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
			flyOccupancyPercentForSpotSumNoFly = XMLUtil.getElementDoubleValue(nodeMeta,
					"flyOccupancyPercentForSpotSumNoFly", flyOccupancyPercentForSpotSumNoFly);
		}

		Element xmlVal = XMLUtil.getElement(node, "DetectFliesParameters");
		if (xmlVal != null) {
			threshold = XMLUtil.getElementIntValue(xmlVal, "threshold", -1);
			btrackWhite = XMLUtil.getElementBooleanValue(xmlVal, "btrackWhite", false);
			blimitLow = XMLUtil.getElementBooleanValue(xmlVal, "blimitLow", false);
			blimitUp = XMLUtil.getElementBooleanValue(xmlVal, "blimitUp", false);
			blimitRatio = XMLUtil.getElementBooleanValue(xmlVal, "blimitRatio", true);
			bjitter = XMLUtil.getElementBooleanValue(xmlVal, "bjitter", false);
			limitLow = XMLUtil.getElementIntValue(xmlVal, "limitLow", -1);
			limitUp = XMLUtil.getElementIntValue(xmlVal, "limitUp", -1);
			limitRatio = XMLUtil.getElementDoubleValue(xmlVal, "limitRatio", limitRatio);
			jitter = XMLUtil.getElementIntValue(xmlVal, "jitter", 10);
			nFliesPresent = XMLUtil.getElementIntValue(xmlVal, "nFliesPresent", nFliesPresent);
			blimitMaxBlobsPerCage = XMLUtil.getElementBooleanValue(xmlVal, "blimitMaxBlobsPerCage",
					blimitMaxBlobsPerCage);
			String op1 = XMLUtil.getElementValue(xmlVal, "transformOp", null);
			transformop = ImageTransformEnums.findByText(op1);
			videoChannel = XMLUtil.getAttributeIntValue(xmlVal, "videoChannel", 0);
		}
		return true;
	}

	@Override
	public boolean saveToXML(Node node) {
		final Node nodeMeta = XMLUtil.setElement(node, "LimitsOptions");
		if (nodeMeta != null) {
			XMLUtil.setElementBooleanValue(nodeMeta, "detectTop", detectTop);
			XMLUtil.setElementBooleanValue(nodeMeta, "detectBottom", detectBottom);
			XMLUtil.setElementBooleanValue(nodeMeta, "detectAllImages", detectAllSeries);
			XMLUtil.setElementBooleanValue(nodeMeta, "directionUp", spotThresholdUp);
			XMLUtil.setElementIntValue(nodeMeta, "firstImage", seriesFirst);
			XMLUtil.setElementIntValue(nodeMeta, "detectLevelThreshold", spotThreshold);
			XMLUtil.setElementValue(nodeMeta, "Transform", transform01.toString());
			XMLUtil.setElementBooleanValue(nodeMeta, "v5SpotLocalMeanRestrictedToRoi", v5SpotLocalMeanRestrictedToRoi);
			XMLUtil.setElementIntValue(nodeMeta, "v5FlyNaNDilationBins", v5FlyNaNDilationBins);
			XMLUtil.setElementIntValue(nodeMeta, "v5FlyNaNBorderMedianHalfWidth", v5FlyNaNBorderMedianHalfWidth);
			XMLUtil.setElementDoubleValue(nodeMeta, "v5FlyNaNBorderSpikeRatio", v5FlyNaNBorderSpikeRatio);
			XMLUtil.setElementIntValue(nodeMeta, "v5GreySumCleanSpikeMedianHalfWidth", v5GreySumCleanSpikeMedianHalfWidth);
			XMLUtil.setElementDoubleValue(nodeMeta, "v5GreySumCleanSpikeRatio", v5GreySumCleanSpikeRatio);
			XMLUtil.setElementIntValue(nodeMeta, "v5GreySumCleanSpikePasses", v5GreySumCleanSpikePasses);

			XMLUtil.setElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
			XMLUtil.setElementDoubleValue(nodeMeta, "flyOccupancyPercentForSpotSumNoFly", flyOccupancyPercentForSpotSumNoFly);
		}

		Element xmlVal = XMLUtil.addElement(node, "DetectFliesParameters");
		if (xmlVal != null) {
			XMLUtil.setElementIntValue(xmlVal, "threshold", threshold);
			XMLUtil.setElementBooleanValue(xmlVal, "btrackWhite", btrackWhite);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitLow", blimitLow);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitUp", blimitUp);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitRatio", blimitRatio);
			XMLUtil.setElementBooleanValue(xmlVal, "bjitter", bjitter);
			XMLUtil.setElementIntValue(xmlVal, "limitLow", limitLow);
			XMLUtil.setElementIntValue(xmlVal, "limitUp", limitUp);
			XMLUtil.setElementDoubleValue(xmlVal, "limitRatio", limitRatio);
			XMLUtil.setElementIntValue(xmlVal, "jitter", jitter);
			XMLUtil.setElementIntValue(xmlVal, "nFliesPresent", nFliesPresent);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitMaxBlobsPerCage", blimitMaxBlobsPerCage);
			if (transformop != null) {
				String transform1 = transformop.toString();
				XMLUtil.setElementValue(xmlVal, "transformOp", transform1);
			}
			XMLUtil.setAttributeIntValue(xmlVal, "videoChannel", videoChannel);
		}
		return true;
	}

}
