package plugins.fmp.multitools.tools.imageTransform.transforms;

import javax.vecmath.Vector2d;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.registration.GaspardRigidRegistration;

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
		IcyBufferedImage shifted = GaspardRigidRegistration.applyTranslation2D(sourceImage, -1,
				new Vector2d(options.translateDx, options.translateDy), true);
		return SubtractReferenceImage.mappedDifference(shifted, options.backgroundImage);
	}
}
