package plugins.fmp.multitools.fmp_experiment.cages;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.fmp_experiment.capillaries.Capillaries;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_experiment.capillaries.CapillaryMeasure;
import plugins.fmp.multitools.fmp_tools.Level2D;

/**
 * Handles cage-level capillary measure computations for a specific Cage
 * instance. Handles computations such as L+R aggregation (SUM, PI) that operate
 * on capillaries within a single cage.
 * 
 * Supports different numbers of capillaries per cage (2, 4, 8, etc.): - For 2
 * capillaries: standard L+R with SUM and PI - For 4+ capillaries: aggregates by
 * grouping (e.g., L1+L2, R1+R2, then combined)
 * 
 * This class works with Cage instances to provide computation functionality
 * while storing computed results in transient fields.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class CageCapillariesComputation {

	private final Cage cage;

	// Volatile computed measures stored at cage level
	// Key format: "SUM", "PI", "SUM_L", "SUM_R", etc.
	// private transient Map<String, CapillaryMeasure> computedMeasures = new
	// HashMap<>(); // Deprecated

	/**
	 * Creates a new CageCapillariesComputation for the given cage.
	 * 
	 * @param cage The cage for which to perform computations
	 */
	public CageCapillariesComputation(Cage cage) {
		if (cage == null) {
			throw new IllegalArgumentException("Cage cannot be null");
		}
		this.cage = cage;
	}

	/**
	 * Gets the associated cage.
	 * 
	 * @return The cage instance
	 */
	public Cage getCage() {
		return cage;
	}

	/**
	 * Computes L+R measures (SUM and PI) for capillaries within the associated
	 * cage. Handles different numbers of capillaries: - 2 capillaries: standard L+R
	 * (SUM = |L|+|R|, PI = (L-R)/SUM) - 4+ capillaries: groups into Left and Right
	 * sets, then computes aggregated SUM and PI
	 * 
	 * Note: This should be called AFTER evaporation correction if needed.
	 * 
	 * @param allCapillaries The global Capillaries containing all capillaries
	 * @param threshold      Minimum SUM value required to compute PI
	 */
	public void computeLRMeasures(Capillaries allCapillaries, double threshold) {
		if (allCapillaries == null)
			return;

		List<Capillary> caps = cage.getCapillaries(allCapillaries);
		if (caps.size() < 2)
			return;

		// Group capillaries by side (L or R)
		List<Capillary> leftCaps = new ArrayList<>();
		List<Capillary> rightCaps = new ArrayList<>();

		for (Capillary cap : caps) {
			String side = getCapillarySide(cap);
			if (side.contains("L") || side.contains("1")) {
				leftCaps.add(cap);
			} else if (side.contains("R") || side.contains("2")) {
				rightCaps.add(cap);
			}
		}

		// If no clear L/R grouping, use first half as left, second half as right
		if (leftCaps.isEmpty() && rightCaps.isEmpty() && caps.size() >= 2) {
			int mid = caps.size() / 2;
			leftCaps = caps.subList(0, mid);
			rightCaps = caps.subList(mid, caps.size());
		}

		if (leftCaps.isEmpty() || rightCaps.isEmpty())
			return;

		// Compute aggregated measures for left and right groups
		CapillaryMeasure aggregatedLeft = aggregateMeasures(leftCaps);
		CapillaryMeasure aggregatedRight = aggregateMeasures(rightCaps);

		if (aggregatedLeft == null || aggregatedRight == null || aggregatedLeft.polylineLevel == null
				|| aggregatedRight.polylineLevel == null || aggregatedLeft.polylineLevel.npoints == 0
				|| aggregatedRight.polylineLevel.npoints == 0)
			return;

		// Compute SUM and PI from aggregated left and right
		computeSumAndPI(aggregatedLeft, aggregatedRight, threshold);
	}

	/**
	 * Clears all computed measures for the associated cage.
	 * 
	 * @param allCapillaries The global Capillaries containing all capillaries
	 */
	public void clearComputedMeasures(Capillaries allCapillaries) {
		if (cage.measures != null) {
			cage.measures.clear();
		}
		// Also clear individual capillary computed measures
		if (allCapillaries != null) {
			for (Capillary cap : cage.getCapillaries(allCapillaries)) {
				cap.clearComputedMeasures();
			}
		}
	}

	/**
	 * Gets a computed measure by key.
	 * 
	 * @param key The measure key ("SUM", "PI", etc.)
	 * @return The computed measure, or null if not found
	 */
	public CapillaryMeasure getComputedMeasure(String key) {
		// return computedMeasures != null ? computedMeasures.get(key) : null;
		if (key.equals("SUM"))
			return cage.measures.sum;
		if (key.equals("PI"))
			return cage.measures.pi;
		return null;
	}

	// --------------------------------------------------------
	// Helper methods

	/**
	 * Aggregates measures from multiple capillaries by summing them. Uses
	 * evaporation-corrected measures if available, otherwise raw measures.
	 */
	private CapillaryMeasure aggregateMeasures(List<Capillary> capillaries) {
		if (capillaries == null || capillaries.isEmpty())
			return null;

		// Find maximum dimension
		int maxPoints = 0;
		for (Capillary cap : capillaries) {
			CapillaryMeasure measure = (cap.getTopCorrected() != null) ? cap.getTopCorrected() : cap.getTopLevel();
			if (measure != null && measure.polylineLevel != null) {
				int npoints = measure.polylineLevel.npoints;
				if (npoints > maxPoints)
					maxPoints = npoints;
			}
		}

		if (maxPoints == 0)
			return null;

		// Sum values from all capillaries
		double[] sumY = new double[maxPoints];
		for (int i = 0; i < maxPoints; i++) {
			sumY[i] = 0.0;
		}

		for (Capillary cap : capillaries) {
			CapillaryMeasure measure = (cap.getTopCorrected() != null) ? cap.getTopCorrected() : cap.getTopLevel();
			if (measure == null || measure.polylineLevel == null || measure.polylineLevel.npoints == 0)
				continue;

			int npoints = Math.min(measure.polylineLevel.npoints, maxPoints);
			for (int i = 0; i < npoints; i++) {
				sumY[i] += Math.abs(measure.polylineLevel.ypoints[i]);
			}
		}

		// Create aggregated measure
		double[] xpoints = new double[maxPoints];
		for (int i = 0; i < maxPoints; i++) {
			xpoints[i] = i;
		}

		Level2D aggregatedPolyline = new Level2D(xpoints, sumY, maxPoints);

		CapillaryMeasure aggregated = new CapillaryMeasure("aggregated", -1, new ArrayList<Point2D>());
		aggregated.polylineLevel = aggregatedPolyline;

		return aggregated;
	}

	/**
	 * Computes SUM and PI from left and right aggregated measures. SUM = |L| + |R|
	 * PI = (L-R)/SUM (if SUM >= threshold)
	 */
	private void computeSumAndPI(CapillaryMeasure aggregatedLeft, CapillaryMeasure aggregatedRight, double threshold) {

		Level2D polylineL = aggregatedLeft.polylineLevel;
		Level2D polylineR = aggregatedRight.polylineLevel;
		if (polylineL == null || polylineR == null)
			return;

		int npoints = Math.min(polylineL.npoints, polylineR.npoints);
		double[] sumY = new double[npoints];
		double[] piY = new double[npoints];
		double[] xpoints = new double[npoints];

		for (int i = 0; i < npoints; i++) {
			xpoints[i] = i;
			double valL = polylineL.ypoints[i];
			double valR = polylineR.ypoints[i];
			double sum = Math.abs(valL) + Math.abs(valR);

			sumY[i] = sum;

			if (sum > 0.0 && sum >= threshold) {
				piY[i] = (valL - valR) / sum;
			} else {
				piY[i] = 0.0;
			}
//			if (Math.abs(piY[i]) > 1.)
//				System.out.println("unbalanced");
		}

		// Store SUM and PI in computed measures map
		Level2D sumPolyline = new Level2D(xpoints, sumY, npoints);
		Level2D piPolyline = new Level2D(xpoints, piY, npoints);

		String cageName = cage.prop.getStrCageNumber();
		CapillaryMeasure sumMeasure = new CapillaryMeasure(cageName + "_SUM", -1, new ArrayList<Point2D>());
		sumMeasure.polylineLevel = sumPolyline;
		cage.measures.sum = sumMeasure;
		// computedMeasures.put("SUM", sumMeasure);

		CapillaryMeasure piMeasure = new CapillaryMeasure(cageName + "_PI", -1, new ArrayList<Point2D>());
		piMeasure.polylineLevel = piPolyline;
		cage.measures.pi = piMeasure;
		// computedMeasures.put("PI", piMeasure);
	}

	private String getCapillarySide(Capillary cap) {
		if (cap.getSide() != null && !cap.getSide().equals("."))
			return cap.getSide();
		// Try to get from name
		String name = cap.getRoiName();
		if (name != null) {
			name = name.toUpperCase();
			if (name.contains("L") || name.contains("1"))
				return "L";
			if (name.contains("R") || name.contains("2"))
				return "R";
		}
		return "";
	}

	/**
	 * Gets the SUM measure for the associated cage (L+R aggregation).
	 * 
	 * @return The SUM measure, or null if not computed
	 */
	public CapillaryMeasure getSumMeasure() {
		return cage.measures.sum;
	}

	/**
	 * Gets the PI measure for the associated cage ((L-R)/(L+R) preference index).
	 * 
	 * @return The PI measure, or null if not computed
	 */
	public CapillaryMeasure getPIMeasure() {
		return cage.measures.pi;
	}
}
