package plugins.fmp.multitools.experiment.capillaries.computations;

import java.util.ArrayList;

import plugins.fmp.multitools.experiment.capillaries.capillary.CapillaryGulps;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.MeasurementComputation;

/**
 * Computations for gulp-based measures (NBGULPS, AMPLITUDEGULPS, TTOGULP).
 */
public class GulpMeasureComputation {

	/**
	 * Computes the number of gulps per time bin (NBGULPS). Returns 1 if a gulp
	 * occurs in the bin, 0 otherwise.
	 */
	public static MeasurementComputation computeNbGulps() {
		return (exp, cap, options) -> {
			if (cap == null) {
				return null;
			}
			CapillaryGulps gulps = cap.getGulps();
			if (gulps == null) {
				return null;
			}

			int npoints = cap.getTopLevel().getNPoints();
			long binData = exp.getKymoBin_ms();
			long binExcel = options.buildExcelStepMs;

			if (binData <= 0) {
				binData = 60000;
			}
			if (binExcel <= 0) {
				binExcel = binData;
			}

			ArrayList<Integer> data = gulps.getMeasuresFromGulps(
					EnumResults.NBGULPS, npoints, binData, binExcel);

			if (data == null) {
				return new ArrayList<>();
			}

			ArrayList<Double> result = new ArrayList<>(data.size());
			for (Integer val : data) {
				result.add(val.doubleValue());
			}
			return result;
		};
	}

	/**
	 * Computes the amplitude of gulps per time bin (AMPLITUDEGULPS).
	 */
	public static MeasurementComputation computeAmplitudeGulps() {
		return (exp, cap, options) -> {
			if (cap == null) {
				return null;
			}
			CapillaryGulps gulps = cap.getGulps();
			if (gulps == null) {
				return null;
			}

			int npoints = cap.getTopLevel().getNPoints();
			long binData = exp.getKymoBin_ms();
			long binExcel = options.buildExcelStepMs;

			if (binData <= 0) {
				binData = 60000;
			}
			if (binExcel <= 0) {
				binExcel = binData;
			}

			ArrayList<Integer> data = gulps.getMeasuresFromGulps(
					EnumResults.AMPLITUDEGULPS, npoints, binData, binExcel);

			if (data == null) {
				return new ArrayList<>();
			}

			ArrayList<Double> result = new ArrayList<>(data.size());
			for (Integer val : data) {
				result.add(val.doubleValue());
			}
			return result;
		};
	}

	/**
	 * Computes time to previous gulp (TTOGULP) in time bins.
	 */
	public static MeasurementComputation computeTToGulp() {
		return (exp, cap, options) -> {
			if (cap == null) {
				return null;
			}
			CapillaryGulps gulps = cap.getGulps();
			if (gulps == null) {
				return null;
			}

			int npoints = cap.getTopLevel().getNPoints();
			long binData = exp.getKymoBin_ms();
			long binExcel = options.buildExcelStepMs;

			if (binData <= 0) {
				binData = 60000;
			}
			if (binExcel <= 0) {
				binExcel = binData;
			}

			ArrayList<Integer> data = gulps.getMeasuresFromGulps(
					EnumResults.TTOGULP, npoints, binData, binExcel);

			if (data == null) {
				return new ArrayList<>();
			}

			// Convert bins to minutes if needed
			double binMinutes = binExcel / (60.0 * 1000.0);
			ArrayList<Double> result = new ArrayList<>(data.size());
			for (Integer val : data) {
				result.add(val.doubleValue() * binMinutes);
			}
			return result;
		};
	}
}
