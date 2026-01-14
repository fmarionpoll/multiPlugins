package plugins.fmp.multitools.fmp_tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformOptions;

public class None extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		return sourceImage;
	}

}
