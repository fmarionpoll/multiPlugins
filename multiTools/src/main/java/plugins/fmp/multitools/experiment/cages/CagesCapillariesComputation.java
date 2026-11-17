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
 * all cages. Evaporation correction means nFlies=0 capillaries (skipping
 * non-finite samples at each time), then either keeps that average (AVERAGE)
 * or fits Y(t)=A(1-exp(-t/tau)) (MODEL), and subtracts the resulting Y_ref
 * from each capillary topraw.
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
	 * Computes evaporation correction for all capillaries across all cages. Means
	 * topraw of nFlies==0 capillaries (combined / L / R). At each time, non-finite
	 * samples are left out of that mean, so one invalid capillary does not drop
	 * the others. Times with no finite sample stay NaN. The mean is zeroed at t0,
	 * then optionally fit to Y(t)=A(1-exp(-t/tau)) when method is MODEL (falls
	 * back to the mean if the fit fails), then subtracted from each capillary
	 * topraw to ptsTopCorrected.
	 *
	 * @param exp    The experiment containing all capillaries
	 * @param method AVERAGE or MODEL (null treated as MODEL)
	 */
	public void computeEvaporationCorrection(Experiment exp, EvaporationCorrectionMethod method) {
		if (exp == null || exp.getCapillaries() == null)
			return;
		final EvaporationCorrectionMethod mode = EvaporationCorrectionMethod.fromOrDefault(method);

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
				if (cap.getProperties().getNFlies() == 0 && cap.getTopRaw() != null
						&& cap.getTopRaw().polylineLevel != null && cap.getTopRaw().polylineLevel.npoints > 0) {
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

		EvapCurve avgEvapCombined = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesAll), mode);
		EvapCurve avgEvapL = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesL), mode);
		EvapCurve avgEvapR = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesR), mode);

		ReferenceMeasures ref = allCapillaries.getReferenceMeasures();
		boolean stored = storeEvaporation(ref, avgEvapCombined, avgEvapL, avgEvapR);
		ref.setEvaporationComputed(stored);

		Level2D evaporationForCorrection = usable(avgEvapCombined.series);
		if (evaporationForCorrection == null)
			evaporationForCorrection = usable(avgEvapL.series);
		if (evaporationForCorrection == null)
			evaporationForCorrection = usable(avgEvapR.series);

		for (Cage cage : cages.getCageList()) {
			for (Capillary cap : cage.getCapillaries(allCapillaries)) {
				if (cap.getTopRaw() == null || cap.getTopRaw().polylineLevel == null
						|| cap.getTopRaw().polylineLevel.npoints == 0)
					continue;

				if (evaporationForCorrection != null) {
					cap.setTopCorrected(subtractEvaporation(cap.getTopRaw(), evaporationForCorrection));
				}
			}
		}
	}

	/** @deprecated use {@link #computeEvaporationCorrection(Experiment, EvaporationCorrectionMethod)} */
	public void computeEvaporationCorrection(Experiment exp) {
		computeEvaporationCorrection(exp, EvaporationCorrectionMethod.MODEL);
	}

	private boolean storeEvaporation(ReferenceMeasures ref, EvapCurve combined, EvapCurve left, EvapCurve right) {
		boolean stored = false;
		if (combined.series != null && combined.series.npoints > 0) {
			ref.setEvaporation(level2DToCapillaryMeasure(combined.series, "_ref_evaporation"));
			ref.setEvaporationFromModel(combined.fromModel);
			stored = true;
		} else {
			ref.setEvaporation(null);
			ref.setEvaporationFromModel(false);
		}
		if (left.series != null && left.series.npoints > 0) {
			ref.setEvaporationL(level2DToCapillaryMeasure(left.series, "_ref_evaporationL"));
			ref.setEvaporationLFromModel(left.fromModel);
			stored = true;
		} else {
			ref.setEvaporationL(null);
			ref.setEvaporationLFromModel(false);
		}
		if (right.series != null && right.series.npoints > 0) {
			ref.setEvaporationR(level2DToCapillaryMeasure(right.series, "_ref_evaporationR"));
			ref.setEvaporationRFromModel(right.fromModel);
			stored = true;
		} else {
			ref.setEvaporationR(null);
			ref.setEvaporationRFromModel(false);
		}
		return stored;
	}

	/** Zero at t0; optionally fit exponential; keep the averaged series if fit fails or AVERAGE. */
	private static EvapCurve fitEvaporationOrKeepAverage(Level2D avg, EvaporationCorrectionMethod method) {
		if (avg == null || avg.npoints <= 0)
			return new EvapCurve(avg, false);
		avg.offsetToStartWithZeroAmplitude();
		if (method != EvaporationCorrectionMethod.MODEL)
			return new EvapCurve(avg, false);
		Level2D fitted = EvaporationCurveFitter.fit(avg);
		return fitted != null ? new EvapCurve(fitted, true) : new EvapCurve(avg, false);
	}

	private static Level2D usable(Level2D series) {
		return series != null && series.npoints > 0 ? series : null;
	}

	private static final class EvapCurve {
		final Level2D series;
		final boolean fromModel;

		EvapCurve(Level2D series, boolean fromModel) {
			this.series = series;
			this.fromModel = fromModel;
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

	static Level2D computeAverageMeasure(List<Capillary> capillaries) {
		if (capillaries == null || capillaries.isEmpty())
			return null;

		// Find maximum dimension
		int maxPoints = 0;
		for (Capillary cap : capillaries) {
			if (cap.getTopRaw() != null && cap.getTopRaw().polylineLevel != null) {
				int npoints = cap.getTopRaw().polylineLevel.npoints;
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
			if (cap.getTopRaw() == null || cap.getTopRaw().polylineLevel == null
					|| cap.getTopRaw().polylineLevel.npoints == 0)
				continue;
			Level2D polyline = cap.getTopRaw().polylineLevel;
			if (polyline == null)
				continue;

			int npoints = Math.min(polyline.npoints, maxPoints);
			for (int i = 0; i < npoints; i++) {
				double y = polyline.ypoints[i];
				if (!Double.isFinite(y))
					continue;
				sumY[i] += y;
				count[i]++;
			}
		}

		double[] avgY = new double[maxPoints];
		double[] xpoints = new double[maxPoints];
		for (int i = 0; i < maxPoints; i++) {
			xpoints[i] = i;
			if (count[i] > 0)
				avgY[i] = sumY[i] / count[i];
			else
				avgY[i] = Double.NaN;
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
