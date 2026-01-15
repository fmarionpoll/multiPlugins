package plugins.fmp.multiSPOTS96.tools.imageTransform;

import icy.image.IcyBufferedImage;

public interface ImageTransformInterface {
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options);
}
