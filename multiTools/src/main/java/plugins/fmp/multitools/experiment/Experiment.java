package plugins.fmp.multitools.experiment;

import java.awt.Rectangle;
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

import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.cages.CagesPersistence.Persistence;
import plugins.fmp.multitools.experiment.cages.CagesSequenceMapper;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesDescription;
import plugins.fmp.multitools.experiment.capillaries.CapillariesKymosMapper;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.ids.CapillaryID;
import plugins.fmp.multitools.experiment.sequence.ImageAdjustmentOptions;
import plugins.fmp.multitools.experiment.sequence.ImageFileData;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.sequence.ImageProcessingResult;
import plugins.fmp.multitools.experiment.sequence.KymographInfo;
import plugins.fmp.multitools.experiment.sequence.MeasureRoiSync;
import plugins.fmp.multitools.experiment.sequence.MeasureRoiSync.MeasureRoiFilter;
import plugins.fmp.multitools.experiment.sequence.ROIsAtTProvider;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.experiment.spots.SpotsSequenceMapper;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Directories;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.Results;
import plugins.fmp.multitools.tools.results.ResultsArray;
import plugins.fmp.multitools.tools.results.ResultsArrayFromCapillaries;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.enums.EnumXLSColumnHeader;

public class Experiment {
	public final static String RESULTS = "results";
	public final static String BIN = "bin_";

	private String camDataImagesDirectory = null;
	private String resultsDirectory = null;
	private String binDirectory = null;

	private SequenceCamData seqCamData = null;
	private SequenceKymos seqKymos = null;
	private Sequence seqReference = null;

	private Cages cages = new Cages();
	private Capillaries capillaries = new Capillaries();
	private Spots spots = new Spots();

	private ExperimentTimeManager timeManager = new ExperimentTimeManager();
	private ExperimentProperties prop = new ExperimentProperties();

	private BinDescription activeBinDescription = new BinDescription();
	private BinDescriptionPersistence binDescriptionPersistence = new BinDescriptionPersistence();

	public Experiment chainToPreviousExperiment = null;
	public Experiment chainToNextExperiment = null;
	public long chainImageFirst_ms = 0;
	public int experimentID = 0;
	public int col = -1;

	private String generatorProgram = null;
	private static String staticProgramContext = null;
	// Flags to prevent race conditions between loading and saving
	private volatile boolean isLoading = false;
	private volatile boolean isSaving = false;

	// -----------------------------------------

	public Sequence getSeqReference() {
		return seqReference;
	}

	public void setSeqReference(Sequence seqReference) {
		this.seqReference = seqReference;
	}

	public Cages getCages() {
		return cages;
	}

	public void setCages(Cages cages) {
		this.cages = cages;
	}

	// __________________________________________________

	public FileTime getFirstImage_FileTime() {
		return timeManager.getFirstImage_FileTime();
	}

	public void setFirstImage_FileTime(FileTime fileTime) {
		timeManager.setFirstImage_FileTime(fileTime);
	}

	public FileTime getLastImage_FileTime() {
		return timeManager.getLastImage_FileTime();
	}

	public void setLastImage_FileTime(FileTime fileTime) {
		timeManager.setLastImage_FileTime(fileTime);
	}

	public long getCamImageFirst_ms() {
		return timeManager.getCamImageFirst_ms();
	}

	public void setCamImageFirst_ms(long ms) {
		timeManager.setCamImageFirst_ms(ms);
	}

	public long getCamImageLast_ms() {
		return timeManager.getCamImageLast_ms();
	}

	public void setCamImageLast_ms(long ms) {
		timeManager.setCamImageLast_ms(ms);
	}

	public long getCamImageBin_ms() {
		return timeManager.getCamImageBin_ms();
	}

	public void setCamImageBin_ms(long ms) {
		timeManager.setCamImageBin_ms(ms);
	}

	public long[] getCamImages_ms() {
		return timeManager.getCamImages_ms();
	}

	public void setCamImages_ms(long[] ms) {
		timeManager.setCamImages_ms(ms);
	}

	public long getKymoFirst_ms() {
		if (activeBinDescription != null && activeBinDescription.getFirstKymoColMs() >= 0) {
			return activeBinDescription.getFirstKymoColMs();
		}
		return timeManager.getKymoFirst_ms();
	}

	public void setKymoFirst_ms(long ms) {
		if (activeBinDescription != null) {
			activeBinDescription.setFirstKymoColMs(ms);
		}
		timeManager.setKymoFirst_ms(ms);
	}

	public long getKymoLast_ms() {
		if (activeBinDescription != null && activeBinDescription.getLastKymoColMs() >= 0) {
			return activeBinDescription.getLastKymoColMs();
		}
		return timeManager.getKymoLast_ms();
	}

	public void setKymoLast_ms(long ms) {
		if (activeBinDescription != null) {
			activeBinDescription.setLastKymoColMs(ms);
		}
		timeManager.setKymoLast_ms(ms);
	}

	public long getKymoBin_ms() {
		long timeManagerValue = timeManager.getKymoBin_ms();
		long activeBinValue = (activeBinDescription != null && activeBinDescription.getBinKymoColMs() > 0)
				? activeBinDescription.getBinKymoColMs()
				: 0;

		// Generic checks to determine if timeManager has been explicitly set
		// 1. Check if camImageBin_ms has been calculated (indicates refresh from files)
		boolean intervalCalculated = timeManager.getCamImageBin_ms() >= 0;
		// 2. Check if values differ (timeManager assumed more recent)
		boolean valuesDiffer = timeManagerValue != activeBinValue && activeBinValue > 0;

		// Prioritize timeManager if interval was calculated or if values differ
		if (intervalCalculated || (valuesDiffer && timeManagerValue > 0)) {
			// Sync activeBinDescription for consistency
			if (activeBinDescription != null) {
				activeBinDescription.setBinKymoColMs(timeManagerValue);
			}
			return timeManagerValue;
		}

		// Fall back to activeBinDescription if it has a value
		if (activeBinValue > 0) {
			return activeBinValue;
		}

		// Default to timeManager value
		return timeManagerValue;
	}

	public void setKymoBin_ms(long ms) {
		if (activeBinDescription != null) {
			activeBinDescription.setBinKymoColMs(ms);
		}
		timeManager.setKymoBin_ms(ms);
	}

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
		loadExperimentDescriptors();
	}

	public Experiment(ExperimentDirectories eADF) {
		camDataImagesDirectory = eADF.getCameraImagesDirectory();
		resultsDirectory = eADF.getResultsDirectory();
		seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
		loadExperimentDescriptors();

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

	public boolean isLoading() {
		return isLoading;
	}

	public void setLoading(boolean loading) {
		this.isLoading = loading;
	}

	public boolean isSaving() {
		return isSaving;
	}

	public void setSaving(boolean saving) {
		this.isSaving = saving;
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

	public String getImagesDirectory() {
		return camDataImagesDirectory;
	}

	public void setImagesDirectory(String imagesDirectory) {
		this.camDataImagesDirectory = imagesDirectory;
	}

	// ------------------------------ Legacy Metadata Accessors

	public String getBoxID() {
		return prop.field_boxID;
	}

	public void setBoxID(String boxID) {
		prop.field_boxID = boxID;
	}

	public String getExperiment() {
		return prop.field_experiment;
	}

	public void setExperiment(String experiment) {
		prop.field_experiment = experiment;
	}

	public String getComment1() {
		return prop.field_comment1;
	}

	public void setComment1(String comment1) {
		prop.field_comment1 = comment1;
	}

	public String getComment2() {
		return prop.field_comment2;
	}

	public void setComment2(String comment2) {
		prop.field_comment2 = comment2;
	}

	public String getStrain() {
		return prop.field_strain;
	}

	public void setStrain(String strain) {
		prop.field_strain = strain;
	}

	public String getSex() {
		return prop.field_sex;
	}

	public void setSex(String sex) {
		prop.field_sex = sex;
	}

	public String getCondition1() {
		return prop.field_stim2;
	}

	public void setCondition1(String condition1) {
		prop.field_stim2 = condition1;
	}

	public String getCondition2() {
		return prop.field_conc2;
	}

	public void setCondition2(String condition2) {
		prop.field_conc2 = condition2;
	}

	public String getGeneratorProgram() {
		return generatorProgram;
	}

	public void setGeneratorProgram(String generatorProgram) {
		this.generatorProgram = generatorProgram;
	}

	public static void setProgramContext(String programName) {
		staticProgramContext = programName;
	}

	public static String getProgramContext() {
		return staticProgramContext;
	}

//	private String getOrDetermineGeneratorProgram() {
//		if (generatorProgram != null) {
//			return generatorProgram;
//		}
//		String programName = determineProgramFromStackTrace();
//		if (programName != null) {
//			return programName;
//		}
//		if (staticProgramContext != null) {
//			return staticProgramContext;
//		}
//		return null;
//	}

//	private String determineProgramFromStackTrace() {
//		return determineProgramFromStackTraceStatic();
//	}

	public static String determineProgramFromStackTraceStatic() {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		for (StackTraceElement element : stackTrace) {
			String className = element.getClassName();
			try {
				Class<?> clazz = Class.forName(className);
				if (isRootProgramClass(clazz)) {
					return convertClassNameToProgramName(clazz.getSimpleName());
				}
			} catch (ClassNotFoundException e) {
				continue;
			} catch (NoClassDefFoundError e) {
				continue;
			} catch (Exception e) {
				continue;
			}
		}
		return null;
	}

	private static boolean isRootProgramClass(Class<?> clazz) {
		String className = clazz.getName();
		String simpleName = clazz.getSimpleName();

		if (className == null || simpleName == null) {
			return false;
		}

		if (className.contains("Experiment") || className.contains("Persistence")
				|| className.contains("fmp_experiment") || className.contains("fmp_tools")) {
			return false;
		}

		try {
			Class<?> superClass = clazz.getSuperclass();
			if (superClass != null && superClass.getName().equals("icy.plugin.abstract_.PluginActionable")) {
				if (simpleName.startsWith("Multi")) {
					return true;
				}
			}
		} catch (Exception e) {
		}

		return false;
	}

	public static String convertClassNameToProgramName(String className) {
		if (className == null || className.isEmpty()) {
			return null;
		}
		char[] chars = className.toCharArray();
		if (chars.length == 0) {
			return className.toLowerCase();
		}
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (i == 0) {
				result.append(Character.toLowerCase(c));
			} else if (Character.isUpperCase(c)) {
				result.append(Character.toLowerCase(c));
			} else {
				result.append(c);
			}
		}
		return result.toString();
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
		if (seqKymos != null)
			seqKymos.closeSequence();
	}

	public boolean zopenPositionsMeasures() {
		if (seqCamData == null) {
			// Use builder pattern for initialization
			seqCamData = SequenceCamData.builder().withStatus(EnumStatus.FILESTACK).build();
		}
		loadExperimentDescriptors();
		getFileIntervalsFromSeqCamData();

		return loadCagesMeasures();
	}

//	private boolean zxmlReadDrosoTrack(String filename) {
//		if (filename == null) {
//			filename = getXML_MS96_cages_Location(cages.ID_MS96_cages_XML);
//			if (filename == null)
//				return false;
//		}
//		return cages.xmlReadCagesFromFileNoQuestion(filename);
//	}

//	private String getRootWithNoResultNorBinString(String directoryName) {
//		String name = directoryName.toLowerCase();
//		while (name.contains(RESULTS) || name.contains(BIN))
//			name = Paths.get(resultsDirectory).getParent().toString();
//		return name;
//	}

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
		load_cages_description_and_measures();
		if (seqCamData != null && seqCamData.getSequence() != null) {
			seqCamData.removeROIsContainingString("spot");
			cages.transferCageSpotsToSequenceAsROIs(seqCamData, spots);
		}
		return (seqCamData != null && seqCamData.getSequence() != null);
	}

	public SequenceCamData openSequenceCamData() {
		loadImagesForSequenceCamData(camDataImagesDirectory);
		if (seqCamData != null) {
			loadExperimentDescriptors();
			getFileIntervalsFromSeqCamData();
		}
		return seqCamData;
	}

	public void getFileIntervalsFromSeqCamData() {
		timeManager.getFileIntervalsFromSeqCamData(seqCamData, camDataImagesDirectory);
	}

	public void loadFileIntervalsFromSeqCamData() {
		timeManager.loadFileIntervalsFromSeqCamData(seqCamData, camDataImagesDirectory);
	}

	public String getBinNameFromKymoFrameStep() {
		return Experiment.BIN + getKymoBin_ms() / 1000;
	}

	public long[] build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(long firstImage_ms) {
		return timeManager.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(seqCamData, firstImage_ms);
	}

	public void initTmsForFlyPositions(long time_start_ms) {
		timeManager.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(seqCamData, time_start_ms);
		cages.initCagesTmsForFlyPositions(timeManager.getCamImages_ms());
	}

	public int findNearestIntervalWithBinarySearch(long value, int low, int high) {
		return timeManager.findNearestIntervalWithBinarySearch(value, low, high);
	}

	public int getSeqCamSizeT() {
		int lastFrame = 0;
		if (seqCamData != null)
			lastFrame = seqCamData.getImageLoader().getNTotalFrames() - 1;
		return lastFrame;
	}

	public String getDirectoryToSaveResults() {
		Path dir = Paths.get(resultsDirectory);
		if (binDirectory != null)
			dir = dir.resolve(binDirectory);
		String directory = dir.toAbsolutePath().toString();
		if (!createDirectoryIfDoesNotExist(directory))
			directory = null;
		return directory;
	}

	// -------------------------------

	/**
	 * Loads experiment-level metadata (description and timing) from the v2/legacy
	 * {@code *Experiment.xml} file.
	 * <p>
	 * This is the primary entry point for experiment description persistence.
	 */
	public boolean xmlLoad_MCExperiment() {
		return loadExperimentDescriptors();
	}

	public boolean saveExperimentDescriptors() {
		ExperimentPersistence persistence = new ExperimentPersistence();
		boolean saved = persistence.saveExperimentDescriptors(this);
		// Also save bin description if interval has been calculated
		// Update binDirectory to match calculated interval before saving
		if (saved && this.timeManager.getCamImageBin_ms() >= 0) {
			// Interval was calculated - ensure binDirectory matches the interval
			long kymoBinMs = getKymoBin_ms();
			if (kymoBinMs > 0) {
				String expectedBinDir = getBinNameFromKymoFrameStep();
				// Extract just the subdirectory name from binDirectory if it's a full path
				String currentBinDirName = binDirectory;
				if (currentBinDirName != null) {
					File binDirFile = new File(currentBinDirName);
					if (binDirFile.isAbsolute()) {
						currentBinDirName = binDirFile.getName();
					}
				}
				// Update binDirectory if it doesn't match the calculated interval
				if (binDirectory == null || currentBinDirName == null || !currentBinDirName.equals(expectedBinDir)) {
					setBinSubDirectory(expectedBinDir);
				}
				// Now save with correct directory name
				saveBinDescription();
			}
		}
		return saved;
	}

	public boolean loadExperimentDescriptors() {
		if (resultsDirectory == null && seqCamData != null) {
			camDataImagesDirectory = seqCamData.getImagesDirectory();
			resultsDirectory = camDataImagesDirectory + File.separator + RESULTS;
		}

		ExperimentPersistence persistence = new ExperimentPersistence();
		boolean loaded = persistence.loadExperimentDescriptors(this);
		if (loaded) {
			checkOffsetValues();
		}
		return loaded;
	}

	private void checkOffsetValues() {
		if (seqCamData == null)
			return;

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

	/**
	 * High-level loader for cage description and measures (legacy \"MS96\" naming).
	 * <p>
	 * Responsibilities:
	 * <ul>
	 * <li>load cage description from results directory (transparent fallback to
	 * legacy formats),</li>
	 * <li>load cage measures from bin directory,</li>
	 * <li>materialize cages and spots as ROIs on {@link SequenceCamData}.</li>
	 * </ul>
	 */
	public boolean load_cages_description_and_measures() {
		String resultsDir = getResultsDirectory();

		// Load descriptions from results directory (Legacy classes handle fallback
		// transparently)
		boolean cagesLoaded = Persistence.loadDescription(cages, resultsDir);

		// Load measures from bin directory (if available)
		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			cages.getPersistence().loadMeasures(cages, binDir);
		}

		// Transfer cages to ROIs on sequence if loaded successfully
		if (cagesLoaded && seqCamData != null && seqCamData.getSequence() != null) {
			CagesSequenceMapper.transferROIsToSequence(cages, seqCamData);
		}

		return cagesLoaded;
	}

	public boolean save_cages_description_and_measures() {
		String resultsDir = getResultsDirectory();
		// Always sync latest cage geometry from sequence before persisting.
		CagesSequenceMapper.syncCagesFromSequenceBeforeSave(cages, seqCamData);
		boolean descriptionsSaved = Persistence.saveDescription(cages, resultsDir);

		// Also save measures to bin directory (if available)
		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			cages.getPersistence().saveMeasures(cages, binDir);
		}

		return descriptionsSaved;
	}

	// -------------------------------

	/**
	 * Loads spot description and measures for the current experiment.
	 * <p>
	 * Description comes from the results directory, measures from the bin
	 * directory.
	 * <p>
	 * <b>Experiment Type Behavior:</b>
	 * <ul>
	 * <li><b>multiCAFE:</b> No spots files exist - returns false immediately (no
	 * error)</li>
	 * <li><b>multiSPOTS/multiSPOTS96:</b> Loads spot measures successfully</li>
	 * </ul>
	 * This early exit for multiCAFE experiments improves performance by avoiding
	 * unnecessary file system checks.
	 */
	public boolean load_spots_description_and_measures() {
		String resultsDir = getResultsDirectory();

		// Check if spots files exist before attempting to load
		// This avoids unnecessary file system checks for multiCAFE experiments
		if (!spots.hasSpotsFiles(resultsDir)) {
			return false;
		}

		boolean descriptionsLoaded = spots.getPersistence().loadDescriptions(spots, resultsDir);

		// Load measures from bin directory (if available)
		String binDir = getKymosBinFullDirectory();
		if (binDir != null && spots.getPersistence().hasSpotsMeasuresFiles(binDir)) {
			spots.getPersistence().loadMeasures(spots, binDir);
		}

		return descriptionsLoaded;
	}

	/**
	 * Saves spot description and measures for the current experiment.
	 * <p>
	 * Description is written to the results directory, measures to the bin
	 * directory.
	 */
	public boolean save_spots_description_and_measures() {
		String resultsDir = getResultsDirectory();
		boolean descriptionsSaved = spots.getPersistence().saveDescriptions(spots, resultsDir);

		// Also save measures to bin directory (if available)
		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			spots.getPersistence().saveMeasures(spots, binDir);
		}

		return descriptionsSaved;
	}

	// -------------------------------

	/**
	 * Loads capillary description and measures for the current experiment.
	 * <p>
	 * Description comes from the results directory, measures from the bin
	 * directory.
	 * <p>
	 * <b>Experiment Type Behavior:</b>
	 * <ul>
	 * <li><b>multiCAFE:</b> Loads capillary measures (main data) + kymographs</li>
	 * <li><b>multiSPOTS/multiSPOTS96:</b> No capillaries files exist - returns
	 * false immediately (no error)</li>
	 * </ul>
	 * This early exit for multiSPOTS experiments improves performance by avoiding
	 * unnecessary file system checks.
	 * <p>
	 * Note: Kymographs are always present in multiCAFE (used to construct
	 * measures), but optional in multiSPOTS.
	 */
	public boolean load_capillaries_description_and_measures() {
		String resultsDir = getResultsDirectory();

		if (!capillaries.getPersistence().hasCapillariesDescriptionFiles(resultsDir)) {
			return false;
		}

		boolean descriptionsLoaded = capillaries.getPersistence().loadDescriptions(capillaries, resultsDir);

		String binDir = getKymosBinFullDirectory();
		boolean measuresLoaded = false;
		if (binDir != null && capillaries.getPersistence().hasCapillariesMeasuresFiles(binDir)) {
			measuresLoaded = capillaries.getPersistence().loadMeasures(capillaries, binDir);
		}

		transferCapillaryRoisToSequence();

		if (measuresLoaded && seqKymos != null && seqKymos.getSequence() != null) {
			CapillariesKymosMapper.pushCapillaryMeasuresToKymos(capillaries, seqKymos);
		}

		return descriptionsLoaded || measuresLoaded;
	}

	/**
	 * Loads capillary descriptions only (no measures). Use when running direct
	 * detection so existing in-memory top/bottom/derivative/gulps are preserved.
	 */
	public boolean load_capillaries_description_only() {
		String resultsDir = getResultsDirectory();

		if (!capillaries.getPersistence().hasCapillariesDescriptionFiles(resultsDir)) {
			return false;
		}

		boolean descriptionsLoaded = capillaries.getPersistence().loadDescriptions(capillaries, resultsDir);
		transferCapillaryRoisToSequence();
		return descriptionsLoaded;
	}

	private void transferCapillaryRoisToSequence() {
		if (capillaries.getList().size() == 0 || seqCamData == null || seqCamData.getSequence() == null)
			return;
		seqCamData.removeROIsContainingString("line");
		int roisAdded = 0;
		for (Capillary cap : capillaries.getList()) {
			if (cap.getRoi() != null) {
				seqCamData.getSequence().addROI(cap.getRoi());
				roisAdded++;
			}
		}
		icy.gui.viewer.Viewer viewer = seqCamData.getSequence().getFirstViewer();
		if (roisAdded > 0 && viewer != null) {
			seqCamData.displaySpecificROIs(true, "line");
		}
	}

	/**
	 * Saves capillary description and measures for the current experiment.
	 * <p>
	 * Description is written to the results directory, measures to the bin
	 * directory.
	 */
	public boolean save_capillaries_description_and_measures() {
		String resultsDir = getResultsDirectory();

		if (resultsDir == null) {
			Logger.warn("Experiment:save_capillaries_description_and_measures() resultsDir is null");
			return false;
		}

		boolean descriptionsSaved = false;
		if (capillaries.getList().size() > 0) {
			descriptionsSaved = capillaries.getPersistence().saveDescriptions(capillaries, resultsDir);
			if (!descriptionsSaved) {
				Logger.warn("Experiment:save_capillaries_description_and_measures() saveDescriptions failed for "
						+ resultsDir);
			}
		} else {
			Logger.warn(
					"Experiment:save_capillaries_description_and_measures() Skipping save - capillaries list is empty. This may indicate capillaries were not loaded.");
		}

		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			// Check if any measures are actually loaded before saving to prevent
			// overwriting with empty data
			boolean hasMeasures = false;
			for (Capillary cap : capillaries.getList()) {
				if (cap.isThereAnyMeasuresDone(EnumResults.TOPLEVEL)
						|| cap.isThereAnyMeasuresDone(EnumResults.BOTTOMLEVEL)
						|| cap.isThereAnyMeasuresDone(EnumResults.TOPLEVELDIRECT)
						|| cap.isThereAnyMeasuresDone(EnumResults.BOTTOMLEVELDIRECT)
						|| cap.isThereAnyMeasuresDone(EnumResults.DERIVEDVALUES)
						|| cap.isThereAnyMeasuresDone(EnumResults.SUMGULPS)) {
					hasMeasures = true;
					break;
				}
			}

			// Only save measures if they are actually loaded, or use the protected
			// saveCapillariesMeasures method
			if (hasMeasures) {
				capillaries.getPersistence().saveMeasures(capillaries, binDir);
			} else {
				// Use the protected method which will load measures if needed
				saveCapillariesMeasures(binDir);
			}
		}

		return descriptionsSaved;
	}

	public boolean load_FliesPositions() {
		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			return cages.getPersistence().loadMeasures(cages, binDir);
		}
		return false;
	}

	public boolean save_cagesFliesPositions() {
		// Save fly positions to bin directory (e.g.,
		// results/bin60/CagesMeasures.csv)
		String binDir = getKymosBinFullDirectory();
		if (binDir != null) {
			return cages.getPersistence().saveMeasures(cages, binDir);
		}
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
			textList.add(prop.getField(fieldEnumCode));
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
		case CAP_STIM:
		case CAP_CONC:
		case CAP_VOLUME:
			load_capillaries_description_and_measures();
			textList = getCapillariesFieldValues(fieldEnumCode);
			break;
		default:
			break;
		}
		return textList;
	}

	public boolean replaceExperimentFieldIfEqualOldValue(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		boolean flag = prop.getField(fieldEnumCode).equals(oldValue);
		if (flag) {
			prop.setFieldNoTest(fieldEnumCode, newValue);
		}
		return flag;
	}

	public void replaceCapillariesFieldIfEqualOldValue(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		for (Capillary cap : getCapillaries().getList()) {
			String capVal = cap.getField(fieldEnumCode);
			boolean flag = capVal.equals(oldValue);
			if (flag) {
				cap.setField(fieldEnumCode, newValue);
			}
		}
	}

	public String getExperimentField(EnumXLSColumnHeader fieldEnumCode) {
		// Handle non-property fields that are derived from experiment data
		switch (fieldEnumCode) {
		case PATH:
			return getPath();
		case DATE:
			return getDate();
		case CAM:
			return getCam();
		default:
			// Delegate all property fields to ExperimentProperties
			// This keeps property access logic in one place
			return prop.getField(fieldEnumCode);
		}
	}

	private String getPath() {
		String filename = getResultsDirectory();
		if (filename == null)
			filename = seqCamData != null ? seqCamData.getImagesDirectory() : null;
		if (filename == null)
			return "";
		Path path = Paths.get(filename);
		return path.toString();
	}

	private String getDate() {
		if (chainImageFirst_ms <= 0)
			return "";
		java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("MM/dd/yyyy");
		return df.format(chainImageFirst_ms);
	}

	private String getCam() {
		String strField = getPath();
		int pos = strField.indexOf("cam");
		if (pos > 0) {
			int pos5 = pos + 5;
			if (pos5 >= strField.length())
				pos5 = strField.length() - 1;
			strField = strField.substring(pos, pos5);
		}
		return strField;
	}

	public void setExperimentFieldNoTest(EnumXLSColumnHeader fieldEnumCode, String newValue) {
		switch (fieldEnumCode) {
		case EXP_STIM1:
			prop.field_stim1 = newValue;
			break;
		case EXP_CONC1:
			prop.field_conc1 = newValue;
			break;
		case EXP_EXPT:
			setExperiment(newValue);
			break;
		case EXP_BOXID:
			setBoxID(newValue);
			break;
		case EXP_STRAIN:
			setStrain(newValue);
			break;
		case EXP_SEX:
			setSex(newValue);
			break;
		case EXP_STIM2:
			setCondition1(newValue);
			break;
		case EXP_CONC2:
			setCondition2(newValue);
			break;
		default:
			prop.setFieldNoTest(fieldEnumCode, newValue);
			break;
		}
	}

	public boolean replaceExperimentFieldIfEqualOld(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		boolean flag = getExperimentField(fieldEnumCode).equals(oldValue);
		if (flag) {
			setExperimentFieldNoTest(fieldEnumCode, newValue);
		}
		return flag;
	}

	public void copyExperimentFields(Experiment expSource) {
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_BOXID,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_BOXID));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_EXPT,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_EXPT));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STIM1,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_STIM1));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC1,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_CONC1));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STRAIN,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_STRAIN));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_SEX,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_SEX));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_STIM2,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_STIM2));
		setExperimentFieldNoTest(EnumXLSColumnHeader.EXP_CONC2,
				expSource.getExperimentField(EnumXLSColumnHeader.EXP_CONC2));
	}

	public void getFieldValues(EnumXLSColumnHeader fieldEnumCode, List<String> textList) {
		switch (fieldEnumCode) {
		case EXP_STIM1:
		case EXP_CONC1:
		case EXP_EXPT:
		case EXP_BOXID:
		case EXP_STRAIN:
		case EXP_SEX:
		case EXP_STIM2:
		case EXP_CONC2:
			addValue(getExperimentField(fieldEnumCode), textList);
			break;
		case SPOT_STIM:
		case SPOT_CONC:
			addCapillariesValues(fieldEnumCode, textList);
			break;
		default:
			textList.add(prop.getField(fieldEnumCode));
			break;
		}
	}

	private void addValue(String text, List<String> textList) {
		if (!isFound(text, textList))
			textList.add(text);
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
			Logger.debug("Experiment:loadReferenceImage() image not loaded / not found");
			return false;
		}
		seqCamData.setReferenceImage(IcyBufferedImage.createFrom(image));
		seqReference = new Sequence(seqCamData.getReferenceImage());
		seqReference.setName("referenceImage");
		return true;
	}

	public boolean saveReferenceImage(IcyBufferedImage referenceImage) {
		if (referenceImage == null) {
			Logger.error("Cannot save reference image: image is null");
			return false;
		}

		String fullPath = getReferenceImageFullName();
		File outputfile = new File(fullPath);
		File parentDir = outputfile.getParentFile();

		if (parentDir != null && !parentDir.exists()) {
			Logger.info("Creating directory for reference image: " + parentDir.getPath());
			if (!parentDir.mkdirs()) {
				Logger.error("Failed to create directory for reference image: " + parentDir.getPath());
				return false;
			}
		}

		Logger.info("Saving reference image to: " + fullPath);
		RenderedImage image = ImageUtil.toRGBImage(referenceImage);
		boolean success = ImageUtil.save(image, "jpg", outputfile);

		if (!success) {
			Logger.error("Failed to save reference image to: " + fullPath);
		} else {
			Logger.info("Reference image saved successfully to: " + fullPath);
		}

		return success;
	}

	public void cleanPreviousDetectedFliesROIs() {
		if (seqCamData == null || seqCamData.getSequence() == null) {
			return;
		}
		// Remove all ROIs containing "det" (detected fly positions)
		seqCamData.removeROIsContainingString("det");
	}

	public void updateROIsAt(int t) {
		if (seqCamData == null || seqCamData.getSequence() == null)
			return;
		for (ROIsAtTProvider p : getCamRoiProviders()) {
			MeasureRoiSync.updateMeasureROIsAt(t, seqCamData.getSequence(), p.getFilter(), p.getROIsAtT(t));
		}
	}

	private List<ROIsAtTProvider> getCamRoiProviders() {
		List<ROIsAtTProvider> list = new ArrayList<>(2);
		list.add(new ROIsAtTProvider() {
			@Override
			public MeasureRoiFilter getFilter() {
				return MeasureRoiFilter.FLY_POSITION;
			}

			@Override
			public List<ROI2D> getROIsAtT(int t) {
				return cages.getPositionsAsListOfROI2DRectanglesAtT(t);
			}
		});
		list.add(new ROIsAtTProvider() {
			@Override
			public MeasureRoiFilter getFilter() {
				return MeasureRoiFilter.CAPILLARY_LINES;
			}

			@Override
			public List<ROI2D> getROIsAtT(int t) {
				return capillaries.getCapillaryROIsAtT(t);
			}
		});
		return list;
	}

	/**
	 * Central entry point when a viewer's T position changes. Dispatches to cam or
	 * kymo handling: title, optional saveDetRois, and ROI sync for cam; validate
	 * previous frame and syncROIsForCurrentFrame for kymo. If viewOptions is
	 * non-null, applies layer visibility from the DTO after syncing.
	 */
	public void onViewerTPositionChanged(Viewer v, int t, boolean saveDetRoisToPositions) {
		onViewerTPositionChanged(v, t, saveDetRoisToPositions, null);
	}

	public void onViewerTPositionChanged(Viewer v, int t, boolean saveDetRoisToPositions, ViewOptionsDTO viewOptions) {
		if (v == null || v.getSequence() == null)
			return;
		int viewerSeqId = v.getSequence().getId();

		if (seqCamData != null && seqCamData.getSequence() != null && viewerSeqId == seqCamData.getSequence().getId()) {
			v.setTitle(seqCamData.getDecoratedImageName(t));
			if (saveDetRoisToPositions)
				saveDetRoisToPositions();
			updateROIsAt(t);
			if (viewOptions != null)
				applyCamViewOptions(v, viewOptions);
			return;
		}

		if (seqKymos != null && seqKymos.getSequence() != null && viewerSeqId == seqKymos.getSequence().getId()) {
			Sequence seq = seqKymos.getSequence();
			int tOld = seqKymos.getCurrentFrame();
			if (tOld >= 0 && tOld != t) {
				seqKymos.validateRoisAtT(tOld);
				Capillary capOld = seqKymos.getCapillaryForFrame(tOld, capillaries);
				if (capOld != null)
					seqKymos.transferKymosRoi_at_T_To_Capillaries_Measures(tOld, capOld);
			}
			seq.beginUpdate();
			try {
				seqKymos.syncROIsForCurrentFrame(t, capillaries);
			} finally {
				seq.endUpdate();
			}
			if (viewOptions != null)
				applyKymosViewOptions(v, viewOptions);
		}
	}

	private void applyCamViewOptions(Viewer v, ViewOptionsDTO opts) {
		IcyCanvas canvas = v.getCanvas();
		if (canvas == null)
			return;
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null)
			return;
		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null)
				continue;
			String name = roi.getName();
			if (name == null)
				continue;
			if (name.contains("line"))
				layer.setVisible(opts.isViewCapillaries());
			else if (name.contains("cell") || name.contains("cage"))
				layer.setVisible(opts.isViewCages());
			else if (name.contains("det"))
				layer.setVisible(opts.isViewFliesCenter() || opts.isViewFliesRect());
		}
	}

	private void applyKymosViewOptions(Viewer v, ViewOptionsDTO opts) {
		IcyCanvas canvas = v.getCanvas();
		if (canvas == null)
			return;
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null)
			return;
		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null)
				continue;
			String name = roi.getName();
			if (name == null)
				continue;
			if (name.contains("level"))
				layer.setVisible(opts.isViewLevels());
			else if (name.contains("deriv"))
				layer.setVisible(opts.isViewDerivative());
			else if (name.contains("gulp"))
				layer.setVisible(opts.isViewGulps());
		}
	}

	public void saveDetRoisToPositions() {
		List<ROI2D> detectedROIsList = seqCamData.getSequence().getROI2Ds();
		for (Cage cage : cages.cagesList) {
			cage.transferRoisToPositions(detectedROIsList);
		}
	}

	public void updateCapillaryRoisAtT(int t) {
		if (seqCamData == null || seqCamData.getSequence() == null)
			return;

		List<ROI2D> listRois = seqCamData.getSequence().getROI2Ds();
		for (ROI2D roi : listRois) {
			if (roi.getName() == null || !roi.getName().contains("line"))
				continue;
			Capillary cap = capillaries.getCapillaryFromRoiName(roi.getName());
			if (cap != null) {
				AlongT at = cap.getAlongTAtT(t);
				if (at != null)
					at.setRoi((ROI2D) roi.getCopy());
			}
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

	public boolean replaceSpotsFieldValueWithNewValueIfOld(EnumXLSColumnHeader fieldEnumCode, String oldValue,
			String newValue) {
		load_cages_description_and_measures();
		boolean flag = false;
		for (Cage cage : cages.cagesList) {
			List<Spot> spotList = cage.getSpotList(spots);
			for (Spot spot : spotList) {
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
		load_cages_description_and_measures();
		boolean flag = false;
		for (Cage cage : cages.cagesList) {
			String current = cage.getField(fieldEnumCode);
			if (current != null && oldValue != null && current.trim().equals(oldValue.trim())) {
				cage.setField(fieldEnumCode, newValue);
				flag = true;
			}
		}
		return flag;
	}

	private List<String> getSpotsFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		load_cages_description_and_measures();
		List<String> textList = new ArrayList<String>();
		for (Cage cage : cages.cagesList) {
			List<Spot> spotList = cage.getSpotList(spots);
			for (Spot spot : spotList)
				addValueIfUnique(spot.getField(fieldEnumCode), textList);
		}
		return textList;
	}

	private List<String> getCapillariesFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		load_capillaries_description_and_measures();
		List<String> textList = new ArrayList<String>();
		for (Capillary cap : getCapillaries().getList())
			addValueIfUnique(cap.getField(fieldEnumCode), textList);
		return textList;
	}

	private List<String> getCagesFieldValues(EnumXLSColumnHeader fieldEnumCode) {
		load_cages_description_and_measures();
		List<String> textList = new ArrayList<String>();
		for (Cage cage : cages.cagesList)
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
		CagesSequenceMapper.transferROIsToSequence(cages, seqCamData);
	}

	public boolean saveCages_File() {
		CagesSequenceMapper.syncCagesFromSequenceBeforeSave(cages, seqCamData);
		return save_cages_description_and_measures();
	}

	public boolean saveSpots_File() {
		SpotsSequenceMapper.transferROIsFromSequence(spots, seqCamData);
		return save_spots_description_and_measures();
	}

	public void transferSpotsROI_toSequence() {
		SpotsSequenceMapper.transferROIsToSequence(spots, seqCamData);
	}

	public ExperimentProperties getProperties() {
		return prop;
	}

	// ------------------------------ Capillaries Support

	public Capillaries getCapillaries() {
		return capillaries;
	}

	public void setCapillaries(Capillaries capillaries) {
		this.capillaries = capillaries;
	}

	// ------------------------------ Spots Support

	public Spots getSpots() {
		return spots;
	}

	public void setSpots(Spots spotsArray) {
		this.spots = spotsArray != null ? spotsArray : new Spots();
	}

	public SequenceCamData getSeqCamData() {
		if (seqCamData == null)
			seqCamData = new SequenceCamData();
		return seqCamData;
	}

	public void setSeqCamData(SequenceCamData seqCamData) {
		this.seqCamData = seqCamData;
	}

	public SequenceKymos getSeqKymos() {
		if (seqKymos == null)
			seqKymos = new SequenceKymos();
		return seqKymos;
	}

	public void setSeqKymos(SequenceKymos seqKymos) {
		this.seqKymos = seqKymos;
	}

	public String getKymosBinFullDirectory() {
		if (binDirectory == null) {
			return resultsDirectory;
		}
		// Check if binDirectory is already an absolute path
		File binDirFile = new File(binDirectory);
		if (binDirFile.isAbsolute()) {
			return binDirectory;
		}
		// Otherwise, concatenate it to resultsDirectory
		return resultsDirectory + File.separator + binDirectory;
	}

	public String getKymoFullPath(String filename) {
		if (binDirectory == null) {
			return resultsDirectory + File.separator + filename + ".tiff";
		}
		// Check if binDirectory is already an absolute path
		File binDirFile = new File(binDirectory);
		if (binDirFile.isAbsolute()) {
			return binDirectory + File.separator + filename + ".tiff";
		}
		// Otherwise, concatenate it to resultsDirectory
		return resultsDirectory + File.separator + binDirectory + File.separator + filename + ".tiff";
	}

	public String getExperimentDirectory() {
		return resultsDirectory;
	}

	public void setExperimentDirectory(String fileName) {
		resultsDirectory = ExperimentDirectories.getParentIf(fileName, BIN);
	}

	public String getBinSubDirectory() {
		return binDirectory;
	}

	public void setBinSubDirectory(String bin) {
		String oldBinDirectory = binDirectory;
		binDirectory = bin;
		if (bin != null) {
			// Only load from existing directory if:
			// 1. It's the same directory as before (just reloading), OR
			// 2. Interval hasn't been calculated yet (we need to load saved values)
			// If setting a new directory name after interval is calculated, don't load
			// to avoid loading wrong values from existing directories
			boolean shouldLoad = (bin.equals(oldBinDirectory)) || (timeManager.getCamImageBin_ms() < 0);
			if (shouldLoad) {
				loadBinDescription(bin);
			} else {
				// Setting new directory after interval calculated - initialize from current
				// values
				if (activeBinDescription != null) {
					activeBinDescription.setBinDirectory(bin);
					activeBinDescription.setBinKymoColMs(timeManager.getKymoBin_ms());
					activeBinDescription.setFirstKymoColMs(timeManager.getKymoFirst_ms());
					activeBinDescription.setLastKymoColMs(timeManager.getKymoLast_ms());
				}
			}
		}
	}

	/**
	 * Loads bin description from the specified bin directory.
	 * 
	 * @param binSubDirectory the bin subdirectory name (e.g., "bin_60")
	 * @return true if successful
	 */
	public boolean loadBinDescription(String binSubDirectory) {
		if (binSubDirectory == null || resultsDirectory == null) {
			return false;
		}
		// Extract just the subdirectory name if binSubDirectory is a full path
		File binDirFile = new File(binSubDirectory);
		if (binDirFile.isAbsolute()) {
			binSubDirectory = binDirFile.getName();
		}
		String binFullDir = resultsDirectory + File.separator + binSubDirectory;
		boolean loaded = binDescriptionPersistence.load(activeBinDescription, binFullDir);
		if (loaded) {
			activeBinDescription.setBinDirectory(binSubDirectory);
			// Sync with TimeManager for backward compatibility
			timeManager.setKymoFirst_ms(activeBinDescription.getFirstKymoColMs());
			timeManager.setKymoLast_ms(activeBinDescription.getLastKymoColMs());
			// Only sync kymoBin_ms if interval hasn't been calculated from files
			// (camImageBin_ms < 0 means it wasn't calculated from file timestamps)
			if (timeManager.getCamImageBin_ms() < 0) {
				timeManager.setKymoBin_ms(activeBinDescription.getBinKymoColMs());
			} else {
				// Interval was calculated from files, sync activeBinDescription with
				// timeManager instead
				activeBinDescription.setBinKymoColMs(timeManager.getKymoBin_ms());
			}
		} else {
			// If loading failed, initialize from TimeManager (for backward compatibility)
			activeBinDescription.setFirstKymoColMs(timeManager.getKymoFirst_ms());
			activeBinDescription.setLastKymoColMs(timeManager.getKymoLast_ms());
			// Only set from TimeManager if interval has been calculated from files
			// Don't use default 60000 if interval hasn't been calculated yet
			// (camImageBin_ms < 0 means interval wasn't calculated from file timestamps)
			if (timeManager.getCamImageBin_ms() >= 0) {
				// Interval was calculated, safe to use kymoBin_ms
				activeBinDescription.setBinKymoColMs(timeManager.getKymoBin_ms());
			}
			// Otherwise, leave activeBinDescription.binKymoColMs at its default (60000)
			// but this won't be saved unless interval is calculated (guard in
			// saveBinDescription)
			activeBinDescription.setBinDirectory(binSubDirectory);
		}
		return loaded;
	}

	/**
	 * Saves bin description to the specified bin directory.
	 * 
	 * @param binSubDirectory the bin subdirectory name (e.g., "bin_60")
	 * @return true if successful
	 */
	public boolean saveBinDescription(String binSubDirectory) {
		if (binSubDirectory == null || resultsDirectory == null) {
			return false;
		}
		// Extract just the subdirectory name if binSubDirectory is a full path
		File binDirFile = new File(binSubDirectory);
		if (binDirFile.isAbsolute()) {
			binSubDirectory = binDirFile.getName();
		}
		// Get kymo bin value - only save if interval has been calculated or value is
		// explicitly set
		long kymoBinMs = getKymoBin_ms();
		// Don't save with default 60000 if interval hasn't been calculated yet
		// (camImageBin_ms < 0 means interval wasn't calculated from files)
		if (timeManager.getCamImageBin_ms() < 0 && kymoBinMs == 60000 && timeManager.getKymoBin_ms() == 60000) {
			// This is likely the default value, not a real value
			// Don't save yet to avoid creating bin_60 prematurely
			return false;
		}
		// Update activeBinDescription with current values before saving
		activeBinDescription.setFirstKymoColMs(getKymoFirst_ms());
		activeBinDescription.setLastKymoColMs(getKymoLast_ms());
		activeBinDescription.setBinKymoColMs(kymoBinMs);
		activeBinDescription.setBinDirectory(binSubDirectory);

		String binFullDir = resultsDirectory + File.separator + binSubDirectory;
		return binDescriptionPersistence.save(activeBinDescription, binFullDir);
	}

	/**
	 * Saves bin description to the currently active bin directory.
	 * 
	 * @return true if successful
	 */
	public boolean saveBinDescription() {
		return saveBinDescription(binDirectory);
	}

	/**
	 * Gets the number of output frames for the experiment.
	 * 
	 * @param exp     The experiment
	 * @param options The export options
	 * @return The number of output frames
	 */
	public int getNOutputFrames(ResultsOptions options) {
		// For capillaries, use kymograph timing
		long kymoFirst_ms = getKymoFirst_ms();
		long kymoLast_ms = getKymoLast_ms();
		long kymoBin_ms = getKymoBin_ms();

		// If buildExcelStepMs equals kymoBin_ms, we want 1:1 mapping - use actual frame
		// count
		if (kymoBin_ms > 0 && options.buildExcelStepMs == kymoBin_ms && getSeqKymos() != null) {
			ImageLoader imgLoader = getSeqKymos().getImageLoader();
			if (imgLoader != null) {
				int nFrames = imgLoader.getNTotalFrames();
				if (nFrames > 0) {
					return nFrames;
				}
			}
		}

		if (kymoLast_ms <= kymoFirst_ms) {
			// Try to get from kymograph sequence
			if (getSeqKymos() != null) {
				ImageLoader imgLoader = getSeqKymos().getImageLoader();
				if (imgLoader != null) {
					if (kymoBin_ms > 0) {
						kymoLast_ms = kymoFirst_ms + imgLoader.getNTotalFrames() * kymoBin_ms;
						setKymoLast_ms(kymoLast_ms);
					}
				}
			}
		}

		long durationMs = kymoLast_ms - kymoFirst_ms;
		int nOutputFrames = (int) (durationMs / options.buildExcelStepMs + 1);

		if (nOutputFrames <= 1) {
			handleError(-1);
			// Fallback to a reasonable default
			nOutputFrames = 1000;
		}

		return nOutputFrames;
	}

	/**
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleError(int nOutputFrames) {
		String error = String.format(
				"Experiment:GNOutputFrames Error() ERROR in %s\n nOutputFrames=%d kymoFirstCol_Ms=%d kymoLastCol_Ms=%d",
				getExperimentDirectory(), nOutputFrames, getKymoFirst_ms(), getKymoLast_ms());
		System.err.println(error);
	}

	// ------------------------------

	public boolean loadMCCapillaries_Only() {
		// Try to load from CSV first (new format)
		String resultsDir = getResultsDirectory();
		boolean csvLoaded = capillaries.getPersistence().loadDescriptions(capillaries, resultsDir);

		// New format methods already have internal fallback to legacy formats
		boolean flag = csvLoaded;

		// load MCcapillaries description of experiment
		if (prop.field_boxID.contentEquals("..") && prop.field_experiment.contentEquals("..")
				&& prop.field_comment1.contentEquals("..") && prop.field_comment2.contentEquals("..")
				&& prop.field_sex.contentEquals("..") && prop.field_strain.contentEquals("..")) {
			prop.field_boxID = capillaries.getCapillariesDescription().getOld_boxID();
			prop.field_experiment = capillaries.getCapillariesDescription().getOld_experiment();
			prop.field_comment1 = capillaries.getCapillariesDescription().getOld_comment1();
			prop.field_comment2 = capillaries.getCapillariesDescription().getOld_comment2();
			prop.field_sex = capillaries.getCapillariesDescription().getOld_sex();
			prop.field_strain = capillaries.getCapillariesDescription().getOld_strain();
			prop.field_stim2 = capillaries.getCapillariesDescription().getOld_cond1();
			prop.field_conc2 = capillaries.getCapillariesDescription().getOld_cond2();
		}
		return flag;
	}

	// ---------------------------------------------

	public boolean saveMCCapillaries_Only() {
		// XML save disabled - descriptions and ROI coordinates are now stored in CSV
		// String xmlCapillaryFileName = resultsDirectory + File.separator +
		// capillaries.getXMLNameToAppend();
		transferExpDescriptorsToCapillariesDescriptors();
		// return capillaries.xmlSaveCapillaries_Descriptors(xmlCapillaryFileName);
		return true; // Return true since CSV save is handled separately
	}

	private void transferExpDescriptorsToCapillariesDescriptors() {
		CapillariesDescription desc = capillaries.getCapillariesDescription();
		desc.setOld_boxID(prop.field_boxID);
		desc.setOld_experiment(prop.field_experiment);
		desc.setOld_comment1(prop.field_comment1);
		desc.setOld_comment2(prop.field_comment2);
		desc.setOld_strain(prop.field_strain);
		desc.setOld_sex(prop.field_sex);
		desc.setOld_cond1(prop.field_stim2);
		desc.setOld_cond2(prop.field_conc2);
	}

	public boolean loadCagesMeasures() {

		String resultsDir = getResultsDirectory();
		// Try new format: descriptions from results, measures from bin
		boolean descriptionsLoaded = cages.getPersistence().loadDescriptions(cages, resultsDir);
		if (!descriptionsLoaded)
			return descriptionsLoaded;

		String binDir = getKymosBinFullDirectory();
		boolean measuresLoaded = false;
		if (binDir != null) {
			measuresLoaded = cages.getPersistence().loadMeasures(cages, binDir);
		}

		if (measuresLoaded && seqCamData.getSequence() != null) {
			CagesSequenceMapper.transferROIsToSequence(cages, seqCamData);
		}

		// If cages list is empty after loading, create cages from capillaries
		if (cages.getCageList().size() == 0 && capillaries.getList().size() > 0) {
			dispatchCapillariesToCages();
		}

		return measuresLoaded;
	}

	public boolean saveCagesMeasures() {
		String resultsDir = getResultsDirectory();
		boolean descriptionsSaved = Persistence.saveDescription(cages, resultsDir);

		// Also save measures to bin directory (if available)
		String binDir = getKymosBinFullDirectory();
		Logger.debug(binDir);
		if (binDir != null) {
			cages.getPersistence().saveMeasures(cages, binDir);
		}

		return descriptionsSaved;
	}

	public void saveCagesAndMeasures() {
		CagesSequenceMapper.syncCagesFromSequenceBeforeSave(cages, seqCamData);
		saveCagesMeasures();
	}

	public boolean adjustCapillaryMeasuresDimensions() {
		KymographInfo kymoInfo = seqKymos.getKymographInfo();
		if (kymoInfo.getMaxWidth() < 1) {
			kymoInfo = seqKymos.updateMaxDimensionsFromSequence();
			if (kymoInfo.getMaxWidth() < 1)
				return false;
		}
		capillaries.adjustToImageWidth(kymoInfo.getMaxWidth());
		seqKymos.getSequence().removeAllROI();
		CapillariesKymosMapper.pushCapillaryMeasuresToKymos(capillaries, seqKymos);
		return true;
	}

	public boolean cropCapillaryMeasuresDimensions() {
		KymographInfo kymoInfo = seqKymos.getKymographInfo();
		if (kymoInfo.getMaxWidth() < 1) {
			kymoInfo = seqKymos.updateMaxDimensionsFromSequence();
			if (kymoInfo.getMaxWidth() < 1)
				return false;
		}
		int imageWidth = kymoInfo.getMaxWidth();
		capillaries.cropToImageWidth(imageWidth);
		seqKymos.getSequence().removeAllROI();
		CapillariesKymosMapper.pushCapillaryMeasuresToKymos(capillaries, seqKymos);
		return true;
	}

	public boolean saveCapillariesMeasures(String directory) {
		// CRITICAL: Load capillaries if not already loaded to prevent overwriting with
		// empty list
		// This protects against cases where BuildSeries operations (fly detection,
		// background building)
		// load only cages but not capillaries, and then a save operation is triggered
		if (capillaries.getList().isEmpty()) {
			loadMCCapillaries_Only();
			// Also try to load the actual measures if kymos directory exists
			String binDir = getKymosBinFullDirectory();
			if (binDir != null) {
				capillaries.getPersistence().loadMeasures(capillaries, binDir);
			}
		}

		if (seqKymos != null && seqKymos.getSequence() != null) {
			CapillariesKymosMapper.pullCapillaryMeasuresFromKymos(capillaries, seqKymos);
		}
		return capillaries.getPersistence().saveMeasures(capillaries, directory);
	}

	// ---------------------------------------------------------

	public void dispatchCapillariesToCages() {
		if (capillaries.getList().size() < 1)
			return;

		// Clear capillary ID lists in all cages
		for (Cage cage : cages.getCageList()) {
			cage.clearCapillaryList();
		}

		// Dispatch capillaries to cages using ID-based approach
		for (Capillary cap : capillaries.getList()) {
			int kymographIndex = cap.getKymographIndex();
			// Skip capillaries with invalid kymographIndex (not properly loaded from
			// persistence)
			if (kymographIndex < 0) {
				Logger.warn(
						"Experiment.dispatchCapillariesToCages() - Skipping capillary with invalid kymographIndex (-1): "
								+ cap.getKymographName() + ". This may indicate incomplete persistence data.");
				continue;
			}

			int nflies = cap.getProperties().getNFlies();
			int cageID = cap.getCageID();
			Cage cage = cages.getCageFromID(cageID);
			if (cage == null) {
				cage = new Cage();
				cage.getProperties().setCageID(cageID);
				cage.getProperties().setCageNFlies(nflies);
				cages.getCageList().add(cage);
			}
			// Add capillary ID instead of object reference
			CapillaryID capID = new CapillaryID(kymographIndex);
			cage.addCapillaryIDIfUnique(capID);
			// Also update legacy field for backward compatibility
			cage.addCapillaryIfUnique(cap);
		}

		if (cages.getCageList().size() > 0) {
			cages.checkAndCorrectCagePositions();
		}
	}

	public plugins.fmp.multitools.experiment.capillary.CapillaryMeasure getGulpThresholdMeasure() {
		plugins.fmp.multitools.experiment.capillary.CapillaryMeasure t = capillaries.getReferenceMeasures()
				.getDerivativeThreshold();
		if (t != null && t.polylineLevel != null && t.polylineLevel.npoints > 0)
			return t;
		return getGulpThresholdMeasureFromCapillaries();
	}

	private plugins.fmp.multitools.experiment.capillary.CapillaryMeasure getGulpThresholdMeasureFromCapillaries() {
		for (plugins.fmp.multitools.experiment.capillary.Capillary cap : capillaries.getList()) {
			int cageID = cap.getCageID();
			Cage cage = cages.getCageFromID(cageID);
			if (cage != null && cage.getCageNFlies() == 0) {
				plugins.fmp.multitools.experiment.capillary.CapillaryMeasure threshold = cap.getThreshold();
				if (threshold != null && threshold.polylineLevel != null && threshold.polylineLevel.npoints > 0) {
					return threshold;
				}
			}
		}
		return null;
	}

	public void getCapillaryMeasures(EnumResults resultType, boolean subtractEvaporation) {
		ResultsOptions resultsOptions = new ResultsOptions();
		resultsOptions.resultType = resultType;
		resultsOptions.correctEvaporation = subtractEvaporation;
		prepareComputations(resultsOptions);
	}

	public void prepareComputations(ResultsOptions resultsOptions) {
		getCages().prepareComputations(this, resultsOptions);
	}

	public ResultsArray transformDataScalingForOutput(EnumResults resultType, boolean subtractEvaporation) {
		ResultsOptions resultsOptions = new ResultsOptions();
		long kymoBin_ms = getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}
		resultsOptions.buildExcelStepMs = (int) kymoBin_ms;
		resultsOptions.relativeToMaximum = false;
		resultsOptions.correctEvaporation = subtractEvaporation;
		resultsOptions.resultType = resultType;

		ResultsArray resultsArray = new ResultsArray();
		double scalingFactorToPhysicalUnits = getCapillaries().getScalingFactorToPhysicalUnits(resultType);

		ResultsArrayFromCapillaries collectResults = new ResultsArrayFromCapillaries(getCapillaries().getList().size());
		for (Capillary capillary : getCapillaries().getList()) {
			Results results = collectResults.getCapillaryMeasure(this, capillary, resultsOptions);
			if (results != null) {
				results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultType);
				resultsArray.addRow(results);
			}
		}

		return resultsArray;
	}

	// ---------------------------------------------------------

	public boolean loadKymographs() {
		if (getSeqKymos() == null)
			setSeqKymos(new SequenceKymos());

		List<ImageFileData> myList = new KymographService()
				.loadListOfPotentialKymographsFromCapillaries(getKymosBinFullDirectory(), capillaries);

		// Filter to get existing file names
		ImageFileData.getExistingFileNames(myList);

		// Convert to experiment1 ImageFileDescriptor format
		ArrayList<ImageFileData> newList = new ArrayList<ImageFileData>();
		for (ImageFileData oldDesc : myList) {
			if (oldDesc.fileName != null && oldDesc.exists) {
				ImageFileData newDesc = new ImageFileData();
				newDesc.fileName = oldDesc.fileName;
				newDesc.exists = oldDesc.exists;
				newDesc.imageHeight = oldDesc.imageHeight;
				newDesc.imageWidth = oldDesc.imageWidth;
				newList.add(newDesc);
			}
		}

		// Load images using the new API
		Rectangle rectMax = getSeqKymos().calculateMaxDimensions(newList);
		ImageAdjustmentOptions options = ImageAdjustmentOptions.withSizeAdjustment(rectMax);
		ImageProcessingResult result = getSeqKymos().loadKymographs(newList, options);
		return result.isSuccess();
	}

	public boolean loadCamDataCapillaries() {
		if (resultsDirectory == null) {
			return false;
		}
		boolean flag = loadMCCapillaries_Only();
		// Transfer ROIs if capillaries exist, even if descriptions didn't load
		// (capillaries might have been loaded from XML files directly)
		if (capillaries.getList().size() > 0 && seqCamData != null && seqCamData.getSequence() != null) {
			// Remove only capillary ROIs (containing "line"), preserving cages and other
			// ROIs
			seqCamData.removeROIsContainingString("line");
			// Add capillary ROIs to sequence
			for (Capillary cap : capillaries.getList()) {
				if (cap.getRoi() != null) {
					seqCamData.getSequence().addROI(cap.getRoi());
				}
			}
		}
		return flag;
	}

	private void addCapillariesValues(EnumXLSColumnHeader fieldEnumCode, List<String> textList) {
		if (capillaries.getList().size() == 0)
			loadMCCapillaries_Only();
		for (Capillary cap : capillaries.getList())
			addValueIfUnique(cap.getCapillaryField(fieldEnumCode), textList);
	}

}
