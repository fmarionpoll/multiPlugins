package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class ThresholdSingleValue extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		if (sourceImage == null)
			return null;

		IcyBufferedImage binaryMap = new IcyBufferedImage(sourceImage.getSizeX(), sourceImage.getSizeY(), 1,
				DataType.UBYTE);
		byte[] binaryMapDataBuffer = binaryMap.getDataXYAsByte(0);
		int[] imageSourceDataBuffer = null;
		DataType datatype = sourceImage.getDataType_();
		if (datatype != DataType.INT) {
			Object sourceArray = sourceImage.getDataXY(0);
			imageSourceDataBuffer = Array1DUtil.arrayToIntArray(sourceArray, sourceImage.isSignedDataType());
		} else {
			imageSourceDataBuffer = sourceImage.getDataXYAsInt(0);
		}

		byte on = options.byteTRUE;
		byte off = options.byteFALSE;

		for (int x = 0; x < binaryMapDataBuffer.length; x++) {
			int val = imageSourceDataBuffer[x] & 0xFF;
			boolean passes = options.ifGreater ? (val > options.simplethreshold) : (val < options.simplethreshold);
			binaryMapDataBuffer[x] = passes ? off : on;
		}

		return binaryMap;
	}

}
