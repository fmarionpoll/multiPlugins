package plugins.fmp.multiSPOTS96.tools.imageTransform.transforms;

import java.awt.Color;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.type.DataType;
import plugins.fmp.multiSPOTS96.tools.NHDistance.NHDistanceColor;
import plugins.fmp.multiSPOTS96.tools.NHDistance.NHDistanceColorL1;
import plugins.fmp.multiSPOTS96.tools.NHDistance.NHDistanceColorL2;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

public class ThresholdColors extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		if (options.colorarray.size() == 0)
			return null;

		if (sourceImage == null)
			return null;
		if (sourceImage.getSizeC() < 3) {
			System.out
					.print("Failed operation: attempt to compute threshold from image with less than 3 color channels");
			return null;
		}

		NHDistanceColor distance;
		if (options.colordistanceType == 1)
			distance = new NHDistanceColorL1();
		else
			distance = new NHDistanceColorL2();

		IcyBufferedImage binaryResultBuffer = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(), 1,
				DataType.UBYTE);
		IcyBufferedImage dummy = sourceImage;
		if (sourceImage.getDataType_() == DataType.DOUBLE) {
			dummy = IcyBufferedImageUtil.convertToType(sourceImage, DataType.BYTE, false);
		}
		byte[][] sourceBuffer = dummy.getDataXYCAsByte();
		byte[] binaryResultArray = binaryResultBuffer.getDataXYAsByte(0);
		int npixels = binaryResultArray.length;
		Color pixel = new Color(0, 0, 0);
		for (int ipixel = 0; ipixel < npixels; ipixel++) {
			byte val = options.byteFALSE;
			pixel = new Color(sourceBuffer[0][ipixel] & 0xFF, sourceBuffer[1][ipixel] & 0xFF,
					sourceBuffer[2][ipixel] & 0xFF);
			for (int k = 0; k < options.colorarray.size(); k++) {
				Color color = options.colorarray.get(k);
				if (distance.computeDistance(pixel, color) <= options.colorthreshold) {
					val = options.byteTRUE;
					break;
				}
			}
			binaryResultArray[ipixel] = val;
		}
		return binaryResultBuffer;
	}
}
