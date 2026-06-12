package plugins.fmp.multitools.experiment.sequence;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import icy.common.exception.UnsupportedFormatException;
import icy.file.Loader;
import icy.file.Saver;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import ome.xml.meta.OMEXMLMetadata;
import plugins.fmp.multitools.experiment.EnumStatus;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.cages.Cages;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Logger;

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
	private final CapillaryKymographMeasureBridge capillaryMeasureBridge = new CapillaryKymographMeasureBridge(this);
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
			return capillaryMeasureBridge.validateROIs();
		} finally {
			processingLock.unlock();
		}
	}

	public void validateRoisAtT(int t) {
		capillaryMeasureBridge.validateRoisAtT(t);
	}

	public void removeROIsPolylineAtT(int t) {
		capillaryMeasureBridge.removeROIsPolylineAtT(t);
	}

	public void updateROIFromCapillaryMeasure(Capillary cap, CapillaryMeasure caplimits) {
		capillaryMeasureBridge.updateROIFromCapillaryMeasure(cap, caplimits);
	}

	public boolean transferKymosRoisToCapillaries_Measures(Capillaries capillaries) {
		return capillaryMeasureBridge.transferKymosRoisToCapillaries_Measures(capillaries);
	}

	public boolean transferKymosRoi_at_T_To_Capillaries_Measures(int t, Capillary cap) {
		return capillaryMeasureBridge.transferKymosRoi_at_T_To_Capillaries_Measures(t, cap);
	}

	/**
	 * Transfers ROIs at the current frame to the capillary's measures. Resolves
	 * frame index from getCurrentFrame() or viewer; use when cap is already known.
	 */
	public boolean transferKymosRoi_at_T_To_Capillaries_Measures(Capillary cap) {
		return capillaryMeasureBridge.transferKymosRoi_at_T_To_Capillaries_Measures(cap);
	}

	public boolean validateLinearROIsAtT(int t, Capillary cap) {
		return capillaryMeasureBridge.validateLinearROIsAtT(t, cap);
	}

	/**
	 * Validates linear (level/derivative) ROIs at the current frame. Resolves frame
	 * index from getCurrentFrame() or viewer; use when cap is already known.
	 */
	public boolean validateLinearROIsAtT(Capillary cap) {
		return capillaryMeasureBridge.validateLinearROIsAtT(cap);
	}

	/**
	 * Rebuilds capillary gulps from current gulp ROIs at frame t, then refreshes
	 * gulp ROIs on the sequence. Clears the capillary's gulpMeasuresDirty flag.
	 * Call from EditLevels (Validate) or Options (Save unsaved changes).
	 */

	public void validateGulpROIsAtT(Experiment exp, int t) {
		capillaryMeasureBridge.validateGulpROIsAtT(exp, t);
	}

	/**
	 * Rebuilds gulp ROIs at the current frame for the given capillary. Resolves
	 * frame index from getCurrentFrame() or viewer; use when cap is already known.
	 */
	public void validateGulpROIsAtT(Capillary cap) {
		capillaryMeasureBridge.validateGulpROIsAtT(cap);
	}

	public void validateGulpROIsAtT(int t, Capillary cap) {
		capillaryMeasureBridge.validateGulpROIsAtT(t, cap);
	}

	public void transferCapillariesMeasuresToKymos(Capillaries capillaries) {
		capillaryMeasureBridge.transferCapillariesMeasuresToKymos(capillaries);
	}

	/**
	 * Resolves the capillary for kymograph frame t. The t-th frame shows the t-th
	 * image in the list; we resolve by that image's filename first (source of
	 * truth), then by list index, then by kymographIndex.
	 */
	public Capillary getCapillaryForFrame(int t, Capillaries capillaries) {
		return capillaryMeasureBridge.getCapillaryForFrame(t, capillaries);
	}

	/**
	 * Syncs the sequence so only capillary measure ROIs for frame t are present
	 * (same pattern as Experiment.updateROIsAt for fly positions). No-op if t
	 * equals current frame to avoid redundant work and repeated calls from
	 * viewer/combo events.
	 */
	public void syncROIsForCurrentFrame(int t, Capillaries capillaries) {
		capillaryMeasureBridge.syncROIsForCurrentFrame(t, capillaries);
	}

	/**
	 * Removes capillary measure ROIs on frame {@code t} and re-adds them from the model (same as
	 * {@link #syncROIsForCurrentFrame} but without skipping when {@code t} is already the current
	 * frame, and without changing the current T). Use after capillary / prefix rename so sequence
	 * ROI names match edited measure geometry in the model.
	 */
	public void replaceCapillaryMeasureRoisAtT(int t, Capillaries capillaries) {
		capillaryMeasureBridge.replaceCapillaryMeasureRoisAtT(t, capillaries);
	}

	public void saveKymosCurvesToCapillariesMeasures(Experiment exp) {
		capillaryMeasureBridge.saveKymosCurvesToCapillariesMeasures(exp);
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
				ImageProcessingResult adjustResult = adjustImageSize(imageDescriptors, maxDimensions,
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
					configuration.getAcceptedFileExtensions().toArray(new String[0]), false);

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
			return PerSpotRoiTiffDiskLayout.INSTANCE.listImageDescriptors(baseDirectory, cages, allSpots);
		} finally {
			processingLock.unlock();
		}
	}

	/**
	 * One descriptor per cage for stacked cage kymographs written by
	 * {@link plugins.fmp.multitools.service.CageSpotKymographBuilder} ({@code kymocage_<id>.tiff}).
	 */
	public List<ImageFileData> createCageSpotKymographFileList(String baseDirectory, Cages cages) {
		if (baseDirectory == null || baseDirectory.trim().isEmpty()) {
			throw new IllegalArgumentException("Base directory cannot be null or empty");
		}
		if (cages == null) {
			throw new IllegalArgumentException("Cages array cannot be null");
		}

		processingLock.lock();
		try {
			return CageStackTiffDiskLayout.INSTANCE.listImageDescriptors(baseDirectory, cages, null);
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
	private ImageProcessingResult adjustImageSize(List<ImageFileData> imageDescriptors, Rectangle targetDimensions,
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
