package plugins.fmp.multitools.experiment.capillaries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	 * Checks if any capillary description files exist in the results directory.
	 * This is useful to determine if an experiment has capillaries data.
	 * 
	 * @param resultsDirectory the results directory
	 * @return true if any capillary description file exists
	 */
	public boolean hasCapillariesDescriptionFiles(String resultsDirectory) {
		if (resultsDirectory == null) {
			return false;
		}

		Path v2Path = Paths.get(resultsDirectory, ID_V2_CAPILLARIESDESCRIPTION_CSV);
		if (Files.exists(v2Path)) {
			return true;
		}

		Path legacyPath = Paths.get(resultsDirectory, ID_CAPILLARIESARRAY_CSV);
		if (Files.exists(legacyPath)) {
			return true;
		}

		Path xmlPath = Paths.get(resultsDirectory, ID_MCCAPILLARIES_XML);
		return Files.exists(xmlPath);
	}

	/**
	 * Checks if any capillary measures files exist in the bin directory.
	 * 
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if any capillary measures file exists
	 */
	public boolean hasCapillariesMeasuresFiles(String binDirectory) {
		if (binDirectory == null) {
			return false;
		}

		Path v2Path = Paths.get(binDirectory, ID_V2_CAPILLARIESMEASURES_CSV);
		if (Files.exists(v2Path)) {
			return true;
		}

		Path legacyPath = Paths.get(binDirectory, ID_CAPILLARIESARRAYMEASURES_CSV);
		return Files.exists(legacyPath);
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

		private static double parseVersion(String v) {
			if (v == null || v.isEmpty()) {
				return 0;
			}
			try {
				String[] parts = v.trim().split("\\.");
				if (parts.length >= 2) {
					return Double.parseDouble(parts[0] + "." + parts[1]);
				}
				return Double.parseDouble(v);
			} catch (NumberFormatException e) {
				return 0;
			}
		}

		/**
		 * Loads capillary descriptions (DESCRIPTION section) from v2 format file. If v2
		 * format is not found or missing version header, delegates to Legacy class for
		 * fallback handling.
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

			// Parse version and apply strict version rules
			double fileVersion = 0;
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String firstLine = csvReader.readLine();
				csvReader.close();

				if (firstLine == null || !firstLine.startsWith("#")) {
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}

				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}

				fileVersion = parseVersion(versionData[2]);
				if (fileVersion < 2.0) {
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}
			} catch (IOException e) {
				return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
			}

			boolean allowXmlFallback = (fileVersion < 2.1);

			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String row;
				String sep = csvSep;

				boolean loaded = false;
				while ((row = csvReader.readLine()) != null) {
					if (row.length() > 0 && row.charAt(0) == '#')
						sep = String.valueOf(row.charAt(1));

					String[] data = row.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						switch (data.length > 1 ? data[1] : "") {
						case "version":
							break;
						case "DESCRIPTION":
							CapillariesPersistenceLegacy.csvLoad_Description(capillaries, csvReader, sep);
							break;
						case "CAPILLARIES":
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Description(capillaries, csvReader, sep);
							loaded = true;
							break;
						case "ALONGT":
							try {
								CapillariesPersistenceLegacy.csvLoad_AlongT(capillaries, csvReader, sep);
							} catch (IOException e) {
								// ignore
							}
							break;
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
				if (loaded) {
					return true;
				}
				if (allowXmlFallback) {
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}
				return false;
			} catch (Exception e) {
				if (allowXmlFallback) {
					return CapillariesPersistenceLegacy.loadDescriptionWithFallback(capillaries, resultsDirectory);
				}
				return false;
			}
		}

		/**
		 * Loads capillary measures from v2 format file in bin directory. If v2 format
		 * is not found or missing version header, delegates to Legacy class for
		 * fallback handling.
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
					return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
				}

				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					return CapillariesPersistenceLegacy.loadMeasuresWithFallback(capillaries, binDirectory);
				}
			} catch (IOException e) {
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
						switch (data.length > 1 ? data[1] : "") {
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
						case "BOTTOM":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.BOTTOMLEVEL, sep, row.contains("xi"));
							break;
						case "TOPDERIVATIVE":
						case "TOPDER":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.TOPDERIVATIVE, sep, row.contains("xi"));
							break;
						case "THRESHOLD":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.THRESHOLD, sep, row.contains("xi"));
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
						case "GULPS_F":
							seenGulpsFlat = true;
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_Capillaries_Measures(capillaries, csvReader,
									EnumCapillaryMeasures.GULPS, sep, true);
							break;
						case "REFERENCE":
							measuresLoaded = true;
							CapillariesPersistenceLegacy.csvLoad_ReferenceMeasures(capillaries, csvReader, sep);
							break;
						default:
							break;
						}
					}
				}
				csvReader.close();
				capillaries.migrateThresholdFromCapillariesIfNeeded();
				return measuresLoaded;
			} catch (Exception e) {
				return false;
			}
		}

		/**
		 * Saves capillary descriptions (DESCRIPTION section) to
		 * CapillariesDescription.csv in results directory. Always saves with version
		 * header.
		 * 
		 * @param capillaries      the Capillaries to save
		 * @param resultsDirectory the results directory
		 * @return true if successful
		 */
		public static boolean saveDescription(Capillaries capillaries, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			Path path = Paths.get(resultsDirectory);
			if (!Files.exists(path)) {
				return false;
			}

			try {
				FileWriter csvWriter = new FileWriter(
						resultsDirectory + File.separator + ID_V2_CAPILLARIESDESCRIPTION_CSV);
				csvWriter.write("#" + csvSep + "version" + csvSep + CSV_VERSION + "\n");
				CapillariesPersistenceLegacy.csvSave_DescriptionSection(capillaries, csvWriter, csvSep);
				csvWriter.flush();
				csvWriter.close();
				return true;
			} catch (IOException e) {
				return false;
			}
		}

		/**
		 * Saves capillary measures to CapillariesMeasures.csv in bin directory. Always
		 * saves with version header.
		 * 
		 * @param capillaries  the Capillaries to save
		 * @param binDirectory the bin directory (e.g., results/bin60)
		 * @return true if successful
		 */
		public static boolean saveMeasures(Capillaries capillaries, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			Path path = Paths.get(binDirectory);
			if (!Files.exists(path)) {
				return false;
			}

			try {
				capillaries.copyThresholdToFirstEmptyCapillaryForLegacySave();
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
						EnumCapillaryMeasures.THRESHOLD, csvSep);
				CapillariesPersistenceLegacy.csvSave_MeasuresSection(capillaries, csvWriter,
						EnumCapillaryMeasures.GULPS, csvSep);
				CapillariesPersistenceLegacy.csvSave_ReferenceSection(capillaries, csvWriter, csvSep);
				csvWriter.flush();
				csvWriter.close();
				return true;
			} catch (IOException e) {
				return false;
			}
		}
	}

}
