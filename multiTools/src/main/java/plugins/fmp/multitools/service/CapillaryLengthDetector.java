package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.polyline.Bresenham;

/**
 * Measures the true pixel length of each capillary from the user-drawn ROI.
 * <p>
 * The ROI ends are assumed to sit a few pixels <em>past</em> the glass. Each
 * end is therefore a short region known to be outside the tube. The detector
 * walks inward along the ROI until a paired-wall capillary cross-section
 * appears and stays, independently at the two extremities. The physical length
 * is the distance between those two tips. The dark bar of the rack, which
 * crosses the middle of the ROI, is never a candidate endpoint.
 * <p>
 * Because all capillaries are cut to the same physical length, the detected
 * pixel lengths must vary smoothly with position in the image. After tips are
 * found, a length-vs-X trend is fitted on the reliable tubes and that trend
 * length is applied to every usable capillary (and its blue overlay).
 */
public class CapillaryLengthDetector {

	private static final int MIN_POINTS_FOR_QUADRATIC_FIT = 6;
	private static final int MIN_POINTS_FOR_LINEAR_FIT = 4;
	private static final double MAD_TO_SIGMA = 1.4826;
	private static final int GEOMETRY_HALF = 12;

	public CapillaryLengthResult measure(Experiment exp, CapillaryLengthDetectorOptions options) {
		CapillaryLengthResult result = new CapillaryLengthResult();
		if (options == null)
			options = new CapillaryLengthDetectorOptions();
		result.setPhysicalLengthMm(options.physicalLengthMm);

		if (exp == null) {
			result.setErrorMessage("No experiment selected.");
			return result;
		}
		Capillaries capillaries = exp.getCapillaries();
		if (capillaries == null || capillaries.getList().isEmpty()) {
			result.setErrorMessage("No capillary ROI found. Create or load capillaries first.");
			return result;
		}

		ImageData image = loadAveragedImage(exp, options);
		if (image == null) {
			result.setErrorMessage("Could not read the camera image at frame " + options.frameIndex + ".");
			return result;
		}

		for (Capillary cap : capillaries.getList())
			result.addMeasure(measureOneCapillary(cap, image, options));
		validate(result, image.width, options);
		return result;
	}

	/**
	 * Writes the detected lengths of the selected measures into the capillaries and
	 * marks them as auto-measured. The endpoints are stored as well, so the
	 * measured extent can be drawn over the image later on.
	 *
	 * @return number of capillaries updated
	 */
	public static int apply(CapillaryLengthResult result) {
		if (result == null)
			return 0;
		int updated = 0;
		for (CapillaryLengthResult.Measure m : result.getMeasures()) {
			if (!m.isSelected() || m.getCapillary() == null)
				continue;
			int pixels = m.getRoundedPixels();
			if (pixels <= 0)
				continue;
			m.getCapillary().setPixels(pixels);
			m.getCapillary().getProperties().setPixelsAutoMeasured(true);
			m.getCapillary().getProperties().setMeasuredEndpoints(m.getDetectedStart(), m.getDetectedEnd());
			m.getCapillary().setDescriptionOK(true);
			updated++;
		}
		return updated;
	}

	private CapillaryLengthResult.Measure measureOneCapillary(Capillary cap, ImageData image,
			CapillaryLengthDetectorOptions options) {
		String name = cap.getRoiName() != null ? cap.getRoiName() : cap.getKymographName();
		CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(cap, name, cap.getPixels());

		ROI2D roi = cap.getRoi();
		if (roi == null) {
			measure.setStatus(CapillaryLengthResult.Status.NO_ROI);
			measure.setMessage("capillary has no ROI");
			return measure;
		}
		ArrayList<Point2D> roiPoints = ROI2DUtilities.getCapillaryPoints(roi);
		if (roiPoints.size() < 2) {
			measure.setStatus(CapillaryLengthResult.Status.NO_ROI);
			measure.setMessage("ROI is not a line");
			return measure;
		}
		ArrayList<int[]> axis = Bresenham.getPixelsAlongLineFromROI2D(roiPoints);
		if (axis.size() < 8) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage("ROI too short");
			return measure;
		}

		measure.setCentroidX(centroidX(roiPoints));
		boolean straight = roiPoints.size() == 2;
		double[] cumulative = cumulativeArcLength(axis);
		measure.setRoiPixels(straight ? roiPoints.get(0).distance(roiPoints.get(roiPoints.size() - 1))
				: cumulative[axis.size() - 1]);

		AxisMeasure located = locateAlongAxis(axis, image, options);
		if (!located.found) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage(located.failure);
			return measure;
		}

		Point2D startPoint = interpolatePoint(axis, located.startFrac);
		Point2D endPoint = interpolatePoint(axis, located.endFrac);
		double lengthPx = straight ? startPoint.distance(endPoint)
				: interpolateArc(cumulative, located.endFrac) - interpolateArc(cumulative, located.startFrac);
		measure.setDetectedPixels(lengthPx);
		measure.setDetectedEndpoints(startPoint, endPoint);
		measure.setStartConfidence(located.startConfidence);
		measure.setEndConfidence(located.endConfidence);

		if (lengthPx < options.minLengthFraction * measure.getRoiPixels()) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage(String.format("detected segment too short (%.0f px)", lengthPx));
			return measure;
		}
		if (located.touchesBorder) {
			measure.setStatus(CapillaryLengthResult.Status.BORDER);
			measure.setMessage("tip sits on the ROI end: the ROI may be too short");
		} else {
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setMessage("");
		}
		measure.setSelected(true);
		return measure;
	}

	/** Where the capillary starts and ends along the sampled axis, in sample units. */
	static final class AxisMeasure {
		double startFrac;
		double endFrac;
		double startConfidence;
		double endConfidence;
		boolean found;
		boolean touchesBorder;
		String failure;

		static AxisMeasure failed(String failure) {
			AxisMeasure m = new AxisMeasure();
			m.failure = failure;
			return m;
		}
	}

	static final class Geometry {
		double offset;
		double halfWidth;
	}

	static final class TipFind {
		double axisFrac;
		double confidence;
		boolean atRoiEnd;
		boolean found;
		String failure;

		static TipFind failed(String failure) {
			TipFind t = new TipFind();
			t.failure = failure;
			return t;
		}
	}

	/**
	 * Finds both tips by walking inward from each ROI end. The axis is the ROI
	 * itself; it is not extended past the ends.
	 */
	static AxisMeasure locateAlongAxis(ArrayList<int[]> axis, ImageData image,
			CapillaryLengthDetectorOptions options) {
		int n = axis.size();
		if (n < 8)
			return AxisMeasure.failed("ROI too short");
		Geometry geometry = estimateGeometry(axis, image, options);
		TipFind start = findTip(axis, image, geometry, 0, +1, options);
		TipFind end = findTip(axis, image, geometry, n - 1, -1, options);
		if (!start.found)
			return AxisMeasure.failed("top: " + (start.failure != null ? start.failure : "tip not found"));
		if (!end.found)
			return AxisMeasure.failed("bottom: " + (end.failure != null ? end.failure : "tip not found"));
		if (start.axisFrac >= end.axisFrac)
			return AxisMeasure.failed("detected tips are in the wrong order");

		AxisMeasure located = new AxisMeasure();
		located.found = true;
		located.startFrac = start.axisFrac;
		located.endFrac = end.axisFrac;
		located.startConfidence = start.confidence;
		located.endConfidence = end.confidence;
		located.touchesBorder = start.atRoiEnd || end.atRoiEnd;
		return located;
	}

	/**
	 * Width and lateral offset of the glass, measured on the middle of the ROI,
	 * which is certainly on the tube.
	 */
	static Geometry estimateGeometry(ArrayList<int[]> axis, ImageData image,
			CapillaryLengthDetectorOptions options) {
		int n = axis.size();
		int half = Math.max(6, options.perpendicularHalfLength);
		int window = Math.max(1, options.tangentWindow);
		int from = n / 3;
		int to = (2 * n) / 3;
		if (to <= from)
			to = n - 1;
		double[] acc = new double[2 * half + 1];
		int nAcc = 0;
		for (int i = from; i <= to; i++) {
			double[] normal = normalAt(axis, i, window);
			double cx = axis.get(i)[0];
			double cy = axis.get(i)[1];
			for (int u = -half; u <= half; u++) {
				acc[u + half] += grey(image, cx + u * normal[0], cy + u * normal[1]);
			}
			nAcc++;
		}
		Geometry geometry = new Geometry();
		geometry.offset = 0.;
		geometry.halfWidth = 4.;
		if (nAcc == 0)
			return geometry;
		for (int u = 0; u < acc.length; u++)
			acc[u] /= nAcc;

		int left = bestLocalMin(acc, 1, half - 1);
		int right = bestLocalMin(acc, half + 1, acc.length - 2);
		if (left < 0 || right <= left) {
			double bestPair = 0.;
			for (int iL = 1; iL < half - 1; iL++) {
				for (int iR = half + 1; iR < acc.length - 1; iR++) {
					double gL = acc[iL + 1] - acc[iL - 1];
					double gR = acc[iR + 1] - acc[iR - 1];
					if (gL * gR >= 0.)
						continue;
					double pair = Math.min(Math.abs(gL), Math.abs(gR));
					if (pair > bestPair) {
						bestPair = pair;
						left = iL;
						right = iR;
					}
				}
			}
		}
		if (left < 0 || right <= left)
			return geometry;
		double uL = left - half;
		double uR = right - half;
		geometry.offset = 0.5 * (uL + uR);
		geometry.halfWidth = 0.5 * (uR - uL);
		if (geometry.halfWidth < 1.5)
			geometry.halfWidth = 4.;
		return geometry;
	}

	/**
	 * Walks inward from one ROI end until a paired-wall cross-section appears and
	 * persists. {@code direction} is +1 from the first axis sample, -1 from the
	 * last.
	 */
	static TipFind findTip(ArrayList<int[]> axis, ImageData image, Geometry geometry, int origin, int direction,
			CapillaryLengthDetectorOptions options) {
		int n = axis.size();
		int maxLen = Math.min(options.inwardSearchMaxPixels, n - 1);
		int len = Math.min(Math.max(options.inwardSearchPixels, options.outsidePixels + options.persistencePixels + 2),
				maxLen);
		double[] score = null;
		while (true) {
			score = wallScoresInward(axis, image, geometry, origin, direction, len, options);
			if (interiorLooksLikeCapillary(score, options) || len >= maxLen)
				break;
			len = Math.min(maxLen, len + 10);
		}
		double[] smooth = medianSmooth(score, 1);
		int outsideN = Math.min(options.outsidePixels, Math.max(1, score.length / 4));
		int confirm = confirmationPixels(options);

		if (alreadyOnCapillary(smooth, outsideN, options)) {
			TipFind atEnd = new TipFind();
			atEnd.found = true;
			atEnd.axisFrac = origin;
			atEnd.atRoiEnd = true;
			atEnd.confidence = 0.;
			return atEnd;
		}

		if (!interiorLooksLikeCapillary(score, options))
			return TipFind.failed("no capillary cross-section inward of the ROI end");

		double threshold = outsideThreshold(smooth, outsideN, options);
		int solid = firstSolidGlass(smooth, threshold, confirm);
		if (solid < 0)
			solid = firstSolidGlass(smooth, options.capillaryScoreThreshold, confirm);
		if (solid < 0)
			return TipFind.failed("no outside-to-inside transition");

		double outsideMed = lowPercentile(smooth, 0, Math.min(outsideN, Math.max(1, solid)), 50.);
		double innerMed = mean(smooth, solid, Math.min(smooth.length, solid + confirm));
		double fracAlongWindow = onsetOfGlass(smooth, solid, outsideMed, innerMed);
		double axisFrac = origin + direction * fracAlongWindow;
		if (axisFrac < 0)
			axisFrac = 0;
		if (axisFrac > n - 1)
			axisFrac = n - 1;

		TipFind tip = new TipFind();
		tip.found = true;
		tip.axisFrac = axisFrac;
		tip.atRoiEnd = fracAlongWindow < 1.25;
		tip.confidence = confidence(smooth, Math.max(1, (int) Math.round(fracAlongWindow)), outsideN);
		return tip;
	}

	private static boolean interiorLooksLikeCapillary(double[] score, CapillaryLengthDetectorOptions options) {
		if (score == null || score.length < options.outsidePixels + confirmationPixels(options))
			return false;
		double[] smooth = medianSmooth(score, 1);
		int outsideN = Math.min(options.outsidePixels, Math.max(1, score.length / 4));
		if (alreadyOnCapillary(smooth, outsideN, options))
			return true;
		double threshold = outsideThreshold(smooth, outsideN, options);
		int confirm = confirmationPixels(options);
		return firstSolidGlass(smooth, threshold, confirm) >= 0
				|| firstSolidGlass(smooth, options.capillaryScoreThreshold, confirm) >= 0;
	}

	private static int confirmationPixels(CapillaryLengthDetectorOptions options) {
		return Math.max(10, Math.max(options.persistencePixels + 6, options.confirmationPixels));
	}

	/**
	 * True when the ROI end is already on the tube: both the first samples and the
	 * inward stretch show walls. Requiring a further rise would reject the
	 * capillary instead of placing the tip on the ROI end.
	 */
	private static boolean alreadyOnCapillary(double[] smooth, int outsideN, CapillaryLengthDetectorOptions options) {
		int n = smooth.length;
		if (n < confirmationPixels(options) + 1)
			return false;
		double inner = median(smooth, Math.max(0, n - 8), n);
		double floor = options.capillaryScoreThreshold;
		int persist = confirmationPixels(options);
		return inner >= floor && persists(smooth, 0, Math.min(persist, n), floor);
	}

	/**
	 * Threshold is the overhang itself plus a small rise. Using the later liquid
	 * column as the "inside" reference would push the crossing down to the
	 * meniscus, because empty glass scores much less than coloured liquid. The
	 * 25th percentile of the outer stretch is used so a couple of glass samples
	 * at the ROI end do not raise the baseline.
	 */
	private static double outsideThreshold(double[] smooth, int outsideN, CapillaryLengthDetectorOptions options) {
		int n = Math.max(1, Math.min(Math.max(outsideN, 8), Math.max(1, smooth.length / 3)));
		n = Math.min(n, smooth.length);
		double outsideMed = lowPercentile(smooth, 0, n, 25.);
		double[] outer = copyRange(smooth, 0, n);
		double outsideMad = mad(outer, outsideMed);
		return outsideMed + Math.max(options.capillaryScoreThreshold, 2.5 * outsideMad + 0.3);
	}

	private static double[] wallScoresInward(ArrayList<int[]> axis, ImageData image, Geometry geometry, int origin,
			int direction, int length, CapillaryLengthDetectorOptions options) {
		double[] score = new double[length];
		int window = Math.max(1, options.tangentWindow);
		for (int k = 0; k < length; k++) {
			int i = origin + direction * k;
			if (i < 0 || i >= axis.size())
				break;
			double[] normal = normalAt(axis, i, window);
			score[k] = pairedWallScore(image, axis.get(i)[0], axis.get(i)[1], normal, geometry, options);
		}
		return score;
	}

	/**
	 * Two thin walls at the learned spacing, or a bright bar of that width in a
	 * dark cage slot. A fly is a filled dark blob and scores 0.
	 */
	static double pairedWallScore(ImageData image, double cx, double cy, double[] normal, Geometry geometry,
			CapillaryLengthDetectorOptions options) {
		int half = Math.max(GEOMETRY_HALF, options.perpendicularHalfLength);
		double[] grey = new double[2 * half + 1];
		for (int u = -half; u <= half; u++) {
			double px = cx + (geometry.offset + u) * normal[0];
			double py = cy + (geometry.offset + u) * normal[1];
			grey[u + half] = grey(image, px, py);
		}
		double bright = brightTubeScore(grey, geometry);
		if (bright > 0.)
			return bright;
		if (isFilledDarkBlob(grey, geometry))
			return 0.;
		return ridgePair(grey, geometry);
	}

	/**
	 * Glass in a dark cage slot is brighter than the slot at the learned width.
	 * A horizontal background edge lights the whole strip, so the sides rise too
	 * and this score stays 0.
	 */
	private static double brightTubeScore(double[] grey, Geometry geometry) {
		int half = grey.length / 2;
		int iL = (int) Math.round(-geometry.halfWidth) + half;
		int iR = (int) Math.round(geometry.halfWidth) + half;
		if (iL < 1 || iR >= grey.length - 1 || iR <= iL + 1)
			return 0.;
		int oL0 = Math.max(0, iL - 6);
		int oR1 = Math.min(grey.length, iR + 7);
		if (oL0 >= iL || iR + 1 >= oR1)
			return 0.;
		double core = mean(grey, iL, iR + 1);
		double sides = 0.5 * (mean(grey, oL0, iL) + mean(grey, iR + 1, oR1));
		double contrast = core - sides;
		if (contrast < 5.)
			return 0.;
		return 0.2 * contrast;
	}

	/**
	 * A fly or a filled slot is dark across the tube and immediately outside it.
	 * Liquid on a light field has bright surroundings; a bright tube in a dark
	 * slot is handled by {@link #brightTubeScore} first.
	 */
	private static boolean isFilledDarkBlob(double[] grey, Geometry geometry) {
		int half = grey.length / 2;
		int iL = (int) Math.round(-geometry.halfWidth) + half;
		int iR = (int) Math.round(geometry.halfWidth) + half;
		if (iL < 2 || iR >= grey.length - 2 || iR <= iL + 1)
			return false;
		double interior = mean(grey, iL, iR + 1);
		double outL = mean(grey, Math.max(0, iL - 5), iL);
		double outR = mean(grey, iR + 1, Math.min(grey.length, iR + 6));
		double outside = 0.5 * (outL + outR);
		if (outside > interior + 10.)
			return false;
		double cut = percentile(grey, 40.);
		int from = Math.max(0, iL - 4);
		int to = Math.min(grey.length, iR + 5);
		int dark = 0;
		for (int i = from; i < to; i++) {
			if (grey[i] < cut)
				dark++;
		}
		int span = to - from;
		return span > 0 && dark >= 0.7 * span;
	}

	private static double ridgePair(double[] grey, Geometry geometry) {
		int half = grey.length / 2;
		int uL = (int) Math.round(-geometry.halfWidth);
		int uR = (int) Math.round(geometry.halfWidth);
		double best = 0.;
		for (int shift = -2; shift <= 2; shift++) {
			int iL = uL + shift + half;
			int iR = uR + shift + half;
			if (iL < 1 || iR < 1 || iL >= grey.length - 1 || iR >= grey.length - 1 || iR <= iL + 1)
				continue;
			double ridgeL = 0.5 * (grey[iL - 1] + grey[iL + 1]) - grey[iL];
			double ridgeR = 0.5 * (grey[iR - 1] + grey[iR + 1]) - grey[iR];
			if (ridgeL > 0. && ridgeR > 0.) {
				double ridge = Math.min(ridgeL, ridgeR);
				if (ridge > best)
					best = ridge;
			}
			double gL = grey[iL + 1] - grey[iL - 1];
			double gR = grey[iR + 1] - grey[iR - 1];
			if (gL * gR < 0.) {
				double pair = 0.35 * Math.min(Math.abs(gL), Math.abs(gR));
				if (pair > best)
					best = pair;
			}
		}
		return best;
	}

	private static int bestLocalMin(double[] values, int from, int to) {
		int best = -1;
		double bestDepth = 0.;
		int start = Math.max(1, from);
		int end = Math.min(values.length - 2, to);
		for (int i = start; i <= end; i++) {
			double depth = 0.5 * (values[i - 1] + values[i + 1]) - values[i];
			if (depth > bestDepth) {
				bestDepth = depth;
				best = i;
			}
		}
		return bestDepth > 0.4 ? best : -1;
	}

	/**
	 * First index at which a long stretch looks like glass. Short lid edges and
	 * dust lines do not last this long, so they are skipped. A mean over a mixed
	 * overhang-plus-glass window is not enough: most samples in the stretch must
	 * actually be above the threshold.
	 */
	private static int firstSolidGlass(double[] score, double threshold, int confirm) {
		if (score == null || confirm < 2 || score.length < confirm)
			return -1;
		for (int k = 0; k <= score.length - confirm; k++) {
			if (solidStretch(score, k, confirm, threshold))
				return k;
		}
		return -1;
	}

	private static boolean solidStretch(double[] score, int from, int confirm, double threshold) {
		int nLow = 0;
		double sum = 0.;
		for (int i = 0; i < confirm; i++) {
			double v = score[from + i];
			sum += v;
			if (v < threshold)
				nLow++;
		}
		return nLow <= 1 && sum / confirm >= threshold;
	}

	/**
	 * Walks back from a confirmed glass stretch to the last sample that still
	 * looks like the overhang, so faint pre-tip leakage is not counted as glass.
	 */
	private static double onsetOfGlass(double[] score, int solid, double outsideMed, double innerMed) {
		if (solid <= 0)
			return 0;
		double rise = innerMed - outsideMed;
		if (!(rise > 0.2))
			return solid;
		double cut = outsideMed + 0.5 * rise;
		int k = solid;
		while (k > 0 && score[k - 1] >= cut)
			k--;
		if (k <= 0)
			return 0;
		return refineCrossing(score, cut, k);
	}

	private static boolean persists(double[] values, int from, int count, double threshold) {
		if (from + count > values.length)
			return false;
		for (int i = 0; i < count; i++) {
			if (values[from + i] < threshold)
				return false;
		}
		return true;
	}

	private static double refineCrossing(double[] values, double threshold, int index) {
		if (index <= 0)
			return index;
		double vIn = values[index];
		double vOut = values[index - 1];
		double rise = vIn - vOut;
		if (!(rise > 0))
			return index;
		double frac = (threshold - vOut) / rise;
		if (frac < 0)
			frac = 0;
		if (frac > 1)
			frac = 1;
		return (index - 1) + frac;
	}

	private static double confidence(double[] smooth, int split, int outsideN) {
		double[] out = copyRange(smooth, 0, Math.min(outsideN, split));
		double[] in = copyRange(smooth, split, smooth.length);
		if (out.length == 0 || in.length == 0)
			return 0.;
		double medIn = percentile(in, 50.);
		double medOut = percentile(out, 50.);
		double madIn = mad(in, medIn);
		double madOut = mad(out, medOut);
		return (medIn - medOut) / (madIn + madOut + 1.);
	}

	private static double[] copyRange(double[] values, int from, int to) {
		if (to < from)
			to = from;
		if (from < 0)
			from = 0;
		if (to > values.length)
			to = values.length;
		double[] out = new double[to - from];
		System.arraycopy(values, from, out, 0, out.length);
		return out;
	}

	private static double mad(double[] values, double median) {
		if (values.length == 0)
			return 0.;
		double[] dev = new double[values.length];
		for (int i = 0; i < values.length; i++)
			dev[i] = Math.abs(values[i] - median);
		return percentile(dev, 50.);
	}

	private static double[] medianSmooth(double[] values, int radius) {
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			int from = Math.max(0, i - radius);
			int to = Math.min(values.length, i + radius + 1);
			out[i] = median(values, from, to);
		}
		return out;
	}

	private static double median(double[] values, int from, int to) {
		return lowPercentile(values, from, to, 50.);
	}

	private static double lowPercentile(double[] values, int from, int to, double percent) {
		int n = to - from;
		if (n <= 0)
			return 0.;
		double[] slice = new double[n];
		System.arraycopy(values, from, slice, 0, n);
		return percentile(slice, percent);
	}

	private static double mean(double[] values, int from, int to) {
		int n = to - from;
		if (n <= 0)
			return 0.;
		double sum = 0.;
		for (int i = from; i < to; i++)
			sum += values[i];
		return sum / n;
	}

	private static double[] normalAt(ArrayList<int[]> axis, int i, int window) {
		int n = axis.size();
		int i0 = Math.max(0, i - window);
		int i1 = Math.min(n - 1, i + window);
		double tx = axis.get(i1)[0] - axis.get(i0)[0];
		double ty = axis.get(i1)[1] - axis.get(i0)[1];
		double len = Math.hypot(tx, ty);
		if (len < 1e-6)
			return new double[] { 1., 0. };
		return new double[] { -ty / len, tx / len };
	}

	private static double grey(ImageData image, double x, double y) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int x1 = x0 + 1;
		int y1 = y0 + 1;
		double fx = x - x0;
		double fy = y - y0;
		return (1. - fy) * ((1. - fx) * greyInt(image, x0, y0) + fx * greyInt(image, x1, y0))
				+ fy * ((1. - fx) * greyInt(image, x0, y1) + fx * greyInt(image, x1, y1));
	}

	private static double greyInt(ImageData image, int x, int y) {
		if (x < 0)
			x = 0;
		if (y < 0)
			y = 0;
		if (x >= image.width)
			x = image.width - 1;
		if (y >= image.height)
			y = image.height - 1;
		int idx = x + y * image.width;
		double sum = 0.;
		for (int c = 0; c < image.nChannels; c++)
			sum += image.channels[c][idx];
		return sum / image.nChannels;
	}

	private static Point2D interpolatePoint(ArrayList<int[]> axis, double frac) {
		int n = axis.size();
		int i0 = (int) Math.floor(frac);
		if (i0 < 0)
			i0 = 0;
		if (i0 > n - 2)
			i0 = n - 2;
		double f = frac - i0;
		int[] p0 = axis.get(i0);
		int[] p1 = axis.get(i0 + 1);
		return new Point2D.Double(p0[0] + f * (p1[0] - p0[0]), p0[1] + f * (p1[1] - p0[1]));
	}

	private static double[] cumulativeArcLength(ArrayList<int[]> axis) {
		int n = axis.size();
		double[] cumulative = new double[n];
		for (int i = 1; i < n; i++) {
			int[] p0 = axis.get(i - 1);
			int[] p1 = axis.get(i);
			cumulative[i] = cumulative[i - 1] + Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
		}
		return cumulative;
	}

	private static double interpolateArc(double[] cumulative, double frac) {
		int n = cumulative.length;
		int i0 = (int) Math.floor(frac);
		if (i0 < 0)
			i0 = 0;
		if (i0 > n - 2)
			i0 = n - 2;
		double f = frac - i0;
		return cumulative[i0] + f * (cumulative[i0 + 1] - cumulative[i0]);
	}

	private static double centroidX(List<Point2D> points) {
		double sum = 0.;
		for (Point2D p : points)
			sum += p.getX();
		return sum / points.size();
	}

	// === cross-capillary validation ===

	/**
	 * Fits a smooth length-vs-X trend on the reliable tubes, then replaces every
	 * usable capillary's length (and blue overlay) with that trend. Tip outliers
	 * are cleaned first so the top/bottom curves used to place the overlays are
	 * not pulled by a few bad tips.
	 */
	static void validate(CapillaryLengthResult result, int imageWidth, CapillaryLengthDetectorOptions options) {
		List<CapillaryLengthResult.Measure> usable = new ArrayList<CapillaryLengthResult.Measure>();
		for (CapillaryLengthResult.Measure m : result.getMeasures()) {
			if (m.getStatus().isUsable() && Double.isFinite(m.getDetectedPixels()))
				usable.add(m);
		}
		if (usable.isEmpty())
			return;

		correctEndpointOutliers(usable, imageWidth, options);
		applyLengthTrend(result, usable, imageWidth, options);
	}

	private static void applyLengthTrend(CapillaryLengthResult result, List<CapillaryLengthResult.Measure> usable,
			int imageWidth, CapillaryLengthDetectorOptions options) {
		double[] lengths = new double[usable.size()];
		for (int i = 0; i < usable.size(); i++)
			lengths[i] = usable.get(i).getDetectedPixels();
		double median = percentile(lengths, 50.);

		double[] fit = fitLengthVersusPosition(usable, imageWidth);
		double[] residuals = new double[usable.size()];
		for (int i = 0; i < usable.size(); i++) {
			CapillaryLengthResult.Measure m = usable.get(i);
			double expected = fit != null ? evaluateFit(fit, normalizeX(m.getCentroidX(), imageWidth)) : median;
			m.setFittedPixels(expected);
			residuals[i] = Math.abs(m.getDetectedPixels() - expected);
		}

		double tolerance = options.outlierMadFactor * MAD_TO_SIGMA * percentile(residuals, 50.);
		double floor = options.outlierMinTolerance * median;
		if (!(tolerance > floor))
			tolerance = floor;

		List<CapillaryLengthResult.Measure> inliers = new ArrayList<CapillaryLengthResult.Measure>();
		List<Double> retained = new ArrayList<Double>();
		for (int i = 0; i < usable.size(); i++) {
			CapillaryLengthResult.Measure m = usable.get(i);
			if (residuals[i] <= tolerance) {
				inliers.add(m);
				retained.add(Double.valueOf(m.getDetectedPixels()));
			}
		}

		if (inliers.isEmpty()) {
			for (CapillaryLengthResult.Measure m : usable) {
				m.setStatus(CapillaryLengthResult.Status.OUTLIER);
				m.setSelected(false);
				m.setMessage("off the trend of the other capillaries");
			}
			result.setMedianPixels(median);
			result.setMinPixels(min(lengths));
			result.setMaxPixels(max(lengths));
			return;
		}

		double[] fit2 = fitLengthVersusPosition(inliers, imageWidth);
		double inlierMedian = percentile(toArray(retained), 50.);
		for (CapillaryLengthResult.Measure m : usable) {
			double expected = fit2 != null ? evaluateFit(fit2, normalizeX(m.getCentroidX(), imageWidth))
					: inlierMedian;
			m.setFittedPixels(expected);
		}

		applyTrendOverlays(usable, imageWidth);

		double[] applied = new double[usable.size()];
		for (int i = 0; i < usable.size(); i++)
			applied[i] = usable.get(i).getDetectedPixels();
		result.setMedianPixels(percentile(applied, 50.));
		result.setMinPixels(min(applied));
		result.setMaxPixels(max(applied));
	}

	/**
	 * Replaces every usable length with {@code fittedPixels} and rebuilds the
	 * overlay so its tip-to-tip distance matches. Placement uses the midpoint of
	 * the smooth top and bottom tip trends, so lengths vary regularly along X.
	 */
	static void applyTrendOverlays(List<CapillaryLengthResult.Measure> usable, int imageWidth) {
		List<CapillaryLengthResult.Measure> withEnds = new ArrayList<CapillaryLengthResult.Measure>();
		for (CapillaryLengthResult.Measure m : usable) {
			if (m.hasDetectedEndpoints() && Double.isFinite(m.getCentroidX()))
				withEnds.add(m);
		}

		double[] topFit = null;
		double[] botFit = null;
		double topMed = Double.NaN;
		double botMed = Double.NaN;
		if (withEnds.size() >= MIN_POINTS_FOR_LINEAR_FIT) {
			double[] topY = valuesFor(withEnds, true);
			double[] botY = valuesFor(withEnds, false);
			topFit = fitScalarVersusPosition(withEnds, imageWidth, topY);
			botFit = fitScalarVersusPosition(withEnds, imageWidth, botY);
			topMed = percentile(topY, 50.);
			botMed = percentile(botY, 50.);
		}

		for (CapillaryLengthResult.Measure m : usable) {
			double target = m.getFittedPixels();
			if (!Double.isFinite(target) || target <= 0)
				continue;
			double raw = m.getDetectedPixels();
			if (m.hasDetectedEndpoints() && withEnds.size() >= MIN_POINTS_FOR_LINEAR_FIT) {
				double u = normalizeX(m.getCentroidX(), imageWidth);
				double fittedTop = topFit != null ? evaluateFit(topFit, u) : topMed;
				double fittedBot = botFit != null ? evaluateFit(botFit, u) : botMed;
				double mid = 0.5 * (fittedTop + fittedBot);
				double newTop = mid - 0.5 * target;
				double newBot = mid + 0.5 * target;
				applyEndpointYs(m, newTop, newBot, axisOf(m));
			}
			m.setDetectedPixels(target);
			m.setSelected(true);
			if (Math.abs(raw - target) > 1.5) {
				m.setStatus(CapillaryLengthResult.Status.CORRECTED);
				m.setMessage(String.format("replaced %.0f px with trend %.0f px", raw, target));
			} else if (m.getStatus() != CapillaryLengthResult.Status.CORRECTED
					&& m.getStatus() != CapillaryLengthResult.Status.BORDER) {
				m.setStatus(CapillaryLengthResult.Status.OK);
				m.setMessage("trend length");
			}
		}
	}

	/**
	 * Tops should form a smooth curve across the image, and bottoms another.
	 * Moving only the outlier end avoids stretching a false tip that sits on the
	 * glass (high wall score) and shoving the other end past the tube.
	 */
	static void correctEndpointOutliers(List<CapillaryLengthResult.Measure> measures, int imageWidth,
			CapillaryLengthDetectorOptions options) {
		List<CapillaryLengthResult.Measure> withEnds = new ArrayList<CapillaryLengthResult.Measure>();
		for (CapillaryLengthResult.Measure m : measures) {
			if (m.hasDetectedEndpoints() && Double.isFinite(m.getCentroidX()))
				withEnds.add(m);
		}
		if (withEnds.size() < MIN_POINTS_FOR_LINEAR_FIT)
			return;

		int n = withEnds.size();
		double[] topY = new double[n];
		double[] botY = new double[n];
		double[] span = new double[n];
		for (int i = 0; i < n; i++) {
			Point2D a = withEnds.get(i).getDetectedStart();
			Point2D b = withEnds.get(i).getDetectedEnd();
			topY[i] = Math.min(a.getY(), b.getY());
			botY[i] = Math.max(a.getY(), b.getY());
			span[i] = botY[i] - topY[i];
		}
		double medianSpan = percentile(span, 50.);
		double[] fitTop = fitScalarVersusPosition(withEnds, imageWidth, topY);
		double[] fitBot = fitScalarVersusPosition(withEnds, imageWidth, botY);
		double[] topRes = residualsVersusFit(withEnds, imageWidth, topY, fitTop);
		double[] botRes = residualsVersusFit(withEnds, imageWidth, botY, fitBot);
		double topTol = yTolerance(topRes, medianSpan, options);
		double botTol = yTolerance(botRes, medianSpan, options);

		List<CapillaryLengthResult.Measure> topInliers = inliersOf(withEnds, topRes, topTol);
		List<CapillaryLengthResult.Measure> botInliers = inliersOf(withEnds, botRes, botTol);
		double[] topFit2 = topInliers.size() >= MIN_POINTS_FOR_LINEAR_FIT
				? fitScalarVersusPosition(topInliers, imageWidth, valuesFor(topInliers, true))
				: fitTop;
		double[] botFit2 = botInliers.size() >= MIN_POINTS_FOR_LINEAR_FIT
				? fitScalarVersusPosition(botInliers, imageWidth, valuesFor(botInliers, false))
				: fitBot;
		double topMed = percentile(topY, 50.);
		double botMed = percentile(botY, 50.);

		for (int i = 0; i < n; i++) {
			CapillaryLengthResult.Measure m = withEnds.get(i);
			double u = normalizeX(m.getCentroidX(), imageWidth);
			double fittedTop = topFit2 != null ? evaluateFit(topFit2, u) : topMed;
			double fittedBot = botFit2 != null ? evaluateFit(botFit2, u) : botMed;
			boolean fixTop = topRes[i] > topTol;
			boolean fixBot = botRes[i] > botTol;
			if (!fixTop && !fixBot)
				continue;
			double newTop = fixTop ? fittedTop : topY[i];
			double newBot = fixBot ? fittedBot : botY[i];
			if (newBot < newTop + 4.)
				newBot = newTop + 4.;
			ArrayList<int[]> axis = axisOf(m);
			StringBuilder note = new StringBuilder();
			if (fixTop)
				note.append(String.format("top %.0f->%.0f", topY[i], newTop));
			if (fixBot) {
				if (note.length() > 0)
					note.append(", ");
				note.append(String.format("bottom %.0f->%.0f", botY[i], newBot));
			}
			applyEndpointYs(m, newTop, newBot, axis);
			m.setStatus(CapillaryLengthResult.Status.CORRECTED);
			m.setSelected(true);
			m.setMessage("replaced " + note);
		}
	}

	private static double yTolerance(double[] residuals, double medianSpan, CapillaryLengthDetectorOptions options) {
		double mad = percentile(residuals, 50.);
		double tolerance = options.outlierMadFactor * MAD_TO_SIGMA * mad;
		double floor = Math.max(6., 0.015 * medianSpan);
		if (!(tolerance > floor))
			tolerance = floor;
		return tolerance;
	}

	private static List<CapillaryLengthResult.Measure> inliersOf(List<CapillaryLengthResult.Measure> measures,
			double[] residuals, double tolerance) {
		List<CapillaryLengthResult.Measure> inliers = new ArrayList<CapillaryLengthResult.Measure>();
		for (int i = 0; i < measures.size(); i++) {
			if (residuals[i] <= tolerance)
				inliers.add(measures.get(i));
		}
		return inliers;
	}

	private static double[] valuesFor(List<CapillaryLengthResult.Measure> measures, boolean tops) {
		double[] values = new double[measures.size()];
		for (int i = 0; i < measures.size(); i++) {
			Point2D a = measures.get(i).getDetectedStart();
			Point2D b = measures.get(i).getDetectedEnd();
			values[i] = tops ? Math.min(a.getY(), b.getY()) : Math.max(a.getY(), b.getY());
		}
		return values;
	}

	private static double[] residualsVersusFit(List<CapillaryLengthResult.Measure> measures, int imageWidth,
			double[] values, double[] fit) {
		double[] residuals = new double[values.length];
		double median = percentile(values, 50.);
		for (int i = 0; i < values.length; i++) {
			double expected = fit != null ? evaluateFit(fit, normalizeX(measures.get(i).getCentroidX(), imageWidth))
					: median;
			residuals[i] = Math.abs(values[i] - expected);
		}
		return residuals;
	}

	private static ArrayList<int[]> axisOf(CapillaryLengthResult.Measure m) {
		Capillary cap = m.getCapillary();
		if (cap == null || cap.getRoi() == null)
			return null;
		ArrayList<Point2D> roiPoints = ROI2DUtilities.getCapillaryPoints(cap.getRoi());
		if (roiPoints.size() < 2)
			return null;
		ArrayList<int[]> axis = Bresenham.getPixelsAlongLineFromROI2D(roiPoints);
		return axis.size() < 2 ? null : axis;
	}

	/**
	 * Places the overlay ends at {@code topY} / {@code botY}, walking the ROI
	 * when one is available so a tilted tube stays on its axis.
	 */
	static void applyEndpointYs(CapillaryLengthResult.Measure m, double topY, double botY, ArrayList<int[]> axis) {
		if (m == null || !m.hasDetectedEndpoints())
			return;
		Point2D start = m.getDetectedStart();
		Point2D end = m.getDetectedEnd();
		boolean startIsTop = start.getY() <= end.getY();
		Point2D oldTop = startIsTop ? start : end;
		Point2D oldBot = startIsTop ? end : start;
		Point2D top;
		Point2D bot;
		if (axis != null && axis.size() >= 2) {
			top = pointOnAxisAtY(axis, topY);
			bot = pointOnAxisAtY(axis, botY);
		} else {
			top = new Point2D.Double(oldTop.getX(), topY);
			bot = new Point2D.Double(oldBot.getX(), botY);
		}
		if (startIsTop)
			m.setDetectedEndpoints(top, bot);
		else
			m.setDetectedEndpoints(bot, top);
		m.setDetectedPixels(top.distance(bot));
	}

	static Point2D pointOnAxisAtY(ArrayList<int[]> axis, double targetY) {
		int best = 0;
		double bestD = Double.POSITIVE_INFINITY;
		for (int i = 0; i < axis.size(); i++) {
			double d = Math.abs(axis.get(i)[1] - targetY);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		int[] p = axis.get(best);
		return new Point2D.Double(p[0], p[1]);
	}

	private static double[] toArray(List<Double> values) {
		double[] array = new double[values.size()];
		for (int i = 0; i < array.length; i++)
			array[i] = values.get(i).doubleValue();
		return array;
	}

	private static double normalizeX(double x, int imageWidth) {
		double halfWidth = Math.max(1, imageWidth) / 2.;
		return (x - halfWidth) / halfWidth;
	}

	private static double[] fitLengthVersusPosition(List<CapillaryLengthResult.Measure> measures, int imageWidth) {
		double[] values = new double[measures.size()];
		for (int i = 0; i < measures.size(); i++)
			values[i] = measures.get(i).getDetectedPixels();
		return fitScalarVersusPosition(measures, imageWidth, values);
	}

	private static double[] fitScalarVersusPosition(List<CapillaryLengthResult.Measure> measures, int imageWidth,
			double[] values) {
		int n = measures.size();
		if (values == null || values.length != n)
			return null;
		int degree = n >= MIN_POINTS_FOR_QUADRATIC_FIT ? 2 : (n >= MIN_POINTS_FOR_LINEAR_FIT ? 1 : -1);
		if (degree < 0)
			return null;

		int nCoef = degree + 1;
		double[][] normal = new double[nCoef][nCoef + 1];
		for (int i = 0; i < n; i++) {
			double u = normalizeX(measures.get(i).getCentroidX(), imageWidth);
			double y = values[i];
			double[] powers = new double[nCoef];
			powers[0] = 1.;
			for (int k = 1; k < nCoef; k++)
				powers[k] = powers[k - 1] * u;
			for (int r = 0; r < nCoef; r++) {
				for (int c = 0; c < nCoef; c++)
					normal[r][c] += powers[r] * powers[c];
				normal[r][nCoef] += powers[r] * y;
			}
		}
		return solve(normal, nCoef);
	}

	private static double[] solve(double[][] matrix, int size) {
		for (int col = 0; col < size; col++) {
			int pivot = col;
			for (int r = col + 1; r < size; r++) {
				if (Math.abs(matrix[r][col]) > Math.abs(matrix[pivot][col]))
					pivot = r;
			}
			if (Math.abs(matrix[pivot][col]) < 1e-12)
				return null;
			double[] swap = matrix[col];
			matrix[col] = matrix[pivot];
			matrix[pivot] = swap;
			for (int r = 0; r < size; r++) {
				if (r == col)
					continue;
				double factor = matrix[r][col] / matrix[col][col];
				for (int c = col; c <= size; c++)
					matrix[r][c] -= factor * matrix[col][c];
			}
		}
		double[] solution = new double[size];
		for (int i = 0; i < size; i++)
			solution[i] = matrix[i][size] / matrix[i][i];
		return solution;
	}

	private static double evaluateFit(double[] coefficients, double u) {
		double value = 0.;
		double power = 1.;
		for (int i = 0; i < coefficients.length; i++) {
			value += coefficients[i] * power;
			power *= u;
		}
		return value;
	}

	// === image access ===

	static final class ImageData {
		final int width;
		final int height;
		final int nChannels;
		final double[][] channels;

		ImageData(int width, int height, double[][] channels) {
			this.width = width;
			this.height = height;
			this.nChannels = channels.length;
			this.channels = channels;
		}
	}

	private ImageData loadAveragedImage(Experiment exp, CapillaryLengthDetectorOptions options) {
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return null;
		int nTotalFrames = seqCamData.getImageLoader() != null ? seqCamData.getImageLoader().getNTotalFrames() : 0;
		int first = Math.max(0, options.frameIndex);
		int nWanted = Math.max(1, options.nFramesAveraged);
		int stride = Math.max(1, options.frameStride);

		SequenceLoaderService loader = new SequenceLoaderService();
		List<ImageData> frames = new ArrayList<ImageData>();

		for (int i = 0; i < nWanted; i++) {
			int t = first + i * stride;
			if (nTotalFrames > 0 && t >= nTotalFrames)
				break;
			String path = seqCamData.getFileNameFromImageList(t);
			if (path == null)
				continue;
			IcyBufferedImage image = loader.imageIORead(path);
			if (image == null)
				continue;
			if (!frames.isEmpty()) {
				ImageData firstFrame = frames.get(0);
				if (image.getSizeX() != firstFrame.width || image.getSizeY() != firstFrame.height) {
					Logger.warn("CapillaryLengthDetector: frame " + t + " has a different size, skipped");
					continue;
				}
			}
			int width = image.getSizeX();
			int height = image.getSizeY();
			int nCh = Math.max(1, Math.min(3, image.getSizeC()));
			double[][] channels = new double[nCh][width * height];
			for (int c = 0; c < nCh; c++) {
				double[] values = Array1DUtil.arrayToDoubleArray(image.getDataXY(c), image.isSignedDataType());
				int n = Math.min(channels[c].length, values.length);
				System.arraycopy(values, 0, channels[c], 0, n);
			}
			frames.add(new ImageData(width, height, channels));
		}

		if (frames.isEmpty())
			return null;
		return combineFrames(frames);
	}

	/**
	 * Per-pixel median across frames. A fly present in a minority of frames
	 * disappears; the glass walls, present in every frame, remain.
	 */
	static ImageData combineFrames(List<ImageData> frames) {
		if (frames == null || frames.isEmpty())
			return null;
		if (frames.size() == 1)
			return frames.get(0);
		ImageData first = frames.get(0);
		int n = frames.size();
		double[][] out = new double[first.nChannels][first.width * first.height];
		double[] buf = new double[n];
		for (int c = 0; c < first.nChannels; c++) {
			for (int p = 0; p < out[c].length; p++) {
				for (int f = 0; f < n; f++)
					buf[f] = frames.get(f).channels[c][p];
				out[c][p] = percentile(buf, 50.);
			}
		}
		return new ImageData(first.width, first.height, out);
	}

	private static double percentile(double[] values, double percent) {
		if (values.length == 0)
			return Double.NaN;
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		double rank = (percent / 100.) * (sorted.length - 1);
		int low = (int) Math.floor(rank);
		int high = (int) Math.ceil(rank);
		if (low == high)
			return sorted[low];
		return sorted[low] + (rank - low) * (sorted[high] - sorted[low]);
	}

	private static double min(double[] values) {
		double result = Double.POSITIVE_INFINITY;
		for (double v : values) {
			if (v < result)
				result = v;
		}
		return result;
	}

	private static double max(double[] values) {
		double result = Double.NEGATIVE_INFINITY;
		for (double v : values) {
			if (v > result)
				result = v;
		}
		return result;
	}
}
