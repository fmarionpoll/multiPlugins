package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.service.GulpDetector;
import plugins.fmp.multitools.service.KymographService;

public class DetectGulps extends BuildSeries {

	void analyzeExperiment(Experiment exp) {
		if (!loadExperimentDataToDetectGulps(exp)) {
			exp.getSeqKymos().closeSequence();
			return;
		}
		exp.ensureFrameTimeScale();
		if (!exp.isNativeFrameIndexedKymo()) {
			int nFrames = exp.getAnalysisFrameCount();
			int kymoWidth = exp.getKymoColumnCount();
			String msg = "Gulp/derivative blocked: kymo is not native frame-indexed "
					+ "(kymo width=" + kymoWidth + ", analysis frames=" + nFrames
					+ "). Use a 1-column-per-frame kymo, or wait for the future curve-based detector.";
			plugins.fmp.multitools.tools.Logger.warn("DetectGulps: " + msg);
			icy.gui.dialog.MessageDialog.showDialog(msg, icy.gui.dialog.MessageDialog.ERROR_MESSAGE);
			exp.getSeqKymos().closeSequence();
			return;
		}
		buildFilteredImage(exp);
		new GulpDetector().detectGulps(exp, options);
		exp.getSeqKymos().closeSequence();
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
				options.transformForGulps);
	}

}
