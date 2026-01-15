package plugins.fmp.multitools.tools.results;

/**
 * Specific accessor methods for stored measurement data.
 * Each method corresponds to a specific EnumResults type and shows exactly
 * which field/method is used to access the stored data.
 */
public class StoredDataAccessors {

	/**
	 * Accesses stored TOPRAW data from measurements.ptsTop.
	 * Used via: cap.getTopLevel()
	 */
	public static MeasurementComputation accessStored_TOPRAW() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"TOPRAW uses stored data from measurements.ptsTop - access via cap.getTopLevel(), not computation");
		};
	}

	/**
	 * Accesses stored TOPLEVEL data from measurements.ptsTopCorrected or measurements.ptsTop.
	 * Used via: cap.getTopCorrected() (if available) or cap.getTopLevel()
	 */
	public static MeasurementComputation accessStored_TOPLEVEL() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"TOPLEVEL uses stored data from measurements.ptsTopCorrected/ptsTop - access via cap.getTopCorrected() or cap.getTopLevel(), not computation");
		};
	}

	/**
	 * Accesses stored BOTTOMLEVEL data from measurements.ptsBottom.
	 * Used via: cap.getBottomLevel()
	 */
	public static MeasurementComputation accessStored_BOTTOMLEVEL() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"BOTTOMLEVEL uses stored data from measurements.ptsBottom - access via cap.getBottomLevel(), not computation");
		};
	}

	/**
	 * Accesses stored DERIVEDVALUES data from measurements.ptsDerivative.
	 * Used via: cap.getDerivative()
	 */
	public static MeasurementComputation accessStored_DERIVEDVALUES() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"DERIVEDVALUES uses stored data from measurements.ptsDerivative - access via cap.getDerivative(), not computation");
		};
	}

	/**
	 * Accesses stored TOPLEVEL_LR data from CageCapillariesComputation (SUM/PI).
	 * Used via: exp.getCages().getCageComputation(cageID).getSumMeasure() or getPIMeasure()
	 */
	public static MeasurementComputation accessStored_TOPLEVEL_LR() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"TOPLEVEL_LR uses stored data from CageCapillariesComputation - access via cage computation, not computation");
		};
	}

	/**
	 * Accesses stored TOPLEVELDELTA data from measurements.ptsTop (delta computation).
	 * Used via: cap.getTopLevel() with delta calculation
	 */
	public static MeasurementComputation accessStored_TOPLEVELDELTA() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"TOPLEVELDELTA uses stored data from measurements.ptsTop with delta calculation - access via cap.getTopLevel(), not computation");
		};
	}

	/**
	 * Accesses stored TOPLEVELDELTA_LR data from CageCapillariesComputation.
	 * Used via: cage computation with delta calculation
	 */
	public static MeasurementComputation accessStored_TOPLEVELDELTA_LR() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"TOPLEVELDELTA_LR uses stored data from CageCapillariesComputation with delta calculation - access via cage computation, not computation");
		};
	}

	/**
	 * Accesses stored SUMGULPS data from measurements.ptsGulps.getMeasuresFromGulps().
	 * Used via: cap.getGulps().getMeasuresFromGulps(SUMGULPS, ...)
	 */
	public static MeasurementComputation accessStored_SUMGULPS() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"SUMGULPS uses stored data from measurements.ptsGulps - access via cap.getGulps().getMeasuresFromGulps(), not computation");
		};
	}

	/**
	 * Accesses stored SUMGULPS_LR data from CageCapillariesComputation or measurements.ptsGulps.
	 * Used via: cage computation or cap.getGulps().getMeasuresFromGulps()
	 */
	public static MeasurementComputation accessStored_SUMGULPS_LR() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"SUMGULPS_LR uses stored data from cage computation or measurements.ptsGulps - access via cage computation or cap.getGulps(), not computation");
		};
	}

	/**
	 * Accesses stored XYIMAGE data from FlyPositions.
	 * Used via: cage.flyPositions with coordinate extraction
	 */
	public static MeasurementComputation accessStored_XYIMAGE() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"XYIMAGE uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored XYTOPCAGE data from FlyPositions.
	 * Used via: cage.flyPositions with coordinate extraction
	 */
	public static MeasurementComputation accessStored_XYTOPCAGE() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"XYTOPCAGE uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored XYTIPCAPS data from FlyPositions.
	 * Used via: cage.flyPositions with coordinate extraction
	 */
	public static MeasurementComputation accessStored_XYTIPCAPS() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"XYTIPCAPS uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored ELLIPSEAXES data from FlyPositions.
	 * Used via: cage.flyPositions with ellipse extraction
	 */
	public static MeasurementComputation accessStored_ELLIPSEAXES() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"ELLIPSEAXES uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored DISTANCE data from FlyPositions (computed from consecutive points).
	 * Used via: cage.flyPositions with distance calculation
	 */
	public static MeasurementComputation accessStored_DISTANCE() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"DISTANCE uses stored data from FlyPositions with distance calculation - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored ISALIVE data from FlyPositions.
	 * Used via: cage.flyPositions with alive status extraction
	 */
	public static MeasurementComputation accessStored_ISALIVE() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"ISALIVE uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored SLEEP data from FlyPositions.
	 * Used via: cage.flyPositions with sleep status extraction
	 */
	public static MeasurementComputation accessStored_SLEEP() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"SLEEP uses stored data from FlyPositions - access via cage.flyPositions, not computation");
		};
	}

	/**
	 * Accesses stored AREA_SUM data from Spot measurements.
	 * Used via: spot.getMeasurements(AREA_SUM)
	 */
	public static MeasurementComputation accessStored_AREA_SUM() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"AREA_SUM uses stored data from Spot measurements - access via spot.getMeasurements(), not computation");
		};
	}

	/**
	 * Accesses stored AREA_SUMCLEAN data from Spot measurements.
	 * Used via: spot.getMeasurements(AREA_SUMCLEAN)
	 */
	public static MeasurementComputation accessStored_AREA_SUMCLEAN() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"AREA_SUMCLEAN uses stored data from Spot measurements - access via spot.getMeasurements(), not computation");
		};
	}

	/**
	 * Accesses stored AREA_OUT data from Spot measurements.
	 * Used via: spot.getMeasurements(AREA_OUT)
	 */
	public static MeasurementComputation accessStored_AREA_OUT() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"AREA_OUT uses stored data from Spot measurements - access via spot.getMeasurements(), not computation");
		};
	}

	/**
	 * Accesses stored AREA_DIFF data from Spot measurements.
	 * Used via: spot.getMeasurements(AREA_DIFF)
	 */
	public static MeasurementComputation accessStored_AREA_DIFF() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"AREA_DIFF uses stored data from Spot measurements - access via spot.getMeasurements(), not computation");
		};
	}

	/**
	 * Accesses stored AREA_FLYPRESENT data from Spot measurements.
	 * Used via: spot.getMeasurements(AREA_FLYPRESENT)
	 */
	public static MeasurementComputation accessStored_AREA_FLYPRESENT() {
		return (exp, cap, options) -> {
			throw new UnsupportedOperationException(
					"AREA_FLYPRESENT uses stored data from Spot measurements - access via spot.getMeasurements(), not computation");
		};
	}

	/**
	 * Placeholder for measures not yet implemented.
	 */
	public static MeasurementComputation notImplemented_TTOGULP_LR() {
		return (exp, cap, options) -> {
			// Returns null to indicate not implemented
			return null;
		};
	}
}

