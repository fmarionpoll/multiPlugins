package plugins.fmp.multitools.fmp_service;

import java.util.List;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.ExperimentDirectories;
import plugins.fmp.multitools.fmp_experiment.ImageFileDescriptor;
import plugins.fmp.multitools.fmp_experiment.sequence.ImageFileData;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceKymos;

public class ExperimentService {

	public void closeSequences(Experiment exp) {
		if (exp.getSeqKymos() != null) {
			exp.getSeqKymos().closeSequence();
		}
		if (exp.getSeqCamData() != null) {
			exp.getSeqCamData().closeSequence();
		}
		if (exp.getSeqReference() != null) {
			exp.getSeqReference().close();
		}
	}

	public SequenceCamData openSequenceCamData(Experiment exp) {
		loadImagesForSequenceCamData(exp, exp.getImagesDirectory());
		if (exp.getSeqCamData() != null) {
			exp.xmlLoad_MCExperiment();
			exp.getFileIntervalsFromSeqCamData();
		}
		return exp.getSeqCamData();
	}

	private SequenceCamData loadImagesForSequenceCamData(Experiment exp, String filename) {
		String imagesDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(filename);
		exp.setImagesDirectory(imagesDirectory);
		List<String> imagesList = ExperimentDirectories.getV2ImagesListFromPath(imagesDirectory);
		String[] strExt = { "jpg" };
		imagesList = ExperimentDirectories.keepOnlyAcceptedNames_List(imagesList, strExt);
		if (imagesList.size() < 1) {
			exp.setSeqCamData(null);
		} else {
			SequenceCamData seqCamData = new SequenceCamData();
			seqCamData.setImagesList(imagesList);
			seqCamData.attachSequence(seqCamData.getImageLoader().loadSequenceFromImagesList(imagesList));
			exp.setSeqCamData(seqCamData);
		}
		return exp.getSeqCamData();
	}

	public boolean loadCamDataImages(Experiment exp) {
		if (exp.getSeqCamData() != null)
			exp.getSeqCamData().loadImages();

		return (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null);
	}

	public boolean loadCamDataCapillaries(Experiment exp) {
		exp.loadMCCapillaries_Only();
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
			exp.getCapillaries().transferCapillaryRoiToSequence(exp.getSeqCamData().getSequence());

		return (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null);
	}

	public boolean loadKymographs(Experiment exp) {
		if (exp.getSeqKymos() == null)
			exp.setSeqKymos(new SequenceKymos());
		String fullDir = exp.getKymosBinFullDirectory();
		List<ImageFileData> myList = new KymographService().loadListOfPotentialKymographsFromCapillaries(fullDir,
				exp.getCapillaries());

		ImageFileDescriptor.getExistingFileNames(myList);
		return new KymographService().loadImagesFromList(exp.getSeqKymos(), myList, true);
	}
}
