package plugins.fmp.multitools.fmp_series;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_service.LevelDetector;

public class DetectLevels extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		if (loadExperimentDataToDetectLevels(exp)) {
			exp.getSeqKymos().displayViewerAtRectangle(options.parent0Rect);
			new LevelDetector().detectLevels(exp, options);
		}
		exp.closeSequences();
	}

	private boolean loadExperimentDataToDetectLevels(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		return exp.loadKymographs();
	}
}
