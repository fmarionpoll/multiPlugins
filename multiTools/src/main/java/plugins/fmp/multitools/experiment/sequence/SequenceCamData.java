package plugins.fmp.multitools.experiment.sequence;

import java.awt.Rectangle;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.EnumStatus;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

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

	// === CORE FIELDS ===
	private final ReentrantLock lock = new ReentrantLock();
	private volatile boolean closed = false;

	private Sequence seq = null;
	private EnumStatus status = EnumStatus.REGULAR;
	private int currentFrame = 0;
	private IcyBufferedImage referenceImage = null;
	private IcyBufferedImage referenceImageLight = null;
	private IcyBufferedImage referenceImageDark = null;

	// Fields ported from experiment.SequenceCamData
	private long seqAnalysisStart = 0;
	private int seqAnalysisStep = 1;
	private ROI2DPolygon referenceROI2DPolygon = null;

	// Specialized managers
	private final ImageLoader imageLoader;
	private final TimeManager timeManager;
	private final ROIManager roiManager;
	private final ViewerManager viewerManager;

	// Per-frame light status: 1 = light, 0 = dark (or unspecified)
	// Aligned with camera frames managed by ImageLoader / TimeManager.
	// This is populated by dark-interval detection routines (e.g. CleanGaps
	// dialog).
	private int[] lightStatusPerFrame = null;

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

	public SequenceCamData(List<String> listNames) {
		this.imageLoader = new ImageLoader();
		this.timeManager = new TimeManager();
		this.roiManager = new ROIManager();
		this.viewerManager = new ViewerManager();
		this.seq = new Sequence();
		this.status = EnumStatus.FILESTACK;

		setImagesList(listNames);
		status = EnumStatus.FILESTACK;
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
			Logger.warn("Failed to get image at t=" + t + ", z=" + z, e);
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
				int displayedTotalFrames = Math.max(0, seq.getSizeT() - 1);
				return fileName + " [" + t + "/" + displayedTotalFrames + "]";
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
			Logger.warn("Cannot process ROIs: sequence is not initialized");
			return false;
		}

		lock.lock();
		try {
			switch (operation.getType()) {
			case DISPLAY:
				roiManager.displaySpecificROIs(seq, operation.isVisible(), operation.getPattern());
				return true;
			case REMOVE_WITH_PATTERN:
				roiManager.removeROIsContainingString(seq, operation.getPattern());
				return true;
			case REMOVE_MISSING_PATTERN:
				roiManager.removeROIsMissingString(seq, operation.getPattern());
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
			Logger.warn("Failed to process ROI operation: " + operation.getType(), e);
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
	public List<ROI2D> findROIsMatchingNamePattern(String pattern) {
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

	/**
	 * Finds all ROIs missing the specified pattern.
	 * 
	 * @param pattern the search pattern
	 * @return list of matching ROIs
	 */
	public List<ROI2D> findROIsMissingNamePattern(String pattern) {
		if (pattern == null) {
			throw new IllegalArgumentException("Pattern cannot be null");
		}

		ensureNotClosed();
		if (seq == null) {
			return new ArrayList<>();
		}

		lock.lock();
		try {
			return roiManager.getROIsMissingString(seq, pattern);
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
	 * Returns the absolute on-disk frame index that corresponds to the first valid
	 * (visible) frame of this sequence. When the user offsets the experiment start
	 * frame, the image list is clipped and valid frame indices start at 0.
	 */
	public int getAbsoluteIndexFirstValidFrame() {
		long v = imageLoader.getAbsoluteIndexFirstImage();
		if (v < 0)
			v = 0;
		if (v > Integer.MAX_VALUE)
			v = Integer.MAX_VALUE;
		return (int) v;
	}

	/**
	 * Converts a valid (visible) frame index to the absolute on-disk frame index.
	 */
	public int validToAbsoluteFrameIndex(int validFrameIndex) {
		if (validFrameIndex < 0)
			return getAbsoluteIndexFirstValidFrame();
		long abs = (long) getAbsoluteIndexFirstValidFrame() + (long) validFrameIndex;
		if (abs < 0)
			return 0;
		if (abs > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		return (int) abs;
	}

	/**
	 * Converts an absolute on-disk frame index to the corresponding valid (visible)
	 * frame index. Values before the valid start are clamped to 0.
	 */
	public int absoluteToValidFrameIndex(int absoluteFrameIndex) {
		int offset = getAbsoluteIndexFirstValidFrame();
		int valid = absoluteFrameIndex - offset;
		return Math.max(0, valid);
	}

	/**
	 * Returns the epoch milliseconds timestamp of the first valid (visible) frame,
	 * or -1 if unavailable.
	 */
	public long getFirstValidFrameEpochMs() {
		FileTime ft = getFileTimeFromStructuredName(0);
		return (ft != null) ? ft.toMillis() : -1L;
	}

	/**
	 * Returns the epoch milliseconds timestamp of a valid (visible) frame index, or
	 * -1 if unavailable.
	 */
	public long getValidFrameEpochMs(int validFrameIndex) {
		if (validFrameIndex < 0)
			validFrameIndex = 0;
		FileTime ft = getFileTimeFromStructuredName(validFrameIndex);
		return (ft != null) ? ft.toMillis() : -1L;
	}

	/**
	 * Returns a relative timestamp in ms for a valid (visible) frame, using the
	 * first valid frame as time origin. This is the canonical "experiment time"
	 * used by analyses (kymograph, tracking, exports) within a single experiment.
	 */
	public long getValidFrameRelativeMs(int validFrameIndex) {
		long t0 = getFirstValidFrameEpochMs();
		long ti = getValidFrameEpochMs(validFrameIndex);
		if (t0 < 0 || ti < 0)
			return -1L;
		return ti - t0;
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
			Logger.warn("Cannot configure viewer: sequence is not initialized");
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
		// Canonical: first valid frame epoch ms when frames exist.
		// For synthetic sequences (e.g. expAll used for export grids) there are no
		// frames/filenames, and TimeManager would fall back to dummy timestamps which
		// breaks duration computations and can make exports appear empty.
		if (imageLoader.getNTotalFrames() > 0) {
			long ft = getFirstValidFrameEpochMs();
			if (ft > 0)
				return ft;
		}
		return timeManager.getFirstImageMs();
	}

	public void setFirstImageMs(long timeMs) {
		timeManager.setFirstImageMs(timeMs);
	}

	public long getLastImageMs() {
		// Canonical: last valid frame epoch ms when frames exist. See getFirstImageMs()
		// for why we avoid dummy timestamps on empty/synthetic sequences.
		int n = imageLoader.getNTotalFrames();
		if (n > 0) {
			long ft = getValidFrameEpochMs(n - 1);
			if (ft > 0)
				return ft;
		}
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
	 * Attaches an existing sequence to this object. Closes the previous sequence
	 * (if different) to ensure viewers are properly cleaned up and reattached to
	 * the new sequence.
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
			// If attaching the same sequence, do nothing (preserve viewers)
			if (this.seq == sequence) {
				return;
			}

			// Close old sequence if it's different from the new one
			// This ensures viewers attached to the old sequence are closed
			// and will be recreated for the new sequence
			if (this.seq != null) {
				// Remove ROIs but don't close viewers here - closeSequence() handles that
				// We'll close the sequence properly below
				this.seq.removeAllROI();
				List<Viewer> oldViewers = this.seq.getViewers();
				if (oldViewers != null) {
					for (Viewer viewer : oldViewers) {
						if (viewer != null) {
							viewer.close();
						}
					}
				}
				this.seq.close();
			}

			// Close any auto-created viewers on the new sequence before attaching
			// This prevents the brief "loading canvas" display when switching experiments
			// ICY's Loader.loadSequences() may auto-create viewers, which we want to
			// replace
			// with our own properly configured viewers
			// Note: sequence should already be in beginUpdate() mode from ImageLoader
			List<Viewer> newSequenceViewers = sequence.getViewers();
			if (newSequenceViewers != null && !newSequenceViewers.isEmpty()) {
				for (Viewer viewer : newSequenceViewers) {
					if (viewer != null) {
						viewer.close();
					}
				}
			}

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
				// Remove all ROIs before closing to prevent them from persisting in viewers
				seq.removeAllROI();
				// Close all viewers associated with this sequence
				List<Viewer> viewers = seq.getViewers();
				if (viewers != null) {
					for (Viewer viewer : viewers) {
						if (viewer != null) {
							viewer.close();
						}
					}
				}
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

	public void setSequence(Sequence seq) {
		this.seq = seq;
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

	public int[] getLightStatusPerFrame() {
		return lightStatusPerFrame;
	}

	public void setLightStatusPerFrame(int[] lightStatusPerFrame) {
		this.lightStatusPerFrame = lightStatusPerFrame;
	}

	public IcyBufferedImage getReferenceImage() {
		return referenceImage;
	}

	public void setReferenceImage(IcyBufferedImage image) {
		this.referenceImage = image;
	}

	public IcyBufferedImage getReferenceImageLight() {
		return referenceImageLight;
	}

	public void setReferenceImageLight(IcyBufferedImage image) {
		this.referenceImageLight = image;
	}

	public IcyBufferedImage getReferenceImageDark() {
		return referenceImageDark;
	}

	public void setReferenceImageDark(IcyBufferedImage image) {
		this.referenceImageDark = image;
	}

	// === ANALYSIS PARAMETERS (Ported from experiment.SequenceCamData) ===

	public long getSeqAnalysisStart() {
		return seqAnalysisStart;
	}

	public void setSeqAnalysisStart(long seqAnalysisStart) {
		this.seqAnalysisStart = seqAnalysisStart;
	}

	public int getSeqAnalysisStep() {
		return seqAnalysisStep;
	}

	public void setSeqAnalysisStep(int seqAnalysisStep) {
		this.seqAnalysisStep = seqAnalysisStep;
	}

	public plugins.kernel.roi.roi2d.ROI2DPolygon getReferenceROI2DPolygon() {
		return referenceROI2DPolygon;
	}

	public void setReferenceROI2DPolygon(plugins.kernel.roi.roi2d.ROI2DPolygon roi) {
		referenceROI2DPolygon = roi;
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