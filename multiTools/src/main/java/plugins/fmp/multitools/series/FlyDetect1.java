package plugins.fmp.multitools.series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

public class FlyDetect1 extends FlyDetect {
	public boolean buildBackground = true;
	public boolean detectFlies = true;

	// -----------------------------------------------------

	/**
	 * Same transform chain as {@link #findFliesInAllFrames} for frame index {@code t} (file list based).
	 */
	public static IcyBufferedImage transformFrameForFlyDetect1(Experiment exp, BuildSeriesOptions options, int t) {
		if (exp == null || exp.getSeqCamData() == null)
			return null;
		SequenceLoaderService loader = new SequenceLoaderService();
		String path = exp.getSeqCamData().getFileNameFromImageList(t);
		if (path == null)
			return null;
		IcyBufferedImage workImage = loader.imageIORead(path);
		if (workImage == null)
			return null;

		ImageTransformEnums src = options.flyDetectSourceTransform != null ? options.flyDetectSourceTransform
				: ImageTransformEnums.NONE;
		ImageTransformEnums bg = options.flyDetectBackgroundTransform != null ? options.flyDetectBackgroundTransform
				: ImageTransformEnums.NONE;

		if (src == ImageTransformEnums.NONE && bg == ImageTransformEnums.NONE) {
			CanvasImageTransformOptions to = new CanvasImageTransformOptions();
			to.transformOption = options.transformop != null ? options.transformop : ImageTransformEnums.NONE;
			to.backgroundImage = null;
			fillSingleStepBackgroundOptions(exp, t, to);
			return to.transformOption.getFunction().getTransformedImage(workImage, to);
		}

		CanvasImageTransformOptions bgOpts = new CanvasImageTransformOptions();
		bgOpts.transformOption = bg;
		bgOpts.backgroundImage = null;
		fillFlyDetectBackgroundOptions(exp, t, bgOpts);

		IcyBufferedImage afterBg = workImage;
		if (bg != ImageTransformEnums.NONE) {
			IcyBufferedImage sub = bg.getFunction().getTransformedImage(workImage, bgOpts);
			if (sub == null)
				return null;
			afterBg = sub;
		}

		if (src == ImageTransformEnums.NONE)
			return afterBg;

		CanvasImageTransformOptions srcOpts = new CanvasImageTransformOptions();
		srcOpts.transformOption = src;
		return src.getFunction().getTransformedImage(afterBg, srcOpts);
	}

	static void fillFlyDetectBackgroundOptions(Experiment exp, int t, CanvasImageTransformOptions bgOpts) {
		SequenceLoaderService loader = new SequenceLoaderService();
		switch (bgOpts.transformOption) {
		case SUBTRACT_TM1:
			if (t > 0)
				bgOpts.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t - 1));
			else
				bgOpts.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case SUBTRACT_T0:
		case SUBTRACT_REF:
			if (bgOpts.backgroundImage == null)
				bgOpts.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case NONE:
		default:
			break;
		}
	}

	static void fillSingleStepBackgroundOptions(Experiment exp, int t, CanvasImageTransformOptions options) {
		SequenceLoaderService loader = new SequenceLoaderService();
		switch (options.transformOption) {
		case SUBTRACT_TM1:
			if (t > 0)
				options.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t - 1));
			else
				options.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case SUBTRACT_T0:
		case SUBTRACT_REF:
			if (options.backgroundImage == null)
				options.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case NONE:
		default:
			break;
		}
	}

	@Override
	protected void runFlyDetect(Experiment exp) {
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage, exp.getFlyMmPerPixelX(), exp.getFlyMmPerPixelY());

		openFlyDetectViewers1(exp);
		findFliesInAllFrames(exp);
	}

	@Override
	protected void findFliesInAllFrames(Experiment exp) {
		ImageTransformEnums src = options.flyDetectSourceTransform != null ? options.flyDetectSourceTransform
				: ImageTransformEnums.NONE;
		ImageTransformEnums bg = options.flyDetectBackgroundTransform != null ? options.flyDetectBackgroundTransform
				: ImageTransformEnums.NONE;
		if (src == ImageTransformEnums.NONE && bg == ImageTransformEnums.NONE) {
			super.findFliesInAllFrames(exp);
			return;
		}

		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");
		int totalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		SequenceLoaderService loader = new SequenceLoaderService();

		for (int index = 0; index < totalFrames; index++) {
			if (stopFlag)
				break;
			int t = index;
			String title = "Frame #" + t + "/" + totalFrames;
			progressBar.setMessage(title);

			IcyBufferedImage workImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t));

			CanvasImageTransformOptions bgOpts = new CanvasImageTransformOptions();
			bgOpts.transformOption = bg;
			bgOpts.backgroundImage = null;
			fillFlyDetectBackgroundOptions(exp, t, bgOpts);

			IcyBufferedImage afterBg = workImage;
			if (bg != ImageTransformEnums.NONE) {
				ImageTransformInterface bgFn = bg.getFunction();
				afterBg = bgFn.getTransformedImage(workImage, bgOpts);
			}

			CanvasImageTransformOptions srcOpts = new CanvasImageTransformOptions();
			srcOpts.transformOption = src;
			IcyBufferedImage negativeImage = afterBg;
			if (src != ImageTransformEnums.NONE) {
				ImageTransformInterface srcFn = src.getFunction();
				negativeImage = srcFn.getTransformedImage(afterBg, srcOpts);
			}

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
		}
		progressBar.close();
	}

	@Override
	protected CanvasImageTransformOptions setupTransformOptions(Experiment exp) {
		CanvasImageTransformOptions transformOptions = new CanvasImageTransformOptions();
		transformOptions.transformOption = options.transformop;
		return transformOptions;
	}

	@Override
	protected void updateTransformOptions(Experiment exp, int t, int t_previous, CanvasImageTransformOptions options) {
		fillSingleStepBackgroundOptions(exp, t, options);
	}
}
