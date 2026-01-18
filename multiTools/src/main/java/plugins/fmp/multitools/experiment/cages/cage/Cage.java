package plugins.fmp.multitools.experiment.cages.cage;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.roi.BooleanMask2D;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.cages.cages.EnumCageMeasures;
import plugins.fmp.multitools.experiment.capillaries.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.capillary.Capillary;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spots.spot.Spot;
import plugins.fmp.multitools.experiment.spots.spot.SpotString;
import plugins.fmp.multitools.experiment.spots.spots.Spots;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectangle;
import plugins.kernel.roi.roi2d.ROI2DShape;

public class Cage implements Comparable<Cage>, AutoCloseable {
	private static final Color FLY_POSITION_ROI_COLOR = Color.YELLOW;

	private ROI2D cageROI2D = null;
	public BooleanMask2D cageMask2D = null;
	public CageProperties prop = new CageProperties();
	public CageMeasures measures = new CageMeasures();

	public FlyPositions flyPositions = new FlyPositions();

	// ID-based references (new approach)
	private List<SpotID> spotIDs = new ArrayList<>();
	private List<CapillaryID> capillaryIDs = new ArrayList<>();

	private final AtomicBoolean closed = new AtomicBoolean(false);

	public boolean valid = false;
	public boolean bDetect = true;
	public boolean initialflyRemoved = false;

	private final String ID_CAGELIMITS = "CageLimits";
	private final String ID_FLYPOSITIONS = "FlyPositions";

	// --------------------------------------

	public Cage(ROI2DShape roi) {
		this.cageROI2D = roi;
	}

	public Cage() {
	}

	@Override
	public int compareTo(Cage o) {
		if (o == null)
			return 1;
		// Handle null ROIs by comparing cage IDs as fallback
		if (this.cageROI2D == null && o.cageROI2D == null) {
			return Integer.compare(this.prop.getCageID(), o.prop.getCageID());
		}
		if (this.cageROI2D == null)
			return -1;
		if (o.cageROI2D == null)
			return 1;
		String thisName = this.cageROI2D.getName();
		String otherName = o.cageROI2D.getName();
		if (thisName == null && otherName == null)
			return 0;
		if (thisName == null)
			return -1;
		if (otherName == null)
			return 1;
		return thisName.compareTo(otherName);
	}

	// ------------------------------------
	// ID-based access methods

	public List<SpotID> getSpotIDs() {
		return spotIDs;
	}

	public void setSpotIDs(List<SpotID> spotIDs) {
		this.spotIDs = spotIDs != null ? new ArrayList<>(spotIDs) : new ArrayList<>();
	}

	public List<CapillaryID> getCapillaryIDs() {
		return capillaryIDs;
	}

	public void setCapillaryIDs(List<CapillaryID> capillaryIDs) {
		this.capillaryIDs = capillaryIDs != null ? new ArrayList<>(capillaryIDs) : new ArrayList<>();
	}

	/**
	 * Resolves SpotIDs to actual Spot objects from the global SpotsArray.
	 * 
	 * @param allSpots the global SpotsArray containing all spots
	 * @return list of Spot objects for this cage
	 */
	public List<Spot> getSpotList(Spots allSpots) {
		List<Spot> result = new ArrayList<>();
		if (allSpots == null) {
			return result;
		}
		for (SpotID spotID : spotIDs) {
			// Find spot by matching unique ID
			for (Spot spot : allSpots.getSpotList()) {
				if (spot.getSpotUniqueID() != null && spot.getSpotUniqueID().equals(spotID)) {
					result.add(spot);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Resolves CapillaryIDs to actual Capillary objects from the global
	 * Capillaries.
	 * 
	 * @param allCapillaries the global Capillaries containing all capillaries
	 * @return list of Capillary objects for this cage
	 */
	public List<Capillary> getCapillaries(Capillaries allCapillaries) {
		List<Capillary> result = new ArrayList<>();
		if (allCapillaries == null) {
			return result;
		}
		for (CapillaryID capID : capillaryIDs) {
			// Find capillary by kymographIndex
			for (Capillary cap : allCapillaries.getList()) {
				if (cap.getKymographIndex() == capID.getKymographIndex()) {
					result.add(cap);
					break;
				}
			}
		}
		return result;
	}

//	// Backward-compatible getters (deprecated, use ID-based methods)
//	@Deprecated
//	public SpotsArray getSpotsArray() {
//		return null; // Deprecated - use getSpots(SpotsArray allSpots) instead
//	}

	public CageProperties getProperties() {
		return prop;
	}

	public ROI2D getRoi() {
		return cageROI2D;
	}

	public ROI2D getCageRoi2D() {
		return cageROI2D;
	}

	public void setCageRoi(ROI2D roi) {
		cageROI2D = roi;
		setCageID(getCageIDFromRoiName());
	}

	public String getCageNumberFromRoiName() {
		if (cageROI2D == null || cageROI2D.getName() == null) {
			// Fallback to cage ID or existing strCageNumber if ROI is not available
			String fallback = prop.getStrCageNumber();
			if (fallback == null || fallback.isEmpty() || fallback.equals("0")) {
				fallback = formatCageNumberToString(prop.getCageID());
			}
			prop.setStrCageNumber(fallback);
			return prop.getStrCageNumber();
		}

		String roiName = cageROI2D.getName();
		if (roiName.length() >= 3) {
			prop.setStrCageNumber(roiName.substring(roiName.length() - 3));
		} else {
			// ROI name too short, use cage ID as fallback
			String fallback = String.format("%03d", prop.getCageID());
			prop.setStrCageNumber(fallback);
		}
		return prop.getStrCageNumber();
	}

	public String formatCageNumberToString(int number) {
		return String.format("%03d", number);
	}

	public int getCageIDFromRoiName() {
		int cageID = -1;
		if (cageROI2D != null && cageROI2D.getName() != null) {
			String roiName = cageROI2D.getName();
			if (roiName.length() >= 3)
				cageID = Integer.parseInt(roiName.substring(roiName.length() - 3));
		}
		return cageID;
	}

	public int getCageID() {
		return prop.getCageID();
	}

	public void setCageID(int ID) {
		prop.setCageID(ID);
	}

	public BooleanMask2D getCageMask2D() {
		return cageMask2D;
	}

	public void setMask2D(BooleanMask2D mask) {
		cageMask2D = mask;
	}

	public int getCageNFlies() {
		return prop.getCageNFlies();
	}

	public void setCageNFlies(int nFlies) {
		prop.setCageNFlies(nFlies);
	}

	// ------------------------------------------

	public FlyPositions getFlyPositions() {
		return flyPositions;
	}

	public void clearMeasures() {
		flyPositions.clear();
		measures.clear();
	}

	public Point2D getCenterTopCage() {
		Rectangle2D rect = cageROI2D.getBounds2D();
		Point2D pt = new Point2D.Double(rect.getX() + rect.getWidth() / 2, rect.getY());
		return pt;
	}

	public Point2D getCenterTipCapillaries(Capillaries capList) {
		List<Point2D> listpts = new ArrayList<Point2D>();
		for (Capillary cap : capList.getList()) {
			Point2D pt = cap.getCapillaryTipWithinROI2D(cageROI2D);
			if (pt != null)
				listpts.add(pt);
		}
		double x = 0;
		double y = 0;
		int n = listpts.size();
		for (Point2D pt : listpts) {
			x += pt.getX();
			y += pt.getY();
		}
		Point2D pt = new Point2D.Double(x / n, y / n);
		return pt;
	}

	public void addCapillaryIfUnique(Capillary cap) {
		if (cap == null) {
			return;
		}
		CapillaryID capID = new CapillaryID(cap.getKymographIndex());
		if (!capillaryIDs.contains(capID)) {
			capillaryIDs.add(capID);
		}
	}

	public void addCapillaryIDIfUnique(CapillaryID capID) {
		if (capID != null && !capillaryIDs.contains(capID)) {
			capillaryIDs.add(capID);
		}
	}

	public void addCapillaryIfUniqueBulkFilteredOnCageID(List<Capillary> capillaryList) {
		for (Capillary cap : capillaryList) {
			if (cap.getCageID() == prop.getCageID())
				addCapillaryIfUnique(cap);
		}
	}

	public void clearCapillaryList() {
		capillaryIDs.clear();
	}

	@Deprecated
	public List<Capillary> getCapillaryList() {
		return new ArrayList<>(); // Deprecated - use getCapillaries(Capillaries allCapillaries) instead
	}

	// -------------------------------------------------

	public void copyCageInfo(Cage cageFrom) {
		copyCage(cageFrom, false);
	}

	public void copyCage(Cage cageFrom, boolean bMeasures) {
		prop.copy(cageFrom.prop);
		cageROI2D = (ROI2D) cageFrom.cageROI2D.getCopy();
		valid = false;
		if (bMeasures)
			flyPositions.copyXYTaSeries(cageFrom.flyPositions);
		// Copy ID lists
		spotIDs = new ArrayList<>(cageFrom.spotIDs);
		capillaryIDs = new ArrayList<>(cageFrom.capillaryIDs);
	}

	public void pasteCageInfo(Cage cageTo) {
		prop.paste(cageTo.prop);
		cageTo.cageROI2D = (ROI2D) cageROI2D.getCopy();
		// Paste ID lists
		cageTo.spotIDs = new ArrayList<>(spotIDs);
		cageTo.capillaryIDs = new ArrayList<>(capillaryIDs);
	}

	public void pasteCage(Cage cageTo, boolean bMeasures) {
		prop.paste(cageTo.prop);
		cageTo.cageROI2D = (ROI2D) cageROI2D.getCopy();
		// Paste ID lists
		cageTo.spotIDs = new ArrayList<>(spotIDs);
		cageTo.capillaryIDs = new ArrayList<>(capillaryIDs);
		if (bMeasures)
			flyPositions.copyXYTaSeries(cageTo.flyPositions);
	}

	public String getField(EnumXLSColumnHeader fieldEnumCode) {
		String stringValue = null;
		switch (fieldEnumCode) {
		case CAGE_SEX:
			stringValue = prop.getFlySex();
			break;
		case CAGE_AGE:
			stringValue = String.valueOf(prop.getFlyAge());
			break;
		case CAGE_STRAIN:
			stringValue = prop.getFlyStrain();
			break;
		case CAGE_NFLIES:
			stringValue = Integer.toString(prop.getCageNFlies());
			break;
		default:
			break;
		}
		return stringValue;
	}

	public void setField(EnumXLSColumnHeader fieldEnumCode, String stringValue) {
		switch (fieldEnumCode) {
		case CAGE_SEX:
			prop.setFlySex(stringValue);
			break;
		case CAGE_AGE:
			int ageValue = Integer.valueOf(stringValue);
			prop.setFlyAge(ageValue);
			break;
		case CAGE_STRAIN:
			prop.setFlyStrain(stringValue);
			break;
		default:
			break;
		}
	}

	public ROI2DRectangle getRoiRectangleFromPositionAtT(int t) {
		// Return null if cage ROI is not available (prevents NPE in
		// getCageNumberFromRoiName)
		if (cageROI2D == null) {
			return null;
		}
		int nitems = flyPositions.flyPositionList.size();
		if (nitems == 0 || t >= nitems)
			return null;
		FlyPosition aValue = flyPositions.flyPositionList.get(t);

		ROI2DRectangle flyRoiR = new ROI2DRectangle(aValue.rectPosition);
		flyRoiR.setName("detR" + getCageNumberFromRoiName() + "_" + t);
		flyRoiR.setT(t);
		flyRoiR.setColor(FLY_POSITION_ROI_COLOR);
		return flyRoiR;
	}

	public void transferRoisToPositions(List<ROI2D> detectedROIsList) {
		// Skip if cage ROI is not available (prevents NPE in getCageNumberFromRoiName)
		if (cageROI2D == null) {
			return;
		}
		String filter = "detR" + getCageNumberFromRoiName();
		for (ROI2D roi : detectedROIsList) {
			String name = roi.getName();
			if (!name.contains(filter))
				continue;
			Rectangle2D rect = ((ROI2DRectangle) roi).getRectangle();
			int t = (int) roi.getT();
			if (t >= 0 && t < flyPositions.flyPositionList.size()) {
				flyPositions.flyPositionList.get(t).rectPosition = rect;
			}
		}
	}

	public void computeCageBooleanMask2D() throws InterruptedException {
		cageMask2D = cageROI2D.getBooleanMask2D(0, 0, 1, true);
	}

	// -------------------------------------

	public boolean xmlLoadCage(Node node, int index) {
		// Memory monitoring before loading
//		long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		// System.out.println(" Loading Cage " + index + " - Memory: " + (startMemory /
		// 1024 / 1024) + " MB");

		try {
			if (node == null) {
				System.err.println("ERROR: Null node provided for cage " + index);
				return false;
			}

			Element xmlVal = XMLUtil.getElement(node, "Cage" + index);
			if (xmlVal == null) {
				System.err.println("ERROR: Could not find Cage" + index + " element");
				return false;
			}

			// Load cage limits with error handling
			if (!xmlLoadCageLimits(xmlVal)) {
				System.err.println("ERROR: Failed to load cage limits for cage " + index);
				return false;
			}

			// Load cage properties with error handling
			if (!prop.xmlLoadCageParameters(xmlVal)) {
				System.err.println("ERROR: Failed to load cage parameters for cage " + index);
				return false;
			}

			// Set color from properties
			if (cageROI2D != null) {
				cageROI2D.setColor(prop.getColor());
			}

			// Load capillary IDs (new format)
			if (!xmlLoadCapillaryIDs(xmlVal)) {
				// Legacy format will be handled by migration tool
			}

			// Load spot IDs (new format)
			if (!xmlLoadSpotIDs(xmlVal)) {
				// Legacy format: spots are loaded via transparent fallback in Legacy
				// persistence classes
				// Users can manually save in new format when desired
			}

			// Load cage measures
			measures.loadFromXml(xmlVal);

			// Load fly positions (legacy format support)
			boolean flyPositionsLoaded = xmlLoadFlyPositions(xmlVal);
			if (flyPositionsLoaded) {
				System.out.println("Cage.xmlLoadCage() Successfully loaded fly positions for cage " + index);
			} else {
				System.out.println("Cage.xmlLoadCage() No fly positions found in XML for cage " + index);
			}

			// Memory monitoring after loading
//			long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
//			long memoryIncrease = endMemory - startMemory;
			// System.out.println(" Cage " + index + " loaded - Memory increase: " +
			// (memoryIncrease / 1024 / 1024) + " MB");
			// System.out.println(" Spots in cage: " + spotsArray.getSpotsCount());

			return true;

		} catch (Exception e) {
			System.err.println("ERROR loading cage " + index + ": " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean xmlSaveCage(Node node, int index) {
		// Memory monitoring before saving
//		long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		// System.out.println(" Saving Cage " + index + " - Memory: " + (startMemory /
		// 1024 / 1024) + " MB");

		try {
			if (node == null) {
				System.err.println("ERROR: Null node provided for cage " + index);
				return false;
			}

			Element xmlVal = XMLUtil.addElement(node, "Cage" + index);
			if (xmlVal == null) {
				System.err.println("ERROR: Could not create Cage" + index + " element");
				return false;
			}

			// Save cage limits with error handling
			if (!xmlSaveCageLimits(xmlVal)) {
				System.err.println("ERROR: Failed to save cage limits for cage " + index);
				return false;
			}

			// Save cage properties with error handling
			if (!prop.xmlSaveCageParameters(xmlVal)) {
				System.err.println("ERROR: Failed to save cage parameters for cage " + index);
				return false;
			}

			// Save capillary IDs (new format)
			if (!xmlSaveCapillaryIDs(xmlVal)) {
				System.err.println("ERROR: Failed to save capillary IDs for cage " + index);
				return false;
			}

			// Save spot IDs (new format)
			if (!xmlSaveSpotIDs(xmlVal)) {
				System.err.println("ERROR: Failed to save spot IDs for cage " + index);
				return false;
			}

			// Save cage measures
			measures.saveToXml(xmlVal);

			// Memory monitoring after saving
//			long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
//			long memoryIncrease = endMemory - startMemory;
			// System.out.println(" Cage " + index + " saved - Memory increase: " +
			// (memoryIncrease / 1024 / 1024) + " MB");
			// System.out.println(" Spots in cage: " + spotsArray.getSpotsCount());

			return true;

		} catch (Exception e) {
			System.err.println("ERROR saving cage " + index + ": " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean xmlLoadCageLimits(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_CAGELIMITS);
			if (xmlVal2 != null) {
				cageROI2D = (ROI2D) ROI.createFromXML(xmlVal2);
				if (cageROI2D != null) {
					cageROI2D.setSelected(false);
					// System.out.println(" Loaded cage ROI: " + cageROI2D.getName());
				} else {
					System.err.println("WARNING: Failed to create ROI from XML for cage limits");
				}
			} else {
				System.err.println("WARNING: No cage limits found in XML");
			}
			return true;

		} catch (Exception e) {
			System.err.println("ERROR loading cage limits: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean xmlSaveCageLimits(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.addElement(xmlVal, ID_CAGELIMITS);
			if (cageROI2D != null) {
				cageROI2D.setSelected(false);
				cageROI2D.saveToXML(xmlVal2);
				// System.out.println(" Saved cage ROI: " + cageROI2D.getName());
			} else {
				System.err.println("WARNING: No cage ROI to save");
			}
			return true;

		} catch (Exception e) {
			System.err.println("ERROR saving cage limits: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean xmlLoadFlyPositions(Element xmlVal) {
		Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_FLYPOSITIONS);
		if (xmlVal2 != null) {
			flyPositions.loadXYTseriesFromXML(xmlVal2);
			return true;
		}
		return false;
	}

	public boolean xmlSaveFlyPositions(Element xmlVal) {
		Element xmlVal2 = XMLUtil.addElement(xmlVal, ID_FLYPOSITIONS);
		flyPositions.saveXYTseriesToXML(xmlVal2);
		return true;
	}

	// -----------------------------------------
	// SpotID persistence methods

	private static final String ID_SPOTIDS = "SpotIDs";
	private static final String ID_NSPOTIDS = "N_spotIDs";
	private static final String ID_SPOTID_ = "spotID_";
	private static final String ID_CAPILLARYIDS = "CapillaryIDs";
	private static final String ID_NCAPILLARYIDS = "N_capillaryIDs";
	private static final String ID_CAPILLARYID_ = "capillaryID_";

	private boolean xmlSaveSpotIDs(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.addElement(xmlVal, ID_SPOTIDS);
			if (xmlVal2 == null) {
				return false;
			}
			XMLUtil.setElementIntValue(xmlVal2, ID_NSPOTIDS, spotIDs.size());
			for (int i = 0; i < spotIDs.size(); i++) {
				Element spotIDElement = XMLUtil.addElement(xmlVal2, ID_SPOTID_ + i);
				SpotID spotID = spotIDs.get(i);
				XMLUtil.setElementIntValue(spotIDElement, "id", spotID.getId());
			}
			return true;
		} catch (Exception e) {
			System.err.println("ERROR saving spot IDs: " + e.getMessage());
			return false;
		}
	}

	private boolean xmlLoadSpotIDs(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_SPOTIDS);
			if (xmlVal2 == null) {
				return false;
			}
			int nitems = XMLUtil.getElementIntValue(xmlVal2, ID_NSPOTIDS, 0);
			spotIDs.clear();
			for (int i = 0; i < nitems; i++) {
				Element spotIDElement = XMLUtil.getElement(xmlVal2, ID_SPOTID_ + i);
				if (spotIDElement != null) {
					// Try new format (just id)
					int id = XMLUtil.getElementIntValue(spotIDElement, "id", -1);
					if (id >= 0) {
						spotIDs.add(new SpotID(id));
					} else {
						// Legacy format (cageID, position) - will need migration
						int cageID = XMLUtil.getElementIntValue(spotIDElement, "cageID", -1);
						int position = XMLUtil.getElementIntValue(spotIDElement, "position", -1);
						if (cageID >= 0 && position >= 0) {
							// Legacy format: spotIDs will be rebuilt after spots are loaded
							// For now, skip adding legacy format IDs
						}
					}
				}
			}
			return true;
		} catch (Exception e) {
			System.err.println("ERROR loading spot IDs: " + e.getMessage());
			return false;
		}
	}

	private boolean xmlSaveCapillaryIDs(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.addElement(xmlVal, ID_CAPILLARYIDS);
			if (xmlVal2 == null) {
				return false;
			}
			XMLUtil.setElementIntValue(xmlVal2, ID_NCAPILLARYIDS, capillaryIDs.size());
			for (int i = 0; i < capillaryIDs.size(); i++) {
				Element capIDElement = XMLUtil.addElement(xmlVal2, ID_CAPILLARYID_ + i);
				CapillaryID capID = capillaryIDs.get(i);
				XMLUtil.setElementIntValue(capIDElement, "kymographIndex", capID.getKymographIndex());
			}
			return true;
		} catch (Exception e) {
			System.err.println("ERROR saving capillary IDs: " + e.getMessage());
			return false;
		}
	}

	private boolean xmlLoadCapillaryIDs(Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_CAPILLARYIDS);
			if (xmlVal2 == null) {
				return false;
			}
			int nitems = XMLUtil.getElementIntValue(xmlVal2, ID_NCAPILLARYIDS, 0);
			capillaryIDs.clear();
			for (int i = 0; i < nitems; i++) {
				Element capIDElement = XMLUtil.getElement(xmlVal2, ID_CAPILLARYID_ + i);
				if (capIDElement != null) {
					int kymographIndex = XMLUtil.getElementIntValue(capIDElement, "kymographIndex", -1);
					if (kymographIndex >= 0) {
						capillaryIDs.add(new CapillaryID(kymographIndex));
					}
				}
			}
			return true;
		} catch (Exception e) {
			System.err.println("ERROR loading capillary IDs: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Adds a SpotID to this cage if it's not already present.
	 */
	public void addSpotIDIfUnique(SpotID spotID) {
		if (spotID != null && !spotIDs.contains(spotID)) {
			spotIDs.add(spotID);
		}
	}

	// -----------------------------------------

	public String csvExportCageDescription(String sep) {
		StringBuffer sbf = new StringBuffer();
		List<String> row = new ArrayList<String>();
		row.add(prop.getStrCageNumber());
		// Handle null ROI name safely
		String roiName = (cageROI2D != null && cageROI2D.getName() != null) ? cageROI2D.getName()
				: "cage" + String.format("%03d", prop.getCageID());
		row.add(roiName);
		row.add(Integer.toString(prop.getCageNFlies()));
		row.add(Integer.toString(prop.getFlyAge()));
		row.add(prop.getComment());
		row.add(prop.getFlyStrain());
		row.add(prop.getFlySex());

		if (cageROI2D != null) {
			Polygon2D polygon = ((ROI2DPolygon) cageROI2D).getPolygon2D();
			row.add(Integer.toString(polygon.npoints));
			for (int i = 0; i < polygon.npoints; i++) {
				row.add(Integer.toString((int) polygon.xpoints[i]));
				row.add(Integer.toString((int) polygon.ypoints[i]));
			}
		} else
			row.add("0");
		sbf.append(String.join(sep, row));
		sbf.append("\n");
		return sbf.toString();
	}

	// --------------------------------------------------------

	public int addEllipseSpot(Point2D.Double center, int radius, Spots allSpots) {
		int position = spotIDs.size();
		// Add spot to global SpotsArray first to get unique ID
		if (allSpots != null) {
			int uniqueSpotID = allSpots.getNextUniqueSpotID();
			SpotID spotUniqueID = new SpotID(uniqueSpotID);
			Spot spot = createEllipseSpot(position, center, radius);
			spot.setSpotUniqueID(spotUniqueID);
			allSpots.addSpot(spot);
			spotIDs.add(spotUniqueID);
		}
		return spotIDs.size();
	}

	private Spot createEllipseSpot(int cagePosition, Point2D.Double center, int radius) {
		Ellipse2D ellipse = new Ellipse2D.Double(center.x, center.y, 2 * radius, 2 * radius);
		ROI2DEllipse roiEllipse = new ROI2DEllipse(ellipse);
		roiEllipse.setName(SpotString.createSpotString(prop.getCageID(), cagePosition));
		Spot spot = new Spot(roiEllipse);
		spot.getProperties().setCageID(prop.getCageID());
		spot.getProperties().setCagePosition(cagePosition);
		spot.getProperties().setSpotRadius(radius);
		spot.getProperties().setSpotXCoord((int) center.getX());
		spot.getProperties().setSpotYCoord((int) center.getY());
		try {
			spot.getProperties().setSpotNPixels((int) roiEllipse.getNumberOfPoints());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return spot;
	}

	public Spot getSpotFromRoiName(String name, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		int cagePosition = SpotString.getSpotCagePositionFromSpotName(name);
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			if (spot.getProperties().getCagePosition() == cagePosition)
				return spot;
		}
		return null;
	}

	public void mapSpotsToCageColumnRow(Spots allSpots) {
		if (allSpots == null || cageROI2D == null) {
			return;
		}
		Rectangle rect = cageROI2D.getBounds();
		int deltaX = rect.width / 8;
		int deltaY = rect.height / 4;
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			Rectangle rectSpot = spot.getRoi().getBounds();
			spot.getProperties().setCageColumn((rectSpot.x - rect.x) / deltaX);
			spot.getProperties().setCageRow((rectSpot.y - rect.y) / deltaY);
		}
	}

	public void cleanUpSpotNames(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		List<Spot> spots = getSpotList(allSpots);
		for (int i = 0; i < spots.size(); i++) {
			Spot spot = spots.get(i);
			int originalPosition = spot.getProperties().getCagePosition();
			spot.setName(prop.getCageID(), originalPosition);
			spot.getProperties().setCageID(prop.getCageID());
			spot.getProperties().setCagePosition(i);
		}
	}

	public void updateSpotsStimulus_i(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		ArrayList<String> stimulusArray = new ArrayList<String>(8);
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			String test = spot.getProperties().getStimulus();
			stimulusArray.add(test);
			spot.getProperties().setStimulusI(test + "_" + findNumberOfIdenticalItems(test, stimulusArray));
		}
	}

	private int findNumberOfIdenticalItems(String test, ArrayList<String> array) {
		int items = 0;
		for (String element : array)
			if (element.equals(test))
				items++;
		return items;
	}

	public Spot combineSpotsWithSameStimConc(String stim, String conc, Spots allSpots) {
		if (allSpots == null) {
			return null;
		}
		Spot spotCombined = null;
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spotSource : spots) {
			if (stim.equals(spotSource.getProperties().getStimulus())
					&& conc.equals(spotSource.getProperties().getConcentration())) {
				if (spotCombined == null) {
					spotCombined = new Spot(spotSource, true);
				} else {
					spotCombined.addMeasurements(spotSource);
				}
			}
		}
		return spotCombined;
	}

	public void normalizeSpotMeasures(Spots allSpots) {
		if (allSpots == null) {
			return;
		}
		List<Spot> spots = getSpotList(allSpots);
		for (Spot spot : spots) {
			spot.normalizeMeasures();
		}
	}

	public Spot createSpotPI(Spot spot1, Spot spot2) {
		if (spot1 == null || spot2 == null)
			return null;
		Spot spotPI = new Spot();
		spotPI.getProperties().setCageID(spot1.getProperties().getCageID());
		spotPI.getProperties().setName("PI");
		spotPI.getProperties().setStimulus("PI");
		spotPI.getProperties().setConcentration(
				spot1.getCombinedStimulusConcentration() + " / " + spot2.getCombinedStimulusConcentration());
		spotPI.computePI(spot1, spot2);
		return spotPI;
	}

	public Spot createSpotSUM(Spot spot1, Spot spot2) {
		if (spot1 == null || spot2 == null)
			return null;
		Spot spotSUM = new Spot();
		spotSUM.getProperties().setCageID(spot1.getProperties().getCageID());
		spotSUM.getProperties().setName("SUM");
		spotSUM.getProperties().setStimulus("SUM");
		spotSUM.getProperties().setConcentration(
				spot1.getCombinedStimulusConcentration() + " / " + spot2.getCombinedStimulusConcentration());
		spotSUM.computeSUM(spot1, spot2);
		return spotSUM;
	}

	// ----------------------------------------
	// CSV Import methods

	public void csvImport_CAGE_Header(String[] data) {
		if (data == null || data.length < 3) {
			System.err.println(
					"Cage:csvImport_CAGE_Header() Invalid data array length: " + (data == null ? "null" : data.length));
			return;
		}

		int i = 0;
		// Parse cageID (as String first, then convert to int)
		String cageIDStr = data[i];
		i++;
		try {
			int cageIDInt = Integer.valueOf(cageIDStr);
			prop.setCageID(cageIDInt);
			prop.setStrCageNumber(cageIDStr);
		} catch (NumberFormatException e) {
			System.err.println("Cage:csvImport_CAGE_Header() Invalid cageID: " + cageIDStr);
		}

		// Parse nFlies
		if (i < data.length) {
			try {
				int nFlies = Integer.valueOf(data[i]);
				prop.setCageNFlies(nFlies);
				i++;
			} catch (NumberFormatException e) {
				System.err.println("Cage:csvImport_CAGE_Header() Invalid nFlies: " + data[i]);
				i++;
			}
		}

		// Parse age
		if (i < data.length) {
			try {
				int age = Integer.valueOf(data[i]);
				prop.setFlyAge(age);
				i++;
			} catch (NumberFormatException e) {
				System.err.println("Cage:csvImport_CAGE_Header() Invalid age: " + data[i]);
				i++;
			}
		}

		// Parse comment
		if (i < data.length) {
			prop.setComment(data[i]);
			i++;
		}

		// Parse strain
		if (i < data.length) {
			prop.setFlyStrain(data[i]);
			i++;
		}

		// Parse sex (may be labeled as "sect" in legacy format)
		if (i < data.length) {
			prop.setFlySex(data[i]);
			i++;
		}

		// Parse ROI name (if present in legacy CAGES section format)
		String cageROI_name = "";
		if (i < data.length) {
			cageROI_name = data[i];
			i++;
		}

		// Parse npoints (if present in legacy CAGES section format)
		int npoints = 0;
		if (i < data.length) {
			try {
				npoints = Integer.valueOf(data[i]);
				i++;
			} catch (NumberFormatException e) {
				// npoints not present or invalid - that's OK, ROI might be loaded from XML
			}
		}

		// Parse polygon vertices (x, y pairs) if present in legacy CAGES section format
		if (npoints > 0 && i + (npoints * 2) <= data.length) {
			double[] x = new double[npoints];
			double[] y = new double[npoints];
			for (int j = 0; j < npoints; j++) {
				try {
					x[j] = Double.valueOf(data[i]);
					i++;
					y[j] = Double.valueOf(data[i]);
					i++;
				} catch (NumberFormatException e) {
					System.err.println("Cage:csvImport_CAGE_Header() Invalid coordinate at index " + j);
					break;
				}
			}
			Polygon2D polygon = new Polygon2D(x, y, npoints);
			cageROI2D = new ROI2DPolygon(polygon);
			if (cageROI_name != null && !cageROI_name.isEmpty()) {
				cageROI2D.setName(cageROI_name);
			}
			cageROI2D.setColor(Color.MAGENTA);
		}
	}

	public void csvImport_MEASURE_Data_v0(EnumCageMeasures measureType, String[] data, boolean complete) {
		switch (measureType) {
		case POSITION:
			if (complete) {
				flyPositions.csvImport_Rectangle_FromRow(data, 1);
			} else {
				flyPositions.csvImport_XY_FromRow(data, 1);
			}
			break;
		default:
			break;
		}
	}

	public void csvImport_MEASURE_Data_Parameters(String[] data) {
		flyPositions.cvsImport_Parameter_FromRow(data);
	}

	// ----------------------------------------

	@Override
	public void close() throws Exception {
		if (closed.compareAndSet(false, true)) {
			Logger.debug("Closing cage: "); // + data.getName());
			// Cleanup resources if needed
			flyPositions.clear();
		}
	}
}
