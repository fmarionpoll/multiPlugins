package plugins.fmp.multitools.experiment.cages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Logger;

public class CagesPersistence {

//	private static final String ID_CAGES = "Cages";
//	private static final String ID_NCAGES = "n_cages";
//	private static final String ID_NCAGESALONGX = "N_cages_along_X";
//	private static final String ID_NCAGESALONGY = "N_cages_along_Y";
//	private static final String ID_NCOLUMNSPERCAGE = "N_columns_per_cage";
//	private static final String ID_NROWSPERCAGE = "N_rows_per_cage";

	// Current format filenames (version stored internally in file header)
	private static final String ID_V2_CAGESDESCRIPTION_CSV = "CagesDescription.csv";
	private static final String ID_V2_CAGESMEASURES_CSV = "CagesMeasures.csv";

	// Version for CSV files
	private static final String CSV_VERSION = "2.0";

//	// Legacy filenames (for fallback)
//	private static final String ID_CAGESARRAY_CSV = "CagesArray.csv";
//	private static final String ID_CAGESARRAYMEASURES_CSV = "CagesArrayMeasures.csv";
//	private static final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// New standardized method names (v2.3.3+)
	// ========================================================================

	/**
	 * Loads cage descriptions from the results directory. Descriptions include cage
	 * properties but not time-series measures (fly positions). Tries new v2.1
	 * format first, then falls back to legacy format.
	 * 
	 * @param cages            the Cages to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadDescriptions(Cages cages, String resultsDirectory) {
		return Persistence.loadDescription(cages, resultsDirectory);
	}

	/**
	 * Loads cage measures from the bin directory (e.g., results/bin60). Measures
	 * include fly position data over time. Tries new v2.1 format first, then falls
	 * back to legacy format.
	 * 
	 * @param cages        the Cages to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean loadMeasures(Cages cages, String binDirectory) {
		return Persistence.loadMeasures(cages, binDirectory);
	}

	/**
	 * Saves cage descriptions to the results directory. Descriptions include cage
	 * properties but not time-series measures (fly positions). Uses new v2.1
	 * format.
	 * 
	 * @param cages            the Cages to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveDescriptions(Cages cages, String resultsDirectory) {
		return Persistence.saveDescription(cages, resultsDirectory);
	}

	/**
	 * Saves cage measures to the bin directory (e.g., results/bin60). Measures
	 * include fly position data over time. Uses new v2.1 format.
	 * 
	 * @param cages        the Cages to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean saveMeasures(Cages cages, String binDirectory) {
		return Persistence.saveMeasures(cages, binDirectory);
	}

	// ========================================================================
	// Deprecated methods - kept for backward compatibility (will be removed in
	// v3.0)
	// ========================================================================

	/**
	 * @deprecated Use {@link #loadDescriptions(String)} instead.
	 */
	@Deprecated
	public boolean load_Cages(Cages cages, String directory) {
		return Persistence.loadDescription(cages, directory);
	}

	/**
	 * @deprecated Use {@link #saveDescriptions(String)} instead.
	 */
	@Deprecated
	public boolean save_Cages(Cages cages, String directory) {
		return Persistence.saveDescription(cages, directory);
	}

	/**
	 * @deprecated Use {@link #saveDescriptions(String)} instead.
	 */
	@Deprecated
	public boolean saveCagesDescription(Cages cages, String resultsDirectory) {
		return saveDescriptions(cages, resultsDirectory);
	}

	/**
	 * @deprecated Use {@link #saveMeasures(String)} instead.
	 */
	@Deprecated
	public boolean saveCagesMeasures(Cages cages, String binDirectory) {
		return saveMeasures(cages, binDirectory);
	}

	/**
	 * @deprecated Use {@link #loadDescriptions(String)} instead.
	 */
	@Deprecated
	public boolean loadCagesDescription(Cages cages, String resultsDirectory) {
		return loadDescriptions(cages, resultsDirectory);
	}

	/**
	 * @deprecated Use {@link #loadMeasures(String)} instead.
	 */
	@Deprecated
	public boolean loadCagesMeasures(Cages cages, String binDirectory) {
		return loadMeasures(cages, binDirectory);
	}

	// ========================================================================
	// Legacy methods - private, only for internal use within persistence class
	// ========================================================================
//
//	private boolean xmlReadCagesFromFileNoQuestion(Cages cages, String tempname) {
//		return CagesPersistenceLegacy.xmlReadCagesFromFileNoQuestion(cages, tempname);
//	}

	/**
	 * Synchronously loads cage descriptions and measures from CSV/XML files.
	 * 
	 * @param cages     The Cages to populate
	 * @param directory The directory containing cage files
	 * @param exp       The experiment being loaded (for validation, currently
	 *                  unused)
	 * @return true if successful, false otherwise
	 */
	public boolean loadCages(Cages cages, String directory, Experiment exp) {
		if (directory == null) {
			Logger.warn("CagesPersistence:loadCages() directory is null");
			return false;
		}

		try {
			// Use existing synchronous load method to load descriptions
			boolean descriptionsLoaded = Persistence.loadDescription(cages, directory);

			// Also load measures from bin directory (if available)
			if (exp != null) {
				String binDir = exp.getKymosBinFullDirectory();
				if (binDir != null) {
					loadMeasures(cages, binDir);
				}
			}

			return descriptionsLoaded;
		} catch (Exception e) {
			Logger.error("CagesPersistence:loadCages() Error: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Synchronously saves cage descriptions to CSV file.
	 * 
	 * @param cages     The Cages to save
	 * @param directory The directory to save Cages.csv
	 * @param exp       The experiment being saved (for validation, currently
	 *                  unused)
	 * @return true if successful, false otherwise
	 */
	public boolean saveCages(Cages cages, String directory, Experiment exp) {
		if (directory == null) {
			Logger.warn("CagesPersistence:saveCages() directory is null");
			return false;
		}

		Path path = Paths.get(directory);
		if (!Files.exists(path)) {
			Logger.warn("CagesPersistence:saveCages() directory does not exist: " + directory);
			return false;
		}

		try {
			// Save descriptions to new format (Cages.csv in results directory)
			return saveDescriptions(cages, directory);
		} catch (Exception e) {
			Logger.error("CagesPersistence:saveCages() Error: " + e.getMessage(), e);
			return false;
		}
	}

	// ========================================================================
	// Nested class for current v2 format persistence
	// ========================================================================

	public static class Persistence {
		private static final String csvSep = ";";

		/**
		 * Loads cage descriptions (DESCRIPTION and CAGE sections) from v2 format file.
		 * If v2 format is not found or missing version header, delegates to Legacy
		 * class for fallback handling.
		 */
		public static boolean loadDescription(Cages cages, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			String pathToCsv = resultsDirectory + File.separator + ID_V2_CAGESDESCRIPTION_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				boolean loaded = CagesPersistenceLegacy.loadDescriptionWithFallback(cages, resultsDirectory);
				if (loaded) {
					// Auto-migrate to v2 format once legacy load succeeded
					saveDescription(cages, resultsDirectory);
				}
				return loaded;
			}

			// Validate version header before committing to new format parser
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String firstLine = csvReader.readLine();
				csvReader.close();

				if (firstLine == null || !firstLine.startsWith("#")) {
					Logger.info("CagesPersistence: No header found in " + ID_V2_CAGESDESCRIPTION_CSV
							+ ", using legacy parser");
					boolean loaded = CagesPersistenceLegacy.loadDescriptionWithFallback(cages, resultsDirectory);
					if (loaded) {
						saveDescription(cages, resultsDirectory);
					}
					return loaded;
				}

				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					Logger.info("CagesPersistence: First line is not version header in " + ID_V2_CAGESDESCRIPTION_CSV
							+ ", using legacy parser");
					boolean loaded = CagesPersistenceLegacy.loadDescriptionWithFallback(cages, resultsDirectory);
					if (loaded) {
						saveDescription(cages, resultsDirectory);
					}
					return loaded;
				}

				String fileVersion = versionData[2];
				if (!fileVersion.equals(CSV_VERSION)) {
					Logger.warn("CagesPersistence: File version " + fileVersion + " differs from current version "
							+ CSV_VERSION);
				}
			} catch (IOException e) {
				Logger.error("CagesPersistence: Error reading file header: " + e.getMessage(), e);
				return CagesPersistenceLegacy.loadDescriptionWithFallback(cages, resultsDirectory);
			}

			// Version validated - proceed with new format parser
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
							case "version":
								break;
							case "DESCRIPTION":
								descriptionLoaded = true;
								CagesPersistenceLegacy.csvLoad_DESCRIPTION(cages, csvReader, sep);
								break;
							case "CAGE":
							case "CAGES":
								cageLoaded = true;
								CagesPersistenceLegacy.csvLoad_CAGE(cages, csvReader, sep);
								break;
							case "POSITION":
								csvReader.close();
								return descriptionLoaded || cageLoaded;
							default:
								break;
							}
						}
					}
				}
				csvReader.close();
				return descriptionLoaded || cageLoaded;
			} catch (Exception e) {
				Logger.error("CagesPersistence:loadDescription() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Loads cage measures (POSITION section) from v2 format file in bin directory.
		 * If v2 format is not found or missing version header, delegates to Legacy
		 * class for fallback handling.
		 */
		public static boolean loadMeasures(Cages cages, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			String pathToCsv = binDirectory + File.separator + ID_V2_CAGESMEASURES_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				boolean loaded = CagesPersistenceLegacy.loadMeasuresWithFallback(cages, binDirectory);
				if (loaded) {
					// Auto-migrate to v2 format once legacy load succeeded
					saveMeasures(cages, binDirectory);
				}
				return loaded;
			}

			// Validate version header before committing to new format parser
			try {
				BufferedReader csvReader = new BufferedReader(new FileReader(pathToCsv));
				String firstLine = csvReader.readLine();
				csvReader.close();

				if (firstLine == null || !firstLine.startsWith("#")) {
					Logger.info("CagesPersistence: No header found in " + ID_V2_CAGESMEASURES_CSV
							+ ", using legacy parser");
					boolean loaded = CagesPersistenceLegacy.loadMeasuresWithFallback(cages, binDirectory);
					if (loaded) {
						saveMeasures(cages, binDirectory);
					}
					return loaded;
				}

				String sep = String.valueOf(firstLine.charAt(1));
				String[] versionData = firstLine.split(sep);
				if (versionData.length < 3 || !versionData[1].equals("version")) {
					Logger.info("CagesPersistence: First line is not version header in " + ID_V2_CAGESMEASURES_CSV
							+ ", using legacy parser");
					boolean loaded = CagesPersistenceLegacy.loadMeasuresWithFallback(cages, binDirectory);
					if (loaded) {
						saveMeasures(cages, binDirectory);
					}
					return loaded;
				}

				String fileVersion = versionData[2];
				if (!fileVersion.equals(CSV_VERSION)) {
					Logger.warn("CagesPersistence: File version " + fileVersion + " differs from current version "
							+ CSV_VERSION);
				}
			} catch (IOException e) {
				Logger.error("CagesPersistence: Error reading file header: " + e.getMessage(), e);
				return CagesPersistenceLegacy.loadMeasuresWithFallback(cages, binDirectory);
			}

			// Version validated - proceed with new format parser
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
							if (data[1].equals("version")) {
								continue;
							}
							if (data[1].equals("POSITION")) {
								CagesPersistenceLegacy.csvLoad_Measures(cages, csvReader, EnumCageMeasures.POSITION,
										sep);
								csvReader.close();
								return true;
							}
						}
					}
				}
				csvReader.close();
				return false;
			} catch (Exception e) {
				Logger.error("CagesPersistence:loadMeasures() Error: " + e.getMessage(), e);
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Saves cage descriptions (DESCRIPTION and CAGE sections) to
		 * CagesDescription.csv in results directory. Always saves with version header.
		 */
		public static boolean saveDescription(Cages cages, String resultsDirectory) {
			if (resultsDirectory == null) {
				Logger.warn("CagesPersistence:saveCages() directory is null");
				return false;
			}

			Path path = Paths.get(resultsDirectory);
			if (!Files.exists(path)) {
				Logger.warn("CagesPersistence:saveCages() directory does not exist: " + resultsDirectory);
				return false;
			}

			try {
				FileWriter csvWriter = new FileWriter(resultsDirectory + File.separator + ID_V2_CAGESDESCRIPTION_CSV);
				csvWriter.write("#" + csvSep + "version" + csvSep + CSV_VERSION + "\n");
				CagesPersistenceLegacy.csvSaveDESCRIPTIONSection(cages, csvWriter, csvSep);
				CagesPersistenceLegacy.csvSaveCAGESection(cages, csvWriter, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.debug("CagesPersistence:saveCages() Saved descriptions to " + ID_V2_CAGESDESCRIPTION_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CagesPersistence:saveCages() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Saves cage measures (POSITION section) to CagesMeasures.csv in bin directory.
		 * Always saves with version header.
		 */
		public static boolean saveMeasures(Cages cages, String binDirectory) {
			if (binDirectory == null) {
				Logger.warn("CagesPersistence:saveCagesMeasures() directory is null");
				return false;
			}

			Path path = Paths.get(binDirectory);
			if (!Files.exists(path)) {
				Logger.warn("CagesPersistence:saveCagesMeasures() directory does not exist: " + binDirectory);
				return false;
			}

			try {
				FileWriter csvWriter = new FileWriter(binDirectory + File.separator + ID_V2_CAGESMEASURES_CSV);
				csvWriter.write("#" + csvSep + "version" + csvSep + CSV_VERSION + "\n");
				CagesPersistenceLegacy.csvSaveMeasuresSection(cages, csvWriter, EnumCageMeasures.POSITION, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.debug("CagesPersistence:saveCagesMeasures() Saved measures to " + ID_V2_CAGESMEASURES_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CagesPersistence:saveCagesMeasures() Error: " + e.getMessage(), e);
				return false;
			}
		}
	}

}
