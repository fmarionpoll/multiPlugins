package plugins.fmp.multitools.tools.toExcel.csv;

/**
 * Time-weighted piecewise-constant (step-hold) resampling onto a regular grid
 * starting at t=0. Each sample holds its value on {@code [t_k, t_{k+1})};
 * each output bin {@code [i*step, (i+1)*step)} gets the duration-weighted mean
 * of overlapping hold intervals.
 * <p>
 * NaN values in the input are forward-filled from the last known value so that
 * cumulated/staircase signals (e.g. consumption) produce continuous output even
 * when the native sampling is coarser than the bin grid or has missing frames.
 * Bins before the first finite sample stay {@link Double#NaN}.
 */
public final class CsvTimeWeightedResample {

	private CsvTimeWeightedResample() {
	}

	/**
	 * @param timesMs   sorted relative times (ms), length n
	 * @param values    parallel values, length n
	 * @param stepMs    bin width (ms), must be &gt; 0
	 * @return length {@code floor(tLast/step)+1}; bin i starts at {@code i*stepMs}
	 */
	public static double[] resampleOne(long[] timesMs, double[] values, long stepMs) {
		if (timesMs == null || values == null || stepMs <= 0 || timesMs.length == 0) {
			return new double[0];
		}
		int n = Math.min(timesMs.length, values.length);

		// Forward-fill NaN entries so cumulated/staircase signals have no gaps.
		double[] filled = new double[n];
		double last = Double.NaN;
		for (int k = 0; k < n; k++) {
			double v = values[k];
			if (!Double.isNaN(v)) {
				last = v;
			}
			filled[k] = last;
		}

		long tLast = timesMs[n - 1];
		int nBins = (int) (tLast / stepMs) + 1;
		if (nBins < 1) {
			nBins = 1;
		}
		double[] out = new double[nBins];
		double[] w = new double[nBins];
		for (int i = 0; i < nBins; i++) {
			out[i] = Double.NaN;
		}
		long tMax = (long) nBins * stepMs;
		for (int k = 0; k < n; k++) {
			long aSeg = timesMs[k];
			// Last sample holds to the end of its bin (at least one stepMs beyond).
			long bSeg = (k + 1 < n) ? timesMs[k + 1]
					: Math.max(aSeg + stepMs, tMax);
			if (bSeg <= aSeg || bSeg <= 0 || aSeg >= tMax) {
				continue;
			}
			double v = filled[k];
			if (Double.isNaN(v)) {
				continue;
			}
			int i0 = (int) Math.max(0, aSeg / stepMs);
			int i1 = (int) Math.min(nBins - 1, (bSeg - 1) / stepMs);
			for (int i = i0; i <= i1; i++) {
				long left = Math.max(i * stepMs, aSeg);
				long right = Math.min((i + 1L) * stepMs, bSeg);
				double overlap = right - left;
				if (overlap <= 0) {
					continue;
				}
				if (Double.isNaN(out[i])) {
					out[i] = 0.0;
					w[i] = 0.0;
				}
				out[i] += v * overlap;
				w[i] += overlap;
			}
		}
		for (int i = 0; i < nBins; i++) {
			if (w[i] > 0) {
				out[i] /= w[i];
			} else {
				out[i] = Double.NaN;
			}
		}
		return out;
	}

	/** Bin start times in ms for a resampled series of length {@code nBins}. */
	public static long[] binStartsMs(int nBins, long stepMs) {
		long[] starts = new long[Math.max(0, nBins)];
		for (int i = 0; i < starts.length; i++) {
			starts[i] = i * stepMs;
		}
		return starts;
	}

	/**
	 * Resample several value columns that share the same time base.
	 *
	 * @return {@code [col][bin]}
	 */
	public static double[][] resampleColumns(long[] timesMs, double[][] columns, long stepMs) {
		if (columns == null || columns.length == 0) {
			return new double[0][];
		}
		double[][] out = new double[columns.length][];
		for (int c = 0; c < columns.length; c++) {
			out[c] = resampleOne(timesMs, columns[c], stepMs);
		}
		return out;
	}
}
