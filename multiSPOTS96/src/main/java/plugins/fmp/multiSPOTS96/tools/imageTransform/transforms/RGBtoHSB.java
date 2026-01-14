package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import java.awt.Color;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class RGBtoHSB extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int channelOut = 0;

	public RGBtoHSB(int channelOut) {
		this.channelOut = channelOut;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage img = functionRGBtoHSB(sourceImage);
		if (channelOut >= 0)
			img = functionRGB_keepOneChan(img, channelOut);
		return img;
	}

	protected IcyBufferedImage functionRGBtoHSB(IcyBufferedImage sourceImage) {
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());

		float[] tabValuesR = Array1DUtil.arrayToFloatArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		float[] tabValuesG = Array1DUtil.arrayToFloatArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		float[] tabValuesB = Array1DUtil.arrayToFloatArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());

		float[] outValues0 = Array1DUtil.arrayToFloatArray(img2.getDataXY(0), img2.isSignedDataType());
		float[] outValues1 = Array1DUtil.arrayToFloatArray(img2.getDataXY(1), img2.isSignedDataType());
		float[] outValues2 = Array1DUtil.arrayToFloatArray(img2.getDataXY(2), img2.isSignedDataType());

		// compute values
		for (int ky = 0; ky < tabValuesR.length; ky++) {
			int R = (int) tabValuesR[ky];
			int G = (int) tabValuesG[ky];
			int B = (int) tabValuesB[ky];

			float[] hsb = Color.RGBtoHSB(R, G, B, null);
			outValues0[ky] = hsb[0] * 100;
			outValues1[ky] = hsb[1] * 100;
			outValues2[ky] = hsb[2] * 100;
		}
		int c = 0;
		Array1DUtil.floatArrayToSafeArray(outValues0, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		c++;
		Array1DUtil.floatArrayToSafeArray(outValues1, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		c++;
		Array1DUtil.floatArrayToSafeArray(outValues2, img2.getDataXY(c), false); // img2.isSignedDataType());
		img2.setDataXY(c, img2.getDataXY(c));
		return img2;
	}
}
