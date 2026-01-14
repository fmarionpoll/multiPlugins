package plugins.fmp.multitools.fmp_tools.registration;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Vector2d;

import icy.file.Saver;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.sequence.Sequence;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;

public class ImageRegistrationFeatures extends ImageRegistration {

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
		
		public void transform(double x, double y, Point2D.Double out) {
			double z = h[6] * x + h[7] * y + h[8];
			out.x = (h[0] * x + h[1] * y + h[2]) / z;
			out.y = (h[3] * x + h[4] * y + h[5]) / z;
		}
	}

	@Override
	public boolean runRegistration(Experiment exp, int referenceFrame, int startFrame, int endFrame, boolean reverse) {
		if (!doBackup(exp))
			return false;

		SequenceCamData seqCamData = exp.getSeqCamData();
		Sequence seq = seqCamData.getSequence();

		List<Point2D> referencePoints = extractPoints(exp);
		List<Point2D> currentPoints = new ArrayList<Point2D>();
		for (Point2D p : referencePoints)
			currentPoints.add((Point2D) p.clone());

		int step = reverse ? -1 : 1;
		int start = reverse ? endFrame : startFrame;
		int end = reverse ? startFrame : endFrame;

		// Check if we have enough points for Homography (need exactly 4 for unique solution with this simple solver)
		// or use Affine for < 4 (though GUI usually enforces 4)
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
					// Compute Homography: Src (Current) -> Dst (Reference)
					// We want to warp Current to Reference
					Homography H = computeHomography(currentPoints, referencePoints);
					
					// To warp, we iterate over Destination (Reference) pixels and sample from Source (Current)
					// So we need Inverse Homography: Dst -> Src
					// Wait, computeHomography(current, reference) gives transformation T such that T(current) = reference.
					// So if we iterate over reference pixel (x,y), we want to find (u,v) in current image.
					// The relation is (x,y) = H * (u,v)  => (u,v) = H^-1 * (x,y)
					// Actually, standard warp implementation iterates over destination pixels (x,y)
					// and maps them BACK to source pixels (u,v) to sample.
					// If H maps Source -> Dest, then we need H^-1.
					
					// Let's define computeHomography(src, dst) as mapping src points to dst points.
					// Then H maps Current -> Reference.
					// For warping, for each pixel in Reference (Output), we need coordinate in Current (Input).
					// So we need Inverse(H).
					
					if (H == null) {
						// Fallback
						newImg = imgCurr; 
					} else {
						Homography invH = H.invert();
						newImg = warpPerspective(imgCurr, invH);
					}
				} else {
					// Fallback to Affine for != 4 points
					AffineTransform transform = computeAffineTransform(referencePoints, currentPoints);
					AffineTransform inverse = transform.createInverse();

					newImg = new IcyBufferedImage(imgCurr.getWidth(), imgCurr.getHeight(),
							imgCurr.getSizeC(), imgCurr.getDataType_());

					for (int c = 0; c < imgCurr.getSizeC(); c++) {
						BufferedImage awtSrc = imgCurr.getImage(c);
						int w = awtSrc.getWidth();
						int h = awtSrc.getHeight();
						int type = awtSrc.getType();
						if (type == 0 || type == BufferedImage.TYPE_CUSTOM) {
							type = BufferedImage.TYPE_INT_RGB;
						}
						BufferedImage awtDst = new BufferedImage(w, h, type);
						Graphics2D g2 = awtDst.createGraphics();

						g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

						g2.setTransform(inverse);
						g2.drawImage(awtSrc, 0, 0, null);
						g2.dispose();

						IcyBufferedImage tempWrapper = IcyBufferedImage.createFrom(awtDst);
						newImg.setDataXY(c, tempWrapper.getDataXY(0));
					}
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

	private List<Point2D> extractPoints(Experiment exp) {
		ArrayList<Point2D> points = exp.getSeqCamData().getReferenceROI2DPolygon().getPoints();
		return points;
	}

	private void trackPoints(IcyBufferedImage imgPrev, IcyBufferedImage imgCurr, List<Point2D> points) {
		int width = 32;
		int half = width / 2;

		for (int i = 0; i < points.size(); i++) {
			Point2D p = points.get(i);
			int x = (int) p.getX();
			int y = (int) p.getY();

			if (x - half < 0 || y - half < 0 || x + half >= imgPrev.getWidth() || y + half >= imgPrev.getHeight())
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

	// --- Homography Logic ---

	private Homography computeHomography(List<Point2D> src, List<Point2D> dst) {
		// Solves H * src = dst
		// For 4 points, we have 8 equations.
		// Matrix equation Ah = b is not quite right because of the homogeneous division.
		// h0*x + h1*y + h2 - h6*x*u - h7*y*u = u
		// h3*x + h4*y + h5 - h6*x*v - h7*y*v = v
		// where (x,y) is src, (u,v) is dst. h8 = 1.
		
		int n = src.size();
		if (n != 4) return null; // Only implemented for 4 points

		double[][] A = new double[8][8];
		double[] B = new double[8];

		for (int i = 0; i < n; i++) {
			double x = src.get(i).getX();
			double y = src.get(i).getY();
			double u = dst.get(i).getX();
			double v = dst.get(i).getY();

			// Eq 1 for point i
			A[2*i][0] = x;
			A[2*i][1] = y;
			A[2*i][2] = 1;
			A[2*i][3] = 0;
			A[2*i][4] = 0;
			A[2*i][5] = 0;
			A[2*i][6] = -x * u;
			A[2*i][7] = -y * u;
			B[2*i] = u;

			// Eq 2 for point i
			A[2*i+1][0] = 0;
			A[2*i+1][1] = 0;
			A[2*i+1][2] = 0;
			A[2*i+1][3] = x;
			A[2*i+1][4] = y;
			A[2*i+1][5] = 1;
			A[2*i+1][6] = -x * v;
			A[2*i+1][7] = -y * v;
			B[2*i+1] = v;
		}

		double[] h = solveGaussian(A, B);
		if (h == null) return null;

		return new Homography(new double[] { h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0 });
	}

	private double[] solveGaussian(double[][] A, double[] B) {
		int n = B.length;
		for (int k = 0; k < n; k++) {
			// Find pivot
			int max = k;
			for (int i = k + 1; i < n; i++)
				if (Math.abs(A[i][k]) > Math.abs(A[max][k]))
					max = i;

			double[] temp = A[k]; A[k] = A[max]; A[max] = temp;
			double t = B[k]; B[k] = B[max]; B[max] = t;

			if (Math.abs(A[k][k]) < 1e-10) return null; // Singular

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

	private IcyBufferedImage warpPerspective(IcyBufferedImage src, Homography invH) {
		int width = src.getWidth();
		int height = src.getHeight();
		int numC = src.getSizeC();
		
		IcyBufferedImage dst = new IcyBufferedImage(width, height, numC, src.getDataType_());
		
		Point2D.Double srcPt = new Point2D.Double();
		
		// Pre-fetch data arrays for speed? Or use getPixel/setPixel (slow)
		// Direct array access is much faster.
		
		for (int c = 0; c < numC; c++) {
			Object srcData = src.getDataXY(c);
			Object dstData = dst.getDataXY(c);
			boolean isSigned = src.isSignedDataType();
			
			// Convert to float for processing
			float[] srcArr = Array1DUtil.arrayToFloatArray(srcData, isSigned);
			float[] dstArr = new float[width * height];
			
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					// Map dst pixel (x,y) to src pixel (u,v)
					invH.transform(x, y, srcPt);
					double u = srcPt.x;
					double v = srcPt.y;
					
					// Bilinear Interpolation
					if (u >= 0 && v >= 0 && u < width - 1 && v < height - 1) {
						int x0 = (int) u;
						int y0 = (int) v;
						double dx = u - x0;
						double dy = v - y0;
						
						int idx00 = x0 + y0 * width;
						int idx10 = (x0 + 1) + y0 * width;
						int idx01 = x0 + (y0 + 1) * width;
						int idx11 = (x0 + 1) + (y0 + 1) * width;
						
						float val00 = srcArr[idx00];
						float val10 = srcArr[idx10];
						float val01 = srcArr[idx01];
						float val11 = srcArr[idx11];
						
						float val0 = (float) (val00 * (1 - dx) + val10 * dx);
						float val1 = (float) (val01 * (1 - dx) + val11 * dx);
						float val = (float) (val0 * (1 - dy) + val1 * dy);
						
						dstArr[x + y * width] = val;
					} else {
						dstArr[x + y * width] = 0; // Black outside
					}
				}
			}
			
			Array1DUtil.floatArrayToArray(dstArr, dstData);
		}
		
		return dst;
	}

	// --- End Homography Logic ---

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

}