package plugins.fmp.multitools.fmp_tools.registration;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.vecmath.Vector2d;

import com.nativelibs4java.opencl.CLBuffer;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLDevice;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLPlatform;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;

import icy.file.Saver;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.cages.Cage;
import plugins.fmp.multitools.fmp_experiment.capillaries.Capillary;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.fmp_workinprogress_gpu.EnumCLFunction;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class ImageRegistrationFeaturesGPU extends ImageRegistration implements AutoCloseable {

	private CLContext context = null;
	private CLProgram program = null;
	private CLKernel kernelAffine = null;
	private CLKernel kernelPerspective = null;
	private boolean gpuAvailable = false;

	public ImageRegistrationFeaturesGPU() {
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
			String kernelSource = loadKernelSource();
			program = context.createProgram(kernelSource);
			program.build();
			kernelAffine = program.createKernel(EnumCLFunction.AFFINETRANSFORM2D.toString());
			kernelPerspective = program.createKernel(EnumCLFunction.PERSPECTIVETRANSFORM2D.toString());
			gpuAvailable = true;
		} catch (Exception e) {
			e.printStackTrace();
			gpuAvailable = false;
		}
	}

	private String loadKernelSource() {
		try {
			// Try loading from resources first (if CL file is in resources directory)
			InputStream is = getClass().getResourceAsStream(
					"/plugins/fmp/multicafe/workinprogress_gpu/CLfunctions.cl");
			if (is == null) {
				is = getClass().getClassLoader()
						.getResourceAsStream("plugins/fmp/multicafe/workinprogress_gpu/CLfunctions.cl");
			}
			// If not in resources, try loading from file system (for development)
			if (is == null) {
				// Try relative path from project root
				java.io.File file = new java.io.File(
						"src/main/java/plugins/fmp/multicafe/workinprogress_gpu/CLfunctions.cl");
				if (!file.exists()) {
					// Try absolute path (workspace-specific)
					file = new java.io.File(
							"C:\\Users\\fred\\git\\MultiCAFE\\src\\main\\java\\plugins\\fmp\\multicafe\\workinprogress_gpu\\CLfunctions.cl");
				}
				if (!file.exists()) {
					// Try to find it relative to the class location
					java.net.URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
					if (classUrl != null) {
						java.io.File classDir = new java.io.File(classUrl.toURI());
						file = new java.io.File(classDir,
								"plugins/fmp/multicafe/workinprogress_gpu/CLfunctions.cl");
					}
				}
				if (file.exists() && file.isFile()) {
					return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
				}
			} else {
				try (Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
					String result = s.hasNext() ? s.next() : "";
					return result;
				} finally {
					if (is != null) {
						is.close();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	private static class Homography {
		// 3x3 matrix flattened, row-major
		double[] h = new double[9];

		public Homography(double[] mat) {
			this.h = mat;
		}

		public Homography invert() {
			double det = h[0] * (h[4] * h[8] - h[5] * h[7]) -
						 h[1] * (h[3] * h[8] - h[5] * h[6]) +
						 h[2] * (h[3] * h[7] - h[4] * h[6]);
			
			if (Math.abs(det) < 1e-9) return null;

			double invDet = 1.0 / det;
			double[] res = new double[9];
			
			res[0] = (h[4] * h[8] - h[5] * h[7]) * invDet;
			res[1] = (h[2] * h[7] - h[1] * h[8]) * invDet;
			res[2] = (h[1] * h[5] - h[2] * h[4]) * invDet;
			res[3] = (h[5] * h[6] - h[3] * h[8]) * invDet;
			res[4] = (h[0] * h[8] - h[2] * h[6]) * invDet;
			res[5] = (h[2] * h[3] - h[0] * h[5]) * invDet;
			res[6] = (h[3] * h[7] - h[4] * h[6]) * invDet;
			res[7] = (h[1] * h[6] - h[0] * h[7]) * invDet;
			res[8] = (h[0] * h[4] - h[1] * h[3]) * invDet;
			
			return new Homography(res);
		}
	}

	@Override
	public boolean runRegistration(Experiment exp, int referenceFrame, int startFrame, int endFrame,
			boolean reverse) {
		if (!doBackup(exp))
			return false;

		if (!gpuAvailable) {
			ImageRegistrationFeatures cpuVersion = new ImageRegistrationFeatures();
			return cpuVersion.runRegistration(exp, referenceFrame, startFrame, endFrame, reverse);
		}

		SequenceCamData seqCamData = exp.getSeqCamData();
		Sequence seq = seqCamData.getSequence();

		List<Point2D> referencePoints = extractPoints(exp);
		List<Point2D> currentPoints = new ArrayList<Point2D>();
		for (Point2D p : referencePoints)
			currentPoints.add((Point2D) p.clone());

		int step = reverse ? -1 : 1;
		int start = reverse ? endFrame : startFrame;
		int end = reverse ? startFrame : endFrame;
		
		// Check if we have enough points for Homography (need exactly 4)
		boolean usePerspective = referencePoints.size() == 4;

		int t = start;
		while ((reverse && t >= end) || (!reverse && t <= end)) {
			if (t == referenceFrame) {
				t += step;
				continue;
			}

			int t_prev = t - step;
			if (t_prev < 0 || t_prev >= seq.getSizeT()) {
				t += step;
				continue;
			}

			IcyBufferedImage imgPrev = seq.getImage(t_prev, 0);
			IcyBufferedImage imgCurr = seq.getImage(t, 0);

			trackPoints(imgPrev, imgCurr, currentPoints);

			try {
				IcyBufferedImage newImg;
				if (usePerspective) {
					Homography H = computeHomography(currentPoints, referencePoints);
					if (H == null) {
						newImg = imgCurr;
					} else {
						// We need Inverse Homography for the kernel: Output(x,y) -> Input(u,v)
						Homography invH = H.invert();
						if (invH == null) newImg = imgCurr;
						else newImg = applyPerspectiveTransformGPU(imgCurr, invH);
					}
				} else {
					AffineTransform transform = computeAffineTransform(referencePoints, currentPoints);
					AffineTransform inverse = transform.createInverse();
					newImg = applyAffineTransformGPU(imgCurr, inverse);
				}

				String filename = seqCamData.getFileNameFromImageList(t);
				File file = new File(filename);
				Saver.saveImage(newImg, file, true);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}

			t += step;
		}
		return true;
	}

	private IcyBufferedImage applyAffineTransformGPU(IcyBufferedImage img, AffineTransform transform)
			throws Exception {
		int width = img.getWidth();
		int height = img.getHeight();
		int numChannels = img.getSizeC();

		IcyBufferedImage result = new IcyBufferedImage(width, height, numChannels, img.getDataType_());
		CLQueue queue = context.createDefaultQueue();

		for (int c = 0; c < numChannels; c++) {
			float[] inputData = Array1DUtil.arrayToFloatArray(img.getDataXY(c), img.isSignedDataType());
			float[] outputData = new float[width * height];

			CLBuffer<FloatBuffer> inputBuffer = context.createFloatBuffer(CLMem.Usage.Input,
					FloatBuffer.wrap(inputData), true);
			CLBuffer<FloatBuffer> outputBuffer = context.createFloatBuffer(CLMem.Usage.Output,
					outputData.length);

			// AffineTransform.getMatrix returns: [m00, m10, m01, m11, m02, m12]
			double[] matrix = new double[6];
			transform.getMatrix(matrix);

			// Kernel expects: m00, m01, m02 (row 0), m10, m11, m12 (row 1)
			kernelAffine.setArgs(inputBuffer, width, height, 
					(float) matrix[0], (float) matrix[2], (float) matrix[4],
					(float) matrix[1], (float) matrix[3], (float) matrix[5],
					outputBuffer);

			int[] globalWorkSizes = new int[] { width * height };
			kernelAffine.enqueueNDRange(queue, globalWorkSizes);

			queue.finish();

			outputBuffer.read(queue, FloatBuffer.wrap(outputData), true);
			
			// Safe conversion from float[] back to original image data type
			Object destArray = result.getDataXY(c);
			Array1DUtil.floatArrayToSafeArray(outputData, destArray, img.isSignedDataType());
			result.setDataXY(c, destArray);

			inputBuffer.release();
			outputBuffer.release();
		}

		queue.release();
		return result;
	}
	
	private IcyBufferedImage applyPerspectiveTransformGPU(IcyBufferedImage img, Homography H)
			throws Exception {
		int width = img.getWidth();
		int height = img.getHeight();
		int numChannels = img.getSizeC();

		IcyBufferedImage result = new IcyBufferedImage(width, height, numChannels, img.getDataType_());
		CLQueue queue = context.createDefaultQueue();

		for (int c = 0; c < numChannels; c++) {
			float[] inputData = Array1DUtil.arrayToFloatArray(img.getDataXY(c), img.isSignedDataType());
			float[] outputData = new float[width * height];

			CLBuffer<FloatBuffer> inputBuffer = context.createFloatBuffer(CLMem.Usage.Input,
					FloatBuffer.wrap(inputData), true);
			CLBuffer<FloatBuffer> outputBuffer = context.createFloatBuffer(CLMem.Usage.Output,
					outputData.length);

			kernelPerspective.setArgs(inputBuffer, width, height, 
					(float) H.h[0], (float) H.h[1], (float) H.h[2],
					(float) H.h[3], (float) H.h[4], (float) H.h[5],
					(float) H.h[6], (float) H.h[7], (float) H.h[8],
					outputBuffer);

			int[] globalWorkSizes = new int[] { width * height };
			kernelPerspective.enqueueNDRange(queue, globalWorkSizes);

			queue.finish();

			outputBuffer.read(queue, FloatBuffer.wrap(outputData), true);
			
			// Safe conversion from float[] back to original image data type
			Object destArray = result.getDataXY(c);
			Array1DUtil.floatArrayToSafeArray(outputData, destArray, img.isSignedDataType());
			result.setDataXY(c, destArray);

			inputBuffer.release();
			outputBuffer.release();
		}

		queue.release();
		return result;
	}

	private List<Point2D> extractPoints(Experiment exp) {
		List<Point2D> points = new ArrayList<>();
		for (Capillary cap : exp.getCapillaries().getList()) {
			Point2D p1 = cap.getCapillaryROIFirstPoint();
			if (p1 != null)
				points.add(p1);
			Point2D p2 = cap.getCapillaryROILastPoint();
			if (p2 != null)
				points.add(p2);
		}
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.getCageRoi2D() instanceof ROI2DPolygon) {
				ROI2DPolygon poly = (ROI2DPolygon) cage.getCageRoi2D();
				for (Point2D p : poly.getPoints()) {
					points.add(p);
				}
			}
		}
		return points;
	}

	private void trackPoints(IcyBufferedImage imgPrev, IcyBufferedImage imgCurr, List<Point2D> points) {
		int width = 32;
		int half = width / 2;

		for (int i = 0; i < points.size(); i++) {
			Point2D p = points.get(i);
			int x = (int) p.getX();
			int y = (int) p.getY();

			if (x - half < 0 || y - half < 0 || x + half >= imgPrev.getWidth()
					|| y + half >= imgPrev.getHeight())
				continue;

			IcyBufferedImage patchPrev = IcyBufferedImageUtil.getSubImage(imgPrev,
					new java.awt.Rectangle(x - half, y - half, width, width));
			IcyBufferedImage patchCurr = IcyBufferedImageUtil.getSubImage(imgCurr,
					new java.awt.Rectangle(x - half, y - half, width, width));

			Vector2d translation = new Vector2d();
			int n = 0;
			for (int c = 0; c < imgPrev.getSizeC(); c++) {
				translation.add(GaspardRigidRegistration.findTranslation2D(patchCurr, c, patchPrev, c));
				n++;
			}
			translation.scale(1.0 / n);

			double newX = p.getX() - translation.x;
			double newY = p.getY() - translation.y;

			p.setLocation(newX, newY);
		}
	}
	
	private Homography computeHomography(List<Point2D> src, List<Point2D> dst) {
		int n = src.size();
		if (n != 4) return null; // Only implemented for 4 points

		double[][] A = new double[8][8];
		double[] B = new double[8];

		for (int i = 0; i < n; i++) {
			double x = src.get(i).getX();
			double y = src.get(i).getY();
			double u = dst.get(i).getX();
			double v = dst.get(i).getY();

			A[2*i][0] = x; A[2*i][1] = y; A[2*i][2] = 1;
			A[2*i][3] = 0; A[2*i][4] = 0; A[2*i][5] = 0;
			A[2*i][6] = -x * u; A[2*i][7] = -y * u;
			B[2*i] = u;

			A[2*i+1][0] = 0; A[2*i+1][1] = 0; A[2*i+1][2] = 0;
			A[2*i+1][3] = x; A[2*i+1][4] = y; A[2*i+1][5] = 1;
			A[2*i+1][6] = -x * v; A[2*i+1][7] = -y * v;
			B[2*i+1] = v;
		}

		double[] h = solveGaussian(A, B);
		if (h == null) return null;

		return new Homography(new double[] { h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0 });
	}
	
	private double[] solveGaussian(double[][] A, double[] B) {
		int n = B.length;
		for (int k = 0; k < n; k++) {
			int max = k;
			for (int i = k + 1; i < n; i++)
				if (Math.abs(A[i][k]) > Math.abs(A[max][k]))
					max = i;

			double[] temp = A[k]; A[k] = A[max]; A[max] = temp;
			double t = B[k]; B[k] = B[max]; B[max] = t;

			if (Math.abs(A[k][k]) < 1e-10) return null;

			for (int i = k + 1; i < n; i++) {
				double factor = A[i][k] / A[k][k];
				B[i] -= factor * B[k];
				for (int j = k; j < n; j++)
					A[i][j] -= factor * A[k][j];
			}
		}

		double[] solution = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			double sum = 0.0;
			for (int j = i + 1; j < n; j++)
				sum += A[i][j] * solution[j];
			solution[i] = (B[i] - sum) / A[i][i];
		}
		return solution;
	}

	private AffineTransform computeAffineTransform(List<Point2D> srcPoints, List<Point2D> dstPoints) {
		int n = srcPoints.size();
		if (n < 3)
			return new AffineTransform();

		double sumX = 0, sumY = 0, sumX2 = 0, sumY2 = 0, sumXY = 0;
		double sumU = 0, sumV = 0, sumUX = 0, sumUY = 0, sumVX = 0, sumVY = 0;

		for (int i = 0; i < n; i++) {
			double x = srcPoints.get(i).getX();
			double y = srcPoints.get(i).getY();
			double u = dstPoints.get(i).getX();
			double v = dstPoints.get(i).getY();

			sumX += x;
			sumY += y;
			sumX2 += x * x;
			sumY2 += y * y;
			sumXY += x * y;

			sumU += u;
			sumV += v;
			sumUX += u * x;
			sumUY += u * y;

			sumVX += v * x;
			sumVY += v * y;
		}

		double[][] A = { { sumX2, sumXY, sumX }, { sumXY, sumY2, sumY }, { sumX, sumY, (double) n } };

		double[] B_u = { sumUX, sumUY, sumU };
		double[] B_v = { sumVX, sumVY, sumV };

		double[] sol_u = solve3x3(A, B_u);
		double[] sol_v = solve3x3(A, B_v);

		if (sol_u == null || sol_v == null)
			return new AffineTransform();

		return new AffineTransform(sol_u[0], sol_v[0], sol_u[1], sol_v[1], sol_u[2], sol_v[2]);
	}

	private double[] solve3x3(double[][] A, double[] B) {
		double det = det3x3(A);
		if (Math.abs(det) < 1e-9)
			return null;

		double detX = det3x3(replaceCol(A, B, 0));
		double detY = det3x3(replaceCol(A, B, 1));
		double detZ = det3x3(replaceCol(A, B, 2));

		return new double[] { detX / det, detY / det, detZ / det };
	}

	private double det3x3(double[][] m) {
		return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
				+ m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
	}

	private double[][] replaceCol(double[][] m, double[] col, int c) {
		double[][] res = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				res[i][j] = (j == c) ? col[i] : m[i][j];
			}
		}
		return res;
	}

	/**
	 * Releases GPU resources. Should be called when this object is no longer needed,
	 * or use try-with-resources for automatic cleanup.
	 */
	@Override
	public void close() {
		if (context != null) {
			context.release();
			context = null;
		}
	}
}