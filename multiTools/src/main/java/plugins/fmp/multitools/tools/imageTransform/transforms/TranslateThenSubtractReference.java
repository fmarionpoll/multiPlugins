package plugins.fmp.multitools.tools.imageTransform.transforms;

import javax.vecmath.Vector2d;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.registration.GaspardRigidRegistration;

/**
 * One-pass preview: rotate the current frame about {@link CanvasImageTransformOptions#rotatePivotX} /
 * {@link CanvasImageTransformOptions#rotatePivotY} by {@link CanvasImageTransformOptions#rotationRadians},
 * translate by {@link CanvasImageTransformOptions#translateDx} / {@link CanvasImageTransformOptions#translateDy},
 * then mapped difference against {@link CanvasImageTransformOptions#backgroundImage}.
 */
public class TranslateThenSubtractReference extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (options == null || options.backgroundImage == null) {
			return null;
		}
		IcyBufferedImage warped = GaspardRigidRegistration.applyRotateAboutPivotThenTranslate2D(sourceImage, -1,
				options.rotationRadians, options.rotatePivotX, options.rotatePivotY,
				new Vector2d(options.translateDx, options.translateDy), true);
		return SubtractReferenceImage.mappedDifference(warped, options.backgroundImage);
	}
}
