package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.AlgorithmException;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformBase;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformConstants;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformException;
import plugins.fmp.multitools.tools.imageTransform.InvalidParameterException;

/**
 * Per-pixel {@code clamp(127.5 + B − (R+G+B)/3)}. Achromatic pixels sit near mid-grey (~127.5);
 * blue biased pixels shift up; thresholds can be tuned like on raw B when using "below fly" logic.
 */
public class BlueMinusMeanRgbCentered extends ImageTransformBase {

	private static double clampByte(double v) {
		double lo = ImageTransformConstants.Thresholding.MIN_THRESHOLD;
		double hi = ImageTransformConstants.Thresholding.MAX_THRESHOLD;
		if (v <= lo)
			return lo;
		if (v >= hi)
			return hi;
		return v;
	}

	@Override
	protected void validateTransformSpecificParameters(IcyBufferedImage sourceImage, CanvasImageTransformOptions options,
			String transformName) throws ImageTransformException {
		if (sourceImage.getSizeC() < ImageTransformConstants.ColorSpace.RGB_CHANNELS) {
			throw new InvalidParameterException("channels", sourceImage.getSizeC(),
					"B−RGB_mean needs at least 3 channels", transformName);
		}
	}

	@Override
	protected IcyBufferedImage executeTransform(IcyBufferedImage sourceImage, CanvasImageTransformOptions options,
			IcyBufferedImage reuseBuffer) throws ImageTransformException {
		try {
			double[][] rgb = getRGBArraysOptimized(sourceImage);
			double[] r = rgb[0];
			double[] g = rgb[1];
			double[] b = rgb[2];
			int n = r.length;
			double[] out = new double[n];
			final double mid = 127.5;
			for (int i = 0; i < n; i++) {
				double mean = (r[i] + g[i] + b[i]) / 3.0;
				out[i] = clampByte(mid + (b[i] - mean));
			}
			IcyBufferedImage resultImage = getResultImageOrReuse(sourceImage, ImageTransformConstants.ColorSpace.RGB_CHANNELS,
					reuseBuffer);
			copyArrayToImage(out, resultImage, options.copyResultsToThe3planes);
			return resultImage;
		} catch (Exception e) {
			throw new AlgorithmException("B−meanRGB+c", e.getMessage(), e);
		}
	}

	@Override
	protected IcyBufferedImage executeTransform(IcyBufferedImage sourceImage, CanvasImageTransformOptions options)
			throws ImageTransformException {
		return executeTransform(sourceImage, options, null);
	}
}
