package plugins.fmp.multitools.fmp_series;

import plugins.fmp.multitools.fmp_experiment.Experiment;

public class CropMeasuresToDimensions extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		if (exp.loadKymographs()) {
			exp.cropCapillaryMeasuresDimensions();
			exp.saveCapillariesMeasures(exp.getKymosBinFullDirectory());
		}
		exp.getSeqCamData().closeSequence();
		exp.getSeqKymos().closeSequence();
	}
}
