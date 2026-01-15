package plugins.fmp.multitools.experiment.spots;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.tools.Logger;

/**
 * Legacy persistence for spots files. Handles loading from legacy CSV formats:
 * SpotsArray.csv, SpotsArrayMeasures.csv, SpotsMeasures.csv
 */
public class SpotsPersistenceLegacy {

	private static final String ID_SPOTSARRAY_CSV = "SpotsArray.csv";
	private static final String ID_SPOTSARRAYMEASURES_CSV = "SpotsArrayMeasures.csv";
	private static final String CSV_FILENAME = "SpotsMeasures.csv";
	private static final String csvSep = ";";

	// ========================================================================
	// Fallback methods that handle all legacy formats (CSV only)
	// These methods replicate the original MultiCAFE0 persistence behavior
	// ========================================================================

	/**
	 * Loads spot descriptions with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files (SpotsArray.csv or SpotsMeasures.csv).
	 * 
	 * @param spotsArray        The SpotsArray to populate
	 * @param resultsDirectory The results directory
	 * @return true if successful
	 */
	public static boolean loadDescriptionWithFallback(Spots spotsArray, String resultsDirectory) {
		if (resultsDirectory == null) {
			return false;
		}

		// Priority 1: Try legacy CSV format (SpotsArray.csv)
		Path csvPath = Paths.get(resultsDirectory, ID_SPOTSARRAY_CSV);
		if (Files.exists(csvPath)) {
			try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
				String line;
				String sep = csvSep;
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
								spotsArray.csvLoadSpotsDescription(reader, sep);
								break;
							case "SPOTS":
								spotsLoaded = true;
								spotsArray.csvLoadSpotsArray(reader, sep);
								break;
							case "AREA_SUM":
							case "AREA_SUMCLEAN":
							case "AREA_FLYPRESENT":
								// Stop reading when we hit measures section
								Logger.info("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
										+ ID_SPOTSARRAY_CSV);
								return descriptionLoaded || spotsLoaded;
							default:
								// Check if it's a measure type
								EnumSpotMeasures measure = EnumSpotMeasures.findByText(data[1]);
								if (measure != null) {
									// Stop reading when we hit measures section
									Logger.info("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
											+ ID_SPOTSARRAY_CSV);
									return descriptionLoaded || spotsLoaded;
								}
								break;
							}
						}
					}
				}
				if (descriptionLoaded || spotsLoaded) {
					Logger.info("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
							+ ID_SPOTSARRAY_CSV);
					return true;
				}
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Error loading CSV: "
						+ e.getMessage(), e, true);
			}
		}

		// Priority 2: Try legacy CSV format (SpotsMeasures.csv) - combined file
		csvPath = Paths.get(resultsDirectory, CSV_FILENAME);
		if (Files.exists(csvPath)) {
			try {
				boolean success = spotsArray.loadSpotsAll(resultsDirectory);
				if (success) {
					Logger.info("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
							+ CSV_FILENAME);
				}
				return success;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Error loading combined CSV: "
						+ e.getMessage(), e, true);
			}
		}

		return false;
	}

	/**
	 * Loads spot measures with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files (SpotsArrayMeasures.csv or SpotsMeasures.csv).
	 * 
	 * @param spotsArray    The SpotsArray to populate
	 * @param binDirectory The bin directory (e.g., results/bin60)
	 * @return true if successful
	 */
	public static boolean loadMeasuresWithFallback(Spots spotsArray, String binDirectory) {
		if (binDirectory == null) {
			return false;
		}

		// Priority 1: Try legacy CSV format (SpotsArrayMeasures.csv)
		Path csvPath = Paths.get(binDirectory, ID_SPOTSARRAYMEASURES_CSV);
		if (Files.exists(csvPath)) {
			try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
				String line;
				String sep = csvSep;

				while ((line = reader.readLine()) != null) {
					if (line.length() > 0 && line.charAt(0) == '#')
						sep = String.valueOf(line.charAt(1));

					String[] data = line.split(sep);
					if (data.length > 0 && data[0].equals("#")) {
						if (data.length > 1) {
							EnumSpotMeasures measure = EnumSpotMeasures.findByText(data[1]);
							if (measure != null) {
								spotsArray.csvLoadSpotsMeasures(reader, measure, sep);
							}
						}
					}
				}
				Logger.info("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
						+ ID_SPOTSARRAYMEASURES_CSV);
				return true;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: "
						+ e.getMessage(), e, true);
			}
		}

		// Priority 2: Try legacy CSV format (SpotsMeasures.csv) in bin directory
		csvPath = Paths.get(binDirectory, CSV_FILENAME);
		if (Files.exists(csvPath)) {
			try {
				boolean success = spotsArray.loadSpotsMeasures(binDirectory);
				if (success) {
					Logger.info("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
							+ CSV_FILENAME);
				}
				return success;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Error loading combined CSV: "
						+ e.getMessage(), e, true);
			}
		}

		// Priority 3: Try legacy CSV format (SpotsMeasures.csv) in results directory
		// Check if binDirectory is actually results directory
		String resultsDir = binDirectory;
		if (binDirectory.contains(File.separator + "bin")) {
			// Extract results directory from bin directory
			int binIndex = binDirectory.lastIndexOf(File.separator + "bin");
			if (binIndex > 0) {
				resultsDir = binDirectory.substring(0, binIndex);
			}
		}
		csvPath = Paths.get(resultsDir, CSV_FILENAME);
		if (Files.exists(csvPath)) {
			try {
				boolean success = spotsArray.loadSpotsMeasures(resultsDir);
				if (success) {
					Logger.info("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
							+ CSV_FILENAME);
				}
				return success;
			} catch (Exception e) {
				Logger.error("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Error loading combined CSV from results: "
						+ e.getMessage(), e, true);
			}
		}

		return false;
	}
}
