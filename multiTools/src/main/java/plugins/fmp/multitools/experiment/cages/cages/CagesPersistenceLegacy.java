package plugins.fmp.multitools.experiment.cages.cages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.cages.cage.EnumCageMeasures;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;

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
	private static final String ID_FLYDETECTED = "Fly_Detected";
	private static final String ID_NBITEMS = "nb_items";

	private static final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";
	private static final String csvSep = ";";
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
	 * Loads cages from legacy XML format (MCdrosotrack.xml).
	 * 
	 * @param cages The Cages to populate
	 * @param node  The XML node containing cages data
	 * @return true if successful
	 */
	public static boolean xmlLoadCages(Cages cages, Node node) {
		try {
			// Try new format first (with Cages element)
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal != null) {
				cages.cagesList.clear();
				int ncages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
				if (ncages < 0) {
					Logger.error("CagesPersistenceLegacy:xmlLoadCages() ERROR: Invalid number of cages: " + ncages);
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
						boolean cageSuccess = cage.xmlLoadCage(xmlVal, index);
						if (cageSuccess) {
							cages.cagesList.add(cage);
							loadedCages++;
						} else {
							Logger.warn("CagesPersistenceLegacy:xmlLoadCages() WARNING: Failed to load cage at index "
									+ index);
						}
					} catch (Exception e) {
						Logger.error("CagesPersistenceLegacy:xmlLoadCages() ERROR loading cage at index " + index + ": "
								+ e.getMessage(), e);
					}
				}

				Logger.info("CagesPersistenceLegacy:xmlLoadCages() Loaded " + loadedCages + " cages");
				return loadedCages > 0;
			}

			// Try legacy v0 format (with drosoTrack element)
			Node drosoTrackNode = XMLUtil.getElement(node, ID_DROSOTRACK);
			if (drosoTrackNode != null) {
				return xmlLoadCages_v0(cages, drosoTrackNode);
			}

			Logger.warn("CagesPersistenceLegacy:xmlLoadCages() Could not find Cages or drosoTrack element in XML");
			return false;

		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlLoadCages() ERROR during xmlLoadCages: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads cages from legacy v0 XML format (with drosoTrack element).
	 * 
	 * @param cages The Cages to populate
	 * @param node  The drosoTrack XML node
	 * @return true if successful
	 */
	private static boolean xmlLoadCages_v0(Cages cages, Node node) {
		try {
			cages.cagesList.clear();
			Element xmlVal = XMLUtil.getElement(node, ID_CAGES);
			if (xmlVal != null) {
				int nCages = XMLUtil.getAttributeIntValue(xmlVal, ID_NCAGES, 0);
				for (int index = 0; index < nCages; index++) {
					Cage cage = new Cage();
					cage.xmlLoadCage(xmlVal, index);
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
	 * Loads cages from legacy XML file.
	 * 
	 * @param cages    The Cages to populate
	 * @param tempname The path to the XML file
	 * @return true if successful
	 */
	public static boolean xmlReadCagesFromFileNoQuestion(Cages cages, String tempname) {
		if (tempname == null) {
			return false;
		}

		File file = new File(tempname);
		if (!file.exists()) {
			return false;
		}

		try {
			final Document doc = XMLUtil.loadDocument(tempname);
			if (doc == null) {
				Logger.warn("CagesPersistenceLegacy:xmlReadCagesFromFileNoQuestion() Could not load XML document: "
						+ tempname);
				return false;
			}

			boolean success = xmlLoadCages(cages, XMLUtil.getRootElement(doc));
			return success;

		} catch (Exception e) {
			Logger.error("CagesPersistenceLegacy:xmlReadCagesFromFileNoQuestion() ERROR during cages XML loading: "
					+ e.getMessage(), e);
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

		File file = new File(tempname);
		if (!file.exists()) {
			return true; // File doesn't exist yet, that's OK
		}

		try {
			final Document doc = XMLUtil.loadDocument(tempname);
			if (doc == null) {
				Logger.warn("CagesPersistenceLegacy:xmlLoadCagesROIsOnly() Could not load XML document: " + tempname);
				return false;
			}

			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlVal = XMLUtil.getElement(rootNode, ID_CAGES);
			if (xmlVal == null) {
				Logger.warn("CagesPersistenceLegacy:xmlLoadCagesROIsOnly() Could not find Cages element in XML");
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
						boolean roiLoaded = cage.xmlLoadCageLimits(cageElement);
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
		if (tempname == null) {
			return false;
		}

		File file = new File(tempname);
		if (!file.exists()) {
			return false;
		}

		try {
			final Document doc = XMLUtil.loadDocument(tempname);
			if (doc == null) {
				Logger.warn(
						"CagesPersistenceLegacy:xmlLoadFlyPositionsFromXML() Could not load XML document: " + tempname);
				return false;
			}

			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlVal = XMLUtil.getElement(rootNode, ID_CAGES);
			if (xmlVal == null) {
				Logger.warn("CagesPersistenceLegacy:xmlLoadFlyPositionsFromXML() Could not find Cages element in XML");
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
						boolean flyPositionsLoaded = cage.xmlLoadFlyPositions(cageElement);
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
					+ csvSep + "sect" + csvSep + "ROIname" + csvSep + "npoints\n");

			for (Cage cage : cages.cagesList) {
				csvWriter.append(String.format("%d%s%d%s%d%s%s%s%s%s%s%s", cage.getProperties().getCageID(), csvSep,
						cage.getProperties().getCageNFlies(), csvSep, cage.getProperties().getFlyAge(), csvSep,
						cage.getProperties().getComment() != null ? cage.getProperties().getComment() : "", csvSep,
						cage.getProperties().getFlyStrain() != null ? cage.getProperties().getFlyStrain() : "", csvSep,
						cage.getProperties().getFlySex() != null ? cage.getProperties().getFlySex() : "", csvSep));

				String roiName = (cage.getRoi() != null && cage.getRoi().getName() != null) ? cage.getRoi().getName()
						: "cage" + String.format("%03d", cage.getProperties().getCageID());
				csvWriter.append(roiName + csvSep);

				if (cage.getRoi() != null && cage.getRoi() instanceof plugins.kernel.roi.roi2d.ROI2DPolygon) {
					plugins.kernel.roi.roi2d.ROI2DPolygon polyRoi = (plugins.kernel.roi.roi2d.ROI2DPolygon) cage
							.getRoi();
					icy.type.geom.Polygon2D polygon = polyRoi.getPolygon2D();
					csvWriter.append(Integer.toString(polygon.npoints));
					for (int i = 0; i < polygon.npoints; i++) {
						csvWriter.append(csvSep + Integer.toString((int) polygon.xpoints[i]));
						csvWriter.append(csvSep + Integer.toString((int) polygon.ypoints[i]));
					}
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
				Logger.error("CagesPersistenceLegacy:loadDescriptionWithFallback() Error loading CSV: "
						+ e.getMessage(), e);
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

		// Priority 3: Fall back to XML (legacy format)
		String pathToXml = resultsDirectory + File.separator + ID_MCDROSOTRACK_XML;
		File xmlFile = new File(pathToXml);
		if (xmlFile.isFile()) {
			Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Trying legacy XML format: "
					+ pathToXml);
			boolean loaded = xmlReadCagesFromFileNoQuestion(cages, pathToXml);
			if (loaded) {
				Logger.info("CagesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy XML: "
						+ ID_MCDROSOTRACK_XML);
			}
			return loaded;
		}

		return false;
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
				Logger.error("CagesPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: "
						+ e.getMessage(), e);
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
		String pathToXml = resultsDir + File.separator + ID_MCDROSOTRACK_XML;
		File xmlFile = new File(pathToXml);
		if (xmlFile.isFile()) {
			Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Trying legacy XML format: "
					+ pathToXml);
			// Load fly positions from XML
			boolean loaded = xmlLoadFlyPositionsFromXML(cages, pathToXml);
			if (loaded) {
				Logger.info("CagesPersistenceLegacy:loadMeasuresWithFallback() Loaded measures from legacy XML: "
						+ ID_MCDROSOTRACK_XML);
			}
			return loaded;
		}

		return false;
	}
}
