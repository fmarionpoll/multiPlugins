package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.LevelDetector;

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
