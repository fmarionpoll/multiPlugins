package plugins.fmp.multitools.tools.toExcel.csv;

/**
 * Time-weighted piecewise-constant resampling onto a regular grid starting at
 * t=0. Value is constant on {@code [t_k, t_{k+1})}; each bin
 * {@code [i*step, (i+1)*step)} gets the duration-weighted mean of overlapping
 * segments. Empty bins stay {@link Double#NaN}.
 */
public final class CsvTimeWeightedResample {

	private CsvTimeWeightedResample() {
	}

	/**
	 * @param timesMs   sorted relative times (ms), length n
	 * @param values    parallel values, length n (NaN skipped in weights)
	 * @param stepMs    bin width (ms), must be &gt; 0
	 * @return length {@code floor(tLast/step)+1}; bin i starts at {@code i*stepMs}
	 */
	public static double[] resampleOne(long[] timesMs, double[] values, long stepMs) {
		if (timesMs == null || values == null || stepMs <= 0 || timesMs.length == 0) {
			return new double[0];
		}
		int n = Math.min(timesMs.length, values.length);
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
			long bSeg = (k + 1 < n) ? timesMs[k + 1] : Math.max(timesMs[n - 1], tMax);
			if (bSeg <= aSeg || bSeg <= 0 || aSeg >= tMax) {
				continue;
			}
			double v = values[k];
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
