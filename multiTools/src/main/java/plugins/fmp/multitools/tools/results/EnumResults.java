package plugins.fmp.multitools.tools.results;

import java.util.ArrayList;

import plugins.fmp.multitools.experiment.capillaries.computations.CorrelationComputation;
import plugins.fmp.multitools.experiment.capillaries.computations.GulpMeasureComputation;

/**
 * Enumeration of all available measurement types.
 * 
 * <p>Each measurement type has a computation strategy that indicates how the data is obtained:
 * <ul>
 * <li><b>STORED_DATA</b> - Data stored directly in capillary/spot measurement fields 
 *     (e.g., measurements.ptsTop, measurements.ptsBottom, measurements.ptsGulps).
 *     Retrieved via getter methods without computation - the data was already computed/stored during processing.</li>
 * <li><b>Computation method reference</b> - Derived measure computed on-the-fly using MeasurementComputation interface</li>
 * <li><b>NOT_IMPLEMENTED</b> - Measure declared but computation algorithm not yet implemented</li>
 * </ul>
 * </p>
 */
public enum EnumResults {

	// === STORED DATA MEASURES (each has specific accessor method showing what field/method is used) ===
	TOPRAW("topraw", "volume (ul)", "top liquid level (t-t0)", StoredDataAccessors.accessStored_TOPRAW()),
	TOPLEVEL("toplevel", "volume (ul)", "top liquid compensated for evaporation (t-t0)", StoredDataAccessors.accessStored_TOPLEVEL()),
	BOTTOMLEVEL("bottomlevel", "volume (ul)", "bottom liquid level (t-t0)", StoredDataAccessors.accessStored_BOTTOMLEVEL()),
	TOPLEVELDIRECT("topleveldirect", "volume (ul)", "top liquid from direct cam detection (t-t0)", StoredDataAccessors.accessStored_TOPLEVELDIRECT()),
	BOTTOMLEVELDIRECT("bottomleveldirect", "volume (ul)", "bottom liquid from direct cam detection (t-t0)", StoredDataAccessors.accessStored_BOTTOMLEVELDIRECT()),
	DERIVEDVALUES("derivative", "volume (ul)", "derived top liquid level (t-t0)", StoredDataAccessors.accessStored_DERIVEDVALUES()),

	TOPLEVEL_LR("toplevel_L+R", "volume (ul)", "volume consumed in capillaries / cage (t-t0)", StoredDataAccessors.accessStored_TOPLEVEL_LR()),
	TOPLEVELDELTA("topdelta", "volume (ul)", "top liquid consumed (t - t-1)", StoredDataAccessors.accessStored_TOPLEVELDELTA()),
	TOPLEVELDELTA_LR("topdelta_L+R", "volume (ul)", "volume consumed in capillaries /cage (t - t-1)", StoredDataAccessors.accessStored_TOPLEVELDELTA_LR()),

	// === GULP MEASURES (handled via CapillaryGulps.getMeasuresFromGulps) ===
	SUMGULPS("sumGulps", "volume (ul)", "cumulated volume of gulps (t-t0)", StoredDataAccessors.accessStored_SUMGULPS()),
	SUMGULPS_LR("sumGulps_L+R", "volume (ul)", "cumulated volume of gulps / cage (t-t0)", StoredDataAccessors.accessStored_SUMGULPS_LR()),

	// === COMPUTED GULP MEASURES ===
	NBGULPS("nbGulps", "volume (ul)", "number of gulps (at t)", GulpMeasureComputation.computeNbGulps()),
	AMPLITUDEGULPS("amplitudeGulps", "volume (ul)", "amplitude of gulps (at t)", GulpMeasureComputation.computeAmplitudeGulps()),
	TTOGULP("tToGulp", "minutes", "time to previous gulp(at t)", GulpMeasureComputation.computeTToGulp()),
	TTOGULP_LR("tToGulp_LR", "minutes", "time to previous gulp of either capillary (at t)", StoredDataAccessors.notImplemented_TTOGULP_LR()),

	// === COMPUTED CORRELATION MEASURES ===
	MARKOV_CHAIN("markov_chain", "n observ", "boolean transition (at t)", CorrelationComputation.computeMarkovChain()),
	AUTOCORREL("autocorrel", "n observ", "auto-correlation (at t, over n intervals)", CorrelationComputation.computeAutocorrelation()),
	AUTOCORREL_LR("autocorrel_LR", "n observ", "auto-correlation over capillaries/cage (at t, over n intervals)", CorrelationComputation.computeAutocorrelationLR()),
	CROSSCORREL("crosscorrel", "n observ", "cross-correlation (at t, over n intervals)", CorrelationComputation.computeCrosscorrelation()),
	CROSSCORREL_LR("crosscorrel_LR", "n observ", "cross-correlation over capillaries/cage (at t, over n intervals)", CorrelationComputation.computeCrosscorrelationLR()),

	// === FLY POSITION MEASURES (stored data) ===
	XYIMAGE("xy-image", "mm", "xy image", StoredDataAccessors.accessStored_XYIMAGE()),
	XYTOPCAGE("xy-topcage", "mm", "xy top cage", StoredDataAccessors.accessStored_XYTOPCAGE()),
	XYTIPCAPS("xy-tipcaps", "mm", "xy tip capillaries", StoredDataAccessors.accessStored_XYTIPCAPS()),
	ELLIPSEAXES("ellipse-axes", "mm", "Ellipse of axes", StoredDataAccessors.accessStored_ELLIPSEAXES()),
	DISTANCE("distance", "mm", "Distance between consecutive points", StoredDataAccessors.accessStored_DISTANCE()),
	ISALIVE("_alive", "yes/no", "Fly alive or not", StoredDataAccessors.accessStored_ISALIVE()),
	SLEEP("sleep", "yes, no", "Fly sleeping", StoredDataAccessors.accessStored_SLEEP()),

	// === SPOT AREA MEASURES (stored data) ===
	AREA_SUM("AREA_SUM", "grey value", "Consumption (estimated/threshold)", StoredDataAccessors.accessStored_AREA_SUM()),
	AREA_SUMCLEAN("AREA_SUMCLEAN", "grey value - no fly", "Consumption (estimated/threshold)", StoredDataAccessors.accessStored_AREA_SUMCLEAN()),
	AREA_OUT("AREA_OUT", "pixel grey value", "background", StoredDataAccessors.accessStored_AREA_OUT()),
	AREA_DIFF("AREA_DIFF", "grey value - background", "diff", StoredDataAccessors.accessStored_AREA_DIFF()),
	AREA_FLYPRESENT("AREA_FLYPRESENT", "boolean value", "Fly is present or not over the spot", StoredDataAccessors.accessStored_AREA_FLYPRESENT());


	private String label;
	private String unit;
	private String title;
	private MeasurementComputation computationStrategy;

	EnumResults(String label, String unit, String title, MeasurementComputation computationStrategy) {
		this.label = label;
		this.unit = unit;
		this.title = title;
		this.computationStrategy = computationStrategy;
	}

	public String toString() {
		return label;
	}

	public String toUnit() {
		return unit;
	}

	public String toTitle() {
		return title;
	}

	/**
	 * Gets the computation strategy for this measure type.
	 * 
	 * @return The computation strategy:
	 *         - Actual computation method for computed measures
	 *         - STORED_DATA marker for measures accessed from stored fields
	 *         - NOT_IMPLEMENTED marker for measures not yet implemented
	 */
	public MeasurementComputation getComputationStrategy() {
		return computationStrategy;
	}

	/**
	 * Checks if this measure type requires computation (has a real computation algorithm).
	 * 
	 * @return true if computation is required (not stored data and not not-implemented)
	 */
	public boolean requiresComputation() {
		if (computationStrategy == null) {
			return false;
		}
		try {
			// Try calling it - if it throws UnsupportedOperationException, it's stored data
			computationStrategy.compute(null, null, null);
			// If it returns null without exception, check if it's the not-implemented marker
			return true; // Has actual computation logic
		} catch (UnsupportedOperationException e) {
			// This is a stored data accessor
			return false;
		}
	}

	/**
	 * Checks if this measure type uses stored data (direct access to measurement fields).
	 * 
	 * @return true if data is stored in measurement fields and accessed directly
	 */
	public boolean isStoredData() {
		if (computationStrategy == null) {
			return false;
		}
		try {
			computationStrategy.compute(null, null, null);
			return false;
		} catch (UnsupportedOperationException e) {
			// If it throws UnsupportedOperationException, it's a stored data accessor
			return e.getMessage().contains("uses stored data");
		}
	}

	/**
	 * Checks if this measure type is not yet implemented.
	 * 
	 * @return true if this measure is declared but computation is not implemented
	 */
	public boolean isNotImplemented() {
		if (computationStrategy == null) {
			return true;
		}
		try {
			ArrayList<Double> result = computationStrategy.compute(null, null, null);
			// If it returns null without exception, it's the not-implemented marker
			return result == null;
		} catch (UnsupportedOperationException e) {
			return false; // It's stored data, not not-implemented
		} catch (Exception e) {
			// Any other exception means it tried to compute, so it's implemented
			return false;
		}
	}

	public static EnumResults findByText(String abbr) {
		for (EnumResults v : values()) {
			if (v.toString().equals(abbr))
				return v;
		}
		return null;
	}
}
