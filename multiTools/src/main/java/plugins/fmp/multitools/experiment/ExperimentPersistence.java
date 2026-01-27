package plugins.fmp.multitools.experiment;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.tools.Logger;

public class ExperimentPersistence {

	private final static String ID_VERSION = "version";
	private final static String ID_VERSIONNUM = "2.0.0";
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

	private final static String ID_IMAGESDIRECTORY = "imagesDirectory";
	private final static String ID_MCEXPERIMENT = "MCexperiment";
	// Current format filename (version stored internally in XML)
	public final static String ID_V2_EXPERIMENT_XML = "Experiment.xml";
	// Legacy filenames (for fallback)
	public final static String ID_MCEXPERIMENT_XML = "MCexperiment.xml";
//	private final static String ID_MS96_EXPERIMENT_XML_LEGACY = "MS96_experiment.xml";

	private final static String ID_GENERATOR_PROGRAM = "generatorProgram";

	// ========================================================================
	// Public API methods (delegate to nested classes)
	// ========================================================================

	public boolean loadExperimentDescriptors(Experiment exp) {
		return Persistence.load(exp);
	}

	public boolean saveExperimentDescriptors(Experiment exp) {
		return Persistence.save(exp);
	}

	// ========================================================================
	// Nested class for current v2 format persistence
	// ========================================================================

	public static class Persistence {

		/**
		 * Loads experiment from XML file. Tries v2_ format first, then falls back to
		 * legacy formats.
		 */
		public static boolean load(Experiment exp) {
			// Priority 1: Try new v2_ format
			String filename = concatenateExptDirectoryWithSubpathAndName(exp, null, ID_V2_EXPERIMENT_XML);
			boolean found = xmlLoadExperiment(exp, filename);

			// Priority 2: Try legacy MCexperiment.xml using legacy persistence
			if (!found) {
				found = ExperimentPersistenceLegacy.loadMCExperiment(exp);
			}

			// Priority 3: Try legacy MS96_experiment.xml using legacy persistence
			if (!found) {
				found = ExperimentPersistenceLegacy.loadMS96Experiment(exp);
			}

			return found;
		}

		/**
		 * Saves experiment to XML file. Always saves to v2_ format.
		 */
		public static boolean save(Experiment exp) {
			// Always save to v2_ format
			String filename = concatenateExptDirectoryWithSubpathAndName(exp, null, ID_V2_EXPERIMENT_XML);
			return xmlSaveExperiment(exp, filename);
		}

		// ------------------------------------------

		/**
		 * Loads experiment from v2_ format XML file. Legacy formats are handled by
		 * ExperimentPersistenceLegacy.
		 */
		private static boolean xmlLoadExperiment(Experiment exp, String csFileName) {
			final Document doc = XMLUtil.loadDocument(csFileName);
			if (doc == null)
				return false;
			Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_MCEXPERIMENT);
			if (node == null)
				return false;

			String version = XMLUtil.getElementValue(node, ID_VERSION, ID_VERSIONNUM);
			if (!version.equals(ID_VERSIONNUM))
				return false;

			exp.setCamImageFirst_ms(XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGEMS, -1));
			exp.setCamImageLast_ms(XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGEMS, -1));
			if (exp.getCamImageLast_ms() < 0) {
			exp.setCamImageFirst_ms(XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGE, 0) * 60000);
			exp.setCamImageLast_ms(XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGE, 0) * 60000);
		}

		// Migration: Extract bin parameters from old XML format and migrate to bin
			// directory
			long firstKymoColMs = XMLUtil.getElementLongValue(node, ID_FIRSTKYMOCOLMS, -1);
			long lastKymoColMs = XMLUtil.getElementLongValue(node, ID_LASTKYMOCOLMS, -1);
			long binKymoColMs = XMLUtil.getElementLongValue(node, ID_BINKYMOCOLMS, -1);

			// Only migrate if binKymoColMs was explicitly found in XML (>= 0)
			// If binKymoColMs is missing, skip migration entirely - let it be calculated from files later
			// This prevents creating bin_60 with default value before interval is calculated
			if (binKymoColMs >= 0) {
				// Determine target bin directory (use current binDirectory or default to
				// bin_60)
				String targetBinDir = exp.getBinSubDirectory();
				if (targetBinDir != null) {
					// Extract just the subdirectory name if binDirectory is a full path
					File binDirFile = new File(targetBinDir);
					if (binDirFile.isAbsolute()) {
						// Extract just the last component of the path (e.g., "bin_60")
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

					// Load into active bin description (using subdirectory name, not full path)
					exp.loadBinDescription(targetBinDir);
				}
			} else {
				// No bin parameters in XML, try to load from current bin directory
				// Only load if interval hasn't been calculated yet (need saved values)
				// If interval was calculated, skip loading to avoid overwriting calculated values
				if (exp.getCamImageBin_ms() < 0) {
					String currentBinDir = exp.getBinSubDirectory();
					if (currentBinDir != null) {
						// Extract subdirectory name if binDirectory is a full path
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
				if (frameFirst < 0) {
					// Fallback: try to read from legacy binT0 parameter
					frameFirst = XMLUtil.getElementLongValue(node, ID_BINT0, 0);
				}
				if (frameFirst < 0)
					frameFirst = 0;
				imgLoader.setAbsoluteIndexFirstImage(frameFirst);

				long nImages = XMLUtil.getElementLongValue(node, ID_NFRAMES, -1);
				if (nImages > 0) {
					imgLoader.setFixedNumberOfImages(nImages);
					imgLoader.setNTotalFrames((int) (nImages - frameFirst));
					// getNTotalFrames() will auto-correct if value is invalid (-1, 0, or 1)
					imgLoader.getNTotalFrames();
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
				// Bin parameters are now stored in bin directories, not in experiment XML
				// TimeManager bin parameters will be set from activeBinDescription when bin is
				// loaded
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
				Logger.warn("ExperimentPersistence.xmlLoadExperiment() - Failed to load experiment properties: "
						+ e.getMessage());
			}
			String generatorProgram = XMLUtil.getElementValue(node, ID_GENERATOR_PROGRAM, null);
			if (generatorProgram != null) {
				exp.setGeneratorProgram(generatorProgram);
			}
			return true;
		}

		private static boolean xmlSaveExperiment(Experiment exp, String csFileName) {
			final Document doc = XMLUtil.createDocument(true);
			if (doc != null) {
				Node xmlRoot = XMLUtil.getRootElement(doc, true);
				Node node = XMLUtil.setElement(xmlRoot, ID_MCEXPERIMENT);
				if (node == null)
					return false;

				XMLUtil.setElementValue(node, ID_VERSION, ID_VERSIONNUM);

				// Check if values are uninitialized (-1) and attempt to compute from
				// sequenceCamData
				long firstMs = exp.getCamImageFirst_ms();
				long lastMs = exp.getCamImageLast_ms();

				if (firstMs < 0 || lastMs <= 0) {
					// Attempt to compute from sequenceCamData
					if (exp.getSeqCamData() != null) {
						firstMs = exp.getSeqCamData().getFirstImageMs();
						lastMs = exp.getSeqCamData().getLastImageMs();
						if (firstMs < 0 || lastMs < 0) {
							exp.getFileIntervalsFromSeqCamData();
							firstMs = exp.getCamImageFirst_ms();
							lastMs = exp.getCamImageLast_ms();
						}
					}

					// If still uninitialized after attempting computation, log a warning
					if (firstMs < 0 || lastMs < 0) {
						Logger.warn(
								"ExperimentPersistence.xmlSaveExperiment() - Could not compute fileTimeImageFirstMs/LastMs from sequenceCamData. Saving as -1.");
					} else {
						exp.setCamImageFirst_ms(firstMs);
						exp.setCamImageLast_ms(lastMs);
					}
				}

				XMLUtil.setElementLongValue(node, ID_TIMEFIRSTIMAGEMS, firstMs);
				XMLUtil.setElementLongValue(node, ID_TIMELASTIMAGEMS, lastMs);

			// Bin parameters (firstKymoColMs, lastKymoColMs, binKymoColMs) are now stored
			// in BinDescription.xml files in each bin directory, not in
			// Experiment.xml

				// Save ImageLoader configuration
				if (exp.getSeqCamData() != null) {
					ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
					long frameFirst = imgLoader.getAbsoluteIndexFirstImage();
					long nImages = imgLoader.getFixedNumberOfImages();
					XMLUtil.setElementLongValue(node, ID_FRAMEFIRST, frameFirst);
					
					// Only save nFrames if it's a valid positive value
					// -1 or 0 means "undetermined" - will be calculated from actual images on next load
					if (nImages > 0) {
						XMLUtil.setElementLongValue(node, ID_NFRAMES, nImages);
					} else {
						// Remove nFrames element if it exists (to indicate undetermined)
						// This allows the next load to determine it from actual image count
						org.w3c.dom.Node nFramesNode = XMLUtil.getElement(node, ID_NFRAMES);
						if (nFramesNode != null) {
							node.removeChild(nFramesNode);
						}
					}

					// Save TimeManager configuration
					TimeManager timeManager = exp.getSeqCamData().getTimeManager();
					XMLUtil.setElementLongValue(node, ID_FRAMEDELTA, timeManager.getDeltaImage());
				}

				if (exp.getImagesDirectory() != null)
					XMLUtil.setElementValue(node, ID_IMAGESDIRECTORY, exp.getImagesDirectory());

				// Save properties using ExperimentProperties.saveXML_Properties()
				try {
					exp.getProperties().saveXML_Properties(node);
				} catch (Exception e) {
					Logger.warn("ExperimentPersistence.xmlSaveExperiment() - Failed to save experiment properties: "
							+ e.getMessage());
				}

				// Save generator program (optional field)
				// Auto-determine if not already set - Experiment class handles detection
				// automatically
				String programToSave = exp.getGeneratorProgram();
				if (programToSave == null) {
					programToSave = Experiment.determineProgramFromStackTraceStatic();
					if (programToSave != null) {
						exp.setGeneratorProgram(programToSave);
					}
				}
				if (programToSave != null) {
					XMLUtil.setElementValue(node, ID_GENERATOR_PROGRAM, programToSave);
				}

				XMLUtil.saveDocument(doc, csFileName);
				return true;
			}
			return false;
		}

		private static String concatenateExptDirectoryWithSubpathAndName(Experiment exp, String subpath, String name) {
			String strExperimentDirectory = exp.getExperimentDirectory();
			if (subpath != null)
				return strExperimentDirectory + File.separator + subpath + File.separator + name;
			else
				return strExperimentDirectory + File.separator + name;
		}
	}
}
