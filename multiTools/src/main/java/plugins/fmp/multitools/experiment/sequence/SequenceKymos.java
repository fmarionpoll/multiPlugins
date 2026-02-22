package plugins.fmp.multitools.experiment.sequence;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import icy.common.exception.UnsupportedFormatException;
import icy.file.Loader;
import icy.file.Saver;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import icy.type.geom.Polyline2D;
import loci.formats.FormatException;
import ome.xml.meta.OMEXMLMetadata;
import plugins.fmp.multitools.experiment.EnumStatus;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesKymosMapper;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.MeasureRoiSync.MeasureRoiFilter;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Comparators;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;

/**
 * Extended SequenceCamData for handling kymograph-specific operations.
 * 
 * <p>
 * This class provides specialized functionality for kymograph sequence
 * management:
 * <ul>
 * <li>Loading and processing kymograph images</li>
 * <li>ROI validation and interpolation</li>
 * <li>Image size adjustment and normalization</li>
 * <li>Batch processing of multiple kymographs</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * SequenceKymos kymo = SequenceKymos.builder()
 *     .withConfiguration(KymographConfiguration.qualityProcessing())
 *     .withImageList(imageList)
 *     .build();
 * 
 * try (kymo) {
 *     ImageProcessingResult result = kymo.loadKymographs(imageDescriptors);
 *     KymographInfo info = kymo.getKymographInfo();
 *     // ... work with kymographs
 * }
 * }</pre>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 1.0
 */
public class SequenceKymos extends SequenceCamData {

	// === CORE FIELDS ===
	private final ReentrantLock processingLock = new ReentrantLock();
	private volatile boolean isLoadingImages = false;
	private volatile int maxImageWidth = 0;
	private volatile int maxImageHeight = 0;
	private KymographConfiguration configuration;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new SequenceKymos with default configuration.
	 */
	public SequenceKymos() {
		super();
		this.configuration = KymographConfiguration.defaultConfiguration();
		setStatus(EnumStatus.KYMOGRAPH);
		setCurrentFrame(-1);
	}

	/**
	 * Creates a new SequenceKymos with specified name and initial image.
	 * 
	 * @param name  the sequence name, must not be null or empty
	 * @param image the initial image, must not be null
	 * @throws IllegalArgumentException if name is null/empty or image is null
	 */
	public SequenceKymos(String name, IcyBufferedImage image) {
		super(name, image);
		this.configuration = KymographConfiguration.defaultConfiguration();
		setStatus(EnumStatus.KYMOGRAPH);
		setCurrentFrame(-1);
	}

	/**
	 * Creates a new SequenceKymos with specified image list.
	 * 
	 * @param imageNames the list of image names, must not be null or empty
	 * @throws IllegalArgumentException if imageNames is null or empty
	 */
	public SequenceKymos(List<String> imageNames) {
		super();
		if (imageNames == null || imageNames.isEmpty()) {
			throw new IllegalArgumentException("Image names list cannot be null or empty");
		}
		this.configuration = KymographConfiguration.defaultConfiguration();
		setCurrentFrame(-1);
		List<String> convertedNames = new KymographService().convertLinexLRFileNames(imageNames);
		setImagesList(convertedNames);
		setStatus(EnumStatus.KYMOGRAPH);
	}

	/**
	 * Creates a builder for constructing SequenceKymos instances.
	 * 
	 * @return a new builder instance
	 */
	public static Builder kymographBuilder() {
		return new Builder();
	}

	// === KYMOGRAPH OPERATIONS ===

	/**
	 * Gets comprehensive kymograph information.
	 * 
	 * @return kymograph information object
	 */
	public KymographInfo getKymographInfo() {
		if (getSequence() == null) {
			throw new IllegalStateException("Sequence is not initialized");
		}
		processingLock.lock();
		try {
			List<String> imageNames = getImagesList();
			return KymographInfo.builder().totalImages(imageNames.size()).maxWidth(maxImageWidth)
					.maxHeight(maxImageHeight).validImages(countValidImages(imageNames))
					.invalidImages(countInvalidImages(imageNames)).isLoading(isLoadingImages).imageNames(imageNames)
					.build();
		} finally {
			processingLock.unlock();
		}
	}

	/**
	 * Validates and processes ROIs in the sequence.
	 * 
	 * @return processing result
	 */
	public ImageProcessingResult validateROIs() {
		if (getSequence() == null) {
			return ImageProcessingResult.failure(new IllegalStateException("Sequence is not initialized"),
					"Cannot validate ROIs: sequence is not initialized");
		}

		processingLock.lock();
		try {
			long startTime = System.currentTimeMillis();
			int processed = 0;
			int failed = 0;

			List<ROI2D> roiList = getSequence().getROI2Ds();
			int sequenceWidth = getSequence().getWidth();

			for (ROI2D roi : roiList) {
				if (!(roi instanceof ROI2DPolyLine)) {
					continue;
				}

				try {
					if (roi.getName() != null && roi.getName().contains("level")) {
						ROI2DUtilities.interpolateMissingPointsAlongXAxis((ROI2DPolyLine) roi, sequenceWidth);
						processed++;
						continue;
					}
					if (roi.getName() != null && roi.getName().contains("derivative")) {
						continue;
					}
					if (roi.getName() != null && roi.getName().contains("gulp")) {
						// Gulps are vertical segments; do not interpolate along X
						continue;
					}
					// if gulp not found - add an index to it
					ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
					Polyline2D line = roiLine.getPolyline2D();
					roi.setName("gulp" + String.format("%07d", (int) line.xpoints[0]));
					roi.setColor(Color.red);

				} catch (Exception e) {
					Logger.warn("Failed to process ROI: " + roi.getName(), e);
					failed++;
				}
			}

			// Sort ROIs by name
			Collections.sort(roiList, new Comparators.ROI2D_Name());

			long processingTime = System.currentTimeMillis() - startTime;

			return ImageProcessingResult.builder().success(failed == 0).processedCount(processed).failedCount(failed)
					.processingTimeMs(processingTime)
					.message(String.format("Processed %d ROIs, %d failed", processed, failed)).build();

		} finally {
			processingLock.unlock();
		}
	}

	public void validateRoisAtT(int t) {
		List<ROI2D> listRois = getSequence().getROI2Ds();
		int width = getSequence().getWidth();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() == -1)
				roi.setT(t);
			if (roi.getT() != t)
				continue;
			// interpolate or expand to full width so transfer back to capillary keeps length
			if (roi.getName().contains("level") || roi.getName().contains("derivative")) {
				ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
				Polyline2D line = roiLine.getPolyline2D();
				if (line != null && line.npoints > 0 && line.npoints < width) {
					Level2D expanded = new Level2D(line).expandPolylineToNewWidth(width);
					roiLine.setPolyline2D(expanded);
				} else {
					ROI2DUtilities.interpolateMissingPointsAlongXAxis(roiLine, width);
				}
				continue;
			}
			if (roi.getName().contains("gulp")) {
				// Gulps are vertical segments (same x); do not interpolate along X or amplitude is lost
				continue;
			}
			// if gulp not found - add an index to it
			ROI2DPolyLine roiLine = (ROI2DPolyLine) roi;
			Polyline2D line = roiLine.getPolyline2D();
			roi.setName("gulp" + String.format("%07d", (int) line.xpoints[0]));
			roi.setColor(Color.red);
		}
		Collections.sort(listRois, new Comparators.ROI2D_Name());
	}

	public void removeROIsPolylineAtT(int t) {
		List<ROI2D> listRois = getSequence().getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() == t)
				getSequence().removeROI(roi);
		}
	}

	public void updateROIFromCapillaryMeasure(Capillary cap, CapillaryMeasure caplimits) {
		int t = cap.getKymographIndex();
		List<ROI2D> listRois = getSequence().getROI2Ds();
		for (ROI2D roi : listRois) {
			if (!(roi instanceof ROI2DPolyLine))
				continue;
			if (roi.getT() != t)
				continue;
			if (!roi.getName().contains(caplimits.capName))
				continue;

			((ROI2DPolyLine) roi).setPolyline2D(caplimits.polylineLevel);
			roi.setName(caplimits.capName);
			getSequence().roiChanged(roi);
			break;
		}
	}

	public boolean transferKymosRoisToCapillaries_Measures(Capillaries capillaries) {
		List<ROI> allRois = getSequence().getROIs();
		if (allRois.size() < 1)
			return false;

		for (int kymo = 0; kymo < getSequence().getSizeT(); kymo++) {
			List<ROI> roisAtT = new ArrayList<ROI>();
			for (ROI roi : allRois) {
				if (roi instanceof ROI2D && ((ROI2D) roi).getT() == kymo)
					roisAtT.add(roi);
			}
			if (capillaries.getList().size() <= kymo) {
				capillaries.getList().add(new Capillary());
			}

//			Capillary cap = capillaries.getList().get(kymo);
//			cap.transferROIsToMeasures(roisAtT);
			final int i = kymo;
			Capillary cap = capillaries.getList().stream().filter(c -> c.getKymographIndex() == i).findFirst()
					.orElse(null);
			if (cap != null) {
				cap.transferROIsToMeasures(roisAtT);
			}
		}
		return true;
	}

	public boolean transferKymosRoi_atT_ToCapillaries_Measures(int t, Capillary cap) {
		List<ROI> allRois = getSequence().getROIs();
		if (allRois.size() < 1)
			return false;

		List<ROI> roisAtT = new ArrayList<ROI>();
		for (ROI roi : allRois) {
			if (roi instanceof ROI2D && ((ROI2D) roi).getT() == t)
				roisAtT.add(roi);
		}
		cap.transferROIsToMeasures(roisAtT);

		return true;
	}

	public void transferCapillariesMeasuresToKymos(Capillaries capillaries) {
		// Remove existing measure ROIs to prevent duplication
		// Measure ROIs have names like "prefix_toplevel", "prefix_bottomlevel",
		// "prefix_derivative", "prefix_gulps"
		List<ROI2D> allROIs = getSequence().getROI2Ds();
		List<ROI2D> roisToRemove = new ArrayList<ROI2D>();
		for (ROI2D roi : allROIs) {
			if (roi instanceof ROI2DPolyLine && roi.getName() != null) {
				String name = roi.getName();
				// Check if this ROI matches the pattern of measure ROIs
				if (name.contains("_") && (name.contains("toplevel") || name.contains("bottomlevel")
						|| name.contains("derivative") || name.contains("gulps"))) {
					roisToRemove.add(roi);
				}
			}
		}
		if (!roisToRemove.isEmpty()) {
			getSequence().removeROIs(roisToRemove, false);
		}

		List<ROI2D> newRoisList = new ArrayList<ROI2D>();
		int ncapillaries = capillaries.getList().size();
		List<String> imagesList = getImagesList();

		for (int i = 0; i < ncapillaries; i++) {
			List<ROI2D> listOfRois = capillaries.getList().get(i).transferMeasuresToROIs(imagesList);
			if (listOfRois != null) {
				newRoisList.addAll(listOfRois);
			}
		}

		getSequence().addROIs(newRoisList, false);
	}

	/**
	 * Resolves the capillary for kymograph frame t. The t-th frame shows the t-th
	 * image in the list; we resolve by that image's filename first (source of
	 * truth), then by list index, then by kymographIndex.
	 */
	public Capillary getCapillaryForFrame(int t, Capillaries capillaries) {
		if (capillaries == null || t < 0)
			return null;
		String path = getFileNameFromImageList(t);
		if (path != null) {
			String baseName = new File(path).getName();
			int lastDot = baseName.lastIndexOf('.');
			if (lastDot > 0)
				baseName = baseName.substring(0, lastDot);
			Capillary cap = capillaries.getCapillaryFromKymographName(baseName);
			if (cap != null)
				return cap;
			String displayName = baseName.replaceAll("1$", "L").replaceAll("2$", "R");
			cap = capillaries.getCapillaryFromKymographName(displayName);
			if (cap != null)
				return cap;
			String numericName = Capillary.replace_LR_with_12(baseName);
			if (!numericName.equals(baseName)) {
				cap = capillaries.getCapillaryFromKymographName(numericName);
				if (cap != null)
					return cap;
			}
			String prefix = prefixFromKymographBaseName(baseName);
			if (prefix != null) {
				cap = capillaries.getCapillaryFromRoiNamePrefix(prefix);
				if (cap != null)
					return cap;
			}
		}
		List<Capillary> list = capillaries.getList();
		if (t < list.size())
			return list.get(t);
		return capillaries.getCapillaryAtT(t);
	}

	private static String prefixFromKymographBaseName(String baseName) {
		if (baseName == null || baseName.length() < 6 || !baseName.startsWith("line"))
			return null;
		String number = baseName.substring(4, baseName.length() - 1);
		String last = baseName.substring(baseName.length() - 1);
		if ("1".equals(last))
			return number + "L";
		if ("2".equals(last))
			return number + "R";
		return baseName.substring(baseName.length() - 2);
	}

	/**
	 * Syncs the sequence so only capillary measure ROIs for frame t are present
	 * (same pattern as Experiment.updateROIsAt for fly positions). No-op if t
	 * equals current frame to avoid redundant work and repeated calls from
	 * viewer/combo events.
	 */
	public void syncROIsForCurrentFrame(int t, Capillaries capillaries) {
		if (getSequence() == null || capillaries == null)
			return;
		if (t == getCurrentFrame())
			return;
		Capillary cap = getCapillaryForFrame(t, capillaries);
		List<ROI2D> roisForT = null;
		if (cap != null) {
			roisForT = cap.transferMeasuresToROIs(getImagesList());
			if (roisForT != null)
				for (ROI2D roi : roisForT)
					roi.setT(t);
		}
		MeasureRoiSync.updateMeasureROIsAt(t, getSequence(), MeasureRoiFilter.CAPILLARY_MEASURES, roisForT);
		setCurrentFrame(t);
		icy.gui.viewer.Viewer v = getSequence().getFirstViewer();
		if (v != null && v.getCanvas() != null)
			v.getCanvas().refresh();
	}

	public void saveKymosCurvesToCapillariesMeasures(Experiment exp) {
		if (exp == null) {
			return;
		}
		CapillariesKymosMapper.pullCapillaryMeasuresFromKymos(exp.getCapillaries(), exp.getSeqKymos());
		exp.save_capillaries_description_and_measures();
	}

	/**
	 * Loads kymograph images from descriptors with specified options.
	 * 
	 * @param imageDescriptors  the image descriptors
	 * @param adjustmentOptions the adjustment options
	 * @return processing result
	 */
	public ImageProcessingResult loadKymographs(List<ImageFileData> imageDescriptors,
			ImageAdjustmentOptions adjustmentOptions) {
		if (imageDescriptors == null) {
			throw new IllegalArgumentException("Image descriptors cannot be null");
		}
		if (adjustmentOptions == null) {
			throw new IllegalArgumentException("Adjustment options cannot be null");
		}

//		if (getSequence() == null) {
//			throw new IllegalStateException("Sequence is not initialized");
//		}
		processingLock.lock();
		try {
			isLoadingImages = true;
			long startTime = System.currentTimeMillis();

			if (imageDescriptors.isEmpty()) {
				return ImageProcessingResult.success(0, "No images to process");
			}

			// Process image dimensions if size adjustment is needed
			if (adjustmentOptions.isAdjustSize()) {
				Rectangle maxDimensions = calculateMaxDimensions(imageDescriptors);
				ImageProcessingResult adjustResult = adjustImageSizes(imageDescriptors, maxDimensions,
						adjustmentOptions);
				if (!adjustResult.isSuccess()) {
					return adjustResult;
				}
			}

			// Create list of valid image files
			List<String> validImageFiles = extractValidImageFiles(imageDescriptors);

			if (validImageFiles.isEmpty()) {
				return ImageProcessingResult.failure(new IllegalStateException("No valid image files found"),
						"No valid images to load");
			}

			// Load images
			setStatus(EnumStatus.KYMOGRAPH);
			List<String> acceptedFiles = ExperimentDirectories.keepOnlyAcceptedNames_List(validImageFiles,
					configuration.getAcceptedFileExtensions().toArray(new String[0]));

			loadImageList(acceptedFiles);
			setSequenceNameFromFirstImage(acceptedFiles);
			setStatus(EnumStatus.KYMOGRAPH);
			if (getSequence() != null)
				getSequence().endUpdate();

			long processingTime = System.currentTimeMillis() - startTime;

			return ImageProcessingResult.builder().success(true).processedCount(acceptedFiles.size())
					.processingTimeMs(processingTime)
					.message(String.format("Successfully loaded %d kymograph images", acceptedFiles.size())).build();

		} catch (Exception e) {
			Logger.error("Failed to load kymographs", e);
			return ImageProcessingResult.failure(e, "Failed to load kymographs: " + e.getMessage());
		} finally {
			isLoadingImages = false;
			processingLock.unlock();
		}
	}

	/**
	 * Loads kymograph images from descriptors with default options.
	 * 
	 * @param imageDescriptors the image descriptors
	 * @return processing result
	 */
	public ImageProcessingResult loadKymographs(List<ImageFileData> imageDescriptors) {
		return loadKymographs(imageDescriptors, ImageAdjustmentOptions.defaultOptions());
	}

	/**
	 * Creates a list of potential kymograph files from spots in cages.
	 * 
	 * @param baseDirectory the base directory
	 * @param cages         the cages array
	 * @return list of image file descriptors
	 */
	public List<ImageFileData> createKymographFileList(String baseDirectory, Cages cages, Spots allSpots) {
		if (baseDirectory == null || baseDirectory.trim().isEmpty()) {
			throw new IllegalArgumentException("Base directory cannot be null or empty");
		}
		if (cages == null) {
			throw new IllegalArgumentException("Cages array cannot be null");
		}
		if (allSpots == null) {
			return new ArrayList<>();
		}

		processingLock.lock();
		try {
			String fullDirectory = baseDirectory + File.separator;

			if (cages.cagesList.isEmpty()) {
				Logger.warn("No cages found in cages array");
				return new ArrayList<>();
			}

			Cage firstCage = cages.cagesList.get(0);
			List<Spot> firstCageSpots = firstCage.getSpotList(allSpots);
			if (firstCageSpots.isEmpty()) {
				Logger.warn("No spots found in first cage");
				return new ArrayList<>();
			}

			// Calculate total expected files
			int totalExpectedFiles = cages.cagesList.size() * firstCageSpots.size();
			List<ImageFileData> fileList = new ArrayList<>(totalExpectedFiles);

			// Generate file descriptors for each spot in each cage
			for (Cage cage : cages.cagesList) {
				List<Spot> spots = cage.getSpotList(allSpots);
				if (spots.isEmpty())
					continue;

				for (Spot spot : spots) {
					ImageFileData descriptor = new ImageFileData();
					descriptor.fileName = fullDirectory + spot.getRoi().getName() + ".tiff";
					descriptor.exists = new File(descriptor.fileName).exists();
					fileList.add(descriptor);
				}
			}
			return fileList;

		} finally {
			processingLock.unlock();
		}
	}

	// === CONFIGURATION ===

	/**
	 * Updates the kymograph configuration.
	 * 
	 * @param configuration the new configuration
	 */
	public void updateConfiguration(KymographConfiguration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Configuration cannot be null");
		}

		processingLock.lock();
		try {
			this.configuration = configuration;
		} finally {
			processingLock.unlock();
		}
	}

	/**
	 * Gets the current kymograph configuration.
	 * 
	 * @return the current configuration
	 */
	public KymographConfiguration getConfiguration() {
		return configuration;
	}

	// === LEGACY METHODS (for backward compatibility) ===

	/**
	 * @deprecated Use {@link #validateROIs()} instead
	 */
	@Deprecated
	public void validateRois() {
		validateROIs();
	}

	/**
	 * @deprecated Use {@link #createKymographFileList(String, Cages, Spots)}
	 *             instead
	 */
	@Deprecated
	public List<ImageFileData> loadListOfPotentialKymographsFromSpots(String dir, Cages cages, Spots allSpots) {
		return createKymographFileList(dir, cages, allSpots);
	}

	/**
	 * @deprecated Use {@link #loadKymographs(List, ImageAdjustmentOptions)} instead
	 */
	@Deprecated
	public boolean loadKymographImagesFromList(List<ImageFileData> kymoImagesDesc, boolean adjustImagesSize) {
		ImageAdjustmentOptions options = adjustImagesSize
				? ImageAdjustmentOptions.withSizeAdjustment(calculateMaxDimensions(kymoImagesDesc))
				: ImageAdjustmentOptions.noAdjustment();

		ImageProcessingResult result = loadKymographs(kymoImagesDesc, options);
		return result.isSuccess();
	}

	// === ACCESSORS ===

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public boolean isRunning_loadImages() {
//		return isLoadingImages;
//	}

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public void setRunning_loadImages(boolean isRunning) {
//		isLoadingImages = isRunning;
//	}

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public int getImageWidthMax() {
//		return maxImageWidth;
//	}

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public void setImageWidthMax(int maxImageWidth) {
//		this.maxImageWidth = maxImageWidth;
//	}

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public int getImageHeightMax() {
//		return maxImageHeight;
//	}

//	/**
//	 * @deprecated Use {@link #getKymographInfo()} instead
//	 */
//	@Deprecated
//	public void setImageHeightMax(int maxImageHeight) {
//		this.maxImageHeight = maxImageHeight;
//	}

	/**
	 * Updates the maximum dimensions from the current sequence dimensions. This is
	 * useful when the sequence has been loaded and dimensions need to be
	 * synchronized.
	 * 
	 * @return updated KymographInfo with the new dimensions
	 * @throws IllegalStateException if sequence is not initialized
	 */
	public KymographInfo updateMaxDimensionsFromSequence() {
		if (getSequence() == null) {
			throw new IllegalStateException("Sequence is not initialized");
		}
		processingLock.lock();
		try {
			maxImageWidth = getSequence().getSizeX();
			maxImageHeight = getSequence().getSizeY();
			return getKymographInfo();
		} finally {
			processingLock.unlock();
		}
	}

	// === PRIVATE HELPER METHODS ===

	/**
	 * Calculates the maximum dimensions from a list of image descriptors.
	 * 
	 * @param imageDescriptors the image descriptors
	 * @return rectangle representing maximum dimensions
	 */
	public Rectangle calculateMaxDimensions(List<ImageFileData> imageDescriptors) {
		int maxWidth = 0;
		int maxHeight = 0;

		for (ImageFileData descriptor : imageDescriptors) {
			if (!descriptor.exists)
				continue;

			try {
				updateImageDimensions(descriptor);
				maxWidth = Math.max(maxWidth, descriptor.imageWidth);
				maxHeight = Math.max(maxHeight, descriptor.imageHeight);
			} catch (Exception e) {
				Logger.warn("Failed to get dimensions for: " + descriptor.fileName, e);
			}
		}

		maxImageWidth = maxWidth;
		maxImageHeight = maxHeight;

		return new Rectangle(0, 0, maxWidth, maxHeight);
	}

	/**
	 * Updates image dimensions for a file descriptor.
	 * 
	 * @param descriptor the file descriptor
	 * @throws Exception if dimensions cannot be retrieved
	 */
	private void updateImageDimensions(ImageFileData descriptor) throws Exception {
		try {
			OMEXMLMetadata metadata = Loader.getOMEXMLMetaData(descriptor.fileName);
			descriptor.imageWidth = MetaDataUtil.getSizeX(metadata, 0);
			descriptor.imageHeight = MetaDataUtil.getSizeY(metadata, 0);
		} catch (UnsupportedFormatException | IOException | InterruptedException e) {
			throw new Exception("Failed to get image dimensions for: " + descriptor.fileName, e);
		}
	}

	/**
	 * Adjusts image sizes according to target dimensions.
	 * 
	 * @param imageDescriptors the image descriptors
	 * @param targetDimensions the target dimensions
	 * @param options          the adjustment options
	 * @return processing result
	 */
	private ImageProcessingResult adjustImageSizes(List<ImageFileData> imageDescriptors, Rectangle targetDimensions,
			ImageAdjustmentOptions options) {
		if (!options.isAdjustSize()) {
			return ImageProcessingResult.success(0, "Size adjustment disabled");
		}

		long startTime = System.currentTimeMillis();
		int processed = 0;
		int failed = 0;
		List<String> failedFiles = new ArrayList<>();

		ProgressFrame progress = null;
		if (options.isShowProgress()) {
			progress = new ProgressFrame(options.getProgressMessage());
			progress.setLength(imageDescriptors.size());
		}

		try {
			for (ImageFileData descriptor : imageDescriptors) {
				if (!descriptor.exists)
					continue;

				if (progress != null) {
					progress.setMessage("Adjusting: " + descriptor.fileName);
				}

				try {
					if (descriptor.imageWidth == targetDimensions.width
							&& descriptor.imageHeight == targetDimensions.height) {
						processed++;
						continue;
					}

					adjustSingleImage(descriptor, targetDimensions);
					processed++;

				} catch (Exception e) {
					Logger.warn("Failed to adjust image: " + descriptor.fileName, e);
					failed++;
					failedFiles.add(descriptor.fileName);
				}

				if (progress != null) {
					progress.incPosition();
				}
			}

			long processingTime = System.currentTimeMillis() - startTime;

			return ImageProcessingResult.builder().success(failed == 0).processedCount(processed).failedCount(failed)
					.failedFiles(failedFiles).processingTimeMs(processingTime)
					.message(String.format("Adjusted %d images, %d failed", processed, failed)).build();

		} finally {
			if (progress != null) {
				progress.close();
			}
		}
	}

	/**
	 * Adjusts a single image to target dimensions.
	 * 
	 * @param descriptor       the image descriptor
	 * @param targetDimensions the target dimensions
	 * @throws Exception if adjustment fails
	 */
	private void adjustSingleImage(ImageFileData descriptor, Rectangle targetDimensions) throws Exception {
		try {
			// Load source image
			IcyBufferedImage sourceImage = Loader.loadImage(descriptor.fileName);

			// Create target image with new dimensions
			IcyBufferedImage targetImage = new IcyBufferedImage(targetDimensions.width, targetDimensions.height,
					sourceImage.getSizeC(), sourceImage.getDataType_());

			// Transfer image data
			transferImageData(sourceImage, targetImage);

			// Save adjusted image
			Saver.saveImage(targetImage, new File(descriptor.fileName), true);

		} catch (UnsupportedFormatException | IOException | InterruptedException | FormatException e) {
			throw new Exception("Failed to adjust image: " + descriptor.fileName, e);
		}
	}

	/**
	 * Transfers image data from source to destination.
	 * 
	 * @param source      the source image
	 * @param destination the destination image
	 */
	private void transferImageData(IcyBufferedImage source, IcyBufferedImage destination) {
		final int sourceHeight = source.getSizeY();
		final int channelCount = source.getSizeC();
		final int sourceWidth = source.getSizeX();
		final int destinationWidth = destination.getSizeX();
		final DataType dataType = source.getDataType_();
		final boolean signed = dataType.isSigned();

		destination.lockRaster();
		try {
			for (int channel = 0; channel < channelCount; channel++) {
				final Object sourceData = source.getDataXY(channel);
				final Object destinationData = destination.getDataXY(channel);

				int sourceOffset = 0;
				int destinationOffset = 0;

				for (int y = 0; y < sourceHeight; y++) {
					Array1DUtil.arrayToArray(sourceData, sourceOffset, destinationData, destinationOffset, sourceWidth,
							signed);
					destination.setDataXY(channel, destinationData);
					sourceOffset += sourceWidth;
					destinationOffset += destinationWidth;
				}
			}
		} finally {
			destination.releaseRaster(true);
		}
		destination.dataChanged();
	}

	/**
	 * Extracts valid image files from descriptors.
	 * 
	 * @param descriptors the image descriptors
	 * @return list of valid image file paths
	 */
	private List<String> extractValidImageFiles(List<ImageFileData> descriptors) {
		List<String> validFiles = new ArrayList<>();
		for (ImageFileData descriptor : descriptors) {
			if (descriptor.exists) {
				validFiles.add(descriptor.fileName);
			}
		}
		return validFiles;
	}

	/**
	 * Counts valid images in the list.
	 * 
	 * @param imageNames the image names
	 * @return count of valid images
	 */
	private int countValidImages(List<String> imageNames) {
		int count = 0;
		for (String imageName : imageNames) {
			if (new File(imageName).exists()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Counts invalid images in the list.
	 * 
	 * @param imageNames the image names
	 * @return count of invalid images
	 */
	private int countInvalidImages(List<String> imageNames) {
		return imageNames.size() - countValidImages(imageNames);
	}

	/**
	 * Sets the sequence name from the first image file.
	 * 
	 * @param imageFiles the image files
	 */
	private void setSequenceNameFromFirstImage(List<String> imageFiles) {
		if (imageFiles.isEmpty())
			return;

		try {
			Path imagePath = Paths.get(imageFiles.get(0));
			if (imagePath.getNameCount() >= 2) {
				String sequenceName = imagePath.getName(imagePath.getNameCount() - 2).toString();
				getSequence().setName(sequenceName);
			}
		} catch (Exception e) {
			Logger.warn("Failed to set sequence name from first image", e);
		}
	}

	// === BUILDER PATTERN ===

	/**
	 * Builder for creating SequenceKymos instances.
	 */
	public static class Builder {
		private String name;
		private IcyBufferedImage image;
		private List<String> imageNames;
		private KymographConfiguration configuration = KymographConfiguration.defaultConfiguration();

		/**
		 * Sets the sequence name.
		 * 
		 * @param name the sequence name
		 * @return this builder
		 */
		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Sets the initial image.
		 * 
		 * @param image the initial image
		 * @return this builder
		 */
		public Builder withImage(IcyBufferedImage image) {
			this.image = image;
			return this;
		}

		/**
		 * Sets the image names list.
		 * 
		 * @param imageNames the image names
		 * @return this builder
		 */
		public Builder withImageList(List<String> imageNames) {
			this.imageNames = imageNames;
			return this;
		}

		/**
		 * Sets the kymograph configuration.
		 * 
		 * @param configuration the configuration
		 * @return this builder
		 */
		public Builder withConfiguration(KymographConfiguration configuration) {
			this.configuration = configuration;
			return this;
		}

		/**
		 * Builds the SequenceKymos instance.
		 * 
		 * @return a new SequenceKymos instance
		 */
		public SequenceKymos build() {
			SequenceKymos sequence;

			if (name != null && image != null) {
				sequence = new SequenceKymos(name, image);
			} else if (imageNames != null && !imageNames.isEmpty()) {
				sequence = new SequenceKymos(imageNames);
			} else {
				sequence = new SequenceKymos();
			}

			sequence.updateConfiguration(configuration);
			return sequence;
		}
	}

}
