package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class XDiffn extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int spanDiff = 5;

	public XDiffn(int spanDiff) {
		this.spanDiff = spanDiff;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		int chan0 = 0;
		int chan1 = sourceImage.getSizeC();
		int imageSizeX = sourceImage.getSizeX();
		int imageSizeY = sourceImage.getSizeY();
		IcyBufferedImage img2 = new IcyBufferedImage(imageSizeX, imageSizeY, 3, sourceImage.getDataType_());
		for (int c = chan0; c < chan1; c++) {
			int[] tabValues = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
			int[] outValues = Array1DUtil.arrayToIntArray(img2.getDataXY(c), img2.isSignedDataType());
			for (int iy = 0; iy < imageSizeY; iy++) {
				// erase border values
				for (int ix = 0; ix < spanDiff; ix++)
					outValues[ix + iy * imageSizeX] = 0;
				// compute values
				int deltay = iy * imageSizeX;
				for (int ix = spanDiff; ix < imageSizeX - spanDiff; ix++) {
					int kx = ix + deltay;
					int deltax = 0;
					double outVal = 0;
					for (int ispan = 1; ispan < spanDiff; ispan++) {
						deltax += 1;
						outVal += tabValues[kx + deltax] - tabValues[kx - deltax];
					}
					outValues[kx] = (int) Math.abs(outVal);
				}
				// erase border values
				for (int ix = imageSizeX - spanDiff; ix < imageSizeX; ix++)
					outValues[ix + iy * imageSizeX] = 0;
			}
			Array1DUtil.intArrayToSafeArray(outValues, img2.getDataXY(c), true, img2.isSignedDataType());
			img2.setDataXY(c, img2.getDataXY(c));
		}
		return img2;
	}

}
