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
 * Handles CSV-only persistence for SpotsArray. ROIs are not persisted - they
 * are regenerated from coordinates when needed for display.
 */
public class SpotsPersistence {

	// New v2.1 format filenames (with ROI type support)
	private static final String ID_V2_SPOTSARRAY_CSV = "v2.1_spots_description.csv";
	private static final String ID_V2_SPOTSARRAYMEASURES_CSV = "v2.1_spots_measures.csv";

	// Version for CSV files
	private static final String CSV_VERSION = "2.1";

	// Legacy filenames (for fallback)
	private static final String ID_SPOTSARRAY_CSV = "SpotsArray.csv";
	private static final String ID_SPOTSARRAYMEASURES_CSV = "SpotsArrayMeasures.csv";
	private static final String CSV_FILENAME = "SpotsMeasures.csv";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// New standardized method names (v2.3.3+)
	// ========================================================================

	/**
	 * Loads spot descriptions from the results directory. Descriptions include spot
	 * properties but not time-series measures. Tries new v2 format first, then
	 * falls back to legacy format.
	 * 
	 * @param spots            the Spots to populate
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean loadDescriptions(Spots spots, String resultsDirectory) {
		return Persistence.loadDescription(spots, resultsDirectory);
	}

	/**
	 * Loads spot measures from the bin directory (e.g., results/bin60). Measures
	 * include time-series data like area_sum, area_clean, flypresent. Tries new v2
	 * format first, then falls back to legacy format.
	 * 
	 * @param spots        the Spots to populate
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean loadMeasures(Spots spots, String binDirectory) {
		return Persistence.loadMeasures(spots, binDirectory);
	}

	/**
	 * Saves spot descriptions to the results directory. Descriptions include spot
	 * properties but not time-series measures. Uses new v2 format.
	 * 
	 * @param spots            the Spots to save
	 * @param resultsDirectory the results directory
	 * @return true if successful
	 */
	public boolean saveDescriptions(Spots spots, String resultsDirectory) {
		return Persistence.saveDescription(spots, resultsDirectory);
	}

	/**
	 * Saves spot measures to the bin directory (e.g., results/bin60). Measures
	 * include time-series data like area_sum, area_clean, flypresent. Uses new v2
	 * format.
	 * 
	 * @param spots        the Spots to save
	 * @param binDirectory the bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public boolean saveMeasures(Spots spots, String binDirectory) {
		return Persistence.saveMeasures(spots, binDirectory);
	}

	// ========================================================================
	// Deprecated methods - kept for backward compatibility (will be removed in
	// v3.0)
	// ========================================================================

	/**
	 * @deprecated Use {@link #loadDescriptions(Spots, String)} instead.
	 */
	@Deprecated
	public boolean load_SpotsArray(Spots spotsArray, String directory) {
		return loadDescriptions(spotsArray, directory);
	}

	/**
	 * @deprecated Use {@link #loadDescriptions(Spots, String)} instead.
	 */
	@Deprecated
	public boolean loadSpotsDescription(Spots spots, String resultsDirectory) {
		return loadDescriptions(spots, resultsDirectory);
	}

	/**
	 * @deprecated Use {@link #loadMeasures(Spots, String)} instead.
	 */
	@Deprecated
	public boolean loadSpotsMeasures(Spots spotsArray, String binDirectory) {
		return loadMeasures(spotsArray, binDirectory);
	}

	/**
	 * @deprecated Use {@link #saveDescriptions(Spots, String)} instead.
	 */
	@Deprecated
	public boolean save_SpotsArray(Spots spotsArray, String resultsDirectory) {
		return saveDescriptions(spotsArray, resultsDirectory);
	}

	/**
	 * @deprecated Use {@link #saveDescriptions(Spots, String)} instead.
	 */
	@Deprecated
	public boolean saveSpotsDescription(Spots spotsArray, String resultsDirectory) {
		return saveDescriptions(spotsArray, resultsDirectory);
	}

	/**
	 * @deprecated Use {@link #saveMeasures(Spots, String)} instead.
	 */
	@Deprecated
	public boolean saveSpotsMeasures(Spots spotsArray, String binDirectory) {
		return saveMeasures(spotsArray, binDirectory);
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
								EnumSpotMeasures measure = EnumSpotMeasures.findByText(data[1]);
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
		 * Loads spot measures from v2 format file in bin directory. If v2 format is not
		 * found, delegates to Legacy class for fallback handling.
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
							EnumSpotMeasures measure = EnumSpotMeasures.findByText(data[1]);
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

			// Always save to v2.1 format
			Path csvPath = Paths.get(resultsDirectory, ID_V2_SPOTSARRAY_CSV);
			try (FileWriter writer = new FileWriter(csvPath.toFile())) {
				// Write version header
				writer.write("#;version;" + CSV_VERSION + "\n");
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
		 * Saves spot measures (AREA_SUM, AREA_SUMCLEAN sections) to
		 * SpotsArrayMeasures.csv in bin directory. Always saves to v2_ format.
		 */
		public static boolean saveMeasures(Spots spotsArray, String binDirectory) {
			if (binDirectory == null) {
				Logger.warn("SpotsArrayPersistence:save_SpotsArrayMeasures() directory is null");
				return false;
			}

			Path path = Paths.get(binDirectory);
			if (!Files.exists(path)) {
				Logger.warn(
						"SpotsArrayPersistence:save_SpotsArrayMeasures() directory does not exist: " + binDirectory);
				return false;
			}

			// Always save to v2.1 format
			Path csvPath = Paths.get(binDirectory, ID_V2_SPOTSARRAYMEASURES_CSV);
			try (FileWriter writer = new FileWriter(csvPath.toFile())) {
				// Write version header
				writer.write("#;version;" + CSV_VERSION + "\n");
				// Save measures sections
				if (!SpotsPersistenceLegacy.csvSave_MeasuresSection(spotsArray, writer, EnumSpotMeasures.AREA_SUM,
						";")) {
					return false;
				}
				if (!SpotsPersistenceLegacy.csvSave_MeasuresSection(spotsArray, writer, EnumSpotMeasures.AREA_SUMCLEAN,
						";")) {
					return false;
				}
				Logger.info("SpotsArrayPersistence:save_SpotsArrayMeasures() saved measures to "
						+ ID_V2_SPOTSARRAYMEASURES_CSV);
				return true;
			} catch (IOException e) {
				Logger.error("SpotsArrayPersistence:save_SpotsArrayMeasures() Failed: " + e.getMessage(), e, true);
				return false;
			}
		}
	}
}
