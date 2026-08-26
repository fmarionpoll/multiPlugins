package plugins.fmp.multitools.service;

import java.util.Arrays;

/**
 * 1D post-filters for meniscus Y(t): odd median then spike clamp.
 */
public final class LevelSeriesSmoother {

	private LevelSeriesSmoother() {
	}

	/**
	 * Applies median (if window &gt; 1) then spike clamp (if maxSpikePx &gt; 0) in place.
	 */
	public static void smooth(int[] y, int medianWindow, int maxSpikePx) {
		if (y == null || y.length == 0)
			return;
		if (medianWindow > 1)
			medianFilterInPlace(y, medianWindow);
		if (maxSpikePx > 0)
			spikeClampInPlace(y, maxSpikePx);
	}

	static void medianFilterInPlace(int[] y, int window) {
		int w = window;
		if ((w & 1) == 0)
			w++;
		if (w < 3 || y.length < 2)
			return;
		int half = w / 2;
		int[] src = Arrays.copyOf(y, y.length);
		int[] buf = new int[w];
		for (int i = 0; i < y.length; i++) {
			int n = 0;
			for (int k = -half; k <= half; k++) {
				int j = i + k;
				if (j < 0)
					j = 0;
				else if (j >= src.length)
					j = src.length - 1;
				buf[n++] = src[j];
			}
			Arrays.sort(buf, 0, n);
			y[i] = buf[n / 2];
		}
	}

	/**
	 * If |y[i] − mean(y[i−1], y[i+1])| &gt; maxSpikePx, replace y[i] with that mean.
	 */
	static void spikeClampInPlace(int[] y, int maxSpikePx) {
		if (y.length < 3 || maxSpikePx <= 0)
			return;
		int[] src = Arrays.copyOf(y, y.length);
		for (int i = 1; i < y.length - 1; i++) {
			int neighborMean = (src[i - 1] + src[i + 1]) / 2;
			if (Math.abs(src[i] - neighborMean) > maxSpikePx)
				y[i] = neighborMean;
		}
	}
}
