package plugins.fmp.multitools.series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;

public class DetectFlyFromCleanBackground extends BuildSeries {
	private DetectFlyTools find_flies = new DetectFlyTools();
	public boolean viewInternalImages = true;

	// -----------------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!loadSeqCamDataAndCages(exp))
			return;
		if (!checkBoundsForCages(exp))
			return;

		runFlyDetect2(exp);
		exp.getCages().orderFlyPositions();
		if (!stopFlag)
			exp.save_cagesFliesPositions();
		exp.getSeqCamData().closeSequence();
		closeSequence(seqNegative);
		closeViewer(vNegative);
	}

	private void runFlyDetect2(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage);
		options.threshold = options.thresholdDiff;

		if (exp.loadReferenceImage()) {
			openFlyDetectViewers1(exp);
			findFliesInAllFrames(exp);
		}
	}

	private void findFliesInAllFrames(Experiment exp) {
		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = ImageTransformEnums.SUBTRACT_REF;
		transformOptions.backgroundImage = IcyBufferedImageUtil.getCopy(exp.getSeqCamData().getReferenceImage());
		ImageTransformInterface transformFunction = transformOptions.transformOption.getFunction();

		int totalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		for (int index = 0; index < totalFrames; index++) {
			int t_from = index;
			String title = "Frame #" + t_from + "/" + exp.getSeqCamData().getImageLoader().getNTotalFrames();
			progressBar.setMessage(title);

			IcyBufferedImage workImage = imageIORead(exp.getSeqCamData().getFileNameFromImageList(t_from));
			IcyBufferedImage negativeImage = transformFunction.getTransformedImage(workImage, transformOptions);
			try {
				seqNegative.beginUpdate();
				seqNegative.setImage(0, 0, negativeImage);
				vNegative.setTitle(title);
				List<Rectangle2D> listRectangles = find_flies.findFlies(negativeImage, t_from);
				displayRectanglesAsROIs1(seqNegative, listRectangles, true);
				seqNegative.endUpdate();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		progressBar.close();
	}

}