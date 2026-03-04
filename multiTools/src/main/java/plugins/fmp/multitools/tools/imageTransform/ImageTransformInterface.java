package plugins.fmp.multitools.tools.imageTransform;

import icy.image.IcyBufferedImage;

public interface ImageTransformInterface {
	IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options);

	default IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options,
			IcyBufferedImage reuseBuffer) {
		return getTransformedImage(sourceImage, options);
	}
}
