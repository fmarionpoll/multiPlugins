package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.LevelDetectorFromKymo;
import plugins.fmp.multitools.service.LevelDetectorFromCam;

public class DetectLevels extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		if (options.sourceCamDirect) {
			exp.xmlLoad_MCExperiment();
			exp.load_capillaries_description_and_measures();
			exp.getCapillaries().clearDirectMeasuresOnly();
			exp.getSeqCamData().loadImages();
			exp.getFileIntervalsFromSeqCamData();
			exp.build_MsTimeIntervalsArray_From_SeqCamData_FileNamesList(exp.getCamImageFirst_ms());
			getTimeLimitsOfSequence(exp);
			new LevelDetectorFromCam().detectLevels(exp, options);

		} else if (loadExperimentDataToDetectLevels(exp)) {
			exp.getSeqKymos().displayViewerAtRectangle(options.parent0Rect);
			new LevelDetectorFromKymo().detectLevels(exp, options);
		}
		exp.closeSequences();
	}

	private boolean loadExperimentDataToDetectLevels(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		return exp.loadKymographs();
	}
}
