package plugins.fmp.multiSPOTS.experiment;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.file.Loader;
import icy.file.SequenceFileImporter;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.fmp.multiSPOTS.tools.ViewerFMP;

public class SequenceCamData {
	public Sequence seq = null;
	public IcyBufferedImage refImage = null;

	public long seqAnalysisStart = 0;
	public int seqAnalysisStep = 1;

	public int currentFrame = 0;
	public int nTotalFrames = 0;

	public EnumStatus status = EnumStatus.REGULAR;
	protected String csFileName = null;
	public String imagesDirectory = null;
	public ArrayList<String> imagesList = new ArrayList<String>();

	long timeFirstImageInMs = 0;
	int indexTimePattern = -1;
	// _________________________________________________

	public long firstImage_ms = -1;
	public long lastImage_ms = -1;
	public long binImage_ms = -1;
	public long[] camImages_array_ms = null;

	public long absoluteIndexFirstImage = 0;
	public long fixedNumberOfImages = -1;
	public long deltaImage = 1;
	public long binFirst_ms = 0;
	public long binLast_ms = 0;
	public long binDuration_ms = 60000;

	// _________________________________________________

	FileNameTimePattern[] timePatternArray = new FileNameTimePattern[] { new FileNameTimePattern(),
			new FileNameTimePattern("yyyy-MM-dd_HH-mm-ss", "\\d{4}-\\d{2}-\\d{2}_\\d{2}\\-\\d{2}\\-\\d{2}"),
			new FileNameTimePattern("yy-MM-dd_HH-mm-ss", "\\d{2}-\\d{2}-\\d{2}_\\d{2}\\-\\d{2}\\-\\d{2}"),
			new FileNameTimePattern("yy.MM.dd_HH.mm.ss", "\\d{2}.\\d{2}.\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}") };

	// -------------------------

	public SequenceCamData() {
		seq = new Sequence();
		status = EnumStatus.FILESTACK;
	}

	public SequenceCamData(String name, IcyBufferedImage image) {
		seq = new Sequence(name, image);
		status = EnumStatus.FILESTACK;
	}

	// -----------------------

	public String getImagesDirectory() {
		Path strPath = Paths.get(imagesList.get(0));
		imagesDirectory = strPath.getParent().toString();
		return imagesDirectory;
	}

	public void setImagesDirectory(String directoryString) {
		imagesDirectory = directoryString;
	}

	public List<String> getImagesList(boolean bsort) {
		if (bsort)
			Collections.sort(imagesList);
		return imagesList;
	}

	public String getDecoratedImageName(int t) {
		currentFrame = t;
		if (seq != null)
			return getCSCamFileName() + " [" + (t) + "/" + (seq.getSizeT() - 1) + "]";
		else
			return getCSCamFileName() + "[]";
	}

	public String getCSCamFileName() {
		if (csFileName == null) {
			Path path = Paths.get(imagesList.get(0));
			int rootlevel = path.getNameCount() - 4;
			if (rootlevel < 0)
				rootlevel = 0;
			csFileName = path.subpath(rootlevel, path.getNameCount() - 1).toString();
		}
		return csFileName;
	}

	public String getFileNameFromImageList(int t) {
		String csName = null;
		if (status == EnumStatus.FILESTACK || status == EnumStatus.KYMOGRAPH) {
			if (imagesList.size() < 1)
				loadImageList(imagesList);
			csName = imagesList.get(t);
		}
//		else if (status == EnumStatus.AVIFILE)
//			csName = csFileName;
		return csName;
	}

	public boolean loadImages() {
		if (imagesList.size() == 0)
			return false;
		attachSequence(loadSequenceFromImagesList(imagesList));
		return (seq != null);
	}

	public boolean loadFirstImage() {
		if (imagesList.size() == 0)
			return false;
		List<String> dummyList = new ArrayList<String>();
		dummyList.add(imagesList.get(0));
		attachSequence(loadSequenceFromImagesList(dummyList));
		return (seq != null);
	}

	public void loadImageList(List<String> imagesList) {
		if (imagesList.size() > 0) {
			clipImagesList(imagesList);
			setImagesList(imagesList);
			attachSequence(loadSequenceFromImagesList(imagesList));
		}
	}

	private void clipImagesList(List<String> imagesList) {
		Collections.sort(imagesList);
		if (absoluteIndexFirstImage > 0) {
			for (int i = (int) absoluteIndexFirstImage; i > 0; i--)
				imagesList.remove(0);
		}

		if (fixedNumberOfImages > 0) {
			for (int i = imagesList.size(); i > fixedNumberOfImages; i--)
				imagesList.remove(i - 1);
		}
	}

	// --------------------------

	public IcyBufferedImage getSeqImage(int t, int z) {
		currentFrame = t;
		return seq.getImage(t, z);
	}

	// --------------------------

	String fileComponent(String fname) {
		int pos = fname.lastIndexOf("/");
		if (pos > -1)
			return fname.substring(pos + 1);
		else
			return fname;
	}

	public FileTime getFileTimeFromStructuredName(int t) {
		long timeInMs = 0;
		String fileName = fileComponent(getFileNameFromImageList(t));

		if (fileName == null) {
			timeInMs = timePatternArray[0].getDummyTime(t);
		} else {
			if (indexTimePattern < 0) {
				indexTimePattern = findProperFilterIfAny(fileName);
			}
			FileNameTimePattern tp = timePatternArray[indexTimePattern];
			timeInMs = tp.getTimeFromString(fileName, t);
		}

		FileTime fileTime = FileTime.fromMillis(timeInMs);
		return fileTime;
	}

	int findProperFilterIfAny(String fileName) {
		int index = 0;
		for (int i = 1; i < timePatternArray.length; i++) {
			if (timePatternArray[i].findMatch(fileName))
				return i;
		}
		return index;
	}

	public FileTime getFileTimeFromFileAttributes(int t) {
		FileTime filetime = null;
		File file = new File(getFileNameFromImageList(t));
		Path filePath = file.toPath();

		BasicFileAttributes attributes = null;
		try {
			attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
		} catch (IOException exception) {
			System.out.println("SeqCamData:getFileTimeFromFileAttributes() Exception handled when trying to get file "
					+ "attributes: " + exception.getMessage());
		}

		long milliseconds = attributes.creationTime().to(TimeUnit.MILLISECONDS);
		if ((milliseconds > Long.MIN_VALUE) && (milliseconds < Long.MAX_VALUE)) {
			Date creationDate = new Date(attributes.creationTime().to(TimeUnit.MILLISECONDS));
			filetime = FileTime.fromMillis(creationDate.getTime());
		}
		return filetime;
	}

	public FileTime getFileTimeFromJPEGMetaData(int t) {
		FileTime filetime = null;
		File file = new File(getFileNameFromImageList(t));
		Metadata metadata;
		try {
			metadata = ImageMetadataReader.readMetadata(file);
			ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
			filetime = FileTime.fromMillis(date.getTime());
		} catch (ImageProcessingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return filetime;
	}

	public void displayViewerAtRectangle(Rectangle parent0Rect) {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					ViewerFMP v = (ViewerFMP) seq.getFirstViewer();
					if (v == null)
						v = new ViewerFMP(seq, true, true);
					Rectangle rectv = v.getBoundsInternal();
					rectv.setLocation(parent0Rect.x + parent0Rect.width, parent0Rect.y);
					v.setBounds(rectv);
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	// ------------------------

	public void closeSequence() {
		if (seq == null)
			return;

		seq.removeAllROI();
		seq.close();
	}

	public List<String> getImagesList() {
		return imagesList;
	}

	public void setImagesList(List<String> extImagesList) {
		imagesList.clear();
		imagesList = new ArrayList<String>(extImagesList);
		nTotalFrames = imagesList.size();
	}

	public void attachSequence(Sequence seq) {
		this.seq = seq;
		status = EnumStatus.FILESTACK;
		seqAnalysisStart = 0;
	}

	public IcyBufferedImage imageIORead(String name) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(name));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return IcyBufferedImage.createFrom(image);
	}

	public Sequence loadSequenceFromImagesList(List<String> imagesList) {
		SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(imagesList.get(0), true);

		List<Sequence> sequenceList = Loader.loadSequences(seqFileImporter, imagesList, 0, // series index to load
				true, // force volatile
				false, // separate
				false, // auto-order
				true, // directory
				false, // add to recent
				true // show progress
		);
		if (sequenceList.size() == 1)
			seq = sequenceList.get(0);
		else {
			System.out.println("list of sequences size=" + sequenceList.size());
			seq = sequenceList.get(0);
		}

		return seq;
	}

	public Sequence initSequenceFromFirstImage(List<String> imagesList) {
		SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(imagesList.get(0), true);
		Sequence seq = Loader.loadSequence(seqFileImporter, imagesList.get(0), 0, false); // show progress
		return seq;
	}

	// -------------------------

	public void displaySpecificROIs(boolean isVisible, String pattern) {
		Viewer v = seq.getFirstViewer();
		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null)
			return;
		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null)
				continue;
			String cs = roi.getName();
			if (cs.contains(pattern))
				layer.setVisible(isVisible);
		}
	}

}