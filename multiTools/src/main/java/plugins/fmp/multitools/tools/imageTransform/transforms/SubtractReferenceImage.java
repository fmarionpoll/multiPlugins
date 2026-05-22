package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

public class SubtractReferenceImage extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (options.backgroundImage == null)
			return null;

		return mappedDifference(sourceImage, options.backgroundImage);
	}

	/**
	 * Per-channel absolute difference mapped like legacy {@code t-ref} display: {@code 0xFF - |a-b|}.
	 */
	public static IcyBufferedImage mappedDifference(IcyBufferedImage sourceImage, IcyBufferedImage refImage) {
		if (sourceImage == null || refImage == null) {
			return null;
		}
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(),
				sourceImage.getSizeC(), sourceImage.getDataType_());
		for (int c = 0; c < sourceImage.getSizeC(); c++) {
			int[] imgSourceInt = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
			int[] img2Int = Array1DUtil.arrayToIntArray(img2.getDataXY(c), img2.isSignedDataType());
			int[] imgReferenceInt = Array1DUtil.arrayToIntArray(refImage.getDataXY(c), refImage.isSignedDataType());
			for (int i = 0; i < imgSourceInt.length; i++) {
				int val = imgSourceInt[i] - imgReferenceInt[i];
				if (val < 0)
					val = -val;
				img2Int[i] = 0xFF - val;
			}
			Array1DUtil.intArrayToSafeArray(img2Int, img2.getDataXY(c), true, img2.isSignedDataType());
			img2.setDataXY(c, img2.getDataXY(c));
		}
		return img2;
	}

	/**
	 * In-place per-channel stretch: map values between the {@code pLow}-th and
	 * {@code pHigh}-th percentile (inclusive counting) to 0–255. No-op if the range
	 * is degenerate.
	 */
	public static void percentileStretchPerChannelInPlace(IcyBufferedImage img, int pLow, int pHigh) {
		if (img == null || pLow < 0 || pHigh > 100 || pLow >= pHigh) {
			return;
		}
		int nPix = img.getSizeX() * img.getSizeY();
		if (nPix <= 0) {
			return;
		}
		for (int c = 0; c < img.getSizeC(); c++) {
			int[] arr = Array1DUtil.arrayToIntArray(img.getDataXY(c), img.isSignedDataType());
			int[] hist = new int[256];
			for (int v : arr) {
				int b = Math.max(0, Math.min(255, v));
				hist[b]++;
			}
			int low = percentileBinFromCumulative(hist, nPix, pLow);
			int high = percentileBinFromCumulative(hist, nPix, pHigh);
			if (high <= low) {
				high = Math.min(255, low + 1);
			}
			for (int i = 0; i < arr.length; i++) {
				int v = Math.max(0, Math.min(255, arr[i]));
				int out = (int) Math.round(255.0 * (v - low) / (high - low));
				if (out < 0) {
					out = 0;
				} else if (out > 255) {
					out = 255;
				}
				arr[i] = out;
			}
			Array1DUtil.intArrayToSafeArray(arr, img.getDataXY(c), true, img.isSignedDataType());
			img.setDataXY(c, img.getDataXY(c));
		}
	}

	private static int percentileBinFromCumulative(int[] hist, int n, int pPercent) {
		long thresh = (long) Math.ceil(n * (pPercent / 100.0));
		if (thresh < 1) {
			thresh = 1;
		}
		long cum = 0;
		for (int i = 0; i < 256; i++) {
			cum += hist[i];
			if (cum >= thresh) {
				return i;
			}
		}
		return 255;
	}

}
