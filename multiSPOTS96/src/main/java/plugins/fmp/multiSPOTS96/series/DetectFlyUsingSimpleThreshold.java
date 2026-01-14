package plugins.fmp.multiSPOTS96.series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class DetectFlyUsingSimpleThreshold extends BuildSeries {
	public boolean buildBackground = true;
	public boolean detectFlies = true;
	public DetectFlyTools find_flies = new DetectFlyTools();

	// -----------------------------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!loadSeqCamDataAndCages(exp))
			return;
		if (!checkBoundsForCages(exp))
			return;

		runFlyDetect1(exp);
		exp.cagesArray.orderFlyPositions();
		if (!stopFlag)
			exp.save_MS96_fliesPositions();
		exp.seqCamData.closeSequence();
		closeSequence(seqNegative);
	}

	private void runFlyDetect1(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.cagesArray.initFlyPositions(options.detectCage);

		openFlyDetectViewers(exp);
		findFliesInAllFrames(exp);
	}

	private void getReferenceImage(Experiment exp, int t, ImageTransformOptions options) {
		switch (options.transformOption) {
		case SUBTRACT_TM1:
			options.backgroundImage = imageIORead(exp.seqCamData.getFileNameFromImageList(t));
			break;

		case SUBTRACT_T0:
		case SUBTRACT_REF:
			if (options.backgroundImage == null)
				options.backgroundImage = imageIORead(exp.seqCamData.getFileNameFromImageList(0));
			break;

		case NONE:
		default:
			break;
		}
	}

	private void findFliesInAllFrames(Experiment exp) {
		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		ImageTransformInterface transformFunction = options.transformop.getFunction();

		int t_previous = 0;
		int totalFrames = exp.seqCamData.getImageLoader().getNTotalFrames();

		for (int index = 0; index < totalFrames; index++) {
			int t_from = index;
			String title = "Frame #" + t_from + "/" + exp.seqCamData.getImageLoader().getNTotalFrames();
			progressBar.setMessage(title);

			IcyBufferedImage sourceImage = imageIORead(exp.seqCamData.getFileNameFromImageList(t_from));
			getReferenceImage(exp, t_previous, transformOptions);
			IcyBufferedImage workImage = transformFunction.getTransformedImage(sourceImage, transformOptions);
			try {
				seqNegative.beginUpdate();
				seqNegative.setImage(0, 0, workImage);
				vNegative.setTitle(title);
				List<Rectangle2D> listRectangles = find_flies.findFlies(workImage, t_from);
				displayRectanglesAsROIs(seqNegative, listRectangles, true);
				seqNegative.endUpdate();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			t_previous = t_from;
		}

		progressBar.close();
	}
}