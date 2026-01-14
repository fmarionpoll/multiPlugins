package plugins.fmp.multiSPOTS96.experiment.sequence;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import icy.file.Loader;
import icy.file.SequenceFileImporter;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;

public class ImageLoader {
	private static final Logger LOGGER = Logger.getLogger(ImageLoader.class.getName());

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
		Sequence seq = loadSequenceFromImagesList(imagesList);
		nTotalFrames = (int) (seq.getSizeT() - absoluteIndexFirstImage);
		seqCamData.attachSequence(seq);
		return (seq != null);
	}

	public boolean loadFirstImage(SequenceCamData seqCamData) {
		if (imagesList.isEmpty()) {
			return false;
		}
		List<String> singleImageList = new ArrayList<>();
		singleImageList.add(imagesList.get(0));
		Sequence seq = loadSequenceFromImagesList(singleImageList);
		seqCamData.attachSequence(seq);
		return (seq != null);
	}

	public void loadImageList(List<String> images, SequenceCamData seqCamData) {
		if (images.isEmpty()) {
			return;
		}
		List<String> clippedList = clipImagesList(images);
		setImagesList(clippedList);
		fixedNumberOfImages = images.size();
		nTotalFrames = clippedList.size();
		Sequence seq = loadSequenceFromImagesList(imagesList);
		seqCamData.attachSequence(seq);
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
			LOGGER.severe("Failed to read image: " + name + " - " + e.getMessage());
			return null;
		}
	}

	public Sequence loadSequenceFromImagesList(List<String> images) {
		if (images.isEmpty()) {
			LOGGER.warning("Empty images list provided");
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
					true // show progress
			);

			if (sequenceList.isEmpty()) {
				LOGGER.warning("No sequences loaded");
				return null;
			}

//			if (sequenceList.size() > 1) {
//				LOGGER.info("Multiple sequences loaded, using first one. Count: " + sequenceList.size());
//			}

			return sequenceList.get(0);
		} catch (Exception e) {
			LOGGER.severe("Error loading sequence: " + e.getMessage());
			return null;
		}
	}

	public Sequence initSequenceFromFirstImage(List<String> images) {
		if (images.isEmpty()) {
			LOGGER.warning("Empty images list provided");
			return null;
		}

		try {
			SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(images.get(0), true);
			return Loader.loadSequence(seqFileImporter, images.get(0), 0, false);
		} catch (Exception e) {
			LOGGER.severe("Error initializing sequence: " + e.getMessage());
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
		return nTotalFrames;
	}

	public boolean checkIfNFramesIsValid() {
		int nFrames = getImagesCount();
		if (nFrames != getNTotalFrames())
			System.out.println("error: nFrames (seqCamData.camImagesList.size()):" + nFrames
					+ " is different from seqCamData.getImageLoader().getNTotalFrames():" + getNTotalFrames());
		if (nFrames < 1)
			return false;
		return true;
	}
}