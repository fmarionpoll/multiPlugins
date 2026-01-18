package plugins.fmp.multitools.experiment.capillaries.computations;

import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillary;
import plugins.fmp.multitools.experiment.capillaries.CapillaryGulps;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.MeasurementComputation;
import plugins.fmp.multitools.tools.results.ResultsOptions;

/**
 * Computations for correlation-based measures (AUTOCORREL, CROSSCORREL, MARKOV_CHAIN).
 */
public class CorrelationComputation {

	/**
	 * Computes autocorrelation for a single capillary (AUTOCORREL).
	 * Computes correlation between gulp events at time t and t+lag.
	 */
	public static MeasurementComputation computeAutocorrelation() {
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
			int nBins = options.nBinsCorrelation;

			if (binData <= 0) {
				binData = 60000;
			}
			if (binExcel <= 0) {
				binExcel = binData;
			}

			// Get gulp events as binary (0/1) array
			ArrayList<Integer> events = gulps.getMeasuresFromGulps(
					EnumResults.AUTOCORREL, npoints, binData, binExcel);

			if (events == null || events.isEmpty()) {
				return new ArrayList<>();
			}

			return computeAutocorrelationForEvents(events, nBins);
		};
	}

	/**
	 * Computes autocorrelation for L+R combined measures (AUTOCORREL_LR).
	 */
	public static MeasurementComputation computeAutocorrelationLR() {
		return (exp, cap, options) -> {
			// For LR, we need to combine L and R capillaries from the same cage
			Cage cage = exp.getCages().getCageFromID(cap.getCageID());
			if (cage == null) {
				return null;
			}

			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries.size() < 2) {
				return computeAutocorrelation().compute(exp, cap, options);
			}

			// Combine events from L and R capillaries (OR operation: event if either has a gulp)
			ArrayList<Integer> combinedEvents = combineCapillaryEvents(exp, capillaries, options);

			if (combinedEvents == null || combinedEvents.isEmpty()) {
				return new ArrayList<>();
			}

			int nBins = options.nBinsCorrelation;
			return computeAutocorrelationForEvents(combinedEvents, nBins);
		};
	}

	/**
	 * Computes cross-correlation between L and R capillaries (CROSSCORREL).
	 */
	public static MeasurementComputation computeCrosscorrelation() {
		return (exp, cap, options) -> {
			Cage cage = exp.getCages().getCageFromID(cap.getCageID());
			if (cage == null) {
				return null;
			}

			List<Capillary> capillaries = cage.getCapillaries(exp.getCapillaries());
			if (capillaries.size() < 2) {
				return new ArrayList<>();
			}

			// Find L and R capillaries
			Capillary capL = null;
			Capillary capR = null;
			for (Capillary c : capillaries) {
				String side = c.getCapillarySide();
				if (side != null && (side.contains("L") || side.contains("1"))) {
					capL = c;
				} else if (side != null && (side.contains("R") || side.contains("2"))) {
					capR = c;
				}
			}

			if (capL == null || capR == null) {
				return new ArrayList<>();
			}

			// Get events from both capillaries
			ArrayList<Integer> eventsL = getGulpEvents(exp, capL, options);
			ArrayList<Integer> eventsR = getGulpEvents(exp, capR, options);

			if (eventsL == null || eventsR == null || eventsL.isEmpty() || eventsR.isEmpty()) {
				return new ArrayList<>();
			}

			// Align sizes
			int size = Math.min(eventsL.size(), eventsR.size());
			int nBins = options.nBinsCorrelation;

			return computeCrosscorrelationForEvents(eventsL, eventsR, size, nBins);
		};
	}

	/**
	 * Computes cross-correlation at cage level (CROSSCORREL_LR).
	 * Similar to CROSSCORREL but computed differently if needed.
	 */
	public static MeasurementComputation computeCrosscorrelationLR() {
		// For now, same as CROSSCORREL
		return computeCrosscorrelation();
	}

	/**
	 * Computes Markov chain transition probabilities (MARKOV_CHAIN).
	 * Returns transition state: 0 = no transition, 1 = transition from no-gulp to gulp,
	 * 2 = transition from gulp to no-gulp, etc.
	 */
	public static MeasurementComputation computeMarkovChain() {
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

			// Get gulp events as binary array
			ArrayList<Integer> events = gulps.getMeasuresFromGulps(
					EnumResults.MARKOV_CHAIN, npoints, binData, binExcel);

			if (events == null || events.isEmpty()) {
				return new ArrayList<>();
			}

			// Compute transitions: 0->1 (no-gulp to gulp) = 1, 1->0 (gulp to no-gulp) = -1
			ArrayList<Double> transitions = new ArrayList<>(events.size());
			transitions.add(0.0); // First point has no transition

			for (int i = 1; i < events.size(); i++) {
				int prev = events.get(i - 1);
				int curr = events.get(i);
				if (prev == 0 && curr == 1) {
					transitions.add(1.0); // Transition to feeding
				} else if (prev == 1 && curr == 0) {
					transitions.add(-1.0); // Transition to not feeding
				} else {
					transitions.add(0.0); // No transition
				}
			}

			return transitions;
		};
	}

	// Helper methods

	private static ArrayList<Integer> getGulpEvents(Experiment exp, Capillary cap, ResultsOptions options) {
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

		return gulps.getMeasuresFromGulps(EnumResults.AUTOCORREL, npoints,
				binData, binExcel);
	}

	private static ArrayList<Integer> combineCapillaryEvents(Experiment exp, List<Capillary> capillaries,
			ResultsOptions options) {
		ArrayList<Integer> combined = null;
		int maxSize = 0;

		for (Capillary cap : capillaries) {
			ArrayList<Integer> events = getGulpEvents(exp, cap, options);
			if (events != null) {
				if (combined == null) {
					maxSize = events.size();
					combined = new ArrayList<>(maxSize);
					for (int i = 0; i < maxSize; i++) {
						combined.add(0);
					}
				}

				int size = Math.min(events.size(), maxSize);
				for (int i = 0; i < size; i++) {
					if (events.get(i) > 0) {
						combined.set(i, 1);
					}
				}
			}
		}

		return combined;
	}

	private static ArrayList<Double> computeAutocorrelationForEvents(ArrayList<Integer> events, int maxLag) {
		ArrayList<Double> correlations = new ArrayList<>(events.size());

		for (int t = 0; t < events.size(); t++) {
			double sum = 0.0;
			int count = 0;

			for (int lag = 1; lag <= maxLag && t + lag < events.size(); lag++) {
				int val_t = events.get(t);
				int val_t_lag = events.get(t + lag);

				// Correlation: E[X(t) * X(t+lag)] - E[X(t)] * E[X(t+lag)]
				// For binary events, this is simpler
				sum += val_t * val_t_lag;
				count++;
			}

			correlations.add(count > 0 ? sum / count : 0.0);
		}

		return correlations;
	}

	private static ArrayList<Double> computeCrosscorrelationForEvents(ArrayList<Integer> eventsL,
			ArrayList<Integer> eventsR, int size, int maxLag) {
		ArrayList<Double> correlations = new ArrayList<>(size);

		for (int t = 0; t < size; t++) {
			double sum = 0.0;
			int count = 0;

			for (int lag = -maxLag; lag <= maxLag; lag++) {
				int idxR = t + lag;
				if (idxR >= 0 && idxR < size) {
					int valL = eventsL.get(t);
					int valR = eventsR.get(idxR);
					sum += valL * valR;
					count++;
				}
			}

			correlations.add(count > 0 ? sum / count : 0.0);
		}

		return correlations;
	}
}

