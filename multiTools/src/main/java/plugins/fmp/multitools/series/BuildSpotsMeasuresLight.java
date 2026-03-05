package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.service.SpotLevelDetectionRunner;
import plugins.fmp.multitools.service.SpotLevelDetectorFromCam;
import plugins.fmp.multitools.service.SpotLevelDetectorFromCamBasic;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.SpotDetectionMode;
import plugins.fmp.multitools.tools.Logger;

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

		// Choose detection backend
		SpotLevelDetectionRunner runner = createSpotDetectionRunner(options);

		// Run the light detector with basic timing
		long t0 = System.nanoTime();
		runner.detectSpots(exp, options);
		long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
		Logger.info("BuildSpotsMeasuresLight: analyzeExperiment completed in " + elapsedMs + " ms");

		// Reset analysis flags
		if (spots != null) {
			spots.setReadyToAnalyze(false, options);
		}

		// Close sequences opened for this experiment
		exp.closeSequences();
	}

	private SpotLevelDetectionRunner createSpotDetectionRunner(BuildSeriesOptions options) {
		SpotDetectionMode mode = options != null ? options.spotDetectionMode : SpotDetectionMode.AUTO;

		if (mode == SpotDetectionMode.BASIC) {
			return new SpotLevelDetectorFromCamBasic();
		}
		if (mode == SpotDetectionMode.PIPELINED) {
			return new SpotLevelDetectorFromCam();
		}

		// AUTO: decide based on machine capabilities
		int cores = Runtime.getRuntime().availableProcessors();
		long maxMemGb = Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L);
		boolean strongMachine = cores >= 6 && maxMemGb >= 12;

		if (strongMachine) {
			return new SpotLevelDetectorFromCam();
		}
		return new SpotLevelDetectorFromCamBasic();
	}
}

