package plugins.fmp.multitools.fmp_series;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_service.GulpDetector;
import plugins.fmp.multitools.fmp_service.KymographService;

public class DetectGulps extends BuildSeries {

	void analyzeExperiment(Experiment exp) {
		if (loadExperimentDataToDetectGulps(exp)) {
			buildFilteredImage(exp);
			new GulpDetector().detectGulps(exp, options);
		}
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
		int zChannelDestination = 2;
		new KymographService().buildFiltered(exp, 0, zChannelDestination, options.transformForGulps, options.spanDiff);
	}

}
