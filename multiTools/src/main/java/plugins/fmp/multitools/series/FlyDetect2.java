package plugins.fmp.multitools.series;

import icy.image.IcyBufferedImageUtil;
import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class FlyDetect2 extends FlyDetect {
	public boolean viewInternalImages = true;

	// -----------------------------------------

	@Override
	protected void runFlyDetect(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage, exp.getFlyMmPerPixelX(), exp.getFlyMmPerPixelY());

		options.threshold = options.thresholdDiff;
		if (ensureBackgroundsLoaded(exp)) {
			openFlyDetectViewers1(exp);
			findFliesInAllFrames(exp);
		}
	}

	private boolean ensureBackgroundsLoaded(Experiment exp) {
		SequenceLoaderService loader = new SequenceLoaderService();
		if (options.dualBackground) {
			boolean okLight = loader.loadReferenceImage(exp, SequenceLoaderService.ReferenceImageKind.LIGHT);
			boolean okDark = loader.loadReferenceImage(exp, SequenceLoaderService.ReferenceImageKind.DARK);
			if (okLight || okDark) {
				return true;
			}
		}
		return loader.loadReferenceImage(exp, SequenceLoaderService.ReferenceImageKind.DEFAULT);
	}

	@Override
	protected CanvasImageTransformOptions setupTransformOptions(Experiment exp) {
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		transformOptions.transformOption = ImageTransformEnums.SUBTRACT_REF;
		transformOptions.backgroundImage = IcyBufferedImageUtil.getCopy(exp.getSeqCamData().getReferenceImage());
		return transformOptions;
	}

	@Override
	protected void updateTransformOptions(Experiment exp, int t, int t_previous, CanvasImageTransformOptions options,
			IcyBufferedImage workImage) {
		if (!this.options.dualBackground) {
			return;
		}

		IcyBufferedImage light = exp.getSeqCamData().getReferenceImageLight();
		IcyBufferedImage dark = exp.getSeqCamData().getReferenceImageDark();
		if (light == null && dark == null) {
			return;
		}
		if (light == null) {
			options.backgroundImage = dark;
			return;
		}
		if (dark == null) {
			options.backgroundImage = light;
			return;
		}

		int phase = IlluminationPhase.fromFrameForDualBackground(workImage, this.options.rednessThreshold);
		options.backgroundImage = (phase == IlluminationPhase.DARK) ? dark : light;
	}
}
