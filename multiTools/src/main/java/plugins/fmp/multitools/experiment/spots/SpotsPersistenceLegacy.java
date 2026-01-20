package plugins.fmp.multitools.experiment.spots;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotPersistence;
import plugins.fmp.multitools.tools.Logger;

/**
 * Legacy persistence for spots files. Handles loading from legacy CSV formats:
 * SpotsArray.csv, SpotsArrayMeasures.csv, SpotsMeasures.csv
 */
public class SpotsPersistenceLegacy {

	private static final String ID_SPOTSARRAY_CSV = "SpotsArray.csv";
	private static final String ID_SPOTSARRAYMEASURES_CSV = "SpotsArrayMeasures.csv";
	private static final String csvSep = ";";

	// ========================================================================
	// Fallback methods that handle all legacy formats (CSV only)
	// These methods replicate the original MultiCAFE0 persistence behavior
	// ========================================================================

	/**
	 * Loads spot descriptions with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files (SpotsArray.csv or SpotsMeasures.csv).
	 * 
	 * @param spotsArray       The SpotsArray to populate
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
								csvLoad_SpotsArray_Metadata(spotsArray, reader, sep);
								break;
							case "SPOTS":
								spotsLoaded = true;
								csvLoad_Spots_Description(spotsArray, reader, sep);
								break;
							case "AREA_SUM":
							case "AREA_SUMCLEAN":
							case "AREA_FLYPRESENT":
								// Stop reading when we hit measures section
								Logger.info(
										"SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
												+ ID_SPOTSARRAY_CSV);
								return descriptionLoaded || spotsLoaded;
							default:
								// Check if it's a measure type
								EnumSpotMeasures measure = EnumSpotMeasures.findByText(data[1]);
								if (measure != null) {
									// Stop reading when we hit measures section
									Logger.info(
											"SpotsArrayPersistenceLegacy:loadDescriptionWithFallback() Loaded from legacy CSV: "
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

	// Note: SpotsMeasures.csv is not checked here to avoid infinite recursion
	// multiCAFE data does not have spots persistence files, so we return false
	return false;
	}

	/**
	 * Loads spot measures with fallback logic. Replicates original MultiCAFE0
	 * behavior: checks for legacy CSV files (SpotsArrayMeasures.csv or
	 * SpotsMeasures.csv).
	 * 
	 * @param spotsArray   The SpotsArray to populate
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
								csvLoad_Spots_Measures(spotsArray, reader, measure, sep);
							}
						}
					}
				}
				Logger.info("SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Loaded from legacy CSV: "
						+ ID_SPOTSARRAYMEASURES_CSV);
				return true;
			} catch (Exception e) {
				Logger.error(
						"SpotsArrayPersistenceLegacy:loadMeasuresWithFallback() Error loading CSV: " + e.getMessage(),
						e, true);
			}
		}

	// Note: SpotsMeasures.csv is not checked here to avoid infinite recursion
	// multiCAFE data does not have spots persistence files, so we return false
	return false;
	}

	// ========================================================================
	// CSV Load/Save methods moved from Spots.java
	// ========================================================================

	/**
	 * Loads spot descriptions from CSV reader. Previously csvLoadSpotsArray() in
	 * Spots.java.
	 */
	static String csvLoad_Spots_Description(Spots spots, BufferedReader reader, String csvSeparator)
			throws IOException {
		String line = reader.readLine();
		while ((line = reader.readLine()) != null) {
			String[] data = line.split(csvSeparator);
			if (data[0].equals("#"))
				return data[1];

			Spot spot = spots.findSpotByName(data[0]);
			if (spot == null) {
				spot = new Spot();
				int uniqueID = spots.getNextUniqueSpotID();
				spot.setSpotUniqueID(new SpotID(uniqueID));
				spots.getSpotList().add(spot);
			}
			SpotPersistence.csvImportSpotDescription(spot, data);
		}
		return null;
	}

	/**
	 * Loads spots array metadata from CSV reader. Previously
	 * csvLoadSpotsDescription() in Spots.java.
	 */
	static String csvLoad_SpotsArray_Metadata(Spots spots, BufferedReader reader, String csvSeparator)
			throws IOException {
		String line = reader.readLine();
		String[] data = line.split(csvSeparator);
		String motif = data[0].substring(0, Math.min(data[0].length(), 6));
		if (motif.equals("n spot")) {
			int nspots = Integer.valueOf(data[1]);
			if (nspots < spots.getSpotList().size())
				spots.getSpotList().subList(nspots, spots.getSpotList().size()).clear();
			line = reader.readLine();
			if (line != null)
				data = line.split(csvSeparator);
		}
		if (data[0].equals("#")) {
			return data[1];
		}
		return null;
	}

	/**
	 * Loads spot measures from CSV reader. Previously csvLoadSpotsMeasures() in
	 * Spots.java.
	 */
	static String csvLoad_Spots_Measures(Spots spots, BufferedReader reader, EnumSpotMeasures measureType,
			String csvSeparator) throws IOException {
		String line = reader.readLine();
		boolean y = true;
		boolean x = line.contains("xi");
		while ((line = reader.readLine()) != null) {
			String[] data = line.split(csvSeparator);
			if (data[0].equals("#"))
				return data[1];

			Spot spot = spots.findSpotByName(data[0]);
			if (spot == null) {
				spot = new Spot();
				if (spot.getSpotUniqueID() == null) {
					int uniqueID = spots.getNextUniqueSpotID();
					spot.setSpotUniqueID(new SpotID(uniqueID));
				}
				spots.getSpotList().add(spot);
			}
			SpotPersistence.csvImportSpotData(spot, measureType, data, x, y);
		}
		return null;
	}

	/**
	 * Saves spots array section to CSV writer. Previously
	 * csvSaveSpotsArraySection() in Spots.java.
	 */
	static boolean csvSave_DescriptionSection(Spots spots, FileWriter writer, String csvSeparator) throws IOException {
		writer.write("#" + csvSeparator + "#\n");
		writer.write("#" + csvSeparator + "SPOTS_ARRAY" + csvSeparator + "multiSPOTS data\n");
		writer.write("n spots=" + csvSeparator + spots.getSpotList().size() + "\n");
		writer.write("#" + csvSeparator + "#\n");
		writer.write(SpotPersistence.csvExportSpotSubSectionHeader(csvSeparator));

		for (Spot spot : spots.getSpotList()) {
			if (spot != null && spot.getProperties() != null) {
				String name = spot.getProperties().getName();
				int cageID = spot.getProperties().getCageID();
				if (name != null && !name.trim().isEmpty() && cageID >= 0) {
					writer.write(SpotPersistence.csvExportSpotDescription(spot, csvSeparator));
				}
			}
		}

		return true;
	}

	/**
	 * Saves spot measures section to CSV writer. Previously
	 * csvSaveMeasuresSection() in Spots.java.
	 */
	static boolean csvSave_MeasuresSection(Spots spots, FileWriter writer, EnumSpotMeasures measureType,
			String csvSeparator) throws IOException {
		writer.write(SpotPersistence.csvExportMeasureSectionHeader(measureType, csvSeparator));

		for (Spot spot : spots.getSpotList()) {
			writer.write(SpotPersistence.csvExportMeasuresOneType(spot, measureType, csvSeparator));
		}

		return true;
	}
}
