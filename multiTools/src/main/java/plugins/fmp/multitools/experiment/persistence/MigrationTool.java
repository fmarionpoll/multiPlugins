package plugins.fmp.multitools.experiment.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.Logger;

/**
 * Migrates experiments from old format (spots in cage XML) to new format (spots
 * in CSV, IDs in cage XML).
 * 
 */

public class MigrationTool {

	private MigrationDetector detector = new MigrationDetector();

	/**
	 * Migrates an experiment from old format to new format.
	 * 
	 * @param exp       the Experiment to migrate
	 * @param directory the experiment results directory
	 * @return true if migration was successful
	 */
	public boolean migrateExperiment(Experiment exp, String directory) {
		if (exp == null || directory == null) {
			Logger.error("MigrationTool:migrateExperiment() Invalid parameters", null);
			return false;
		}

		if (!detector.needsMigration(directory)) {
			Logger.info("MigrationTool:migrateExperiment() No migration needed - already in new format");
			return true;
		}

		Logger.info("MigrationTool:migrateExperiment() Starting migration for: " + directory);

		try {
			// Step 1: Backup old files
			backupOldFiles(directory);

			// Step 2: Load old format (spots from cage XML)
			// This happens when loading cages - spots are loaded from nested XML
			// We need to extract them before saving in new format

			// Step 3: Extract spots from all cages to global SpotsArray
			extractSpotsFromCages(exp);

			// Step 4: Convert cage's spotsArray to SpotID lists
			convertCageSpotsToIDs(exp);

			// Step 5: Convert cage's capillaries to CapillaryID lists
			convertCageCapillariesToIDs(exp);

			// Step 6: Save in new format (plugin-specific: only 2 entity types)
			// multiCAFE: cages + capillaries. multiSPOTS96: cages + spots.
			String programContext = Experiment.getProgramContext();
			boolean isMultiCAFE = "multiCAFE".equals(programContext);

			// Save cages descriptions (always)
			boolean cagesDescriptionsSaved = exp.getCages().getPersistence().saveDescriptions(exp.getCages(),
					directory);
			if (!cagesDescriptionsSaved) {
				Logger.warn("MigrationTool:migrateExperiment() Failed to save cage descriptions");
			}

			boolean secondDescriptionsSaved;
			if (isMultiCAFE) {
				secondDescriptionsSaved = exp.getCapillaries().getPersistence()
						.saveDescriptions(exp.getCapillaries(), directory);
				if (!secondDescriptionsSaved) {
					Logger.warn("MigrationTool:migrateExperiment() Failed to save capillary descriptions");
				}
			} else {
				secondDescriptionsSaved = exp.getSpots().getPersistence().saveDescriptions(exp.getSpots(), directory);
				if (!secondDescriptionsSaved) {
					Logger.warn("MigrationTool:migrateExperiment() Failed to save spot descriptions to CSV");
				}
			}

			// Save measures to bin directory (if available)
			String binDir = exp.getKymosBinFullDirectory();
			if (binDir != null) {
				exp.getCages().getPersistence().saveMeasures(exp.getCages(), binDir);
				if (isMultiCAFE) {
					exp.getCapillaries().getPersistence().saveMeasures(exp.getCapillaries(), binDir);
				} else {
					exp.getSpots().getPersistence().saveMeasures(exp.getSpots(), binDir);
				}
			}

			Logger.info("MigrationTool:migrateExperiment() Migration completed successfully");
			return cagesDescriptionsSaved && secondDescriptionsSaved;

		} catch (Exception e) {
			Logger.error("MigrationTool:migrateExperiment() Migration failed: " + e.getMessage(), e, true);
			return false;
		}
	}

	/**
	 * Backs up old files before migration.
	 */
	private void backupOldFiles(String directory) {
		try {
			Path dirPath = Paths.get(directory);
			Path backupDir = dirPath.resolve("backup_before_migration");
			if (!Files.exists(backupDir)) {
				Files.createDirectories(backupDir);
			}

			// Legacy experiment / cages XML files to move out of the root results directory
			String[] legacyXmlFiles = new String[] { "MCdrosotrack.xml", "MCexperiment.xml", "MS96_experiment.xml",
					"MS96_cages.xml" };

			for (String name : legacyXmlFiles) {
				Path src = dirPath.resolve(name);
				if (Files.exists(src)) {
					Path dst = backupDir.resolve(name);
					Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
					Logger.info("MigrationTool:backupOldFiles() Moved legacy file to backup: " + dst);
				}
			}

			// Legacy combined CSVs (if present) can also be moved to keep results/ clean
			String[] legacyCsvFiles = new String[] { "CagesMeasures.csv", "SpotsMeasures.csv" };
			for (String name : legacyCsvFiles) {
				Path src = dirPath.resolve(name);
				if (Files.exists(src)) {
					Path dst = backupDir.resolve(name);
					Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
					Logger.info("MigrationTool:backupOldFiles() Moved legacy CSV to backup: " + dst);
				}
			}

		} catch (Exception e) {
			Logger.warn("MigrationTool:backupOldFiles() Failed to move legacy files: " + e.getMessage());
		}
	}

	/**
	 * Extracts all spots from cages and adds them to the global SpotsArray.
	 */
	private void extractSpotsFromCages(Experiment exp) {
		Spots globalSpots = exp.getSpots();
		// Spots should already be in global array if loaded from XML
		// This method is kept for compatibility but spots are now loaded directly to
		// global array
		// Ensure coordinates are saved for all spots
		for (Spot spot : globalSpots.getSpotList()) {
			if (spot.getRoi() != null
					&& (spot.getProperties().getSpotXCoord() < 0 || spot.getProperties().getSpotYCoord() < 0)) {
				// Extract coordinates from ROI
				java.awt.geom.Rectangle2D bounds = spot.getRoi().getBounds2D();
				spot.getProperties().setSpotXCoord((int) bounds.getCenterX());
				spot.getProperties().setSpotYCoord((int) bounds.getCenterY());
				if (spot.getProperties().getSpotRadius() <= 0) {
					spot.getProperties().setSpotRadius((int) Math.max(bounds.getWidth(), bounds.getHeight()) / 2);
				}
			}
		}

		Logger.info("MigrationTool:extractSpotsFromCages() Processed " + globalSpots.getSpotListCount() + " spots");
	}

	/**
	 * Converts global spots to SpotID lists in cages.
	 */
	private void convertCageSpotsToIDs(Experiment exp) {
		Spots globalSpots = exp.getSpots();
		for (Cage cage : exp.getCages().getCageList()) {
			List<SpotID> spotIDs = new ArrayList<>();
			// Find all spots belonging to this cage and collect their unique IDs
			for (Spot spot : globalSpots.getSpotList()) {
				int cageID = spot.getProperties().getCageID();
				if (cageID == cage.getCageID() && spot.getSpotUniqueID() != null)
					spotIDs.add(spot.getSpotUniqueID());
			}
			cage.setSpotIDs(spotIDs);
		}
		Logger.info("MigrationTool:convertCageSpotsToIDs() Converted spots to IDs for all cages");
	}

	/**
	 * Converts global capillaries to CapillaryID lists in cages.
	 */
	private void convertCageCapillariesToIDs(Experiment exp) {
		Capillaries globalCapillaries = exp.getCapillaries();
		for (Cage cage : exp.getCages().getCageList()) {
			List<CapillaryID> capillaryIDs = new ArrayList<>();
			// Find all capillaries belonging to this cage
			for (Capillary cap : globalCapillaries.getList()) {
				if (cap.getCageID() == cage.getCageID()) {
					capillaryIDs.add(new CapillaryID(cap.getKymographIndex()));
				}
			}
			cage.setCapillaryIDs(capillaryIDs);
		}
		Logger.info("MigrationTool:convertCageCapillariesToIDs() Converted capillaries to IDs for all cages");
	}
}
