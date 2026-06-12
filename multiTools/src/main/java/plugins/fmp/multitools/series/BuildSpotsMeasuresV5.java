package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.SpotLevelDetectionRunner;
import plugins.fmp.multitools.service.SpotLevelDetectorFromCamV5;
import plugins.fmp.multitools.tools.Logger;

/**
 * Memory-light series that computes V5 spot measures via
 * {@link SpotLevelDetectorFromCamV5}.
 */
public class BuildSpotsMeasuresV5 extends BuildSeries {

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null) {
			return;
		}

		exp.xmlLoad_MCExperiment();
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData != null && seqCamData.getSequence() == null && seqCamData.getImageLoader() != null) {
			seqCamData.attachSequence(
					seqCamData.getImageLoader().initSequenceFromFirstImage(seqCamData.getImagesList(true)));
		}
		if (seqCamData != null && seqCamData.getTimeManager().getBinDurationMs() == 0) {
			exp.loadFileIntervalsFromSeqCamData();
		}

		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(exp.getSeqCamData().getFirstImageMs());
		getTimeLimitsOfSequence(exp);

		Spots spots = exp.getSpots();
		if (spots != null) {
			spots.setReadyToAnalyze(true, options);
		}

		SpotLevelDetectionRunner runner = new SpotLevelDetectorFromCamV5();

		long t0 = System.nanoTime();
		runner.detectSpots(exp, options);
		long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
		Logger.info("BuildSpotsMeasuresV5: analyzeExperiment completed in " + elapsedMs + " ms");

		if (spots != null) {
			spots.setReadyToAnalyze(false, options);
		}

		exp.closeSequences();
	}
}
