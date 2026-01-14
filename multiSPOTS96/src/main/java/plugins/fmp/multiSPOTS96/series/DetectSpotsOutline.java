package plugins.fmp.multiSPOTS96.series;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS96.experiment.Experiment;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class DetectSpotsOutline extends BuildSeries {
	public boolean buildBackground = true;
	public boolean detectFlies = true;
	public DetectSpotsTools find_spots = new DetectSpotsTools();

	// -----------------------------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!loadSeqCamDataAndCages(exp))
			return;
		if (!checkBoundsForCages(exp))
			return;

		openFlyDetectViewers(exp);
		runSpotsDetect(exp);
		if (!stopFlag)
			exp.save_MS96_cages();

		exp.seqCamData.closeSequence();
		closeSequence(seqNegative);
	}

	private void runSpotsDetect(Experiment exp) {
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		ImageTransformInterface transformFunction = options.transformop.getFunction();
		int t_from = (int) options.fromFrame;
		String fileName = exp.seqCamData.getFileNameFromImageList(t_from);

		ProgressFrame progressBar = new ProgressFrame("Detecting spots from " + fileName);
		IcyBufferedImage sourceImage = imageIORead(fileName);
		IcyBufferedImage workImage = transformFunction.getTransformedImage(sourceImage, transformOptions);

		seqNegative.setImage(0, 0, workImage);
		vNegative.setTitle("frame " + t_from);

		try {
			find_spots.findSpots(exp, seqNegative, options, workImage);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		progressBar.close();
	}
}
