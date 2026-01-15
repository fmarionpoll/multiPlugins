package plugins.fmp.multitools.tools.results;

import java.util.ArrayList;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillary;

/**
 * Functional interface for computing derived measurements from capillary data.
 * This allows EnumResults to reference computation methods for measures that
 * cannot be directly accessed from stored data.
 */
@FunctionalInterface
public interface MeasurementComputation {
	/**
	 * Computes the measurement values for a given capillary.
	 * 
	 * @param exp     The experiment containing the capillary
	 * @param cap     The capillary to compute measurements for
	 * @param options The results options containing computation parameters
	 * @return A list of measurement values (one per time bin), or null if
	 *         computation cannot be performed
	 */
	ArrayList<Double> compute(Experiment exp, Capillary cap, ResultsOptions options);
}


