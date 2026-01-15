package plugins.fmp.multitools.series;

import icy.image.IcyBufferedImageUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformOptions;

public class FlyDetect2 extends FlyDetect {
	public boolean viewInternalImages = true;

	// -----------------------------------------

	@Override
	protected void runFlyDetect(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage);

		options.threshold = options.thresholdDiff;
		if (new SequenceLoaderService().loadReferenceImage(exp)) {
			openFlyDetectViewers1(exp);
			findFliesInAllFrames(exp);
		}
	}

	@Override
	protected ImageTransformOptions setupTransformOptions(Experiment exp) {
		ImageTransformOptions transformOptions = new ImageTransformOptions();
		transformOptions.transformOption = ImageTransformEnums.SUBTRACT_REF;
		transformOptions.backgroundImage = IcyBufferedImageUtil.getCopy(exp.getSeqCamData().getReferenceImage());
		return transformOptions;
	}
}
