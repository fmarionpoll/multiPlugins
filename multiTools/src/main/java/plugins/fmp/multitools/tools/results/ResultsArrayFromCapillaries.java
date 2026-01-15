package plugins.fmp.multitools.tools.results;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillary;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.tools.Logger;

public class ResultsArrayFromCapillaries extends ResultsArray {
	Results evapL = null;
	Results evapR = null;
	boolean sameLR = true;
	String stim = null;
	String conc = null;
	double lowestPiAllowed = -1.2;
	double highestPiAllowed = 1.2;

	public ResultsArrayFromCapillaries(int size) {
		resultsList = new ArrayList<Results>(size);
	}

	/**
	 * Gets the results for a capillary.
	 * 
	 * @param exp            The experiment
	 * @param capillary      The capillary
	 * @param resultsOptions The export options
	 * @param subtractT0     Whether to subtract T0 value
	 * @return The XLS results
	 */
	public Results getCapillaryMeasure(Experiment exp, Capillary capillary, ResultsOptions resultsOptions) {
		boolean subtractT0 = resultsOptions.subtractT0;
		ResultsCapillaries results = new ResultsCapillaries(capillary.getKymographName(),
				capillary.getProperties().nFlies, capillary.getCageID(), 0, resultsOptions.resultType);
		results.setStimulus(capillary.getStimulus());
		results.setConcentration(capillary.getConcentration());
		results.setCapSide(capillary.getCageID() + "_" + capillary.getCapillarySide());

		// Get bin durations
		long binData = exp.getKymoBin_ms();
		long binExcel = resultsOptions.buildExcelStepMs;

		// Validate bin sizes to prevent division by zero
		if (binData <= 0) {
			binData = 60000; // Default to 60 seconds if invalid
		}
		if (binExcel <= 0) {
			binExcel = binData; // Default to binData if invalid
		}

		// For TOPLEVEL_LR, read from CageCapillariesComputation instead of capillary
		if (resultsOptions.resultType == EnumResults.TOPLEVEL_LR) {
			results.getLRDataFromCage(exp, capillary, binData, binExcel, subtractT0);
		} else {
			results.getDataFromCapillary(exp, capillary, binData, binExcel, resultsOptions, subtractT0);
		}

		// Initialize valuesOut array with the actual size of dataValues
		if (results.getDataValues() != null && results.getDataValues().size() > 0) {
			int actualSize = results.getDataValues().size();
			results.initValuesOutArray(actualSize, Double.NaN);
		} else {
			// Fallback to calculated size if no data
			int nOutputFrames = exp.getNOutputFrames(resultsOptions);
			results.initValuesOutArray(nOutputFrames, Double.NaN);
		}

		return results;
	}

	/**
	 * Gets the number of output frames for the experiment.
	 * 
	 * @param exp     The experiment
	 * @param options The export options
	 * @return The number of output frames
	 */
	protected int getNOutputFrames(Experiment exp, ResultsOptions options) {
		// For capillaries, use kymograph timing
		long kymoFirst_ms = exp.getKymoFirst_ms();
		long kymoLast_ms = exp.getKymoLast_ms();
		long kymoBin_ms = exp.getKymoBin_ms();

		// If buildExcelStepMs equals kymoBin_ms, we want 1:1 mapping - use actual frame
		// count
		if (kymoBin_ms > 0 && options.buildExcelStepMs == kymoBin_ms && exp.getSeqKymos() != null) {
			ImageLoader imgLoader = exp.getSeqKymos().getImageLoader();
			if (imgLoader != null) {
				int nFrames = imgLoader.getNTotalFrames();
				if (nFrames > 0) {
					return nFrames;
				}
			}
		}

		if (kymoLast_ms <= kymoFirst_ms) {
			// Try to get from kymograph sequence
			if (exp.getSeqKymos() != null) {
				ImageLoader imgLoader = exp.getSeqKymos().getImageLoader();
				if (imgLoader != null) {
					if (kymoBin_ms > 0) {
						kymoLast_ms = kymoFirst_ms + imgLoader.getNTotalFrames() * kymoBin_ms;
						exp.setKymoLast_ms(kymoLast_ms);
					}
				}
			}
		}

		long durationMs = kymoLast_ms - kymoFirst_ms;
		int nOutputFrames = (int) (durationMs / options.buildExcelStepMs + 1);

		if (nOutputFrames <= 1) {
			handleExportError(exp, -1);
			// Fallback to a reasonable default
			nOutputFrames = 1000;
		}

		return nOutputFrames;
	}

	/**
	 * Handles export errors by logging them.
	 * 
	 * @param exp           The experiment
	 * @param nOutputFrames The number of output frames
	 */
	protected void handleExportError(Experiment exp, int nOutputFrames) {
		String error = String.format(
				"ResultsFromCapillaries:ExportError() ERROR in %s\n nOutputFrames=%d kymoFirstCol_Ms=%d kymoLastCol_Ms=%d",
				exp.getExperimentDirectory(), nOutputFrames, exp.getKymoFirst_ms(), exp.getKymoLast_ms());
		System.err.println(error);
	}

	public ResultsArray getMeasuresFromAllCapillaries(Experiment exp, ResultsOptions resultsOptions) {

		// Note: computations (dispatch to cages, evaporation correction, L+R measures)
		// should be performed before calling this method via
		// exp.prepareComputations(resultsOptions)

		double scalingFactorToPhysicalUnits = exp.getCapillaries()
				.getScalingFactorToPhysicalUnits(resultsOptions.resultType);

		long kymoBin_ms = exp.getKymoBin_ms();
		if (kymoBin_ms <= 0) {
			kymoBin_ms = 60000;
		}

		ResultsArray resultsArray = new ResultsArray();
		List<Capillary> capillaries = exp.getCapillaries().getList();
		if (capillaries == null) {
			Logger.warn("Capillaries list is null");
			return resultsArray;
		}

		for (Capillary capillary : capillaries) {
			try {
				Results results = getCapillaryMeasure(exp, capillary, resultsOptions);
				if (results != null) {
					results.transferDataValuesToValuesOut(scalingFactorToPhysicalUnits, resultsOptions.resultType);
					resultsArray.addRow(results);
				}
			} catch (Exception e) {
				Logger.warn("Error processing capillary: " + e.getMessage());
			}
		}

		return resultsArray;
	}

	// ---------------------------------

	private int getLen(Results rowL, Results rowR) {
		int lenL = rowL.valuesOut.length;
		int lenR = rowR.valuesOut.length;
		return Math.min(lenL, lenR);
	}

	public void getPI_and_SUM_from_LR(Results rowL, Results rowR, double threshold) {
		int len = getLen(rowL, rowR);
		for (int index = 0; index < len; index++) {
			double dataL = rowL.valuesOut[index];
			double dataR = rowR.valuesOut[index];

			double pi = 0.;
			double sum = Math.abs(dataL) + Math.abs(dataR);
			if (sum != 0. && sum >= threshold) {
				pi = (dataL - dataR) / sum;
			}
			rowL.valuesOut[index] = sum;
			rowR.valuesOut[index] = pi;
		}
	}

	void getMinTimeToGulpLR(Results rowL, Results rowR, Results rowOut) {
		int len = getLen(rowL, rowR);
		for (int index = 0; index < len; index++) {
			double dataMax = Double.NaN;
			double dataL = rowL.valuesOut[index];
			double dataR = rowR.valuesOut[index];
			if (dataL <= dataR)
				dataMax = dataL;
			else if (dataL > dataR)
				dataMax = dataR;
			rowOut.valuesOut[index] = dataMax;
		}
	}

	void getMaxTimeToGulpLR(Results rowL, Results rowR, Results rowOut) {
		int len = getLen(rowL, rowR);
		for (int index = 0; index < len; index++) {
			double dataMin = Double.NaN;
			double dataL = rowL.valuesOut[index];
			double dataR = rowR.valuesOut[index];
			if (dataL >= dataR)
				dataMin = dataL;
			else if (dataL < dataR)
				dataMin = dataR;
			rowOut.valuesOut[index] = dataMin;
		}
	}

	// ---------------------------------------------------

	public Results getNextRowIfSameCage(int irow) {
		Results rowL = resultsList.get(irow);
		int cellL = getCageFromKymoFileName(rowL.getName());
		Results rowR = null;
		if (irow + 1 < resultsList.size()) {
			rowR = resultsList.get(irow + 1);
			int cellR = getCageFromKymoFileName(rowR.getName());
			if (cellR != cellL)
				rowR = null;
		}
		return rowR;
	}

	protected int getCageFromKymoFileName(String name) {
		if (!name.contains("line"))
			return -1;
		return Integer.valueOf(name.substring(4, 5));
	}

}
