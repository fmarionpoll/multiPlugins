package plugins.fmp.multitools.fmp_tools.toExcel.enums;

/**
 * Enumeration of export data types for Excel export operations.
 * 
 * <p>
 * This enum identifies the category of data being exported, allowing the factory
 * to select the appropriate export implementation.
 * </p>
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public enum ExportType {
	/**
	 * Spot measurements (AREA_SUM, AREA_FLYPRESENT, AREA_SUMCLEAN)
	 */
	SPOT,

	/**
	 * Capillary measurements (topraw, toplevel, toplevel_L+R, derivative, etc.)
	 */
	CAPILLARY,

	/**
	 * Gulp measurements (sumGulps, nbGulps, amplitudeGulps, tToGulp, etc.)
	 */
	GULP,

	/**
	 * Fly position measurements (XYIMAGE, XYTOPCAGE, XYTIPCAPS, ELLIPSEAXES, DISTANCE, ISALIVE, SLEEP)
	 */
	FLY_POSITION,

	/**
	 * Markov chain analysis
	 */
	MARKOV_CHAIN
}

