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
 * Measures the true pixel length of each capillary inside the ROI drawn by the
 * user, so that every capillary gets its own volume/pixel scale.
 * <p>
 * The ROI is normally a bit longer than the capillary. Rather than trusting the
 * ROI extremities, the detector walks along the ROI axis and, at each position,
 * compares the intensity of a narrow band centred on the axis with the
 * intensity of the background just beside it. This center-versus-flank contrast
 * is high while the axis crosses the glass tube and drops to nearly zero beyond
 * its ends, which locates the endpoints without relying on absolute brightness
 * (the background is light grey but inhomogeneous).
 * <p>
 * Because all capillaries are calibrated to the same physical length, the
 * detected pixel lengths must vary smoothly with position in the image; the
 * residual variation is the lens distortion and camera tilt we want to correct.
 * Capillaries departing from that smooth pattern are flagged as outliers.
 */
public class CapillaryLengthDetector {

	private static final int MIN_POINTS_FOR_QUADRATIC_FIT = 6;
	private static final int MIN_POINTS_FOR_LINEAR_FIT = 4;
	private static final double MAD_TO_SIGMA = 1.4826;

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

		for (Capillary cap : capillaries.getList()) {
			result.addMeasure(measureOneCapillary(cap, image, options));
		}
		validate(result, image.width, options);
		return result;
	}

	/**
	 * Writes the detected lengths of the selected measures into the capillaries and
	 * marks them as auto-measured.
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
			m.getCapillary().setDescriptionOK(true);
			updated++;
		}
		return updated;
	}

	// === detection of one capillary ===

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
		int n = axis.size();
		if (n < 8) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage("ROI too short");
			return measure;
		}

		measure.setCentroidX(centroidX(roiPoints));
		boolean straight = roiPoints.size() == 2;
		double[] cumulative = cumulativeArcLength(axis);
		measure.setRoiPixels(straight ? roiPoints.get(0).distance(roiPoints.get(roiPoints.size() - 1))
				: cumulative[n - 1]);

		AxisMeasure located = locateAlongAxis(axis, image, options);
		if (!located.found) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage(located.failure);
			return measure;
		}

		double lengthPx = straight
				? interpolatePoint(axis, located.startFrac).distance(interpolatePoint(axis, located.endFrac))
				: interpolateArc(cumulative, located.endFrac) - interpolateArc(cumulative, located.startFrac);
		measure.setDetectedPixels(lengthPx);

		if (lengthPx < options.minLengthFraction * measure.getRoiPixels()) {
			measure.setStatus(CapillaryLengthResult.Status.FAILED);
			measure.setMessage(String.format("detected segment too short (%.0f px)", lengthPx));
			return measure;
		}
		if (located.touchesBorder) {
			measure.setStatus(CapillaryLengthResult.Status.BORDER);
			measure.setMessage("capillary extends beyond the ROI: lengthen the ROI");
			return measure;
		}
		measure.setStatus(CapillaryLengthResult.Status.OK);
		measure.setSelected(true);
		return measure;
	}

	/** Where the capillary starts and ends along the sampled axis, in sample units. */
	static final class AxisMeasure {
		double startFrac;
		double endFrac;
		boolean found;
		boolean touchesBorder;
		String failure;

		static AxisMeasure failed(String failure) {
			AxisMeasure m = new AxisMeasure();
			m.failure = failure;
			return m;
		}
	}

	static AxisMeasure locateAlongAxis(ArrayList<int[]> axis, ImageData image,
			CapillaryLengthDetectorOptions options) {
		int n = axis.size();
		double[] presence = buildPresenceProfile(axis, image, options);
		double baseline = percentile(presence, 10.);
		double peak = percentile(presence, 90.);
		double contrast = peak - baseline;
		if (!(contrast > 0))
			return AxisMeasure.failed("no contrast between capillary and background");

		double threshold = baseline + options.thresholdFraction * contrast;
		int margin = (int) Math.round(Math.max(0., options.searchMarginFraction) * n);
		margin = Math.min(margin, (n - 4) / 2);
		int[] run = findLongestRun(presence, threshold, margin, n - 1 - margin);
		if (run == null)
			return AxisMeasure.failed("capillary not found inside the ROI");

		int start = run[0];
		int end = run[1];
		while (start > 0 && presence[start - 1] >= threshold)
			start--;
		while (end < n - 1 && presence[end + 1] >= threshold)
			end++;

		AxisMeasure located = new AxisMeasure();
		located.found = true;
		located.touchesBorder = start == 0 || end == n - 1;
		located.startFrac = refineCrossing(presence, threshold, start, -1);
		located.endFrac = refineCrossing(presence, threshold, end, +1);
		return located;
	}

	/**
	 * Contrast between the band centred on the capillary axis and the background
	 * flanks, one value per position along the axis. The strongest channel wins, so
	 * both coloured liquid and plain glass edges are picked up.
	 */
	static double[] buildPresenceProfile(ArrayList<int[]> axis, ImageData image,
			CapillaryLengthDetectorOptions options) {
		int n = axis.size();
		double[] presence = new double[n];
		int half = Math.max(options.capillaryHalfWidth + options.flankGap + 1, options.perpendicularHalfLength);
		int inner = Math.max(0, options.capillaryHalfWidth);
		int outerStart = inner + Math.max(1, options.flankGap);
		int window = Math.max(1, options.tangentWindow);
		int nCh = image.nChannels;
		double[] centerSum = new double[nCh];
		double[] flankSum = new double[nCh];

		for (int i = 0; i < n; i++) {
			double[] normal = normalAt(axis, i, window);
			int cx = axis.get(i)[0];
			int cy = axis.get(i)[1];
			Arrays.fill(centerSum, 0.);
			Arrays.fill(flankSum, 0.);
			int centerN = 0;
			int flankN = 0;

			for (int d = -half; d <= half; d++) {
				int ad = Math.abs(d);
				if (ad > inner && ad < outerStart)
					continue;
				int x = (int) Math.round(cx + d * normal[0]);
				int y = (int) Math.round(cy + d * normal[1]);
				if (x < 0 || x >= image.width || y < 0 || y >= image.height)
					continue;
				int idx = x + y * image.width;
				if (ad <= inner) {
					for (int c = 0; c < nCh; c++)
						centerSum[c] += image.channels[c][idx];
					centerN++;
				} else {
					for (int c = 0; c < nCh; c++)
						flankSum[c] += image.channels[c][idx];
					flankN++;
				}
			}

			if (centerN == 0 || flankN == 0)
				continue;
			double best = 0.;
			for (int c = 0; c < nCh; c++) {
				double diff = Math.abs(centerSum[c] / centerN - flankSum[c] / flankN);
				if (diff > best)
					best = diff;
			}
			presence[i] = best;
		}
		return presence;
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

	private static int[] findLongestRun(double[] values, double threshold, int from, int to) {
		int bestStart = -1;
		int bestEnd = -1;
		int bestLength = 0;
		int runStart = -1;
		for (int i = from; i <= to; i++) {
			if (values[i] >= threshold) {
				if (runStart < 0)
					runStart = i;
				int length = i - runStart + 1;
				if (length > bestLength) {
					bestLength = length;
					bestStart = runStart;
					bestEnd = i;
				}
			} else {
				runStart = -1;
			}
		}
		return bestLength > 0 ? new int[] { bestStart, bestEnd } : null;
	}

	/**
	 * Sub-pixel position where the presence profile crosses the threshold, just
	 * outside the detected run (direction -1 for the start, +1 for the end).
	 */
	private static double refineCrossing(double[] values, double threshold, int index, int direction) {
		int neighbour = index + direction;
		if (neighbour < 0 || neighbour >= values.length)
			return index;
		double vIn = values[index];
		double vOut = values[neighbour];
		double drop = vIn - vOut;
		if (!(drop > 0))
			return index;
		return index + direction * (vIn - threshold) / drop;
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
	 * Flags capillaries whose length departs from the smooth spatial pattern the
	 * other capillaries describe. All tubes are physically identical, so any abrupt
	 * departure is a detection failure (fly over the tip, bubble, misplaced ROI)
	 * rather than real distortion.
	 */
	static void validate(CapillaryLengthResult result, int imageWidth, CapillaryLengthDetectorOptions options) {
		List<CapillaryLengthResult.Measure> usable = new ArrayList<CapillaryLengthResult.Measure>();
		for (CapillaryLengthResult.Measure m : result.getMeasures()) {
			if (m.getStatus().isUsable() && Double.isFinite(m.getDetectedPixels()))
				usable.add(m);
		}
		if (usable.isEmpty())
			return;

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
		double floor = 0.01 * median;
		if (!(tolerance > floor))
			tolerance = floor;

		List<Double> retained = new ArrayList<Double>();
		for (int i = 0; i < usable.size(); i++) {
			CapillaryLengthResult.Measure m = usable.get(i);
			if (residuals[i] <= tolerance) {
				retained.add(Double.valueOf(m.getDetectedPixels()));
				continue;
			}
			m.setStatus(CapillaryLengthResult.Status.OUTLIER);
			m.setSelected(false);
			m.setMessage(String.format("%.0f px off the trend of the other capillaries", residuals[i]));
		}

		// Report the spread over the capillaries that follow the trend, so that a
		// detection failure is not mistaken for a huge distortion.
		double[] kept = retained.isEmpty() ? lengths : toArray(retained);
		result.setMedianPixels(percentile(kept, 50.));
		result.setMinPixels(min(kept));
		result.setMaxPixels(max(kept));
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

	/**
	 * Least-squares fit of length versus horizontal position: the linear term
	 * captures camera tilt, the quadratic term the radial lens effect. Returns the
	 * coefficients of {@code a + b*u + c*u^2}, or null when there are too few
	 * points.
	 */
	private static double[] fitLengthVersusPosition(List<CapillaryLengthResult.Measure> measures, int imageWidth) {
		int n = measures.size();
		int degree = n >= MIN_POINTS_FOR_QUADRATIC_FIT ? 2 : (n >= MIN_POINTS_FOR_LINEAR_FIT ? 1 : -1);
		if (degree < 0)
			return null;

		int nCoef = degree + 1;
		double[][] normal = new double[nCoef][nCoef + 1];
		for (CapillaryLengthResult.Measure m : measures) {
			double u = normalizeX(m.getCentroidX(), imageWidth);
			double y = m.getDetectedPixels();
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
		int nFrames = Math.max(1, options.nFramesAveraged);
		if (nTotalFrames > 0)
			nFrames = Math.min(nFrames, Math.max(1, nTotalFrames - first));

		SequenceLoaderService loader = new SequenceLoaderService();
		double[][] accumulator = null;
		int width = 0;
		int height = 0;
		int nAveraged = 0;

		for (int i = 0; i < nFrames; i++) {
			String path = seqCamData.getFileNameFromImageList(first + i);
			if (path == null)
				continue;
			IcyBufferedImage image = loader.imageIORead(path);
			if (image == null)
				continue;
			if (accumulator == null) {
				width = image.getSizeX();
				height = image.getSizeY();
				accumulator = new double[Math.max(1, Math.min(3, image.getSizeC()))][width * height];
			} else if (image.getSizeX() != width || image.getSizeY() != height) {
				Logger.warn("CapillaryLengthDetector: frame " + (first + i) + " has a different size, skipped");
				continue;
			}
			int nCh = Math.min(accumulator.length, image.getSizeC());
			for (int c = 0; c < nCh; c++) {
				double[] values = Array1DUtil.arrayToDoubleArray(image.getDataXY(c), image.isSignedDataType());
				for (int p = 0; p < accumulator[c].length && p < values.length; p++)
					accumulator[c][p] += values[p];
			}
			nAveraged++;
		}

		if (accumulator == null || nAveraged == 0)
			return null;
		if (nAveraged > 1) {
			for (int c = 0; c < accumulator.length; c++) {
				for (int p = 0; p < accumulator[c].length; p++)
					accumulator[c][p] /= nAveraged;
			}
		}
		return new ImageData(width, height, accumulator);
	}

	// === small statistics helpers ===

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
