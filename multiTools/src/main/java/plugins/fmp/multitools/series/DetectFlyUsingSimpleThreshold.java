package plugins.fmp.multitools.series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

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
		exp.getCages().orderFlyPositions();
		if (!stopFlag)
			exp.save_cagesFliesPositions();
		exp.getSeqCamData().closeSequence();
		closeSequence(seqNegative);
	}

	private void runFlyDetect1(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage);

		openFlyDetectViewers1(exp);
		findFliesInAllFrames(exp);
	}

	private void getReferenceImage(Experiment exp, int t, CanvasImageTransformOptions options) {
		switch (options.transformOption) {
		case SUBTRACT_TM1:
			options.backgroundImage = imageIORead(exp.getSeqCamData().getFileNameFromImageList(t));
			break;

		case SUBTRACT_T0:
		case SUBTRACT_REF:
			if (options.backgroundImage == null)
				options.backgroundImage = imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case NONE:
		default:
			break;
		}
	}

	private void findFliesInAllFrames(Experiment exp) {
		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		ImageTransformInterface transformFunction = options.transformop.getFunction();

		int t_previous = 0;
		int totalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();

		for (int index = 0; index < totalFrames; index++) {
			int t_from = index;
			String title = "Frame #" + t_from + "/" + exp.getSeqCamData().getImageLoader().getNTotalFrames();
			progressBar.setMessage(title);

			IcyBufferedImage sourceImage = imageIORead(exp.getSeqCamData().getFileNameFromImageList(t_from));
			getReferenceImage(exp, t_previous, transformOptions);
			IcyBufferedImage workImage = transformFunction.getTransformedImage(sourceImage, transformOptions);
			try {
				seqNegative.beginUpdate();
				seqNegative.setImage(0, 0, workImage);
				vNegative.setTitle(title);
				List<Rectangle2D> listRectangles = find_flies.findFlies(workImage, t_from);
				displayRectanglesAsROIs1(seqNegative, listRectangles, true);
				seqNegative.endUpdate();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			t_previous = t_from;
		}

		progressBar.close();
	}
}