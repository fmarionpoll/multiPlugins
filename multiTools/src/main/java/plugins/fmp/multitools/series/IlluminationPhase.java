package plugins.fmp.multitools.series;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

/**
 * Helper for dual-background fly detection (light/dark phase).
 * Encodes the selected background as a small integer so it can be stored alongside
 * fly-position measures.
 */
public final class IlluminationPhase {
	/** Unknown / not applicable. */
	public static final int UNKNOWN = -1;
	/** Light reference selected. */
	public static final int LIGHT = 0;
	/** Dark reference selected. */
	public static final int DARK = 1;

	private IlluminationPhase() {
	}

	/**
	 * Returns LIGHT or DARK depending on redness threshold.
	 * Returns UNKNOWN if the image is null or not RGB.
	 */
	/**
	 * Phase stored with fly positions when dual-background mode is on; otherwise {@link #UNKNOWN}.
	 */
	public static int phaseForFlyDetection(BuildSeriesOptions options, IcyBufferedImage workImage) {
		if (options == null || !options.dualBackground) {
			return UNKNOWN;
		}
		return fromFrameForDualBackground(workImage, options.rednessThreshold);
	}

	public static int fromFrameForDualBackground(IcyBufferedImage img, double rednessThreshold) {
		if (img == null) {
			return UNKNOWN;
		}
		double r = computeRednessRatio(img, 16);
		if (r <= 0.0) {
			return UNKNOWN;
		}
		return (r >= rednessThreshold) ? DARK : LIGHT;
	}

	/**
	 * Computes average per-pixel redness ratio: r / (r+g+b).
	 * Sampling step reduces cost on large frames.
	 */
	public static double computeRednessRatio(IcyBufferedImage img, int step) {
		if (img == null) {
			return 0.0;
		}
		int w = img.getSizeX();
		int h = img.getSizeY();
		int c = img.getSizeC();
		if (c < 3) {
			return 0.0;
		}
		if (step < 1) {
			step = 1;
		}

		double sum = 0.0;
		int n = 0;
		IcyBufferedImageCursor cur = new IcyBufferedImageCursor(img);
		for (int y = 0; y < h; y += step) {
			for (int x = 0; x < w; x += step) {
				double rr = cur.get(x, y, 0);
				double gg = cur.get(x, y, 1);
				double bb = cur.get(x, y, 2);
				double denom = rr + gg + bb + 1e-9;
				sum += (rr / denom);
				n++;
			}
		}
		return n > 0 ? (sum / n) : 0.0;
	}
}
