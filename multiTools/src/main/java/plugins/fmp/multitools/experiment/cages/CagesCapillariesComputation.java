package plugins.fmp.multitools.experiment.cages;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.ReferenceMeasures;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Handles experiment-wide capillary measure computations that require access to
 * all cages. This includes evaporation correction which needs to find all
 * capillaries with nFlies=0 across all cages to compute the average
 * evaporation.
 * 
 * @author MultiSPOTS96
 * @version 2.3.3
 */
public class CagesCapillariesComputation {

	private final Cages cages;

	public CagesCapillariesComputation(Cages cages) {
		if (cages == null) {
			throw new IllegalArgumentException("Cages cannot be null");
		}
		this.cages = cages;
	}

	/**
	 * Computes evaporation correction for all capillaries across all cages. For
	 * capillaries with capNFlies == 0, computes average evaporation separately for
	 * L and R sides. Subtracts the average evaporation from ptsTop to create
	 * ptsTopCorrected.
	 * 
	 * @param exp The experiment containing all capillaries
	 */
	public void computeEvaporationCorrection(Experiment exp) {
		if (exp == null || exp.getCapillaries() == null)
			return;

		// First, dispatch capillaries to cages to ensure they're organized
		exp.dispatchCapillariesToCages();

		Capillaries allCapillaries = exp.getCapillaries();
		if (allCapillaries == null)
			return;

		// Collect all capillaries with zero flies for evaporation calculation
		List<Capillary> zeroFliesCapillariesAll = new ArrayList<>();
		List<Capillary> zeroFliesCapillariesL = new ArrayList<>();
		List<Capillary> zeroFliesCapillariesR = new ArrayList<>();

		for (Cage cage : cages.getCageList()) {
			for (Capillary cap : cage.getCapillaries(allCapillaries)) {
				if (cap.getProperties().getNFlies() == 0 && cap.getTopLevel() != null
						&& cap.getTopLevel().polylineLevel != null && cap.getTopLevel().polylineLevel.npoints > 0) {
					zeroFliesCapillariesAll.add(cap);
					String side = getCapillarySide(cap);
					if (side.contains("L") || side.contains("1")) {
						zeroFliesCapillariesL.add(cap);
					} else if (side.contains("R") || side.contains("2")) {
						zeroFliesCapillariesR.add(cap);
					} else {
						zeroFliesCapillariesL.add(cap);
						zeroFliesCapillariesR.add(cap);
					}
				}
			}
		}

		Level2D avgEvapCombined = computeAverageMeasure(zeroFliesCapillariesAll);
		Level2D avgEvapL = computeAverageMeasure(zeroFliesCapillariesL);
		Level2D avgEvapR = computeAverageMeasure(zeroFliesCapillariesR);
		if (avgEvapCombined != null && avgEvapCombined.npoints > 0)
			avgEvapCombined.offsetToStartWithZeroAmplitude();
		if (avgEvapL != null && avgEvapL.npoints > 0)
			avgEvapL.offsetToStartWithZeroAmplitude();
		if (avgEvapR != null && avgEvapR.npoints > 0)
			avgEvapR.offsetToStartWithZeroAmplitude();

		ReferenceMeasures ref = allCapillaries.getReferenceMeasures();
		if (avgEvapCombined != null && avgEvapCombined.npoints > 0)
			ref.setEvaporation(level2DToCapillaryMeasure(avgEvapCombined, "_ref_evaporation"));
		if (avgEvapL != null && avgEvapL.npoints > 0)
			ref.setEvaporationL(level2DToCapillaryMeasure(avgEvapL, "_ref_evaporationL"));
		if (avgEvapR != null && avgEvapR.npoints > 0)
			ref.setEvaporationR(level2DToCapillaryMeasure(avgEvapR, "_ref_evaporationR"));

		Level2D evaporationForCorrection = avgEvapCombined;
		if (evaporationForCorrection == null && avgEvapL != null)
			evaporationForCorrection = avgEvapL;
		if (evaporationForCorrection == null && avgEvapR != null)
			evaporationForCorrection = avgEvapR;

		for (Cage cage : cages.getCageList()) {
			for (Capillary cap : cage.getCapillaries(allCapillaries)) {
				if (cap.getTopLevel() == null || cap.getTopLevel().polylineLevel == null
						|| cap.getTopLevel().polylineLevel.npoints == 0)
					continue;

				if (evaporationForCorrection != null) {
					cap.setTopCorrected(subtractEvaporation(cap.getTopLevel(), evaporationForCorrection));
				}
			}
		}
	}

	/**
	 * Clears all computed measures from capillaries in all cages.
	 * 
	 * @param exp The experiment containing all capillaries
	 */
	public void clearComputedMeasures(Experiment exp) {
		if (exp == null || exp.getCapillaries() == null)
			return;

		Capillaries allCapillaries = exp.getCapillaries();
		for (Cage cage : cages.getCageList()) {
			for (Capillary cap : cage.getCapillaries(allCapillaries)) {
				cap.clearComputedMeasures();
			}
		}
	}

	// --------------------------------------------------------
	// Helper methods for capillary computation

	private String getCapillarySide(Capillary cap) {
		if (cap.getProperties().getSide() != null && !cap.getProperties().getSide().equals("."))
			return cap.getProperties().getSide();
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

	private Level2D computeAverageMeasure(List<Capillary> capillaries) {
		if (capillaries == null || capillaries.isEmpty())
			return null;

		// Find maximum dimension
		int maxPoints = 0;
		for (Capillary cap : capillaries) {
			if (cap.getTopLevel() != null && cap.getTopLevel().polylineLevel != null) {
				int npoints = cap.getTopLevel().polylineLevel.npoints;
				if (npoints > maxPoints)
					maxPoints = npoints;
			}
		}

		if (maxPoints == 0)
			return null;

		// Accumulate values
		double[] sumY = new double[maxPoints];
		int[] count = new int[maxPoints];
		for (int i = 0; i < maxPoints; i++) {
			sumY[i] = 0.0;
			count[i] = 0;
		}

		for (Capillary cap : capillaries) {
			if (cap.getTopLevel() == null || cap.getTopLevel().polylineLevel == null
					|| cap.getTopLevel().polylineLevel.npoints == 0)
				continue;
			Level2D polyline = cap.getTopLevel().polylineLevel;
			if (polyline == null)
				continue;

			int npoints = Math.min(polyline.npoints, maxPoints);
			for (int i = 0; i < npoints; i++) {
				sumY[i] += polyline.ypoints[i];
				count[i]++;
			}
		}

		// Average
		double[] avgY = new double[maxPoints];
		double[] xpoints = new double[maxPoints];
		for (int i = 0; i < maxPoints; i++) {
			xpoints[i] = i;
			if (count[i] > 0)
				avgY[i] = sumY[i] / count[i];
			else
				avgY[i] = 0.0;
		}

		return new Level2D(xpoints, avgY, maxPoints);
	}

	private CapillaryMeasure level2DToCapillaryMeasure(Level2D level, String name) {
		if (level == null || level.npoints == 0)
			return null;
		CapillaryMeasure m = new CapillaryMeasure(name);
		m.polylineLevel = level.clone();
		return m;
	}

	private CapillaryMeasure subtractEvaporation(CapillaryMeasure original, Level2D evaporation) {
		if (original == null || original.polylineLevel == null || original.polylineLevel.npoints == 0
				|| evaporation == null)
			return null;

		Level2D polyline = original.polylineLevel;
		if (polyline == null)
			return null;

		int npoints = Math.min(polyline.npoints, evaporation.npoints);
		double[] correctedY = new double[npoints];
		double[] xpoints = new double[npoints];

		for (int i = 0; i < npoints; i++) {
			xpoints[i] = i;
			correctedY[i] = polyline.ypoints[i] - evaporation.ypoints[i];
		}

		Level2D correctedPolyline = new Level2D(xpoints, correctedY, npoints);

		CapillaryMeasure corrected = new CapillaryMeasure(original.capName + "_corrected", -1,
				new ArrayList<Point2D>());
		corrected.polylineLevel = correctedPolyline;

		return corrected;
	}

}
