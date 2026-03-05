package plugins.fmp.multitools.tools.imageTransform;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.imageTransform.transforms.SumDiff;

/**
 * Central factory and helpers for image transforms. Provides GPU-backed
 * implementations when enabled and available, and falls back to the existing
 * CPU implementations otherwise.
 */
public class ImageTransformFactory {

	private static final SumDiff CPU_SUM_DIFF = new SumDiff();
	private static volatile GpuSumDiff GPU_SUM_DIFF = null;

	public static ImageTransformInterface getFunction(ImageTransformEnums transform, boolean useGpu) {
		if (transform == null)
			return null;

		if (useGpu && transform == ImageTransformEnums.RGB_DIFFS) {
			GpuSumDiff gpu = getOrCreateGpuSumDiff();
			if (gpu != null) {
				return gpu;
			}
		}

		// Default: existing CPU implementation
		return transform.getFunction();
	}

	public static SumDiff getCpuSumDiff() {
		return CPU_SUM_DIFF;
	}

	private static GpuSumDiff getOrCreateGpuSumDiff() {
		if (GPU_SUM_DIFF == null) {
			synchronized (ImageTransformFactory.class) {
				if (GPU_SUM_DIFF == null) {
					GPU_SUM_DIFF = new GpuSumDiff();
				}
			}
		}
		return GPU_SUM_DIFF;
	}

	// ------------- Helpers reused from SumDiff (without code duplication) -------------

	public static IcyBufferedImage getResultImageOrReuse(int width, int height, int nChannels, DataType dataType,
			IcyBufferedImage reuseBuffer) {
		if (reuseBuffer != null && reuseBuffer.getWidth() == width && reuseBuffer.getHeight() == height
				&& reuseBuffer.getSizeC() == nChannels && reuseBuffer.getDataType_() == dataType) {
			return reuseBuffer;
		}
		return new IcyBufferedImage(width, height, nChannels, dataType);
	}

	public static void copyExGIntToIcyBufferedImage(int[] ExG, IcyBufferedImage img2, boolean copyTo3Planes) {
		Object destArray = Array1DUtil.createArray(img2.getDataType_(), ExG.length);
		Array1DUtil.intArrayToSafeArray(ExG, destArray, true);
		img2.setDataXY(0, destArray);

		if (copyTo3Planes) {
			for (int c = 1; c < img2.getSizeC(); c++) {
				img2.setDataXY(c, destArray);
			}
		}
	}
}

