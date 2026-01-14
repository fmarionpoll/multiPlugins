package plugins.fmp.multiSPOTS96.series;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.file.xml.XMLPersistent;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS96.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

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
	public boolean doRegistration = false;
	public int referenceFrame = 0;
	public int fromFrame = 0;
	public int toFrame = -1;
	public boolean doCreateBinDir = false;

	public boolean loopRunning = false;

	public boolean detectTop = true;
	public boolean detectBottom = true;
	public int detectCage = -1;

	public boolean detectSelectedROIs = false;
	public ArrayList<Integer> selectedIndexes = null;
	public boolean detectAllSeries = true;
	public int seriesFirst = 0;
	public int seriesLast = 0;
	public boolean runBackwards = false;
	public boolean analyzePartOnly = false;

	public int spotThreshold = 35;
	public int detectLevel2Threshold = 35;
	public int threshold = -1;
	public int flyThreshold = 60;
	public int backgroundThreshold = 40;
	public int overlayThreshold = 0;
	public boolean compensateBackground = false;

	public ImageTransformEnums transform01 = ImageTransformEnums.R_RGB;
	public ImageTransformEnums transform02 = ImageTransformEnums.L1DIST_TO_1RSTCOL;
	public ImageTransformEnums overlayTransform = ImageTransformEnums.NONE;
	public ImageTransformEnums transformop = ImageTransformEnums.NONE;

	public boolean overlayIfGreater = true;
	public boolean spotThresholdUp = true;
	public boolean flyThresholdUp = true;
	public boolean btrackWhite = false;
	public boolean blimitLow = false;
	public boolean blimitUp = false;
	public boolean forceBuildBackground = false;
	public boolean detectFlies = true;
	public boolean backgroundSubstraction = false;
	public boolean buildDerivative = true;
	public boolean pass1 = true;
	public boolean pass2 = false;
	public boolean directionUp2 = true;
	public boolean concurrentDisplay = true;

	public Rectangle searchArea = new Rectangle();
	public int spanDiffTop = 3;
	public int spanDiff = 3;

	public int backgroundNFrames = 60;
	public int backgroundFirst = 0;

	public int thresholdDiff = 100;
	public int limitLow = 0;
	public int limitUp = 1;
	public int limitRatio = 4;
	public int jitter = 10;
	public int nFliesPresent = 1;

	public int videoChannel = 0;
	public int background_delta = 50;
	public int background_jitter = 1;
	public int spotRadius = 5;

	// Memory optimization options
	public int batchSize = 10; // Number of frames to process in each batch
	public int maxConcurrentTasks = 4; // Maximum number of concurrent processing tasks
	public boolean enableMemoryCleanup = true; // Enable explicit memory cleanup
	public boolean usePrimitiveArrays = true; // Use primitive arrays instead of Point objects
	public boolean enableGarbageCollection = true; // Force GC between batches
	public boolean enableMemoryProfiling = false;

	// -----------------------

	void copyTo(BuildSeriesOptions destination) {
		destination.detectTop = detectTop;
		destination.detectBottom = detectBottom;
		destination.transform01 = transform01;
		destination.spotThresholdUp = spotThresholdUp;
		destination.spotThreshold = spotThreshold;
		destination.detectAllSeries = detectAllSeries;

	}

	void copyFrom(BuildSeriesOptions destination) {
		detectTop = destination.detectTop;
		detectBottom = destination.detectBottom;
		transform01 = destination.transform01;
		spotThresholdUp = destination.spotThresholdUp;
		spotThreshold = destination.spotThreshold;
		detectAllSeries = destination.detectAllSeries;
	}

	public void copyParameters(BuildSeriesOptions det) {
		threshold = det.threshold;
		backgroundThreshold = det.backgroundThreshold;
		thresholdDiff = det.thresholdDiff;
		btrackWhite = det.btrackWhite;
		blimitLow = det.blimitLow;
		blimitUp = det.blimitUp;
		limitLow = det.limitLow;
		limitUp = det.limitUp;
		limitRatio = det.limitRatio;
		jitter = det.jitter;
		forceBuildBackground = det.forceBuildBackground;
		detectFlies = det.detectFlies;
		transformop = det.transformop;
		videoChannel = det.videoChannel;
		backgroundSubstraction = det.backgroundSubstraction;
		isFrameFixed = det.isFrameFixed;
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

			buildDerivative = XMLUtil.getElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
		}

		Element xmlVal = XMLUtil.getElement(node, "DetectFliesParameters");
		if (xmlVal != null) {
			threshold = XMLUtil.getElementIntValue(xmlVal, "threshold", -1);
			btrackWhite = XMLUtil.getElementBooleanValue(xmlVal, "btrackWhite", false);
			blimitLow = XMLUtil.getElementBooleanValue(xmlVal, "blimitLow", false);
			blimitUp = XMLUtil.getElementBooleanValue(xmlVal, "blimitUp", false);
			limitLow = XMLUtil.getElementIntValue(xmlVal, "limitLow", -1);
			limitUp = XMLUtil.getElementIntValue(xmlVal, "limitUp", -1);
			jitter = XMLUtil.getElementIntValue(xmlVal, "jitter", 10);
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

			XMLUtil.setElementBooleanValue(nodeMeta, "buildDerivative", buildDerivative);
		}

		Element xmlVal = XMLUtil.addElement(node, "DetectFliesParameters");
		if (xmlVal != null) {
			XMLUtil.setElementIntValue(xmlVal, "threshold", threshold);
			XMLUtil.setElementBooleanValue(xmlVal, "btrackWhite", btrackWhite);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitLow", blimitLow);
			XMLUtil.setElementBooleanValue(xmlVal, "blimitUp", blimitUp);
			XMLUtil.setElementIntValue(xmlVal, "limitLow", limitLow);
			XMLUtil.setElementIntValue(xmlVal, "limitUp", limitUp);
			XMLUtil.setElementIntValue(xmlVal, "jitter", jitter);
			if (transformop != null) {
				String transform1 = transformop.toString();
				XMLUtil.setElementValue(xmlVal, "transformOp", transform1);
			}
			XMLUtil.setAttributeIntValue(xmlVal, "videoChannel", videoChannel);
		}
		return true;
	}

}
