package plugins.fmp.multitools.experiment.spots;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.tools.Logger;

/**
 * Handles CSV-only persistence for SpotsArray.
 * ROIs are not persisted - they are regenerated from coordinates when needed for display.
 */
public class SpotsPersistence {

	// New v2 format filenames
	private static final String ID_V2_SPOTSARRAY_CSV = "v2_spots_description.csv";
	private static final String ID_V2_SPOTSARRAYMEASURES_CSV = "v2_spots_measures.csv";
	
	// Legacy filenames (for fallback)
	private static final String ID_SPOTSARRAY_CSV = "SpotsArray.csv";
	private static final String ID_SPOTSARRAYMEASURES_CSV = "SpotsArrayMeasures.csv";
	private static final String CSV_FILENAME = "SpotsMeasures.csv";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// ========================================================================

	/**
	 * Loads spots from CSV file with fallback logic.
	 * Tries new format first, then falls back to legacy format.
	 * 
	 * @param spotsArray the SpotsArray to populate
	 * @param directory  the directory containing spot files
	 * @return true if successful
	 */
	public boolean load_SpotsArray(Spots spotsArray, String directory) {
		if (directory == null) {
			Logger.warn("SpotsArrayPersistence:load_SpotsArray() directory is null");
			return false;
		}

		Path path = Paths.get(directory);
		if (!Files.exists(path)) {
			Logger.warn("SpotsArrayPersistence:load_SpotsArray() directory does not exist: " + directory);
			return false;
		}

		// Priority 1: Try new v2_ format (descriptions only)
		boolean descriptionsLoaded = Persistence.loadDescription(spotsArray, directory);
		if (descriptionsLoaded) {
			Logger.info("SpotsArrayPersistence:load_SpotsArray() loaded " + spotsArray.getSpotListCount()
					+ " spot descriptions from new format");
			return true;
		}

		// Priority 2: Fall back to legacy format (combined file)
		try {
			boolean success = spotsArray.loadSpotsAll(directory);
			if (success) {
				Logger.info("SpotsArrayPersistence:load_SpotsArray() loaded " + spotsArray.getSpotListCount()
						+ " spots from legacy format");
			}
			return success;
		} catch (Exception e) {
			Logger.error("SpotsArrayPersistence:load_SpotsArray() Failed to load spots from: " + directory, e, true);
			return false;
		}
	}
	
	/**
	 * Loads spot descriptions (SPOTS_ARRAY and SPOTS sections) from SpotsArray.csv.
	 * Stops reading when it encounters measure sections.
	 * 
	 * @param spots the SpotsArray to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadSpotsDescription(Spots spots, String resultsDirectory) {
		return Persistence.loadDescription(spots, resultsDirectory);
	}
	
	/**
	 * Loads spot measures from SpotsArrayMeasures.csv in bin directory.
	 * 
	 * @param spotsArray the SpotsArray to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean loadSpotsMeasures(Spots spotsArray, String binDirectory) {
		return Persistence.loadMeasures(spotsArray, binDirectory);
	}

	/**
	 * Saves spots to CSV file (descriptions only, to results directory).
	 * 
	 * @param spotsArray the SpotsArray to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean save_SpotsArray(Spots spotsArray, String resultsDirectory) {
		return Persistence.saveDescription(spotsArray, resultsDirectory);
	}
	
	/**
	 * Saves spot descriptions (SPOTS_ARRAY and SPOTS sections) to SpotsArray.csv.
	 * 
	 * @param spotsArray the SpotsArray to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveSpotsDescription(Spots spotsArray, String resultsDirectory) {
		return Persistence.saveDescription(spotsArray, resultsDirectory);
	}
	
	/**
	 * Saves spot measures (AREA_SUM, AREA_SUMCLEAN sections) to SpotsArrayMeasures.csv in bin directory.
	 * 
	 * @param spotsArray the SpotsArray to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean saveSpotsMeasures(Spots spotsArray, String binDirectory) {
		return Persistence.saveMeasures(spotsArray, binDirectory);
	}

	/**
	 * Gets the CSV filename used for persistence (descriptions).
	 * 
	 * @return the CSV filename
	 */
	public String getCSVFilename() {
		return ID_SPOTSARRAY_CSV;
	}
	
	/**
	 * Gets the CSV filename used for measures persistence.
	 * 
	 * @return the measures CSV filename
	 */
	public String getMeasuresCSVFilename() {
		return ID_SPOTSARRAYMEASURES_CSV;
	}
	
	/**
	 * Gets the legacy CSV filename (for fallback).
	 * 
	 * @return the legacy CSV filename
	 */
	public String getLegacyCSVFilename() {
		return CSV_FILENAME;
	}

	// ========================================================================
	// Nested class for current v2 format persistence
	// ========================================================================

	public static class Persistence {
		
		/**
		 * Loads spot descriptions (SPOTS_ARRAY and SPOTS sections) from v2 format file.
		 * If v2 format is not found, delegates to Legacy class for fallback handling.
		 */
		public static boolean loadDescription(Spots spotsArray, String resultsDirectory) {
			if (resultsDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			Path csvPath = Paths.get(resultsDirectory, ID_V2_SPOTSARRAY_CSV);
			if (!Files.exists(csvPath)) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return SpotsPersistenceLegacy.loadDescriptionWithFallback(spotsArray, resultsDirectory);
			}

			// Load from v2 format
			try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
				String line;
				String sep = ";";
				boolean descriptionLoaded = false;
				boolean spotsLoaded = false;
				
				while ((line = reader.readLine()) != null) {
					if (line.length() > 0 && line.charAt(0) == '#')
						sep = String.valueOf(line.charAt(1));
					
					String[] data = line.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							switch (data[1]) {
							case "SPOTS_ARRAY":
								descriptionLoaded = true;
								SpotsPersistenceLegacy.csvLoad_SpotsArray_Metadata(spotsArray, reader, sep);
								break;
							case "SPOTS":
								spotsLoaded = true;
								SpotsPersistenceLegacy.csvLoad_Spots_Description(spotsArray, reader, sep);
								break;
							case "AREA_SUM":
							case "AREA_SUMCLEAN":
							case "AREA_FLYPRESENT":
								// Stop reading when we hit measures section
								return descriptionLoaded || spotsLoaded;
							default:
								// Check if it's a measure type
								EnumSpotMeasures measure = 
									EnumSpotMeasures.findByText(data[1]);
								if (measure != null) {
									// Stop reading when we hit measures section
									return descriptionLoaded || spotsLoaded;
								}
								break;
							}
						}
					}
				}
				return descriptionLoaded || spotsLoaded;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistence:loadDescription() Failed: " + e.getMessage(), e, true);
				return false;
			}
		}
		
		/**
		 * Loads spot measures from v2 format file in bin directory.
		 * If v2 format is not found, delegates to Legacy class for fallback handling.
		 */
		public static boolean loadMeasures(Spots spotsArray, String binDirectory) {
			if (binDirectory == null) {
				return false;
			}

			// Try v2_ format ONLY
			Path csvPath = Paths.get(binDirectory, ID_V2_SPOTSARRAYMEASURES_CSV);
			if (!Files.exists(csvPath)) {
				// v2 format not found - delegate to Legacy class for all fallback logic
				return SpotsPersistenceLegacy.loadMeasuresWithFallback(spotsArray, binDirectory);
			}

			// Load from v2 format
			try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
				String line;
				String sep = ";";
				
				while ((line = reader.readLine()) != null) {
					if (line.length() > 0 && line.charAt(0) == '#')
						sep = String.valueOf(line.charAt(1));
					
					String[] data = line.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							EnumSpotMeasures measure = 
								EnumSpotMeasures.findByText(data[1]);
							if (measure != null) {
								SpotsPersistenceLegacy.csvLoad_Spots_Measures(spotsArray, reader, measure, sep);
							}
						}
					}
				}
				return true;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistence:loadMeasures() Failed: " + e.getMessage(), e, true);
				return false;
			}
		}

		/**
		 * Saves spot descriptions (SPOTS_ARRAY and SPOTS sections) to SpotsArray.csv.
		 * Always saves to v2_ format.
		 */
		public static boolean saveDescription(Spots spotsArray, String resultsDirectory) {
			if (resultsDirectory == null) {
				Logger.warn("SpotsArrayPersistence:saveSpotsArray() directory is null");
				return false;
			}

			Path path = Paths.get(resultsDirectory);
			if (!Files.exists(path)) {
				Logger.warn("SpotsArrayPersistence:saveSpotsArray() directory does not exist: " + resultsDirectory);
				return false;
			}

			// Always save to v2_ format
			Path csvPath = Paths.get(resultsDirectory, ID_V2_SPOTSARRAY_CSV);
			try (FileWriter writer = new FileWriter(csvPath.toFile())) {
				// Save spots array section
				if (!SpotsPersistenceLegacy.csvSave_DescriptionSection(spotsArray, writer, ";")) {
					return false;
				}
				Logger.info("SpotsArrayPersistence:saveSpotsArray() saved " + spotsArray.getSpotListCount()
						+ " spot descriptions to " + ID_V2_SPOTSARRAY_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("SpotsArrayPersistence:saveSpotsArray() Failed: " + e.getMessage(), e, true);
				return false;
			}
		}
		
		/**
		 * Saves spot measures (AREA_SUM, AREA_SUMCLEAN sections) to SpotsArrayMeasures.csv in bin directory.
		 * Always saves to v2_ format.
		 */
		public static boolean saveMeasures(Spots spotsArray, String binDirectory) {
			if (binDirectory == null) {
				Logger.warn("SpotsArrayPersistence:save_SpotsArrayMeasures() directory is null");
				return false;
			}

			Path path = Paths.get(binDirectory);
			if (!Files.exists(path)) {
				Logger.warn("SpotsArrayPersistence:save_SpotsArrayMeasures() directory does not exist: " + binDirectory);
				return false;
			}

			// Always save to v2_ format
			Path csvPath = Paths.get(binDirectory, ID_V2_SPOTSARRAYMEASURES_CSV);
			try (FileWriter writer = new FileWriter(csvPath.toFile())) {
				// Save measures sections
				if (!SpotsPersistenceLegacy.csvSave_MeasuresSection(spotsArray, writer, EnumSpotMeasures.AREA_SUM, ";")) {
					return false;
				}
				if (!SpotsPersistenceLegacy.csvSave_MeasuresSection(spotsArray, writer, EnumSpotMeasures.AREA_SUMCLEAN, ";")) {
					return false;
				}
				Logger.info("SpotsArrayPersistence:save_SpotsArrayMeasures() saved measures to " + ID_V2_SPOTSARRAYMEASURES_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("SpotsArrayPersistence:save_SpotsArrayMeasures() Failed: " + e.getMessage(), e, true);
				return false;
			}
		}
	}
}
