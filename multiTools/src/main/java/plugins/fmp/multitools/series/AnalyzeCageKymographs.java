package plugins.fmp.multitools.series;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CageKymoAnalyzer;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoMeasuresCommit;
import plugins.fmp.multitools.tools.Logger;

/**
 * Batch worker: analyze stacked cage kymographs ({@code kymocage_*.tif*}), commit per-spot kymograph
 * raw measurements, and save the primary sidecar and compatibility CSV in the kymographs bin.
 */
public class AnalyzeCageKymographs extends BuildSeries {

	/** Acquisition parameters (metric transform, thresholds and insect gate). */
	public Params analyzerParams;

	/** Last non-empty result (last experiment in the batch that produced data). */
	public volatile KymoAnalysisResult lastResult;
	public volatile int savedExperiments, failedExperiments;
	public String lastFailure;

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null || analyzerParams == null) {
			return;
		}
		try {
			exp.releaseKymographSequence();
			if (!prepareExperimentForCageKymoAnalysis(exp)) {
				throw new IllegalStateException("Could not prepare experiment");
			}
			if (!exp.loadCageSpotKymographs()) {
				throw new IllegalStateException("Build/load cage kymographs first");
			}

			KymoAnalysisResult result = CageKymoAnalyzer.measure(exp, analyzerParams);
			if (result != null && !result.byCageId.isEmpty()) {
				KymoMeasuresCommit.apply(result, exp);
				MeasurementProgress.phase("Saving measurements");
				if (exp.save_kymo_spot_measures()) {
					SpotDetectionProvenance.recordKymograph(exp, SpotDetectionProvenance.Family.KYMO_STRIP,
							getClass().getSimpleName(), "metric_transform", analyzerParams.metricTransform,
							"metric_threshold", analyzerParams.metricThreshold,
							"min_rgb_sum", analyzerParams.minSumRgbForValidPixel,
							"min_valid_rows", analyzerParams.minValidRowsPerColumn,
							"measurement_stage", "raw_height_and_color_integral",
							"row_occlusion_fill", false,
							"insect_gate", analyzerParams.insectMetricGateEnabled,
							"insect_transform", analyzerParams.insectMetricTransform,
							"insect_threshold", analyzerParams.insectMetricThreshold,
							"insect_threshold_above", analyzerParams.insectMetricThresholdUp);
				} else {
					throw new IllegalStateException("Could not save raw measurements");
				}
				savedExperiments++;
				lastResult = result;
				Logger.info("AnalyzeCageKymographs: " + result.byCageId.size() + " cage(s), " + result.widthBins
						+ " bin(s) — " + exp.getResultsDirectory());
			} else {
				throw new IllegalStateException("No spot strips were found");
			}
		} catch (Exception ex) {
			failedExperiments++;
			lastFailure = exp.getResultsDirectory() + ": " + ex.getMessage();
			Logger.error("Kymograph measurement failed: " + lastFailure, ex);
		} finally {
			exp.releaseKymographSequence();
			exp.closeSequences();
		}
	}

	/**
	 * Same prerequisites as {@link BuildKymosFromCageSpots#loadExperimentDataToBuildCageKymos}: resolve bin,
	 * load cages/spots, and attach a camera reference frame so strip layout matches build-time coordinates.
	 */
	private boolean prepareExperimentForCageKymoAnalysis(Experiment exp) {
		if (options != null && options.expList != null) {
			String sessionBin = options.expList.expListBinSubDirectory;
			if (sessionBin != null && !sessionBin.isEmpty()) {
				exp.setBinSubDirectory(sessionBin);
			}
		}
		exp.adoptBinSubdirectoryContainingCageKymographTiffs();

		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		SequenceCamData seqData = exp.getSeqCamData();
		if (seqData == null) {
			Logger.warn("AnalyzeCageKymographs: seqCamData is null for " + exp.getResultsDirectory());
			return false;
		}
		if (seqData.getSequence() == null) {
			if (!seqData.loadImages()) {
				List<String> imagesList = seqData.getImagesList(true);
				if (imagesList == null || imagesList.isEmpty()) {
					Logger.warn("AnalyzeCageKymographs: no camera images for " + exp.getResultsDirectory());
					return false;
				}
				seqData.attachSequence(seqData.getImageLoader().initSequenceFromFirstImage(imagesList));
			}
		}
		if (seqData.getSequence() == null) {
			Logger.warn("AnalyzeCageKymographs: camera reference sequence unavailable for " + exp.getResultsDirectory());
			return false;
		}

		exp.getFileIntervalsFromSeqCamData();
		long firstValidEpochMs = seqData.getFirstValidFrameEpochMs();
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = exp.getCamImageFirst_ms();
		}
		if (firstValidEpochMs < 0) {
			firstValidEpochMs = 0;
		}
		exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(firstValidEpochMs);
		return true;
	}

	@Override
	protected void done() {
		super.done();
	}
}
