package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.SpotLevelDetectorFromCam;

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

		// Prepare camera sequence and timing as in DetectLevels (cam direct)
		exp.getSeqCamData().loadImages();
		exp.getFileIntervalsFromSeqCamData();
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

