package plugins.fmp.multitools.tools.toExcel;

/**
 * Compare requested export period T to native median Delta-t (+/-1 s) and regroup
 * samples by real timestamps when T is coarser.
 */
public final class ExportTimePolicy {

	public static final long TOLERANCE_MS = 1000L;

	public enum Relation {
		NATIVE, COARSER, FINER
	}

	private ExportTimePolicy() {
	}

	public static Relation relation(long requestedStepMs, long nativeMedianDeltaMs) {
		if (requestedStepMs <= 0 || nativeMedianDeltaMs <= 0) {
			return Relation.NATIVE;
		}
		long diff = requestedStepMs - nativeMedianDeltaMs;
		if (Math.abs(diff) <= TOLERANCE_MS) {
			return Relation.NATIVE;
		}
		return diff > 0 ? Relation.COARSER : Relation.FINER;
	}

	public static double[] regroupSum(long[] relativeMs, double[] values, long stepMs) {
		if (relativeMs == null || values == null || stepMs <= 0 || relativeMs.length == 0) {
			return new double[0];
		}
		int n = Math.min(relativeMs.length, values.length);
		long t0 = relativeMs[0];
		long last = relativeMs[n - 1];
		int nBins = (int) ((last - t0) / stepMs) + 1;
		if (nBins < 1) {
			nBins = 1;
		}
		double[] out = new double[nBins];
		boolean[] any = new boolean[nBins];
		for (int i = 0; i < nBins; i++) {
			out[i] = Double.NaN;
		}
		for (int i = 0; i < n; i++) {
			double v = values[i];
			if (Double.isNaN(v)) {
				continue;
			}
			int bin = (int) ((relativeMs[i] - t0) / stepMs);
			if (bin < 0 || bin >= nBins) {
				continue;
			}
			if (!any[bin]) {
				out[bin] = v;
				any[bin] = true;
			} else {
				out[bin] += v;
			}
		}
		return out;
	}

	public static double[] regroupPresence(long[] relativeMs, double[] values, long stepMs) {
		if (relativeMs == null || values == null || stepMs <= 0 || relativeMs.length == 0) {
			return new double[0];
		}
		int n = Math.min(relativeMs.length, values.length);
		long t0 = relativeMs[0];
		long last = relativeMs[n - 1];
		int nBins = (int) ((last - t0) / stepMs) + 1;
		if (nBins < 1) {
			nBins = 1;
		}
		double[] out = new double[nBins];
		for (int i = 0; i < nBins; i++) {
			out[i] = Double.NaN;
		}
		for (int i = 0; i < n; i++) {
			double v = values[i];
			if (Double.isNaN(v) || v == 0.0) {
				continue;
			}
			int bin = (int) ((relativeMs[i] - t0) / stepMs);
			if (bin < 0 || bin >= nBins) {
				continue;
			}
			out[bin] = 1.0;
		}
		return out;
	}

	public static double[] regroupHoldLast(long[] relativeMs, double[] values, long stepMs) {
		if (relativeMs == null || values == null || stepMs <= 0 || relativeMs.length == 0) {
			return new double[0];
		}
		int n = Math.min(relativeMs.length, values.length);
		long t0 = relativeMs[0];
		long last = relativeMs[n - 1];
		int nBins = (int) ((last - t0) / stepMs) + 1;
		if (nBins < 1) {
			nBins = 1;
		}
		double[] out = new double[nBins];
		for (int i = 0; i < nBins; i++) {
			out[i] = Double.NaN;
		}
		for (int i = 0; i < n; i++) {
			double v = values[i];
			if (Double.isNaN(v)) {
				continue;
			}
			int bin = (int) ((relativeMs[i] - t0) / stepMs);
			if (bin < 0 || bin >= nBins) {
				continue;
			}
			out[bin] = v;
		}
		return out;
	}

	public static long[] binCentersMs(long t0, int nBins, long stepMs) {
		long[] centers = new long[nBins];
		for (int i = 0; i < nBins; i++) {
			centers[i] = t0 + i * stepMs + stepMs / 2;
		}
		return centers;
	}
}
