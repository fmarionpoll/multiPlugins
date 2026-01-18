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

	// New v2 format filenames
	public final static String ID_V2_CAPILLARIESDESCRIPTION_CSV = "v2_capillaries_description.csv";
	public final static String ID_V2_CAPILLARIESMEASURES_CSV = "v2_capillaries_measures.csv";

	// Legacy filenames (for fallback)
	public final static String ID_CAPILLARIESARRAY_CSV = "CapillariesArray.csv";
	public final static String ID_CAPILLARIESARRAYMEASURES_CSV = "CapillariesArrayMeasures.csv";
	public final static String ID_MCCAPILLARIES_XML = "MCcapillaries.xml";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// ========================================================================

	/**
	 * Loads capillary descriptions (DESCRIPTION section) from CapillariesArray.csv.
	 * 
	 * @param capillaries      the Capillaries to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean load_CapillariesDescription(Capillaries capillaries, String resultsDirectory) {
		return Persistence.loadDescription(capillaries, resultsDirectory);
	}

	/**
	 * Loads capillary measures from CapillariesArrayMeasures.csv in bin directory.
	 * 
	 * @param capillaries  the Capillaries to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean load_CapillariesMeasures(Capillaries capillaries, String binDirectory) {
		return Persistence.loadMeasures(capillaries, binDirectory);
	}

	/**
	 * Saves capillary descriptions (DESCRIPTION section) to CapillariesArray.csv in
	 * results directory.
	 * 
	 * @param capillaries      the Capillaries to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveCapillariesDescription(Capillaries capillaries, String resultsDirectory) {
		return Persistence.saveDescription(capillaries, resultsDirectory);
	}

	/**
	 * Saves capillary measures to CapillariesArrayMeasures.csv in bin directory.
	 * 
	 * @param capillaries  the Capillaries to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean save_CapillariesMeasures(Capillaries capillaries, String binDirectory) {
		return Persistence.saveMeasures(capillaries, binDirectory);
	}

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
		 * format is not found, delegates to Legacy class for fallback handling.
		 * 
		 * @param capillaries      the Capillaries to populate
		 * @param resultsDirectory the results directory
		 * @return true if successful
		 */
		public static boolean loadDescription(Capillaries capillaries, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			String pathToCsv = resultsDirectory + File.separator + ID_V2_CAPILLARIESDESCRIPTION_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
			}

			// Load from v2 format
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
							CapillariesPersistenceLegacy.csvLoad_Description(capillaries, csvReader, sep);
							break;
						case "CAPILLARIES":
							// Load CAPILLARIES section with ROI coordinates
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
							// Stop reading when we hit measures section
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
		 * is not found, delegates to Legacy class for fallback handling.
		 * 
		 * @param capillaries  the Capillaries to populate
		 * @param binDirectory the bin directory (e.g., results/bin60)
		 * @return true if successful
		 */
		public static boolean loadMeasures(Capillaries capillaries, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			String pathToCsv = binDirectory + File.separator + ID_V2_CAPILLARIESMEASURES_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
			}

			// Load from v2 format
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
						case "DESCRIPTION":
							// Skip description section in measures file
							CapillariesPersistenceLegacy.csvSkipSection(csvReader, sep);
							break;
						case "CAPILLARIES":
							// Skip CAPILLARIES section
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
		 * Saves capillary descriptions (DESCRIPTION section) to CapillariesArray.csv in
		 * results directory. Always saves to v2_ format.
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
				// Always save to v2_ format
				FileWriter csvWriter = new FileWriter(
						resultsDirectory + File.separator + ID_V2_CAPILLARIESDESCRIPTION_CSV);
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
		 * Saves capillary measures to CapillariesArrayMeasures.csv in bin directory.
		 * Always saves to v2_ format.
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
				// Always save to v2_ format
				FileWriter csvWriter = new FileWriter(binDirectory + File.separator + ID_V2_CAPILLARIESMEASURES_CSV);
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
