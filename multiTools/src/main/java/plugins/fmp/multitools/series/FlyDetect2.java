package plugins.fmp.multitools.series;

import icy.image.IcyBufferedImageUtil;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
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

		double r = computeRednessRatio(workImage, 16);
		options.backgroundImage = (r >= this.options.rednessThreshold) ? dark : light;
	}

	private static double computeRednessRatio(IcyBufferedImage img, int step) {
		if (img == null) {
			return 0.0;
		}
		int w = img.getSizeX();
		int h = img.getSizeY();
		int c = img.getSizeC();
		if (c < 3) {
			return 0.0;
		}
		if (step < 1) {
			step = 1;
		}

		double sum = 0.0;
		int n = 0;
		IcyBufferedImageCursor cur = new IcyBufferedImageCursor(img);
		for (int y = 0; y < h; y += step) {
			for (int x = 0; x < w; x += step) {
				double rr = cur.get(x, y, 0);
				double gg = cur.get(x, y, 1);
				double bb = cur.get(x, y, 2);
				double denom = rr + gg + bb + 1e-9;
				sum += (rr / denom);
				n++;
			}
		}
		return n > 0 ? (sum / n) : 0.0;
	}
}
