package plugins.fmp.multitools.service;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFactory;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * Shared transform list and metric-map helper for kymograph spot metrics (same palette as spot
 * measure threshold UIs).
 */
public final class KymoImageTransforms {

	public static final ImageTransformEnums[] METRIC_CHOICES = new ImageTransformEnums[] { ImageTransformEnums.R_RGB,
			ImageTransformEnums.G_RGB, ImageTransformEnums.B_RGB, ImageTransformEnums.B_MINUS_MINRG,
			ImageTransformEnums.B_MINUS_MEANGREY_CTR, ImageTransformEnums.R2MINUS_GB, ImageTransformEnums.G2MINUS_RB,
			ImageTransformEnums.B2MINUS_RG, ImageTransformEnums.RGB, ImageTransformEnums.GBMINUS_2R,
			ImageTransformEnums.RBMINUS_2G, ImageTransformEnums.RGMINUS_2B, ImageTransformEnums.RGB_DIFFS,
			ImageTransformEnums.RGB_DIFFS_LOCAL_MEAN, ImageTransformEnums.H_HSB, ImageTransformEnums.S_HSB,
			ImageTransformEnums.B_HSB };

	private KymoImageTransforms() {
	}

	/**
	 * Applies the chosen spot-style RGB transform; channel 0 holds the scalar metric per pixel (possibly
	 * replicated on other channels). Returns null if the transform cannot be applied.
	 */
	public static IcyBufferedImage applyMetricTransform(IcyBufferedImage sourceRgb, ImageTransformEnums transform,
			boolean useGpu) {
		if (sourceRgb == null || transform == null) {
			return null;
		}
		ImageTransformInterface fn = ImageTransformFactory.getFunction(transform, useGpu);
		if (fn == null) {
			return null;
		}
		CanvasImageTransformOptions opt = new CanvasImageTransformOptions();
		return fn.getTransformedImage(sourceRgb, opt);
	}

	public static double[] channel0AsDouble(IcyBufferedImage img) {
		if (img == null || img.getSizeC() < 1) {
			return null;
		}
		return Array1DUtil.arrayToDoubleArray(img.getDataXY(0), img.isSignedDataType());
	}

	public static int indexOfMetric(ImageTransformEnums e) {
		if (e == null) {
			return 0;
		}
		for (int i = 0; i < METRIC_CHOICES.length; i++) {
			if (METRIC_CHOICES[i] == e) {
				return i;
			}
		}
		return 0;
	}
}
