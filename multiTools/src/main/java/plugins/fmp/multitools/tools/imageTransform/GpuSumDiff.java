package plugins.fmp.multitools.tools.imageTransform;

import java.nio.FloatBuffer;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLDevice;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLPlatform;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;

/**
 * GPU-backed implementation of the RGB_DIFFS transform using OpenCL via
 * JavaCL. Falls back to the CPU SumDiff implementation when no suitable
 * device is available or if any error occurs.
 */
public class GpuSumDiff implements ImageTransformInterface {

	private CLContext context;
	private CLProgram program;
	private boolean gpuAvailable = false;

	private static final String KERNEL_SOURCE = ""
			+ "__kernel void rgbDiffs(__global const float* r, __global const float* g, __global const float* b,         \n"
			+ "                        int n, __global float* out) {                                             \n"
			+ "  int i = get_global_id(0);                                                                       \n"
			+ "  if (i >= n) return;                                                                             \n"
			+ "  float diff1 = fabs(r[i] - b[i]);                                                                \n"
			+ "  float diff2 = fabs(r[i] - g[i]);                                                                \n"
			+ "  float diff3 = fabs(b[i] - g[i]);                                                                \n"
			+ "  out[i] = diff1 + diff2 + diff3;                                                                 \n"
			+ "}                                                                                                  \n";

	public GpuSumDiff() {
		initializeGPU();
	}

	private void initializeGPU() {
		try {
			CLPlatform[] platforms = JavaCL.listPlatforms();
			if (platforms.length == 0) {
				gpuAvailable = false;
				return;
			}

			CLDevice device = null;
			for (CLPlatform platform : platforms) {
				CLDevice[] devices = platform.listGPUDevices(true);
				if (devices.length > 0) {
					device = devices[0];
					break;
				}
			}

			if (device == null) {
				for (CLPlatform platform : platforms) {
					CLDevice[] devices = platform.listCPUDevices(true);
					if (devices.length > 0) {
						device = devices[0];
						break;
					}
				}
			}

			if (device == null) {
				gpuAvailable = false;
				return;
			}

			context = JavaCL.createContext(null, device);
			program = context.createProgram(KERNEL_SOURCE);
			program.build();
			gpuAvailable = true;
		} catch (Exception e) {
			e.printStackTrace();
			gpuAvailable = false;
		}
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		return getTransformedImage(sourceImage, options, null);
	}

	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options,
			IcyBufferedImage reuseBuffer) {
		// Fallback to CPU implementation when GPU is not available or input is invalid
		if (!gpuAvailable || sourceImage == null || sourceImage.getSizeC() < 3) {
			return ImageTransformFactory.getCpuSumDiff().getTransformedImage(sourceImage, options, reuseBuffer);
		}

		try {
			final int width = sourceImage.getWidth();
			final int height = sourceImage.getHeight();
			final int n = width * height;

			IcyBufferedImage img2 = ImageTransformFactory.getResultImageOrReuse(width, height, 3,
					sourceImage.getDataType_(), reuseBuffer);

			// Extract RGB channels as floats
			int Rlayer = 0;
			int[] Rn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Rlayer), sourceImage.isSignedDataType());
			int Glayer = 1;
			int[] Gn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Glayer), sourceImage.isSignedDataType());
			int Blayer = 2;
			int[] Bn = Array1DUtil.arrayToIntArray(sourceImage.getDataXY(Blayer), sourceImage.isSignedDataType());

			float[] r = Array1DUtil.arrayToFloatArray(Rn, sourceImage.isSignedDataType());
			float[] g = Array1DUtil.arrayToFloatArray(Gn, sourceImage.isSignedDataType());
			float[] b = Array1DUtil.arrayToFloatArray(Bn, sourceImage.isSignedDataType());
			float[] out = new float[n];

			CLQueue queue = context.createDefaultQueue();
			CLBuffer<FloatBuffer> rBuf = context.createFloatBuffer(CLMem.Usage.Input, FloatBuffer.wrap(r), true);
			CLBuffer<FloatBuffer> gBuf = context.createFloatBuffer(CLMem.Usage.Input, FloatBuffer.wrap(g), true);
			CLBuffer<FloatBuffer> bBuf = context.createFloatBuffer(CLMem.Usage.Input, FloatBuffer.wrap(b), true);
			CLBuffer<FloatBuffer> outBuf = context.createFloatBuffer(CLMem.Usage.Output, n);

			com.nativelibs4java.opencl.CLKernel kernel = program.createKernel("rgbDiffs");
			kernel.setArgs(rBuf, gBuf, bBuf, n, outBuf);

			int[] globalSizes = new int[] { n };
			kernel.enqueueNDRange(queue, globalSizes);
			queue.finish();

			outBuf.read(queue, FloatBuffer.wrap(out), true);

			// Convert float[] back to INT and copy to output image
			int[] ExG = (int[]) Array1DUtil.createArray(DataType.INT, n);
			Array1DUtil.floatArrayToSafeArray(out, ExG, true);

			ImageTransformFactory.copyExGIntToIcyBufferedImage(ExG, img2,
					options != null && options.copyResultsToThe3planes);

			rBuf.release();
			gBuf.release();
			bBuf.release();
			outBuf.release();
			queue.release();

			return img2;
		} catch (Exception e) {
			e.printStackTrace();
			// On error, fall back to CPU implementation
			return ImageTransformFactory.getCpuSumDiff().getTransformedImage(sourceImage, options, reuseBuffer);
		}
	}
}

