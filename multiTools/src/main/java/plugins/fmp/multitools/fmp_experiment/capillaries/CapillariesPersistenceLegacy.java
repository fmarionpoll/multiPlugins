package plugins.fmp.multitools.fmp_experiment.capillaries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.util.XMLUtil;
import plugins.fmp.multitools.fmp_tools.Logger;
import plugins.kernel.roi.roi2d.ROI2DShape;

/**
 * Legacy persistence for capillaries files. Handles loading from legacy XML and
 * CSV formats: MCcapillaries.xml, CapillariesMeasures.csv, individual capillary
 * XML files
 */
public class CapillariesPersistenceLegacy {

	public final static String ID_CAPILLARYTRACK = "capillaryTrack";
	public final static String ID_NCAPILLARIES = "N_capillaries";
	public final static String ID_LISTOFCAPILLARIES = "List_of_capillaries";
	public final static String ID_CAPILLARY_ = "capillary_";
	public final static String ID_MCCAPILLARIES_XML = "MCcapillaries.xml";

	private static final String csvSep = ";";
	private static final String ID_CAPILLARIESMEASURES_CSV = "CapillariesMeasures.csv";
	private static final String ID_CAPILLARIESARRAY_CSV = "CapillariesArray.csv";
	private static final String ID_CAPILLARIESARRAYMEASURES_CSV = "CapillariesArrayMeasures.csv";

	/**
	 * Legacy entry point for loading capillaries. Tries CSV format first, then
	 * falls back to XML format.
	 */
	public static boolean load(Capillaries capillaries, String directory) {
		boolean flag = false;
		try {
			flag = csvLoad_Capillaries(capillaries, directory);
		} catch (Exception e) {
			Logger.error("CapillariesPersistenceLegacy:load() Failed to load capillaries from CSV: " + directory, e);
		}

		if (!flag) {
			flag = xmlLoadCapillaries_Measures(capillaries, directory);
		}
		return flag;
	}

	/**
	 * Legacy entry point for saving capillaries.
	 */
	public static boolean save(Capillaries capillaries, String directory) {
		return csvSave_Capillaries(capillaries, directory);
	}

	/**
	 * Loads capillary descriptions from legacy XML format (MCcapillaries.xml).
	 * 
	 * @param capillaries The Capillaries object to populate
	 * @param csFileName  The path to the XML file
	 * @return true if successful
	 */

	public static boolean xmlLoadOldCapillaries_Only(Capillaries capillaries, String csFileName) {
		if (csFileName == null) {
			Logger.warn("CapillariesPersistenceLegacy:xmlLoadOldCapillaries_Only() File path is null");
			return false;
		}
		try {
			final Document doc = XMLUtil.loadDocument(csFileName);
			if (doc == null) {
				Logger.warn(
						"CapillariesPersistenceLegacy:xmlLoadOldCapillaries_Only() Could not load XML document from "
								+ csFileName);
				return false;
			}
			capillaries.getCapillariesDescription().xmlLoadCapillaryDescription(doc);
			int version = capillaries.getCapillariesDescription().getVersion();
			boolean loaded = false;
			switch (version) {
			case 1: // old xml storage structure
				loaded = xmlLoadCapillaries_Only_v1(capillaries, doc);
				break;
			case 0: // old-old xml storage structure
				xmlLoadCapillaries_v0(capillaries, doc, csFileName);
				loaded = true;
				break;
			default:
				xmlLoadCapillaries_Only_v2(capillaries, doc, csFileName);
				loaded = false;
				break;
			}
			if (loaded) {
				Logger.info("CapillariesPersistenceLegacy:xmlLoadOldCapillaries_Only() Loaded "
						+ capillaries.getList().size() + " capillaries from version " + version);
			} else {
				Logger.warn(
						"CapillariesPersistenceLegacy:xmlLoadOldCapillaries_Only() Failed to load capillaries from version "
								+ version);
			}
			return loaded;
		} catch (Exception e) {
			Logger.error("CapillariesPersistenceLegacy:xmlLoadOldCapillaries_Only() Error loading from " + csFileName
					+ ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Loads capillary measures from individual XML files ({kymographName}.xml).
	 * 
	 * @param capillaries The Capillaries object to populate measures for
	 * @param directory   The directory containing the XML files
	 * @return true if successful
	 */
	public static boolean xmlLoadCapillaries_Measures(Capillaries capillaries, String directory) {
		boolean flag = false;
		int ncapillaries = capillaries.getList().size();
		if (ncapillaries == 0) {
			Logger.warn(
					"CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() No capillaries to load measures for");
			return false;
		}
		int loadedCount = 0;
		for (int i = 0; i < ncapillaries; i++) {
			Capillary cap = capillaries.getList().get(i);
			String kymographName = cap.getKymographName();
			if (kymographName == null || kymographName.isEmpty()) {
				Logger.warn("CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() Capillary " + i
						+ " has no kymograph name");
				continue;
			}
			String csFile = directory + File.separator + kymographName + ".xml";
			try {
				final Document capdoc = XMLUtil.loadDocument(csFile);
				if (capdoc != null) {
					Node node = XMLUtil.getRootElement(capdoc, true);
					cap.setKymographIndex(i);
					if (cap.xmlLoad_MeasuresOnly(node)) {
						flag = true;
						loadedCount++;
					}
				} else {
					Logger.debug("CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() Could not load XML from "
							+ csFile);
				}
			} catch (Exception e) {
				Logger.warn("CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() Error loading measures from "
						+ csFile + ": " + e.getMessage());
			}
		}
		if (flag) {
			Logger.info("CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() Loaded measures for " + loadedCount
					+ " out of " + ncapillaries + " capillaries");
		} else {
			Logger.warn(
					"CapillariesPersistenceLegacy:xmlLoadCapillaries_Measures() Failed to load measures for any capillaries");
		}
		return flag;
	}

	/**
	 * Loads capillaries from legacy v0 XML format (ROI-based).
	 */
	private static void xmlLoadCapillaries_v0(Capillaries capillaries, Document doc, String csFileName) {
		List<ROI> listOfCapillaryROIs = ROI.loadROIsFromXML(XMLUtil.getRootElement(doc));
		capillaries.getList().clear();
		Path directorypath = Paths.get(csFileName).getParent();
		String directory = directorypath + File.separator;
		int t = 0;
		for (ROI roiCapillary : listOfCapillaryROIs) {
			xmlLoadIndividualCapillary_v0(capillaries, (ROI2DShape) roiCapillary, directory, t);
			t++;
		}
	}

	/**
	 * Loads an individual capillary from legacy v0 format.
	 */
	private static void xmlLoadIndividualCapillary_v0(Capillaries capillaries, ROI2D roiCapillary, String directory,
			int t) {
		Capillary cap = new Capillary(roiCapillary);
		if (!capillaries.isPresent(cap))
			capillaries.getList().add(cap);
		String csFile = directory + roiCapillary.getName() + ".xml";
		cap.setKymographIndex(t);
		final Document dockymo = XMLUtil.loadDocument(csFile);
		if (dockymo != null) {
			NodeList nodeROISingle = dockymo.getElementsByTagName("roi");
			if (nodeROISingle.getLength() > 0) {
				List<ROI> rois = new ArrayList<ROI>();
				for (int i = 0; i < nodeROISingle.getLength(); i++) {
					Node element = nodeROISingle.item(i);
					ROI roi_i = ROI.createFromXML(element);
					if (roi_i != null)
						rois.add(roi_i);
				}
				cap.transferROIsToMeasures(rois);
			}
		}
	}

	/**
	 * Loads capillaries from legacy v1 XML format.
	 */
	private static boolean xmlLoadCapillaries_Only_v1(Capillaries capillaries, Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_CAPILLARYTRACK);
		if (node == null)
			return false;
		Node nodecaps = XMLUtil.getElement(node, ID_LISTOFCAPILLARIES);
		int nitems = XMLUtil.getElementIntValue(nodecaps, ID_NCAPILLARIES, 0);
		capillaries.setCapillariesList(new ArrayList<Capillary>(nitems));
		for (int i = 0; i < nitems; i++) {
			Node nodecapillary = XMLUtil.getElement(node, ID_CAPILLARY_ + i);
			Capillary cap = new Capillary();
			cap.xmlLoad_CapillaryOnly(nodecapillary);

			if (!capillaries.isPresent(cap))
				capillaries.getList().add(cap);
		}
		return true;
	}

	/**
	 * Loads capillaries from legacy v2 XML format.
	 */
	private static void xmlLoadCapillaries_Only_v2(Capillaries capillaries, Document doc, String csFileName) {
		xmlLoadCapillaries_Only_v1(capillaries, doc);
		Path directorypath = Paths.get(csFileName).getParent();
		String directory = directorypath + File.separator;
		for (Capillary cap : capillaries.getList()) {
			String csFile = directory + cap.getKymographName() + ".xml";
			final Document capdoc = XMLUtil.loadDocument(csFile);
			if (capdoc != null) {
				Node node = XMLUtil.getRootElement(capdoc, true);
				cap.xmlLoad_CapillaryOnly(node);
			}
		}
	}

	/**
	 * Loads capillaries from legacy CSV format (CapillariesMeasures.csv).
	 */
	private static boolean csvLoad_Capillaries(Capillaries capillaries, String directory) throws Exception {
		String pathToCsv = directory + File.separator + ID_CAPILLARIESMEASURES_CSV;
		File csvFile = new File(pathToCsv);
		if (!csvFile.isFile())
			return false;

		BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
		String row;
		String sep = csvSep;
		boolean seenGulpsFlat = false;
		try {
			while ((row = csvReader.readLine()) != null) {
				if (row.length() > 0 && row.charAt(0) == '#')
					sep = String.valueOf(row.charAt(1));

				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#")) {
					if (data.length > 1) {
						switch (data[1]) {
						case "DESCRIPTION":
							csvLoad_Description(capillaries, csvReader, sep);
							break;
						case "CAPILLARIES":
							csvSkipSection(csvReader, sep);
							break;
						case "TOPLEVEL":
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPRAW, sep,
									row.contains("xi"));
							break;
						case "TOPRAW":
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPRAW, sep,
									row.contains("xi"));
							break;
						case "TOPLEVEL_CORRECTED":
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPLEVEL, sep,
									row.contains("xi"));
							break;
						case "BOTTOMLEVEL":
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.BOTTOMLEVEL, sep,
									row.contains("xi"));
							break;
						case "TOPDERIVATIVE":
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPDERIVATIVE,
									sep, row.contains("xi"));
							break;
						case "GULPS":
						case "GULPS_CORRECTED":
							if (seenGulpsFlat) {
								csvSkipSection(csvReader, sep);
								break;
							}
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
							break;
						case "GULPS_FLAT":
							seenGulpsFlat = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
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
		return true;
	}

	/**
	 * Skips a measures section until the next header line.
	 */
	static void csvSkipSection(BufferedReader csvReader, String sep) throws IOException {
		String row;
		while ((row = csvReader.readLine()) != null) {
			String[] data = row.split(sep);
			if (data.length > 0 && "#".equals(data[0]))
				return;
		}
	}

	/**
	 * Loads capillary descriptions from CSV.
	 */
	static String csvLoad_Capillaries_Description(Capillaries capillaries, BufferedReader csvReader, String sep) {
		String row;
		try {
			row = csvReader.readLine();
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#"))
					return data.length > 1 ? data[1] : null;

				Capillary cap = null;
				if (data.length > 2) {
					cap = capillaries.getCapillaryFromKymographName(data[2]);
				}
				if (cap == null) {
					cap = new Capillary();
					capillaries.getList().add(cap);
				}
				cap.csvImport_CapillaryDescription(data);
			}
		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvLoad_Capillaries_Description() Failed to read CSV file", e);
		}
		return null;
	}

	/**
	 * Loads description section from CSV.
	 */
	static String csvLoad_Description(Capillaries capillaries, BufferedReader csvReader, String sep) {
		String row;
		try {
			row = csvReader.readLine();
			row = csvReader.readLine();
			String[] data = row.split(sep);
			capillaries.getCapillariesDescription().csvImportCapillariesDescriptionData(data);

			row = csvReader.readLine();
			data = row.split(sep);
			if (data.length > 0 && data[0].substring(0, Math.min(data[0].length(), 5)).equals("n cap")) {
				int ncapillaries = Integer.valueOf(data[1]);
				if (ncapillaries >= capillaries.getList().size())
					((ArrayList<Capillary>) capillaries.getList()).ensureCapacity(ncapillaries);
				else
					capillaries.getList().subList(ncapillaries, capillaries.getList().size()).clear();

				row = csvReader.readLine();
				data = row.split(sep);
			}
			if (data.length > 0 && data[0].equals("#")) {
				return data.length > 1 ? data[1] : null;
			}
		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvLoad_Description()", e);
		}
		return null;
	}

	/**
	 * Loads capillary measures from CSV.
	 */
	static String csvLoad_Capillaries_Measures(Capillaries capillaries, BufferedReader csvReader,
			EnumCapillaryMeasures measureType, String sep, boolean x) {
		String row;
		final boolean y = true;
		try {
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(sep);
				if (data.length > 0 && data[0].equals("#"))
					return data.length > 1 ? data[1] : null;

				Capillary cap = capillaries.getCapillaryFromRoiNamePrefix(data[0]);
				if (cap == null)
					cap = new Capillary();
				cap.csvImport_CapillaryData(measureType, data, x, y);
			}
		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvLoad_Capillaries_Measures() Failed to read CSV file", e);
		}
		return null;
	}

	/**
	 * Saves capillaries to legacy CSV format.
	 */
	private static boolean csvSave_Capillaries(Capillaries capillaries, String directory) {
		Path path = Paths.get(directory);
		if (!Files.exists(path))
			return false;

		try {
			FileWriter csvWriter = new FileWriter(directory + File.separator + ID_CAPILLARIESMEASURES_CSV);

			csvSave_DescriptionSection(capillaries, csvWriter, csvSep);

			csvSave_MeasuresSection(capillaries, csvWriter, EnumCapillaryMeasures.TOPRAW, csvSep);
			csvSave_MeasuresSection(capillaries, csvWriter, EnumCapillaryMeasures.TOPLEVEL, csvSep);
			csvSave_MeasuresSection(capillaries, csvWriter, EnumCapillaryMeasures.BOTTOMLEVEL, csvSep);
			csvSave_MeasuresSection(capillaries, csvWriter, EnumCapillaryMeasures.TOPDERIVATIVE, csvSep);
			csvSave_MeasuresSection(capillaries, csvWriter, EnumCapillaryMeasures.GULPS, csvSep);
			csvWriter.flush();
			csvWriter.close();

		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvSave_Capillaries()", e);
		}

		return true;
	}

	/**
	 * Saves description section to CSV.
	 */
	static boolean csvSave_DescriptionSection(Capillaries capillaries, FileWriter csvWriter, String csvSep) {
		try {
			csvWriter.append(capillaries.getCapillariesDescription().csvExportSectionHeader(csvSep));
			csvWriter.append(capillaries.getCapillariesDescription().csvExportExperimentDescriptors(csvSep));
			csvWriter.append("n caps=" + csvSep + Integer.toString(capillaries.getList().size()) + "\n");
			csvWriter.append("#" + csvSep + "#\n");

			if (capillaries.getList().size() > 0) {
				csvWriter.append(capillaries.getList().get(0).csvExport_CapillarySubSectionHeader(csvSep));
				for (Capillary cap : capillaries.getList())
					csvWriter.append(cap.csvExport_CapillaryDescription(csvSep));
				csvWriter.append("#" + csvSep + "#\n");
			}
		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvSave_DescriptionSection()", e);
		}

		return true;
	}

	/**
	 * Saves measures section to CSV.
	 */
	static boolean csvSave_MeasuresSection(Capillaries capillaries, FileWriter csvWriter,
			EnumCapillaryMeasures measureType, String csvSep) {
		try {
			if (capillaries.getList().size() <= 1)
				return false;

			csvWriter.append(capillaries.getList().get(0).csvExport_MeasureSectionHeader(measureType, csvSep));
			for (Capillary cap : capillaries.getList())
				csvWriter.append(cap.csvExport_MeasuresOneType(measureType, csvSep));

			csvWriter.append("#" + csvSep + "#\n");
		} catch (IOException e) {
			Logger.error("CapillariesPersistenceLegacy:csvSave_MeasuresSection()", e);
		}
		return true;
	}

	/**
	 * Saves capillary descriptors to XML format.
	 */
	public static boolean xmlSaveCapillaries_Descriptors(Capillaries capillaries, String csFileName) {
		if (csFileName != null) {
			final Document doc = XMLUtil.createDocument(true);
			if (doc != null) {
				capillaries.getCapillariesDescription().xmlSaveCapillaryDescription(doc);
				xmlSaveListOfCapillaries(capillaries, doc);
				return XMLUtil.saveDocument(doc, csFileName);
			}
		}
		return false;
	}

	/**
	 * Saves list of capillaries to XML document.
	 */
	private static boolean xmlSaveListOfCapillaries(Capillaries capillaries, Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_CAPILLARYTRACK);
		if (node == null)
			return false;
		XMLUtil.setElementIntValue(node, "version", 2);
		Node nodecaps = XMLUtil.setElement(node, ID_LISTOFCAPILLARIES);
		XMLUtil.setElementIntValue(nodecaps, ID_NCAPILLARIES, capillaries.getList().size());
		int i = 0;
		Collections.sort(capillaries.getList());
		for (Capillary cap : capillaries.getList()) {
			Node nodecapillary = XMLUtil.setElement(node, ID_CAPILLARY_ + i);
			cap.xmlSave_CapillaryOnly(nodecapillary);
			i++;
		}
		return true;
	}

	/**
	 * Loads capillary descriptors from XML format.
	 */
	public static boolean loadMCCapillaries_Descriptors(Capillaries capillaries, String csFileName) {
		boolean flag = false;
		if (csFileName == null)
			return flag;

		final Document doc = XMLUtil.loadDocument(csFileName);
		if (doc != null) {
			capillaries.getCapillariesDescription().xmlLoadCapillaryDescription(doc);
			flag = xmlLoadCapillaries_Only_v1(capillaries, doc);
		}
		return flag;
	}

	/**
	 * Merges capillary descriptors from XML format.
	 */
	public static boolean mergeMCCapillaries_Descriptors(Capillaries capillaries, String csFileName) {
		boolean flag = false;
		if (csFileName == null)
			return flag;

		final Document doc = XMLUtil.loadDocument(csFileName);
		if (doc != null) {
			capillaries.getCapillariesDescription().xmlLoadCapillaryDescription(doc);
			flag = xmlMergeCapillaries_Descriptors(capillaries, doc);
		}
		return flag;
	}

	/**
	 * Merges capillary descriptors from XML document.
	 */
	private static boolean xmlMergeCapillaries_Descriptors(Capillaries capillaries, Document doc) {
		Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_CAPILLARYTRACK);
		if (node == null)
			return false;
		Node nodecaps = XMLUtil.getElement(node, ID_LISTOFCAPILLARIES);
		int nitems = XMLUtil.getElementIntValue(nodecaps, ID_NCAPILLARIES, 0);

		for (int i = 0; i < nitems; i++) {
			Node nodecapillary = XMLUtil.getElement(node, ID_CAPILLARY_ + i);
			Capillary capXML = new Capillary();
			capXML.xmlLoad_CapillaryOnly(nodecapillary);

			Capillary cap = capillaries.getCapillaryFromKymographName(capXML.getKymographName());
			if (cap != null) {
				cap.setStimulus(capXML.getStimulus());
				cap.setConcentration(capXML.getConcentration());
				cap.setVolume(capXML.getVolume());
			} else {
				capillaries.getList().add(capXML);
			}
		}
		return true;
	}

	// ========================================================================
	// Fallback methods that handle all legacy formats (CSV → XML)
	// These methods replicate the original MultiCAFE0 persistence behavior
	// ========================================================================

	/**
	 * Loads capillary descriptions with fallback logic. Replicates original
	 * MultiCAFE0 behavior: checks for legacy CSV files first, then falls back to
	 * XML.
	 * 
	 * @param capillaries      The Capillaries to populate
	 * @param resultsDirectory The results directory
	 * @return true if successful
	 */
	public static boolean loadDescriptionWithFallback(Capillaries capillaries, String resultsDirectory) {
		if (resultsDirectory == null) {
			return false;
		}

		// Priority 1: Try legacy CSV format (CapillariesArray.csv)
		String pathToCsv = resultsDirectory + File.separator + ID_CAPILLARIESARRAY_CSV;
		File csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						switch (data[1]) {
						case "DESCRIPTION":
							csvLoad_Description(capillaries, csvReader, sep);
							break;
						case "CAPILLARIES":
							// Load CAPILLARIES section with ROI coordinates
							csvLoad_Capillaries_Description(capillaries, csvReader, sep);
							csvReader.close();
							Logger.info(
									"CapillariesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
											+ ID_CAPILLARIESARRAY_CSV);
							return true;
						case "TOPLEVEL":
						case "TOPRAW":
						case "BOTTOMLEVEL":
						case "TOPDERIVATIVE":
						case "GULPS":
						case "GULPS_CORRECTED":
						case "GULPS_FLAT":
							// Stop reading when we hit measures section
							csvReader.close();
							Logger.info(
									"CapillariesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
											+ ID_CAPILLARIESARRAY_CSV);
							return true;
						default:
							break;
						}
					}
				}
				csvReader.close();
				return false;
			} catch (Exception e) {
				Logger.error("CapillariesPersistenceLegacy:loadDescriptionWithFallback() Error loading CSV: "
						+ e.getMessage(), e);
			}
		}

		// Priority 2: Fall back to legacy XML format
		String pathToXml = resultsDirectory + File.separator + ID_MCCAPILLARIES_XML;
		File xmlFile = new File(pathToXml);
		if (xmlFile.isFile()) {
			Logger.info("CapillariesPersistenceLegacy:loadDescriptionWithFallback() Trying legacy XML format: "
					+ pathToXml);
			boolean loaded = xmlLoadOldCapillaries_Only(capillaries, pathToXml);
			if (loaded) {
				Logger.info("CapillariesPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy XML: "
						+ ID_MCCAPILLARIES_XML);
			} else {
				Logger.warn(
						"CapillariesPersistenceLegacy:loadDescriptionWithFallback() Failed to load from legacy XML: "
								+ pathToXml);
			}
			return loaded;
		}

		return false;
	}

	/**
	 * Loads capillary measures with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files first, then falls back to XML.
	 * 
	 * @param capillaries  The Capillaries to populate
	 * @param binDirectory The bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public static boolean loadMeasuresWithFallback(Capillaries capillaries, String binDirectory) {
		if (binDirectory == null) {
			return false;
		}

		boolean measuresLoaded = false;

		// Priority 1: Try legacy CSV format (CapillariesArrayMeasures.csv)
		String pathToCsv = binDirectory + File.separator + ID_CAPILLARIESARRAYMEASURES_CSV;
		File csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;
				boolean seenGulpsFlat = false;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						switch (data[1]) {
						case "DESCRIPTION":
							// Skip description section in measures file
							csvSkipSection(csvReader, sep);
							break;
						case "CAPILLARIES":
							// Skip CAPILLARIES section
							csvSkipSection(csvReader, sep);
							break;
						case "TOPLEVEL":
						case "TOPRAW":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPRAW, sep,
									row.contains("xi"));
							break;
						case "TOPLEVEL_CORRECTED":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPLEVEL, sep,
									row.contains("xi"));
							break;
						case "BOTTOMLEVEL":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.BOTTOMLEVEL, sep,
									row.contains("xi"));
							break;
						case "TOPDERIVATIVE":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPDERIVATIVE,
									sep, row.contains("xi"));
							break;
						case "GULPS":
						case "GULPS_CORRECTED":
							if (seenGulpsFlat) {
								csvSkipSection(csvReader, sep);
								break;
							}
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
							break;
						case "GULPS_FLAT":
							seenGulpsFlat = true;
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
							break;
						default:
							break;
						}
					}
				}
				csvReader.close();
				if (measuresLoaded) {
					Logger.info("CapillariesPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
							+ ID_CAPILLARIESARRAYMEASURES_CSV);
					return true;
				}
			} catch (Exception e) {
				Logger.error(
						"CapillariesPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: " + e.getMessage(),
						e);
			}
		}

		// Priority 2: Try legacy CSV format (CapillariesMeasures.csv) in bin directory
		if (!measuresLoaded) {
			pathToCsv = binDirectory + File.separator + ID_CAPILLARIESMEASURES_CSV;
			csvFile = new File(pathToCsv);
			if (csvFile.isFile()) {
				try {
					BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
					String row;
					String sep = csvSep;
					boolean seenGulpsFlat = false;

					while ((row = csvReader.readLine()) != null) {
						if (row.length() > 0 && row.charAt(0) == '#')
							sep = String.valueOf(row.charAt(1));

						String[] data = row.split(sep);
						if (data.length > 0 && data[0].equals("#")) {
							switch (data[1]) {
							case "DESCRIPTION":
								// Skip description section in measures file
								csvSkipSection(csvReader, sep);
								break;
							case "CAPILLARIES":
								// Skip CAPILLARIES section
								csvSkipSection(csvReader, sep);
								break;
							case "TOPLEVEL":
							case "TOPRAW":
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPRAW, sep,
										row.contains("xi"));
								break;
							case "TOPLEVEL_CORRECTED":
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPLEVEL, sep,
										row.contains("xi"));
								break;
							case "BOTTOMLEVEL":
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.BOTTOMLEVEL, sep,
										row.contains("xi"));
								break;
							case "TOPDERIVATIVE":
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPDERIVATIVE,
										sep, row.contains("xi"));
								break;
							case "GULPS":
							case "GULPS_CORRECTED":
								if (seenGulpsFlat) {
									csvSkipSection(csvReader, sep);
									break;
								}
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
										true);
								break;
							case "GULPS_FLAT":
								seenGulpsFlat = true;
								measuresLoaded = true;
								csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
										true);
								break;
							default:
								break;
							}
						}
					}
					csvReader.close();
					if (measuresLoaded) {
						Logger.info("CapillariesPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
								+ ID_CAPILLARIESMEASURES_CSV);
						return true;
					}
				} catch (Exception e) {
					Logger.error(
							"CapillariesPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: " + e.getMessage(),
							e);
				}
			}
		}

		// Priority 3: Try legacy CSV format (CapillariesMeasures.csv) in results directory (if not already loaded)
		String resultsDir = binDirectory;
		if (binDirectory.contains(File.separator + "bin")) {
			// Extract results directory from bin directory
			int binIndex = binDirectory.lastIndexOf(File.separator + "bin");
			if (binIndex > 0) {
				resultsDir = binDirectory.substring(0, binIndex);
			}
		}
		pathToCsv = resultsDir + File.separator + ID_CAPILLARIESMEASURES_CSV;
		csvFile = new File(pathToCsv);
		if (csvFile.isFile()) {
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;
				boolean seenGulpsFlat = false;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						switch (data[1]) {
						case "DESCRIPTION":
							// Skip description section in measures file
							csvSkipSection(csvReader, sep);
							break;
						case "CAPILLARIES":
							// Skip CAPILLARIES section
							csvSkipSection(csvReader, sep);
							break;
						case "TOPLEVEL":
						case "TOPRAW":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPRAW, sep,
									row.contains("xi"));
							break;
						case "TOPLEVEL_CORRECTED":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPLEVEL, sep,
									row.contains("xi"));
							break;
						case "BOTTOMLEVEL":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.BOTTOMLEVEL, sep,
									row.contains("xi"));
							break;
						case "TOPDERIVATIVE":
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.TOPDERIVATIVE,
									sep, row.contains("xi"));
							break;
						case "GULPS":
						case "GULPS_CORRECTED":
							if (seenGulpsFlat) {
								csvSkipSection(csvReader, sep);
								break;
							}
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
							break;
						case "GULPS_FLAT":
							seenGulpsFlat = true;
							measuresLoaded = true;
							csvLoad_Capillaries_Measures(capillaries, csvReader, EnumCapillaryMeasures.GULPS, sep,
									true);
							break;
						default:
							break;
						}
					}
				}
				csvReader.close();
				if (measuresLoaded) {
					Logger.info("CapillariesPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
							+ ID_CAPILLARIESMEASURES_CSV);
					return true;
				}
			} catch (Exception e) {
				Logger.error(
						"CapillariesPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV from results directory: " + e.getMessage(),
						e);
			}
		}

		// Priority 4: Fall back to legacy XML format (individual capillary XML files)
		// Measures are stored in {kymographName}.xml files in the bin directory
		Logger.info(
				"CapillariesPersistenceLegacy:loadMeasuresWithFallback() Trying legacy XML format in bin directory: "
						+ binDirectory);
		if (capillaries.getList().size() == 0) {
			Logger.warn(
					"CapillariesPersistenceLegacy:loadMeasuresWithFallback() No capillaries loaded, cannot load measures from XML");
			return false;
		}
		boolean loaded = xmlLoadCapillaries_Measures(capillaries, binDirectory);
		if (loaded) {
			Logger.info("CapillariesPersistenceLegacy:loadMeasuresWithFallback() Loaded measures from legacy XML");
		} else {
			Logger.warn(
					"CapillariesPersistenceLegacy:loadMeasuresWithFallback() Failed to load measures from legacy XML");
		}
		return loaded;
	}
}
