package plugins.fmp.multitools.tools.imageTransform.transforms;

import java.awt.Color;
import java.util.ArrayList;
import java.util.stream.IntStream;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.RgbColorKdTree3;
import plugins.fmp.multitools.tools.imageTransform.SpotThresholdColorSpace;

public class ThresholdColors extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	private static final int PARALLEL_MIN_PIXELS = 48 * 48;
	private static final int KD_TREE_MIN_REFS = 12;

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (options == null || options.colorarray == null || options.colorarray.isEmpty()) {
			return null;
		}
		if (sourceImage == null) {
			return null;
		}
		if (sourceImage.getSizeC() < 3) {
			System.out.print(
					"Failed operation: attempt to compute threshold from image with less than 3 color channels");
			return null;
		}

		SpotThresholdColorSpace space = options.thresholdColorSpace != null ? options.thresholdColorSpace
				: SpotThresholdColorSpace.RGB;

		int width = sourceImage.getSizeX();
		int height = sourceImage.getSizeY();
		int npixels = width * height;

		IcyBufferedImage binaryResultBuffer = new IcyBufferedImage(width, height, 1, DataType.UBYTE);
		IcyBufferedImage dummy = sourceImage;
		if (sourceImage.getDataType_() == DataType.DOUBLE) {
			dummy = IcyBufferedImageUtil.convertToType(sourceImage, DataType.BYTE, false);
		}
		byte[][] sourceBuffer = dummy.getDataXYCAsByte();
		byte[] binaryResultArray = binaryResultBuffer.getDataXYAsByte(0);

		if (space == SpotThresholdColorSpace.RGB) {
			fillBinaryRgbDistance(sourceBuffer, binaryResultArray, width, height, npixels, options);
		} else {
			fillBinaryGeneralSpace(sourceBuffer, binaryResultArray, width, height, npixels, options, space);
		}
		return binaryResultBuffer;
	}

	private static void fillBinaryRgbDistance(byte[][] sourceBuffer, byte[] binaryResultArray, int width, int height,
			int npixels, CanvasImageTransformOptions options) {
		ArrayList<Color> refs = options.colorarray;
		int n = refs.size();
		int[] rr = new int[n];
		int[] gg = new int[n];
		int[] bb = new int[n];
		for (int k = 0; k < n; k++) {
			Color c = refs.get(k);
			rr[k] = c.getRed();
			gg[k] = c.getGreen();
			bb[k] = c.getBlue();
		}
		final int thr = options.colorthreshold;
		final boolean l1 = options.colordistanceType == 1;
		final long thr2 = (long) thr * (long) thr;

		ArrayList<Color> exList = options.colorExcludeArray;
		final int nex = (exList != null) ? exList.size() : 0;
		final int tex = options.colorExcludeThreshold;
		final boolean useExclude = nex > 0 && tex > 0;
		int[] er = null;
		int[] eg = null;
		int[] eb = null;
		if (useExclude) {
			er = new int[nex];
			eg = new int[nex];
			eb = new int[nex];
			for (int k = 0; k < nex; k++) {
				Color c = exList.get(k);
				er[k] = c.getRed();
				eg[k] = c.getGreen();
				eb[k] = c.getBlue();
			}
		}

		RgbColorKdTree3 kd = null;
		if (!l1 && n >= KD_TREE_MIN_REFS) {
			kd = RgbColorKdTree3.build(rr, gg, bb);
		}

		final int[] frr = rr;
		final int[] fgg = gg;
		final int[] fbb = bb;
		final int[] fer = er;
		final int[] feg = eg;
		final int[] feb = eb;
		final RgbColorKdTree3 fkd = kd;

		if (npixels >= PARALLEL_MIN_PIXELS) {
			IntStream.range(0, height).parallel().forEach(y -> fillRgbRow(sourceBuffer, binaryResultArray, width, y,
					frr, fgg, fbb, n, thr, thr2, l1, fkd, useExclude, fer, feg, feb, nex, tex, options));
		} else {
			for (int y = 0; y < height; y++) {
				fillRgbRow(sourceBuffer, binaryResultArray, width, y, frr, fgg, fbb, n, thr, thr2, l1, fkd, useExclude,
						fer, feg, feb, nex, tex, options);
			}
		}
	}

	private static void fillRgbRow(byte[][] sourceBuffer, byte[] out, int width, int y, int[] rr, int[] gg, int[] bb,
			int n, int thr, long thr2, boolean l1, RgbColorKdTree3 kd, boolean useExclude, int[] er, int[] eg, int[] eb,
			int nex, int tex, CanvasImageTransformOptions options) {
		int row = y * width;
		byte t = options.byteTRUE;
		byte f = options.byteFALSE;
		for (int x = 0; x < width; x++) {
			int i = row + x;
			int r = sourceBuffer[0][i] & 0xFF;
			int g = sourceBuffer[1][i] & 0xFF;
			int b = sourceBuffer[2][i] & 0xFF;
			boolean nearInc = rgbWithinUnion(r, g, b, rr, gg, bb, n, thr, thr2, l1, kd);
			boolean nearExc = useExclude && rgbWithinUnion(r, g, b, er, eg, eb, nex, tex, (long) tex * tex, l1, null);
			boolean match = nearInc && !nearExc;
			out[i] = match ? f : t;
		}
	}

	private static boolean rgbWithinUnion(int r, int g, int b, int[] rr, int[] gg, int[] bb, int n, int thr, long thr2,
			boolean l1, RgbColorKdTree3 kd) {
		if (n <= 0) {
			return false;
		}
		if (!l1 && kd != null) {
			return kd.nearestDistanceSquared(r, g, b) <= thr2;
		}
		if (l1) {
			for (int k = 0; k < n; k++) {
				int d = Math.abs(r - rr[k]) + Math.abs(g - gg[k]) + Math.abs(b - bb[k]);
				if (d <= thr) {
					return true;
				}
			}
			return false;
		}
		for (int k = 0; k < n; k++) {
			long dr = r - rr[k];
			long dg = g - gg[k];
			long db = b - bb[k];
			if (dr * dr + dg * dg + db * db <= thr2) {
				return true;
			}
		}
		return false;
	}

	private static void fillBinaryGeneralSpace(byte[][] sourceBuffer, byte[] binaryResultArray, int width, int height,
			int npixels, CanvasImageTransformOptions options, SpotThresholdColorSpace space) {
		boolean l1 = options.colordistanceType == 1;
		int nref = options.colorarray.size();
		double[][] refTriples = new double[nref][3];
		for (int k = 0; k < nref; k++) {
			Color c = options.colorarray.get(k);
			rgbToSpaceTriple(c.getRed(), c.getGreen(), c.getBlue(), space, refTriples[k]);
		}
		final int thr = options.colorthreshold;

		ArrayList<Color> exList = options.colorExcludeArray;
		final int nex = (exList != null) ? exList.size() : 0;
		final int tex = options.colorExcludeThreshold;
		final boolean useExclude = nex > 0 && tex > 0;
		double[][] exTriples = null;
		if (useExclude) {
			exTriples = new double[nex][3];
			for (int k = 0; k < nex; k++) {
				Color c = exList.get(k);
				rgbToSpaceTriple(c.getRed(), c.getGreen(), c.getBlue(), space, exTriples[k]);
			}
		}

		final double[][] fref = refTriples;
		final double[][] fex = exTriples;
		final boolean fl1 = l1;
		final SpotThresholdColorSpace fsp = space;

		if (npixels >= PARALLEL_MIN_PIXELS) {
			IntStream.range(0, height).parallel().forEach(y -> fillGeneralRow(sourceBuffer, binaryResultArray, width, y,
					fref, nref, thr, fl1, fsp, useExclude, fex, nex, tex, options));
		} else {
			for (int y = 0; y < height; y++) {
				fillGeneralRow(sourceBuffer, binaryResultArray, width, y, fref, nref, thr, fl1, fsp, useExclude, fex,
						nex, tex, options);
			}
		}
	}

	private static void fillGeneralRow(byte[][] sourceBuffer, byte[] out, int width, int y, double[][] refTriples,
			int nref, int thr, boolean l1, SpotThresholdColorSpace space, boolean useExclude, double[][] exTriples,
			int nex, int tex, CanvasImageTransformOptions options) {
		double[] px = new double[3];
		int row = y * width;
		byte t = options.byteTRUE;
		byte f = options.byteFALSE;
		for (int x = 0; x < width; x++) {
			int i = row + x;
			int r = sourceBuffer[0][i] & 0xFF;
			int g = sourceBuffer[1][i] & 0xFF;
			int b = sourceBuffer[2][i] & 0xFF;
			rgbToSpaceTriple(r, g, b, space, px);
			boolean nearInc = withinUnionTriple(px, refTriples, nref, thr, l1, space);
			boolean nearExc = useExclude && withinUnionTriple(px, exTriples, nex, tex, l1, space);
			boolean match = nearInc && !nearExc;
			out[i] = match ? f : t;
		}
	}

	private static boolean withinUnionTriple(double[] px, double[][] refs, int n, int thr, boolean l1,
			SpotThresholdColorSpace space) {
		for (int k = 0; k < n; k++) {
			double d = l1 ? l1Triple(px, refs[k], space) : l2Triple(px, refs[k], space);
			if (d <= thr) {
				return true;
			}
		}
		return false;
	}

	private static void rgbToSpaceTriple(int r, int g, int b, SpotThresholdColorSpace space, double[] out) {
		switch (space) {
			case HSV:
				float[] hsb = Color.RGBtoHSB(clamp255(r), clamp255(g), clamp255(b), null);
				out[0] = hsb[0] * 255.0;
				out[1] = hsb[1] * 255.0;
				out[2] = hsb[2] * 255.0;
				break;
			case H1H2H3:
				h1h2h3FromRgb(r, g, b, out);
				break;
			case RGB:
			default:
				out[0] = r;
				out[1] = g;
				out[2] = b;
				break;
		}
	}

	private static void h1h2h3FromRgb(int r, int g, int b, double[] out) {
		final double VMAX = 255.0;
		double rd = clamp255(r);
		double gd = clamp255(g);
		double bd = clamp255(b);
		out[0] = (rd + gd) / 2.0;
		out[1] = (VMAX + rd - gd) / 2.0;
		out[2] = (VMAX + bd - (rd + gd) / 2.0) / 2.0;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static double l1Triple(double[] a, double[] b, SpotThresholdColorSpace space) {
		double d0 = componentDiff(a[0], b[0], space == SpotThresholdColorSpace.HSV);
		return d0 + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
	}

	private static double l2Triple(double[] a, double[] b, SpotThresholdColorSpace space) {
		double d0 = componentDiff(a[0], b[0], space == SpotThresholdColorSpace.HSV);
		double d1 = a[1] - b[1];
		double d2 = a[2] - b[2];
		return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	private static double componentDiff(double x, double y, boolean circularHue) {
		if (!circularHue) {
			return Math.abs(x - y);
		}
		double d = Math.abs(x - y);
		return Math.min(d, 255.0 - d);
	}
}
