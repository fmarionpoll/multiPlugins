package plugins.fmp.multitools.experiment.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentPersistenceLegacy;
import plugins.fmp.multitools.experiment.ExperimentProperties;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.cages.CagesPersistenceLegacy;
import plugins.fmp.multitools.experiment.ids.SpotID;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotPersistence;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

/**
 * Restores editable descriptor fields from legacy XML kept under
 * {@link MigrationTool#DIR_BACKUP_BEFORE_MIGRATION}.
 */
public final class MigrationBackupFieldRestore {

	private static final String MS96_EXPERIMENT = "MS96_experiment.xml";
	private static final String MS96_CAGES = "MS96_cages.xml";
	private static final String ID_CAGES = "Cages";
	private static final String ID_NCAGES = "n_cages";
	private static final String ID_LISTOFSPOTS = "List_of_spots";
	private static final String ID_NSPOTS = "N_spots";
	private static final String ID_SPOT_PREFIX = "spot_";

	private MigrationBackupFieldRestore() {
	}

	public static boolean isMigrationBackupPresent(String resultsDirectory) {
		if (resultsDirectory == null) {
			return false;
		}
		Path backup = Paths.get(resultsDirectory, MigrationTool.DIR_BACKUP_BEFORE_MIGRATION);
		return Files.isRegularFile(backup.resolve(MS96_EXPERIMENT)) && Files.isRegularFile(backup.resolve(MS96_CAGES));
	}

	private static String resolveResultsDirectory(Experiment live) {
		String rd = live.getResultsDirectory();
		if (rd == null && live instanceof LazyExperiment) {
			LazyExperiment le = (LazyExperiment) live;
			if (le.getMetadata() != null) {
				rd = le.getMetadata().getResultsDirectory();
			}
		}
		return rd;
	}

	/**
	 * Overwrites the given field on {@code live} from values read in
	 * {@code resultsDir/backup_before_migration/} (MS96 XML). Matching uses cage
	 * ID for cages; for spots, {@link SpotID} when present in backup XML, otherwise
	 * cageID and cage position.
	 *
	 * @return true if backup files were read and at least one value was applied
	 */
	public static boolean restoreFieldFromMigrationBackup(Experiment live, EnumXLSColumnHeader field) {
		String resultsDir = resolveResultsDirectory(live);
		if (resultsDir == null) {
			Logger.warn("MigrationBackupFieldRestore: no results directory");
			return false;
		}
		Path backupDir = Paths.get(resultsDir, MigrationTool.DIR_BACKUP_BEFORE_MIGRATION);
		Path expXml = backupDir.resolve(MS96_EXPERIMENT);
		Path cagesXml = backupDir.resolve(MS96_CAGES);
		if (!Files.isRegularFile(expXml) || !Files.isRegularFile(cagesXml)) {
			return false;
		}

		switch (field) {
		case EXP_EXPT:
		case EXP_ID:
		case EXP_STIM1:
		case EXP_CONC1:
		case EXP_STRAIN:
		case EXP_SEX:
		case EXP_STIM2:
		case EXP_CONC2:
			return restoreExperimentField(live, field, expXml.toString());
		case CAGE_SEX:
		case CAGE_STRAIN:
		case CAGE_AGE:
			return restoreCageField(live, field, cagesXml.toString());
		case SPOT_STIM:
		case SPOT_CONC:
		case SPOT_VOLUME:
			return restoreSpotField(live, field, cagesXml.toString());
		default:
			return false;
		}
	}

	private static boolean restoreExperimentField(Experiment live, EnumXLSColumnHeader field, String expXmlPath) {
		Experiment shadow = new Experiment();
		if (!ExperimentPersistenceLegacy.xmlLoadExperiment(shadow, expXmlPath)) {
			Logger.warn("MigrationBackupFieldRestore: failed to load " + expXmlPath);
			return false;
		}
		String backupVal = shadow.getExperimentField(field);
		live.loadExperimentDescriptors();
		live.setExperimentFieldNoTest(field, backupVal);
		if (live instanceof LazyExperiment) {
			LazyExperiment le = (LazyExperiment) live;
			ExperimentProperties cached = le.getCachedProperties();
			if (cached != null) {
				cached.setFieldNoTest(field, backupVal);
			}
		}
		if (!live.saveExperimentDescriptors()) {
			Logger.warn("MigrationBackupFieldRestore: saveExperimentDescriptors failed");
			return false;
		}
		DescriptorsIO.buildFromExperiment(live);
		return true;
	}

	private static boolean restoreCageField(Experiment live, EnumXLSColumnHeader field, String cagesXmlPath) {
		Cages backupCages = new Cages();
		if (!CagesPersistenceLegacy.xmlReadCagesFromMS96CagesXml(backupCages, cagesXmlPath)) {
			Logger.warn("MigrationBackupFieldRestore: failed to load cages " + cagesXmlPath);
			return false;
		}
		Map<Integer, String> byCageId = new HashMap<>();
		for (Cage c : backupCages.getCageList()) {
			byCageId.put(c.getCageID(), c.getField(field));
		}
		if (live instanceof LazyExperiment) {
			((LazyExperiment) live).loadIfNeeded();
		}
		live.load_cages_description_and_measures();
		boolean any = false;
		for (Cage c : live.getCages().getCageList()) {
			String v = byCageId.get(c.getCageID());
			if (v != null) {
				c.setField(field, v);
				any = true;
			}
		}
		if (!any) {
			return false;
		}
		if (!live.save_cages_description_and_measures()) {
			Logger.warn("MigrationBackupFieldRestore: save_cages_description_and_measures failed");
			return false;
		}
		DescriptorsIO.buildFromExperiment(live);
		return true;
	}

	private static boolean appendSpotsFromMs96CagesFile(Spots spots, String cagesXmlAbsolutePath) {
		File file = new File(cagesXmlAbsolutePath);
		if (!file.isFile()) {
			return false;
		}
		try {
			Document doc = XMLUtil.loadDocument(file.getAbsolutePath());
			if (doc == null) {
				return false;
			}
			Node rootNode = XMLUtil.getRootElement(doc);
			Element xmlCages = XMLUtil.getElement(rootNode, ID_CAGES);
			if (xmlCages == null) {
				return false;
			}
			int ncages = XMLUtil.getAttributeIntValue(xmlCages, ID_NCAGES, 0);
			int totalLoaded = 0;
			for (int cageIndex = 0; cageIndex < ncages; cageIndex++) {
				Element cageElement = XMLUtil.getElement(xmlCages, "Cage" + cageIndex);
				if (cageElement == null) {
					cageElement = XMLUtil.getElement(xmlCages, "cage" + cageIndex);
				}
				if (cageElement == null) {
					continue;
				}
				Node listNode = XMLUtil.getElement(cageElement, ID_LISTOFSPOTS);
				if (listNode == null) {
					continue;
				}
				int nspots = XMLUtil.getElementIntValue(listNode, ID_NSPOTS, 0);
				for (int i = 0; i < nspots; i++) {
					Node spotNode = XMLUtil.getElement(cageElement, ID_SPOT_PREFIX + i);
					if (spotNode == null) {
						continue;
					}
					Spot spot = new Spot();
					if (!SpotPersistence.xmlLoadSpot(spotNode, spot)) {
						continue;
					}
					if (spot.getSpotUniqueID() == null) {
						int uniqueID = spots.getNextUniqueSpotID();
						spot.setSpotUniqueID(new SpotID(uniqueID));
					}
					spots.addSpot(spot);
					totalLoaded++;
				}
			}
			return totalLoaded > 0;
		} catch (Exception e) {
			Logger.error("MigrationBackupFieldRestore: spots from backup: " + e.getMessage(), e);
			return false;
		}
	}

	private static boolean restoreSpotField(Experiment live, EnumXLSColumnHeader field, String cagesXmlPath) {
		Spots backupSpots = new Spots();
		if (!appendSpotsFromMs96CagesFile(backupSpots, cagesXmlPath)) {
			Logger.warn("MigrationBackupFieldRestore: no spots parsed from " + cagesXmlPath);
			return false;
		}
		Map<Integer, String> bySpotUid = new HashMap<>();
		Map<String, String> byCageAndPosition = new HashMap<>();
		for (Spot s : backupSpots.getSpotList()) {
			String val = s.getField(field);
			if (s.getSpotUniqueID() != null) {
				bySpotUid.put(s.getSpotUniqueID().getId(), val);
			}
			String key = s.getProperties().getCageID() + ":" + s.getProperties().getCagePosition();
			byCageAndPosition.put(key, val);
		}
		if (live instanceof LazyExperiment) {
			((LazyExperiment) live).loadIfNeeded();
		}
		live.load_cages_description_and_measures();
		live.load_spots_description_and_measures();
		boolean any = false;
		for (Cage cage : live.getCages().getCageList()) {
			for (Spot spot : cage.getSpotList(live.getSpots())) {
				String v = null;
				if (spot.getSpotUniqueID() != null) {
					v = bySpotUid.get(spot.getSpotUniqueID().getId());
				}
				if (v == null) {
					v = byCageAndPosition.get(spot.getProperties().getCageID() + ":"
							+ spot.getProperties().getCagePosition());
				}
				if (v != null) {
					spot.setField(field, v);
					any = true;
				}
			}
		}
		if (!any) {
			return false;
		}
		if (!live.save_spots_description_and_measures()) {
			Logger.warn("MigrationBackupFieldRestore: save_spots_description_and_measures failed");
			return false;
		}
		DescriptorsIO.buildFromExperiment(live);
		return true;
	}
}
