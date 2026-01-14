package plugins.fmp.multiSPOTS96.tools.imageTransform;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;

public abstract class ImageTransformFunctionAbstract {

	protected void copyExGIntToIcyBufferedImage(int[] ExG, IcyBufferedImage img2, boolean copyResultTo3Planes) {
		Array1DUtil.intArrayToSafeArray(ExG, img2.getDataXY(0), false, false); // true);
		img2.setDataXY(0, img2.getDataXY(0));
		if (copyResultTo3Planes) {
			for (int c = 1; c < 3; c++) {
				img2.copyData(img2, 0, c);
				img2.setDataXY(c, img2.getDataXY(c));
			}
		}
	}

	protected void copyExGDoubleToIcyBufferedImage(double[] ExG, IcyBufferedImage img2, boolean copyResultTo3Planes) {
		Array1DUtil.doubleArrayToSafeArray(ExG, img2.getDataXY(0), false);
		img2.setDataXY(0, img2.getDataXY(0));
		if (copyResultTo3Planes) {
			for (int c = 1; c < 3; c++) {
				img2.copyData(img2, 0, c);
				img2.setDataXY(c, img2.getDataXY(c));
			}
		}
	}

	protected IcyBufferedImage functionRGB_keepOneChan(IcyBufferedImage sourceImage, int keepChan) {
		IcyBufferedImage resultImage = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		resultImage.copyData(sourceImage, keepChan, 0);
		resultImage.setDataXY(0, resultImage.getDataXY(0));
		for (int c = 1; c < 3; c++) {
			resultImage.copyData(resultImage, 0, c);
			resultImage.setDataXY(c, resultImage.getDataXY(c));
		}
		return resultImage;
	}

	protected IcyBufferedImage transformToGrey(IcyBufferedImage sourceImage, boolean copyResultTo3Planes) {
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		int[] tabValuesR = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] tabValuesG = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		int[] tabValuesB = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());
		int[] outValues0 = Array1DUtil.arrayToIntArray(img2.getDataXY(0), sourceImage.isSignedDataType());

		for (int ky = 0; ky < outValues0.length; ky++)
			outValues0[ky] = (tabValuesR[ky] + tabValuesG[ky] + tabValuesB[ky]) / 3;

		copyExGIntToIcyBufferedImage(outValues0, img2, copyResultTo3Planes);
		return img2;
	}

}
