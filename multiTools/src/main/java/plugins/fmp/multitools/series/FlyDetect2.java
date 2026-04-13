package plugins.fmp.multitools.series;

import icy.image.IcyBufferedImageUtil;
import icy.image.IcyBufferedImage;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.service.SequenceLoaderService;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class FlyDetect2 extends FlyDetect {
	public boolean viewInternalImages = true;
	private boolean warnedNoPositions = false;

	// -----------------------------------------

	@Override
	protected void runFlyDetect(Experiment exp) {
		if (options != null && !options.detectFlies && options.detectIllumPhase) {
			updateIllumPhaseOnly(exp);
			return;
		}

		exp.cleanPreviousDetectedFliesROIs();
		find_flies.initParametersForDetection(exp, options);
		exp.getCages().initFlyPositions(options.detectCage, exp.getFlyMmPerPixelX(), exp.getFlyMmPerPixelY());

		options.threshold = options.thresholdDiff;
		if (ensureBackgroundsLoaded(exp)) {
			openFlyDetectViewers1(exp);
			findFliesInAllFrames(exp);
		}
	}

	private void updateIllumPhaseOnly(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null) {
			return;
		}

		// Ensure backgrounds exist (needed for dual-background choice consistency).
		if (!ensureBackgroundsLoaded(exp)) {
			return;
		}

		boolean hasAnyPositions = false;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage != null && cage.getFlyPositions() != null && cage.getFlyPositions().flyPositionList != null
					&& !cage.getFlyPositions().flyPositionList.isEmpty()) {
				hasAnyPositions = true;
				break;
			}
		}
		if (!hasAnyPositions) {
			// Silent in batch mode (index1>index0 means we will run series).
			boolean isBatch = options != null && options.expList != null && options.expList.index1 > options.expList.index0;
			if (!isBatch && !warnedNoPositions) {
				warnedNoPositions = true;
				new AnnounceFrame(
						"Light-only requested but no fly positions are saved.\n"
								+ "Run fly detection first, then re-run with light enabled to store the phase.");
			}
			return;
		}

		int totalFrames = exp.getSeqCamData().getImageLoader() != null ? exp.getSeqCamData().getImageLoader().getNTotalFrames() : 0;
		if (totalFrames <= 0) {
			return;
		}

		ProgressFrame progress = new ProgressFrame("Detecting lighting phase...");
		int[] phaseByT = new int[totalFrames];
		SequenceLoaderService loader = new SequenceLoaderService();
		for (int t = 0; t < totalFrames; t++) {
			if (stopFlag) {
				break;
			}
			progress.setMessage("Frame #" + t + "/" + totalFrames);
			IcyBufferedImage workImage = loader.imageIORead(exp.getSeqCamData().getFileNameFromImageList(t));
			if (options != null && options.dualBackground) {
				phaseByT[t] = IlluminationPhase.fromFrameForDualBackground(workImage, options.rednessThreshold);
			} else {
				phaseByT[t] = IlluminationPhase.UNKNOWN;
			}
		}
		progress.close();

		// Apply to existing entries (do not touch rectangles/ids).
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage == null || cage.getFlyPositions() == null || cage.getFlyPositions().flyPositionList == null) {
				continue;
			}
			for (FlyPosition p : cage.getFlyPositions().flyPositionList) {
				if (p == null) {
					continue;
				}
				int t = p.flyIndexT;
				if (t >= 0 && t < phaseByT.length) {
					p.illumPhase = phaseByT[t];
				}
			}
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
