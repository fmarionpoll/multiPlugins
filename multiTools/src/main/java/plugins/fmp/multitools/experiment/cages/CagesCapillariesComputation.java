package plugins.fmp.multitools.experiment.cages;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.ReferenceMeasures;
import plugins.fmp.multitools.experiment.capillary.BottomBaselineEstimator;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.polyline.Level2D;

/**
 * Handles experiment-wide capillary measure computations that require access to
 * all cages. Evaporation correction means nFlies=0 capillaries, then either
 * keeps that average (AVERAGE) or fits Y(t)=A(1-exp(-t/tau)) (MODEL), and
 * subtracts the resulting Y_ref from each capillary topraw.
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
	 * topraw of nFlies==0 capillaries (combined / L / R), zeros at t0, optionally
	 * fits Y(t)=A(1-exp(-t/tau)) when method is MODEL (falls back to the mean if
	 * the fit fails), then subtracts that curve from each capillary topraw to
	 * ptsTopCorrected.
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

		Level2D avgEvapCombined = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesAll), mode);
		Level2D avgEvapL = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesL), mode);
		Level2D avgEvapR = fitEvaporationOrKeepAverage(computeAverageMeasure(zeroFliesCapillariesR), mode);

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

	/** Zero at t0; optionally fit exponential; keep the averaged series if fit fails or AVERAGE. */
	private static Level2D fitEvaporationOrKeepAverage(Level2D avg, EvaporationCorrectionMethod method) {
		if (avg == null || avg.npoints <= 0)
			return avg;
		avg.offsetToStartWithZeroAmplitude();
		if (method != EvaporationCorrectionMethod.MODEL)
			return avg;
		Level2D fitted = EvaporationCurveFitter.fit(avg);
		return fitted != null ? fitted : avg;
	}

	/** Max px below the longest empty fill still accepted as a t00 reference. */
	public static final double T00_FILL_TOLERANCE_PX = 6.0;

	/**
	 * t00 fill reference from empty capillaries (native pixels).
	 * <ol>
	 * <li>Empties ({@code nFlies==0}) with tip (bottomlevel) and top[t0]</li>
	 * <li>Fill length {@code L = tip − top[t0]}; keep those within
	 * {@link #T00_FILL_TOLERANCE_PX} of the longest</li>
	 * <li>Need ≥2 suitable; else mark experiment unsuitable and clear capillary
	 * t00 Y</li>
	 * <li>Else {@code h = median(L)}; each capillary {@code Y_t00 = tip − h}</li>
	 * </ol>
	 */
	public void computeT00References(Experiment exp) {
		if (exp == null || exp.getCapillaries() == null)
			return;
		exp.dispatchCapillariesToCages();
		Capillaries allCapillaries = exp.getCapillaries();

		List<Capillary> allCaps = new ArrayList<>();
		for (Cage cage : cages.getCageList()) {
			if (cage == null)
				continue;
			List<Capillary> caps = cage.getCapillaries(allCapillaries);
			if (caps == null)
				continue;
			allCaps.addAll(caps);
		}

		ensureBottomBaselines(allCaps);

		List<Double> emptyLengths = new ArrayList<>();
		List<Capillary> emptyCapsWithLength = new ArrayList<>();
		for (Capillary cap : allCaps) {
			if (cap == null || cap.getProperties().getNFlies() != 0)
				continue;
			Double length = emptyFillLengthPx(cap);
			if (length == null)
				continue;
			emptyLengths.add(length);
			emptyCapsWithLength.add(cap);
		}

		if (emptyLengths.isEmpty()) {
			clearT00(exp, allCaps);
			Logger.warn("t00: no usable empty capillary (nFlies==0 with tip + top[t0])");
			return;
		}

		double lMax = Double.NEGATIVE_INFINITY;
		for (double l : emptyLengths) {
			if (l > lMax)
				lMax = l;
		}

		List<Double> suitableLengths = new ArrayList<>();
		List<Capillary> suitableCaps = new ArrayList<>();
		for (int i = 0; i < emptyLengths.size(); i++) {
			double l = emptyLengths.get(i);
			if (lMax - l <= T00_FILL_TOLERANCE_PX) {
				suitableLengths.add(l);
				suitableCaps.add(emptyCapsWithLength.get(i));
			}
		}

		int nSuitable = suitableLengths.size();
		exp.setT00NSuitable(nSuitable);
		if (nSuitable < 2) {
			exp.setT00ReferencePixels(Double.NaN);
			exp.setT00UlPerPx(Double.NaN);
			for (Capillary cap : allCaps) {
				if (cap != null)
					cap.setT00YPixels(Double.NaN);
			}
			return;
		}

		double h = median(suitableLengths);
		double ulPerPx = medianUlPerPx(suitableCaps);
		if (!Double.isFinite(ulPerPx))
			ulPerPx = medianUlPerPx(allCaps);
		exp.setT00ReferencePixels(h);
		exp.setT00UlPerPx(ulPerPx);

		int nSet = 0;
		for (Capillary cap : allCaps) {
			if (cap == null)
				continue;
			double tip = cap.getBottomBaselineY();
			if (!Double.isFinite(tip)) {
				cap.setT00YPixels(Double.NaN);
				continue;
			}
			cap.setT00YPixels(tip - h);
			nSet++;
		}
		Logger.info("t00: h=" + String.format("%.2f", h) + " px (median of " + nSuitable
				+ " suitable empties), set Y_t00 on " + nSet + " capillary(ies)"
				+ (Double.isFinite(ulPerPx) ? ", ref=" + String.format("%.4f", h * ulPerPx) + " uL" : ""));
	}

	private static void clearT00(Experiment exp, List<Capillary> allCaps) {
		exp.clearT00Reference();
		for (Capillary cap : allCaps) {
			if (cap != null)
				cap.setT00YPixels(Double.NaN);
		}
	}

	private static void ensureBottomBaselines(List<Capillary> caps) {
		for (Capillary cap : caps) {
			if (cap == null)
				continue;
			if (Double.isFinite(cap.getBottomBaselineY()))
				continue;
			CapillaryMeasure bottom = cap.getBottomRaw();
			if (bottom != null && bottom.isThereAnyMeasuresDone())
				BottomBaselineEstimator.estimateAndApply(cap);
		}
	}

	/** {@code tip − top[t0]} in native pixels, or null if unavailable. */
	public static Double emptyFillLengthPx(Capillary cap) {
		if (cap == null)
			return null;
		double tip = cap.getBottomBaselineY();
		if (!Double.isFinite(tip))
			return null;
		CapillaryMeasure top = cap.getTopRaw();
		if (top == null || top.polylineLevel == null || top.polylineLevel.npoints <= 0)
			return null;
		Level2D poly = top.polylineLevel;
		if (poly.ypoints == null || poly.ypoints.length == 0 || !Double.isFinite(poly.ypoints[0]))
			return null;
		return tip - poly.ypoints[0];
	}

	static double median(List<Double> values) {
		if (values == null || values.isEmpty())
			return Double.NaN;
		Double[] arr = values.toArray(new Double[0]);
		java.util.Arrays.sort(arr);
		int n = arr.length;
		if ((n & 1) == 1)
			return arr[n / 2];
		return 0.5 * (arr[n / 2 - 1] + arr[n / 2]);
	}

	static double medianUlPerPx(List<Capillary> caps) {
		if (caps == null || caps.isEmpty())
			return Double.NaN;
		List<Double> scales = new ArrayList<>();
		for (Capillary cap : caps) {
			if (cap == null)
				continue;
			double vol = cap.getVolume();
			int px = cap.getPixels();
			if (px > 0 && Double.isFinite(vol) && vol > 0)
				scales.add(vol / px);
		}
		return median(scales);
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
