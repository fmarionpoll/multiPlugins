package plugins.fmp.multitools.series.options;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.file.xml.XMLPersistent;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.SpotThresholdColorSpace;

public class BuildSeriesOptions implements XMLPersistent {

	public enum GulpDetectionMethod {
		XDIFFN_REF("XDiffn vs ref (classic)"), TOPRAW_DY("topraw dY (v2)");

		private final String label;

		GulpDetectionMethod(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}

		public static GulpDetectionMethod fromXml(String value) {
			if (value == null || value.trim().isEmpty()) {
				return TOPRAW_DY;
			}
			String trimmed = value.trim();
			for (GulpDetectionMethod m : values()) {
				if (m.name().equals(trimmed) || m.label.equals(trimmed)) {
					return m;
				}
			}
			return TOPRAW_DY;
		}
	}

	public boolean isFrameFixed = false;
	public long t_Ms_First = 0;
	public long t_Ms_Last = 0;
	public long t_Ms_BinDuration = 1;
	/**
	 * Keep every Nth analysis-interval frame when building kymographs (1 = native).
	 */
	public int kymoDownsampleFactor = 1;

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
	/** Span for Diffn-family gulp transforms ({@code XDiffn}/{@code YDiffn}/…). Min useful value is 2. */
	public int spanDiffForGulps = 3;
	public boolean buildGulps = true;

	public GulpDetectionMethod gulpDetectionMethod = GulpDetectionMethod.TOPRAW_DY;
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

	/** When true, reject blobs whose center falls inside a spot ROI (reduces spot-consumption artifacts). */
	public boolean bexcludeSpotBlobs = false;

	/**
	 * When true, apply morphological close (dilate then erode) on the binary fly mask before
	 * connected-component extraction — helps merge flies split by a thin threshold gap.
	 */
	public boolean bmorphClose = false;

	/** Radius (iterations of 3×3 dilate/erode) for {@link #bmorphClose}; clamped to 1–5 at use. */
	public int morphCloseRadius = 1;

	/**
	 * Deprecated: previous-frame rectangle carry-forward piled up false positives under
	 * {@code t-(t-1)}. Kept for XML compatibility; detection no longer uses it.
	 */
	public boolean bcarryStillFlies = false;

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
	 * V6 color-distance spot detection: reference colors (RGB); distance computed in {@link #spotColorSpace}.
	 */
	public ArrayList<Color> spotColorReferenceList = new ArrayList<>();

	/** V6: 1 = L1, 2 = L2 (matches {@link plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions#colordistanceType}). */
	public int spotColorDistanceType = 1;

	/** V6: max color distance to treat a pixel as matching a reference (passed to threshold-colors transform). */
	public int spotColorDistanceMax = 10;

	/** V6: color space for distance to {@link #spotColorReferenceList}. */
	public SpotThresholdColorSpace spotColorSpace = SpotThresholdColorSpace.RGB;

	/**
	 * Optional V6 colors treated as background: a pixel matching {@link #spotColorReferenceList} is rejected if it is
	 * also within {@link #spotColorExcludeDistanceMax} of any color here (same metric as spot references).
	 */
	public ArrayList<Color> spotColorExcludeList = new ArrayList<>();

	/** V6: distance threshold for {@link #spotColorExcludeList}; {@code <= 0} disables exclusion. */
	public int spotColorExcludeDistanceMax = 0;

	/**
	 * V5 only: when using {@link ImageTransformEnums#RGB_DIFFS_LOCAL_MEAN}, restrict the local-mean neighborhood to
	 * the spot disk ROI (CPU path).
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
		if (spotColorReferenceList != null) {
			destination.spotColorReferenceList = new ArrayList<>(spotColorReferenceList);
		} else {
			destination.spotColorReferenceList = new ArrayList<>();
		}
		destination.spotColorDistanceType = spotColorDistanceType;
		destination.spotColorDistanceMax = spotColorDistanceMax;
		destination.spotColorSpace = spotColorSpace;
		if (spotColorExcludeList != null) {
			destination.spotColorExcludeList = new ArrayList<>(spotColorExcludeList);
		} else {
			destination.spotColorExcludeList = new ArrayList<>();
		}
		destination.spotColorExcludeDistanceMax = spotColorExcludeDistanceMax;
		destination.transform02 = transform02;
		destination.flyThreshold = flyThreshold;
		destination.flyThresholdUp = flyThresholdUp;

	}

	public void copyFrom(BuildSeriesOptions destination) {
		detectTop = destination.detectTop;
		detectBottom = destination.detectBottom;
		transform01 = destination.transform01;
		spotThresholdUp = destination.spotThresholdUp;
		spotThreshold = destination.spotThreshold;
		detectAllSeries = destination.detectAllSeries;
		v5SpotLocalMeanRestrictedToRoi = destination.v5SpotLocalMeanRestrictedToRoi;
		if (destination.spotColorReferenceList != null) {
			spotColorReferenceList = new ArrayList<>(destination.spotColorReferenceList);
		} else {
			spotColorReferenceList = new ArrayList<>();
		}
		spotColorDistanceType = destination.spotColorDistanceType;
		spotColorDistanceMax = destination.spotColorDistanceMax;
		spotColorSpace = destination.spotColorSpace;
		if (destination.spotColorExcludeList != null) {
			spotColorExcludeList = new ArrayList<>(destination.spotColorExcludeList);
		} else {
			spotColorExcludeList = new ArrayList<>();
		}
		spotColorExcludeDistanceMax = destination.spotColorExcludeDistanceMax;
		transform02 = destination.transform02;
		flyThreshold = destination.flyThreshold;
		flyThresholdUp = destination.flyThresholdUp;
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
		bexcludeSpotBlobs = det.bexcludeSpotBlobs;
		bmorphClose = det.bmorphClose;
		morphCloseRadius = det.morphCloseRadius;
		bcarryStillFlies = det.bcarryStillFlies;
		forceBuildBackground = det.forceBuildBackground;
		detectFlies = det.detectFlies;
		transformop = det.transformop;
		videoChannel = det.videoChannel;
		backgroundSubstraction = det.backgroundSubstraction;
		isFrameFixed = det.isFrameFixed;
		v5GreySumCleanSpikeMedianHalfWidth = det.v5GreySumCleanSpikeMedianHalfWidth;
		v5GreySumCleanSpikeRatio = det.v5GreySumCleanSpikeRatio;
		v5GreySumCleanSpikePasses = det.v5GreySumCleanSpikePasses;
		v5SpotLocalMeanRestrictedToRoi = det.v5SpotLocalMeanRestrictedToRoi;
		if (det.spotColorReferenceList != null) {
			spotColorReferenceList = new ArrayList<>(det.spotColorReferenceList);
		}
		spotColorDistanceType = det.spotColorDistanceType;
		spotColorDistanceMax = det.spotColorDistanceMax;
		spotColorSpace = det.spotColorSpace;
		if (det.spotColorExcludeList != null) {
			spotColorExcludeList = new ArrayList<>(det.spotColorExcludeList);
		}
		spotColorExcludeDistanceMax = det.spotColorExcludeDistanceMax;
		transform02 = det.transform02;
		flyThreshold = det.flyThreshold;
		flyThresholdUp = det.flyThresholdUp;
	}

	/**
	 * Loads only the {@code LimitsOptions} subtree from {@code parent} (child element or {@code parent} itself if it is
	 * already {@code LimitsOptions}). Used for standalone color-threshold preset files and by {@link #loadFromXML}.
	 *
	 * @return true if a limits node was found and read
	 */
	public boolean loadLimitsOptionsFromParentNode(Node parent) {
		if (parent == null) {
			return false;
		}
		Node nodeMeta = XMLUtil.getElement(parent, "LimitsOptions");
		if (nodeMeta == null && isElementNamed(parent, "LimitsOptions")) {
			nodeMeta = parent;
		}
		if (nodeMeta == null) {
			return false;
		}
		loadFromLimitsOptionsNode(nodeMeta);
		return true;
	}

	/**
	 * Writes the {@code LimitsOptions} subtree under {@code parent} without adding {@code DetectFliesParameters}.
	 */
	public boolean saveLimitsOptionsToParentNode(Node parent) {
		if (parent == null) {
			return false;
		}
		final Node nodeMeta = XMLUtil.setElement(parent, "LimitsOptions");
		if (nodeMeta == null) {
			return false;
		}
		saveToLimitsOptionsNode(nodeMeta);
		return true;
	}

	private static boolean isElementNamed(Node n, String localName) {
		if (n == null || localName == null) {
			return false;
		}
		String ln = n.getLocalName();
		if (ln != null) {
			return localName.equals(ln);
		}
		return localName.equals(n.getNodeName());
	}

	private void loadFromLimitsOptionsNode(Node nodeMeta) {
		detectTop = XMLUtil.getElementBooleanValue(nodeMeta, "detectTop", detectTop);
		detectBottom = XMLUtil.getElementBooleanValue(nodeMeta, "detectBottom", detectBottom);
		detectAllSeries = XMLUtil.getElementBooleanValue(nodeMeta, "detectAllImages", detectAllSeries);
		spotThresholdUp = XMLUtil.getElementBooleanValue(nodeMeta, "directionUp", spotThresholdUp);
		seriesFirst = XMLUtil.getElementIntValue(nodeMeta, "firstImage", seriesFirst);
		spotThreshold = XMLUtil.getElementIntValue(nodeMeta, "detectLevelThreshold", spotThreshold);
		transform01 = ImageTransformEnums
				.findByText(XMLUtil.getElementValue(nodeMeta, "Transform", transform01.toString()));
		v5SpotLocalMeanRestrictedToRoi = XMLUtil.getElementBooleanValue(nodeMeta, "v5SpotLocalMeanRestrictedToRoi",
				v5SpotLocalMeanRestrictedToRoi);
		v5FlyNaNDilationBins = XMLUtil.getElementIntValue(nodeMeta, "v5FlyNaNDilationBins", v5FlyNaNDilationBins);
		v5FlyNaNBorderMedianHalfWidth = XMLUtil.getElementIntValue(nodeMeta, "v5FlyNaNBorderMedianHalfWidth",
				v5FlyNaNBorderMedianHalfWidth);
		v5FlyNaNBorderSpikeRatio = XMLUtil.getElementDoubleValue(nodeMeta, "v5FlyNaNBorderSpikeRatio",
				v5FlyNaNBorderSpikeRatio);
		v5GreySumCleanSpikeMedianHalfWidth = XMLUtil.getElementIntValue(nodeMeta, "v5GreySumCleanSpikeMedianHalfWidth",
				v5GreySumCleanSpikeMedianHalfWidth);
		v5GreySumCleanSpikeRatio = XMLUtil.getElementDoubleValue(nodeMeta, "v5GreySumCleanSpikeRatio",
				v5GreySumCleanSpikeRatio);
		v5GreySumCleanSpikePasses = XMLUtil.getElementIntValue(nodeMeta, "v5GreySumCleanSpikePasses",
				v5GreySumCleanSpikePasses);

		String colorRefs = XMLUtil.getElementValue(nodeMeta, "spotColorReferences", null);
		spotColorReferenceList = decodeSpotColorList(colorRefs);
		spotColorDistanceMax = XMLUtil.getElementIntValue(nodeMeta, "spotColorDistanceMax", spotColorDistanceMax);
		spotColorDistanceType = XMLUtil.getElementIntValue(nodeMeta, "spotColorDistanceType", spotColorDistanceType);
		spotColorSpace = SpotThresholdColorSpace
				.fromXmlToken(XMLUtil.getElementValue(nodeMeta, "spotColorSpace", spotColorSpace.toXmlToken()));
		String exRefs = XMLUtil.getElementValue(nodeMeta, "spotColorExcludeReferences", null);
		spotColorExcludeList = decodeSpotColorList(exRefs);
		spotColorExcludeDistanceMax = XMLUtil.getElementIntValue(nodeMeta, "spotColorExcludeDistanceMax",
				spotColorExcludeDistanceMax);

		String tf2 = firstNonBlank(XMLUtil.getElementValue(nodeMeta, "colorFlyTransform2", null),
				XMLUtil.getElementValue(nodeMeta, "v6FlyTransform2", null));
		if (tf2 != null) {
			ImageTransformEnums t = ImageTransformEnums.findByText(tf2);
			if (t != null) {
				transform02 = t;
			}
		}
		if (XMLUtil.getElement(nodeMeta, "colorFlyThreshold") != null) {
			flyThreshold = XMLUtil.getElementIntValue(nodeMeta, "colorFlyThreshold", flyThreshold);
		} else if (XMLUtil.getElement(nodeMeta, "v6FlyThreshold") != null) {
			flyThreshold = XMLUtil.getElementIntValue(nodeMeta, "v6FlyThreshold", flyThreshold);
		}
		if (XMLUtil.getElement(nodeMeta, "colorFlyThresholdUp") != null) {
			flyThresholdUp = XMLUtil.getElementBooleanValue(nodeMeta, "colorFlyThresholdUp", flyThresholdUp);
		} else if (XMLUtil.getElement(nodeMeta, "v6FlyThresholdUp") != null) {
			flyThresholdUp = XMLUtil.getElementBooleanValue(nodeMeta, "v6FlyThresholdUp", flyThresholdUp);
		}

		buildDerivative = XMLUtil.getElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
		gulpDetectionMethod = GulpDetectionMethod
				.fromXml(XMLUtil.getElementValue(nodeMeta, "gulpDetectionMethod", null));
		pass1 = XMLUtil.getElementBooleanValue(nodeMeta, "pass1", pass1);
		pass2 = XMLUtil.getElementBooleanValue(nodeMeta, "pass2", pass2);
		directionUp1 = XMLUtil.getElementBooleanValue(nodeMeta, "directionUp1", directionUp1);
		directionUp2 = XMLUtil.getElementBooleanValue(nodeMeta, "directionUp2", directionUp2);
		detectLevel1Threshold = XMLUtil.getElementIntValue(nodeMeta, "detectLevel1Threshold", detectLevel1Threshold);
		detectLevel2Threshold = XMLUtil.getElementIntValue(nodeMeta, "detectLevel2Threshold", detectLevel2Threshold);
		jitter2 = XMLUtil.getElementIntValue(nodeMeta, "jitter2", jitter2);
		sourceCamDirect = XMLUtil.getElementBooleanValue(nodeMeta, "sourceCamDirect", sourceCamDirect);
		String gulpTransform = XMLUtil.getElementValue(nodeMeta, "transformForGulps", null);
		if (gulpTransform != null) {
			ImageTransformEnums t = ImageTransformEnums.findByText(gulpTransform);
			if (t != null) {
				transformForGulps = t;
			}
		}
		spanDiffForGulps = XMLUtil.getElementIntValue(nodeMeta, "spanDiffForGulps", spanDiffForGulps);
		String thMethod = XMLUtil.getElementValue(nodeMeta, "thresholdMethod", null);
		if (thMethod != null && !thMethod.isEmpty()) {
			for (GulpThresholdMethod m : GulpThresholdMethod.values()) {
				if (m.name().equals(thMethod.trim())) {
					thresholdMethod = m;
					break;
				}
			}
		}
		thresholdSdMultiplier = XMLUtil.getElementDoubleValue(nodeMeta, "thresholdSdMultiplier", thresholdSdMultiplier);
		detectGulpsThreshold_uL = XMLUtil.getElementDoubleValue(nodeMeta, "detectGulpsThreshold_uL",
				detectGulpsThreshold_uL);
		String flySrc = XMLUtil.getElementValue(nodeMeta, "flyDetectSourceTransform", null);
		if (flySrc != null) {
			ImageTransformEnums t = ImageTransformEnums.findByText(flySrc);
			if (t != null) {
				flyDetectSourceTransform = t;
			}
		}
		String flyBkg = XMLUtil.getElementValue(nodeMeta, "flyDetectBackgroundTransform", null);
		if (flyBkg != null) {
			ImageTransformEnums t = ImageTransformEnums.findByText(flyBkg);
			if (t != null) {
				flyDetectBackgroundTransform = t;
			}
		}
		flyOccupancyPercentForSpotSumNoFly = XMLUtil.getElementDoubleValue(nodeMeta,
				"flyOccupancyPercentForSpotSumNoFly", flyOccupancyPercentForSpotSumNoFly);
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.trim().isEmpty()) {
			return a.trim();
		}
		if (b != null && !b.trim().isEmpty()) {
			return b.trim();
		}
		return null;
	}

	private void saveToLimitsOptionsNode(Node nodeMeta) {
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

		XMLUtil.setElementValue(nodeMeta, "spotColorReferences", encodeSpotColorList(spotColorReferenceList));
		XMLUtil.setElementIntValue(nodeMeta, "spotColorDistanceMax", spotColorDistanceMax);
		XMLUtil.setElementIntValue(nodeMeta, "spotColorDistanceType", spotColorDistanceType);
		XMLUtil.setElementValue(nodeMeta, "spotColorSpace", spotColorSpace.toXmlToken());
		XMLUtil.setElementValue(nodeMeta, "spotColorExcludeReferences", encodeSpotColorList(spotColorExcludeList));
		XMLUtil.setElementIntValue(nodeMeta, "spotColorExcludeDistanceMax", spotColorExcludeDistanceMax);

		XMLUtil.setElementValue(nodeMeta, "colorFlyTransform2", transform02 != null ? transform02.toString() : "");
		XMLUtil.setElementIntValue(nodeMeta, "colorFlyThreshold", flyThreshold);
		XMLUtil.setElementBooleanValue(nodeMeta, "colorFlyThresholdUp", flyThresholdUp);

		XMLUtil.setElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
		XMLUtil.setElementValue(nodeMeta, "gulpDetectionMethod",
				gulpDetectionMethod != null ? gulpDetectionMethod.name() : GulpDetectionMethod.TOPRAW_DY.name());
		XMLUtil.setElementBooleanValue(nodeMeta, "pass1", pass1);
		XMLUtil.setElementBooleanValue(nodeMeta, "pass2", pass2);
		XMLUtil.setElementBooleanValue(nodeMeta, "directionUp1", directionUp1);
		XMLUtil.setElementBooleanValue(nodeMeta, "directionUp2", directionUp2);
		XMLUtil.setElementIntValue(nodeMeta, "detectLevel1Threshold", detectLevel1Threshold);
		XMLUtil.setElementIntValue(nodeMeta, "detectLevel2Threshold", detectLevel2Threshold);
		XMLUtil.setElementIntValue(nodeMeta, "jitter2", jitter2);
		XMLUtil.setElementBooleanValue(nodeMeta, "sourceCamDirect", sourceCamDirect);
		XMLUtil.setElementValue(nodeMeta, "transformForGulps",
				transformForGulps != null ? transformForGulps.toString() : "");
		XMLUtil.setElementIntValue(nodeMeta, "spanDiffForGulps", spanDiffForGulps);
		XMLUtil.setElementValue(nodeMeta, "thresholdMethod",
				thresholdMethod != null ? thresholdMethod.name() : GulpThresholdMethod.MEAN_PLUS_SD.name());
		XMLUtil.setElementDoubleValue(nodeMeta, "thresholdSdMultiplier", thresholdSdMultiplier);
		XMLUtil.setElementDoubleValue(nodeMeta, "detectGulpsThreshold_uL", detectGulpsThreshold_uL);
		XMLUtil.setElementValue(nodeMeta, "flyDetectSourceTransform",
				flyDetectSourceTransform != null ? flyDetectSourceTransform.toString() : "");
		XMLUtil.setElementValue(nodeMeta, "flyDetectBackgroundTransform",
				flyDetectBackgroundTransform != null ? flyDetectBackgroundTransform.toString() : "");
		XMLUtil.setElementDoubleValue(nodeMeta, "flyOccupancyPercentForSpotSumNoFly", flyOccupancyPercentForSpotSumNoFly);
	}

	@Override
	public boolean loadFromXML(Node node) {
		final Node nodeMeta = XMLUtil.getElement(node, "LimitsOptions");
		if (nodeMeta != null) {
			loadFromLimitsOptionsNode(nodeMeta);
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
			bcarryStillFlies = XMLUtil.getElementBooleanValue(xmlVal, "bcarryStillFlies", bcarryStillFlies);
			bmorphClose = XMLUtil.getElementBooleanValue(xmlVal, "bmorphClose", bmorphClose);
			morphCloseRadius = XMLUtil.getElementIntValue(xmlVal, "morphCloseRadius", morphCloseRadius);
			String op1 = XMLUtil.getElementValue(xmlVal, "transformOp", null);
			transformop = ImageTransformEnums.findByText(op1);
			thresholdDiff = XMLUtil.getElementIntValue(xmlVal, "thresholdDiff", thresholdDiff);
			dualBackground = XMLUtil.getElementBooleanValue(xmlVal, "dualBackground", dualBackground);
			rednessThreshold = XMLUtil.getElementDoubleValue(xmlVal, "rednessThreshold", rednessThreshold);
			backgroundNFrames = XMLUtil.getElementIntValue(xmlVal, "backgroundNFrames", backgroundNFrames);
			backgroundFirst = XMLUtil.getElementIntValue(xmlVal, "backgroundFirst", backgroundFirst);
			backgroundThreshold = XMLUtil.getElementIntValue(xmlVal, "backgroundThreshold", backgroundThreshold);
			videoChannel = XMLUtil.getAttributeIntValue(xmlVal, "videoChannel", 0);
		}
		return true;
	}

	@Override
	public boolean saveToXML(Node node) {
		final Node nodeMeta = XMLUtil.setElement(node, "LimitsOptions");
		if (nodeMeta != null) {
			saveToLimitsOptionsNode(nodeMeta);
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
			XMLUtil.setElementBooleanValue(xmlVal, "bcarryStillFlies", bcarryStillFlies);
			XMLUtil.setElementBooleanValue(xmlVal, "bmorphClose", bmorphClose);
			XMLUtil.setElementIntValue(xmlVal, "morphCloseRadius", morphCloseRadius);
			if (transformop != null) {
				String transform1 = transformop.toString();
				XMLUtil.setElementValue(xmlVal, "transformOp", transform1);
			}
			XMLUtil.setElementIntValue(xmlVal, "thresholdDiff", thresholdDiff);
			XMLUtil.setElementBooleanValue(xmlVal, "dualBackground", dualBackground);
			XMLUtil.setElementDoubleValue(xmlVal, "rednessThreshold", rednessThreshold);
			XMLUtil.setElementIntValue(xmlVal, "backgroundNFrames", backgroundNFrames);
			XMLUtil.setElementIntValue(xmlVal, "backgroundFirst", backgroundFirst);
			XMLUtil.setElementIntValue(xmlVal, "backgroundThreshold", backgroundThreshold);
			XMLUtil.setAttributeIntValue(xmlVal, "videoChannel", videoChannel);
		}
		return true;
	}

	private static String encodeSpotColorList(ArrayList<Color> list) {
		if (list == null || list.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append('|');
			}
			Color c = list.get(i);
			sb.append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue());
		}
		return sb.toString();
	}

	private static ArrayList<Color> decodeSpotColorList(String s) {
		ArrayList<Color> out = new ArrayList<>();
		if (s == null) {
			return out;
		}
		String t = s.trim();
		if (t.isEmpty()) {
			return out;
		}
		for (String part : t.split("\\|")) {
			String p = part.trim();
			if (p.isEmpty()) {
				continue;
			}
			String norm = p.replace(':', ',').replace(';', ',');
			String[] rgb = norm.indexOf(',') >= 0 ? norm.split(",") : p.split("\\s+");
			if (rgb.length >= 3) {
				try {
					int r = Math.max(0, Math.min(255, Integer.parseInt(rgb[0].trim())));
					int g = Math.max(0, Math.min(255, Integer.parseInt(rgb[1].trim())));
					int b = Math.max(0, Math.min(255, Integer.parseInt(rgb[2].trim())));
					out.add(new Color(r, g, b));
				} catch (@SuppressWarnings("unused") NumberFormatException e) {
					// skip malformed token
				}
			}
		}
		return out;
	}

}
