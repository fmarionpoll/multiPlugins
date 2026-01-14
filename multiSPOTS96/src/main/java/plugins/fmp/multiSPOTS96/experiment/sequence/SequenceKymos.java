package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import icy.common.exception.UnsupportedFormatException;
import icy.file.Loader;
import icy.file.Saver;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.sequence.MetaDataUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import loci.formats.FormatException;
import ome.xml.meta.OMEXMLMetadata;
import plugins.fmp.multiSPOTS96.experiment.EnumStatus;
import plugins.fmp.multiSPOTS96.experiment.ExperimentDirectories;
import plugins.fmp.multiSPOTS96.experiment.cages.Cage;
import plugins.fmp.multiSPOTS96.experiment.cages.CagesArray;
import plugins.fmp.multiSPOTS96.experiment.spots.Spot;
import plugins.fmp.multiSPOTS96.tools.Comparators;
import plugins.fmp.multiSPOTS96.tools.ROI2D.Utilities;
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
	// === CONSTANTS ===
	private static final Logger LOGGER = Logger.getLogger(SequenceKymos.class.getName());

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
		setImagesList(imageNames);
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
						Utilities.interpolateMissingPointsAlongXAxis((ROI2DPolyLine) roi, sequenceWidth);
						processed++;
					} else if (roi.getName() != null && roi.getName().contains("derivative")) {
						// Skip derivative ROIs
						continue;
					}
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Failed to process ROI: " + roi.getName(), e);
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

	/**
	 * Loads kymograph images from descriptors with specified options.
	 * 
	 * @param imageDescriptors  the image descriptors
	 * @param adjustmentOptions the adjustment options
	 * @return processing result
	 */
	public ImageProcessingResult loadKymographs(List<ImageFileDescriptor> imageDescriptors,
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

			long processingTime = System.currentTimeMillis() - startTime;

			return ImageProcessingResult.builder().success(true).processedCount(acceptedFiles.size())
					.processingTimeMs(processingTime)
					.message(String.format("Successfully loaded %d kymograph images", acceptedFiles.size())).build();

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Failed to load kymographs", e);
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
	public ImageProcessingResult loadKymographs(List<ImageFileDescriptor> imageDescriptors) {
		return loadKymographs(imageDescriptors, ImageAdjustmentOptions.defaultOptions());
	}

	/**
	 * Creates a list of potential kymograph files from spots in cages.
	 * 
	 * @param baseDirectory the base directory
	 * @param cagesArray    the cages array
	 * @return list of image file descriptors
	 */
	public List<ImageFileDescriptor> createKymographFileList(String baseDirectory, CagesArray cagesArray) {
		if (baseDirectory == null || baseDirectory.trim().isEmpty()) {
			throw new IllegalArgumentException("Base directory cannot be null or empty");
		}
		if (cagesArray == null) {
			throw new IllegalArgumentException("Cages array cannot be null");
		}

		processingLock.lock();
		try {
			String fullDirectory = baseDirectory + File.separator;

			if (cagesArray.cagesList.isEmpty()) {
				LOGGER.warning("No cages found in cages array");
				return new ArrayList<>();
			}

			Cage firstCage = cagesArray.cagesList.get(0);
			if (firstCage.spotsArray == null || firstCage.spotsArray.getSpotsList().isEmpty()) {
				LOGGER.warning("No spots found in first cage");
				return new ArrayList<>();
			}

			// Calculate total expected files
			int totalExpectedFiles = cagesArray.cagesList.size() * firstCage.spotsArray.getSpotsList().size();
			List<ImageFileDescriptor> fileList = new ArrayList<>(totalExpectedFiles);

			// Generate file descriptors for each spot in each cage
			for (Cage cage : cagesArray.cagesList) {
				if (cage.spotsArray == null)
					continue;

				for (Spot spot : cage.spotsArray.getSpotsList()) {
					ImageFileDescriptor descriptor = new ImageFileDescriptor();
					descriptor.fileName = fullDirectory + spot.getRoi().getName() + ".tiff";
					descriptor.exists = new File(descriptor.fileName).exists();
					fileList.add(descriptor);
				}
			}

//			LOGGER.info(String.format("Created %d kymograph file descriptors from %d cages", fileList.size(),
//					cagesArray.cagesList.size()));

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
	 * @deprecated Use {@link #createKymographFileList(String, CagesArray)} instead
	 */
	@Deprecated
	public List<ImageFileDescriptor> loadListOfPotentialKymographsFromSpots(String dir, CagesArray cagesArray) {
		return createKymographFileList(dir, cagesArray);
	}

	/**
	 * @deprecated Use {@link #loadKymographs(List, ImageAdjustmentOptions)} instead
	 */
	@Deprecated
	public boolean loadKymographImagesFromList(List<ImageFileDescriptor> kymoImagesDesc, boolean adjustImagesSize) {
		ImageAdjustmentOptions options = adjustImagesSize
				? ImageAdjustmentOptions.withSizeAdjustment(calculateMaxDimensions(kymoImagesDesc))
				: ImageAdjustmentOptions.noAdjustment();

		ImageProcessingResult result = loadKymographs(kymoImagesDesc, options);
		return result.isSuccess();
	}

	// === ACCESSORS ===

	/**
	 * @deprecated Use {@link #getKymographInfo()} instead
	 */
	@Deprecated
	public boolean isRunning_loadImages() {
		return isLoadingImages;
	}

	/**
	 * @deprecated Use {@link #getKymographInfo()} instead
	 */
	@Deprecated
	public int getImageWidthMax() {
		return maxImageWidth;
	}

	/**
	 * @deprecated Use {@link #getKymographInfo()} instead
	 */
	@Deprecated
	public int getImageHeightMax() {
		return maxImageHeight;
	}

	// === PRIVATE HELPER METHODS ===

	/**
	 * Calculates the maximum dimensions from a list of image descriptors.
	 * 
	 * @param imageDescriptors the image descriptors
	 * @return rectangle representing maximum dimensions
	 */
	public Rectangle calculateMaxDimensions(List<ImageFileDescriptor> imageDescriptors) {
		int maxWidth = 0;
		int maxHeight = 0;

		for (ImageFileDescriptor descriptor : imageDescriptors) {
			if (!descriptor.exists)
				continue;

			try {
				updateImageDimensions(descriptor);
				maxWidth = Math.max(maxWidth, descriptor.imageWidth);
				maxHeight = Math.max(maxHeight, descriptor.imageHeight);
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, "Failed to get dimensions for: " + descriptor.fileName, e);
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
	private void updateImageDimensions(ImageFileDescriptor descriptor) throws Exception {
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
	private ImageProcessingResult adjustImageSizes(List<ImageFileDescriptor> imageDescriptors,
			Rectangle targetDimensions, ImageAdjustmentOptions options) {
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
			for (ImageFileDescriptor descriptor : imageDescriptors) {
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
					LOGGER.log(Level.WARNING, "Failed to adjust image: " + descriptor.fileName, e);
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
	private void adjustSingleImage(ImageFileDescriptor descriptor, Rectangle targetDimensions) throws Exception {
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
	private List<String> extractValidImageFiles(List<ImageFileDescriptor> descriptors) {
		List<String> validFiles = new ArrayList<>();
		for (ImageFileDescriptor descriptor : descriptors) {
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
			LOGGER.log(Level.WARNING, "Failed to set sequence name from first image", e);
		}
	}

	/**
	 * @deprecated Use {@link #setSequenceNameFromFirstImage(List)} instead
	 */
	@Deprecated
	protected void setParentDirectoryAsCSCamFileName(String filename) {
		if (filename != null) {
			setSequenceNameFromFirstImage(List.of(filename));
		}
	}

	/**
	 * @deprecated Use {@link #calculateMaxDimensions(List)} instead
	 */
	@Deprecated
	Rectangle getMaxSizeofTiffFiles(List<ImageFileDescriptor> files) {
		return calculateMaxDimensions(files);
	}

	/**
	 * @deprecated Use {@link #updateImageDimensions(ImageFileDescriptor)} instead
	 */
	@Deprecated
	boolean getImageDim(final ImageFileDescriptor fileProp) {
		try {
			updateImageDimensions(fileProp);
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to get image dimensions", e);
			return false;
		}
	}

	/**
	 * @deprecated Use
	 *             {@link #adjustImageSizes(List, Rectangle, ImageAdjustmentOptions)}
	 *             instead
	 */
	@Deprecated
	void adjustImagesToMaxSize(List<ImageFileDescriptor> files, Rectangle rect) {
		ImageAdjustmentOptions options = ImageAdjustmentOptions.withSizeAdjustment(rect);
		adjustImageSizes(files, rect, options);
	}

	/**
	 * @deprecated Use
	 *             {@link #transferImageData(IcyBufferedImage, IcyBufferedImage)}
	 *             instead
	 */
//    @Deprecated
//    private void transferImage1To2(IcyBufferedImage source, IcyBufferedImage destination) {
//        transferImageData(source, destination);
//    }

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
