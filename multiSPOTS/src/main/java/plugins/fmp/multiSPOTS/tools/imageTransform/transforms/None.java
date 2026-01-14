package plugins.fmp.multiSPOTS.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS.tools.imageTransform.ImageTransformOptions;

public class None extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		return sourceImage;
	}

}
