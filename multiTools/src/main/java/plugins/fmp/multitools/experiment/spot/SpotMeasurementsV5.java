package plugins.fmp.multitools.experiment.spot;

import java.util.ArrayList;
import java.util.List;

import icy.roi.ROI2D;

/**
 * Spot measures for the parallel V5 pipeline ({@code AREA_COUNT_V5}, {@code GREY_SUM_V5},
 * {@code GREY_SUM_CLEAN_V5}).
 * Kept separate from legacy {@link Spot} inner measurements so legacy CSV and behavior stay isolated.
 * {@code GREY_SUM_V5} is stored on the same scale as legacy {@code AREA_SUM}: sum on over-threshold pixels
 * divided by total ROI mask pixel count.
 * {@code GREY_SUM_CLEAN_V5} is built from {@code GREY_SUM_V5} by (1) linear interpolation across NaN runs
 * (including leading/trailing plateaus from the first/last finite sample), then (2) the same span-10 NaN-robust
 * running median as legacy {@code sumClean} from {@code sumNoFly} in {@link plugins.fmp.multitools.experiment.spots.Spots}.
 */
public final class SpotMeasurementsV5 {

	/** Same half-window as legacy {@code rebuildSumCleanFromSumNoFlyForSpot} (span 10). */
	private static final int GREY_SUM_CLEAN_MEDIAN_SPAN = 10;

	private final SpotMeasure areaCount;
	private final SpotMeasure greySum;
	private final SpotMeasure greySumClean;

	public SpotMeasurementsV5() {
		this.areaCount = new SpotMeasure("areaCountV5");
		this.greySum = new SpotMeasure("greySumV5");
		this.greySumClean = new SpotMeasure("greySumCleanV5");
	}

	public SpotMeasurementsV5(SpotMeasurementsV5 source, boolean includeData) {
		this.areaCount = new SpotMeasure("areaCountV5");
		this.greySum = new SpotMeasure("greySumV5");
		this.greySumClean = new SpotMeasure("greySumCleanV5");
		if (includeData && source != null) {
			copyFrom(source);
		}
	}

	public void copyFrom(SpotMeasurementsV5 source) {
		if (source == null) {
			return;
		}
		areaCount.copyMeasures(source.areaCount);
		greySum.copyMeasures(source.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void addFrom(SpotMeasurementsV5 source) {
		if (source == null) {
			return;
		}
		areaCount.addMeasures(source.areaCount);
		greySum.addMeasures(source.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void computePI(SpotMeasurementsV5 m1, int n1, SpotMeasurementsV5 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computePI(m1.areaCount, m2.areaCount);
		greySum.computePI(m1.greySum, m2.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void computeSUM(SpotMeasurementsV5 m1, int n1, SpotMeasurementsV5 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computeSUM(m1.areaCount, n1, m2.areaCount, n2);
		greySum.computeSUM(m1.greySum, n1, m2.greySum, n2);
		rebuildGreySumCleanFromGreySum();
	}

	public void normalizeMeasures() {
		areaCount.normalizeValues();
		greySum.normalizeValues();
		rebuildGreySumCleanFromGreySum();
	}

	/**
	 * Recomputes {@link #getGreySumClean()} from {@link #getGreySum()}: bridge NaN gaps with linear segments
	 * (and edge plateaus), then apply the legacy-style running median.
	 */
	public void rebuildGreySumCleanFromGreySum() {
		double[] in = greySum.getValues();
		if (in == null || in.length == 0) {
			return;
		}
		double[] work = java.util.Arrays.copyOf(in, in.length);
		interpolateFiniteNaNGapsLinear(work);
		double[] out = runningMedianIgnoringNaN(work, GREY_SUM_CLEAN_MEDIAN_SPAN);
		fillNaNPlateausFromFirstLastFinite(out);
		greySumClean.setValues(out);
	}

	public SpotMeasure getAreaCount() {
		return areaCount;
	}

	public SpotMeasure getGreySum() {
		return greySum;
	}

	public SpotMeasure getGreySumClean() {
		return greySumClean;
	}

	public void restoreClippedMeasures() {
		restoreClippedMeasure(areaCount);
		restoreClippedMeasure(greySum);
		restoreClippedMeasure(greySumClean);
	}

	private static void restoreClippedMeasure(SpotMeasure measure) {
		if (measure != null) {
			measure.getSpotLevel2D().restoreCroppedLevel2D();
		}
	}

	public void transferMeasuresToLevel2D() {
		if (areaCount != null) {
			areaCount.transferValuesToLevel2D();
		}
		if (greySum != null) {
			greySum.transferValuesToLevel2D();
		}
		if (greySumClean != null) {
			greySumClean.transferValuesToLevel2D();
		}
	}

	public void transferRoiMeasuresToLevel2D() {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().transferROItoLevel2D();
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().transferROItoLevel2D();
		}
		if (greySumClean != null) {
			greySumClean.getSpotLevel2D().transferROItoLevel2D();
		}
	}

	public void adjustLevel2DMeasuresToImageWidth(int imageWidth) {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
		}
		if (greySumClean != null) {
			greySumClean.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
		}
	}

	public void cropLevel2DMeasuresToImageWidth(int imageWidth) {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
		}
		if (greySumClean != null) {
			greySumClean.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
		}
	}

	public void initializeLevel2DMeasures() {
		if (areaCount != null) {
			areaCount.getSpotLevel2D().clearLevel2D();
		}
		if (greySum != null) {
			greySum.getSpotLevel2D().clearLevel2D();
		}
		if (greySumClean != null) {
			greySumClean.getSpotLevel2D().clearLevel2D();
		}
	}

	public List<ROI2D> transferLevel2DToRois(int imageHeight) {
		List<ROI2D> rois = new ArrayList<>();
		if (areaCount != null) {
			ROI2D roi = areaCount.getSpotLevel2D().getROIForImage("areaCountV5", 0, imageHeight);
			if (roi != null) {
				rois.add(roi);
			}
		}
		return rois;
	}

	public void transferRoiToMeasures(ROI2D roi, int imageHeight) {
		if (roi != null && areaCount != null) {
			areaCount.getSpotLevel2D().transferROItoLevel2D();
		}
		if (roi != null && greySum != null) {
			greySum.getSpotLevel2D().transferROItoLevel2D();
		}
		if (roi != null && greySumClean != null) {
			greySumClean.getSpotLevel2D().transferROItoLevel2D();
		}
	}

	private static void interpolateFiniteNaNGapsLinear(double[] a) {
		if (a == null || a.length == 0) {
			return;
		}
		fillNaNPlateausFromFirstLastFinite(a);
		int n = a.length;
		int i = 0;
		while (i < n) {
			while (i < n && !Double.isFinite(a[i])) {
				i++;
			}
			if (i >= n) {
				return;
			}
			int j = i + 1;
			while (j < n && !Double.isFinite(a[j])) {
				j++;
			}
			if (j >= n) {
				return;
			}
			if (j > i + 1) {
				double v0 = a[i];
				double v1 = a[j];
				for (int k = i + 1; k < j; k++) {
					double t = (k - i) / (double) (j - i);
					a[k] = v0 + (v1 - v0) * t;
				}
			}
			i = j;
		}
	}

	private static double[] runningMedianIgnoringNaN(double[] values, int span) {
		int n = values.length;
		double[] out = new double[n];
		for (int i = 0; i < n; i++) {
			int start = Math.max(0, i - span / 2);
			int end = Math.min(n - 1, i + span / 2);
			int count = end - start + 1;

			double[] window = new double[count];
			int m = 0;
			for (int j = start; j <= end; j++) {
				double v = values[j];
				if (Double.isFinite(v)) {
					window[m++] = v;
				}
			}

			if (m == 0) {
				out[i] = Double.NaN;
				continue;
			}

			double[] finite = (m == window.length) ? window : java.util.Arrays.copyOf(window, m);
			java.util.Arrays.sort(finite);
			out[i] = finite[m / 2];
		}
		return out;
	}

	private static void fillNaNPlateausFromFirstLastFinite(double[] a) {
		if (a == null || a.length == 0) {
			return;
		}
		int first = -1;
		for (int i = 0; i < a.length; i++) {
			if (Double.isFinite(a[i])) {
				first = i;
				break;
			}
		}
		if (first < 0) {
			return;
		}
		for (int i = 0; i < first; i++) {
			a[i] = a[first];
		}
		int last = -1;
		for (int i = a.length - 1; i >= 0; i--) {
			if (Double.isFinite(a[i])) {
				last = i;
				break;
			}
		}
		if (last >= 0) {
			for (int i = last + 1; i < a.length; i++) {
				a[i] = a[last];
			}
		}
	}
}
