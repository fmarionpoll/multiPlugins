package plugins.fmp.multitools.fmp_series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.ExperimentDirectories;
import plugins.fmp.multitools.fmp_experiment.cages.Cage;
import plugins.fmp.multitools.fmp_service.SequenceLoaderService;
import plugins.fmp.multitools.fmp_tools.Logger;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformOptions;

public abstract class FlyDetect extends BuildSeries {
	public DetectFlyTools find_flies = new DetectFlyTools();

	@Override
	void analyzeExperiment(Experiment exp) {
		if (!loadDrosoTrack2(exp))
			return;
		if (!checkBoundsForCages(exp))
			return;
		if (!checkCagesForFlyDetection(exp))
			return;

		runFlyDetect(exp);
		exp.getCages().orderFlyPositions();
		if (!stopFlag)
			exp.saveCagesMeasures();
		exp.getSeqCamData().closeSequence();
		closeSequence(seqNegative);
	}

	protected abstract void runFlyDetect(Experiment exp);

	protected boolean checkCagesForFlyDetection(Experiment exp) {
		if (exp.getCages() == null || exp.getCages().cagesList == null || exp.getCages().cagesList.isEmpty()) {
			Logger.error("FlyDetect:checkCagesForFlyDetection() No cages loaded for experiment: "
					+ exp.getResultsDirectory());
			return false;
		}

		int cagesWithFlies = 0;
		int targetCageID = options.detectCage;

		for (Cage cage : exp.getCages().cagesList) {
			if (cage.getProperties().getCageNFlies() > 0) {
				cagesWithFlies++;
				if (targetCageID != -1 && cage.getProperties().getCageID() == targetCageID) {
					Logger.info("FlyDetect:checkCagesForFlyDetection() Found target cage " + targetCageID + " with "
							+ cage.getProperties().getCageNFlies() + " fly(ies)");
					return true;
				}
			}
		}

		if (cagesWithFlies == 0) {
			Logger.error("FlyDetect:checkCagesForFlyDetection() No cages with flies (nFlies > 0) found. All "
					+ exp.getCages().cagesList.size() + " cages have nFlies = 0. Experiment: "
					+ exp.getResultsDirectory());
			return false;
		}

		if (targetCageID != -1) {
			Logger.error("FlyDetect:checkCagesForFlyDetection() Target cage " + targetCageID
					+ " not found or has nFlies = 0. Found " + cagesWithFlies
					+ " cage(s) with flies, but not the target cage.");
			return false;
		}

		Logger.info("FlyDetect:checkCagesForFlyDetection() Found " + cagesWithFlies + " cage(s) with flies out of "
				+ exp.getCages().cagesList.size() + " total cages");
		return true;
	}

	protected void findFliesInAllFrames(Experiment exp) {
		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");

		ImageTransformOptions transformOptions = setupTransformOptions(exp);
		ImageTransformInterface transformFunction = transformOptions.transformOption.getFunction();

		int t_previous = 0;
		int totalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		SequenceLoaderService loader = new SequenceLoaderService();

		for (int index = 0; index < totalFrames; index++) {
			if (stopFlag)
				break;
			int t = index;
			String title = "Frame #" + t + "/" + totalFrames;
			progressBar.setMessage(title);

			IcyBufferedImage workImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t));
			updateTransformOptions(exp, t, t_previous, transformOptions);

			IcyBufferedImage negativeImage = transformFunction.getTransformedImage(workImage, transformOptions);
			try {
				seqNegative.beginUpdate();
				seqNegative.setImage(0, 0, negativeImage);
				vNegative.setTitle(title);
				List<Rectangle2D> listRectangles = find_flies.findFlies(negativeImage, t);
				displayRectanglesAsROIs1(seqNegative, listRectangles, true);
				seqNegative.endUpdate();
			} catch (Exception e) {
				e.printStackTrace();
			}
			t_previous = t;
		}
		progressBar.close();
	}

	protected abstract ImageTransformOptions setupTransformOptions(Experiment exp);

	protected void updateTransformOptions(Experiment exp, int t, int t_previous, ImageTransformOptions options) {
		// default does nothing
	}

	protected boolean loadDrosoTrack2(Experiment exp) {
		List<String> imagesList = exp.getSeqCamData().getImagesList(true);
		
		// If images list is empty, load it from the experiment's images directory
		if (imagesList == null || imagesList.isEmpty()) {
			String imagesDirectory = exp.getImagesDirectory();
			if (imagesDirectory == null || imagesDirectory.isEmpty()) {
				// Try to get images directory from results directory
				String resultsDir = exp.getResultsDirectory();
				if (resultsDir != null) {
					imagesDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(resultsDir);
					exp.setImagesDirectory(imagesDirectory);
				}
			}
			
			if (imagesDirectory != null && !imagesDirectory.isEmpty()) {
				imagesList = ExperimentDirectories.getImagesListFromPathV2(imagesDirectory, "jpg");
				if (imagesList != null && !imagesList.isEmpty()) {
					exp.getSeqCamData().setImagesList(imagesList);
					exp.getSeqCamData().getImageLoader().setImagesDirectory(imagesDirectory);
				} else {
					Logger.error("FlyDetect:loadDrosoTrack2() No images found in directory: " + imagesDirectory);
					return false;
				}
			} else {
				Logger.error("FlyDetect:loadDrosoTrack2() Could not determine images directory for experiment: " + exp.getResultsDirectory());
				return false;
			}
		}
		
		Sequence seq = new SequenceLoaderService().initSequenceFromFirstImage(imagesList);
		if (seq == null) {
			Logger.error("FlyDetect:loadDrosoTrack2() Failed to initialize sequence from images list");
			return false;
		}
		exp.getSeqCamData().setSequence(seq);
		boolean flag = exp.loadCagesMeasures();
//		// CRITICAL: Also load capillaries to prevent them from being overwritten as empty
//		// when save operations are triggered (e.g., closeViewsForCurrentExperiment)
//		// This protects kymograph measures from being erased during fly detection
//		exp.loadMCCapillaries_Only();
//		if (exp.getKymosBinFullDirectory() != null) {
//			exp.getCapillaries().load_Capillaries(exp.getKymosBinFullDirectory());
//		}
		return flag;
	}
}
