package plugins.fmp.multitools.tools.imageTransform.transforms;

import java.awt.Color;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import plugins.fmp.multitools.tools.NHDistance.NHDistanceColor;
import plugins.fmp.multitools.tools.NHDistance.NHDistanceColorL1;
import plugins.fmp.multitools.tools.NHDistance.NHDistanceColorL2;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.SpotThresholdColorSpace;

public class ThresholdColors extends ImageTransformFunctionAbstract implements ImageTransformInterface {

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

		IcyBufferedImage binaryResultBuffer = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(), 1,
				DataType.UBYTE);
		IcyBufferedImage dummy = sourceImage;
		if (sourceImage.getDataType_() == DataType.DOUBLE) {
			dummy = IcyBufferedImageUtil.convertToType(sourceImage, DataType.BYTE, false);
		}
		byte[][] sourceBuffer = dummy.getDataXYCAsByte();
		byte[] binaryResultArray = binaryResultBuffer.getDataXYAsByte(0);
		int npixels = binaryResultArray.length;

		if (space == SpotThresholdColorSpace.RGB) {
			fillBinaryRgbDistance(sourceBuffer, binaryResultArray, npixels, options);
		} else {
			fillBinaryGeneralSpace(sourceBuffer, binaryResultArray, npixels, options, space);
		}
		return binaryResultBuffer;
	}

	private static void fillBinaryRgbDistance(byte[][] sourceBuffer, byte[] binaryResultArray, int npixels,
			CanvasImageTransformOptions options) {
		NHDistanceColor distance = options.colordistanceType == 1 ? new NHDistanceColorL1() : new NHDistanceColorL2();
		for (int ipixel = 0; ipixel < npixels; ipixel++) {
			byte val = options.byteTRUE;
			Color pixel = new Color(sourceBuffer[0][ipixel] & 0xFF, sourceBuffer[1][ipixel] & 0xFF,
					sourceBuffer[2][ipixel] & 0xFF);
			for (int k = 0; k < options.colorarray.size(); k++) {
				Color color = options.colorarray.get(k);
				if (distance.computeDistance(pixel, color) <= options.colorthreshold) {
					val = options.byteFALSE;
					break;
				}
			}
			binaryResultArray[ipixel] = val;
		}
	}

	private static void fillBinaryGeneralSpace(byte[][] sourceBuffer, byte[] binaryResultArray, int npixels,
			CanvasImageTransformOptions options, SpotThresholdColorSpace space) {
		boolean l1 = options.colordistanceType == 1;
		int nref = options.colorarray.size();
		double[][] refTriples = new double[nref][3];
		for (int k = 0; k < nref; k++) {
			Color c = options.colorarray.get(k);
			rgbToSpaceTriple(c.getRed(), c.getGreen(), c.getBlue(), space, refTriples[k]);
		}
		double[] px = new double[3];
		for (int ipixel = 0; ipixel < npixels; ipixel++) {
			byte val = options.byteTRUE;
			int r = sourceBuffer[0][ipixel] & 0xFF;
			int g = sourceBuffer[1][ipixel] & 0xFF;
			int b = sourceBuffer[2][ipixel] & 0xFF;
			rgbToSpaceTriple(r, g, b, space, px);
			for (int k = 0; k < nref; k++) {
				double d = l1 ? l1Triple(px, refTriples[k], space) : l2Triple(px, refTriples[k], space);
				if (d <= options.colorthreshold) {
					val = options.byteFALSE;
					break;
				}
			}
			binaryResultArray[ipixel] = val;
		}
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

	/** For hue (0..255), use circular shortest arc. Other channels: linear. */
	private static double componentDiff(double x, double y, boolean circularHue) {
		if (!circularHue) {
			return Math.abs(x - y);
		}
		double d = Math.abs(x - y);
		return Math.min(d, 255.0 - d);
	}
}
