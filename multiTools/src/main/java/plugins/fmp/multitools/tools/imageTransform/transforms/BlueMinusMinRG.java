package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.AlgorithmException;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformBase;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformConstants;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformException;
import plugins.fmp.multitools.tools.imageTransform.InvalidParameterException;

/**
 * Per-pixel {@code clamp(B − min(R, G))} into [0, 255].
 * Helps separate blue-channel structure from occlusion when shadows collapse R,G,B together.
 */
public class BlueMinusMinRG extends ImageTransformBase {

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
					"B−min(R,G) needs at least 3 channels", transformName);
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
			for (int i = 0; i < n; i++) {
				out[i] = clampByte(b[i] - Math.min(r[i], g[i]));
			}
			IcyBufferedImage resultImage = getResultImageOrReuse(sourceImage, ImageTransformConstants.ColorSpace.RGB_CHANNELS,
					reuseBuffer);
			copyArrayToImage(out, resultImage, options.copyResultsToThe3planes);
			return resultImage;
		} catch (Exception e) {
			throw new AlgorithmException("B−min(R,G)", e.getMessage(), e);
		}
	}

	@Override
	protected IcyBufferedImage executeTransform(IcyBufferedImage sourceImage, CanvasImageTransformOptions options)
			throws ImageTransformException {
		return executeTransform(sourceImage, options, null);
	}
}
