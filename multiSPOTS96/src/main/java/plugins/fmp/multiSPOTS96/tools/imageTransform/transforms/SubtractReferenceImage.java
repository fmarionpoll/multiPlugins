package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class SubtractReferenceImage extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		if (options.backgroundImage == null)
			return null;

		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(),
				sourceImage.getSizeC(), sourceImage.getDataType_());
		for (int c = 0; c < sourceImage.getSizeC(); c++) {
			int[] imgSourceInt = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
			int[] img2Int = Array1DUtil.arrayToIntArray(img2.getDataXY(0), img2.isSignedDataType());
			int[] imgReferenceInt = Array1DUtil.arrayToIntArray(options.backgroundImage.getDataXY(0),
					options.backgroundImage.isSignedDataType());
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
