package plugins.fmp.multitools.experiment.sequence;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import icy.file.Loader;
import icy.file.SequenceFileImporter;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.tools.Logger;

public class ImageLoader {

	private ArrayList<String> imagesList = new ArrayList<>();
	private String imagesDirectory = null;
	private long absoluteIndexFirstImage = 0;
	private long fixedNumberOfImages = -1;
	private String fileName = null;
	private int nTotalFrames = 0;

	public ImageLoader() {
	}

	public String getImagesDirectory() {
		if (imagesList.isEmpty()) {
			return imagesDirectory;
		}
		Path strPath = Paths.get(imagesList.get(0));
		imagesDirectory = strPath.getParent().toString();
		return imagesDirectory;
	}

	public void setImagesDirectory(String directoryString) {
		imagesDirectory = directoryString;
	}

	public String getFileName() {
		if (fileName == null && !imagesList.isEmpty()) {
			Path path = Paths.get(imagesList.get(0));
			int rootlevel = path.getNameCount() - 4;
			if (rootlevel < 0) {
				rootlevel = 0;
			}
			fileName = path.subpath(rootlevel, path.getNameCount() - 1).toString();
		}
		return fileName;
	}

	public String getFileNameFromImageList(int t) {
		if (imagesList.isEmpty()) {
			return null;
		}

		if (t >= 0 && t < imagesList.size()) {
			return imagesList.get(t);
		}
		return null;
	}

	public boolean loadImages(SequenceCamData seqCamData) {
		if (imagesList.isEmpty()) {
			return false;
		}
		// Skip if sequence already exists and has correct size (avoid recreating
		// unnecessarily)
		// This prevents closing viewers that were just created
		Sequence existingSeq = seqCamData.getSequence();
		if (existingSeq != null) {
			int expectedSize = getNTotalFrames();
			if (expectedSize > 0 && existingSeq.getSizeT() == expectedSize) {
				// Sequence already exists with correct size - don't recreate
				// This preserves any viewers that were already created
				return true;
			}
		}
		// Fix: Auto-correct nTotalFrames and fixedNumberOfImages if they're invalid
		// This must happen BEFORE clipImagesList() to prevent incorrect clipping
		getNTotalFrames();

		long savedFixedNumberOfImages = fixedNumberOfImages;
		List<String> clippedList = clipImagesList(imagesList);
		fixedNumberOfImages = savedFixedNumberOfImages;
		nTotalFrames = clippedList.size();
		Sequence seq = loadSequenceFromImagesList(clippedList);
		if (seq != null) {
			// Sequence is already in beginUpdate() mode from loadSequenceFromImagesList()
			seqCamData.attachSequence(seq);
			// Note: endUpdate() will be called after our viewer is created in updateViewerForSequenceCam()
		}
		return (seq != null);
	}

	public boolean loadFirstImage(SequenceCamData seqCamData) {
		if (imagesList.isEmpty()) {
			return false;
		}
		List<String> singleImageList = new ArrayList<>();
		singleImageList.add(imagesList.get(0));
		Sequence seq = loadSequenceFromImagesList(singleImageList);
		if (seq != null) {
			// Sequence is already in beginUpdate() mode from loadSequenceFromImagesList()
			seqCamData.attachSequence(seq);
		}
		return (seq != null);
	}

	public void loadImageList(List<String> images, SequenceCamData seqCamData) {
		if (images.isEmpty()) {
			return;
		}
		// Fix: Set imagesList temporarily so getNTotalFrames() can auto-correct
		// invalid values (like nFrames=1 from XML) before clipping
		setImagesList(images);
		// Auto-correct nTotalFrames and fixedNumberOfImages if they're invalid
		// This must happen BEFORE clipImagesList() to prevent incorrect clipping
		getNTotalFrames();

		// Preserve fixedNumberOfImages value - don't overwrite user settings
		// This allows the user to control the number of images via the UI
		// If fixedNumberOfImages is -1 (not set), it will remain -1 and clipImagesList
		// will include all images from the starting index
		long savedFixedNumberOfImages = fixedNumberOfImages;
		List<String> clippedList = clipImagesList(images);
		setImagesList(clippedList);
		fixedNumberOfImages = savedFixedNumberOfImages;
		nTotalFrames = clippedList.size();
		Sequence seq = loadSequenceFromImagesList(imagesList);
		if (seq != null) {
			// Sequence is already in beginUpdate() mode from loadSequenceFromImagesList()
			seqCamData.attachSequence(seq);
		}
	}

	private List<String> clipImagesList(List<String> images) {
		if (absoluteIndexFirstImage <= 0 && fixedNumberOfImages <= 0) {
			return new ArrayList<>(images);
		}

		// More efficient approach using subList
		int startIndex = (int) Math.min(absoluteIndexFirstImage, images.size());
		int endIndex = (fixedNumberOfImages > 0) ? (int) Math.min(startIndex + fixedNumberOfImages, images.size())
				: images.size();

		return new ArrayList<>(images.subList(startIndex, endIndex));
	}

	public IcyBufferedImage imageIORead(String name) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(name));
			return IcyBufferedImage.createFrom(image);
		} catch (IOException e) {
			Logger.error("Failed to read image: " + name + " - " + e.getMessage());
			return null;
		}
	}

	/**
	 * Loads a sequence from an image list. Single responsibility: load only; does not
	 * close viewers (caller or displayON handles viewer lifecycle; matches xMultiCAFE0).
	 */
	public Sequence loadSequenceFromImagesList(List<String> images) {
		if (images.isEmpty()) {
			Logger.warn("Empty images list provided");
			return null;
		}

		try {
			SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(images.get(0), true);
			List<Sequence> sequenceList = Loader.loadSequences(seqFileImporter, images, 0, // series index to load
					true, // force volatile
					false, // separate
					false, // auto-order
					true, // directory
					false, // add to recent
					false // show progress
			);

			if (sequenceList.isEmpty()) {
				Logger.warn("No sequences loaded");
				return null;
			}

			Sequence seq = sequenceList.get(0);
			if (seq != null)
				seq.beginUpdate();
			return seq;
		} catch (Exception e) {
			Logger.error("Error loading sequence: " + e.getMessage());
			return null;
		}
	}

	public Sequence initSequenceFromFirstImage(List<String> images) {
		if (images.isEmpty()) {
			Logger.warn("Empty images list provided");
			return null;
		}

		try {
			SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(images.get(0), true);
			return Loader.loadSequence(seqFileImporter, images.get(0), 0, false);
		} catch (Exception e) {
			Logger.error("Error initializing sequence: " + e.getMessage());
			return null;
		}
	}

	// Getters and setters

	public List<String> getImagesList() {
		return imagesList;
	}

	public List<String> getImagesList(boolean sort) {
		if (sort) {
			Collections.sort(imagesList);
		}
		return imagesList;
	}

	public void setImagesList(List<String> images) {
		imagesList.clear();
		imagesList = new ArrayList<>(images);
		// Auto-update nTotalFrames if not already set
		if (nTotalFrames == 0 && !imagesList.isEmpty()) {
			nTotalFrames = imagesList.size();
		}
	}

	public int getImagesCount() {
		return imagesList.size();
	}

	public void setAbsoluteIndexFirstImage(long index) {
		this.absoluteIndexFirstImage = index;
	}

	public long getAbsoluteIndexFirstImage() {
		return absoluteIndexFirstImage;
	}

	public void setFixedNumberOfImages(long number) {
		this.fixedNumberOfImages = number;
	}

	public long getFixedNumberOfImages() {
		return fixedNumberOfImages;
	}

	public void setNTotalFrames(int nTotalFrames) {
		this.nTotalFrames = nTotalFrames;
	}

	public int getNTotalFrames() {
		// Auto-compute from imagesList if invalid (-1, 0, or 1) or not set
		if (nTotalFrames <= 1 && nTotalFrames >= -1 && !imagesList.isEmpty() && imagesList.size() > 1) {
			nTotalFrames = imagesList.size();
			// Also update fixedNumberOfImages when invalid so clipImagesList uses full list
			if (fixedNumberOfImages <= 1) {
				fixedNumberOfImages = imagesList.size() + absoluteIndexFirstImage;
			}
		}
		return nTotalFrames;
	}

	public boolean checkIfNFramesIsValid() {
		int nFrames = getImagesCount();
		if (nFrames < 1)
			return false;
		return true;
	}
}