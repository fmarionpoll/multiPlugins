package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class SumDiff extends ImageTransformFunctionAbstract implements ImageTransformInterface {

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		if (sourceImage == null || sourceImage.getSizeC() < 3)
			return null;
		IcyBufferedImage img2 = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		int c = 0;
		int Rlayer = c;
		int[] Rn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Rlayer), sourceImage.isSignedDataType());
		int Glayer = c + 1;
		int[] Gn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Glayer), sourceImage.isSignedDataType());
		int Blayer = c + 2;
		int[] Bn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Blayer), sourceImage.isSignedDataType());
		int[] ExG = (int[]) Array1DUtil.createArray(DataType.INT, Rn.length);

		for (int i = 0; i < Rn.length; i++) {
			int diff1 = Math.abs(Rn[i] - Bn[i]);
			int diff2 = Math.abs(Rn[i] - Gn[i]);
			int diff3 = Math.abs(Bn[i] - Gn[i]);
			ExG[i] = diff1 + diff2 + diff3; // Math.max(diff3, Math.max(diff1, diff2));
		}

		copyExGIntToIcyBufferedImage(ExG, img2, options.copyResultsToThe3planes);
		return img2;
	}

}
