package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class None extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		return sourceImage;
	}

}
