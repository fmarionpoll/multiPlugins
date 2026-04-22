package plugins.fmp.multitools.experiment.spots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.sequence.ROIOperation;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.kernel.roi.roi2d.ROI2DShape;

/**
 * Manages a collection of spots with comprehensive operations and data
 * persistence.
 * 
 * <p>
 * This class provides thread-safe operations for managing spots collections
 * with clean separation of concerns for loading, saving, and processing
 * operations.
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class Spots {

	// === CONSTANTS ===
	private static final String ID_SPOTTRACK = "spotTrack";
	private static final String ID_NSPOTS = "N_spots";
	private static final String ID_LISTOFSPOTS = "List_of_spots";
	private static final String ID_SPOT_ = "spot_";
	private static final int DEFAULT_VERSION = 2;

	// === CORE FIELDS ===
	private final List<Spot> spotList;
	private SpotsPersistence persistence = new SpotsPersistence();

	// === CONSTRUCTORS ===

	public Spots() {
		this.spotList = new ArrayList<>();
	}

	// === PERSISTENCE ===

	public SpotsPersistence getPersistence() {
		return persistence;
	}

	// === SPOTS MANAGEMENT ===

	public List<Spot> getSpotList() {
		return spotList;
	}

	public int getSpotListCount() {
		return spotList.size();
	}

	public boolean isSpotListEmpty() {
		return spotList.isEmpty();
	}

	public void addSpot(Spot spot) {
		Objects.requireNonNull(spot, "Spot cannot be null");
		spotList.add(spot);
	}

	/**
	 * Gets the next unique spot ID for assigning to new spots.
	 * <p>
	 * IDs are computed from the current maximum existing ID (not from list size) so
	 * they remain unique even when spots are deleted and the list shrinks.
	 * 
	 * @return the next unique spot ID
	 */
	public int getNextUniqueSpotID() {
		int maxID = -1;
		for (int i = 0; i < spotList.size(); i++) {
			Spot spot = spotList.get(i);
			SpotID spotID = spot != null ? spot.getSpotUniqueID() : null;
			int usedID = (spotID != null) ? spotID.getId() : i;
			if (usedID > maxID) {
				maxID = usedID;
			}
		}
		return maxID + 1;
	}

	public boolean removeSpot(Spot spot) {
		return spotList.remove(spot);
	}

	public void clearSpotList() {
		spotList.clear();
	}

	public void sortSpotList() {
		Collections.sort(spotList);
	}

	// === SPOT SEARCH ===

	public Spot findSpotByName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return null;
		}

		return spotList.stream().filter(spot -> name.equals(spot.getName())).findFirst().orElse(null);
	}

	public List<Spot> findSpotsContainingPattern(String pattern) {
		if (pattern == null || pattern.trim().isEmpty()) {
			return new ArrayList<>();
		}

		return spotList.stream().filter(spot -> spot.getName() != null && spot.getName().contains(pattern))
				.collect(Collectors.toList());
	}

	public boolean isSpotPresent(Spot newSpot) {
		if (newSpot == null)
			return false;
		String newSpotName = newSpot.getName();
		for (Spot spot : spotList) {
			if (spot.getName().equals(newSpotName))
				return true;
		}
		return false;
	}

	public Spot findSpotwithID(SpotID spotID) {
		if (spotID == null) {
			return null;
		}

		return spotList.stream().filter(spot -> spot.getSpotUniqueID() != null && spot.getSpotUniqueID().equals(spotID))
				.findFirst().orElse(null);
	}

	// === DATA LOADING ===
	// New standardized method names (v2.3.3+)

	/**
	 * Loads spot descriptions from the results directory. Descriptions include spot
	 * properties but not time-series measures.
	 * 
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadDescriptions(String resultsDirectory) {
		return persistence.loadDescriptions(this, resultsDirectory);
	}

	/**
	 * Loads spot measures from the bin directory (e.g., results/bin60). Measures
	 * include time-series data like area_sum, area_clean, flypresent.
	 * 
	 * @param binDirectory the bin directory
	 * @return true if successful
	 */
	public boolean loadMeasures(String binDirectory) {
		return persistence.loadMeasures(this, binDirectory);
	}

	/**
	 * Checks if spots description files exist in the results directory. Useful to
	 * determine if an experiment has spots data.
	 * 
	 * @param resultsDirectory the results directory
	 * @return true if spots description files exist
	 */
	public boolean hasSpotsFiles(String resultsDirectory) {
		return persistence.hasSpotsDescriptionFiles(resultsDirectory);
	}

	// === DATA SAVING ===
	// New standardized method names (v2.3.3+)

	/**
	 * Saves spot descriptions to the results directory. Descriptions include spot
	 * properties but not time-series measures.
	 * 
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveDescriptions(String resultsDirectory) {
		return persistence.saveDescriptions(this, resultsDirectory);
	}

	/**
	 * Saves spot measures to the bin directory (e.g., results/bin60). Measures
	 * include time-series data like area_sum, area_clean, flypresent.
	 * 
	 * @param binDirectory the bin directory
	 * @return true if successful
	 */
	public boolean saveMeasures(String binDirectory) {
		return persistence.saveMeasures(this, binDirectory);
	}

	/**
	 * Marks spot measures as invalid (NaN) at every time index where the camera
	 * frame was dark (lightStatusPerFrame[t] == 0). Applies to sum, clean and
	 * fly-present measures.
	 */
	public void clearMeasuresAtDarkFrames(int[] lightStatusPerFrame) {
		if (lightStatusPerFrame == null || lightStatusPerFrame.length == 0)
			return;
		for (Spot spot : spotList) {
			if (spot == null)
				continue;
			clearSpotMeasureAtDarkFrames(spot.getSum(), lightStatusPerFrame);
			clearSpotMeasureAtDarkFrames(spot.getSumClean(), lightStatusPerFrame);
			clearSpotMeasureAtDarkFrames(spot.getFlyPresent(), lightStatusPerFrame);
		}
	}

	private static void clearSpotMeasureAtDarkFrames(SpotMeasure measure, int[] lightStatusPerFrame) {
		if (measure == null)
			return;
		double[] values = measure.getValues();
		int[] isPresent = measure.getIsPresent();
		if (values == null || values.length == 0)
			return;
		boolean changedValues = false;
		boolean changedPresence = false;
		int n = values.length;
		for (int t = 0; t < lightStatusPerFrame.length && t < n; t++) {
			if (lightStatusPerFrame[t] == 0) {
				values[t] = Double.NaN;
				changedValues = true;
				if (isPresent != null && t < isPresent.length) {
					isPresent[t] = 0;
					changedPresence = true;
				}
			}
		}
		if (changedValues)
			measure.setValues(values);
		if (changedPresence)
			measure.setIsPresent(isPresent);
	}

	// === DEPRECATED METHODS ===
	// Old method names kept for backward compatibility (will be removed in v3.0)

//	/**
//	 * @deprecated Use {@link #loadMeasures(String)} instead.
//	 */
//	@Deprecated
//	public boolean loadSpotsMeasures(String directory) {
//		return loadMeasures(directory);
//	}

//	/**
//	 * @deprecated Use {@link #loadDescriptions(String)} instead.
//	 */
//	@Deprecated
//	public boolean loadSpotsAll(String directory) {
//		return loadDescriptions(directory);
//	}

//	/**
//	 * @deprecated Use {@link #saveDescriptions(String)} instead.
//	 */
//	@Deprecated
//	public boolean saveSpotsAll(String directory) {
//		return saveDescriptions(directory);
//	}

//	/**
//	 * @deprecated Use {@link #saveMeasures(String)} instead.
//	 */
//	@Deprecated
//	public boolean saveSpotsMeasures(String directory) {
//		return saveMeasures(directory);
//	}

//	/**
//	 * @deprecated Use {@link #saveMeasures(String)} instead.
//	 */
//	@Deprecated
//	public boolean saveSpotsMeasuresOptimized(String directory) {
//		return saveMeasures(directory);
//	}

	// === XML OPERATIONS ===
	// NOTE: XML operations are deprecated. Spots now use CSV-only persistence.
	// These methods are kept for backward compatibility during migration.

	/**
	 * Saves spots array to XML.
	 * 
	 * @deprecated Spots now use CSV-only persistence. ROIs are regenerated from
	 *             coordinates. This method is kept for backward compatibility
	 *             during migration.
	 * 
	 * @param node the XML node
	 * @return true if successful
	 */
	@Deprecated
	public boolean saveToXml(Node node) {
		if (node == null) {
			System.err.println("ERROR: Null node provided for SpotsArray save");
			return false;
		}
		try {
			Node nodeSpotsArray = XMLUtil.setElement(node, ID_LISTOFSPOTS);
			if (nodeSpotsArray == null) {
				System.err.println("ERROR: Could not create List_of_spots element");
				return false;
			}

			XMLUtil.setElementIntValue(nodeSpotsArray, ID_NSPOTS, spotList.size());

			sortSpotList();

			// If no spots, successfully save empty array
			if (spotList.isEmpty()) {
				return true;
			}

			int savedSpots = 0;
			for (int i = 0; i < spotList.size(); i++) {
				try {
					Node nodeSpot = XMLUtil.setElement(node, ID_SPOT_ + i);
					if (nodeSpot == null) {
						System.err.println("ERROR: Could not create spot element for index " + i);
						continue;
					}

					Spot spot = spotList.get(i);
					if (spot == null) {
						System.err.println("WARNING: Null spot at index " + i);
						continue;
					}

					boolean spotSuccess = spot.saveToXml(nodeSpot);
					if (spotSuccess) {
						savedSpots++;
					} else {
						System.err.println("ERROR: Failed to save spot at index " + i);
					}
				} catch (Exception e) {
					System.err.println("ERROR saving spot at index " + i + ": " + e.getMessage());
				}
			}
			return savedSpots > 0; // Return true if at least one spot was saved

		} catch (Exception e) {
			System.err.println("ERROR during SpotsArray save: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Loads spots array from XML.
	 * 
	 * @deprecated Spots now use CSV-only persistence. This method is kept for
	 *             backward compatibility during migration.
	 * 
	 * @param node the XML node
	 * @return true if successful
	 */
	@Deprecated
	public boolean loadFromXml(Node node) {
		if (node == null) {
			System.err.println("ERROR: Null node provided for SpotsArray load");
			return false;
		}
		try {
			Node nodeSpotsArray = XMLUtil.getElement(node, ID_LISTOFSPOTS);
			if (nodeSpotsArray == null) {
				System.err.println("ERROR: Could not find List_of_spots element");
				return false;
			}

			int nitems = XMLUtil.getElementIntValue(nodeSpotsArray, ID_NSPOTS, 0);
			if (nitems < 0) {
				System.err.println("ERROR: Invalid number of spots: " + nitems);
				return false;
			}

			spotList.clear();

			int loadedSpots = 0;
			for (int i = 0; i < nitems; i++) {
				try {
					Node nodeSpot = XMLUtil.getElement(node, ID_SPOT_ + i);
					if (nodeSpot == null) {
						System.err.println("WARNING: Could not find spot element for index " + i);
						continue;
					}

					Spot spot = new Spot();
					boolean spotSuccess = spot.loadFromXml(nodeSpot);
					if (spotSuccess && !isSpotPresent(spot)) {
						// Assign unique ID if not loaded from XML (legacy files)
						if (spot.getSpotUniqueID() == null) {
							int uniqueID = getNextUniqueSpotID();
							spot.setSpotUniqueID(new SpotID(uniqueID));
						}
						spotList.add(spot);
						loadedSpots++;
					} else if (!spotSuccess) {
						Logger.error("Failed to load spot at index " + i);
					} else {
						Logger.warn(" Skipped duplicate spot at index " + i);
					}
				} catch (Exception e) {
					System.err.println("ERROR loading spot at index " + i + ": " + e.getMessage());
				}
			}
			return loadedSpots > 0; // Return true if at least one spot was loaded

		} catch (Exception e) {
			System.err.println("ERROR during SpotsArray load: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	@Deprecated
	public boolean saveDescriptorsToXml(String fileName) {
		if (fileName == null) {
			return false;
		}

		try {
			Document doc = XMLUtil.createDocument(true);
			if (doc == null) {
				return false;
			}

			saveListOfSpotsToXml(XMLUtil.getRootElement(doc));
			return XMLUtil.saveDocument(doc, fileName);
		} catch (Exception e) {
			System.err.println("Error saving descriptors to XML: " + e.getMessage());
			return false;
		}
	}

	@Deprecated
	public boolean loadDescriptorsFromXml(String fileName) {
		if (fileName == null) {
			return false;
		}

		try {
			Document doc = XMLUtil.loadDocument(fileName);
			if (doc == null) {
				return false;
			}

			return loadSpotsOnlyV1(doc);
		} catch (Exception e) {
			System.err.println("Error loading descriptors from XML: " + e.getMessage());
			return false;
		}
	}

	// === COPY OPERATIONS ===

	public void copySpotsInfo(Spots sourceArray) {
		copySpots(sourceArray, false);
	}

	public void copySpots(Spots sourceArray, boolean includeMeasurements) {
		if (sourceArray == null) {
			return;
		}

		spotList.clear();
		for (Spot sourceSpot : sourceArray.getSpotList()) {
			Spot spot = new Spot(sourceSpot, includeMeasurements);
			spotList.add(spot);
		}
	}

	public void pasteSpotsInfo(Spots targetArray) {
		pasteSpots(targetArray, false);
	}

	public void pasteSpots(Spots targetArray, boolean includeMeasurements) {
		if (targetArray == null) {
			return;
		}

		for (Spot targetSpot : targetArray.getSpotList()) {
			for (Spot sourceSpot : spotList) {
				if (sourceSpot.compareTo(targetSpot) == 0) {
					targetSpot.copyFrom(sourceSpot, includeMeasurements);
					break;
				}
			}
		}
	}

	public void mergeSpots(Spots sourceArray) {
		if (sourceArray == null) {
			return;
		}

		for (Spot sourceSpot : sourceArray.getSpotList()) {
			if (!isSpotPresent(sourceSpot)) {
				spotList.add(sourceSpot);
			}
		}
	}

	// === LEVEL2D OPERATIONS ===

	public void adjustSpotsLevel2DMeasuresToImageWidth(int imageWidth) {
		spotList.forEach(spot -> spot.adjustLevel2DMeasuresToImageWidth(imageWidth));
	}

	public void cropSpotsLevel2DMeasuresToImageWidth(int imageWidth) {
		spotList.forEach(spot -> spot.cropLevel2DMeasuresToImageWidth(imageWidth));
	}

	public void initializeLevel2DMeasures() {
		spotList.forEach(Spot::initializeLevel2DMeasures);
	}

	public void transferMeasuresToLevel2D() {
		spotList.forEach(Spot::transferMeasuresToLevel2D);
	}

	/**
	 * Pushes 1D measure values into Level2D for the given spots (e.g. after
	 * in-memory rebuild of sumNoFly / sumClean).
	 */
	public void transferMeasuresToLevel2D(Iterable<Spot> targets) {
		if (targets == null) {
			return;
		}
		for (Spot spot : targets) {
			if (spot != null) {
				spot.transferMeasuresToLevel2D();
			}
		}
	}

	// === SEQUENCE OPERATIONS ===

	public void transferSpotsToSequenceAsROIs(Sequence sequence) {
		if (sequence == null || spotList.isEmpty()) {
			return;
		}

		List<ROI2D> spotROIList = new ArrayList<ROI2D>(spotList.size());
		for (Spot spot : spotList) {
			ROI2D roi = spot.getRoi();
			if (roi != null) {
				spotROIList.add(roi);
			}
		}
		Collections.sort(spotROIList, (r1, r2) -> {
			String name1 = r1.getName();
			String name2 = r2.getName();
			if (name1 == null && name2 == null)
				return 0;
			if (name1 == null)
				return -1;
			if (name2 == null)
				return 1;
			return name1.compareTo(name2);
		});
		sequence.addROIs(spotROIList, true);
	}

	// === UTILITY OPERATIONS ===

	/**
	 * Ensures {@code sumNoFly} exists for all spots.
	 * <p>
	 * Backward compatibility: older datasets may only have {@code sum} and/or
	 * {@code clean}. In that case we reconstruct {@code sumNoFly} as follows:
	 * <ul>
	 * <li>Prefer reconstruction from {@code sum} + {@code flyPresent} by masking
	 * fly-shadow bins and linearly interpolating across them.</li>
	 * <li>If {@code flyPresent} is missing, fall back to existing {@code clean}
	 * values (best-effort historical no-fly signal).</li>
	 * <li>If neither is available, fall back to {@code sum}.</li>
	 * </ul>
	 */
	public void ensureSumNoFlyPresent() {
		for (Spot spot : spotList) {
			if (spot == null)
				continue;
			ensureSumNoFlyForSpot(spot, false);
		}
	}

	/**
	 * Rebuilds sumNoFly and sumClean for the given spots from persistent sum and
	 * flyPresent (and legacy fallbacks when needed), matching load/detection
	 * semantics.
	 *
	 * @param force if false, skips spots whose sumNoFly array is already non-empty.
	 *              If true, always recomputes sumNoFly when sum is present; uses
	 *              {@link #reconstructSumNoFly} when flyPresent length matches sum.
	 */
	public void rebuildNoFlyAndCleanForSpots(List<Spot> targets, boolean force) {
		if (targets == null) {
			return;
		}
		for (Spot spot : targets) {
			ensureSumNoFlyForSpot(spot, force);
		}
		for (Spot spot : targets) {
			rebuildSumCleanFromSumNoFlyForSpot(spot);
		}
	}

	/**
	 * Rebuilds {@code sumClean} for all spots as an in-memory derived series from
	 * {@code sumNoFly}, using a NaN-robust running median.
	 */
	public void rebuildSumCleanFromSumNoFly() {
		for (Spot spot : spotList) {
			if (spot == null)
				continue;
			rebuildSumCleanFromSumNoFlyForSpot(spot);
		}
	}

	private void ensureSumNoFlyForSpot(Spot spot, boolean force) {
		if (spot == null) {
			return;
		}
		SpotMeasure sumNoFly = spot.getSumNoFly();
		if (sumNoFly == null) {
			return;
		}
		if (!force) {
			double[] existing = sumNoFly.getValues();
			if (existing != null && existing.length > 0) {
				return;
			}
		}
		double[] sumIn = spot.getSum() != null ? spot.getSum().getValues() : null;
		if (sumIn == null || sumIn.length == 0) {
			return;
		}

		int[] fly = spot.getFlyPresent() != null ? spot.getFlyPresent().getIsPresent() : null;
		double[] reconstructed;
		if (fly != null && fly.length == sumIn.length) {
			reconstructed = reconstructSumNoFly(sumIn, fly);
		} else {
			if (!force) {
				double[] legacyClean = spot.getSumClean() != null ? spot.getSumClean().getValues() : null;
				if (legacyClean != null && legacyClean.length == sumIn.length) {
					reconstructed = java.util.Arrays.copyOf(legacyClean, legacyClean.length);
				} else {
					reconstructed = java.util.Arrays.copyOf(sumIn, sumIn.length);
				}
			} else {
				reconstructed = java.util.Arrays.copyOf(sumIn, sumIn.length);
			}
		}
		sumNoFly.setValues(reconstructed);
	}

	private void rebuildSumCleanFromSumNoFlyForSpot(Spot spot) {
		final int span = 10;
		if (spot == null) {
			return;
		}
		SpotMeasure sumNoFly = spot.getSumNoFly();
		SpotMeasure sumClean = spot.getSumClean();
		if (sumNoFly == null || sumClean == null) {
			return;
		}
		double[] in = sumNoFly.getValues();
		if (in == null || in.length == 0) {
			return;
		}
		double[] out = runningMedianIgnoringNaN(in, span);
		sumClean.setValues(out);
	}

	/**
	 * For each time index where flyPresent[i] &gt; 0, replaces values in that run
	 * with a linear bridge between the last and next finite neighbors;
	 * unbridgeable edge segments become NaN.
	 */
	public static double[] reconstructSumNoFly(double[] sumIn, int[] flyPresent) {
		double[] out = java.util.Arrays.copyOf(sumIn, sumIn.length);
		if (flyPresent == null || flyPresent.length != sumIn.length) {
			return out;
		}

		final int n = out.length;
		int i = 0;
		while (i < n) {
			boolean fly = flyPresent[i] > 0;
			if (!fly) {
				i++;
				continue;
			}

			int start = i;
			int end = i;
			while (end + 1 < n && flyPresent[end + 1] > 0) {
				end++;
			}

			int left = start - 1;
			int right = end + 1;
			double yLeft = (left >= 0) ? out[left] : Double.NaN;
			double yRight = (right < n) ? out[right] : Double.NaN;

			if (left >= 0 && right < n && Double.isFinite(yLeft) && Double.isFinite(yRight)) {
				for (int k = start; k <= end; k++) {
					double ratio = (k - left) / (double) (right - left);
					out[k] = yLeft + (yRight - yLeft) * ratio;
				}
			} else {
				for (int k = start; k <= end; k++) {
					out[k] = Double.NaN;
				}
			}

			i = end + 1;
		}
		return out;
	}

	private static double[] runningMedianIgnoringNaN(double[] values, int span) {
		int n = values.length;
		double[] out = new double[n];
		for (int i = 0; i < n; i++) {
			int start = Math.max(0, i - span / 2);
			int end = Math.min(n - 1, i + span / 2);
			int count = end - start + 1;

			double[] window = new double[count];
			int m = 0;
			for (int j = start; j <= end; j++) {
				double v = values[j];
				if (Double.isFinite(v)) {
					window[m++] = v;
				}
			}

			if (m == 0) {
				out[i] = Double.NaN;
				continue;
			}

			double[] finite = (m == window.length) ? window : java.util.Arrays.copyOf(window, m);
			java.util.Arrays.sort(finite);
			out[i] = finite[m / 2];
		}
		return out;
	}

	public void medianFilterFromSumToSumClean() {
		// Backward-compatible entry point used across the codebase.
		// New semantics: sumClean is derived from sumNoFly (no-fly) then filtered.
		rebuildSumCleanFromSumNoFly();
	}

	public double getScalingFactorToPhysicalUnits(EnumResults resultType) {
		// Implementation would depend on specific scaling logic
		return 1.0;
	}

	public void setReadyToAnalyze(boolean setFilter, BuildSeriesOptions options) {
		spotList.forEach(spot -> spot.setReadyForAnalysis(setFilter));
	}

	// === PRIVATE HELPER METHODS ===

	private boolean saveListOfSpotsToXml(Node node) {
		Node spotsNode = XMLUtil.getElement(node, ID_SPOTTRACK);
		if (spotsNode == null) {
			return false;
		}

		XMLUtil.setElementIntValue(spotsNode, "version", DEFAULT_VERSION);
		Node nodeSpotsArray = XMLUtil.setElement(spotsNode, ID_LISTOFSPOTS);
		XMLUtil.setElementIntValue(nodeSpotsArray, ID_NSPOTS, spotList.size());

		sortSpotList();
		for (int i = 0; i < spotList.size(); i++) {
			Node nodeSpot = XMLUtil.setElement(spotsNode, ID_SPOT_ + i);
			spotList.get(i).saveToXml(nodeSpot);
		}

		return true;
	}

	private boolean loadSpotsOnlyV1(Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_SPOTTRACK);
		if (node == null) {
			return false;
		}

		Node nodeSpotsArray = XMLUtil.getElement(node, ID_LISTOFSPOTS);
		if (nodeSpotsArray == null) {
			return false;
		}

		int nitems = XMLUtil.getElementIntValue(nodeSpotsArray, ID_NSPOTS, 0);
		spotList.clear();

		for (int i = 0; i < nitems; i++) {
			Node nodeSpot = XMLUtil.getElement(node, ID_SPOT_ + i);
			if (nodeSpot != null) {
				Spot spot = new Spot();
				if (spot.loadFromXml(nodeSpot) && !isSpotPresent(spot)) {
					// Assign unique ID if not loaded from XML (legacy files)
					if (spot.getSpotUniqueID() == null) {
						int uniqueID = getNextUniqueSpotID();
						spot.setSpotUniqueID(new SpotID(uniqueID));
					}
					spotList.add(spot);
				}
			}
		}

		return true;
	}

	// === UTILITY METHODS ===

	@Override
	public String toString() {
		return String.format("SpotsArray{spotsCount=%d}", spotList.size());
	}

	// === SEQUENCE COMMUNICATION ===
	// New standardized method names (v2.3.3+)

	/**
	 * Transfers spot ROIs to the camera sequence. Removes existing spot ROIs and
	 * adds all current spot ROIs.
	 * 
	 * @param seqCamData the camera sequence
	 */
	public void transferROIsToSequence(SequenceCamData seqCamData) {
		// Use modern ROI operation for removing existing spot ROIs
		seqCamData.processROIs(ROIOperation.removeROIs("spot"));

		List<ROI2D> spotROIList = new ArrayList<ROI2D>(spotList.size());
		for (Spot spot : spotList) {
			ROI2D roi = spot.getRoi();
			if (roi != null)
				spotROIList.add(roi);
		}
		Sequence sequence = seqCamData.getSequence();
		if (sequence != null && spotROIList.size() > 0)
			sequence.addROIs(spotROIList, true);
	}

	/**
	 * Transfers ROIs from the camera sequence back to spots. Updates spot positions
	 * based on ROIs with names containing "spot".
	 * 
	 * @param seqCamData the camera sequence
	 */
	public void transferROIsFromSequence(SequenceCamData seqCamData) {
		List<ROI2D> roiList = seqCamData.findROIsMatchingNamePattern("spot");
		Collections.sort(roiList, new Comparators.ROI2D_Name());
		transferROIsToSpots(roiList);
		// addMissingSpots(roiList);
		Collections.sort(spotList, new Comparators.Spot_Name());
	}

	private void transferROIsToSpots(List<ROI2D> roiList) {
		if (spotList.size() < 1)
			return;

		for (Spot spot : spotList) {
			if (roiList.isEmpty())
				return;

			String spotName = spot.getName();
			Iterator<ROI2D> iterator = roiList.iterator();
			while (iterator.hasNext()) {
				ROI2D roi = iterator.next();
				String roiName = roi.getName();
				if (roiName != null && roiName.contains(spotName)) {
					spot.setRoi((ROI2DShape) roi);
					iterator.remove();
					break;
				}
			}
		}
	}

}