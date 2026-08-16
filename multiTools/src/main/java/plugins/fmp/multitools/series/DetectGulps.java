package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.GulpDetector;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Logger;

public class DetectGulps extends BuildSeries {

	void analyzeExperiment(Experiment exp) {
		if (!loadExperimentDataToDetectGulps(exp)) {
			if (exp.getSeqKymos() != null) {
				exp.getSeqKymos().closeSequence();
			}
			return;
		}
		exp.ensureFrameTimeScale();
		if (!exp.isNativeFrameIndexedKymo()) {
			int nFrames = exp.getAnalysisFrameCount();
			int kymoWidth = exp.getKymoColumnCount();
			String expName = experimentLabel(exp);
			Logger.warn("DetectGulps: gulp detection skipped (non-native / downsampled kymo) for experiment="
					+ expName + " (kymo width=" + kymoWidth + ", analysis frames=" + nFrames
					+ "). Use downsample x1 for gulp detection.");
			if (exp.getSeqKymos() != null) {
				exp.getSeqKymos().closeSequence();
			}
			return;
		}
		buildFilteredImage(exp);
		new GulpDetector().detectGulps(exp, options);
		exp.getSeqKymos().closeSequence();
	}

	private static String experimentLabel(Experiment exp) {
		if (exp == null) {
			return "(null)";
		}
		String dir = exp.getExperimentDirectory();
		if (dir != null && !dir.isEmpty()) {
			return dir;
		}
		String results = exp.getResultsDirectory();
		return results != null ? results : exp.toString();
	}

	private boolean loadExperimentDataToDetectGulps(Experiment exp) {
		exp.xmlLoad_MCExperiment();

		boolean flag = exp.loadMCCapillaries_Only();
		flag &= exp.loadKymographs();
		flag &= exp.load_capillaries_description_and_measures();
		return flag;
	}

	private void buildFilteredImage(Experiment exp) {
		if (exp.getSeqKymos() == null)
			return;
		new KymographService().buildFiltered(exp, 0, BuildSeriesOptions.Z_INDEX_FILTERED_FOR_GULPS,
				options.transformForGulps, options.spanDiffForGulps);
	}

}
