package plugins.fmp.multitools.experiment.capillary;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.w3c.dom.Node;

import icy.image.IcyBufferedImage;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.geom.Polyline2D;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.EnumCapillaryMeasures;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Level2D;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.MeasurementComputation;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.kernel.roi.roi2d.ROI2DArea;
import plugins.kernel.roi.roi2d.ROI2DLine;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

public class Capillary implements Comparable<Capillary> {

	// === CORE FIELDS ===
	public String version = null;
	private int kymographIndex = -1;
	private String kymographFilename = null;
	private boolean kymographBuild = true;
	private IcyBufferedImage cap_Image = null;

	// === PROPERTIES ===
	private final CapillaryProperties properties;

	// === MEASUREMENTS ===
	private final CapillaryMeasurements measurements;

	// === METADATA ===
	private final CapillaryMetadata metadata;

	// === PUBLIC FIELDS (Deprecated/Moved logic) ===
	// These are now delegated to properties but kept for backward compatibility
	// (where not private)
	// Actually, the plan says to update external references, but we can temporarily
	// keep them or just remove them.
	// Since I'm refactoring, I will remove the fields and add accessors.
	// WAIT. The user prompt asked "Update all external code to use getters" -> So I
	// should REMOVE these fields.

	public final String ID_TOPLEVEL = "toplevel";
	public final String ID_BOTTOMLEVEL = "bottomlevel";
	public final String ID_DERIVATIVE = "derivative";

	// === CONSTRUCTORS ===

	public Capillary(ROI2D roiCapillary) {
		this.properties = new CapillaryProperties();
		this.measurements = new CapillaryMeasurements();
		this.metadata = new CapillaryMetadata();

		this.metadata.roiCap = roiCapillary;
		this.metadata.kymographName = replace_LR_with_12(roiCapillary.getName());
		if (roiCapillary != null && roiCapillary.getName() != null) {
			this.metadata.kymographPrefix = extractPrefixFromRoiName(roiCapillary.getName());
		}
	}

	Capillary(String name) {
		this.properties = new CapillaryProperties();
		this.measurements = new CapillaryMeasurements();
		this.metadata = new CapillaryMetadata();

		this.metadata.kymographName = replace_LR_with_12(name);
		if (name != null) {
			this.metadata.kymographPrefix = extractPrefixFromName(name);
		}
	}

	public Capillary() {
		this.properties = new CapillaryProperties();
		this.measurements = new CapillaryMeasurements();
		this.metadata = new CapillaryMetadata();
	}

	// === ACCESSORS ===

	public int getKymographIndex() {
		return kymographIndex;
	}

	public void setKymographIndex(int index) {
		kymographIndex = index;
	}

	public String getKymographFileName() {
		return kymographFilename;
	}

	public void setKymographFileName(String name) {
		kymographFilename = name;
	}

	public boolean getKymographBuild() {
		return kymographBuild;
	}

	public void setKymographBuild(boolean flag) {
		kymographBuild = flag;
	}

	public IcyBufferedImage getCap_Image() {
		return cap_Image;
	}

	public void setCap_Image(IcyBufferedImage image) {
		cap_Image = image;
	}

	public CapillaryProperties getProperties() {
		return properties;
	}

	// Delegate getters for properties
	public String getStimulus() {
		return properties.getStimulus();
	}

	public String getConcentration() {
		return properties.getConcentration();
	}

	public String getSide() {
		return properties.getSide();
	}

	public int getNFlies() {
		return properties.getNFlies();
	}

	public int getCageID() {
		return properties.getCageID();
	}

	public double getVolume() {
		return properties.getVolume();
	}

	public int getPixels() {
		return properties.getPixels();
	}

	public boolean isDescriptionOK() {
		return properties.isDescriptionOK();
	}

	public int getVersionInfos() {
		return properties.getVersionInfos();
	}

	public BuildSeriesOptions getLimitsOptions() {
		return properties.getLimitsOptions();
	}

	public void setStimulus(String s) {
		properties.setStimulus(s);
	}

	public void setConcentration(String s) {
		properties.setConcentration(s);
	}

	public void setSide(String s) {
		properties.setSide(s);
	}

	public void setNFlies(int n) {
		properties.setNFlies(n);
	}

	public void setCageID(int n) {
		properties.setCageID(n);
	}

	public void setVolume(double v) {
		properties.setVolume(v);
	}

	public void setPixels(int p) {
		properties.setPixels(p);
	}

	public void setDescriptionOK(boolean b) {
		properties.setDescriptionOK(b);
	}

	public void setVersionInfos(int v) {
		properties.setVersionInfos(v);
	}

	// Delegate getters for measurements
	public CapillaryMeasure getTopLevel() {
		return measurements.ptsTop;
	}

	public CapillaryMeasure getBottomLevel() {
		return measurements.ptsBottom;
	}

	public CapillaryMeasure getDerivative() {
		return measurements.ptsDerivative;
	}

	public void setDerivative(CapillaryMeasure derivative) {
		measurements.ptsDerivative = derivative;
	}

	public CapillaryGulps getGulps() {
		return measurements.ptsGulps;
	}

	public CapillaryMeasure getTopCorrected() {
		return measurements.ptsTopCorrected;
	}

	public void setTopCorrected(CapillaryMeasure m) {
		measurements.ptsTopCorrected = m;
	}

	// Delegate getters for metadata
	public ROI2D getRoi() {
		return metadata.roiCap;
	}

	public void setRoi(ROI2D roi) {
		metadata.roiCap = roi;
		// Clear cached roisForKymo list to force re-initialization with new ROI
		if (metadata.roisForKymo != null && metadata.roisForKymo.size() > 0) {
			metadata.roisForKymo.clear();
		}
		// Update kymographPrefix from ROI name if not already set
		if (roi != null && roi.getName() != null
				&& (metadata.kymographPrefix == null || metadata.kymographPrefix.isEmpty())) {
			metadata.kymographPrefix = extractPrefixFromRoiName(roi.getName());
		}
	}

	public List<AlongT> getRoisForKymo() {
		if (metadata.roisForKymo.size() < 1)
			initROI2DForKymoList();
		return metadata.roisForKymo;
	}

	public String getKymographName() {
		return metadata.kymographName;
	}

	public void setKymographName(String name) {
		metadata.kymographName = name;
		// Update kymographPrefix from name if not already set
		if (name != null && (metadata.kymographPrefix == null || metadata.kymographPrefix.isEmpty())) {
			metadata.kymographPrefix = extractPrefixFromName(name);
		}
	}

	public String getKymographPrefix() {
		return metadata.kymographPrefix;
	}

	public void setKymographPrefix(String prefix) {
		metadata.kymographPrefix = prefix;
	} // Added setter for import

	@Override
	public int compareTo(Capillary o) {
		if (o != null)
			return this.metadata.kymographName.compareTo(o.getKymographName());
		return 1;
	}

	// === FIELD ACCESS ===

	/**
	 * Gets a field value based on the column header.
	 * 
	 * @param fieldEnum the field enum
	 * @return the field value as string
	 */
	public String getField(EnumXLSColumnHeader fieldEnum) {
		Objects.requireNonNull(fieldEnum, "Field enum cannot be null");

		switch (fieldEnum) {
		case CAP_STIM:
			return properties.getStimulus();
		case CAP_CONC:
			return properties.getConcentration();
		case CAP_VOLUME:
			return String.valueOf(properties.getVolume());
		default:
			return null;
		}
	}

	/**
	 * Sets a field value based on the column header.
	 * 
	 * @param fieldEnum the field enum
	 * @param value     the value to set
	 */
	public void setField(EnumXLSColumnHeader fieldEnum, String value) {
		Objects.requireNonNull(fieldEnum, "Field enum cannot be null");
		Objects.requireNonNull(value, "Value cannot be null");

		switch (fieldEnum) {
		case CAP_STIM:
			properties.setStimulus(value);
			break;
		case CAP_CONC:
			properties.setConcentration(value);
			break;
		case CAP_VOLUME:
			try {
				double volume = Double.parseDouble(value);
				properties.setVolume(volume);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid volume value: " + value, e);
			}
			break;
		default:
			// Ignore unsupported fields
			break;
		}
	}

	// ------------------------------------------

	public void copy(Capillary cap) {
		metadata.kymographName = cap.getKymographName();
		version = cap.version;
		metadata.roiCap = cap.getRoi() != null ? (ROI2D) cap.getRoi().getCopy() : null;
		kymographFilename = cap.kymographFilename;
		kymographIndex = cap.kymographIndex;

		properties.copyFrom(cap.properties);
		measurements.copyFrom(cap.measurements);
	}

	/**
	 * Clears computed measures (evaporation-corrected, L+R, etc.). Should be called
	 * when raw measures change.
	 */
	public void clearComputedMeasures() {
		// measurements.ptsTopCorrected = null;
		// Now persistent, so maybe we want to clear its data instead of nulling the
		// reference?
		// For now, keeping it cleared but object exists.
		if (measurements.ptsTopCorrected != null) {
			measurements.ptsTopCorrected.clear();
		}
	}

	public void setRoiName(String name) {
		metadata.roiCap.setName(name);
	}

	public String getRoiName() {
		if (metadata.roiCap != null)
			return metadata.roiCap.getName();
		return null;
	}

	public String getLast2ofCapillaryName() {
		if (metadata.roiCap != null && metadata.roiCap.getName() != null) {
			return metadata.roiCap.getName().substring(metadata.roiCap.getName().length() - 2);
		}
		// Fallback: extract from kymographName if ROI is not available
		if (metadata.kymographName != null && metadata.kymographName.length() >= 2) {
			return metadata.kymographName.substring(metadata.kymographName.length() - 2);
		}
		return "??";
	}

	public String getCapillarySide() {
		if (metadata.roiCap != null && metadata.roiCap.getName() != null) {
			return metadata.roiCap.getName().substring(metadata.roiCap.getName().length() - 1);
		}
		// Fallback: try to extract from kymographName
		if (metadata.kymographName != null && metadata.kymographName.length() > 0) {
			String lastChar = metadata.kymographName.substring(metadata.kymographName.length() - 1);
			// Convert "1" to "L" and "2" to "R" if needed
			if (lastChar.equals("1"))
				return "L";
			if (lastChar.equals("2"))
				return "R";
			// If it's already L or R, use it
			if (lastChar.equals("L") || lastChar.equals("R"))
				return lastChar;
		}
		// Final fallback to stored capSide
		return properties.getSide() != null && !properties.getSide().equals(".") ? properties.getSide() : "?";
	}

	public static String replace_LR_with_12(String name) {
		String newname = name;
		if (name.contains("R"))
			newname = name.replace("R", "2");
		else if (name.contains("L"))
			newname = name.replace("L", "1");
		return newname;
	}

	/**
	 * Derives the kymographIndex from the position of kymographFilename in the kymograph images list.
	 * This accounts for missing/deleted kymographs by using actual position rather than name-based numbering.
	 * 
	 * @param kymographImagesList the list of kymograph image filenames from seqKymos
	 * @return the derived kymograph index, or -1 if it cannot be determined
	 */
	public int deriveKymographIndexFromImageList(List<String> kymographImagesList) {
		if (kymographImagesList == null || kymographImagesList.isEmpty()) {
			return -1;
		}
		
		// First attempt: match using kymographFilename if available
		if (kymographFilename != null && !kymographFilename.isEmpty()) {
			String targetFilename = new java.io.File(kymographFilename).getName();
			
			for (int i = 0; i < kymographImagesList.size(); i++) {
				String imageFilename = new java.io.File(kymographImagesList.get(i)).getName();
				if (imageFilename.equals(targetFilename)) {
					return i;
				}
			}
		}
		
		// Second attempt: match using kymographName (without extension)
		if (metadata.kymographName != null && !metadata.kymographName.isEmpty()) {
			for (int i = 0; i < kymographImagesList.size(); i++) {
				String imagePath = kymographImagesList.get(i);
				String imageFilename = new java.io.File(imagePath).getName();
				
				// Remove extension from image filename to compare with kymographName
				String imageNameWithoutExt = imageFilename;
				int lastDotIndex = imageFilename.lastIndexOf('.');
				if (lastDotIndex > 0) {
					imageNameWithoutExt = imageFilename.substring(0, lastDotIndex);
				}
				
				if (imageNameWithoutExt.equals(metadata.kymographName)) {
					return i;
				}
			}
		}
		
		return -1;
	}

	/**
	 * Extracts prefix from ROI name (e.g., "line0L" -> "0L", "line1R" -> "1R")
	 */
	private String extractPrefixFromRoiName(String roiName) {
		if (roiName == null || roiName.isEmpty())
			return null;
		if (roiName.startsWith("line") && roiName.length() > 4) {
			// Extract number and L/R suffix (e.g., "line0L" -> "0L")
			return roiName.substring(4);
		}
		return null;
	}

	/**
	 * Extracts prefix from kymograph name (e.g., "line01" -> "0L", "line02" ->
	 * "0R")
	 */
	private String extractPrefixFromName(String name) {
		if (name == null || name.isEmpty())
			return null;
		if (name.startsWith("line") && name.length() >= 6) {
			// Extract number and convert last digit: "1" -> "L", "2" -> "R"
			String number = name.substring(4, name.length() - 1); // e.g., "0" from "line01"
			String lastChar = name.substring(name.length() - 1); // e.g., "1" or "2"
			if (lastChar.equals("1")) {
				return number + "L";
			} else if (lastChar.equals("2")) {
				return number + "R";
			} else {
				// Fallback: just use last 2 characters
				return name.substring(name.length() - 2);
			}
		} else if (name.length() >= 2) {
			// If name doesn't start with "line", try to extract from end
			String lastChar = name.substring(name.length() - 1);
			if (lastChar.equals("1")) {
				return name.substring(name.length() - 2, name.length() - 1) + "L";
			} else if (lastChar.equals("2")) {
				return name.substring(name.length() - 2, name.length() - 1) + "R";
			} else {
				return name.substring(name.length() - 2);
			}
		}
		return null;
	}

	public int getCageIndexFromRoiName() {
		if (metadata.roiCap == null) {
			System.out.println("roicap is null");
			return -1;
		}
		String name = metadata.roiCap.getName();
		if (!name.contains("line"))
			return -1;
		String stringNumber = name.substring(4, 5);
		return Integer.valueOf(stringNumber);
	}

	public String getSideDescriptor(EnumResults resultType) {
		String value = null;
		properties.setSide(getCapillarySide());
		String side = properties.getSide();

		switch (resultType) {
		case DISTANCE:
			value = side + "(DIST)";
			break;
		case ISALIVE:
			value = side + "(L=R)";
			break;
		case SUMGULPS_LR:
		case TOPLEVELDELTA_LR:
		case TOPLEVEL_LR:
			if (side.equals("L"))
				value = "sum";
			else
				value = "PI";
			break;
		case XYIMAGE:
		case XYTOPCAGE:
		case XYTIPCAPS:
			if (side.equals("L"))
				value = "x";
			else
				value = "y";
			break;
		default:
			value = side;
			break;
		}
		return value;
	}

	public String getCapillaryField(EnumXLSColumnHeader fieldEnumCode) {
		String stringValue = null;
		switch (fieldEnumCode) {
		case CAP_STIM:
			stringValue = properties.getStimulus();
			break;
		case CAP_CONC:
			stringValue = properties.getConcentration();
			break;
		default:
			break;
		}
		return stringValue;
	}

	public void setCapillaryField(EnumXLSColumnHeader fieldEnumCode, String stringValue) {
		switch (fieldEnumCode) {
		case CAP_STIM:
			properties.setStimulus(stringValue);
			break;
		case CAP_CONC:
			properties.setConcentration(stringValue);
			break;
		default:
			break;
		}
	}

	// -----------------------------------------

	public boolean isThereAnyMeasuresDone(EnumResults resultType) {
		boolean yes = false;
		switch (resultType) {
		case DERIVEDVALUES:
			yes = (measurements.ptsDerivative.isThereAnyMeasuresDone());
			break;
		case SUMGULPS:
			yes = (measurements.ptsGulps.isThereAnyMeasuresDone());
			break;
		case BOTTOMLEVEL:
			yes = measurements.ptsBottom.isThereAnyMeasuresDone();
			break;
		case TOPLEVEL:
		default:
			yes = measurements.ptsTop.isThereAnyMeasuresDone();
			break;
		}
		return yes;
	}

	/**
	 * Gets capillary measures for Excel export (with computation support).
	 * 
	 * @param resultType  The type of measurement
	 * @param seriesBinMs Series bin duration in milliseconds
	 * @param outputBinMs Output bin duration in milliseconds
	 * @param exp         The experiment (required if computation is needed)
	 * @param options     The results options (required if computation is needed)
	 * @return The measurement data as integers, or null if not available
	 */
	public ArrayList<Integer> getCapillaryMeasuresForXLSPass1(EnumResults resultType, long seriesBinMs,
			long outputBinMs, Experiment exp, ResultsOptions options) {
		// Check if this measure type requires computation (skip stored data accessors)
		if (resultType.isStoredData()) {
			// Stored data accessors throw UnsupportedOperationException - use direct access
			return getCapillaryMeasuresForXLSPass1Direct(resultType, seriesBinMs, outputBinMs);
		}

		MeasurementComputation computation = resultType.getComputationStrategy();
		if (computation != null) {
			if (exp == null || options == null) {
				return null; // Cannot compute without exp and options
			}

			// Temporarily set bin parameters in options for computation
			long originalBinMs = options.buildExcelStepMs;
			options.buildExcelStepMs = (int) outputBinMs;

			ArrayList<Double> computedData = computation.compute(exp, this, options);

			// Restore original value
			options.buildExcelStepMs = (int) originalBinMs;

			if (computedData == null || computedData.isEmpty()) {
				return null;
			}

			// Convert to integers (rounding)
			ArrayList<Integer> result = new ArrayList<>(computedData.size());
			for (Double val : computedData) {
				result.add(val != null ? (int) Math.round(val) : 0);
			}
			return result;
		}

		// Use existing direct access logic
		return getCapillaryMeasuresForXLSPass1Direct(resultType, seriesBinMs, outputBinMs);
	}

	/**
	 * Gets capillary measures for Excel export (backward compatibility). Only works
	 * for measures that don't require computation.
	 */
	public ArrayList<Integer> getCapillaryMeasuresForXLSPass1(EnumResults resultType, long seriesBinMs,
			long outputBinMs) {
		// If computation is required, return null (caller should use overloaded
		// version)
		if (resultType.requiresComputation()) {
			return null;
		}
		return getCapillaryMeasuresForXLSPass1Direct(resultType, seriesBinMs, outputBinMs);
	}

	/**
	 * Internal method that retrieves measures using direct access (no computation).
	 */
	private ArrayList<Integer> getCapillaryMeasuresForXLSPass1Direct(EnumResults resultType, long seriesBinMs,
			long outputBinMs) {
		ArrayList<Integer> datai = null;
		switch (resultType) {
		case DERIVEDVALUES:
			datai = measurements.ptsDerivative.getMeasures(seriesBinMs, outputBinMs);
			break;
		case SUMGULPS:
		case SUMGULPS_LR:
			// These can use existing gulp data extraction
			if (measurements.ptsGulps != null)
				datai = measurements.ptsGulps.getMeasuresFromGulps(resultType, measurements.ptsTop.getNPoints(),
						seriesBinMs, outputBinMs);
			break;
		case BOTTOMLEVEL:
			datai = measurements.ptsBottom.getMeasures(seriesBinMs, outputBinMs);
			break;
		case TOPLEVEL:
			// Use evaporation-corrected measure if available, otherwise raw
			if (measurements.ptsTopCorrected != null && measurements.ptsTopCorrected.polylineLevel != null
					&& measurements.ptsTopCorrected.polylineLevel.npoints > 0) {
				datai = measurements.ptsTopCorrected.getMeasures(seriesBinMs, outputBinMs);
			} else {
				datai = measurements.ptsTop.getMeasures(seriesBinMs, outputBinMs);
			}
			break;
		case TOPLEVEL_LR:
			// Note: L+R measures are now stored at the Cage level in
			// CageCapillariesComputation.
			// This method cannot access them directly. The export code should handle
			// TOPLEVEL_LR
			// differently by reading from CageCapillariesComputation.
			// Fallback to raw for now (should not be reached in normal flow)
			datai = measurements.ptsTop.getMeasures(seriesBinMs, outputBinMs);
			break;
		case TOPRAW:
		case TOPLEVELDELTA:
		case TOPLEVELDELTA_LR:
		default:
			datai = measurements.ptsTop.getMeasures(seriesBinMs, outputBinMs);
			break;
		}
		return datai;
	}

	public void cropMeasuresToNPoints(int npoints) {
		measurements.cropMeasuresToNPoints(npoints);
	}

	public void restoreClippedMeasures() {
		measurements.restoreClippedMeasures();
	}

	public void setGulpsOptions(BuildSeriesOptions options) {
		properties.setLimitsOptions(options);
	}

	public BuildSeriesOptions getGulpsOptions() {
		return properties.getLimitsOptions();
	}

	public void initGulps() {
		if (measurements.ptsGulps == null)
			measurements.ptsGulps = new CapillaryGulps();

		// Ensure dense series exists and matches the top level length
		int npoints = (measurements.ptsTop != null) ? measurements.ptsTop.getNPoints() : 0;
		measurements.ptsGulps.ensureSize(npoints);

		if (properties.getLimitsOptions().analyzePartOnly) {
			int searchFromXFirst = (int) properties.getLimitsOptions().searchArea.getX();
			int searchFromXLast = (int) properties.getLimitsOptions().searchArea.getWidth() + searchFromXFirst;
			measurements.ptsGulps.removeGulpsWithinInterval(searchFromXFirst, searchFromXLast);
		} else {
			measurements.ptsGulps.clear();
			measurements.ptsGulps.ensureSize(npoints);
		}
	}

	public void detectGulps() {
		if (measurements.ptsTop.polylineLevel == null || measurements.ptsDerivative.polylineLevel == null)
			return;

		// Dense gulp amplitude series aligned to topraw indices
		int npoints = measurements.ptsTop.polylineLevel.npoints;
		measurements.ptsGulps.ensureSize(npoints);

		int firstPixel = 1; // delta uses t-1
		int lastPixel = npoints;
		if (properties.getLimitsOptions().analyzePartOnly) {
			firstPixel = (int) properties.getLimitsOptions().searchArea.getX();
			lastPixel = (int) properties.getLimitsOptions().searchArea.getWidth() + firstPixel;

		}
		int threshold = (int) ((properties.getLimitsOptions().detectGulpsThreshold_uL / properties.getVolume())
				* properties.getPixels());

		// First-pass detection:
		// - If derivative(t-1) >= threshold, mark the interval t as a gulp interval
		// - Store signed amplitude as topraw(t) - topraw(t-1)
		for (int indexPixel = firstPixel; indexPixel < lastPixel; indexPixel++) {
			int derivativeValue = (int) measurements.ptsDerivative.polylineLevel.ypoints[indexPixel - 1];
			if (derivativeValue >= threshold) {
				double deltaTop = measurements.ptsTop.polylineLevel.ypoints[indexPixel]
						- measurements.ptsTop.polylineLevel.ypoints[indexPixel - 1];
				measurements.ptsGulps.setAmplitudeAt(indexPixel, deltaTop);
			} else {
				measurements.ptsGulps.setAmplitudeAt(indexPixel, 0);
			}
		}
	}

	public int getLastMeasure(EnumResults resultType) {
		int lastMeasure = 0;
		switch (resultType) {
		case DERIVEDVALUES:
			lastMeasure = measurements.ptsDerivative.getLastMeasure();
			break;
		case SUMGULPS:
			if (measurements.ptsGulps != null) {
				List<Integer> datai = measurements.ptsGulps.getCumSumFromGulps(measurements.ptsTop.getNPoints());
				lastMeasure = datai.get(datai.size() - 1);
			}
			break;
		case BOTTOMLEVEL:
			lastMeasure = measurements.ptsBottom.getLastMeasure();
			break;
		case TOPLEVEL:
		default:
			lastMeasure = measurements.ptsTop.getLastMeasure();
			break;
		}
		return lastMeasure;
	}

	public int getLastDeltaMeasure(EnumResults resultType) {
		int lastMeasure = 0;
		switch (resultType) {
		case DERIVEDVALUES:
			lastMeasure = measurements.ptsDerivative.getLastDeltaMeasure();
			break;
		case SUMGULPS:
			if (measurements.ptsGulps != null) {
				List<Integer> datai = measurements.ptsGulps.getCumSumFromGulps(measurements.ptsTop.getNPoints());
				lastMeasure = datai.get(datai.size() - 1) - datai.get(datai.size() - 2);
			}
			break;
		case BOTTOMLEVEL:
			lastMeasure = measurements.ptsBottom.getLastDeltaMeasure();
			break;
		case TOPLEVEL:
		default:
			lastMeasure = measurements.ptsTop.getLastDeltaMeasure();
			break;
		}
		return lastMeasure;
	}

	public int getT0Measure(EnumResults resultType) {
		int t0Measure = 0;
		switch (resultType) {
		case DERIVEDVALUES:
			t0Measure = measurements.ptsDerivative.getT0Measure();
			break;
		case SUMGULPS:
			if (measurements.ptsGulps != null) {
				List<Integer> datai = measurements.ptsGulps.getCumSumFromGulps(measurements.ptsTop.getNPoints());
				t0Measure = datai.get(0);
			}
			break;
		case BOTTOMLEVEL:
			t0Measure = measurements.ptsBottom.getT0Measure();
			break;
		case TOPLEVEL:
		default:
			t0Measure = measurements.ptsTop.getT0Measure();
			break;
		}
		return t0Measure;
	}

	public List<ROI2D> transferMeasuresToROIs() {
		return transferMeasuresToROIs(null);
	}

	public List<ROI2D> transferMeasuresToROIs(List<String> kymographImagesList) {
		List<ROI2D> listrois = new ArrayList<ROI2D>();
		getROIFromCapillaryLevel(measurements.ptsTop, listrois, kymographImagesList);
		getROIFromCapillaryLevel(measurements.ptsBottom, listrois, kymographImagesList);
		getROIFromCapillaryLevel(measurements.ptsDerivative, listrois, kymographImagesList);
		getROIsFromCapillaryGulps(measurements.ptsGulps, listrois, kymographImagesList);
		return listrois;
	}

	private void getROIFromCapillaryLevel(CapillaryMeasure capLevel, List<ROI2D> listrois, 
			List<String> kymographImagesList) {
		if (capLevel.polylineLevel == null || capLevel.polylineLevel.npoints == 0)
			return;

		ROI2D roi = new ROI2DPolyLine(capLevel.polylineLevel);
		String name = metadata.kymographPrefix + "_" + capLevel.capName;
		roi.setName(name);
		
		// Always derive kymograph index from image list (don't trust stored value)
		int tIndex = deriveKymographIndexFromImageList(kymographImagesList);
		if (tIndex >= 0) {
			kymographIndex = tIndex;
		} else {
			System.out.println("capillary:" + (metadata.roiCap != null ? metadata.roiCap.getName() : metadata.kymographName) 
					+ " kymographIndex=" + kymographIndex 
					+ " kymographFilename=" + kymographFilename
					+ " (could not derive from image list)");
			tIndex = kymographIndex; // Fallback to stored value if search fails
		}
		roi.setT(tIndex);

		if (capLevel.capName.contains(ID_DERIVATIVE)) {
			roi.setColor(Color.yellow);
			roi.setStroke(1);
		}
		listrois.add(roi);
	}

	private void getROIsFromCapillaryGulps(CapillaryGulps capGulps, List<ROI2D> listrois, 
			List<String> kymographImagesList) {
		if (capGulps == null || capGulps.getAmplitudeSeries() == null || capGulps.getAmplitudeSeries().npoints == 0)
			return;

		ROI2DArea roiDots = new ROI2DArea();
		// Display-only dots: show positive gulp intervals at the current top level y.
		// (Signed amplitudes are stored, but negatives are not counted/displayed as
		// gulps by default.)
		for (int x = 0; x < capGulps.getAmplitudeSeries().npoints; x++) {
			if (capGulps.getAmplitudeSeries().ypoints[x] > 0) {
				int y = 0;
				if (measurements != null && measurements.ptsTop != null && measurements.ptsTop.polylineLevel != null
						&& x < measurements.ptsTop.polylineLevel.npoints) {
					y = (int) measurements.ptsTop.polylineLevel.ypoints[x];
				}
				roiDots.addPoint(x, y);
			}
		}
		if (roiDots.getBounds().isEmpty())
			return;

		roiDots.setName(metadata.kymographPrefix + "_gulps");
		roiDots.setColor(Color.red);
		
		// Always derive kymograph index from image list (don't trust stored value)
		int tIndex = deriveKymographIndexFromImageList(kymographImagesList);
		if (tIndex >= 0) {
			kymographIndex = tIndex;
		} else {
			tIndex = kymographIndex; // Fallback to stored value if search fails
		}
		roiDots.setT(tIndex);
		listrois.add(roiDots);
	}

	public void transferROIsToMeasures(List<ROI> listRois) {
		measurements.ptsTop.transferROIsToMeasures(listRois);
		measurements.ptsBottom.transferROIsToMeasures(listRois);
		measurements.ptsDerivative.transferROIsToMeasures(listRois);
		measurements.ptsGulps.transferROIsToMeasures(listRois);
	}

	// -----------------------------------------------------------------------------

	public boolean xmlLoad_CapillaryOnly(Node node) {
		return CapillaryPersistence.xmlLoadCapillary(node, this);
	}

	public boolean xmlLoad_MeasuresOnly(Node node) {
		return CapillaryPersistence.xmlLoadMeasures(node, this);
	}

	public boolean xmlSave_CapillaryOnly(Node node) {
		return CapillaryPersistence.xmlSaveCapillary(node, this);
	}

	// -------------------------------------------

	public Point2D getCapillaryTipWithinROI2D(ROI2D roi2D) {
		Point2D pt = null;
		if (metadata.roiCap instanceof ROI2DPolyLine) {
			Polyline2D line = ((ROI2DPolyLine) metadata.roiCap).getPolyline2D();
			int last = line.npoints - 1;
			if (roi2D.contains(line.xpoints[0], line.ypoints[0]))
				pt = new Point2D.Double(line.xpoints[0], line.ypoints[0]);
			else if (roi2D.contains(line.xpoints[last], line.ypoints[last]))
				pt = new Point2D.Double(line.xpoints[last], line.ypoints[last]);
		} else if (metadata.roiCap instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) metadata.roiCap).getLine();
			if (roi2D.contains(line.getP1()))
				pt = line.getP1();
			else if (roi2D.contains(line.getP2()))
				pt = line.getP2();
		}
		return pt;
	}

	public Point2D getCapillaryROILowestPoint() {
		Point2D pt = null;
		if (metadata.roiCap instanceof ROI2DPolyLine) {
			Polyline2D line = ((ROI2DPolyLine) metadata.roiCap).getPolyline2D();
			int last = line.npoints - 1;
			if (line.ypoints[0] > line.ypoints[last])
				pt = new Point2D.Double(line.xpoints[0], line.ypoints[0]);
			else
				pt = new Point2D.Double(line.xpoints[last], line.ypoints[last]);
		} else if (metadata.roiCap instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) metadata.roiCap).getLine();
			if (line.getP1().getY() > line.getP2().getY())
				pt = line.getP1();
			else
				pt = line.getP2();
		}
		return pt;
	}

	public Point2D getCapillaryROIFirstPoint() {
		Point2D pt = null;
		if (metadata.roiCap instanceof ROI2DPolyLine) {
			Polyline2D line = ((ROI2DPolyLine) metadata.roiCap).getPolyline2D();
			pt = new Point2D.Double(line.xpoints[0], line.ypoints[0]);
		} else if (metadata.roiCap instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) metadata.roiCap).getLine();
			pt = line.getP1();
		}
		return pt;
	}

	public Point2D getCapillaryROILastPoint() {
		Point2D pt = null;
		if (metadata.roiCap instanceof ROI2DPolyLine) {
			Polyline2D line = ((ROI2DPolyLine) metadata.roiCap).getPolyline2D();
			int last = line.npoints - 1;
			pt = new Point2D.Double(line.xpoints[last], line.ypoints[last]);
		} else if (metadata.roiCap instanceof ROI2DLine) {
			Line2D line = ((ROI2DLine) metadata.roiCap).getLine();
			pt = line.getP2();
		}
		return pt;
	}

	public int getCapillaryROILength() {
		Point2D pt1 = getCapillaryROIFirstPoint();
		Point2D pt2 = getCapillaryROILastPoint();
		double npixels = Math.sqrt((pt2.getY() - pt1.getY()) * (pt2.getY() - pt1.getY())
				+ (pt2.getX() - pt1.getX()) * (pt2.getX() - pt1.getX()));
		return (int) npixels;
	}

	public void adjustToImageWidth(int imageWidth) {
		measurements.adjustToImageWidth(imageWidth);
	}

	public void cropToImageWidth(int imageWidth) {
		measurements.cropToImageWidth(imageWidth);
	}
	// --------------------------------------------

	public List<AlongT> getROIsForKymo() {
		if (metadata.roisForKymo.size() < 1)
			initROI2DForKymoList();
		return metadata.roisForKymo;
	}

	public AlongT getROI2DKymoAt(int i) {
		if (metadata.roisForKymo.size() < 1)
			initROI2DForKymoList();
		return metadata.roisForKymo.get(i);
	}

	public AlongT getROI2DKymoAtIntervalT(long t) {
		if (metadata.roisForKymo.size() < 1)
			initROI2DForKymoList();

		AlongT capRoi = null;
		for (AlongT item : metadata.roisForKymo) {
			if (t < item.getStart())
				break;
			capRoi = item;
		}
		return capRoi;
	}

	public void removeROI2DIntervalStartingAt(long start) {
		AlongT itemFound = null;
		for (AlongT item : metadata.roisForKymo) {
			if (start != item.getStart())
				continue;
			itemFound = item;
		}
		if (itemFound != null)
			metadata.roisForKymo.remove(itemFound);
	}

	private void initROI2DForKymoList() {
		metadata.roisForKymo.add(new AlongT(0, metadata.roiCap));
	}

	public void setVolumeAndPixels(double volume, int pixels) {
		properties.setVolume(volume);
		properties.setPixels(pixels);
		properties.setDescriptionOK(true);
	}

	// -----------------------------------------------------------------------------

	public String csvExport_CapillarySubSectionHeader(String sep) {
		return CapillaryPersistence.csvExportCapillarySubSectionHeader(sep);
	}

	public String csvExport_CapillaryDescription(String sep) {
		return CapillaryPersistence.csvExportCapillaryDescription(this, sep);
	}

	public String csvExport_MeasureSectionHeader(EnumCapillaryMeasures measureType, String sep) {
		return CapillaryPersistence.csvExportMeasureSectionHeader(measureType, sep);
	}

	public String csvExport_MeasuresOneType(EnumCapillaryMeasures measureType, String sep) {
		return CapillaryPersistence.csvExportMeasuresOneType(this, measureType, sep);
	}

	public void csvImport_CapillaryDescription(String[] data) {
		CapillaryPersistence.csvImportCapillaryDescription(this, data);
	}

	public void csvImport_CapillaryData(EnumCapillaryMeasures measureType, String[] data, boolean x, boolean y) {
		CapillaryPersistence.csvImportCapillaryData(this, measureType, data, x, y);
	}

	// -----------------------------------------------------------------------------

	/**
	 * Gets measurements for the specified result type. Uses computation strategies
	 * if available, otherwise falls back to direct access.
	 * 
	 * @param resultType The type of measurement to retrieve
	 * @param exp        The experiment (required if computation is needed)
	 * @param options    The results options (required if computation is needed)
	 * @return The capillary measure, or null if not available
	 */
	public CapillaryMeasure getMeasurements(EnumResults resultType, Experiment exp, ResultsOptions options) {
		Objects.requireNonNull(resultType, "Export option cannot be null");

		// Check if this measure type requires computation (skip stored data accessors)
		if (resultType.isStoredData()) {
			// Stored data accessors throw UnsupportedOperationException - use direct access
			return getMeasurementsDirect(resultType);
		}

		MeasurementComputation computation = resultType.getComputationStrategy();
		if (computation != null) {
			if (exp == null || options == null) {
				return null; // Cannot compute without exp and options
			}

			ArrayList<Double> computedData = computation.compute(exp, this, options);
			if (computedData == null || computedData.isEmpty()) {
				return null;
			}

			// Convert computed data to CapillaryMeasure
			CapillaryMeasure measure = new CapillaryMeasure(resultType.toString());
			double[] x = new double[computedData.size()];
			double[] y = new double[computedData.size()];
			for (int i = 0; i < computedData.size(); i++) {
				x[i] = i;
				y[i] = computedData.get(i);
			}
			measure.polylineLevel = new Level2D(x, y, computedData.size());
			return measure;
		}

		// Use existing direct access logic
		return getMeasurementsDirect(resultType);
	}

	/**
	 * Gets measurements for the specified result type (backward compatibility).
	 * Only works for measures that don't require computation.
	 * 
	 * @param resultType The type of measurement to retrieve
	 * @return The capillary measure, or null if not available or computation is
	 *         required
	 */
	public CapillaryMeasure getMeasurements(EnumResults resultType) {
		// If computation is required, return null (caller should use overloaded
		// version)
		if (resultType.requiresComputation()) {
			return null;
		}
		return getMeasurementsDirect(resultType);
	}

	/**
	 * Internal method that retrieves measurements using direct access (no
	 * computation).
	 */
	private CapillaryMeasure getMeasurementsDirect(EnumResults resultType) {
		CapillaryMeasure measure = null;
		switch (resultType) {
		case DERIVEDVALUES:
			measure = measurements.ptsDerivative;
			break;
		case SUMGULPS:
		case SUMGULPS_LR:
			// These can use existing gulp data extraction
			if (measurements.ptsGulps != null) {
				int npoints = measurements.ptsTop.getNPoints();
				ArrayList<Integer> data = measurements.ptsGulps.getMeasuresFromGulps(resultType, npoints, 1, 1);
				if (data != null) {
					measure = new CapillaryMeasure(resultType.toString());
					double[] x = new double[data.size()];
					double[] y = new double[data.size()];
					for (int i = 0; i < data.size(); i++) {
						x[i] = i;
						y[i] = data.get(i);
					}
					measure.polylineLevel = new Level2D(x, y, data.size());
				}
			}
			break;
		case BOTTOMLEVEL:
			measure = measurements.ptsBottom;
			break;
		case TOPLEVEL:
		case TOPLEVEL_LR:
			if (measurements.ptsTopCorrected != null && measurements.ptsTopCorrected.isThereAnyMeasuresDone()) {
				measure = new CapillaryMeasure(measurements.ptsTopCorrected.capName);
				measure.copy(measurements.ptsTopCorrected);
			} else {
				measure = new CapillaryMeasure(measurements.ptsTop.capName);
				measure.copy(measurements.ptsTop);
			}
			if (measure.polylineLevel != null && measure.polylineLevel.npoints > 0)
				measure.polylineLevel.offsetToStartWithZeroAmplitude();
			break;

		case TOPRAW:
			if (measurements.ptsTop == null) {
				plugins.fmp.multitools.tools.Logger.warn("Capillary.getMeasurementsDirect(TOPRAW) - measurements.ptsTop is null for " + getKymographName());
				return null;
			}
			measure = new CapillaryMeasure(measurements.ptsTop.capName);
			measure.copy(measurements.ptsTop);
			if (measure.polylineLevel == null || measure.polylineLevel.npoints == 0) {
				plugins.fmp.multitools.tools.Logger.warn("Capillary.getMeasurementsDirect(TOPRAW) - measure.polylineLevel is null or empty (npoints=" + 
				                                         (measure.polylineLevel != null ? measure.polylineLevel.npoints : 0) + ") for " + getKymographName());
			}
			if (measure.polylineLevel != null && measure.polylineLevel.npoints > 0)
				measure.polylineLevel.offsetToStartWithZeroAmplitude();
			break;

		case TOPLEVELDELTA:
		case TOPLEVELDELTA_LR:
		default:
			measure = measurements.ptsTop;
			break;
		}
		return measure;
	}

	public void resetDerivative() {
		if (measurements.ptsDerivative != null)
			measurements.ptsDerivative.clear();
	}

	public void resetGulps() {
		if (measurements.ptsGulps != null)
			measurements.ptsGulps.clear();
	}

	public void clearAllMeasures() {
		measurements.clearAllMeasures();
	}

	// === INNER CLASSES ===

	private static class CapillaryMeasurements {
		public CapillaryMeasure ptsTop = new CapillaryMeasure("toplevel");
		public CapillaryMeasure ptsBottom = new CapillaryMeasure("bottomlevel");
		public CapillaryMeasure ptsDerivative = new CapillaryMeasure("derivative");
		public CapillaryGulps ptsGulps = new CapillaryGulps();
		public CapillaryMeasure ptsTopCorrected = new CapillaryMeasure("toplevel_corrected");

		void copyFrom(CapillaryMeasurements source) {
			ptsGulps.copy(source.ptsGulps);
			ptsTop.copy(source.ptsTop);
			ptsBottom.copy(source.ptsBottom);
			ptsDerivative.copy(source.ptsDerivative);
			ptsTopCorrected.copy(source.ptsTopCorrected);
		}

		void cropMeasuresToNPoints(int npoints) {
			if (ptsTop.polylineLevel != null)
				ptsTop.cropToNPoints(npoints);
			if (ptsBottom.polylineLevel != null)
				ptsBottom.cropToNPoints(npoints);
			if (ptsDerivative.polylineLevel != null)
				ptsDerivative.cropToNPoints(npoints);
		}

		void restoreClippedMeasures() {
			if (ptsTop.polylineLevel != null)
				ptsTop.restoreNPoints();
			if (ptsBottom.polylineLevel != null)
				ptsBottom.restoreNPoints();
			if (ptsDerivative.polylineLevel != null)
				ptsDerivative.restoreNPoints();
		}

		void adjustToImageWidth(int imageWidth) {
			ptsTop.adjustToImageWidth(imageWidth);
			ptsBottom.adjustToImageWidth(imageWidth);
			ptsDerivative.adjustToImageWidth(imageWidth);
			// safest: invalidate gulps when width changes; they must be re-detected
			ptsGulps.clear();
			ptsGulps.ensureSize(imageWidth);
		}

		void cropToImageWidth(int imageWidth) {
			ptsTop.cropToImageWidth(imageWidth);
			ptsBottom.cropToImageWidth(imageWidth);
			ptsDerivative.cropToImageWidth(imageWidth);
			// safest: invalidate gulps when width changes; they must be re-detected
			ptsGulps.clear();
			ptsGulps.ensureSize(imageWidth);
		}

		void clearAllMeasures() {
			ptsGulps.clear();
			ptsTop.clear();
			ptsBottom.clear();
			ptsDerivative.clear();
			ptsTopCorrected.clear();
		}
	}

	private static class CapillaryMetadata {
		public ROI2D roiCap = null;
		public ArrayList<AlongT> roisForKymo = new ArrayList<AlongT>();
		public String kymographName = null;
		public String kymographPrefix = null;
	}

}
