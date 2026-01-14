package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.Rectangle;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS96.experiment.EnumStatus;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformEnums;

/**
 * Manages camera sequence data including images, ROIs, timing, and viewer
 * operations.
 * 
 * <p>
 * This class provides a unified interface for working with image sequences from
 * camera data, supporting operations like:
 * <ul>
 * <li>Loading and managing image sequences</li>
 * <li>ROI (Region of Interest) manipulation</li>
 * <li>Time-based operations and analysis</li>
 * <li>Viewer configuration and display</li>
 * </ul>
 * 
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * SequenceCamData data = SequenceCamData.builder()
 *     .withName("experiment1")
 *     .withImagesDirectory("/path/to/images")
 *     .build();
 * 
 * try (data) {
 *     data.initializeFromDirectory("/path/to/images");
 *     SequenceInfo info = data.getSequenceInfo();
 *     // ... work with sequence
 * }
 * }</pre>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 * @since 1.0
 */
public class SequenceCamData implements AutoCloseable {
	// === CONSTANTS ===
	private static final Logger LOGGER = Logger.getLogger(SequenceCamData.class.getName());

	// === CORE FIELDS ===
	private final ReentrantLock lock = new ReentrantLock();
	private volatile boolean closed = false;

	private Sequence seq = null;
	private EnumStatus status = EnumStatus.REGULAR;
	private int currentFrame = 0;
	private IcyBufferedImage referenceImage = null;

	// Specialized managers
	private final ImageLoader imageLoader;
	private final TimeManager timeManager;
	private final ROIManager roiManager;
	private final ViewerManager viewerManager;

	// === CONSTRUCTORS ===

	/**
	 * Creates a new SequenceCamData with default settings.
	 */
	public SequenceCamData() {
		this.imageLoader = new ImageLoader();
		this.timeManager = new TimeManager();
		this.roiManager = new ROIManager();
		this.viewerManager = new ViewerManager();
		this.seq = new Sequence();
		this.status = EnumStatus.FILESTACK;
	}

	/**
	 * Creates a new SequenceCamData with specified name and initial image.
	 * 
	 * @param name  the sequence name, must not be null or empty
	 * @param image the initial image, must not be null
	 * @throws IllegalArgumentException if name is null/empty or image is null
	 */
	public SequenceCamData(String name, IcyBufferedImage image) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Name cannot be null or empty");
		}
		if (image == null) {
			throw new IllegalArgumentException("Image cannot be null");
		}

		this.imageLoader = new ImageLoader();
		this.timeManager = new TimeManager();
		this.roiManager = new ROIManager();
		this.viewerManager = new ViewerManager();
		this.seq = new Sequence(name, image);
		this.status = EnumStatus.FILESTACK;
	}

	/**
	 * Creates a builder for constructing SequenceCamData instances.
	 * 
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	// === INITIALIZATION ===

	/**
	 * Initializes the sequence from the specified directory.
	 * 
	 * @param directory the directory containing images
	 * @return true if initialization was successful, false otherwise
	 * @throws IllegalArgumentException if directory is null or empty
	 */
	public boolean initializeFromDirectory(String directory) {
		if (directory == null || directory.trim().isEmpty()) {
			throw new IllegalArgumentException("Directory cannot be null or empty");
		}

		ensureNotClosed();
		lock.lock();
		try {
			imageLoader.setImagesDirectory(directory);
			return imageLoader.loadImages(this);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Initializes the sequence with the provided image list.
	 * 
	 * @param imagesList the list of image paths
	 * @throws IllegalArgumentException if imagesList is null or empty
	 */
	public void initializeFromImageList(List<String> imagesList) {
		if (imagesList == null || imagesList.isEmpty()) {
			throw new IllegalArgumentException("Images list cannot be null or empty");
		}

		ensureNotClosed();
		lock.lock();
		try {
			imageLoader.loadImageList(imagesList, this);
		} finally {
			lock.unlock();
		}
	}

	// === SEQUENCE OPERATIONS ===

	/**
	 * Gets comprehensive sequence information.
	 * 
	 * @return sequence information object
	 */
	public SequenceInfo getSequenceInfo() {
		ensureNotClosed();
		lock.lock();
		try {
			return SequenceInfo.builder().name(imageLoader.getFileName()).currentFrame(currentFrame)
					.totalFrames(seq != null ? seq.getSizeT() : 0).status(status).timeRange(getTimeRange()).build();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Gets an image from the sequence at the specified time and z position.
	 * 
	 * @param t the time index
	 * @param z the z position
	 * @return the image at the specified position, or null if not available
	 * @throws IndexOutOfBoundsException if indices are out of bounds
	 */
	public IcyBufferedImage getSeqImage(int t, int z) {
		ensureNotClosed();

		if (seq == null) {
			throw new IllegalStateException("Sequence is not initialized");
		}

		validateFrameIndices(t, z);

		lock.lock();
		try {
			currentFrame = t;
			return seq.getImage(t, z);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to get image at t=" + t + ", z=" + z, e);
			return null;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Gets a decorated image name with frame information.
	 * 
	 * @param t the time index
	 * @return decorated name string
	 */
	public String getDecoratedImageName(int t) {
		ensureNotClosed();
		lock.lock();
		try {
			currentFrame = t;
			String fileName = imageLoader.getFileName();
			if (seq != null) {
				return fileName + " [" + t + "/" + (seq.getSizeT() - 1) + "]";
			} else {
				return fileName + "[]";
			}
		} finally {
			lock.unlock();
		}
	}

	// === ROI OPERATIONS ===

	/**
	 * Processes ROI operations in a unified way.
	 * 
	 * @param operation the ROI operation to perform
	 * @return true if operation was successful, false otherwise
	 */
	public boolean processROIs(ROIOperation operation) {
		if (operation == null) {
			throw new IllegalArgumentException("Operation cannot be null");
		}

		ensureNotClosed();
		if (seq == null) {
			LOGGER.warning("Cannot process ROIs: sequence is not initialized");
			return false;
		}

		lock.lock();
		try {
			switch (operation.getType()) {
			case DISPLAY:
				roiManager.displaySpecificROIs(seq, operation.isVisible(), operation.getPattern());
				return true;
			case REMOVE:
				roiManager.removeROIsContainingString(seq, operation.getPattern());
				return true;
			case CENTER:
				roiManager.centerOnRoi(seq, operation.getRoi());
				return true;
			case SELECT:
				roiManager.selectRoi(seq, operation.getRoi(), operation.isSelected());
				return true;
			default:
				return false;
			}
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to process ROI operation: " + operation.getType(), e);
			return false;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Finds all ROIs containing the specified pattern.
	 * 
	 * @param pattern the search pattern
	 * @return list of matching ROIs
	 */
	public List<ROI2D> findROIs(String pattern) {
		if (pattern == null) {
			throw new IllegalArgumentException("Pattern cannot be null");
		}

		ensureNotClosed();
		if (seq == null) {
			return new ArrayList<>();
		}

		lock.lock();
		try {
			return roiManager.getROIsContainingString(seq, pattern);
		} finally {
			lock.unlock();
		}
	}

	// === TIME OPERATIONS ===

	/**
	 * Gets the time range information for this sequence.
	 * 
	 * @return time range object
	 */
	public TimeRange getTimeRange() {
		ensureNotClosed();
		return new TimeRange(timeManager.getFirstImageMs(), timeManager.getLastImageMs(),
				timeManager.getBinDurationMs());
	}

	/**
	 * Gets file time from the specified source.
	 * 
	 * @param frame  the frame index
	 * @param source the time source
	 * @return file time or null if not available
	 */
	public FileTime getFileTime(int frame, TimeSource source) {
		if (source == null) {
			throw new IllegalArgumentException("Time source cannot be null");
		}

		ensureNotClosed();
		lock.lock();
		try {
			switch (source) {
			case STRUCTURED_NAME:
				return timeManager.getFileTimeFromStructuredName(imageLoader, frame);
			case FILE_ATTRIBUTES:
				return timeManager.getFileTimeFromFileAttributes(imageLoader, frame);
			case JPEG_METADATA:
				return timeManager.getFileTimeFromJPEGMetaData(imageLoader, frame);
			default:
				return null;
			}
		} finally {
			lock.unlock();
		}
	}

	public boolean build_MsTimesArray_From_FileNamesList() {
		if (!getImageLoader().checkIfNFramesIsValid())
			return false;
		getTimeManager().build_MsTimeArray_From_FileNamesList(getImageLoader());
		return true;
	}

	// === VIEWER OPERATIONS ===

	/**
	 * Configures the viewer with the specified configuration.
	 * 
	 * @param config the viewer configuration
	 */
	public void configureViewer(ViewerConfiguration config) {
		if (config == null) {
			throw new IllegalArgumentException("Configuration cannot be null");
		}

		ensureNotClosed();
		if (seq == null) {
			LOGGER.warning("Cannot configure viewer: sequence is not initialized");
			return;
		}

		lock.lock();
		try {
			if (config.getDisplayRectangle() != null) {
				viewerManager.displayViewerAtRectangle(seq, config.getDisplayRectangle());
			}

			if (config.isShowOverlay()) {
				viewerManager.updateOverlayThreshold(config.getThreshold(), config.getTransform(),
						config.isIfGreater());
			} else {
				viewerManager.removeOverlay(seq);
			}

			viewerManager.updateOverlay(seq);
		} finally {
			lock.unlock();
		}
	}

	// === LEGACY DELEGATION METHODS (for backward compatibility) ===

	// Image loading methods
	public String getImagesDirectory() {
		return imageLoader.getImagesDirectory();
	}

	public void setImagesDirectory(String directoryString) {
		imageLoader.setImagesDirectory(directoryString);
	}

	public List<String> getImagesList(boolean bsort) {
		return imageLoader.getImagesList(bsort);
	}

	public List<String> getImagesList() {
		return imageLoader.getImagesList();
	}

	public void setImagesList(List<String> extImagesList) {
		imageLoader.setImagesList(extImagesList);
	}

	public String getCSCamFileName() {
		return imageLoader.getFileName();
	}

	public String getFileNameFromImageList(int t) {
		return imageLoader.getFileNameFromImageList(t);
	}

	public boolean loadImages() {
		return imageLoader.loadImages(this);
	}

	public boolean loadFirstImage() {
		return imageLoader.loadFirstImage(this);
	}

	public void loadImageList(List<String> imagesList) {
		imageLoader.loadImageList(imagesList, this);
	}

	// Time methods
	public FileTime getFileTimeFromStructuredName(int t) {
		return timeManager.getFileTimeFromStructuredName(imageLoader, t);
	}

	public FileTime getFileTimeFromFileAttributes(int t) {
		return timeManager.getFileTimeFromFileAttributes(imageLoader, t);
	}

	public FileTime getFileTimeFromJPEGMetaData(int t) {
		return timeManager.getFileTimeFromJPEGMetaData(imageLoader, t);
	}

	public long getFirstImageMs() {
		return timeManager.getFirstImageMs();
	}

	public void setFirstImageMs(long timeMs) {
		timeManager.setFirstImageMs(timeMs);
	}

	public long getLastImageMs() {
		return timeManager.getLastImageMs();
	}

	public void setLastImageMs(long timeMs) {
		timeManager.setLastImageMs(timeMs);
	}

	public long getBinDurationMs() {
		return timeManager.getBinDurationMs();
	}

	public void setBinDurationMs(long durationMs) {
		timeManager.setBinDurationMs(durationMs);
	}

	// ROI methods
	public void displaySpecificROIs(boolean isVisible, String pattern) {
		roiManager.displaySpecificROIs(seq, isVisible, pattern);
	}

	public ArrayList<ROI2D> getROIsContainingString(String string) {
		return roiManager.getROIsContainingString(seq, string);
	}

	public void removeROIsContainingString(String string) {
		roiManager.removeROIsContainingString(seq, string);
	}

	public void centerDisplayOnRoi(ROI2D roi) {
		roiManager.centerOnRoi(seq, roi);
	}

	public void selectRoi(ROI2D roi, boolean select) {
		roiManager.selectRoi(seq, roi, select);
	}

	// Viewer methods
	public void displayViewerAtRectangle(Rectangle parent0Rect) {
		viewerManager.displayViewerAtRectangle(seq, parent0Rect);
	}

	public void updateOverlay() {
		viewerManager.updateOverlay(seq);
	}

	public void removeOverlay() {
		viewerManager.removeOverlay(seq);
	}

	public void updateOverlayThreshold(int threshold, ImageTransformEnums transform, boolean ifGreater) {
		viewerManager.updateOverlayThreshold(threshold, transform, ifGreater);
	}

	// === SEQUENCE MANAGEMENT ===

	/**
	 * Attaches an existing sequence to this object.
	 * 
	 * @param sequence the sequence to attach
	 * @throws IllegalArgumentException if sequence is null
	 */
	public void attachSequence(Sequence sequence) {
		if (sequence == null) {
			throw new IllegalArgumentException("Sequence cannot be null");
		}

		ensureNotClosed();
		lock.lock();
		try {
			this.seq = sequence;
			this.status = EnumStatus.FILESTACK;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Closes the sequence and cleans up resources.
	 */
	public void closeSequence() {
		lock.lock();
		try {
			if (seq != null) {
				seq.removeAllROI();
				seq.close();
				seq = null;
			}
		} finally {
			lock.unlock();
		}
	}

	// === LIFECYCLE ===

	/**
	 * Closes this SequenceCamData and releases all resources.
	 */
	@Override
	public void close() {
		if (!closed) {
			lock.lock();
			try {
				if (!closed) {
					closeSequence();
					// Clean up references
					referenceImage = null;
					closed = true;
				}
			} finally {
				lock.unlock();
			}
		}
	}

	// === ACCESSORS ===

	public Sequence getSequence() {
		return seq;
	}

	public EnumStatus getStatus() {
		return status;
	}

	public void setStatus(EnumStatus newStatus) {
		this.status = newStatus;
	}

	public int getCurrentFrame() {
		return currentFrame;
	}

	public void setCurrentFrame(int frame) {
		this.currentFrame = frame;
	}

	public ImageLoader getImageLoader() {
		return imageLoader;
	}

	public TimeManager getTimeManager() {
		return timeManager;
	}

	public ROIManager getRoiManager() {
		return roiManager;
	}

	public ViewerManager getViewerManager() {
		return viewerManager;
	}

	public IcyBufferedImage getReferenceImage() {
		return referenceImage;
	}

	public void setReferenceImage(IcyBufferedImage image) {
		this.referenceImage = image;
	}

	// === PRIVATE HELPER METHODS ===

	/**
	 * Ensures this object is not closed.
	 * 
	 * @throws IllegalStateException if the object is closed
	 */
	private void ensureNotClosed() {
		if (closed) {
			throw new IllegalStateException("SequenceCamData is closed");
		}
	}

	/**
	 * Validates frame indices against sequence bounds.
	 * 
	 * @param t the time index
	 * @param z the z index
	 * @throws IndexOutOfBoundsException if indices are out of bounds
	 */
	private void validateFrameIndices(int t, int z) {
		if (t < 0 || t >= seq.getSizeT()) {
			throw new IndexOutOfBoundsException(
					"Frame index out of bounds: " + t + " (max: " + (seq.getSizeT() - 1) + ")");
		}
		if (z < 0 || z >= seq.getSizeZ()) {
			throw new IndexOutOfBoundsException("Z index out of bounds: " + z + " (max: " + (seq.getSizeZ() - 1) + ")");
		}
	}

	// === BUILDER PATTERN ===

	/**
	 * Builder for creating SequenceCamData instances.
	 */
	public static class Builder {
		private String name;
		private IcyBufferedImage image;
		private String imagesDirectory;
		private EnumStatus status = EnumStatus.FILESTACK;

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
		 * Sets the images directory.
		 * 
		 * @param directory the images directory
		 * @return this builder
		 */
		public Builder withImagesDirectory(String directory) {
			this.imagesDirectory = directory;
			return this;
		}

		/**
		 * Sets the initial status.
		 * 
		 * @param status the initial status
		 * @return this builder
		 */
		public Builder withStatus(EnumStatus status) {
			this.status = status;
			return this;
		}

		/**
		 * Builds the SequenceCamData instance.
		 * 
		 * @return a new SequenceCamData instance
		 */
		public SequenceCamData build() {
			SequenceCamData data;

			if (name != null && image != null) {
				data = new SequenceCamData(name, image);
			} else {
				data = new SequenceCamData();
			}

			if (imagesDirectory != null) {
				data.setImagesDirectory(imagesDirectory);
			}

			data.setStatus(status);
			return data;
		}
	}
}