package plugins.fmp.multitools.fmp_experiment.cages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.Logger;

public class CagesPersistence {

//	private static final String ID_CAGES = "Cages";
//	private static final String ID_NCAGES = "n_cages";
//	private static final String ID_NCAGESALONGX = "N_cages_along_X";
//	private static final String ID_NCAGESALONGY = "N_cages_along_Y";
//	private static final String ID_NCOLUMNSPERCAGE = "N_columns_per_cage";
//	private static final String ID_NROWSPERCAGE = "N_rows_per_cage";

	// New v2 format filenames
	private static final String ID_V2_CAGESDESCRIPTION_CSV = "v2_cages_description.csv";
	private static final String ID_V2_CAGESMEASURES_CSV = "v2_cages_measures.csv";

//	// Legacy filenames (for fallback)
//	private static final String ID_CAGESARRAY_CSV = "CagesArray.csv";
//	private static final String ID_CAGESARRAYMEASURES_CSV = "CagesArrayMeasures.csv";
//	private static final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// ========================================================================

	public boolean load_Cages(Cages cages, String directory) {
		// Try v2 format first, then delegate to Legacy for all fallback logic
		// Legacy fallback handles: legacy CSV → XML, with proper ROI and fly positions
		// handling
		return Persistence.loadDescription(cages, directory);
	}

	public boolean save_Cages(Cages cages, String directory) {
		if (directory == null) {
			Logger.warn("CagesPersistence:save_Cages() directory is null");
			return false;
		}

		Path path = Paths.get(directory);
		if (!Files.exists(path)) {
			Logger.warn("CagesPersistence:save_Cages() directory does not exist: " + directory);
			return false;
		}

		// Save descriptions to v2_ format file (includes ROI coordinates in CSV)
		return Persistence.saveDescription(cages, directory);
	}

	/**
	 * Saves cage descriptions (DESCRIPTION and CAGE sections) to CagesArray.csv in
	 * results directory.
	 * 
	 * @param cages            the Cages to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveCagesDescription(Cages cages, String resultsDirectory) {
		return Persistence.saveDescription(cages, resultsDirectory);
	}

	/**
	 * Saves cage measures (POSITION section) to CagesMeasures.csv in bin directory.
	 * 
	 * @param cages        the CagesArray to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean saveCagesMeasures(Cages cages, String binDirectory) {
		return Persistence.saveMeasures(cages, binDirectory);
	}

	/**
	 * Loads cage descriptions (DESCRIPTION and CAGE sections) from CagesArray.csv.
	 * Stops reading when it encounters a POSITION section.
	 * 
	 * @param cages            the Cages to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadCagesDescription(Cages cages, String resultsDirectory) {
		return Persistence.loadDescription(cages, resultsDirectory);
	}

	/**
	 * Loads cage measures (POSITION section) from CagesMeasures.csv in bin
	 * directory.
	 * 
	 * @param cages        the Cages to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean loadCagesMeasures(Cages cages, String binDirectory) {
		return Persistence.loadMeasures(cages, binDirectory);
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
			boolean descriptionsLoaded = load_Cages(cages, directory);

			// Also load measures from bin directory (if available)
			if (exp != null) {
				String binDir = exp.getKymosBinFullDirectory();
				if (binDir != null) {
					loadCagesMeasures(cages, binDir);
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
			return saveCagesDescription(cages, directory);
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
		 * If v2 format is not found, delegates to Legacy class for fallback handling.
		 */
		public static boolean loadDescription(Cages cages, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			String pathToCsv = resultsDirectory + File.separator + ID_V2_CAGESDESCRIPTION_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return CagesPersistenceLegacy.loadDescriptionWithFallback(cages, resultsDirectory);
			}

			// Load from v2 format
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
								CagesPersistenceLegacy.csvLoad_DESCRIPTION(cages, csvReader, sep);
								break;
							case "CAGE":
							case "CAGES":
								cageLoaded = true;
								CagesPersistenceLegacy.csvLoad_CAGE(cages, csvReader, sep);
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
				return descriptionLoaded || cageLoaded;
			} catch (Exception e) {
				Logger.error("CagesPersistence:loadDescription() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Loads cage measures (POSITION section) from v2 format file in bin directory.
		 * If v2 format is not found, delegates to Legacy class for fallback handling.
		 */
		public static boolean loadMeasures(Cages cages, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			String pathToCsv = binDirectory + File.separator + ID_V2_CAGESMEASURES_CSV;
			File csvFile = new File(pathToCsv);
			if (!csvFile.isFile()) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return CagesPersistenceLegacy.loadMeasuresWithFallback(cages, binDirectory);
			}

			// Load from v2 format
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
		 * Saves cage descriptions (DESCRIPTION and CAGE sections) to Cages.csv in
		 * results directory. Always saves to v2_ format.
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
				// Always save to v2_ format
				FileWriter csvWriter = new FileWriter(resultsDirectory + File.separator + ID_V2_CAGESDESCRIPTION_CSV);
				CagesPersistenceLegacy.csvSaveDESCRIPTIONSection(cages, csvWriter, csvSep);
				CagesPersistenceLegacy.csvSaveCAGESection(cages, csvWriter, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.info("CagesPersistence:saveCages() Saved descriptions to " + ID_V2_CAGESDESCRIPTION_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CagesPersistence:saveCages() Error: " + e.getMessage(), e);
				return false;
			}
		}

		/**
		 * Saves cage measures (POSITION section) to CagesMeasures.csv in bin directory.
		 * Always saves to v2_ format.
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
				// Always save to v2_ format
				FileWriter csvWriter = new FileWriter(binDirectory + File.separator + ID_V2_CAGESMEASURES_CSV);
				CagesPersistenceLegacy.csvSaveMeasuresSection(cages, csvWriter, EnumCageMeasures.POSITION, csvSep);
				csvWriter.flush();
				csvWriter.close();
				Logger.info("CagesPersistence:saveCagesMeasures() Saved measures to " + ID_V2_CAGESMEASURES_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("CagesPersistence:saveCagesMeasures() Error: " + e.getMessage(), e);
				return false;
			}
		}
	}

}
