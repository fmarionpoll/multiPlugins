package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class XYDiffn extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int spanDiff = 5;

	public XYDiffn(int spanDiff) {
		this.spanDiff = spanDiff;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		int chan0 = 0;
		int chan1 = sourceImage.getSizeC();
		int imageSizeX = sourceImage.getSizeX();
		int imageSizeY = sourceImage.getSizeY();
		IcyBufferedImage img2 = new IcyBufferedImage(imageSizeX, imageSizeY, 1, sourceImage.getDataType_());

		for (int c = chan0; c < chan1; c++) {
			int[] tabValues = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(c), sourceImage.isSignedDataType());
			int[] outValues = Array1DUtil.arrayToIntArray(img2.getDataXY(c), img2.isSignedDataType());
			for (int ix = 0; ix < imageSizeX; ix++) {
				for (int iy = spanDiff; iy < imageSizeY - spanDiff; iy++) {
					int ky = ix + iy * imageSizeX;
					int deltay = 0;
					double outVal = 0;
					// loop vertically
					for (int ispan = 1; ispan < spanDiff; ispan++) {
						deltay += imageSizeX;
						outVal += tabValues[ky + deltay] - tabValues[ky - deltay];
					}

					// loop horizontally
					int deltax = 0;
					int yspan2 = 10;
					if (ix > yspan2 && ix < imageSizeX - yspan2) {
						for (int ispan = 1; ispan < yspan2; ispan++) {
							deltax += 1;
							outVal += tabValues[ky + deltax] - tabValues[ky - deltax];
						}
					}
					outValues[ky] = (int) Math.abs(outVal);
				}

				// erase out-of-bounds points
				for (int iy = 0; iy < spanDiff; iy++)
					outValues[ix + iy * imageSizeX] = 0;

				for (int iy = imageSizeY - spanDiff; iy < imageSizeY; iy++)
					outValues[ix + iy * imageSizeX] = 0;
			}
			Array1DUtil.intArrayToSafeArray(outValues, img2.getDataXY(c), true, img2.isSignedDataType());
			img2.setDataXY(c, img2.getDataXY(c));
		}
		return img2;
	}

}
