package plugins.fmp.multitools.tools.imageTransform.transforms;

/**
 * Per-spot variant of {@link SumDiffLocalMeanRgb}: at each mask pixel, μ for each RGB channel is the
 * mean over the intersection of the square {@code (x\pm r, y\pm r)} with the spot disk (mask pixels only).
 * The scalar is {@code |R-\mu_R|+|G-\mu_G|+|B-\mu_B|} as in {@link SumDiff}.
 */
public final class RoiMaskedLocalSumDiffRgb {

	private RoiMaskedLocalSumDiffRgb() {
	}

	/**
	 * @param w full image width, h full image height
	 * @param Rn Gn Bn packed row-major length {@code w*h}
	 * @param maskX maskY ROI pixels in image coordinates (inclusive)
	 * @return one value per mask point, same indexing as {@code maskX}/{@code maskY}, or {@code null} if unusable
	 */
	public static int[] computeScalarsForMask(int w, int h, int[] Rn, int[] Gn, int[] Bn, int[] maskX, int[] maskY,
			int r) {
		if (w <= 0 || h <= 0 || Rn == null || Gn == null || Bn == null || Rn.length < w * h || Gn.length < w * h
				|| Bn.length < w * h) {
			return null;
		}
		if (maskX == null || maskY == null || maskX.length == 0 || maskX.length != maskY.length) {
			return null;
		}

		int xmin = w;
		int xmax = -1;
		int ymin = h;
		int ymax = -1;
		for (int i = 0; i < maskX.length; i++) {
			int x = maskX[i];
			int y = maskY[i];
			if (x < 0 || y < 0 || x >= w || y >= h) {
				continue;
			}
			xmin = Math.min(xmin, x);
			xmax = Math.max(xmax, x);
			ymin = Math.min(ymin, y);
			ymax = Math.max(ymax, y);
		}
		if (xmax < 0) {
			return null;
		}

		xmin = Math.max(0, xmin - r);
		xmax = Math.min(w - 1, xmax + r);
		ymin = Math.max(0, ymin - r);
		ymax = Math.min(h - 1, ymax + r);
		final int cw = xmax - xmin + 1;
		final int ch = ymax - ymin + 1;
		final int nCrop = cw * ch;

		final byte[] maskCrop = new byte[nCrop];
		for (int i = 0; i < maskX.length; i++) {
			int gx = maskX[i];
			int gy = maskY[i];
			if (gx < 0 || gy < 0 || gx >= w || gy >= h) {
				continue;
			}
			if (gx < xmin || gx > xmax || gy < ymin || gy > ymax) {
				continue;
			}
			int cx = gx - xmin;
			int cy = gy - ymin;
			maskCrop[cy * cw + cx] = 1;
		}

		final int[] RC = new int[nCrop];
		final int[] GC = new int[nCrop];
		final int[] BC = new int[nCrop];
		for (int cy = 0; cy < ch; cy++) {
			int gy = ymin + cy;
			int srcRow = gy * w + xmin;
			int dstRow = cy * cw;
			System.arraycopy(Rn, srcRow, RC, dstRow, cw);
			System.arraycopy(Gn, srcRow, GC, dstRow, cw);
			System.arraycopy(Bn, srcRow, BC, dstRow, cw);
		}

		final int Wy = cw + 1;
		final int hp1 = ch + 1;
		final int satLen = hp1 * Wy;
		final long[] satM = new long[satLen];
		final long[] satRM = new long[satLen];
		final long[] satGM = new long[satLen];
		final long[] satBM = new long[satLen];
		buildMaskedSats(maskCrop, RC, GC, BC, cw, ch, Wy, satM, satRM, satGM, satBM);

		final int[] out = new int[maskX.length];
		for (int i = 0; i < maskX.length; i++) {
			int gx = maskX[i];
			int gy = maskY[i];
			if (gx < 0 || gy < 0 || gx >= w || gy >= h) {
				out[i] = 0;
				continue;
			}
			int cx = gx - xmin;
			int cy = gy - ymin;
			int xl = Math.max(0, cx - r);
			int xr = Math.min(cw - 1, cx + r);
			int yl = Math.max(0, cy - r);
			int yr = Math.min(ch - 1, cy + r);

			long n = rectSum(satM, Wy, xl, yl, xr, yr);
			int idxC = cy * cw + cx;
			int rPix = RC[idxC];
			int gPix = GC[idxC];
			int bPix = BC[idxC];
			if (n <= 0) {
				out[i] = 0;
				continue;
			}
			int muR = (int) (rectSum(satRM, Wy, xl, yl, xr, yr) / n);
			int muG = (int) (rectSum(satGM, Wy, xl, yl, xr, yr) / n);
			int muB = (int) (rectSum(satBM, Wy, xl, yl, xr, yr) / n);
			out[i] = Math.abs(rPix - muR) + Math.abs(gPix - muG) + Math.abs(bPix - muB);
		}
		return out;
	}

	private static void buildMaskedSats(byte[] mask, int[] R, int[] G, int[] B, int cw, int ch, int Wy, long[] satM,
			long[] satRM, long[] satGM, long[] satBM) {
		final int hp1 = ch + 1;
		for (int y = 0; y < hp1; y++) {
			int row = y * Wy;
			for (int x = 0; x < Wy; x++) {
				satM[row + x] = 0;
				satRM[row + x] = 0;
				satGM[row + x] = 0;
				satBM[row + x] = 0;
			}
		}
		for (int y = 0; y < ch; y++) {
			int row = y * cw;
			for (int x = 0; x < cw; x++) {
				int idx = row + x;
				int m = mask[idx] & 0xff;
				long rm = (long) R[idx] * m;
				long gm = (long) G[idx] * m;
				long bm = (long) B[idx] * m;
				int i2 = (y + 1) * Wy + (x + 1);
				satM[i2] = m + satM[i2 - 1] + satM[i2 - Wy] - satM[i2 - Wy - 1];
				satRM[i2] = rm + satRM[i2 - 1] + satRM[i2 - Wy] - satRM[i2 - Wy - 1];
				satGM[i2] = gm + satGM[i2 - 1] + satGM[i2 - Wy] - satGM[i2 - Wy - 1];
				satBM[i2] = bm + satBM[i2 - 1] + satBM[i2 - Wy] - satBM[i2 - Wy - 1];
			}
		}
	}

	private static long rectSum(long[] sat, int Wy, int xl, int yl, int xr, int yr) {
		return sat[(yr + 1) * Wy + (xr + 1)] - sat[yl * Wy + (xr + 1)] - sat[(yr + 1) * Wy + xl] + sat[yl * Wy + xl];
	}
}
