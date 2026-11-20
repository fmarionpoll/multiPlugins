package plugins.fmp.multitools.tools.results;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Objects;

import plugins.fmp.multitools.experiment.capillaries.computations.CorrelationComputation;
import plugins.fmp.multitools.experiment.capillaries.computations.CapillaryEventMeasureComputation;

/**
 * Enumeration of all available measurement types.
 * 
 * <p>
 * Each enum value is defined by: a UI {@code label} (used by
 * {@link #toString()}), a {@code unit}, a {@code title}, a
 * {@code computationStrategy} (stored accessor or computed), plus an optional
 * {@code persistenceKey} (canonical on-disk token) and a set of
 * {@code persistenceDomains} used to whitelist which file formats may contain
 * it.
 *
 * Each measurement type has a computation strategy that indicates how the data
 * is obtained:
 * <ul>
 * <li><b>STORED_DATA</b> - Data stored directly in capillary/spot measurement
 * fields (e.g., measurements.ptsTop, measurements.ptsBottom,
 * measurements.ptsGulps). Retrieved via getter methods without computation -
 * the data was already computed/stored during processing.</li>
 * <li><b>Computation method reference</b> - Derived measure computed on-the-fly
 * using MeasurementComputation interface</li>
 * <li><b>NOT_IMPLEMENTED</b> - Measure declared but computation algorithm not
 * yet implemented</li>
 * </ul>
 * </p>
 */
public enum EnumResults {

	// === STORED DATA MEASURES (each has specific accessor method showing what
	// field/method is used) ===
	TOPRAW("topraw", "volume (ul)", "top liquid level (t-t0)", StoredDataAccessors.accessStored_TOPRAW(), "TOPRAW",
			PersistenceDomain.CAPILLARY),
	TOPLEVEL("toplevel", "volume (ul)", "top liquid compensated for evaporation (t-t0)",
			StoredDataAccessors.accessStored_TOPLEVEL(), "TOPLEVEL_CORRECTED", PersistenceDomain.CAPILLARY),
	BOTTOMLEVEL("bottomlevel", "volume (ul)", "bottom liquid level (t-t0)",
			StoredDataAccessors.accessStored_BOTTOMLEVEL(), "BOTTOMLEVEL", PersistenceDomain.CAPILLARY),
	DERIVEDVALUES("temp_change", "volume (ul)", "temporal change of top liquid level (t-t0)",
			StoredDataAccessors.accessStored_DERIVEDVALUES(), "TOPDERIVATIVE", PersistenceDomain.CAPILLARY),
	THRESHOLD("threshold", "value", "dynamic threshold computed from empty cages",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "THRESHOLD", PersistenceDomain.CAPILLARY),

	TOPLEVEL_LR("toplevel_L+R", "volume (ul)", "volume consumed in capillaries / cage (t-t0)",
			StoredDataAccessors.accessStored_TOPLEVEL_LR()),
	TOPLEVEL_SUM("SUM", "volume (ul)", "sum of L+R capillary consumption (t-t0)",
			StoredDataAccessors.accessStored_TOPLEVEL_LR()),
	TOPLEVEL_PI("PI", "PI", "preference index (L-R)/(L+R)", StoredDataAccessors.accessStored_TOPLEVEL_LR()),
	TOPRAW_LR("topraw_L+R", "volume (ul)", "volume from raw top liquid / cage (t-t0)",
			StoredDataAccessors.accessStored_TOPRAW_LR()),
	TOPLEVELDELTA("topdelta", "volume (ul)", "top liquid consumed (t - t-1)",
			StoredDataAccessors.accessStored_TOPLEVELDELTA()),
	TOPLEVELDELTA_LR("topdelta_L+R", "volume (ul)", "volume consumed in capillaries /cage (t - t-1)",
			StoredDataAccessors.accessStored_TOPLEVELDELTA_LR()),

	// === GULP MEASURES (handled via CapillaryGulps.getMeasuresFromGulps) ===
	SUMGULPS("sumGulps", "volume (ul)", "cumulated volume of gulps (t-t0)",
			StoredDataAccessors.accessStored_SUMGULPS()),
	SUMGULPS_LR("sumGulps_L+R", "volume (ul)", "cumulated volume of gulps / cage (t-t0)",
			StoredDataAccessors.accessStored_SUMGULPS_LR()),
	SUMGULPS_SUM("SUM_sumgulps", "volume (ul)", "sum of L+R gulp volume / cage (t-t0)",
			StoredDataAccessors.accessStored_SUMGULPS_LR()),
	SUMGULPS_PI("PI_sumgulps", "PI", "preference index of gulp volume (L-R)/(L+R)",
			StoredDataAccessors.accessStored_SUMGULPS_LR()),

	/**
	 * Capillary persistence stores raw gulp events as a flat CSV section. This is
	 * not used for charting directly, but needs a stable persistence token.
	 */
	GULPS_FLAT("gulpsFlat", "events", "gulp events (flat)", StoredDataAccessors.notImplemented_TTOGULP_LR(),
			"GULPS_FLAT", PersistenceDomain.CAPILLARY),

	// === COMPUTED GULP MEASURES ===
	NBGULPS("nbGulps", "volume (ul)", "number of gulps (at t)", CapillaryEventMeasureComputation.computeNbGulps()),
	AMPLITUDEGULPS("amplitudeGulps", "volume (ul)", "amplitude of gulps (at t)",
			CapillaryEventMeasureComputation.computeAmplitudeGulps()),
	GULP_BIN_RAW("gulp_bin_raw", "volume (ul)", "volume per gulp bin, raw (at t)",
			CapillaryEventMeasureComputation.computeGulpBins(false, false)),
	GULP_BIN_CORR("gulp_bin_corr", "volume (ul)", "volume per gulp bin, minus evaporation (at t)",
			CapillaryEventMeasureComputation.computeGulpBins(true, false)),
	SUM_GULP_BIN_RAW("sum_gulp_bin_raw", "volume (ul)", "cumulated volume of gulp bins, raw (t-t0)",
			CapillaryEventMeasureComputation.computeGulpBins(false, true)),
	SUM_GULP_BIN_CORR("sum_gulp_bin_corr", "volume (ul)",
			"cumulated volume of gulp bins, minus evaporation (t-t0)",
			CapillaryEventMeasureComputation.computeGulpBins(true, true)),
	// Intake events are stored separately from the ordinary gulps.
	INTAKE_BIN_RAW("intake_bin_raw", "volume (ul)", "volume per intake bin, raw (at t)",
			CapillaryEventMeasureComputation.computeIntakeBins(false, false)),
	INTAKE_BIN_CORR("intake_bin_corr", "volume (ul)", "volume per intake bin, minus evaporation (at t)",
			CapillaryEventMeasureComputation.computeIntakeBins(true, false)),
	SUM_INTAKE_BIN_RAW("sum_intake_bin_raw", "volume (ul)", "cumulated volume of intake bins, raw (t-t0)",
			CapillaryEventMeasureComputation.computeIntakeBins(false, true)),
	SUM_INTAKE_BIN_CORR("sum_intake_bin_corr", "volume (ul)", "cumulated volume of intake bins, minus evaporation (t-t0)",
			CapillaryEventMeasureComputation.computeIntakeBins(true, true)),
	TTOGULP("tToGulp", "minutes", "time to previous gulp(at t)", CapillaryEventMeasureComputation.computeTToGulp()),
	TTOGULP_LR("tToGulp_LR", "minutes", "time to previous gulp of either capillary (at t)",
			StoredDataAccessors.notImplemented_TTOGULP_LR()),

	// === COMPUTED CORRELATION MEASURES ===
	MARKOV_CHAIN("markov_chain", "n observ", "boolean transition (at t)", CorrelationComputation.computeMarkovChain()),
	AUTOCORREL("autocorrel", "n observ", "auto-correlation (at t, over n intervals)",
			CorrelationComputation.computeAutocorrelation()),
	AUTOCORREL_LR("autocorrel_LR", "n observ", "auto-correlation over capillaries/cage (at t, over n intervals)",
			CorrelationComputation.computeAutocorrelationLR()),
	CROSSCORREL("crosscorrel", "n observ", "cross-correlation (at t, over n intervals)",
			CorrelationComputation.computeCrosscorrelation()),
	CROSSCORREL_LR("crosscorrel_LR", "n observ", "cross-correlation over capillaries/cage (at t, over n intervals)",
			CorrelationComputation.computeCrosscorrelationLR()),

	// === FLY POSITION MEASURES (stored data) ===
	XYIMAGE("xy-image", "mm", "xy image", StoredDataAccessors.accessStored_XYIMAGE()),
	/**
	 * Distance from fly center to food side (per-cage FoodSide on CageProperties).
	 */
	YVSFOOD("dist-food", "mm", "distance vs food", StoredDataAccessors.accessStored_YVSFOOD()),
	XTOPCAGE("xy-topcage", "mm", "xy top cage", StoredDataAccessors.accessStored_XTOPCAGE()),
	YTOPCAGE("xy-topcage", "mm", "xy top cage", StoredDataAccessors.accessStored_YTOPCAGE()),
	ELLIPSEAXES("ellipse-axes", "mm", "Ellipse of axes", StoredDataAccessors.accessStored_ELLIPSEAXES()),
	DISTANCE("distance", "mm", "Distance between consecutive points", StoredDataAccessors.accessStored_DISTANCE()),
	ISALIVE("alive", "yes(1)/no(0)", "Fly alive or not", StoredDataAccessors.accessStored_ISALIVE()),
	SLEEP("sleep", "yes, no", "Fly sleeping", StoredDataAccessors.accessStored_SLEEP()),
	ILLUM_PHASE("illumPhase", "light/dark", "Detect2 dual-background lighting phase (0=light, 1=dark)",
			StoredDataAccessors.accessStored_ILLUM_PHASE()),
	VISIBLE_FLY_COUNT("visibleFlyCount", "flies", "Number of visible flies detected in cage (at t)",
			StoredDataAccessors.accessStored_VISIBLE_FLY_COUNT()),

	// === SPOT AREA MEASURES (stored data) ===
	FILTER_GREY("AREA_SUM", "grey value", "Consumption (estimated/threshold)", StoredDataAccessors.accessStored_AREA_SUM(),
			"AREA_SUM", PersistenceDomain.SPOT),
	FILTER_GREY_FLY_MASKED("AREA_SUMNOFLY", "grey value - no fly, filter", "Consumption (estimated/threshold)",
			StoredDataAccessors.accessStored_AREA_SUMNOFLY(), "AREA_SUMNOFLY", PersistenceDomain.SPOT),
	FILTER_GREY_SMOOTHED("AREA_SUMCLEAN", "grey value - no fly, filter", "Consumption (estimated/threshold)",
			StoredDataAccessors.accessStored_AREA_SUMCLEAN(), "AREA_SUMCLEAN", PersistenceDomain.SPOT),
	/**
	 * V2 spot consumption signal (separate channel): background-corrected and/or
	 * robust statistics version of FILTER_GREY. Used for side-by-side comparison with
	 * legacy FILTER_GREY without changing legacy outputs.
	 */
	FILTER_GREY_CONTRAST("AREA_SUM_V2", "grey value", "Consumption V2 (comparison channel)",
			StoredDataAccessors.accessStored_AREA_SUM_V2(), "AREA_SUM_V2", PersistenceDomain.SPOT),
	FILTER_GREY_CONTRAST_MASKED("AREA_SUMNOFLY_V2", "grey value - no fly, filter", "Consumption V2 (estimated/threshold)",
			StoredDataAccessors.accessStored_AREA_SUMNOFLY_V2(), "AREA_SUMNOFLY_V2", PersistenceDomain.SPOT),
	FILTER_GREY_CONTRAST_SMOOTHED("AREA_SUMCLEAN_V2", "grey value - no fly, filter", "Consumption V2 (estimated/threshold)",
			StoredDataAccessors.accessStored_AREA_SUMCLEAN_V2(), "AREA_SUMCLEAN_V2", PersistenceDomain.SPOT),
	/**
	 * V3 Tier A: per-spot {@code sumClean} shifted by an early-bin median, minus
	 * the per-bin median of those shifted values over a pool (whole experiment or
	 * per cage), optionally smoothed.
	 */
	FILTER_GREY_RESIDUAL("AREA_SUMCLEAN_V3", "grey value - median (V3)", "Consumption V3 (residual vs pooled median)",
			StoredDataAccessors.accessStored_AREA_SUMCLEAN_V3(), "AREA_SUMCLEAN_V3", PersistenceDomain.SPOT),
	AREA_OUT("AREA_OUT", "pixel grey value", "background", StoredDataAccessors.accessStored_AREA_OUT()),
	AREA_DIFF("AREA_DIFF", "grey value - background", "diff", StoredDataAccessors.accessStored_AREA_DIFF()),
	FLY_PIXEL_COUNT("AREA_FLYPRESENT", "% of spot ROI", "Fly occupancy over the spot (% of ROI pixels)",
			StoredDataAccessors.accessStored_AREA_FLYPRESENT(), "AREA_FLYPRESENT", PersistenceDomain.SPOT),

	/**
	 * V5: count of ROI pixels above spot threshold (NaN when fly-pixel count ≥
	 * legacy occupancy gate, see
	 * {@link plugins.fmp.multitools.experiment.spots.Spots#getMinFlyPixelsForOccupancyGate}).
	 */
	FILTER_V5_PIXEL_COUNT("AREA_COUNT_V5", "pixels", "Over-threshold spot pixels (V5)",
			StoredDataAccessors.accessStored_AREA_COUNT_V5(), "AREA_COUNT_V5", PersistenceDomain.SPOT),
	/**
	 * V5: sum of spot-channel grey on over-threshold pixels, divided by ROI pixel
	 * count (same scale as legacy {@code FILTER_GREY}; NaN under same fly gate as
	 * {@link #FILTER_V5_PIXEL_COUNT}).
	 */
	FILTER_V5_GREY("GREY_SUM_V5", "grey / ROI px",
			"Grey on over-threshold pixels / all ROI pixels (V5, legacy AREA_SUM scale)",
			StoredDataAccessors.accessStored_GREY_SUM_V5(), "GREY_SUM_V5", PersistenceDomain.SPOT),
	/**
	 * V5 diagnostic: same grey as {@link #FILTER_V5_GREY} but written before the
	 * fly-occupancy gate sets bins to NaN (still NaN where the fly mask prevents
	 * computing scalars). Filled during camera V5 detection.
	 */
	FILTER_V5_GREY_PRE_GATE("GREY_SUM_V5_PREFLY", "grey / ROI px",
			"V5 grey on over-threshold pixels / ROI before fly-occupancy NaN gate",
			StoredDataAccessors.accessStored_GREY_SUM_V5_PREFLY(), "GREY_SUM_V5_PREFLY", PersistenceDomain.SPOT),
	/**
	 * V5: running-median smooth of {@link #FILTER_V5_GREY} (span 10, NaN-robust; same
	 * spirit as legacy {@code FILTER_GREY_SMOOTHED} from {@code sumNoFly}).
	 */
	FILTER_V5_GREY_SMOOTHED("GREY_SUM_CLEAN_V5", "grey / ROI px",
			"V5 grey: NaN gaps bridged, optional upward spike trim vs local median, then running median of GREY_SUM_V5",
			StoredDataAccessors.accessStored_GREY_SUM_CLEAN_V5(), "GREY_SUM_CLEAN_V5", PersistenceDomain.SPOT),

	/**
	 * Computed cage-level aggregate: sum over spots grouped by (stimulus,
	 * concentration) of normalized FILTER_GREY_SMOOTHED consumption (not persisted as its
	 * own spot field).
	 */
	AGG_FILTER_DEPLETION("AGG_SUMCLEAN", "spots consumed (0..N)",
			"Sum of per-spot normalized depletion by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_SUMCLEAN"),

	/**
	 * Same grouping and normalization as {@link #AGG_FILTER_DEPLETION}, but each spot's
	 * intensity trace is {@link #FILTER_V5_GREY_SMOOTHED} (median-smoothed V5 grey)
	 * instead of legacy {@code FILTER_GREY_SMOOTHED}.
	 */
	AGG_FILTER_V5_DEPLETION("AGG_SUMCLEAN_V5", "spots consumed (0..N)",
			"Sum of per-spot normalized depletion from the smoothed V5 grey signal by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_SUMCLEAN_V5"),

	/**
	 * Cage-level aggregate: sum over spots grouped by (stimulus, concentration) of
	 * per-bin {@link #FILTER_V5_PIXEL_COUNT} (over-threshold pixel counts). Not persisted
	 * on spots.
	 */
	AGG_FILTER_V5_PIXEL_SUM("AGG_AREA_COUNT_V5", "pixels",
			"Sum of selected pixel counts by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_AREA_COUNT_V5"),

	/**
	 * Color-distance spot detection — same roles as V5 counterparts. On-disk keys
	 * remain {@code *_V6}.
	 */
	COLOR_PIXEL_COUNT("AREA_COUNT_COLOR", "pixels", "Over-threshold spot pixels (color-distance)",
			StoredDataAccessors.accessStored_AREA_COUNT_V6(), "AREA_COUNT_V6", PersistenceDomain.SPOT),
	COLOR_GREY("GREY_SUM_COLOR", "grey / ROI px", "Grey on over-threshold pixels / all ROI pixels (color-distance)",
			StoredDataAccessors.accessStored_GREY_SUM_V6(), "GREY_SUM_V6", PersistenceDomain.SPOT),
	COLOR_GREY_PRE_GATE("GREY_SUM_COLOR_PREFLY", "grey / ROI px", "Color-distance grey before fly-occupancy NaN gate",
			StoredDataAccessors.accessStored_GREY_SUM_V6_PREFLY(), "GREY_SUM_V6_PREFLY", PersistenceDomain.SPOT),
	COLOR_GREY_SMOOTHED("GREY_SUM_CLEAN_COLOR", "grey / ROI px",
			"Color-distance grey: cleaned from GREY_SUM_COLOR (same pipeline as V5)",
			StoredDataAccessors.accessStored_GREY_SUM_CLEAN_V6(), "GREY_SUM_CLEAN_V6", PersistenceDomain.SPOT),
	AGG_COLOR_DEPLETION("AGG_SUMCLEAN_COLOR", "spots consumed (0..N)",
			"Sum of per-spot normalized depletion from the smoothed color-distance grey signal by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_SUMCLEAN_COLOR"),
	AGG_COLOR_PIXEL_SUM("AGG_AREA_COUNT_COLOR", "pixels",
			"Sum of selected pixel counts by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_AREA_COUNT_COLOR"),

	/**
	 * Computed cage-level curve: per-bin median of {@code FILTER_GREY} across spots,
	 * excluding bins with fly on spot ({@code FLY_PIXEL_COUNT}); shown with
	 * consumption aggregate (inverted axis).
	 */
	CAGE_GREY_MEDIAN("AGG_MEDIANREF", "grey value", "Median normalized grey signal across the cage's spots, excluding fly-occluded samples.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_MEDIANREF"),

	/**
	 * Kymograph: per-bin sum of per-spot consumption
	 * {@code Σ (1 − KYMO_GREEN_HEIGHT_RATIO)}, grouped by (stimulus, concentration)
	 * per cage.
	 */
	AGG_KYMO_HEIGHT_LOSS("AGG_GREENHEIGHT_CONSO", "Σ (1−h/h_max)",
			"Sum of per-spot height loss relative to each spot's peak, grouped by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_GREENHEIGHT_CONSO"),

	/**
	 * Kymograph-only: fraction of vertical strip rows with metric above threshold
	 * (not persisted on spots). {@link #toPersistenceKey()} remains
	 * {@code KYMO_CHROMA_FRACT} for saved data compatibility.
	 */
	KYMO_ROW_FRACTION("KYMO_FRACT", "fraction (0-1)", "Kymograph: fraction of rows with selected metric above threshold",
			StoredDataAccessors.accessStored_KYMO_FRACT(), "KYMO_CHROMA_FRACT", PersistenceDomain.SPOT),
	/** Kymograph: absolute bin-to-bin change in metric fraction. */
	KYMO_ROW_FRACTION_CHANGE("KYMO_ABS_DELTA", "|Δf|", "Kymograph: |Δf| of metric fraction between consecutive time bins",
			StoredDataAccessors.accessStored_KYMO_ABS_DELTA(), "KYMO_CHROMA_ABS_DELTA", PersistenceDomain.SPOT),
	/** Kymograph: per-bin mean of per-spot metric fractions within a cage. */
	KYMO_CAGE_MEAN_FRACT("KYMO_CAGE_MEAN", "fraction (0-1)", "Kymograph: cage mean of spot metric fractions",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "KYMO_CHROMA_CAGE_MEAN_FRACT"),
	/** Kymograph: per-bin mean of per-spot |Δf| within a cage. */
	KYMO_CAGE_MEAN_ABS_DELTA("KYMO_CAGE_MEAN_DF", "|Δf|", "Kymograph: cage mean of |Δf| across spots",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "KYMO_CHROMA_CAGE_MEAN_ABS_DELTA"),
	/**
	 * Kymograph: count of ON rows in the cleaned green mask per time bin (bar
	 * height in pixels).
	 */
	KYMO_GREEN_HEIGHT("KYMO_GREEN_HEIGHT", "rows",
			"Kymograph: vertical extent (rows) of cleaned green mask per time bin",
			StoredDataAccessors.accessStored_KYMO_GREEN_HEIGHT(), "KYMO_GREEN_HEIGHT", PersistenceDomain.SPOT),
	/**
	 * Kymograph: green height divided by the maximum green height across the strip
	 * (peak-normalized occupancy).
	 */
	KYMO_GREEN_HEIGHT_RATIO("KYMO_GREEN_HEIGHT_RATIO", "h / h_max",
			"Kymograph: green mask height relative to strip maximum",
			StoredDataAccessors.accessStored_KYMO_GREEN_HEIGHT_RATIO(), "KYMO_GREEN_HEIGHT_RATIO",
			PersistenceDomain.SPOT),
	/** Kymograph: per-bin mean of per-spot green height ratios within a cage. */
	KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO("KYMO_CAGE_MEAN_H", "h / h_max",
			"Kymograph: cage mean of peak-normalized green height ratio across spots",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO"),
	/**
	 * Horizontal line through a row of spots: red-deficit integral divided by its
	 * median over the initial window. Zero is the floor outside the circles.
	 */
	KYMO_CROSS_RATIO("KYMO_LINE_RATIO", "I / I0",
			"Line: fraction of the cross above the floor, relative to the initial window",
			StoredDataAccessors.accessStored_KYMO_LINE_RATIO(), "KYMO_LINE_RATIO", PersistenceDomain.SPOT),
	/**
	 * Rim of the physical spot: fraction of the outline still above the outer
	 * floor band, relative to the initial window.
	 */
	KYMO_RIM_RATIO("KYMO_RIM_RATIO", "I / I0",
			"Rim: fraction of the outline above the outer floor, relative to the initial window",
			StoredDataAccessors.accessStored_KYMO_RIM_RATIO(), "KYMO_RIM_RATIO", PersistenceDomain.SPOT),
	/**
	 * Line analysis: per-bin sum of per-spot consumption {@code Σ (1 − KYMO_CROSS_RATIO)},
	 * grouped by (stimulus, concentration) per cage.
	 */
	AGG_KYMO_CROSS_LOSS("AGG_LINE_CONSO", "Σ (1−I/I0)",
			"Sum of per-spot cross-line signal loss relative to each spot's initial baseline, grouped by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_LINE_CONSO"),
	/**
	 * Rim analysis: per-bin sum of per-spot consumption {@code Σ (1 − KYMO_RIM_RATIO)},
	 * grouped by (stimulus, concentration) per cage.
	 */
	AGG_KYMO_RIM_LOSS("AGG_RIM", "Σ (1−I/I0)",
			"Sum of per-spot rim coverage loss relative to each spot's initial baseline, grouped by compound and concentration within each cage.",
			StoredDataAccessors.notImplemented_TTOGULP_LR(), "AGG_RIM");

	public enum PersistenceDomain {
		SPOT, CAPILLARY, FLYPOSITION
	}

	private String label;
	private String unit;
	private String title;
	private MeasurementComputation computationStrategy;
	private String persistenceKey;
	private EnumSet<PersistenceDomain> persistenceDomains;

	EnumResults(String label, String unit, String title, MeasurementComputation computationStrategy) {
		this.label = label;
		this.unit = unit;
		this.title = title;
		this.computationStrategy = computationStrategy;
		this.persistenceKey = null;
		this.persistenceDomains = EnumSet.noneOf(PersistenceDomain.class);
	}

	EnumResults(String label, String unit, String title, MeasurementComputation computationStrategy,
			String persistenceKey, PersistenceDomain... domains) {
		this.label = label;
		this.unit = unit;
		this.title = title;
		this.computationStrategy = computationStrategy;
		this.persistenceKey = Objects.requireNonNull(persistenceKey, "persistenceKey");
		this.persistenceDomains = (domains == null || domains.length == 0) ? EnumSet.noneOf(PersistenceDomain.class)
				: EnumSet.copyOf(java.util.Arrays.asList(domains));
	}

	public String toString() {
		return label;
	}

	/** Measurement name shown in selectors and charts. */
	public String toDisplayLabel() {
		switch (this) {
		case FILTER_GREY:
			return "Normalized grey signal";
		case FILTER_GREY_FLY_MASKED:
			return "Normalized grey signal (fly pixels excluded)";
		case FILTER_GREY_SMOOTHED:
			return "Normalized grey signal (median-smoothed)";
		case FILTER_GREY_CONTRAST:
			return "Background-corrected foreground grey";
		case FILTER_GREY_CONTRAST_MASKED:
			return "Background-corrected grey (fly pixels excluded)";
		case FILTER_GREY_CONTRAST_SMOOTHED:
			return "Background-corrected grey (median-smoothed)";
		case FILTER_GREY_RESIDUAL:
			return "Grey-signal residual vs. pooled median";
		case FLY_PIXEL_COUNT:
			return "Fly coverage (%)";
		case FILTER_V5_PIXEL_COUNT:
			return "Selected pixel count (filter V5)";
		case FILTER_V5_GREY:
			return "Normalized grey signal (V5, occupied bins omitted)";
		case FILTER_V5_GREY_PRE_GATE:
			return "Normalized grey signal (V5, before occupancy check)";
		case FILTER_V5_GREY_SMOOTHED:
			return "Normalized grey signal (V5, gap-filled and smoothed)";
		case COLOR_PIXEL_COUNT:
			return "Selected pixel count (color distance)";
		case COLOR_GREY:
			return "Normalized grey signal (color distance, occupied bins omitted)";
		case COLOR_GREY_PRE_GATE:
			return "Normalized grey signal (color distance, before occupancy check)";
		case COLOR_GREY_SMOOTHED:
			return "Normalized grey signal (color distance, gap-filled and smoothed)";
		case KYMO_ROW_FRACTION:
			return "Fraction of strip rows above threshold";
		case KYMO_ROW_FRACTION_CHANGE:
			return "Absolute change in row fraction per time bin";
		case KYMO_GREEN_HEIGHT:
			return "Green mask height (rows)";
		case KYMO_GREEN_HEIGHT_RATIO:
			return "Green mask height relative to peak";
		case KYMO_CROSS_RATIO:
			return "Cross-line signal relative to initial baseline";
		case KYMO_RIM_RATIO:
			return "Rim coverage relative to initial baseline";
		case AGG_FILTER_DEPLETION:
			return "Total estimated depletion · Image filter";
		case AGG_FILTER_V5_DEPLETION:
			return "Total estimated depletion · Image filter V5";
		case AGG_COLOR_DEPLETION:
			return "Total estimated depletion · Color distance";
		case AGG_FILTER_V5_PIXEL_SUM:
			return "Total selected pixels · Image filter V5";
		case AGG_COLOR_PIXEL_SUM:
			return "Total selected pixels · Color distance";
		case AGG_KYMO_HEIGHT_LOSS:
			return "Total relative green-mask height loss";
		case AGG_KYMO_CROSS_LOSS:
			return "Total relative cross-line signal loss";
		case AGG_KYMO_RIM_LOSS:
			return "Total relative rim coverage loss";
		case CAGE_GREY_MEDIAN:
			return "Cage median grey signal";
		default:
			return label;
		}
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
	 * @return The computation strategy: - Actual computation method for computed
	 *         measures - STORED_DATA marker for measures accessed from stored
	 *         fields - NOT_IMPLEMENTED marker for measures not yet implemented
	 */
	public MeasurementComputation getComputationStrategy() {
		return computationStrategy;
	}

	/**
	 * Canonical token used on-disk (CSV section headers, etc.).
	 * <p>
	 * This is intentionally distinct from {@link #toString()} which is a UI label.
	 */
	public String toPersistenceKey() {
		return persistenceKey != null ? persistenceKey : name();
	}

	public boolean isPersistedIn(PersistenceDomain domain) {
		if (domain == null) {
			return false;
		}
		return persistenceDomains != null && persistenceDomains.contains(domain);
	}

	/**
	 * Spot curve drawn vs black (grey level) or fly-presence boolean; physically
	 * non-negative. Used to keep the absolute-scale Y axis anchored at zero so the
	 * chart does not imply a baseline below zero.
	 */
	public boolean isSpotIntensityVersusBlackMeasure() {
		switch (this) {
		case FILTER_GREY:
		case FILTER_GREY_FLY_MASKED:
		case FILTER_GREY_SMOOTHED:
		case FLY_PIXEL_COUNT:
		case FILTER_V5_PIXEL_COUNT:
		case FILTER_V5_GREY:
		case FILTER_V5_GREY_PRE_GATE:
		case FILTER_V5_GREY_SMOOTHED:
		case AGG_FILTER_DEPLETION:
		case AGG_FILTER_V5_DEPLETION:
		case AGG_FILTER_V5_PIXEL_SUM:
		case COLOR_PIXEL_COUNT:
		case COLOR_GREY:
		case COLOR_GREY_PRE_GATE:
		case COLOR_GREY_SMOOTHED:
		case AGG_COLOR_DEPLETION:
		case AGG_COLOR_PIXEL_SUM:
		case CAGE_GREY_MEDIAN:
		case AGG_KYMO_HEIGHT_LOSS:
		case KYMO_ROW_FRACTION:
		case KYMO_ROW_FRACTION_CHANGE:
		case KYMO_CAGE_MEAN_FRACT:
		case KYMO_CAGE_MEAN_ABS_DELTA:
		case KYMO_GREEN_HEIGHT:
		case KYMO_GREEN_HEIGHT_RATIO:
		case KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO:
		case KYMO_CROSS_RATIO:
		case KYMO_RIM_RATIO:
		case AGG_KYMO_CROSS_LOSS:
		case AGG_KYMO_RIM_LOSS:
			return true;
		default:
			return false;
		}
	}

	public static EnumResults findByPersistenceKey(String key) {
		if (key == null) {
			return null;
		}
		if ("AGG_GREENHEIGHT_RATIO".equals(key)) {
			return AGG_KYMO_HEIGHT_LOSS;
		}
		switch (key) {
		case "AREA_COUNT_COLOR":
			return COLOR_PIXEL_COUNT;
		case "GREY_SUM_COLOR":
			return COLOR_GREY;
		case "GREY_SUM_COLOR_PREFLY":
			return COLOR_GREY_PRE_GATE;
		case "GREY_SUM_CLEAN_COLOR":
			return COLOR_GREY_SMOOTHED;
		default:
			break;
		}
		for (EnumResults v : values()) {
			if (key.equals(v.toPersistenceKey())) {
				return v;
			}
		}
		return null;
	}

	/** Per-spot kymograph strip series persisted in {@code SpotsMeasures.csv}. */
	public boolean isPersistedKymographSpotMeasure() {
		return this == KYMO_ROW_FRACTION || this == KYMO_ROW_FRACTION_CHANGE || this == KYMO_GREEN_HEIGHT
				|| this == KYMO_GREEN_HEIGHT_RATIO || this == KYMO_CROSS_RATIO || this == KYMO_RIM_RATIO;
	}

	public static boolean isKymographMeasure(EnumResults resultType) {
		if (resultType == null) {
			return false;
		}
		switch (resultType) {
		case KYMO_ROW_FRACTION:
		case KYMO_ROW_FRACTION_CHANGE:
		case KYMO_CAGE_MEAN_FRACT:
		case KYMO_CAGE_MEAN_ABS_DELTA:
		case KYMO_GREEN_HEIGHT:
		case KYMO_GREEN_HEIGHT_RATIO:
		case KYMO_CAGE_MEAN_GREEN_HEIGHT_RATIO:
		case KYMO_CROSS_RATIO:
		case KYMO_RIM_RATIO:
		case AGG_KYMO_HEIGHT_LOSS:
		case AGG_KYMO_CROSS_LOSS:
		case AGG_KYMO_RIM_LOSS:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Checks if this measure type requires computation (has a real computation
	 * algorithm).
	 * 
	 * @return true if computation is required (not stored data and not
	 *         not-implemented)
	 */
	public boolean requiresComputation() {
		if (computationStrategy == null) {
			return false;
		}
		try {
			// Try calling it - if it throws UnsupportedOperationException, it's stored data
			computationStrategy.compute(null, null, null);
			// If it returns null without exception, check if it's the not-implemented
			// marker
			return true; // Has actual computation logic
		} catch (UnsupportedOperationException e) {
			// This is a stored data accessor
			return false;
		}
	}

	/**
	 * Checks if this measure type uses stored data (direct access to measurement
	 * fields).
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
			// Stored-data accessors always throw UnsupportedOperationException
			return true;
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
		if (abbr == null) {
			return null;
		}
		for (EnumResults v : values()) {
			if (v.toString().equals(abbr)) {
				return v;
			}
		}
		switch (abbr) {
		case "KYMO_CHROMA_FRACT":
			return KYMO_ROW_FRACTION;
		case "KYMO_CHROMA_ABS_DELTA":
			return KYMO_ROW_FRACTION_CHANGE;
		case "KYMO_CHROMA_CAGE_MEAN_FRACT":
			return KYMO_CAGE_MEAN_FRACT;
		case "KYMO_CHROMA_CAGE_MEAN_ABS_DELTA":
			return KYMO_CAGE_MEAN_ABS_DELTA;
		case "KYMO_CHROMA_CAGE_MEAN":
			return KYMO_CAGE_MEAN_FRACT;
		case "KYMO_CHROMA_CAGE_MEAN_DF":
			return KYMO_CAGE_MEAN_ABS_DELTA;
		default:
			return findByPersistenceKey(abbr);
		}
	}
}
