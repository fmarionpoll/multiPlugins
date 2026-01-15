package plugins.fmp.multiSPOTS96.series;

import plugins.fmp.multitools.experiment.Experiment;

public class AdjustMeasuresToDimensions extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		if (exp.loadKymographs()) {
			exp.adjustCapillaryMeasuresDimensions();
			exp.saveCapillariesMeasures(exp.getKymosBinFullDirectory());
		}
		exp.getSeqCamData().closeSequence();
		exp.getSeqKymos().closeSequence();
	}

}
