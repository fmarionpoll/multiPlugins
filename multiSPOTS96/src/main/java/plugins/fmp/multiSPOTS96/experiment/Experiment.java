package plugins.fmp.multiSPOTS96.experiment;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.util.XMLUtil;
import plugins.fmp.multitools.experiment.cages.Cage;
import plugins.fmp.multitools.experiment.cages.CagesArray;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.TimeManager;
import plugins.fmp.multitools.experiment.spots.Spot;
import plugins.fmp.multitools.tools.Directories;
import plugins.fmp.multitools.tools.toExcel.EnumXLSColumnHeader;

public class Experiment {
	public final static String RESULTS = "results";
	public final static String BIN = "bin_";

	private String camDataImagesDirectory = null;
	private String resultsDirectory = null;
	private String binDirectory = null;

	public SequenceCamData seqCamData = null;
//	public SequenceKymos seqKymos = null;
	public Sequence seqReference = null;
	public CagesArray getCages() = new CagesArray();

	public FileTime firstImage_FileTime;
	public FileTime lastImage_FileTime;

	private ExperimentProperties prop = new ExperimentProperties();
	public int col = -1;
	public Experiment chainToPreviousExperiment = null;
	public Experiment chainToNextExperiment = null;
	public long chainImageFirst_ms = 0;
	public int experimentID = 0;

	private final static String ID_VERSION = "version";
	private final static String ID_VERSIONNUM = "1.0.0";
	private final static String ID_FRAMEFIRST = "indexFrameFirst";
	private final static String ID_NFRAMES = "nFrames";
	private final static String ID_FRAMEDELTA = "indexFrameDelta";

	private final static String ID_TIMEFIRSTIMAGEMS = "fileTimeImageFirstMs";
	private final static String ID_TIMELASTIMAGEMS = "fileTimeImageLastMs";
	private final static String ID_FIRSTKYMOCOLMS = "firstKymoColMs";
	private final static String ID_LASTKYMOCOLMS = "lastKymoColMs";
	private final static String ID_BINKYMOCOLMS = "binKymoColMs";

	private final static String ID_IMAGESDIRECTORY = "imagesDirectory";
	private final static String ID_MCEXPERIMENT = "MCexperiment";
	private final String ID_MS96_experiment_XML = "MS96_experiment.xml";
	private final static String ID_MCDROSOTRACK_XML = "MCdrosotrack.xml";

	private final static int EXPT_DIRECTORY = 1;
	private final static int IMG_DIRECTORY = 2;
	private final static int BIN_DIRECTORY = 3;
	// ----------------------------------

	public Experiment() {
		seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
	}

	public Experiment(String expDirectory) {
		seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
		this.resultsDirectory = expDirectory;
	}

	public Experiment(SequenceCamData seqCamData) {
		this.seqCamData = seqCamData;
		resultsDirectory = this.seqCamData.getImagesDirectory() + File.separator + RESULTS;
		getFileIntervalsFromSeqCamData();
		load_MS96_experiment(concatenateExptDirectoryWithSubpathAndName(null, ID_MS96_experiment_XML));
	}

	public Experiment(ExperimentDirectories eADF) {
		camDataImagesDirectory = eADF.getCameraImagesDirectory();
		resultsDirectory = eADF.getResultsDirectory();
		seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
		String fileName = concatenateExptDirectoryWithSubpathAndName(null, ID_MS96_experiment_XML);
		load_MS96_experiment(fileName);

		ImageLoader imgLoader = seqCamData.getImageLoader();
		imgLoader.setImagesDirectory(eADF.getCameraImagesDirectory());
		List<String> imagesList = ExperimentDirectories.getImagesListFromPathV2(imgLoader.getImagesDirectory(), "jpg");
		seqCamData.loadImageList(imagesList);
		if (eADF.cameraImagesList.size() > 1)
			getFileIntervalsFromSeqCamData();
	}

	// ----------------------------------

	public String getResultsDirectory() {
		return resultsDirectory;
	}

	public String toString() {
		return resultsDirectory;
	}

	public void setResultsDirectory(String fileName) {
		resultsDirectory = ExperimentDirectories.getParentIf(fileName, BIN);
	}

	public void setBinDirectory(String bin) {
		binDirectory = bin;
	}

	public String getBinDirectory() {
		return binDirectory;
	}

	public boolean createDirectoryIfDoesNotExist(String directory) {
		Path pathDir = Paths.get(directory);
		if (Files.notExists(pathDir)) {
			try {
				Files.createDirectory(pathDir);
			} catch (IOException e) {
				e.printStackTrace();
				System.out
						.println("Experiment:createDirectoryIfDoesNotExist() Creating directory failed: " + directory);
				return false;
			}
		}
		return true;
	}

	public void checkKymosDirectory(String kymosSubDirectory) {
		if (kymosSubDirectory == null) {
			List<String> listTIFFlocations = Directories.getSortedListOfSubDirectoriesWithTIFF(getResultsDirectory());
			if (listTIFFlocations.size() < 1)
				return;
			boolean found = false;
			for (String subDir : listTIFFlocations) {
				String test = subDir.toLowerCase();
				if (test.contains(Experiment.BIN)) {
					kymosSubDirectory = subDir;
					found = true;
					break;
				}
				if (test.contains(Experiment.RESULTS)) {
					found = true;
					break;
				}
			}
			if (!found) {
				int lowest = getBinStepFromDirectoryName(listTIFFlocations.get(0)) + 1;
				for (String subDir : listTIFFlocations) {
					int val = getBinStepFromDirectoryName(subDir);
					if (val < lowest) {
						lowest = val;
						kymosSubDirectory = subDir;
					}
				}
			}
		}
//		setBinSubDirectory(kymosSubDirectory);
	}

	public void setCameraImagesDirectory(String name) {
		camDataImagesDirectory = name;
	}

	public String getCameraImagesDirectory() {
		return camDataImagesDirectory;
	}

	public void closeSequences() {
		if (seqCamData != null)
			seqCamData.closeSequence();
		if (seqReference != null)
			seqReference.close();
	}

	public boolean zopenPositionsMeasures() {
		if (seqCamData == null) {
			// Use builder pattern for initialization
			seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
		}
		load_MS96_experiment();
		getFileIntervalsFromSeqCamData();

		return zxmlReadDrosoTrack(null);
	}

	private String getRootWithNoResultNorBinString(String directoryName) {
		String name = directoryName.toLowerCase();
		while (name.contains(RESULTS) || name.contains(BIN))
			name = Paths.get(resultsDirectory).getParent().toString();
		return name;
	}

	private SequenceCamData loadImagesForSequenceCamData(String filename) {
		camDataImagesDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(filename);
		List<String> imagesList = ExperimentDirectories.getImagesListFromPathV2(camDataImagesDirectory, "jpg");
		seqCamData = null;
		if (imagesList.size() > 0) {
			// Use builder pattern with images directory and list
			seqCamData = SequenceCamData.builder().withImagesDirectory(camDataImagesDirectory)
					.withStatus(EnumStatus.FILESTACK).build();
			seqCamData.setImagesList(imagesList);
			seqCamData.attachSequence(seqCamData.getImageLoader().loadSequenceFromImagesList(imagesList));
		}
		return seqCamData;
	}

	public boolean loadCamDataSpots() {
		load_MS96_cages();
		if (seqCamData != null && seqCamData.getSequence() != null) {
			seqCamData.removeROIsContainingString("spot");
			getCages().transferCageSpotsToSequenceAsROIs(seqCamData);
		}
		return (seqCamData != null && seqCamData.getSequence() != null);
	}

	public SequenceCamData openSequenceCamData() {
		loadImagesForSequenceCamData(camDataImagesDirectory);
		if (seqCamData != null) {
			load_MS96_experiment();
			getFileIntervalsFromSeqCamData();
		}
		return seqCamData;
	}

	public void getFileIntervalsFromSeqCamData() {
		if (seqCamData != null && (seqCamData.getFirstImageMs() < 0 || seqCamData.getLastImageMs() < 0
				|| seqCamData.getTimeManager().getBinImage_ms() < 0)) {
			loadFileIntervalsFromSeqCamData();
		}
	}

	public void loadFileIntervalsFromSeqCamData() {
		if (seqCamData != null) {
			seqCamData.setImagesDirectory(camDataImagesDirectory);
			int t0 = (int) seqCamData.getImageLoader().getAbsoluteIndexFirstImage();
			firstImage_FileTime = seqCamData.getFileTimeFromStructuredName(t0);
			int t1 = seqCamData.getImageLoader().getNTotalFrames() - 1;
			lastImage_FileTime = seqCamData.getFileTimeFromStructuredName(t1);

			if (firstImage_FileTime != null && lastImage_FileTime != null) {
				seqCamData.setFirstImageMs(firstImage_FileTime.toMillis());
				seqCamData.setLastImageMs(lastImage_FileTime.toMillis());

				if (seqCamData.getImageLoader().getNTotalFrames() > 1) {
					long binMs = (seqCamData.getLastImageMs() - seqCamData.getFirstImageMs()) / t1;
					seqCamData.getTimeManager().setBinImage_ms(binMs);
				}
				if (seqCamData.getTimeManager().getBinImage_ms() == 0)
					System.out.println("Experiment:loadFileIntervalsFromSeqCamData() error / file interval size");
			} else {
				System.out.println("Experiment:loadFileIntervalsFromSeqCamData() error / file intervals of "
						+ seqCamData.getImagesDirectory());
			}
		}
	}

	public String getBinNameFromKymoFrameStep() {
		return BIN + seqCamData.getTimeManager().getBinDurationMs() / 1000;
	}

	public String getDirectoryToSaveResults() {
		Path dir = Paths.get(resultsDirectory);
//		if (binSubDirectory != null)
//			dir = dir.resolve(binSubDirectory);
		String directory = dir.toAbsolutePath().toString();
		if (!createDirectoryIfDoesNotExist(directory))
			directory = null;
		return directory;
	}

	// -------------------------------

	public boolean load_MS96_experiment() {
		if (resultsDirectory == null && seqCamData != null) {
			camDataImagesDirectory = seqCamData.getImagesDirectory();
			resultsDirectory = camDataImagesDirectory + File.separator + RESULTS;
		}
		String csFileName = concatenateExptDirectoryWithSubpathAndName(null, ID_MS96_experiment_XML);
		return load_MS96_experiment(csFileName);
	}

	private boolean load_MS96_experiment(String csFileName) {
		try {
			final Document doc = XMLUtil.loadDocument(csFileName);
			if (doc == null) {
				System.err.println("ERROR: Could not load XML document from " + csFileName);
				return false;
			}

			// Schema validation removed as requested

			Node node = XMLUtil.getElement(XMLUtil.getRootElement(doc), ID_MCEXPERIMENT);
			if (node == null) {
				System.err.println("ERROR: Could not find MCexperiment node in XML");
				return false;
			}

			// Version validation with detailed logging
			String version = XMLUtil.getElementValue(node, ID_VERSION, ID_VERSIONNUM);
			// System.out.println("XML Version: " + version);
			if (!version.equals(ID_VERSIONNUM)) {
				System.err.println("ERROR: Version mismatch. Expected: " + ID_VERSIONNUM + ", Found: " + version);
				return false;
			}

			// Load ImageLoader configuration with validation
			ImageLoader imgLoader = seqCamData.getImageLoader();
			long frameFirst = XMLUtil.getElementLongValue(node, ID_FRAMEFIRST, 0);
			if (frameFirst < 0) {
				// System.out.println("WARNING: frameFirst < 0, setting to 0");
				frameFirst = 0;
			}
			imgLoader.setAbsoluteIndexFirstImage(frameFirst);

			long nImages = XMLUtil.getElementLongValue(node, ID_NFRAMES, -1);
			if (nImages <= 0) {
				System.err.println("ERROR: Invalid number of frames: " + nImages + " in " + csFileName);
				return false;
			}
			imgLoader.setFixedNumberOfImages(nImages);
			imgLoader.setNTotalFrames((int) (nImages - frameFirst));

			// Load TimeManager configuration with validation
			TimeManager timeManager = seqCamData.getTimeManager();
			long firstMs = XMLUtil.getElementLongValue(node, ID_TIMEFIRSTIMAGEMS, 0);
			timeManager.setFirstImageMs(firstMs);
			long lastMs = XMLUtil.getElementLongValue(node, ID_TIMELASTIMAGEMS, 0);
			timeManager.setLastImageMs(lastMs);
			long durationMs = lastMs - firstMs;
			timeManager.setDurationMs(durationMs);
			long frameDelta = XMLUtil.getElementLongValue(node, ID_FRAMEDELTA, 1);
			timeManager.setDeltaImage(frameDelta);
			long binFirstMs = XMLUtil.getElementLongValue(node, ID_FIRSTKYMOCOLMS, -1);
			timeManager.setBinFirst_ms(binFirstMs);
			long binLastMs = XMLUtil.getElementLongValue(node, ID_LASTKYMOCOLMS, -1);
			timeManager.setBinLast_ms(binLastMs);
			long binDurationMs = XMLUtil.getElementLongValue(node, ID_BINKYMOCOLMS, -1);
			timeManager.setBinDurationMs(binDurationMs);

			// Load properties with error handling
			try {
				prop.loadXML_Properties(node);
				// System.out.println("Experiment properties loaded successfully");
			} catch (Exception e) {
				System.err.println("ERROR: Failed to load experiment properties: " + e.getMessage());
				return false;
			}

			ugly_checkOffsetValues();

			return true;

		} catch (Exception e) {
			System.err.println("ERROR during experiment XML loading: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	public boolean save_MS96_experiment() {
		try {
			final Document doc = XMLUtil.createDocument(true);
			if (doc == null) {
				System.err.println("ERROR: Could not create XML document");
				return false;
			}

			Node xmlRoot = XMLUtil.getRootElement(doc, true);
			Node node = XMLUtil.setElement(xmlRoot, ID_MCEXPERIMENT);
			if (node == null) {
				System.err.println("ERROR: Could not create MCexperiment node");
				return false;
			}

			// Version information
			XMLUtil.setElementValue(node, ID_VERSION, ID_VERSIONNUM);
			// System.out.println("Saving XML Version: " + ID_VERSIONNUM);

			// Save ImageLoader configuration
			ImageLoader imgLoader = seqCamData.getImageLoader();
			long frameFirst = imgLoader.getAbsoluteIndexFirstImage();
			long nImages = imgLoader.getFixedNumberOfImages();
			XMLUtil.setElementLongValue(node, ID_FRAMEFIRST, frameFirst);
			XMLUtil.setElementLongValue(node, ID_NFRAMES, nImages);

			// Save TimeManager configuration
			TimeManager timeManager = seqCamData.getTimeManager();
			long firstMs = timeManager.getFirstImageMs();
			long lastMs = timeManager.getLastImageMs();
			XMLUtil.setElementLongValue(node, ID_TIMEFIRSTIMAGEMS, firstMs);
			XMLUtil.setElementLongValue(node, ID_TIMELASTIMAGEMS, lastMs);
			XMLUtil.setElementLongValue(node, ID_FRAMEDELTA, timeManager.getDeltaImage());
			XMLUtil.setElementLongValue(node, ID_FIRSTKYMOCOLMS, timeManager.getBinFirst_ms());
			XMLUtil.setElementLongValue(node, ID_LASTKYMOCOLMS, timeManager.getBinLast_ms());
			XMLUtil.setElementLongValue(node, ID_BINKYMOCOLMS, timeManager.getBinDurationMs());

			// Save properties
			try {
				prop.saveXML_Properties(node);
				// System.out.println("Experiment properties saved successfully");
			} catch (Exception e) {
				System.err.println("ERROR: Failed to save experiment properties: " + e.getMessage());
				return false;
			}

			if (camDataImagesDirectory == null)
				camDataImagesDirectory = seqCamData.getImagesDirectory();
			XMLUtil.setElementValue(node, ID_IMAGESDIRECTORY, camDataImagesDirectory);

			String tempname = concatenateExptDirectoryWithSubpathAndName(null, ID_MS96_experiment_XML);
			boolean success = XMLUtil.saveDocument(doc, tempname);
			return success;
		} catch (Exception e) {
			System.err.println("ERROR during experiment XML saving: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private void ugly_checkOffsetValues() {
		if (seqCamData.getFirstImageMs() < 0)
			seqCamData.setFirstImageMs(0);
		if (seqCamData.getLastImageMs() < 0)
			seqCamData.setLastImageMs(0);
		if (seqCamData.getTimeManager().getBinFirst_ms() < 0)
			seqCamData.getTimeManager().setBinFirst_ms(0);
		if (seqCamData.getTimeManager().getBinLast_ms() < 0)
			seqCamData.getTimeManager().setBinLast_ms(0);
		if (seqCamData.getTimeManager().getBinDurationMs() < 0)
			seqCamData.getTimeManager().setBinDurationMs(60000);
	}

	// -------------------------------

	private String getXML_MS96_cages_Location(String XMLfileName) {
		String fileName = findFile_3Locations(XMLfileName, EXPT_DIRECTORY, BIN_DIRECTORY, IMG_DIRECTORY);
		if (fileName == null)
			fileName = concatenateExptDirectoryWithSubpathAndName(null, XMLfileName);
		return fileName;
	}

	public boolean load_MS96_cages() {
		String fileName = getXML_MS96_cages_Location(getCages().ID_MS96_cages_XML);
		return getCages().xmlReadCagesFromFileNoQuestion(fileName);
	}

	public boolean save_MS96_cages() {
		String fileName = getXML_MS96_cages_Location(getCages().ID_MS96_cages_XML);
		return getCages().xmlWriteCagesToFileNoQuestion(fileName);
	}

	// -------------------------------

	public boolean load_MS96_spotsMeasures() {
		return getCages().load_SpotsMeasures(getResultsDirectory());
	}

	public boolean save_MS96_spotsMeasures() {
		return getCages().save_SpotsMeasures(getResultsDirectory());
	}

	public boolean load_MS96_fliesPositions() {
		// TODO write real code
		return false;
	}

	public boolean save_MS96_fliesPositions() {
		// TODO write real code
		return false;
	}

	// -------------------------------

	final String csvSep = ";";

	public Experiment getFirstChainedExperiment(boolean globalValue) {
		Experiment exp = this;
		if (globalValue && chainToPreviousExperiment != null)
			exp = chainToPreviousExperiment.getFirstChainedExperiment(globalValue);
		return exp;
	}

	public Experiment getLastChainedExperiment(boolean globalValue) {
		Experiment exp = this;
		if (globalValue && chainToNextExperiment != null)
			exp = chainToNextExperiment.getLastChainedExperiment(globalValue);
		return exp;
	}

	public void setFileTimeImageFirst(FileTime fileTimeImageFirst) {
		this.firstImage_FileTime = fileTimeImageFirst;
	}

	public void setFileTimeImageLast(FileTime fileTimeImageLast) {
		this.lastImage_FileTime = fileTimeImageLast;
	}

	public List<String> getFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		List<String> textList = new ArrayList<String>();
		switch (fieldEnumCode) {
		case EXP_STIM1:
		case EXP_CONC1:
		case EXP_EXPT:
		case EXP_BOXID:
		case EXP_STRAIN:
		case EXP_SEX:
		case EXP_STIM2:
		case EXP_CONC2:
			textList.add(prop.getExperimentField(fieldEnumCode));
			break;
		case SPOT_STIM:
		case SPOT_CONC:
		case SPOT_VOLUME:
			textList = getSpotsFieldValues(fieldEnumCode);
			break;
		case CAGE_SEX:
		case CAGE_STRAIN:
		case CAGE_AGE:
			textList = getCagesFieldValues(fieldEnumCode);
			break;
		default:
			break;
		}
		return textList;
	}

	public boolean replaceExperimentFieldIfEqualOldValue(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		boolean flag = prop.getExperimentField(fieldEnumCode).equals(oldValue);
		if (flag) {
			prop.setExperimentFieldNoTest(fieldEnumCode, newValue);
		}
		return flag;
	}

	// --------------------------------------------

	public boolean loadReferenceImage() {
		BufferedImage image = null;
		File inputfile = new File(getReferenceImageFullName());
		boolean exists = inputfile.exists();
		if (!exists)
			return false;
		image = ImageUtil.load(inputfile, true);
		if (image == null) {
			// System.out.println("Experiment:loadReferenceImage() image not loaded / not
			// found");
			return false;
		}
		seqCamData.setReferenceImage(IcyBufferedImage.createFrom(image));
		seqReference = new Sequence(seqCamData.getReferenceImage());
		seqReference.setName("referenceImage");
		return true;
	}

	public boolean saveReferenceImage(IcyBufferedImage referenceImage) {
		File outputfile = new File(getReferenceImageFullName());
		RenderedImage image = ImageUtil.toRGBImage(referenceImage);
		return ImageUtil.save(image, "jpg", outputfile);
	}

	public void cleanPreviousDetectedFliesROIs() {
		ArrayList<ROI2D> list = seqCamData.getSequence().getROI2Ds();
		for (ROI2D roi : list) {
			if (roi.getName().contains("det"))
				seqCamData.getSequence().removeROI(roi);
		}
	}

	public String zgetMCDrosoTrackFullName() {
		return resultsDirectory + File.separator + ID_MCDROSOTRACK_XML;
	}

	public void updateROIsAt(int t) {
		seqCamData.getSequence().beginUpdate();
		List<ROI2D> rois = seqCamData.getSequence().getROI2Ds();
		for (ROI2D roi : rois) {
			if (roi.getName().contains("det"))
				seqCamData.getSequence().removeROI(roi);
		}
		seqCamData.getSequence().addROIs(getCages().getPositionsAsListOfROI2DRectanglesAtT(t), false);
		seqCamData.getSequence().endUpdate();
	}

	public void saveDetRoisToPositions() {
		List<ROI2D> detectedROIsList = seqCamData.getSequence().getROI2Ds();
		for (Cage cage : getCages().cagesList) {
			cage.transferRoisToPositions(detectedROIsList);
		}
	}

	// ----------------------------------

	private int getBinStepFromDirectoryName(String resultsPath) {
		int step = -1;
		if (resultsPath.contains(BIN)) {
			if (resultsPath.length() < (BIN.length() + 1))
				step = (int) seqCamData.getTimeManager().getBinDurationMs();
			else
				step = Integer.valueOf(resultsPath.substring(BIN.length())) * 1000;
		}
		return step;
	}

	private boolean zxmlReadDrosoTrack(String filename) {
		if (filename == null) {
			filename = getXML_MS96_cages_Location(getCages().ID_MS96_cages_XML);
			if (filename == null)
				return false;
		}
		return getCages().xmlReadCagesFromFileNoQuestion(filename);
	}

	private String findFile_3Locations(String xmlFileName, int first, int second, int third) {
		// current directory
		String xmlFullFileName = findFile_1Location(xmlFileName, first);
		if (xmlFullFileName == null)
			xmlFullFileName = findFile_1Location(xmlFileName, second);
		if (xmlFullFileName == null)
			xmlFullFileName = findFile_1Location(xmlFileName, third);
		return xmlFullFileName;
	}

	private String findFile_1Location(String xmlFileName, int item) {
		String xmlFullFileName = File.separator + xmlFileName;
		switch (item) {
		case IMG_DIRECTORY:
			camDataImagesDirectory = getRootWithNoResultNorBinString(resultsDirectory);
			xmlFullFileName = camDataImagesDirectory + File.separator + xmlFileName;
			break;

		case BIN_DIRECTORY:
			// any directory (below)
			Path dirPath = Paths.get(resultsDirectory);
			List<Path> subFolders = Directories.getAllSubPathsOfDirectory(resultsDirectory, 1);
			if (subFolders == null)
				return null;
			List<String> resultsDirList = Directories.getPathsContainingString(subFolders, RESULTS);
			List<String> binDirList = Directories.getPathsContainingString(subFolders, BIN);
			resultsDirList.addAll(binDirList);
			for (String resultsSub : resultsDirList) {
				Path dir = dirPath.resolve(resultsSub + File.separator + xmlFileName);
				if (Files.notExists(dir))
					continue;
				xmlFullFileName = dir.toAbsolutePath().toString();
				break;
			}
			break;

		case EXPT_DIRECTORY:
		default:
			xmlFullFileName = resultsDirectory + xmlFullFileName;
			break;
		}

		// current directory
		if (xmlFullFileName != null && fileExists(xmlFullFileName)) {
			if (item == IMG_DIRECTORY) {
				camDataImagesDirectory = getRootWithNoResultNorBinString(resultsDirectory);
				ExperimentDirectories.moveAndRename(xmlFileName, camDataImagesDirectory, xmlFileName, resultsDirectory);
				xmlFullFileName = resultsDirectory + xmlFullFileName;
			}
			return xmlFullFileName;
		}
		return null;
	}

	private boolean fileExists(String fileName) {
		File f = new File(fileName);
		return (f.exists() && !f.isDirectory());
	}

	public boolean replaceSpotsFieldValueWithNewValueIfOld(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		load_MS96_cages();
		boolean flag = false;
		for (Cage cage : getCages().cagesList) {
			for (Spot spot : cage.spotsArray.getSpotsList()) {
				String current = spot.getField(fieldEnumCode);
				if (current != null && oldValue != null && current.trim().equals(oldValue.trim())) {
					spot.setField(fieldEnumCode, newValue);
					flag = true;
				}
			}
		}
		return flag;
	}

	public boolean replaceCageFieldValueWithNewValueIfOld(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		load_MS96_cages();
		boolean flag = false;
		for (Cage cage : getCages().cagesList) {
			String current = cage.getField(fieldEnumCode);
			if (current != null && oldValue != null && current.trim().equals(oldValue.trim())) {
				cage.setField(fieldEnumCode, newValue);
				flag = true;
			}
		}
		return flag;
	}

	private String concatenateExptDirectoryWithSubpathAndName(String subpath, String name) {
		if (subpath != null)
			return resultsDirectory + File.separator + subpath + File.separator + name;
		else
			return resultsDirectory + File.separator + name;
	}

	private List<String> getSpotsFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		load_MS96_cages();
		List<String> textList = new ArrayList<String>();
		for (Cage cage : getCages().cagesList)
			for (Spot spot : cage.spotsArray.getSpotsList())
				addValueIfUnique(spot.getField(fieldEnumCode), textList);
		return textList;
	}

	private List<String> getCagesFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		load_MS96_cages();
		List<String> textList = new ArrayList<String>();
		for (Cage cage : getCages().cagesList)
			addValueIfUnique(cage.getField(fieldEnumCode), textList);
		return textList;
	}

	private void addValueIfUnique(String text, List<String> textList) {
		if (!isFound(text, textList))
			textList.add(text);
	}

	private boolean isFound(String pattern, List<String> names) {
		boolean found = false;
		if (names.size() > 0) {
			for (String name : names) {
				found = name.equals(pattern);
				if (found)
					break;
			}
		}
		return found;
	}

	private String getReferenceImageFullName() {
		return resultsDirectory + File.separator + "referenceImage.jpg";
	}

	public void transferCagesROI_toSequence() {
		seqCamData.removeROIsContainingString("cage");
		getCages().transferCagesToSequenceAsROIs(seqCamData);
	}

	public void transferSpotsROI_toSequence() {
		seqCamData.removeROIsContainingString("spot");
		getCages().transferCageSpotsToSequenceAsROIs(seqCamData);
	}

	public boolean saveCagesArray_File() {
		getCages().transferROIsFromSequenceToCages(seqCamData);
		save_MS96_cages();
		return save_MS96_spotsMeasures();
	}

	public boolean saveSpotsArray_file() {
		getCages().transferROIsFromSequenceToCageSpots(seqCamData);
		boolean flag = save_MS96_cages();
		flag &= save_MS96_spotsMeasures();
		return flag;
	}

	public ExperimentProperties getProperties() {
		return prop;
	}

}
