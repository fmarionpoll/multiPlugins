package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
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
		exp.load_cages_description_and_measures();
		exp.load_spots_description_and_measures();

		if (!exp.loadCageSpotKymographs()) {
			Logger.warn("AnalyzeCageKymographs: could not load cage kymographs for " + exp.getResultsDirectory());
			exp.closeSequences();
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
		exp.closeSequences();
	}

	@Override
	protected void done() {
		if (options != null && options.expList != null && selectedExperimentIndex >= 0 && analyzerParams != null) {
			Experiment selected = options.expList.getItemAt(selectedExperimentIndex);
			if (selected != null && selected.loadCageSpotKymographs()) {
				KymoAnalysisResult refreshed = CageKymoAnalyzer.analyze(selected, analyzerParams);
				if (refreshed != null && !refreshed.byCageId.isEmpty()) {
					lastResult = refreshed;
				}
			}
		}
		super.done();
	}
}
