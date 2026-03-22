package plugins.fmp.multitools.experiment.cages;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPositions;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.ROI2D.ROIPersistenceUtils;
import plugins.fmp.multitools.tools.ROI2D.ROIType;

/**
 * Legacy persistence for cages files. Handles loading from legacy XML and CSV
 * formats: MCdrosotrack.xml, CagesMeasures.csv
 */
public class CagesPersistenceLegacy {

	private static final String ID_CAGES = "Cages";
	private static final String ID_NCAGES = "n_cages";
	private static final String ID_NCAGESALONGX = "N_cages_along_X";
	private static final String ID_NCAGESALONGY = "N_cages_along_Y";
	private static final String ID_NCOLUMNSPERCAGE = "N_columns_per_cage";
	private static final String ID_NROWSPERCAGE = "N_rows_per_cage";
	private static final String ID_DROSOTRACK = "drosoTrack";
	private static final String ID_CAGELIMITS = "Cage_Limits";
	private static final String ID_CAGELIMITS_ALT = "CageLimits";
	private static final String ID_FLYDETECTED = "Fly_Detected";
	private static final String ID_FLYPOSITIONS = "FlyPositions";
	private static final String ID_NBITEMS = "nb_items";
	private static final String ID_SPOTIDS = "SpotIDs";
	private static final String ID_NSPOTIDS = "N_spotIDs";
	private static final String ID_SPOTID_ = "spotID_";
	private static final String ID_CAPILLARYIDS = "CapillaryIDs";
	private static final String ID_NCAPILLARYIDS = "N_capillaryIDs";
	private static final String ID_CAPILLARYID_ = "capillaryID_";

	private static final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";
	/** Alternate on-disk name seen in older datasets (case-sensitive FS). */
	private static final String ID_MCDROSOTRACK_XML_LOWER = "mcdrosotrack.xml";
	private static final String csvSep = ";";

	/**
	 * Resolves the path to a MCdrosotrack XML file in {@code directory}, trying
	 * canonical and lowercase filenames.
	 *
	 * @return absolute path to an existing file, or {@code null}
	 */
	public static String resolveMcDrosotrackXmlPath(String directory) {
		if (directory == null) {
			return null;
		}
		for (String name : new String[] { ID_MCDROSOTRACK_XML, ID_MCDROSOTRACK_XML_LOWER }) {
			File f = new File(directory + File.separator + name);
			if (f.isFile()) {
				return f.getAbsolutePath();
			}
		}
		return null;
	}

	/**
	 * Legacy files place {@code <Cages>} either under the document root or under
	 * {@code <drosoTrack>}.
	 */
	private static Element findCagesElementInDrosoTrackDocument(Node rootNode) {
		if (rootNode == null) {
			return null;
		}
		Element c = XMLUtil.getElement(rootNode, ID_CAGES);
		if (c != null) {
			return c;
		}
		Node droso = XMLUtil.getElement(rootNode, ID_DROSOTRACK);
		if (droso != null) {
			return XMLUtil.getElement(droso, ID_CAGES);
		}
		return null;
	}
	private static final String ID_CAGESMEASURES_CSV = "CagesMeasures.csv";
	private static final String ID_CAGESARRAY_CSV = "CagesArray.csv";
	private static final String ID_CAGESARRAYMEASURES_CSV = "CagesArrayMeasures.csv";

	/**
	 * Loads cages from legacy CSV format (CagesMeasures.csv).
	 * 
	 * @param cages     The Cages to populate
	 * @param directory The directory containing the CSV file
	 * @return true if successful
	 */
	public static boolean csvLoadCagesMeasures(Cages cages, String directory) throws Exception {
		String pathToCsv = directory + File.separator + ID_CAGESMEASURES_CSV;
		File csvFile = new File(pathToCsv);
		if (!csvFile.isFile()) {
			return false;
		}

		Logger.info("CagesPersistenceLegacy:csvLoadCagesMeasures() Starting with " + cages.cagesList.size()
				+ " existing cages");

		BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
		String row;
		String sep = csvSep;
		int descriptionCount = 0;
		int cageCount = 0;
		int positionCount = 0;
		try {
			while ((row = csvReader.readLine()) != null) {
				if (row.length() > 0 && row.charAt(0) == '#')
					sep = String.valueOf(row.charAt(1));

				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#")) {
					if (data.length > 1) {
						switch (data[1]) {
						case "DIMENSION":
							csvLoad_DIMENSION(cages, csvReader, sep);
							break;
						case "DESCRIPTION":
							descriptionCount++;
							csvLoad_DESCRIPTION(cages, csvReader, sep);
							break;
						case "CAGE":
						case "CAGES":
							cageCount++;
							csvLoad_CAGE(cages, csvReader, sep);
							break;
						case "POSITION":
							positionCount++;
							csvLoad_Measures(cages, csvReader, EnumCageMeasures.POSITION, sep);
							break;
						default:
							break;
						}
					}
				}
			}
		} finally {
			csvReader.close();
		}

		Logger.info("CagesPersistenceLegacy:csvLoadCagesMeasures() Loaded: " + descriptionCount + " descriptions, "
				+ cageCount + " cages, " + positionCount + " positions");
		return descriptionCount > 0 || cageCount > 0 || positionCount > 0;
	}

	/**
	 * Loads cages from MCdrosotrack.xml (MultiCAFE format). Uses capillary IDs, not
	 * spot IDs.
	 */
	public static boolean xmlLoadCagesFromMCdrosotrack(Cages cages, Node node) {
		try {
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal != null) {
				cages.cagesList.clear();
				int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
				if (ncages < 0) {
					Logger.error(
							"CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() ERROR: Invalid ncages: " + ncages);
					return false;
				}
				cages.nCagesAlongX = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGX, cages.nCagesAlongX);
				cages.nCagesAlongY = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGY, cages.nCagesAlongY);
				cages.nColumnsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NCOLUMNSPERCAGE, cages.nColumnsPerCage);
				cages.nRowsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NROWSPERCAGE, cages.nRowsPerCage);

				int loadedCages = 0;
				for (int index = 0; index < ncages; index++) {
					try {
						Cage cage = new Cage();
						if (xmlLoadCageFromMCdrosotrackElement(cage, xmlVal, index)) {
							cages.cagesList.add(cage);
							loadedCages++;
						} else {
							Logger.warn("CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() Failed cage " + index);
						}
					} catch (Exception e) {
						Logger.error("CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() Error cage " + index + ": "
								+ e.getMessage(), e);
					}
				}
				Logger.info("CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() Loaded " + loadedCages + " cages");
				return loadedCages > 0;
			}

			Node drosoTrackNode = XMLUtil.getElement(node, ID_DROSOTRACK);
			if (drosoTrackNode != null)
				return xmlLoadCages_v0_MCdrosotrack(cages, drosoTrackNode);

			Logger.warn("CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() No Cages or drosoTrack element");
			return false;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCagesFromMCdrosotrack() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads cages from MS96_cages.xml (multiSPOTS96 format). Uses spot IDs, not
	 * capillary IDs.
	 */
	public static boolean xmlLoadCagesFromMS96Cages(Cages cages, Node node) {
		try {
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal == null) {
				Logger.warn("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() No Cages element");
				return false;
			}
			cages.cagesList.clear();
			int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
			if (ncages < 0) {
				Logger.error("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() ERROR: Invalid ncages: " + ncages);
				return false;
			}
			cages.nCagesAlongX = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGX, cages.nCagesAlongX);
			cages.nCagesAlongY = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGY, cages.nCagesAlongY);
			cages.nColumnsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NCOLUMNSPERCAGE, cages.nColumnsPerCage);
			cages.nRowsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NROWSPERCAGE, cages.nRowsPerCage);

			int loadedCages = 0;
			for (int index = 0; index < ncages; index++) {
				try {
					Cage cage = new Cage();
					if (xmlLoadCageFromMS96CagesElement(cage, xmlVal, index)) {
						cages.cagesList.add(cage);
						loadedCages++;
					} else {
						Logger.warn("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() Failed cage " + index);
					}
				} catch (Exception e) {
					Logger.error("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() Error cage " + index + ": "
							+ e.getMessage(), e);
				}
			}
			Logger.info("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() Loaded " + loadedCages + " cages");
			return loadedCages > 0;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCagesFromMS96Cages() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads cages from legacy v0 MCdrosotrack format (with drosoTrack element).
	 */
	private static boolean xmlLoadCages_v0_MCdrosotrack(Cages cages, Node node) {
		try {
			cages.cagesList.clear();
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal != null) {
				int nCages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
				for (int index = 0; index < nCages; index++) {
					Cage cage = new Cage();
					xmlLoadCageFromMCdrosotrackElement(cage, xmlVal, index);
					cages.cagesList.add(cage);
				}
				return true;
			} else {
				// Very old format: separate Cage_Limits and Fly_Detected sections
				java.util.List<icy.roi.ROI2D> cageLimitROIList = new java.util.ArrayList<icy.roi.ROI2D>();
				if (xmlLoadCageLimits_v0(node, cageLimitROIList)) {
					java.util.List<FlyPositions> flyPositionsList = new java.util.ArrayList<FlyPositions>();
					xmlLoadFlyPositions_v0(node, flyPositionsList);
					transferDataToCages_v0(cages, cageLimitROIList, flyPositionsList);
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCages_v0() ERROR: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads cage limits from legacy v0 format.
	 */
	private static boolean xmlLoadCageLimits_v0(Node node, java.util.List<icy.roi.ROI2D> cageLimitROIList) {
		if (node == null)
			return false;
		Element xmlVal = XMLUtil.getElement(node, ID_CAGELIMITS);
		if (xmlVal == null)
			return false;
		cageLimitROIList.clear();
		int nb_items = XMLUtil.getAttributeIntValue(xmlVal, ID_NBITEMS, 0);
		for (int i = 0; i < nb_items; i++) {
			plugins.kernel.roi.roi2d.ROI2DPolygon roi = (plugins.kernel.roi.roi2d.ROI2DPolygon) icy.roi.ROI
					.create("plugins.kernel.roi.roi2d.ROI2DPolygon");
			Element subnode = XMLUtil.getElement(xmlVal, "cage" + i);
			if (subnode != null) {
				roi.loadFromXML(subnode);
				cageLimitROIList.add((icy.roi.ROI2D) roi);
			}
		}
		return true;
	}

	/**
	 * Loads fly positions from legacy v0 format.
	 */
	private static boolean xmlLoadFlyPositions_v0(Node node, java.util.List<FlyPositions> flyPositionsList) {
		if (node == null)
			return false;
		Element xmlVal = XMLUtil.getElement(node, ID_FLYDETECTED);
		if (xmlVal == null)
			return false;
		flyPositionsList.clear();
		int nb_items = XMLUtil.getAttributeIntValue(xmlVal, ID_NBITEMS, 0);
		int ielement = 0;
		for (int i = 0; i < nb_items; i++) {
			Element subnode = XMLUtil.getElement(xmlVal, "cage" + ielement);
			if (subnode != null) {
				FlyPositions pos = new FlyPositions();
				pos.loadXYTseriesFromXML(subnode);
				flyPositionsList.add(pos);
			}
			ielement++;
		}
		return true;
	}

	/**
	 * Transfers data from legacy v0 format lists to cages.
	 */
	private static void transferDataToCages_v0(Cages cages, java.util.List<icy.roi.ROI2D> cageLimitROIList,
			java.util.List<FlyPositions> flyPositionsList) {
		cages.cagesList.clear();
		java.util.Collections.sort(cageLimitROIList, new Comparators.ROI2D_Name());
		int nCages = cageLimitROIList.size();
		for (int index = 0; index < nCages; index++) {
			Cage cage = new Cage();
			cage.setCageRoi(cageLimitROIList.get(index));
			if (index < flyPositionsList.size()) {
				cage.flyPositions = flyPositionsList.get(index);
			} else {
				cage.flyPositions = new FlyPositions();
			}
			cages.cagesList.add(cage);
		}
	}

	/**
	 * Loads a single cage from MCdrosotrack.xml format (MultiCAFE). Loads capillary
	 * IDs, not spot IDs.
	 */
	public static boolean xmlLoadCageFromMCdrosotrackElement(Cage cage, Element cagesElement, int index) {
		try {
			Element xmlVal = XMLUtil.getElement(cagesElement, "Cage" + index);
			if (xmlVal == null)
				xmlVal = XMLUtil.getElement(cagesElement, "cage" + index);
			if (xmlVal == null) {
				Logger.debug("CagesPersistenceLegacy: Could not find Cage" + index + " element");
				return false;
			}
			if (!xmlLoadCageLimitsFromElement(cage, xmlVal))
				Logger.debug("CagesPersistenceLegacy: Failed to load cage limits for cage " + index);
			if (!cage.getProperties().xmlLoadCageParameters(xmlVal)) {
				Logger.debug("CagesPersistenceLegacy: Failed to load cage parameters for cage " + index);
				return false;
			}
			if (cage.getRoi() != null)
				cage.getRoi().setColor(cage.getProperties().getColor());
			xmlLoadCapillaryIDsFromElement(cage, xmlVal);
			cage.measures.loadFromXml(xmlVal);
			Element flyEl = XMLUtil.getElement(xmlVal, ID_FLYPOSITIONS);
			if (flyEl != null)
				cage.getFlyPositions().loadXYTseriesFromXML(flyEl);
			return true;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCageFromMCdrosotrackElement() Error loading cage " + index
					+ ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads a single cage from MS96_cages.xml format (multiSPOTS96). Loads spot
	 * IDs, not capillary IDs.
	 */
	public static boolean xmlLoadCageFromMS96CagesElement(Cage cage, Element cagesElement, int index) {
		try {
			Element xmlVal = XMLUtil.getElement(cagesElement, "Cage" + index);
			if (xmlVal == null)
				xmlVal = XMLUtil.getElement(cagesElement, "cage" + index);
			if (xmlVal == null) {
				Logger.debug("CagesPersistenceLegacy: Could not find Cage" + index + " element");
				return false;
			}

			if (!xmlLoadCageLimitsFromElement(cage, xmlVal))
				Logger.debug("CagesPersistenceLegacy: Failed to load cage limits for cage " + index);

			ensureCageRoiNameForDisplay(cage.getRoi(), index);

			if (!cage.getProperties().xmlLoadCageParameters(xmlVal)) {
				Logger.debug("CagesPersistenceLegacy: Failed to load cage parameters for cage " + index);
				return false;
			}

			if (cage.getRoi() != null)
				cage.getRoi().setColor(cage.getProperties().getColor());

			xmlLoadSpotIDsFromElement(cage, xmlVal);

			return true;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCageFromMS96CagesElement() Error loading cage " + index + ": "
					+ e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Ensures cage ROI name contains "cage" for displaySpecificROIs matching.
	 * displaySpecificROIs uses case-sensitive name.contains("cage").
	 */
	public static void ensureCageRoiNameForDisplay(icy.roi.ROI2D roi, int cageIndex) {
		if (roi == null)
			return;
		String name = roi.getName();
		if (name == null || !name.toLowerCase().contains("cage"))
			roi.setName("cage" + String.format("%03d", cageIndex));
	}

	/**
	 * Loads cage ROI from CageLimits element. Used by format-specific cage loaders
	 * and xmlLoadCagesROIsOnly.
	 */
	public static boolean xmlLoadCageLimitsFromElement(Cage cage, Element cageElement) {
		try {
			String[] tags = { ID_CAGELIMITS_ALT, ID_CAGELIMITS };
			for (String tag : tags) {
				Element xmlVal2 = XMLUtil.getElement(cageElement, tag);
				if (xmlVal2 != null) {
					ROI2D roi = ROI2DUtilities.loadFromXML_ROI(xmlVal2);
					if (roi == null)
						roi = (ROI2D) ROI.createFromXML(xmlVal2);
					if (roi == null && xmlVal2.hasChildNodes()) {
						Node first = xmlVal2.getFirstChild();
						while (first != null) {
							if (first.getNodeType() == Node.ELEMENT_NODE) {
								roi = (ROI2D) ROI.createFromXML(first);
								if (roi != null)
									break;
							}
							first = first.getNextSibling();
						}
					}
					if (roi == null)
						roi = CagesPersistenceMS96Legacy.parseCageLimitsFromPointsFormat(xmlVal2);
					if (roi != null) {
						roi.setSelected(false);
						cage.setCageRoi(roi);
						return true;
					}
				}
			}
			return true;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCageLimitsFromElement() Error: " + e.getMessage(), e);
			return false;
		}
	}

	private static void xmlLoadSpotIDsFromElement(Cage cage, Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_SPOTIDS);
			if (xmlVal2 == null)
				return;
			int nitems = XMLUtil.getElementIntValue(xmlVal2, ID_NSPOTIDS, 0);
			List<SpotID> spotIDs = new ArrayList<>();
			for (int i = 0; i < nitems; i++) {
				Element spotIDElement = XMLUtil.getElement(xmlVal2, ID_SPOTID_ + i);
				if (spotIDElement != null) {
					int id = XMLUtil.getElementIntValue(spotIDElement, "id", -1);
					if (id >= 0)
						spotIDs.add(new SpotID(id));
				}
			}
			cage.setSpotIDs(spotIDs);
		} catch (Exception e) {
			Logger.debug("CagesPersistenceLegacy:xmlLoadSpotIDsFromElement() " + e.getMessage());
		}
	}

	private static void xmlLoadCapillaryIDsFromElement(Cage cage, Element xmlVal) {
		try {
			Element xmlVal2 = XMLUtil.getElement(xmlVal, ID_CAPILLARYIDS);
			if (xmlVal2 == null)
				return;
			int nitems = XMLUtil.getElementIntValue(xmlVal2, ID_NCAPILLARYIDS, 0);
			List<CapillaryID> capillaryIDs = new ArrayList<>();
			for (int i = 0; i < nitems; i++) {
				Element capIDElement = XMLUtil.getElement(xmlVal2, ID_CAPILLARYID_ + i);
				if (capIDElement != null) {
					int kymographIndex = XMLUtil.getElementIntValue(capIDElement, "kymographIndex", -1);
					if (kymographIndex >= 0)
						capillaryIDs.add(new CapillaryID(kymographIndex));
				}
			}
			cage.setCapillaryIDs(capillaryIDs);
		} catch (Exception e) {
			Logger.debug("CagesPersistenceLegacy:xmlLoadCapillaryIDsFromElement() " + e.getMessage());
		}
	}

	/**
	 * Loads cages from MCdrosotrack.xml (MultiCAFE legacy format).
	 */
	public static boolean xmlReadCagesFromMCdrosotrackXml(Cages cages, String path) {
		String pathToUse = pickMcDrosotrackPathForLoading(path);
		if (pathToUse == null)
			return false;
		try {
			Document doc = XMLUtil.loadDocument(pathToUse);
			if (doc == null) {
				Logger.warn("CagesPersistenceLegacy:xmlReadCagesFromMCdrosotrackXml() Could not load: " + pathToUse);
				return false;
			}
			return xmlLoadCagesFromMCdrosotrack(cages, XMLUtil.getRootElement(doc));
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlReadCagesFromMCdrosotrackXml() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Accepts a concrete file path, a directory, or a missing file path whose parent
	 * directory contains MCdrosotrack.xml / mcdrosotrack.xml.
	 */
	private static String pickMcDrosotrackPathForLoading(String pathOrDirectory) {
		if (pathOrDirectory == null) {
			return null;
		}
		File f = new File(pathOrDirectory);
		if (f.isFile()) {
			return f.getAbsolutePath();
		}
		if (f.isDirectory()) {
			return resolveMcDrosotrackXmlPath(f.getAbsolutePath());
		}
		File parent = f.getParentFile();
		if (parent != null) {
			return resolveMcDrosotrackXmlPath(parent.getAbsolutePath());
		}
		return null;
	}

	/**
	 * Loads cages from MS96_cages.xml (multiSPOTS96 legacy format).
	 */
	public static boolean xmlReadCagesFromMS96CagesXml(Cages cages, String path) {
		if (path == null)
			return false;
		File file = new File(path);
		if (!file.exists())
			return false;
		try {
			Document doc = XMLUtil.loadDocument(path);
			if (doc == null) {
				Logger.warn("CagesPersistenceLegacy:xmlReadCagesFromMS96CagesXml() Could not load: " + path);
				return false;
			}
			return xmlLoadCagesFromMS96Cages(cages, XMLUtil.getRootElement(doc));
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlReadCagesFromMS96CagesXml() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads only ROIs (cage limits) from legacy XML file.
	 * 
	 * @param cages    The Cages to populate ROIs for
	 * @param tempname The path to the XML file
	 * @return true if successful
	 */
	public static boolean xmlLoadCagesROIsOnly(Cages cages, String tempname) {
		if (tempname == null) {
			return false;
		}

		String pathToUse = pickMcDrosotrackPathForLoading(tempname);
		if (pathToUse == null) {
			return true; // File doesn't exist yet, that's OK
		}

		try {
			final Document doc = XMLUtil.loadDocument(pathToUse);
			if (doc == null) {
				Logger.warn("CagesPersistenceLegacy:xmlLoadCagesROIsOnly() Could not load XML document: " + pathToUse);
				return false;
			}

			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlVal = findCagesElementInDrosoTrackDocument(rootNode);
			if (xmlVal == null) {
				Logger.warn(
						"CagesPersistenceLegacy:xmlLoadCagesROIsOnly() Could not find Cages element in XML (root or drosoTrack)");
				return false;
			}

			// Load layout information
			cages.nCagesAlongX = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGX, cages.nCagesAlongX);
			cages.nCagesAlongY = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGESALONGY, cages.nCagesAlongY);
			cages.nColumnsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NCOLUMNSPERCAGE, cages.nColumnsPerCage);
			cages.nRowsPerCage = XMLUtil.getAttributeIntValue(xmlVal, ID_NROWSPERCAGE, cages.nRowsPerCage);

			// Load ROIs and match them to existing cages by index
			int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
			int loadedROIs = 0;
			for (int index = 0; index < ncages && index < cages.cagesList.size(); index++) {
				try {
					Cage cage = cages.cagesList.get(index);
					if (cage == null) {
						continue;
					}

					Element cageElement = XMLUtil.getElement(xmlVal, "Cage" + index);
					if (cageElement != null) {
						boolean roiLoaded = xmlLoadCageLimitsFromElement(cage, cageElement);
						if (roiLoaded) {
							loadedROIs++;
						}
					}
				} catch (Exception e) {
					Logger.warn(
							"CagesPersistenceLegacy:xmlLoadCagesROIsOnly() WARNING: Failed to load ROI for cage at index "
									+ index);
				}
			}

			return loadedROIs > 0 || ncages == 0;
		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCagesROIsOnly() ERROR during XML loading: " + e.getMessage(),
					e);
			return false;
		}
	}

	/**
	 * Loads only fly positions from legacy XML file.
	 * 
	 * @param cages    The Cages to populate fly positions for
	 * @param tempname The path to the XML file
	 * @return true if successful
	 */
	public static boolean xmlLoadFlyPositionsFromXML(Cages cages, String tempname) {
		String pathToUse = pickMcDrosotrackPathForLoading(tempname);
		if (pathToUse == null) {
			return false;
		}

		try {
			final Document doc = XMLUtil.loadDocument(pathToUse);
			if (doc == null) {
				Logger.warn(
						"CagesPersistenceLegacy:xmlLoadFlyPositionsFromXML() Could not load XML document: " + pathToUse);
				return false;
			}

			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlVal = findCagesElementInDrosoTrackDocument(rootNode);
			if (xmlVal == null) {
				Logger.warn(
						"CagesPersistenceLegacy:xmlLoadFlyPositionsFromXML() Could not find Cages element in XML (root or drosoTrack)");
				return false;
			}

			// Load fly positions and match them to existing cages by index
			int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
			int loadedFlyPositions = 0;
			for (int index = 0; index < ncages && index < cages.cagesList.size(); index++) {
				try {
					Cage cage = cages.cagesList.get(index);
					if (cage == null) {
						continue;
					}

					Element cageElement = XMLUtil.getElement(xmlVal, "Cage" + index);
					if (cageElement != null) {
						Element flyEl = XMLUtil.getElement(cageElement, ID_FLYPOSITIONS);
						boolean flyPositionsLoaded = flyEl != null
								&& cage.getFlyPositions().loadXYTseriesFromXML(flyEl);
						if (flyPositionsLoaded) {
							loadedFlyPositions++;
						}
					}
				} catch (Exception e) {
					Logger.warn(
							"CagesArrayPersistenceLegacy:xmlLoadFlyPositionsFromXML() WARNING: Failed to load fly positions for cage at index "
									+ index);
				}
			}

			if (loadedFlyPositions > 0) {
				Logger.info("CagesArrayPersistenceLegacy:xmlLoadFlyPositionsFromXML() Loaded fly positions for "
						+ loadedFlyPositions + " cages");
			}
			return loadedFlyPositions > 0;
		} catch (Exception e) {
			Logger.error("CagesArrayPersistenceLegacy:xmlLoadFlyPositionsFromXML() ERROR during XML loading: "
					+ e.getMessage(), e);
			return false;
		}
	}

	// CSV loading helper methods

	static void csvLoad_DIMENSION(Cages cages, BufferedReader csvReader, String sep) {
		String row;
		try {
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#"))
					break;

				if (data.length > 0) {
					String test = data[0].substring(0, Math.min(data[0].length(), 7));
					if (test.equals("n cages") || test.equals("n cells")) {
						if (data.length > 1) {
							try {
								int ncages = Integer.valueOf(data[1]);
								if (ncages >= cages.cagesList.size()) {
									cages.cagesList.ensureCapacity(ncages);
								} else {
									cages.cagesList.subList(ncages, cages.cagesList.size()).clear();
								}
							} catch (NumberFormatException e) {
								Logger.warn(
										"CagesPersistenceLegacy:csvLoad_DIMENSION() Invalid n_cages value: " + data[1]);
							}
						}
						break;
					}
				}
			}
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvLoad_DIMENSION() Error: " + e.getMessage(), e);
		}
	}

	static void csvLoad_DESCRIPTION(Cages cages, BufferedReader csvReader, String sep) {
		String row;
		try {
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#"))
					break;

				if (data.length > 0) {
					String test = data[0].substring(0, Math.min(data[0].length(), 7));
					if (test.equals("n cages") || test.equals("n cells")) {
						if (data.length > 1) {
							int ncages = Integer.valueOf(data[1]);
							if (ncages >= cages.cagesList.size()) {
								cages.cagesList.ensureCapacity(ncages);
							} else {
								cages.cagesList.subList(ncages, cages.cagesList.size()).clear();
							}
						}
					}
				}
			}
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvLoad_DESCRIPTION() Error: " + e.getMessage(), e);
		}
	}

	static void csvLoad_CAGE(Cages cages, BufferedReader csvReader, String sep) {
		String row;
		try {
			row = csvReader.readLine();
			if (row == null) {
				return;
			}

			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#"))
					break;

				if (data.length > 0) {
					Cage cage = getCagefromID(cages, data[0]);
					cage.csvImport_CAGE_Header(data);
				}
			}
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvLoad_CAGE() Error: " + e.getMessage(), e);
		}
	}

	private static Cage getCagefromID(Cages cages, String data) {
		int cageID = 0;
		try {
			cageID = Integer.valueOf(data);
		} catch (NumberFormatException e) {
			Logger.warn("CagesPersistenceLegacy:csvLoad_CAGE() Invalid integer input: " + data);
			cageID = -1;
		}
		Cage cage = cages.getCageFromID(cageID);
		if (cage == null) {
			cage = new Cage();
			cages.cagesList.add(cage);
		}
		return cage;
	}

	static void csvLoad_Measures(Cages cages, BufferedReader csvReader, EnumCageMeasures measureType, String sep) {
		String row;
		try {
			row = csvReader.readLine();
			boolean complete = (row != null && row.contains("w(i)"));
			boolean v0 = (row != null && row.contains("x(i)"));

			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#")) {
					return;
				}

				if (data.length > 0) {
					int cageID = -1;
					try {
						cageID = Integer.valueOf(data[0]);
					} catch (NumberFormatException e) {
						Logger.warn("CagesPersistenceLegacy:csvLoad_Measures() Invalid integer input: " + data[0]);
						continue;
					}
					Cage cage = cages.getCageFromID(cageID);
					if (cage == null) {
						cage = new Cage();
						cages.cagesList.add(cage);
						cage.prop.setCageID(cageID);
					}

					if (v0) {
						cage.csvImport_MEASURE_Data_v0(measureType, data, complete);
					} else {
						cage.csvImport_MEASURE_Data_Parameters(data);
					}
				}
			}
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvLoad_Measures() Error: " + e.getMessage(), e);
			e.printStackTrace();
		}
	}

	// CSV saving helper methods (for backward compatibility)

	static boolean csvSaveDESCRIPTIONSection(Cages cages, FileWriter csvWriter, String csvSep) {
		try {
			csvWriter.append("#" + csvSep + "DESCRIPTION" + csvSep + "Cages data\n");
			csvWriter.append("n cages=" + csvSep + Integer.toString(cages.cagesList.size()) + "\n");
			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvSaveDESCRIPTIONSection() Error: " + e.getMessage(), e);
		}
		return true;
	}

	static boolean csvSaveCAGESection(Cages cages, FileWriter csvWriter, String csvSep) {
		try {
			csvWriter.append("#" + csvSep + "CAGE" + csvSep + "Cage properties\n");
			csvWriter.append("cageID" + csvSep + "nFlies" + csvSep + "age" + csvSep + "Comment" + csvSep + "strain"
					+ csvSep + "sect" + csvSep + "colorR" + csvSep + "colorG" + csvSep + "colorB" + csvSep + "ROIname"
					+ csvSep + "roiType" + csvSep + "npoints\n");

			for (Cage cage : cages.cagesList) {
				Color color = cage.getProperties().getColor();
				if (color == null && cage.getRoi() != null) {
					color = cage.getRoi().getColor();
				}
				if (color == null) {
					color = Color.MAGENTA;
				}

				String comment = cage.getProperties().getComment() != null ? cage.getProperties().getComment() : "";
				String strain = cage.getProperties().getFlyStrain() != null ? cage.getProperties().getFlyStrain() : "";
				String sex = cage.getProperties().getFlySex() != null ? cage.getProperties().getFlySex() : "";

				StringBuilder line = new StringBuilder();
				line.append(cage.getProperties().getCageID()).append(csvSep)
						.append(cage.getProperties().getCageNFlies()).append(csvSep)
						.append(cage.getProperties().getFlyAge()).append(csvSep)
						.append(comment).append(csvSep)
						.append(strain).append(csvSep)
						.append(sex).append(csvSep)
						.append(color.getRed()).append(csvSep)
						.append(color.getGreen()).append(csvSep)
						.append(color.getBlue()).append(csvSep);

				csvWriter.append(line.toString());

				String roiName = (cage.getRoi() != null && cage.getRoi().getName() != null) ? cage.getRoi().getName()
						: "cage" + String.format("%03d", cage.getProperties().getCageID());
				csvWriter.append(roiName + csvSep);

				// Add ROI type (v2.1 format)
				ROIType roiType = ROIPersistenceUtils.detectROIType(cage.getRoi());
				csvWriter.append(roiType.toCsvString() + csvSep);

				if (cage.getRoi() != null && cage.getRoi() instanceof plugins.kernel.roi.roi2d.ROI2DPolygon) {
					plugins.kernel.roi.roi2d.ROI2DPolygon polyRoi = (plugins.kernel.roi.roi2d.ROI2DPolygon) cage
							.getRoi();
					icy.type.geom.Polygon2D polygon = polyRoi.getPolygon2D();
					csvWriter.append(Integer.toString(polygon.npoints));
					for (int i = 0; i < polygon.npoints; i++) {
						csvWriter.append(csvSep + Integer.toString((int) polygon.xpoints[i]));
						csvWriter.append(csvSep + Integer.toString((int) polygon.ypoints[i]));
					}
				} else if (cage.getRoi() != null && cage.getRoi() instanceof plugins.kernel.roi.roi2d.ROI2DRectangle) {
					plugins.kernel.roi.roi2d.ROI2DRectangle rectRoi = (plugins.kernel.roi.roi2d.ROI2DRectangle) cage
							.getRoi();
					java.awt.Rectangle rect = rectRoi.getBounds();
					csvWriter.append("4"); // Rectangle has 4 corner points
					csvWriter.append(csvSep + Integer.toString(rect.x));
					csvWriter.append(csvSep + Integer.toString(rect.y));
					csvWriter.append(csvSep + Integer.toString(rect.width));
					csvWriter.append(csvSep + Integer.toString(rect.height));
				} else {
					csvWriter.append("0");
				}
				csvWriter.append("\n");
			}
			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvSaveCAGESection() Error: " + e.getMessage(), e);
		}
		return true;
	}

	static boolean csvSaveMeasuresSection(Cages cages, FileWriter csvWriter, EnumCageMeasures measuresType,
			String csvSep) {
		try {
			if (cages.cagesList.size() <= 0) {
				return false;
			}

			boolean complete = true;
			csvWriter.append(csvExport_MEASURE_Header(measuresType, csvSep, complete));

			for (Cage cage : cages.cagesList) {
				csvWriter.append(csvExport_MEASURE_Data(cage, measuresType, csvSep, complete));
			}

			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			Logger.error("CagesPersistenceLegacy:csvSaveMeasuresSection() Error: " + e.getMessage(), e);
		}
		return true;
	}

	private static String csvExport_MEASURE_Header(EnumCageMeasures measureType, String sep, boolean complete) {
		StringBuffer sbf = new StringBuffer();
		String explanation = "cageID" + sep + "parm" + sep + "npts";
		switch (measureType) {
		case POSITION:
			sbf.append("#" + sep + "POSITION\n" + explanation + "\n");
			break;
		default:
			sbf.append("#" + sep + "UNDEFINED------------\n");
			break;
		}
		return sbf.toString();
	}

	private static String csvExport_MEASURE_Data(Cage cage, EnumCageMeasures measureType, String sep,
			boolean complete) {
		StringBuffer sbf = new StringBuffer();
		String cageID = Integer.toString(cage.getProperties().getCageID());

		switch (measureType) {
		case POSITION:
			if (cage.flyPositions != null) {
				cage.flyPositions.cvsExport_Parameter_ToRow(sbf, "t(i)", cageID, sep);
				cage.flyPositions.cvsExport_Parameter_ToRow(sbf, "x(i)", cageID, sep);
				cage.flyPositions.cvsExport_Parameter_ToRow(sbf, "y(i)", cageID, sep);
				cage.flyPositions.cvsExport_Parameter_ToRow(sbf, "w(i)", cageID, sep);
				cage.flyPositions.cvsExport_Parameter_ToRow(sbf, "h(i)", cageID, sep);
			}
			break;
		default:
			break;
		}
		return sbf.toString();
	}

	// ========================================================================
	// Fallback methods that handle all legacy formats (CSV → XML)
	// These methods replicate the original MultiCAFE0 persistence behavior
	// ========================================================================

	/**
	 * Loads cage descriptions with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files first, then falls back to XML.
	 * 
	 * @param cages            The Cages to populate
	 * @param resultsDirectory The results directory
	 * @return true if successful
	 */
	public static boolean loadDescriptionWithFallback(Cages cages, String resultsDirectory) {
		if (resultsDirectory == null) {
			return false;
		}

		// Priority 1: Try legacy CSV format (CagesArray.csv)
		String pathToCsv = resultsDirectory + File.separator + ID_CAGESARRAY_CSV;
		File csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;
				boolean descriptionLoaded = false;
				boolean cageLoaded = false;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							switch (data[1]) {
							case "DESCRIPTION":
								descriptionLoaded = true;
								csvLoad_DESCRIPTION(cages, csvReader, sep);
								break;
							case "CAGE":
							case "CAGES":
								cageLoaded = true;
								csvLoad_CAGE(cages, csvReader, sep);
								break;
							case "POSITION":
								// Stop reading when we hit measures section
								csvReader.close();
								return descriptionLoaded || cageLoaded;
							default:
								break;
							}
						}
					}
				}
				csvReader.close();
				if (descriptionLoaded || cageLoaded) {
					Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
							+ ID_CAGESARRAY_CSV);
					return true;
				}
			} catch (Exception e) {
				Logger.error(
						"CagesPersistenceLegacy:loadDescriptionWithFallback() Error loading CSV: " + e.getMessage(), e);
			}
		}

		// Priority 2: Try legacy CSV format (CagesMeasures.csv) - combined file
		pathToCsv = resultsDirectory + File.separator + ID_CAGESMEASURES_CSV;
		csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				boolean success = csvLoadCagesMeasures(cages, resultsDirectory);
				if (success) {
					Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
							+ ID_CAGESMEASURES_CSV);
					// Extract only descriptions from the combined file
					return true;
				}
			} catch (Exception e) {
				Logger.error("CagesPersistenceLegacy:loadDescriptionWithFallback() Error loading combined CSV: "
						+ e.getMessage(), e);
			}
		}

		// Priority 3: MCdrosotrack.xml (MultiCAFE legacy format)
		String pathToXml = resolveMcDrosotrackXmlPath(resultsDirectory);
		if (pathToXml != null) {
			Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Trying MCdrosotrack XML: " + pathToXml);
			boolean loaded = xmlReadCagesFromMCdrosotrackXml(cages, pathToXml);
			if (loaded)
				Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy drosoTrack XML");
			return loaded;
		}

		// Priority 4: MS96_cages.xml (multiSPOTS96 legacy format)
		String ms96XmlPath = resultsDirectory + File.separator + CagesPersistenceMS96Legacy.getMs96CagesXmlFilename();
		File ms96XmlFile = new File(ms96XmlPath);
		if (ms96XmlFile.isFile()) {
			Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Trying MS96_cages.xml: " + ms96XmlPath);
			boolean loaded = xmlReadCagesFromMS96CagesXml(cages, ms96XmlPath);
			if (loaded)
				Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Loaded from "
						+ CagesPersistenceMS96Legacy.getMs96CagesXmlFilename());
			return loaded;
		}

		return false;
	}

	/**
	 * Loads cage descriptions and measures from MS96_cages.xml only.
	 */
	public static boolean loadFromMS96CagesXml(Cages cages, String resultsDirectory) {
		return CagesPersistenceMS96Legacy.loadFromMS96CagesXml(cages, resultsDirectory);
	}

	/**
	 * Loads spot ROIs from MS96_cages.xml.
	 */
	public static boolean loadSpotsFromMS96CagesXml(Spots spots, String resultsDirectory) {
		return CagesPersistenceMS96Legacy.loadSpotsFromMS96CagesXml(spots, resultsDirectory);
	}

	/**
	 * Loads cage ROIs from MS96_cages.xml and assigns them to cages.
	 */
	public static boolean loadCageROIsFromMS96CagesXml(Cages cages, String resultsDirectory) {
		return CagesPersistenceMS96Legacy.loadCageROIsFromMS96CagesXml(cages, resultsDirectory);
	}

	/**
	 * Loads cage measures with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files first, then falls back to XML.
	 * 
	 * @param cages        The Cages to populate
	 * @param binDirectory The bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public static boolean loadMeasuresWithFallback(Cages cages, String binDirectory) {
		if (binDirectory == null) {
			return false;
		}

		// Priority 1: Try legacy CSV format (CagesArrayMeasures.csv)
		String pathToCsv = binDirectory + File.separator + ID_CAGESARRAYMEASURES_CSV;
		File csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#') {
						sep = String.valueOf(row.charAt(1));
					}

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							if (data[1].equals("POSITION")) {
								csvLoad_Measures(cages, csvReader, EnumCageMeasures.POSITION, sep);
								csvReader.close();
								Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
										+ ID_CAGESARRAYMEASURES_CSV);
								return true;
							}
						}
					}
				}
				csvReader.close();
			} catch (Exception e) {
				Logger.error("CagesPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: " + e.getMessage(),
						e);
			}
		}

		// Priority 2: Try legacy CSV format (CagesMeasures.csv) in results directory
		// Check if binDirectory is actually results directory
		String resultsDir = binDirectory;
		if (binDirectory.contains(File.separator + "bin")) {
			// Extract results directory from bin directory
			int binIndex = binDirectory.lastIndexOf(File.separator + "bin");
			if (binIndex > 0) {
				resultsDir = binDirectory.substring(0, binIndex);
			}
		}
		pathToCsv = resultsDir + File.separator + ID_CAGESMEASURES_CSV;
		csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#') {
						sep = String.valueOf(row.charAt(1));
					}

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							if (data[1].equals("POSITION")) {
								csvLoad_Measures(cages, csvReader, EnumCageMeasures.POSITION, sep);
								csvReader.close();
								Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
										+ ID_CAGESMEASURES_CSV);
								return true;
							}
						}
					}
				}
				csvReader.close();
			} catch (Exception e) {
				Logger.error("CagesPersistenceLegacy:loadMeasuresWithFallback() Error loading combined CSV: "
						+ e.getMessage(), e);
			}
		}

		// Priority 3: Fall back to XML (legacy format)
		String pathToXml = resolveMcDrosotrackXmlPath(resultsDir);
		if (pathToXml != null) {
			Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Trying legacy XML format: " + pathToXml);
			boolean loaded = xmlLoadFlyPositionsFromXML(cages, pathToXml);
			if (loaded) {
				Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Loaded measures from legacy drosoTrack XML");
			}
			return loaded;
		}

		// Some legacy datasets stored MCdrosotrack.xml inside the bin directory
		// (results/bin_xx/MCdrosotrack.xml). If it wasn't migrated to results/, try it
		// there as a last resort.
		if (!binDirectory.equals(resultsDir)) {
			String binXmlPath = resolveMcDrosotrackXmlPath(binDirectory);
			if (binXmlPath != null) {
				Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Trying legacy XML in bin directory: "
						+ binXmlPath);
				boolean loaded = xmlLoadFlyPositionsFromXML(cages, binXmlPath);
				if (loaded) {
					Logger.info(
							"CagesPersistenceLegacy:loadMeasuresWithFallback() Loaded measures from legacy XML in bin");
				}
				return loaded;
			}
		}

		return false;
	}
}
