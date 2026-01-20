package plugins.fmp.multitools.experiment.capillaries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.tools.Logger;

public class CapillariesPersistence {

	public final static String ID_CAPILLARYTRACK = "capillaryTrack";
	public final static String ID_NCAPILLARIES = "N_capillaries";
	public final static String ID_LISTOFCAPILLARIES = "List_of_capillaries";
	public final static String ID_CAPILLARY_ = "capillary_";

	// Current format filenames (version stored internally in file header)
	public final static String ID_V2_CAPILLARIESDESCRIPTION_CSV = "CapillariesDescription.csv";
	public final static String ID_V2_CAPILLARIESMEASURES_CSV = "CapillariesMeasures.csv";

	// Version for CSV files
	private static final String CSV_VERSION = "2.1";

	// Legacy filenames (for fallback)
	public final static String ID_CAPILLARIESARRAY_CSV = "CapillariesArray.csv";
	public final static String ID_CAPILLARIESARRAYMEASURES_CSV = "CapillariesArrayMeasures.csv";
	public final static String ID_MCCAPILLARIES_XML = "MCcapillaries.xml";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// New standardized method names (v2.3.3+)
	// ========================================================================

	/**
	 * Loads capillary descriptions from the results directory. Descriptions include
	 * capillary properties but not time-series measures. Tries new v2 format first,
	 * then falls back to legacy format.
	 * 
	 * @param capillaries      the Capillaries to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadDescriptions(Capillaries capillaries, String resultsDirectory) {
		return Persistence.loadDescription(capillaries, resultsDirectory);
	}

	/**
	 * Loads capillary measures from the bin directory (e.g., results/bin60).
	 * Measures include time-series data like toplevel, bottomlevel, derivative,
	 * gulps. Tries new v2 format first, then falls back to legacy format.
	 * 
	 * @param capillaries  the Capillaries to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean loadMeasures(Capillaries capillaries, String binDirectory) {
		return Persistence.loadMeasures(capillaries, binDirectory);
	}

	/**
	 * Saves capillary descriptions to the results directory. Descriptions include
	 * capillary properties but not time-series measures. Uses new v2 format.
	 * 
	 * @param capillaries      the Capillaries to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveDescriptions(Capillaries capillaries, String resultsDirectory) {
		return Persistence.saveDescription(capillaries, resultsDirectory);
	}

	/**
	 * Saves capillary measures to the bin directory (e.g., results/bin60). Measures
	 * include time-series data like toplevel, bottomlevel, derivative, gulps. Uses
	 * new v2 format.
	 * 
	 * @param capillaries  the Capillaries to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean saveMeasures(Capillaries capillaries, String binDirectory) {
		return Persistence.saveMeasures(capillaries, binDirectory);
	}

	// ========================================================================
	// Deprecated methods - kept for backward compatibility (will be removed in
	// v3.0)
	// ========================================================================

//	/**
//	 * @deprecated Use {@link #loadDescriptions(Capillaries, String)} instead.
//	 */
//	@Deprecated
//	public boolean load_CapillariesDescription(Capillaries capillaries, String resultsDirectory) {
//		return loadDescriptions(capillaries, resultsDirectory);
//	}

//	/**
//	 * @deprecated Use {@link #loadMeasures(Capillaries, String)} instead.
//	 */
//	@Deprecated
//	public boolean load_CapillariesMeasures(Capillaries capillaries, String binDirectory) {
//		return loadMeasures(capillaries, binDirectory);
//	}

//	/**
//	 * @deprecated Use {@link #saveDescriptions(Capillaries, String)} instead.
//	 */
//	@Deprecated
//	public boolean saveCapillariesDescription(Capillaries capillaries, String resultsDirectory) {
//		return saveDescriptions(capillaries, resultsDirectory);
//	}

//	/**
//	 * @deprecated Use {@link #saveMeasures(Capillaries, String)} instead.
//	 */
//	@Deprecated
//	public boolean save_CapillariesMeasures(Capillaries capillaries, String binDirectory) {
//		return saveMeasures(capillaries, binDirectory);
//	}

	public String getXMLNameToAppend() {
		return ID_MCCAPILLARIES_XML;
	}

	public boolean xmlSaveCapillaries_Descriptors(Capillaries capillaries, String csFileName) {
		return CapillariesPersistenceLegacy.xmlSaveCapillaries_Descriptors(capillaries, csFileName);
	}

	// ========================================================================
	// Nested class for current v2 format persistence
	// ========================================================================

	public static class Persistence {
		private static final String csvSep = ";";

		/**
		 * Loads capillary descriptions (DESCRIPTION section) from v2 format file. If v2
		 * format is not found or missing version header, delegates to Legacy class for fallback handling.
		 * 
		 * @param capillaries      the Capillaries to populate
		 * @param resultsDirectory the results directory
		 * @return true if successful
		 */
		public static boolean loadDescription(Capillaries capillaries, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			String pathToCsv = resultsDirectory + File.separator + ID_V2_CAPILLARIESDESCRIPTION_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
			}

			// Validate version header before committing to new format parser
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String firstLine = csvReader.readLine();
				csvReader.close();
				
				if (firstLine == null || !firstLine.startsWith("#")) {
					Logger.info("CapillariesPersistence: No header found in " + ID_V2_CAPILLARIESDESCRIPTION_CSV + ", using legacy parser");
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}
				
				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					Logger.info("CapillariesPersistence: First line is not version header in " + ID_V2_CAPILLARIESDESCRIPTION_CSV + ", using legacy parser");
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}
				
				String fileVersion = versionData[2];
				if (!fileVersion.equals(CSV_VERSION)) {
					Logger.warn("CapillariesPersistence: File version " + fileVersion + 
							   " differs from current version " + CSV_VERSION);
				}
			} catch (IOException e) {
				Logger.error("CapillariesPersistence: Error reading file header: " + e.getMessage(), e);
				return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
			}

			// Version validated - proceed with new format parser
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
						case "version":
							break;
						case "DESCRIPTION":
							CapillariesPersistenceLegacy.csvLoad_Description(capillaries, csvReader, sep);
							break;
						case "CAPILLARIES":
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Description(capillaries, csvReader, sep);
							csvReader.close();
							return true;
						case "TOPLEVEL":
						case "TOPRAW":
						case "BOTTOMLEVEL":
						case "TOPDERIVATIVE":
						case "GULPS":
						case "GULPS_CORRECTED":
						case "GULPS_FLAT":
							csvReader.close();
							return true;
						default:
							break;
						}
					}
				}
				csvReader.close();
				return false;
			} catch (Exception e) {
				Logger.error("CapillariesPersistence:loadDescription() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Loads capillary measures from v2 format file in bin directory. If v2 format
		 * is not found or missing version header, delegates to Legacy class for fallback handling.
		 * 
		 * @param capillaries  the Capillaries to populate
		 * @param binDirectory the bin directory (e.g., results/bin60)
		 * @return true if successful
		 */
		public static boolean loadMeasures(Capillaries capillaries, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			String pathToCsv = binDirectory + File.separator + ID_V2_CAPILLARIESMEASURES_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
			}

			// Validate version header before committing to new format parser
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String firstLine = csvReader.readLine();
				csvReader.close();
				
				if (firstLine == null || !firstLine.startsWith("#")) {
					Logger.info("CapillariesPersistence: No header found in " + ID_V2_CAPILLARIESMEASURES_CSV + ", using legacy parser");
					return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
				}
				
				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					Logger.info("CapillariesPersistence: First line is not version header in " + ID_V2_CAPILLARIESMEASURES_CSV + ", using legacy parser");
					return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
				}
				
				String fileVersion = versionData[2];
				if (!fileVersion.equals(CSV_VERSION)) {
					Logger.warn("CapillariesPersistence: File version " + fileVersion + 
							   " differs from current version " + CSV_VERSION);
				}
			} catch (IOException e) {
				Logger.error("CapillariesPersistence: Error reading file header: " + e.getMessage(), e);
				return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
			}

			// Version validated - proceed with new format parser
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;
				boolean seenGulpsFlat = false;
				boolean measuresLoaded = false;

				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						switch (data[1]) {
						case "version":
							break;
						case "DESCRIPTION":
							CapillariesPersistenceLegacy.csvSkipSection(csvReader, sep);
							break;
						case "CAPILLARIES":
							CapillariesPersistenceLegacy.csvSkipSection(csvReader, sep);
							break;
						case "TOPLEVEL":
						case "TOPRAW":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.TOPRAW, sep, row.contains("xi"));
							break;
						case "TOPLEVEL_CORRECTED":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.TOPLEVEL, sep, row.contains("xi"));
							break;
						case "BOTTOMLEVEL":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.BOTTOMLEVEL, sep, row.contains("xi"));
							break;
						case "TOPDERIVATIVE":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.TOPDERIVATIVE, sep, row.contains("xi"));
							break;
						case "GULPS":
						case "GULPS_CORRECTED":
							if (seenGulpsFlat) {
								CapillariesPersistenceLegacy.csvSkipSection(csvReader, sep);
								break;
							}
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.GULPS, sep, true);
							break;
						case "GULPS_FLAT":
							seenGulpsFlat = true;
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.GULPS, sep, true);
							break;
						default:
							break;
						}
					}
				}
				csvReader.close();
				return measuresLoaded;
			} catch (Exception e) {
				Logger.error("CapillariesPersistence:loadMeasures() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Saves capillary descriptions (DESCRIPTION section) to CapillariesDescription.csv in
		 * results directory. Always saves with version header.
		 * 
		 * @param capillaries      the Capillaries to save
		 * @param resultsDirectory the results directory
		 * @return true if successful
		 */
		public static boolean saveDescription(Capillaries capillaries, String resultsDirectory) {
			if (resultsDirectory == null) {
				Logger.warn("CapillariesPersistence:saveCapillariesArrayDescription() directory is null");
				return false;
			}

			Path path = Paths.get(resultsDirectory);
			if (!Files.exists(path)) {
				Logger.warn("CapillariesPersistence:saveCapillariesArrayDescription() directory does not exist: "
						+ resultsDirectory);
				return false;
			}

			try {
				FileWriter csvWriter = new FileWriter(
						resultsDirectory + File.separator + ID_V2_CAPILLARIESDESCRIPTION_CSV);
				csvWriter.write("#" + csvSep + "version" + csvSep + CSV_VERSION + "\n");
				CapillariesPersistenceLegacy.csvSave_DescriptionSection(capillaries, csvWriter, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.info("CapillariesPersistence:saveCapillariesArrayDescription() Saved descriptions to "
						+ ID_V2_CAPILLARIESDESCRIPTION_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CapillariesPersistence:saveCapillariesArrayDescription() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Saves capillary measures to CapillariesMeasures.csv in bin directory.
		 * Always saves with version header.
		 * 
		 * @param capillaries  the Capillaries to save
		 * @param binDirectory the bin directory (e.g., results/bin60)
		 * @return true if successful
		 */
		public static boolean saveMeasures(Capillaries capillaries, String binDirectory) {
			if (binDirectory == null) {
				Logger.warn("CapillariesPersistence:save_CapillariesArrayMeasures() directory is null");
				return false;
			}

			Path path = Paths.get(binDirectory);
			if (!Files.exists(path)) {
				Logger.warn("CapillariesPersistence:save_CapillariesArrayMeasures() directory does not exist: "
						+ binDirectory);
				return false;
			}

			try {
				FileWriter csvWriter = new FileWriter(binDirectory + File.separator + ID_V2_CAPILLARIESMEASURES_CSV);
				csvWriter.write("#" + csvSep + "version" + csvSep + CSV_VERSION + "\n");
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.TOPRAW, csvSep);
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.TOPLEVEL, csvSep);
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.BOTTOMLEVEL, csvSep);
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.TOPDERIVATIVE, csvSep);
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.GULPS, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.info("CapillariesPersistence:save_CapillariesArrayMeasures() Saved measures to "
						+ ID_V2_CAPILLARIESMEASURES_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CapillariesPersistence:save_CapillariesArrayMeasures() Error: " + e.getMessage(), e);
				return false;
			}
		}
	}

}
