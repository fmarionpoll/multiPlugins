package plugins.fmp.multitools.fmp_series;

import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceKymos;

public class CurvesRestoreLength extends BuildSeries {
	void analyzeExperiment(Experiment exp) {
		exp.xmlLoad_MCExperiment();
		exp.load_capillaries_description_and_measures();
		if (exp.loadKymographs()) {
			SequenceKymos seqKymos = exp.getSeqKymos();
			for (int t = 0; t < seqKymos.getImageLoader().getNTotalFrames(); t++) {
				Capillary cap = exp.getCapillaries().getList().get(t);
				cap.restoreClippedMeasures();
			}
			exp.save_capillaries_description_and_measures();
		}
		exp.getSeqCamData().closeSequence();
		exp.getSeqKymos().closeSequence();
	}
}
