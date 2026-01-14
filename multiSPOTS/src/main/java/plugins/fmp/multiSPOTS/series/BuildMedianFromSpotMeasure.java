package plugins.fmp.multiSPOTS.series;

import plugins.fmp.multiSPOTS.experiment.Experiment;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;

public class BuildMedianFromSpotMeasure extends BuildSeries {

	// -----------------------------------

	void analyzeExperiment(Experiment exp) {
		if (!exp.load_Spots())
			return;

		exp.loadKymographs();
		int imageHeight = exp.seqSpotKymos.seq.getHeight();
		for (Spot spot : exp.spotsArray.spotsList) {
			spot.buildRunningMedianFromSumLevel2D(imageHeight);
		}
		exp.save_SpotsMeasures();
		exp.seqSpotKymos.closeSequence();
	}

}
