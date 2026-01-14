package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class SubtractColumn extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int column = 0;

	public SubtractColumn(int column) {
		this.column = column;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		int nchannels = sourceImage.getSizeC();
		int imageSizeX = sourceImage.getSizeX();
		int imageSizeY = sourceImage.getSizeY();
		IcyBufferedImage img2 = new IcyBufferedImage(imageSizeX, imageSizeY, nchannels, sourceImage.getDataType_());
		for (int c = 0; c < nchannels; c++) {
			int[] tabValues = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
			int[] outValues = Array1DUtil.arrayToIntArray(img2.getDataXY(c), img2.isSignedDataType());
			for (int iy = 0; iy < imageSizeY; iy++) {
				int deltay = iy * imageSizeX;
				int kx = column + deltay;
				int refVal = tabValues[kx];
				for (int ix = 0; ix < imageSizeX; ix++) {
					kx = ix + deltay;
					int outVal = tabValues[kx] - refVal;
					outValues[kx] = (int) Math.abs(outVal);
				}
			}
			Array1DUtil.intArrayToSafeArray(outValues, img2.getDataXY(c), sourceImage.isSignedDataType(),
					img2.isSignedDataType());
			img2.setDataXY(c, img2.getDataXY(c));
		}
		return img2;
	}
}
