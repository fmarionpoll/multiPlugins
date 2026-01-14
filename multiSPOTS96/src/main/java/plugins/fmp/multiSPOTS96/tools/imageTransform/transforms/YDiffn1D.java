package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class YDiffn1D extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	int spanDiff = 5;

	public YDiffn1D(int spanDiff) {
		this.spanDiff = spanDiff;
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		int imageSizeX = sourceImage.getSizeX();
		int imageSizeY = sourceImage.getSizeY();
		IcyBufferedImage img2 = new IcyBufferedImage(imageSizeX, imageSizeY, 3, sourceImage.getDataType_());

		int[] Rn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(0), sourceImage.isSignedDataType());
		int[] Gn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(1), sourceImage.isSignedDataType());
		int[] Bn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(2), sourceImage.isSignedDataType());
		double[] outValues = (double[]) Array1DUtil.createArray(DataType.DOUBLE, Rn.length);

		// for (int ix = span; ix < imageSizeX - span; ix++)
		for (int ix = 0; ix < imageSizeX; ix++) {
			for (int iy = spanDiff; iy < imageSizeY - spanDiff; iy++) {
				int kx = ix + iy * imageSizeX;
				int deltax = 0;
				double outVal = 0;
				for (int ispan = 1; ispan < spanDiff; ispan++) {
					deltax += imageSizeX;
					outVal += (Rn[kx + deltax] - Rn[kx - deltax])
							- (Gn[kx + deltax] - Gn[kx - deltax] + Bn[kx + deltax] - Bn[kx - deltax]) / 2.;
				}
				outValues[kx] = (int) Math.abs(outVal);
			}
		}
		copyExGDoubleToIcyBufferedImage(outValues, img2, options.copyResultsToThe3planes);
		return img2;
	}
}
