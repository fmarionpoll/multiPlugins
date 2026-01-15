package plugins.fmp.multiSPOTS96.series;

public class ResultsThreshold {
	double sumOverThreshold = 0.;
	double sumTot_no_fly_over_threshold = 0.;

	int npoints_in = 0;
	int nPointsOverThreshold = 0;
	int nPoints_no_fly = 0;
	int nPoints_fly_present = 0;

	public ResultsThreshold() {
	}

	public String toString() {
		String out = "sum=" + sumOverThreshold 
				+ " sumTot_no_fly_over_threshold=" + sumTot_no_fly_over_threshold 
				+ " npoints_in=" + npoints_in
				+ " nPointsOverThreshold=" + nPointsOverThreshold 
				+ " nPoints_no_fly=" + nPoints_no_fly
				+ " nPoints_fly_present=" + nPoints_fly_present
				;
		return out;
	}
}
