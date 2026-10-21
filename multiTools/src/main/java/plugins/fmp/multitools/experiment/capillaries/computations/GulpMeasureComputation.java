package plugins.fmp.multitools.experiment.capillaries.computations;

import java.util.ArrayList;
import java.util.Arrays;

import plugins.fmp.multitools.experiment.capillary.CapillaryGulps;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
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

			int npoints = cap.getTopRaw().getNPoints();
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

			int npoints = cap.getTopRaw().getNPoints();
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
	 * Intake bins at native kymograph resolution (pixels), raw or minus the
	 * experiment evaporation increment; optionally as a running total. Corrected
	 * variants return null when no evaporation curve is available.
	 */
	public static MeasurementComputation computeIntakeBins(boolean corrected, boolean cumulative) {
		return (exp, cap, options) -> {
			if (cap == null || cap.getGulps() == null || cap.getTopRaw() == null
					|| cap.getGulps().getHeightSeries() == null || cap.getGulps().getHeightSeries().npoints == 0) {
				return null;
			}
			double[] evaporation = null;
			if (corrected) {
				CapillaryMeasure curve = exp.getCapillaries() != null
						? exp.getCapillaries().getReferenceMeasures().getEvaporationUsedForCorrection()
						: null;
				if (curve == null) {
					return null;
				}
				evaporation = Arrays.copyOf(curve.polylineLevel.ypoints, curve.polylineLevel.npoints);
			}
			double[] bins = cap.getGulps().getIntakeBins(cap.getTopRaw().getNPoints(), evaporation);
			if (cumulative) {
				bins = CapillaryGulps.cumulate(bins);
			}
			ArrayList<Double> result = new ArrayList<>(bins.length);
			for (double v : bins) {
				result.add(v);
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

			int npoints = cap.getTopRaw().getNPoints();
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
