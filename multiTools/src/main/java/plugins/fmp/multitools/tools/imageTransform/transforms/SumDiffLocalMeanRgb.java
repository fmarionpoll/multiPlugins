package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Chromatic contrast after removing a slowly varying local illumination estimate:
 * for each RGB channel, subtracts the channel's local spatial mean (square box filter),
 * then forms the same scalar as {@link SumDiff}: {@code |R-μR| + |G-μG| + |B-μB|}.
 * <p>
 * Box half-width is {@code clamp(min(w,h)/20, 6, 96)} pixels (adaptive to image size).
 */
public class SumDiffLocalMeanRgb extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	public SumDiffLocalMeanRgb() {
	}

	/** Box half-width in pixels: {@code clamp(min(w,h)/20, 6, 96)} (shared with ROI-masked path). */
	public static int defaultBoxHalfWidth(int w, int h) {
		int m = Math.min(w, h);
		return Math.min(96, Math.max(6, m / 20));
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		return getTransformedImage(sourceImage, options, null);
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options,
			IcyBufferedImage reuseBuffer) {
		if (sourceImage == null || sourceImage.getSizeC() < 3) {
			return null;
		}
		final int w = sourceImage.getWidth();
		final int h = sourceImage.getHeight();
		final int n = w * h;
		IcyBufferedImage img2 = getResultImageOrReuse(w, h, 3, sourceImage.getDataType_(), reuseBuffer);

		int[] Rn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] Gn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		int[] Bn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());

		final int r = defaultBoxHalfWidth(w, h);

		int[] meanR = new int[n];
		int[] meanG = new int[n];
		int[] meanB = new int[n];

		final int Wy = w + 1;
		final int satLen = (h + 1) * Wy;
		long[] sat = new long[satLen];

		localMeansFromIntegral(Rn, meanR, w, h, r, sat, Wy);
		localMeansFromIntegral(Gn, meanG, w, h, r, sat, Wy);
		localMeansFromIntegral(Bn, meanB, w, h, r, sat, Wy);

		int[] out = (int[]) Array1DUtil.createArray(DataType.INT, n);
		for (int i = 0; i < n; i++) {
			int d1 = Math.abs(Rn[i] - meanR[i]);
			int d2 = Math.abs(Gn[i] - meanG[i]);
			int d3 = Math.abs(Bn[i] - meanB[i]);
			out[i] = d1 + d2 + d3;
		}

		copyExGIntToIcyBufferedImage(out, img2, options.copyResultsToThe3planes);
		return img2;
	}

	private static void localMeansFromIntegral(int[] src, int[] meanOut, int w, int h, int r, long[] sat, int Wy) {
		buildSat(src, sat, w, h, Wy);
		for (int y = 0; y < h; y++) {
			int yl = Math.max(0, y - r);
			int yr = Math.min(h - 1, y + r);
			int row = y * w;
			for (int x = 0; x < w; x++) {
				int xl = Math.max(0, x - r);
				int xr = Math.min(w - 1, x + r);
				long sum = rectSum(sat, Wy, xl, yl, xr, yr);
				int cw = xr - xl + 1;
				int ch = yr - yl + 1;
				int count = cw * ch;
				meanOut[row + x] = count > 0 ? (int) (sum / count) : src[row + x];
			}
		}
	}

	private static void buildSat(int[] pix, long[] sat, int w, int h, int Wy) {
		final int hp1 = h + 1;
		for (int y = 0; y < hp1; y++) {
			sat[y * Wy] = 0;
		}
		for (int x = 0; x < Wy; x++) {
			sat[x] = 0;
		}
		for (int y = 0; y < h; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				long v = pix[row + x];
				int idx = (y + 1) * Wy + (x + 1);
				sat[idx] = v + sat[idx - 1] + sat[idx - Wy] - sat[idx - Wy - 1];
			}
		}
	}

	/** Inclusive rectangle sum in original pixel coordinates (0 .. w-1, 0 .. h-1). */
	private static long rectSum(long[] sat, int Wy, int xl, int yl, int xr, int yr) {
		return sat[(yr + 1) * Wy + (xr + 1)] - sat[yl * Wy + (xr + 1)] - sat[(yr + 1) * Wy + xl] + sat[yl * Wy + xl];
	}
}
