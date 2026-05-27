package plugins.fmp.multitools.series;

import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.service.CageKymoAnalyzer;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.KymoAnalysisResult;
import plugins.fmp.multitools.service.KymoMeasuresCommit;
import plugins.fmp.multitools.tools.Logger;

/**
 * Batch worker: analyze stacked cage kymographs ({@code kymocage_*.tif*}), commit per-spot kymograph
 * measures, and save {@code SpotsMeasures.csv} in the kymographs bin.
 */
public class AnalyzeCageKymographs extends BuildSeries {

	/** Analyzer parameters (metric transform, thresholds, row lift, insect gate). */
	public Params analyzerParams;

	/** Last non-empty result (last experiment in the batch that produced data). */
	public volatile KymoAnalysisResult lastResult;

	@Override
	void analyzeExperiment(Experiment exp) {
		if (exp == null || analyzerParams == null) {
			return;
		}
		try {
			exp.releaseKymographSequence();
			if (!prepareExperimentForCageKymoAnalysis(exp)) {
				Logger.warn("AnalyzeCageKymographs: could not prepare experiment " + exp.getResultsDirectory());
				return;
			}
			if (!exp.loadCageSpotKymographs()) {
				Logger.warn("AnalyzeCageKymographs: could not load cage kymographs for " + exp.getResultsDirectory());
				return;
			}

			KymoAnalysisResult result = CageKymoAnalyzer.analyze(exp, analyzerParams);
			if (result != null && !result.byCageId.isEmpty()) {
				KymoMeasuresCommit.apply(result, exp);
				exp.save_kymo_spot_measures();
				lastResult = result;
				Logger.info("AnalyzeCageKymographs: " + result.byCageId.size() + " cage(s), " + result.widthBins
						+ " bin(s) — " + exp.getResultsDirectory());
			} else {
				Logger.warn("AnalyzeCageKymographs: no strip data for " + exp.getResultsDirectory());
			}
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
