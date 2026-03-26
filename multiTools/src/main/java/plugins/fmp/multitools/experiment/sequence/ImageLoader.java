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
	/**
	 * Absolute index (0-based) of the first valid frame on disk.
	 * <p>
	 * All public APIs of {@link ImageLoader} use <b>valid</b> frame indices (starting
	 * at 0 for the first valid frame). This field is applied internally to map
	 * valid indices to absolute on-disk indices.
	 */
	private long absoluteIndexFirstImage = 0;
	/**
	 * Absolute end index (exclusive) in the original on-disk list. If {@code <= 0},
	 * the effective end is the end of the list.
	 */
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

		if (t < 0)
			return null;

		int absIndex = validToAbsoluteIndex(t);
		if (absIndex < 0)
			return null;

		int endExclusive = getEffectiveAbsoluteEndExclusive();
		if (absIndex >= endExclusive)
			return null;

		if (absIndex >= 0 && absIndex < imagesList.size())
			return imagesList.get(absIndex);

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
		// Ensure derived counts are up-to-date before loading
		getNTotalFrames();
		List<String> validList = getImagesList(true);
		nTotalFrames = validList.size();
		Sequence seq = loadSequenceFromImagesList(validList);
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
		String firstValid = getFileNameFromImageList(0);
		if (firstValid == null)
			return false;
		singleImageList.add(firstValid);
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
		// Store the full on-disk list; do not clip.
		setImagesList(images);
		// Derive valid-frame count from offset + fixed end.
		getNTotalFrames();

		List<String> validList = getImagesList(true);
		nTotalFrames = validList.size();
		Sequence seq = loadSequenceFromImagesList(validList);
		if (seq != null) {
			// Sequence is already in beginUpdate() mode from loadSequenceFromImagesList()
			seqCamData.attachSequence(seq);
		}
	}

	private int getEffectiveAbsoluteStart() {
		long start = absoluteIndexFirstImage;
		if (start < 0)
			start = 0;
		if (start > Integer.MAX_VALUE)
			start = Integer.MAX_VALUE;
		return (int) Math.min(start, imagesList.size());
	}

	private int getEffectiveAbsoluteEndExclusive() {
		int end = imagesList.size();
		if (fixedNumberOfImages > 0) {
			long v = fixedNumberOfImages;
			if (v < 0)
				v = 0;
			if (v > Integer.MAX_VALUE)
				v = Integer.MAX_VALUE;
			end = (int) Math.min(v, imagesList.size());
		}
		int start = getEffectiveAbsoluteStart();
		if (end < start)
			end = start;
		return end;
	}

	private int validToAbsoluteIndex(int tValid) {
		int start = getEffectiveAbsoluteStart();
		long abs = (long) start + (long) tValid;
		if (abs < 0 || abs > Integer.MAX_VALUE)
			return -1;
		return (int) abs;
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
		return getImagesList(false);
	}

	public List<String> getImagesList(boolean sort) {
		if (imagesList.isEmpty())
			return imagesList;

		// Work in on-disk order. If requested, sort a copy.
		List<String> base = imagesList;
		if (sort) {
			ArrayList<String> copy = new ArrayList<>(imagesList);
			Collections.sort(copy);
			base = copy;
		}

		int start = getEffectiveAbsoluteStart();
		int end = getEffectiveAbsoluteEndExclusive();
		if (start >= end)
			return new ArrayList<>();
		return new ArrayList<>(base.subList(start, end));
	}

	public void setImagesList(List<String> images) {
		imagesList.clear();
		imagesList = new ArrayList<>(images);
		// Reset derived count; getNTotalFrames() recomputes.
		nTotalFrames = 0;
	}

	public int getImagesCount() {
		return getNTotalFrames();
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
		if (imagesList.isEmpty())
			return 0;

		// Derive valid-frame count from [start,endExclusive) over the on-disk list.
		int start = getEffectiveAbsoluteStart();
		int end = getEffectiveAbsoluteEndExclusive();
		nTotalFrames = Math.max(0, end - start);
		return nTotalFrames;
	}

	public boolean checkIfNFramesIsValid() {
		int nFrames = getImagesCount();
		if (nFrames < 1)
			return false;
		return true;
	}
}