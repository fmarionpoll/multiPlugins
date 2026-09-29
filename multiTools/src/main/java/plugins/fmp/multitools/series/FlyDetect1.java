package plugins.fmp.multitools.series;

import java.awt.geom.Rectangle2D;
import java.util.List;

import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformBase;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.transforms.PatchPreviousFromReference;
import plugins.fmp.multitools.tools.imageTransform.transforms.SubtractReferenceImage.DifferencePolarity;
import plugins.fmp.multitools.tools.Logger;

public class FlyDetect1 extends FlyDetect {
	public boolean buildBackground = true;
	public boolean detectFlies = true;

	// -----------------------------------------------------

	public static final String MISSING_REFERENCE_WARNING = "Fly-free reference image not found. Build the background before using ref or clean(t-1).";

	private static IcyBufferedImage cachedFixedComparison;
	private static IcyBufferedImage cachedFixedRaw;
	private static Experiment cachedFixedExperiment;
	private static ImageTransformEnums cachedFixedBackground;
	private static ImageTransformEnums cachedFixedSource;

	/**
	 * Source-filtered frame {@code t}. No background subtraction: blob shape comes from this image.
	 */
	public static IcyBufferedImage transformFrameForFlyDetect1(Experiment exp, BuildSeriesOptions options, int t) {
		IcyBufferedImage raw = readCamFrame(exp, t);
		if (raw == null)
			return null;
		return applySourceTransform(raw, options);
	}

	/**
	 * Source-filtered comparison image for {@link BuildSeriesOptions#flyDetectBackgroundTransform},
	 * or null when the choice is {@code none} or the required reference is missing.
	 */
	public static IcyBufferedImage comparisonFrameForFlyDetect1(Experiment exp, BuildSeriesOptions options, int t) {
		ImageTransformEnums bg = backgroundChoice(options);
		if (bg == ImageTransformEnums.NONE)
			return null;
		if (comparisonRequiresReference(bg) && !comparisonReferenceReady(exp, bg))
			return null;
		if (bg == ImageTransformEnums.SUBTRACT_REF || bg == ImageTransformEnums.SUBTRACT_T0)
			return cachedFixedComparison(exp, options, bg);
		IcyBufferedImage raw = loadVaryingComparison(exp, options, t, bg);
		if (raw == null)
			return null;
		return applySourceTransform(raw, options);
	}

	public static boolean comparisonRequiresReference(ImageTransformEnums bg) {
		return bg == ImageTransformEnums.SUBTRACT_REF || bg == ImageTransformEnums.SUBTRACT_TM1_CLEAN;
	}

	/** Loads {@code referenceImage} when {@code bg} needs it. Returns true when no reference is required. */
	public static boolean comparisonReferenceReady(Experiment exp, ImageTransformEnums bg) {
		if (!comparisonRequiresReference(bg))
			return true;
		if (exp == null)
			return false;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getReferenceImage() != null)
			return true;
		return exp.loadReferenceImage();
	}

	public static boolean comparisonReferenceReady(Experiment exp, BuildSeriesOptions options) {
		return comparisonReferenceReady(exp, backgroundChoice(options));
	}

	private static ImageTransformEnums backgroundChoice(BuildSeriesOptions options) {
		if (options == null || options.flyDetectBackgroundTransform == null)
			return ImageTransformEnums.NONE;
		return options.flyDetectBackgroundTransform;
	}

	private static ImageTransformEnums sourceChoice(BuildSeriesOptions options) {
		if (options == null || options.flyDetectSourceTransform == null)
			return ImageTransformEnums.NONE;
		return options.flyDetectSourceTransform;
	}

	private static IcyBufferedImage readCamFrame(Experiment exp, int t) {
		if (exp == null || exp.getSeqCamData() == null || t < 0)
			return null;
		SequenceLoaderService loader = new SequenceLoaderService();
		String path = exp.getSeqCamData().getFileNameFromImageList(t);
		if (path == null)
			return null;
		return loader.imageIORead(path);
	}

	private static IcyBufferedImage applySourceTransform(IcyBufferedImage raw, BuildSeriesOptions options) {
		if (raw == null)
			return null;
		ImageTransformEnums src = sourceChoice(options);
		if (src == ImageTransformEnums.NONE)
			return raw;
		CanvasImageTransformOptions srcOpts = new CanvasImageTransformOptions();
		srcOpts.transformOption = src;
		return src.getFunction().getTransformedImage(raw, srcOpts);
	}

	private static IcyBufferedImage cachedFixedComparison(Experiment exp, BuildSeriesOptions options,
			ImageTransformEnums bg) {
		ImageTransformEnums src = sourceChoice(options);
		IcyBufferedImage raw = bg == ImageTransformEnums.SUBTRACT_REF ? exp.getSeqCamData().getReferenceImage()
				: null;
		if (cachedFixedComparison != null && cachedFixedExperiment == exp && cachedFixedBackground == bg
				&& cachedFixedSource == src && (bg != ImageTransformEnums.SUBTRACT_REF || cachedFixedRaw == raw))
			return cachedFixedComparison;
		if (bg != ImageTransformEnums.SUBTRACT_REF)
			raw = readCamFrame(exp, 0);
		IcyBufferedImage filtered = applySourceTransform(raw, options);
		if (filtered == null)
			return null;
		cachedFixedComparison = filtered;
		cachedFixedRaw = raw;
		cachedFixedExperiment = exp;
		cachedFixedBackground = bg;
		cachedFixedSource = src;
		return filtered;
	}

	private static IcyBufferedImage loadVaryingComparison(Experiment exp, BuildSeriesOptions options, int t,
			ImageTransformEnums bg) {
		if (bg == ImageTransformEnums.SUBTRACT_TM1)
			return readCamFrame(exp, t > 0 ? t - 1 : 0);
		if (bg == ImageTransformEnums.SUBTRACT_TM1_CLEAN) {
			CanvasImageTransformOptions bgOpts = new CanvasImageTransformOptions();
			bgOpts.transformOption = bg;
			copyBackgroundHealParams(bgOpts, options);
			return buildCleanedPreviousFrame(exp, t, bgOpts);
		}
		return null;
	}

	public static void fillFlyDetectBackgroundOptions(Experiment exp, int t, CanvasImageTransformOptions bgOpts) {
		SequenceLoaderService loader = new SequenceLoaderService();
		switch (bgOpts.transformOption) {
		case SUBTRACT_TM1:
			if (t > 0)
				bgOpts.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t - 1));
			else
				bgOpts.backgroundImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(0));
			break;

		case SUBTRACT_TM1_CLEAN:
			bgOpts.backgroundImage = buildCleanedPreviousFrame(exp, t, bgOpts);
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

	/**
	 * Loads {@code t-1} and patches dark fly pixels from Detect2 {@code referenceImage}.
	 * Falls back to raw {@code t-1} when no reference is available.
	 */
	public static IcyBufferedImage buildCleanedPreviousFrame(Experiment exp, int t, CanvasImageTransformOptions bgOpts) {
		if (exp == null || exp.getSeqCamData() == null) {
			return null;
		}
		SequenceLoaderService loader = new SequenceLoaderService();
		int tPrev = t > 0 ? t - 1 : 0;
		IcyBufferedImage previous = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(tPrev));
		if (previous == null) {
			return null;
		}
		IcyBufferedImage reference = exp.getSeqCamData().getReferenceImage();
		if (reference == null) {
			exp.loadReferenceImage();
			reference = exp.getSeqCamData().getReferenceImage();
		}
		if (reference == null) {
			Logger.warn("t-clean(t-1): no referenceImage — falling back to raw t-(t-1). Build background (Detect2) first.");
			return previous;
		}
		int threshold = bgOpts != null && bgOpts.simplethreshold > 0 ? bgOpts.simplethreshold : 60;
		int delta = bgOpts != null ? bgOpts.background_delta : 20;
		int jitter = bgOpts != null ? Math.max(2, bgOpts.background_jitter) : 2;
		IcyBufferedImage cleaned = PatchPreviousFromReference.patchDarkFliesFromReference(previous, reference,
				threshold, delta, jitter);
		// Also wipe known fly boxes from t-1 (covers partial silhouettes that failed the seed test).
		cleaned = PatchPreviousFromReference.patchKnownFlyRects(cleaned, reference, exp.getCages(), tPrev,
				Math.max(3, jitter + 1));
		return cleaned;
	}

	/** Copies Detect2-style heal parameters used by {@link #SUBTRACT_TM1_CLEAN}. */
	public static void copyBackgroundHealParams(CanvasImageTransformOptions bgOpts, BuildSeriesOptions options) {
		if (bgOpts == null || options == null) {
			return;
		}
		bgOpts.simplethreshold = options.backgroundThreshold > 0 ? options.backgroundThreshold : options.threshold;
		bgOpts.background_delta = options.background_delta;
		bgOpts.background_jitter = Math.max(1, options.background_jitter);
	}

	/**
	 * For frame-difference / reference subtract: keep only the polarity of the fly
	 * being tracked so t-1 departure ghosts are not detected as a second fly.
	 */
	public static void applyFlyDetectDifferencePolarity(CanvasImageTransformOptions opts, boolean trackWhite) {
		if (opts == null) {
			return;
		}
		opts.differencePolarity = trackWhite ? DifferencePolarity.BRIGHTER_NOW : DifferencePolarity.DARKER_NOW;
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

		case SUBTRACT_TM1_CLEAN:
			options.backgroundImage = buildCleanedPreviousFrame(exp, t, options);
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
		if (!comparisonReferenceReady(exp, options)) {
			Logger.warn(MISSING_REFERENCE_WARNING);
			return;
		}
		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage, exp.getFlyMmPerPixelX(), exp.getFlyMmPerPixelY());

		openFlyDetectViewers1(exp);
		try {
			findFliesInAllFrames(exp);
		} finally {
			find_flies.clearBackgroundComparison();
			ImageTransformBase.clearArrayCache();
			cachedFixedComparison = null;
			cachedFixedRaw = null;
			cachedFixedExperiment = null;
			cachedFixedBackground = null;
			cachedFixedSource = null;
		}
	}

	@Override
	protected void findFliesInAllFrames(Experiment exp) {
		ProgressFrame progressBar = new ProgressFrame("Detecting flies...");
		int totalFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		ImageTransformEnums bg = backgroundChoice(options);

		for (int index = 0; index < totalFrames; index++) {
			if (stopFlag)
				break;
			int t = index;
			String title = "Frame #" + t + "/" + totalFrames;
			progressBar.setMessage(title);

			IcyBufferedImage raw = readCamFrame(exp, t);
			IcyBufferedImage direct = applySourceTransform(raw, options);
			if (direct == null)
				continue;
			IcyBufferedImage comparison = comparisonFrameForFlyDetect1(exp, options, t);
			if (bg != ImageTransformEnums.NONE && comparison == null) {
				if (comparisonRequiresReference(bg))
					Logger.warn(MISSING_REFERENCE_WARNING);
				else
					Logger.warn("FlyDetect1: comparison frame is missing at frame " + t);
				break;
			}
			if (comparison == null)
				find_flies.clearBackgroundComparison();
			else if (!find_flies.setBackgroundComparison(direct, comparison, options.background_delta,
					options.btrackWhite)) {
				Logger.warn("FlyDetect1: comparison image does not match the filtered frame");
				continue;
			}

			int illumPhase = IlluminationPhase.phaseForFlyDetection(options, raw);
			try {
				seqNegative.beginUpdate();
				seqNegative.setImage(0, 0, direct);
				vNegative.setTitle(title);
				List<Rectangle2D> listRectangles = find_flies.findFlies(direct, t, illumPhase);
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
