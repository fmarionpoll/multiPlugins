package plugins.fmp.multitools.service;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CancellationException;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;

/** Correct vertical camera motion using a stationary feature visible across time. */
public final class KymographAlignment {
	private static final int SEARCH_RADIUS = 8;

	public enum Target {
		HORIZONTAL("Horizontal reference"), STRAIGHT("Sloping straight reference");

		private final String label;

		Target(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static final class Result {
		public final double[] tracked;
		public final double[] target;
		public final int matchedColumns;
		public final double maxShift;
		private final double[] shifts;

		private Result(double[] tracked, double[] target, int matchedColumns, double[] shifts) {
			this.tracked = tracked;
			this.target = target;
			this.matchedColumns = matchedColumns;
			this.shifts = shifts;
			double largest = 0;
			for (double shift : shifts)
				largest = Math.max(largest, Math.abs(shift));
			this.maxShift = largest;
		}
	}

	private KymographAlignment() {
	}

	public static Result analyze(BufferedImage image, Map<Integer, Double> anchors, int first, int last, Target mode) {
		return analyze(image, anchors, first, last, mode, true);
	}

	public static Result analyze(BufferedImage image, Map<Integer, Double> anchors, int first, int last, Target mode,
			boolean useNearbyEdges) {
		if (first < 0 || last <= first || last >= image.getWidth())
			throw new IllegalArgumentException("Select a range of at least two image columns.");

		double[] tracked = MeniscusReferenceTracking.trackFixedFeature(image, anchors, first, last, SEARCH_RADIUS,
				useNearbyEdges);
		double[] valid = new double[last - first + 1];
		int count = 0;
		double sumX = 0;
		double sumY = 0;
		for (int x = first; x <= last; x++) {
			if (Double.isFinite(tracked[x])) {
				valid[count++] = tracked[x];
				sumX += x;
				sumY += tracked[x];
			}
		}
		if (count < 2)
			throw new IllegalArgumentException("Only one column matched. Try another reference point or range.");

		double meanX = sumX / count;
		double center;
		double slope = 0;
		if (mode == Target.HORIZONTAL) {
			Arrays.sort(valid, 0, count);
			center = (valid[(count - 1) / 2] + valid[count / 2]) / 2;
		} else {
			center = sumY / count;
			double variance = 0;
			double covariance = 0;
			for (int x = first; x <= last; x++) {
				if (Double.isFinite(tracked[x])) {
					double dx = x - meanX;
					variance += dx * dx;
					covariance += dx * (tracked[x] - center);
				}
			}
			slope = covariance / variance;
		}

		double[] target = new double[tracked.length];
		Arrays.fill(target, Double.NaN);
		double[] shifts = new double[tracked.length];
		for (int x = first; x <= last; x++) {
			target[x] = center + slope * (x - meanX);
			if (Double.isFinite(tracked[x]))
				shifts[x] = target[x] - tracked[x];
		}
		return new Result(tracked, target, count, shifts);
	}

	public static Result addManualShifts(Result reference, double[] rangeShifts, NavigableMap<Integer, Double> curve) {
		double[] shifts = rangeShifts.clone();
		if (reference != null) {
			for (int x = 0; x < shifts.length; x++)
				shifts[x] += reference.shifts[x];
		}
		Map.Entry<Integer, Double> previous = null;
		for (Map.Entry<Integer, Double> point : curve.entrySet()) {
			int end = point.getKey();
			shifts[end] += point.getValue();
			if (previous != null) {
				int start = previous.getKey();
				for (int x = start + 1; x < end; x++) {
					double fraction = (x - start) / (double) (end - start);
					shifts[x] += previous.getValue() + fraction * (point.getValue() - previous.getValue());
				}
			}
			previous = point;
		}
		return reference == null ? new Result(null, null, 0, shifts)
				: new Result(reference.tracked, reference.target, reference.matchedColumns, shifts);
	}

	/** Resample each shifted column vertically in its native pixel type. */
	public static IcyBufferedImage align(IcyBufferedImage source, Result alignment) {
		int width = source.getSizeX();
		int height = source.getSizeY();
		if (width != alignment.shifts.length)
			throw new IllegalArgumentException("The alignment width differs from the image width.");
		IcyBufferedImage output = IcyBufferedImageUtil.getCopy(source);
		output.setColorMaps(source);
		DataType type = source.getDataType_();
		output.lockRaster();
		try {
			for (int c = 0; c < source.getSizeC(); c++) {
				Object from = source.getDataXY(c);
				Object to = output.getDataXY(c);
				for (int x = 0; x < width; x++) {
					if (Thread.currentThread().isInterrupted()) throw new CancellationException();
					double offset = -alignment.shifts[x];
					if (Math.abs(offset) < 1e-9)
						continue;
					for (int y = 0; y < height; y++) {
						// Extend the nearest edge pixel where the shift exposes a row.
						double sampleY = Math.max(0, Math.min(height - 1, y + offset));
						int low = (int) sampleY;
						int high = Math.min(low + 1, height - 1);
						double a = Array1DUtil.getValue(from, low * width + x, type);
						double weight = sampleY - low;
						double value = weight == 0 ? a
								: a + weight * (Array1DUtil.getValue(from, high * width + x, type) - a);
						int index = y * width + x;
						if (type == DataType.UINT)
							((int[]) to)[index] = (int) Math.round(value);
						else
							Array1DUtil.setValue(to, index, type, type.isInteger() ? Math.rint(value) : value);
					}
				}
			}
		} finally {
			output.releaseRaster(true);
		}
		return output;
	}
}
