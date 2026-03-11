package plugins.fmp.multitools.series;

import java.awt.geom.Rectangle2D;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.CapillariesKymosMapper;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.DarkFrameDetector;
import plugins.fmp.multitools.service.DarkFrameDetector.DarkFrameDetectionOptions;
import plugins.fmp.multitools.service.ExperimentService;
import plugins.fmp.multitools.tools.Logger;

/**
 * BuildSeries worker that runs dark-frame detection on camera images and
 * clears capillary or spot measures at dark frames.
 */
public class BuildCleanGapsFromCam extends BuildSeries {

	public enum Target {
		CAPILLARIES, SPOTS
	}

	public Target target = Target.CAPILLARIES;
	public boolean doDetect = true;
	public boolean doClean = true;
	public DarkFrameDetectionOptions darkOptions = null;

	/** Property name used to publish a short summary like "Dark: x, Light: y". */
	public static final String PROP_RESULT_SUMMARY = "clean_gaps_summary";

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null) {
			return;
		}

		ExperimentService expService = new ExperimentService();
		SequenceCamData seqCam = exp.getSeqCamData();
		if (seqCam == null || seqCam.getSequence() == null) {
			seqCam = expService.openSequenceCamData(exp);
		}
		if (seqCam == null || seqCam.getSequence() == null) {
			Logger.warn("BuildCleanGapsFromCam: no camera sequence available for experiment "
					+ exp.getResultsDirectory());
			return;
		}

		int[] lightStatus = seqCam.getLightStatusPerFrame();

		if (doDetect) {
			lightStatus = runDetection(exp);
			if (lightStatus == null || lightStatus.length == 0) {
				Logger.warn("BuildCleanGapsFromCam: dark frame detection failed for experiment "
						+ exp.getResultsDirectory());
				return;
			}
		}

		if (doClean) {
			if (lightStatus == null || lightStatus.length == 0) {
				// Try to compute detection on the fly if not already done.
				lightStatus = runDetection(exp);
			}
			if (lightStatus == null || lightStatus.length == 0) {
				Logger.warn("BuildCleanGapsFromCam: no light status available for experiment "
						+ exp.getResultsDirectory());
				return;
			}
			switch (target) {
			case CAPILLARIES:
				cleanCapillaryMeasures(exp, lightStatus);
				break;
			case SPOTS:
				cleanSpotMeasures(exp, lightStatus);
				break;
			default:
				break;
			}
		}
	}

	private int[] runDetection(Experiment exp) {
		if (exp == null) {
			return null;
		}

		SequenceCamData seqCam = exp.getSeqCamData();
		if (seqCam == null || seqCam.getSequence() == null || seqCam.getSequence().getSizeT() <= 0) {
			ExperimentService expService = new ExperimentService();
			if (!expService.loadCamDataImages(exp)) {
				Logger.warn("BuildCleanGapsFromCam: no camera images available for experiment "
						+ exp.getResultsDirectory());
				return null;
			}
			seqCam = exp.getSeqCamData();
			if (seqCam == null || seqCam.getSequence() == null || seqCam.getSequence().getSizeT() <= 0) {
				Logger.warn("BuildCleanGapsFromCam: camera sequence has no frames for experiment "
						+ exp.getResultsDirectory());
				return null;
			}
		}

		// Ensure DarkFrameDetector sees a usable reference image.
		if (seqCam.getReferenceImage() == null) {
			// Try to get the first frame from the underlying sequence.
			if (seqCam.getSequence() != null && seqCam.getSequence().getSizeT() > 0) {
				try {
					// This may still be lazily loaded; fall back to explicit getSeqImage if needed.
					plugins.fmp.multitools.tools.Logger.debug(
							"BuildCleanGapsFromCam: initializing reference image from first frame for experiment "
									+ exp.getResultsDirectory());
					icy.image.IcyBufferedImage first = seqCam.getSequence().getFirstImage();
					if (first == null) {
						first = seqCam.getSeqImage(0, 0);
					}
					if (first != null) {
						seqCam.setReferenceImage(first);
					}
				} catch (Exception e) {
					Logger.warn(
							"BuildCleanGapsFromCam: failed to initialize reference image from first frame for experiment "
									+ exp.getResultsDirectory(),
							e);
				}
			}
		}

		DarkFrameDetectionOptions opts = darkOptions != null ? copyOptions(darkOptions) : new DarkFrameDetectionOptions();
		if (opts.rectMonitor != null) {
			Rectangle2D rect = opts.rectMonitor.getRectangle();
			opts.roiX = (int) rect.getMinX();
			opts.roiY = (int) rect.getMinY();
			opts.roiWidth = (int) rect.getWidth();
			opts.roiHeight = (int) rect.getHeight();
		}

		DarkFrameDetector detector = new DarkFrameDetector();
		int[] lightStatus = detector.runDetection(exp, opts);
		if (lightStatus == null) {
			return null;
		}

		int dark = 0;
		for (int s : lightStatus) {
			if (s == 0) {
				dark++;
			}
		}
		String summary = "Dark: " + dark + ", Light: " + (lightStatus.length - dark);
		firePropertyChange(PROP_RESULT_SUMMARY, null, summary);

		// Persist parameters on experiment so that UI can restore them later.
		if (opts.rectMonitor != null) {
			Rectangle2D r = opts.rectMonitor.getRectangle();
			exp.setDarkFrameThresholdMean(opts.thresholdMean);
			exp.setDarkFrameRoiX(r.getMinX());
			exp.setDarkFrameRoiY(r.getMinY());
			exp.setDarkFrameRoiWidth(r.getWidth());
			exp.setDarkFrameRoiHeight(r.getHeight());
		}

		Logger.info("BuildCleanGapsFromCam: computed light status for " + lightStatus.length + " frames (dark=" + dark
				+ ")");
		return lightStatus;
	}

	private void cleanCapillaryMeasures(Experiment exp, int[] lightStatus) {
		if (!exp.load_capillaries_description_and_measures()) {
			Logger.warn("BuildCleanGapsFromCam: could not load capillary measures for experiment "
					+ exp.getResultsDirectory());
			return;
		}
		if (exp.getCapillaries() == null || exp.getCapillaries().getList().isEmpty()) {
			Logger.warn("BuildCleanGapsFromCam: no capillaries for experiment " + exp.getResultsDirectory());
			return;
		}

		exp.getCapillaries().clearMeasuresAtDarkFrames(lightStatus);
		if (exp.getSeqKymos() != null) {
			CapillariesKymosMapper.pushCapillaryMeasuresToKymos(exp.getCapillaries(), exp.getSeqKymos());
		}

		boolean saved = exp.save_capillaries_description_and_measures();
		if (!saved) {
			Logger.warn("BuildCleanGapsFromCam: could not save capillary measures for experiment "
					+ exp.getResultsDirectory());
		}
		Logger.info("BuildCleanGapsFromCam: cleared capillary measures at dark frames (saved=" + saved + ")");
	}

	private void cleanSpotMeasures(Experiment exp, int[] lightStatus) {
		if (!exp.load_spots_description_and_measures()) {
			Logger.warn("BuildCleanGapsFromCam: could not load spot measures for experiment "
					+ exp.getResultsDirectory());
			return;
		}
		if (exp.getSpots() == null || exp.getSpots().getSpotList().isEmpty()) {
			Logger.warn("BuildCleanGapsFromCam: no spots for experiment " + exp.getResultsDirectory());
			return;
		}

		exp.getSpots().clearMeasuresAtDarkFrames(lightStatus);

		boolean saved = exp.save_spots_description_and_measures();
		if (!saved) {
			Logger.warn("BuildCleanGapsFromCam: could not save spot measures for experiment "
					+ exp.getResultsDirectory());
		}
		Logger.info("BuildCleanGapsFromCam: cleared spot measures at dark frames (saved=" + saved + ")");
	}

	private DarkFrameDetectionOptions copyOptions(DarkFrameDetectionOptions source) {
		DarkFrameDetectionOptions copy = new DarkFrameDetectionOptions();
		copy.roiX = source.roiX;
		copy.roiY = source.roiY;
		copy.roiWidth = source.roiWidth;
		copy.roiHeight = source.roiHeight;
		copy.thresholdSum = source.thresholdSum;
		copy.thresholdMean = source.thresholdMean;
		if (source.rectMonitor != null) {
			copy.rectMonitor.setRectangle(source.rectMonitor.getRectangle());
		}
		return copy;
	}
}

