package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import icy.file.Loader;
import icy.file.SequenceFileImporter;
import icy.image.IcyBufferedImage;
import icy.image.ImageUtil;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;

public class SequenceLoaderService {
	public enum ReferenceImageKind {
		DEFAULT, LIGHT, DARK
	}

	public static String getReferenceImageFilename(ReferenceImageKind kind) {
		if (kind == null) {
			kind = ReferenceImageKind.DEFAULT;
		}
		switch (kind) {
		case LIGHT:
			return "referenceImage_light.jpg";
		case DARK:
			return "referenceImage_dark.jpg";
		case DEFAULT:
		default:
			return "referenceImage.jpg";
		}
	}

	public boolean loadReferenceImage(Experiment exp) {
		return loadReferenceImage(exp, ReferenceImageKind.DEFAULT);
	}

	public boolean loadReferenceImage(Experiment exp, ReferenceImageKind kind) {
		BufferedImage image = null;
		String filename = getReferenceImageFilename(kind);
		String path = exp.getExperimentDirectory() + File.separator + filename;
		File inputfile = new File(path);
		boolean exists = inputfile.exists();
		if (!exists)
			return false;
		image = ImageUtil.load(inputfile, true);
		if (image == null) {
			Logger.warn("SequenceLoaderService:loadReferenceImage() image not loaded / not found: " + path);
			return false;
		}
		IcyBufferedImage icy = IcyBufferedImage.createFrom(image);
		if (kind == null) {
			kind = ReferenceImageKind.DEFAULT;
		}
		switch (kind) {
		case LIGHT:
			exp.getSeqCamData().setReferenceImageLight(icy);
			break;
		case DARK:
			exp.getSeqCamData().setReferenceImageDark(icy);
			break;
		case DEFAULT:
		default:
			exp.getSeqCamData().setReferenceImage(icy);
			exp.setSeqReference(new Sequence(exp.getSeqCamData().getReferenceImage()));
			exp.getSeqReference().setName("referenceImage");
			break;
		}
		return true;
	}

	public boolean saveReferenceImage(Experiment exp) {
		return saveReferenceImage(exp, ReferenceImageKind.DEFAULT);
	}

	public boolean saveReferenceImage(Experiment exp, ReferenceImageKind kind) {
		if (exp == null || exp.getSeqCamData() == null) {
			Logger.warn("SequenceLoaderService:saveReferenceImage() experiment or seqCamData is null");
			return false;
		}
		
		if (kind == null) {
			kind = ReferenceImageKind.DEFAULT;
		}
		IcyBufferedImage referenceImage = null;
		switch (kind) {
		case LIGHT:
			referenceImage = exp.getSeqCamData().getReferenceImageLight();
			break;
		case DARK:
			referenceImage = exp.getSeqCamData().getReferenceImageDark();
			break;
		case DEFAULT:
		default:
			referenceImage = exp.getSeqCamData().getReferenceImage();
			break;
		}
		if (referenceImage == null) {
			Logger.warn("SequenceLoaderService:saveReferenceImage() reference image is null");
			return false;
		}
		
		String filename = getReferenceImageFilename(kind);
		String path = exp.getExperimentDirectory() + File.separator + filename;
		File outputfile = new File(path);
		File parentDir = outputfile.getParentFile();
		
		if (parentDir != null && !parentDir.exists()) {
			Logger.info("SequenceLoaderService: Creating directory for reference image: " + parentDir.getPath());
			if (!parentDir.mkdirs()) {
				Logger.error("SequenceLoaderService: Failed to create directory for reference image: " + parentDir.getPath());
				return false;
			}
		}
		
		Logger.info("SequenceLoaderService: Saving reference image to: " + path);
		RenderedImage image = ImageUtil.toRGBImage(referenceImage);
		boolean success = ImageUtil.save(image, "jpg", outputfile);
		
		if (!success) {
			Logger.error("SequenceLoaderService: Failed to save reference image to: " + path);
		} else {
			Logger.info("SequenceLoaderService: Reference image saved successfully to: " + path);
		}
		
		return success;
	}

	public boolean loadImages(SequenceCamData seqData) {
		if (seqData.getImagesList().size() == 0)
			return false;
		seqData.attachSequence(loadSequenceFromImagesList(seqData.getImagesList()));
		return (seqData.getSequence() != null);
	}

	public boolean loadFirstImage(SequenceCamData seqData) {
		if (seqData.getImagesList().size() == 0)
			return false;
		List<String> dummyList = new ArrayList<String>();
		dummyList.add(seqData.getImagesList().get(0));
		seqData.attachSequence(loadSequenceFromImagesList(dummyList));
		return (seqData.getSequence() != null);
	}

	public Sequence loadSequenceFromImagesList(List<String> imagesList) {
		SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(imagesList.get(0), true);
		Sequence seq = Loader.loadSequences(seqFileImporter, imagesList, 0, // series index to load
				true, // force volatile
				false, // separate
				false, // auto-order
				false, // directory
				false, // add to recent
				false // show progress
		).get(0);
		return seq;
	}

	public Sequence initSequenceFromFirstImage(List<String> imagesList) {
		SequenceFileImporter seqFileImporter = Loader.getSequenceFileImporter(imagesList.get(0), true);
		Sequence seq = Loader.loadSequence(seqFileImporter, imagesList.get(0), 0, false);
		return seq;
	}

	public void loadImageList(SequenceCamData seqData) {
		List<String> imagesList = ExperimentDirectories.getV2ImagesListFromPath(seqData.getImagesDirectory());
		String[] strExt = {"jpg"};
		imagesList = ExperimentDirectories.keepOnlyAcceptedNames_List(imagesList, strExt);
		if (imagesList.size() > 0) {
			seqData.setImagesList(imagesList);
			seqData.attachSequence(loadSequenceFromImagesList(imagesList));
		}
	}

	public IcyBufferedImage imageIORead(String name) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new File(name));
		} catch (IOException e) {
			Logger.error("SequenceLoaderService:imageIORead() Failed to read image: " + name, e);
		}
		return IcyBufferedImage.createFrom(image);
	}
}
