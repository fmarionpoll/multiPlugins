package plugins.fmp.multitools.series;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;

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
