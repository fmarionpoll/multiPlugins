package plugins.fmp.multitools.fmp_series;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformOptions;

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

		openFlyDetectViewers1(exp);
		runSpotsDetect(exp);
		if (!stopFlag)
			exp.save_cages_description_and_measures();

		exp.getSeqCamData().closeSequence();
		closeSequence(seqNegative);
	}

	private void runSpotsDetect(Experiment exp) {
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		ImageTransformInterface transformFunction = options.transformop.getFunction();
		int t_from = (int) options.fromFrame;
		String fileName = exp.getSeqCamData().getFileNameFromImageList(t_from);

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
