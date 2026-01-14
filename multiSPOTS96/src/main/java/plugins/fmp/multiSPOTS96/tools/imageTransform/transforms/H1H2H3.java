package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class H1H2H3 extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());

		double[] tabValuesR = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		double[] tabValuesG = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		double[] tabValuesB = Array1DUtil.arrayToDoubleArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());

		double[] outValues0 = Array1DUtil.arrayToDoubleArray(img2.getDataXY(0), img2.isSignedDataType());
		double[] outValues1 = Array1DUtil.arrayToDoubleArray(img2.getDataXY(1), img2.isSignedDataType());
		double[] outValues2 = Array1DUtil.arrayToDoubleArray(img2.getDataXY(2), img2.isSignedDataType());

		// compute values
		final double VMAX = 255.0;
		for (int ky = 0; ky < tabValuesR.length; ky++) {
			int r = (int) tabValuesR[ky];
			int g = (int) tabValuesG[ky];
			int b = (int) tabValuesB[ky];

			outValues0[ky] = (r + g) / 2.0;
			outValues1[ky] = (VMAX + r - g) / 2.0;
			outValues2[ky] = (VMAX + b - (r + g) / 2.0) / 2.0;
		}

		int c = 0;
		Array1DUtil.doubleArrayToSafeArray(outValues0, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		c++;
		Array1DUtil.doubleArrayToSafeArray(outValues1, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		c++;
		Array1DUtil.doubleArrayToSafeArray(outValues2, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		return img2;
	}

}
