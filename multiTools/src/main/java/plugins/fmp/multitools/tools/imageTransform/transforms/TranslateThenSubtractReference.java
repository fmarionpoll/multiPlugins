package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;

/**
 * One-pass preview: translate the current frame by {@link CanvasImageTransformOptions#translateDx} /
 * {@link CanvasImageTransformOptions#translateDy}, then apply the same mapped difference as
 * {@link SubtractReferenceImage} against {@link CanvasImageTransformOptions#backgroundImage}
 * (typically {@code SequenceCamData.getReferenceImage()}).
 */
public class TranslateThenSubtractReference extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (options == null || options.backgroundImage == null) {
			return null;
		}
		IcyBufferedImage shifted = ImagePixelTranslate.translate(sourceImage, options.translateDx, options.translateDy);
		return SubtractReferenceImage.mappedDifference(shifted, options.backgroundImage);
	}
}
