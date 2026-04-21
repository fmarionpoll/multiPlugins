package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class SubtractReferenceImage extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (options.backgroundImage == null)
			return null;

		return mappedDifference(sourceImage, options.backgroundImage);
	}

	/**
	 * Per-channel absolute difference mapped like legacy {@code t-ref} display: {@code 0xFF - |a-b|}.
	 */
	public static IcyBufferedImage mappedDifference(IcyBufferedImage sourceImage, IcyBufferedImage refImage) {
		if (sourceImage == null || refImage == null) {
			return null;
		}
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(),
				sourceImage.getSizeC(), sourceImage.getDataType_());
		for (int c = 0; c < sourceImage.getSizeC(); c++) {
			int[] imgSourceInt = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
			int[] img2Int = Array1DUtil.arrayToIntArray(img2.getDataXY(c), img2.isSignedDataType());
			int[] imgReferenceInt = Array1DUtil.arrayToIntArray(refImage.getDataXY(c), refImage.isSignedDataType());
			for (int i = 0; i < imgSourceInt.length; i++) {
				int val = imgSourceInt[i] - imgReferenceInt[i];
				if (val < 0)
					val = -val;
				img2Int[i] = 0xFF - val;
			}
			Array1DUtil.intArrayToSafeArray(img2Int, img2.getDataXY(c), true, img2.isSignedDataType());
			img2.setDataXY(c, img2.getDataXY(c));
		}
		return img2;
	}

}
