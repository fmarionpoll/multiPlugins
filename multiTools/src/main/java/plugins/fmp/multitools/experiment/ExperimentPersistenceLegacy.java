package plugins.fmp.multitools.experiment;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.Logger;

/**
 * Legacy persistence for experiment files.
 * Handles loading from legacy XML formats: MCexperiment.xml, MS96_experiment.xml
 */
public class ExperimentPersistenceLegacy {

	private final static String ID_VERSION = "version";
	private final static String ID_VERSIONNUM = "1.0.0";
	private final static String ID_TIMEFIRSTIMAGE = "fileTimeImageFirstMinute";
	private final static String ID_TIMELASTIMAGE = "fileTimeImageLastMinute";

	private final static String ID_BINT0 = "indexBinT0";
	private final static String ID_TIMEFIRSTIMAGEMS = "fileTimeImageFirstMs";
	private final static String ID_TIMELASTIMAGEMS = "fileTimeImageLastMs";
	private final static String ID_FIRSTKYMOCOLMS = "firstKymoColMs";
	private final static String ID_LASTKYMOCOLMS = "lastKymoColMs";
	private final static String ID_BINKYMOCOLMS = "binKymoColMs";
	private final static String ID_FRAMEFIRST = "indexFrameFirst";
	private final static String ID_NFRAMES = "nFrames";
	private final static String ID_FRAMEDELTA = "indexFrameDelta";

//	private final static String ID_IMAGESDIRECTORY = "imagesDirectory";
	private final static String ID_MCEXPERIMENT = "MCexperiment";
	
	// Legacy filenames
	public final static String ID_MCEXPERIMENT_XML = "MCexperiment.xml";
	public final static String ID_MS96_EXPERIMENT_XML_LEGACY = "MS96_experiment.xml";

	/**
	 * Loads experiment from legacy XML file format.
	 * 
	 * @param exp        The experiment to populate
	 * @param csFileName The path to the XML file
	 * @return true if successful
	 */
	public static boolean xmlLoadExperiment(Experiment exp, String csFileName) {
		if (csFileName == null) {
			Logger.warn("ExperimentPersistenceLegacy:xmlLoadExperiment() File path is null");
			return false;
		}

		try {
			final Document doc = XMLUtil.loadDocument(csFileName);
			if (doc == null) {
				Logger.warn("ExperimentPersistenceLegacy:xmlLoadExperiment() Could not load XML document from " + csFileName);
				return false;
			}

			Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_MCEXPERIMENT);
			if (node == null) {
				Logger.warn("ExperimentPersistenceLegacy:xmlLoadExperiment() Could not find MCexperiment element in XML");
				return false;
			}

			String version = XMLUtil.getElementValue(node, ID_VERSION, ID_VERSIONNUM);
			if (!version.equals(ID_VERSIONNUM)) {
				Logger.warn("ExperimentPersistenceLegacy:xmlLoadExperiment() Version mismatch: expected " + ID_VERSIONNUM + ", got " + version);
				return false;
			}

			// Load timing information
			exp.setCamImageFirst_ms(XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGEMS, -1));
			exp.setCamImageLast_ms(XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGEMS, -1));
			if (exp.getCamImageLast_ms() < 0) {
				// Fallback to minute-based timing (legacy format)
				exp.setCamImageFirst_ms(XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGE, 0) * 60000);
				exp.setCamImageLast_ms(XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGE, 0) * 60000);
			}

			exp.setBinT0(XMLUtil.getElementLongValue(node, ID_BINT0, 0));

			// Load bin parameters (legacy format - these are now migrated to bin directories)
			long firstKymoColMs = XMLUtil.getElementLongValue(node, ID_FIRSTKYMOCOLMS, -1);
			long lastKymoColMs = XMLUtil.getElementLongValue(node, ID_LASTKYMOCOLMS, -1);
			long binKymoColMs = XMLUtil.getElementLongValue(node, ID_BINKYMOCOLMS, -1);

			// Only migrate if binKymoColMs was explicitly found in XML (>= 0)
			// If binKymoColMs is missing, skip migration entirely - let it be calculated from files later
			// This prevents creating bin_60 with default value before interval is calculated
			if (binKymoColMs >= 0) {
				// Determine target bin directory (use current binDirectory or default to bin_60)
				String targetBinDir = exp.getBinSubDirectory();
				if (targetBinDir != null) {
					// Extract just the subdirectory name if binDirectory is a full path
					File binDirFile = new File(targetBinDir);
					if (binDirFile.isAbsolute()) {
						targetBinDir = binDirFile.getName();
					}
				} else {
					// No bin directory set, default based on duration
					targetBinDir = Experiment.BIN + (binKymoColMs / 1000);
				}

				// Create BinDescription and save to bin directory
				BinDescription binDesc = new BinDescription();
				if (firstKymoColMs >= 0)
					binDesc.setFirstKymoColMs(firstKymoColMs);
				if (lastKymoColMs >= 0)
					binDesc.setLastKymoColMs(lastKymoColMs);
				binDesc.setBinKymoColMs(binKymoColMs);
				binDesc.setBinDirectory(targetBinDir);

				// Save to bin directory
				String resultsDir = exp.getResultsDirectory();
				if (resultsDir != null) {
					String binFullDir = resultsDir + File.separator + targetBinDir;
					BinDescriptionPersistence binPersistence = new BinDescriptionPersistence();
					binPersistence.save(binDesc, binFullDir);

					// Load into active bin description
					exp.loadBinDescription(targetBinDir);
				}
			} else {
				// No bin parameters in XML, try to load from current bin directory
				// Only load if interval hasn't been calculated yet (need saved values)
				// If interval was calculated, skip loading to avoid overwriting calculated values
				if (exp.getCamImageBin_ms() < 0) {
					String currentBinDir = exp.getBinSubDirectory();
					if (currentBinDir != null) {
						File binDirFile = new File(currentBinDir);
						String binSubDir = binDirFile.isAbsolute() ? binDirFile.getName() : currentBinDir;
						exp.loadBinDescription(binSubDir);
					}
				}
			}

			// Load ImageLoader configuration
			if (exp.getSeqCamData() != null) {
				ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
				long frameFirst = XMLUtil.getElementLongValue(node, ID_FRAMEFIRST, 0);
				if (frameFirst < 0)
					frameFirst = 0;
				imgLoader.setAbsoluteIndexFirstImage(frameFirst);

				long nImages = XMLUtil.getElementLongValue(node, ID_NFRAMES, -1);
				if (nImages > 0) {
					imgLoader.setFixedNumberOfImages(nImages);
					imgLoader.setNTotalFrames((int) (nImages - frameFirst));
				} else {
					int loadedImagesCount = imgLoader.getImagesCount();
					if (loadedImagesCount > 0) {
						nImages = loadedImagesCount + frameFirst;
						imgLoader.setFixedNumberOfImages(nImages);
						imgLoader.setNTotalFrames((int) (nImages - frameFirst));
					}
				}

				// Load TimeManager configuration
				TimeManager timeManager = exp.getSeqCamData().getTimeManager();
				long firstMs = XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGEMS, -1);
				if (firstMs >= 0)
					timeManager.setFirstImageMs(firstMs);
				long lastMs = XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGEMS, -1);
				if (lastMs >= 0)
					timeManager.setLastImageMs(lastMs);
				if (firstMs >= 0 && lastMs >= 0) {
					long durationMs = lastMs - firstMs;
					timeManager.setDurationMs(durationMs);
				}
				long frameDelta = XMLUtil.getElementLongValue(node, ID_FRAMEDELTA, 1);
				timeManager.setDeltaImage(frameDelta);
			}

			// Try to compute from sequenceCamData if still uninitialized (-1)
			if (exp.getCamImageFirst_ms() < 0 || exp.getCamImageLast_ms() < 0) {
				if (exp.getSeqCamData() != null) {
					exp.getFileIntervalsFromSeqCamData();
				}
			}

			// Load properties with error handling
			try {
				exp.getProperties().loadXML_Properties(node);
			} catch (Exception e) {
				Logger.warn("ExperimentPersistenceLegacy:xmlLoadExperiment() - Failed to load experiment properties: " + e.getMessage());
			}

			Logger.info("ExperimentPersistenceLegacy:xmlLoadExperiment() Successfully loaded experiment from " + csFileName);
			return true;

		} catch (Exception e) {
			Logger.error("ExperimentPersistenceLegacy:xmlLoadExperiment() Error loading from " + csFileName + ": " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Tries to load experiment from legacy MCexperiment.xml file.
	 * 
	 * @param exp The experiment to populate
	 * @return true if successful
	 */
	public static boolean loadMCExperiment(Experiment exp) {
		String filename = concatenateExptDirectoryWithSubpathAndName(exp, null, ID_MCEXPERIMENT_XML);
		boolean found = xmlLoadExperiment(exp, filename);
		if (!found && exp.getSeqCamData() != null) {
			// Try to load from the images directory (legacy behavior)
			String imagesDirectory = exp.getSeqCamData().getImagesDirectory();
			if (imagesDirectory != null) {
				filename = imagesDirectory + File.separator + ID_MCEXPERIMENT_XML;
				found = xmlLoadExperiment(exp, filename);
			}
		}
		return found;
	}

	/**
	 * Tries to load experiment from legacy MS96_experiment.xml file.
	 * 
	 * @param exp The experiment to populate
	 * @return true if successful
	 */
	public static boolean loadMS96Experiment(Experiment exp) {
		String filename = concatenateExptDirectoryWithSubpathAndName(exp, null, ID_MS96_EXPERIMENT_XML_LEGACY);
		return xmlLoadExperiment(exp, filename);
	}

	private static String concatenateExptDirectoryWithSubpathAndName(Experiment exp, String subpath, String name) {
		String strExperimentDirectory = exp.getExperimentDirectory();
		if (subpath != null)
			return strExperimentDirectory + File.separator + subpath + File.separator + name;
		else
			return strExperimentDirectory + File.separator + name;
	}
}
