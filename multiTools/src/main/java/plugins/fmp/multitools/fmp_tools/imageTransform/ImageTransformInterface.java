package plugins.fmp.multitools.fmp_tools.imageTransform;

import icy.image.IcyBufferedImage;

public interface ImageTransformInterface {
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options);
}
