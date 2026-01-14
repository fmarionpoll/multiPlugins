package plugins.fmp.multitools.fmp_experiment.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.fmp_tools.Logger;

/**
 * Detects if an experiment uses the old format (spots nested in cage XML) or
 * the new format (spots in separate CSV file, IDs in cage XML).
 * 
 * @deprecated Migration is no longer needed. Legacy persistence classes now provide
 * transparent fallback to read old formats automatically. Users can manually save
 * in new format when desired, or use an auto-save flag on experiment close.
 * This class is kept for reference but is no longer used.
 */
@Deprecated
public class MigrationDetector {

	private static final String ID_LISTOFSPOTS = "List_of_spots";

	// New format filenames
	private static final String ID_CAGES_CSV = "Cages.csv";
	private static final String ID_SPOTS_CSV = "Spots.csv";
	private static final String ID_CAPILLARIES_CSV = "Capillaries.csv";

	// Legacy filenames
	private static final String ID_SPOTSMEASURES_CSV = "SpotsMeasures.csv";
	private static final String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";

	/**
	 * Detects if the experiment directory uses the old format. Old format: spots
	 * are nested in cage XML files, combined CSV files. New format: separate CSV
	 * files for descriptions (Cages.csv, SpotsArray.csv, etc.).
	 * 
	 * @param directory the experiment results directory
	 * @return true if old format detected, false if new format or cannot determine
	 */
	public boolean detectOldFormat(String directory) {
		if (directory == null) {
			return false;
		}

		Path dirPath = Paths.get(directory);
		if (!Files.exists(dirPath)) {
			return false;
		}

		// Check if new format exists (new CSV files for descriptions)
		Path cagesCsv = dirPath.resolve(ID_CAGES_CSV);
		Path spotsArrayCsv = dirPath.resolve(ID_SPOTS_CSV);
		Path capillariesArrayCsv = dirPath.resolve(ID_CAPILLARIES_CSV);

		boolean newFormatExists = Files.exists(cagesCsv) || Files.exists(spotsArrayCsv)
				|| Files.exists(capillariesArrayCsv);

		// If new format exists, assume it's already migrated
		if (newFormatExists) {
			return false;
		}

		// Check if old format exists (spots in cage XML or legacy CSV files)
		Path cagesXmlPath = dirPath.resolve(ID_MCDROSOTRACK_XML);
		boolean oldFormatExists = false;
		if (Files.exists(cagesXmlPath)) {
			oldFormatExists = hasSpotsInCageXML(cagesXmlPath.toString());
		}

		// Also check for legacy CSV files
		if (!oldFormatExists) {
			Path legacySpotsCsv = dirPath.resolve(ID_SPOTSMEASURES_CSV);
			if (Files.exists(legacySpotsCsv)) {
				oldFormatExists = true;
			}
		}

		// If old format exists, needs migration
		return oldFormatExists;
	}

	/**
	 * Checks if migration is needed.
	 * 
	 * @param directory the experiment results directory
	 * @return true if migration is needed
	 */
	public boolean needsMigration(String directory) {
		return detectOldFormat(directory);
	}

	/**
	 * Checks if the cage XML file contains nested spots.
	 * 
	 * @param xmlFilePath path to the cage XML file
	 * @return true if spots are found in the XML
	 */
	private boolean hasSpotsInCageXML(String xmlFilePath) {
		try {
			Document doc = XMLUtil.loadDocument(xmlFilePath);
			if (doc == null) {
				return false;
			}

			Node rootNode = XMLUtil.getRootElement(doc);
			Element cagesElement = XMLUtil.getElement(rootNode, "Cages");
			if (cagesElement == null) {
				return false;
			}

			// Check first few cages for nested spots
			int maxCagesToCheck = 5;
			for (int i = 0; i < maxCagesToCheck; i++) {
				Element cageElement = XMLUtil.getElement(cagesElement, "Cage" + i);
				if (cageElement == null) {
					continue;
				}

				// Check if this cage has nested spots
				Node spotsNode = XMLUtil.getElement(cageElement, ID_LISTOFSPOTS);
				if (spotsNode != null) {
					// Found nested spots - this is old format
					return true;
				}
			}

			return false;
		} catch (Exception e) {
			Logger.warn("MigrationDetector:hasSpotsInCageXML() Error checking XML: " + e.getMessage());
			return false;
		}
	}
}
