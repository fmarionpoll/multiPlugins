package plugins.fmp.multiSPOTS96.series;

import plugins.fmp.multitools.experiment.Experiment;

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
