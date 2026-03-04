package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.SpotLevelDetectorFromCam;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;

/**
 * Memory-light series that computes spot-based measures directly from camera
 * images using {@link SpotLevelDetectorFromCam}.
 * <p>
 * This class mirrors the direct-camera branch of {@link DetectLevels} but
 * operates on spots rather than capillaries and does not rely on
 * {@link AdvancedMemoryOptions} or streaming/memory pools.
 */
public class BuildSpotsMeasuresLight extends BuildSeries {

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null)
			return;

		// Load descriptors and spot data
		exp.xmlLoad_MCExperiment();
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		// Prepare camera sequence and timing in a lightweight way, similar to
		// BuildSpotsMeasuresAdvanced: attach only the first image so ROIs and overlays
		// have a reference frame, but do NOT load the full stack into memory.
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData != null && seqCamData.getSequence() == null && seqCamData.getImageLoader() != null) {
			seqCamData.attachSequence(
					seqCamData.getImageLoader().initSequenceFromFirstImage(seqCamData.getImagesList(true)));
		}
		if (seqCamData != null && seqCamData.getTimeManager().getBinDurationMs() == 0) {
			exp.loadFileIntervalsFromSeqCamData();
		}

		// Build time limits used by SpotLevelDetectorFromCam
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(exp.getCamImageFirst_ms());
		getTimeLimitsOfSequence(exp);

		// Mark all spots as ready for analysis
		Spots spots = exp.getSpots();
		if (spots != null) {
			spots.setReadyToAnalyze(true, options);
		}

		// Run the light detector
		new SpotLevelDetectorFromCam().detectSpots(exp, options);

		// Reset analysis flags
		if (spots != null) {
			spots.setReadyToAnalyze(false, options);
		}

		// Close sequences opened for this experiment
		exp.closeSequences();
	}
}

