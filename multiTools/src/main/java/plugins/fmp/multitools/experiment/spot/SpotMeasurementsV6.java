package plugins.fmp.multitools.experiment.spot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.roi.ROI2D;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

/**
 * Spot measures for the parallel V6 pipeline ({@code AREA_COUNT_V6}, {@code GREY_SUM_V6},
 * {@code GREY_SUM_V6_PREFLY}, {@code GREY_SUM_CLEAN_V6}).
 * Kept separate from legacy {@link Spot} inner measurements so legacy CSV and behavior stay isolated.
 * {@code GREY_SUM_V6} is stored on the same scale as legacy {@code AREA_SUM}: sum on over-threshold pixels
 * divided by total ROI mask pixel count.
 * {@code GREY_SUM_V6_PREFLY} holds the same scalar before the fly-occupancy gate turns bins into NaN.
 * {@code GREY_SUM_CLEAN_V6} is built from {@code GREY_SUM_V6} by (1) linear interpolation across NaN runs
 * (including leading/trailing plateaus from the first/last finite sample), (2) optional upward spike
 * suppression vs a <strong>prior-only</strong> local median (see {@link BuildSeriesOptions#v5GreySumCleanSpikeMedianHalfWidth}),
 * then (3) the same span-10 NaN-robust running median as legacy {@code sumClean} from {@code sumNoFly} in
 * {@link plugins.fmp.multitools.experiment.spots.Spots}.
 */
public final class SpotMeasurementsV6 {

	/** Same half-window as legacy {@code rebuildSumCleanFromSumNoFlyForSpot} (span 10). */
	private static final int GREY_SUM_CLEAN_MEDIAN_SPAN = 10;

	private static final int DEFAULT_SPIKE_MEDIAN_HALF_WIDTH = 5;
	private static final double DEFAULT_SPIKE_RATIO = 1.12;
	private static final int DEFAULT_SPIKE_PASSES = 2;

	private final SpotMeasure areaCount;
	/** Same scale as {@link #greySum}; filled before fly-occupancy NaN gate (detection only). */
	private final SpotMeasure greySumPreFly;
	private final SpotMeasure greySum;
	private final SpotMeasure greySumClean;

	public SpotMeasurementsV6() {
		this.areaCount = new SpotMeasure("areaCountV6");
		this.greySumPreFly = new SpotMeasure("greySumV6PreFly");
		this.greySum = new SpotMeasure("greySumV6");
		this.greySumClean = new SpotMeasure("greySumCleanV6");
	}

	public SpotMeasurementsV6(SpotMeasurementsV6 source, boolean includeData) {
		this.areaCount = new SpotMeasure("areaCountV6");
		this.greySumPreFly = new SpotMeasure("greySumV6PreFly");
		this.greySum = new SpotMeasure("greySumV6");
		this.greySumClean = new SpotMeasure("greySumCleanV6");
		if (includeData && source != null) {
			copyFrom(source);
		}
	}

	public void copyFrom(SpotMeasurementsV6 source) {
		if (source == null) {
			return;
		}
		areaCount.copyMeasures(source.areaCount);
		greySumPreFly.copyMeasures(source.greySumPreFly);
		greySum.copyMeasures(source.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void addFrom(SpotMeasurementsV6 source) {
		if (source == null) {
			return;
		}
		areaCount.addMeasures(source.areaCount);
		greySumPreFly.addMeasures(source.greySumPreFly);
		greySum.addMeasures(source.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void computePI(SpotMeasurementsV6 m1, int n1, SpotMeasurementsV6 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computePI(m1.areaCount, m2.areaCount);
		greySumPreFly.computePI(m1.greySumPreFly, m2.greySumPreFly);
		greySum.computePI(m1.greySum, m2.greySum);
		rebuildGreySumCleanFromGreySum();
	}

	public void computeSUM(SpotMeasurementsV6 m1, int n1, SpotMeasurementsV6 m2, int n2) {
		if (m1 == null || m2 == null) {
			return;
		}
		areaCount.computeSUM(m1.areaCount, n1, m2.areaCount, n2);
		greySumPreFly.computeSUM(m1.greySumPreFly, n1, m2.greySumPreFly, n2);
		greySum.computeSUM(m1.greySum, n1, m2.greySum, n2);
		rebuildGreySumCleanFromGreySum();
	}

	public void normalizeMeasures() {
		areaCount.normalizeValues();
		greySumPreFly.normalizeValues();
		greySum.normalizeValues();
		rebuildGreySumCleanFromGreySum();
	}

	/**
	 * Recomputes {@link #getGreySumClean()} from {@link #getGreySum()} using default spike-suppression settings.
	 */
	public void rebuildGreySumCleanFromGreySum() {
		rebuildGreySumCleanFromGreySum(null);
	}

	/**
	 * Recomputes {@link #getGreySumClean()} from {@link #getGreySum()}: bridge NaN gaps with linear segments
	 * (and edge plateaus), optional upward spike suppression, then the legacy-style running median.
	 *
	 * @param opts detection / series options; may be {@code null} for defaults
	 */
	public void rebuildGreySumCleanFromGreySum(BuildSeriesOptions opts) {
		double[] in = greySum.getValues();
		if (in == null || in.length == 0) {
			return;
		}
		int spikeHw = opts != null ? opts.v5GreySumCleanSpikeMedianHalfWidth : DEFAULT_SPIKE_MEDIAN_HALF_WIDTH;
		double spikeRatio = opts != null ? opts.v5GreySumCleanSpikeRatio : DEFAULT_SPIKE_RATIO;
		int spikePasses = opts != null ? opts.v5GreySumCleanSpikePasses : DEFAULT_SPIKE_PASSES;
		if (!Double.isFinite(spikeRatio)) {
			spikeRatio = DEFAULT_SPIKE_RATIO;
		}
		spikePasses = Math.max(1, Math.min(5, spikePasses));

		double[] work = Arrays.copyOf(in, in.length);
		interpolateFiniteNaNGapsLinear(work);
		if (spikeHw > 0 && spikeRatio > 1.0) {
			for (int p = 0; p < spikePasses; p++) {
				suppressUpwardSpikesPastMedian(work, spikeHw, spikeRatio);
			}
		}
		double[] out = runningMedianIgnoringNaN(work, GREY_SUM_CLEAN_MEDIAN_SPAN);
		fillNaNPlateausFromFirstLastFinite(out);
		greySumClean.setValues(out);
	}

	public SpotMeasure getAreaCount() {
		return areaCount;
	}

	public SpotMeasure getGreySumPreFly() {
		return greySumPreFly;
	}

	public SpotMeasure getGreySum() {
		return greySum;
	}

	public SpotMeasure getGreySumClean() {
		return greySumClean;
	}

	public void restoreClippedMeasures() {
		restoreClippedMeasure(areaCount);
		restoreClippedMeasure(greySumPreFly);
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
		if (greySumPreFly != null) {
			greySumPreFly.transferValuesToLevel2D();
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
		if (greySumPreFly != null) {
			greySumPreFly.getSpotLevel2D().transferROItoLevel2D();
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
		if (greySumPreFly != null) {
			greySumPreFly.getSpotLevel2D().adjustLevel2DToImageWidth(imageWidth);
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
		if (greySumPreFly != null) {
			greySumPreFly.getSpotLevel2D().cropLevel2DToNPoints(imageWidth);
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
		if (greySumPreFly != null) {
			greySumPreFly.getSpotLevel2D().clearLevel2D();
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
			ROI2D roi = areaCount.getSpotLevel2D().getROIForImage("areaCountV6", 0, imageHeight);
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
		if (roi != null && greySumPreFly != null) {
			greySumPreFly.getSpotLevel2D().transferROItoLevel2D();
		}
		if (roi != null && greySum != null) {
			greySum.getSpotLevel2D().transferROItoLevel2D();
		}
		if (roi != null && greySumClean != null) {
			greySumClean.getSpotLevel2D().transferROItoLevel2D();
		}
	}

	/**
	 * Pulls brief upward excursions toward a causal baseline: median of up to {@code 2 × halfWidth + 1}
	 * <strong>strictly prior</strong> finite samples. Wide simultaneous highs do not inflate the baseline the way a
	 * symmetric window does.
	 */
	private static void suppressUpwardSpikesPastMedian(double[] a, int medianHalfWidth, double ratio) {
		if (a == null || a.length == 0 || medianHalfWidth < 1 || !(ratio > 1.0)) {
			return;
		}
		int pastSpan = Math.max(3, 2 * medianHalfWidth + 1);
		int bufCap = Math.max(8, pastSpan + 2);
		double[] buf = new double[bufCap];
		int n = a.length;
		for (int i = 0; i < n; i++) {
			double v = a[i];
			if (!Double.isFinite(v)) {
				continue;
			}
			double prev = previousFiniteSample(a, i);
			if (Double.isFinite(prev) && v <= prev) {
				continue;
			}
			int start = Math.max(0, i - pastSpan);
			int count = 0;
			for (int j = start; j < i; j++) {
				if (!Double.isFinite(a[j])) {
					continue;
				}
				if (count >= buf.length) {
					break;
				}
				buf[count++] = a[j];
			}
			if (count < 2) {
				continue;
			}
			double[] finite = (count == buf.length) ? buf : Arrays.copyOf(buf, count);
			Arrays.sort(finite);
			double med = finite[count / 2];
			if (!Double.isFinite(med)) {
				continue;
			}
			if (v > med * ratio) {
				a[i] = med;
			}
		}
	}

	private static double previousFiniteSample(double[] a, int i) {
		for (int j = i - 1; j >= 0; j--) {
			if (Double.isFinite(a[j])) {
				return a[j];
			}
		}
		return Double.NaN;
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

			double[] finite = (m == window.length) ? window : Arrays.copyOf(window, m);
			Arrays.sort(finite);
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
