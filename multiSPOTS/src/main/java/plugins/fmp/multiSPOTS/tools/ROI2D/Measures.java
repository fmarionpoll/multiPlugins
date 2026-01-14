package plugins.fmp.multiSPOTS.tools.ROI2D;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageUtil;
import icy.math.MathUtil;
import icy.roi.BooleanMask2D;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.roi.ROIIterator;
import icy.sequence.Sequence;
import icy.type.collection.array.ArrayUtil;
import icy.type.point.Point5D;
import icy.type.rectangle.Rectangle5D;
import plugins.fmp.multiSPOTS.experiment.spots.Spot;
import plugins.fmp.multiSPOTS.series.BuildSeriesOptions;
import plugins.kernel.roi.roi2d.ROI2DPoint;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi3d.ROI3DPoint;

// copy of ROIEllipsoidFittingDescriptor from package plugins.adufour.roi;
// copy of ROIMassCenterDescriptorsPlugin from package plugins.adufour.vars.lang;

public class Measures {

	/*
	 * @param roi the {@link ROI} we want to compute orientation information
	 * 
	 * @param sequence sequence used to retrieve pixel size information
	 * 
	 * @return A 12-value array describing the ellipse fit as follows:<br> <ul>
	 * <li>index 0: diameter along the first principle axis</li> <li>index 1:
	 * diameter along the second principle axis</li> <li>index 2: diameter along the
	 * third principle axis (0 in 2D)</li> <li>index 3: the X component of the first
	 * principle axis vector</li> <li>index 4: the Y component of the first
	 * principle axis vector</li> <li>index 5: the Z component of the first
	 * principle axis vector (0 in 2D)</li> <li>index 6: the X component of the
	 * second principle axis vector</li> <li>index 7: the Y component of the second
	 * principle axis vector</li> <li>index 8: the Z component of the second
	 * principle axis vector (0 in 2D)</li> <li>index 9: the X component of the
	 * third principle axis vector (0 in 2D)</li> <li>index 10: the Y component of
	 * the third principle axis vector (0 in 2D)</li> <li>index 11: the Z component
	 * of the third principle axis vector (1 in 2D)</li> </ul>
	 * 
	 * @throws InterruptedException if thread was interrupted
	 */
	public static double[] computeOrientation(ROI roi, Sequence sequence) throws InterruptedException {
		double[] ellipse = new double[12];

		if (roi instanceof ROI2D) {
			try {
				Point2d radii = new Point2d();
				Vector2d[] eigenVectors = new Vector2d[2];
				fitEllipse((ROI2D) roi, null, radii, null, eigenVectors, null);

				// convert from radius to diameter
				radii.scale(2.0);

				// diameters
				ellipse[0] = radii.x;
				ellipse[1] = radii.y;

				// vectors
				Vector2d firstAxis = eigenVectors[0];
				ellipse[3] = MathUtil.round(firstAxis.x, 2);
				ellipse[4] = MathUtil.round(firstAxis.y, 2);
				Vector2d secondAxis = eigenVectors[1];
				ellipse[6] = MathUtil.round(secondAxis.x, 2);
				ellipse[7] = MathUtil.round(secondAxis.y, 2);
				// 3rd axis is always normal to the XY plane
				ellipse[11] = 1.0;
			} catch (RuntimeException e) {
				// System.err.println("Warning: could not fit ellipse on ROI \"" + roi.getName()
				// + "\": " + e.getMessage());
				Arrays.fill(ellipse, Double.NaN);
			}
		} else {
			System.err.println("Cannot compute ellipse dimensions for ROI of type: " + roi.getClassName());
			Arrays.fill(ellipse, Double.NaN);
		}

		if (sequence != null) {
			// convert to real units
			ellipse[0] *= sequence.getPixelSizeX();
			ellipse[1] *= sequence.getPixelSizeY();
			ellipse[2] *= sequence.getPixelSizeZ();
		}

		Arrays.sort(ellipse, 0, 3);
		// sorting is in ascending order, but we need the largest diameter first
		double tmp = ellipse[0];
		ellipse[0] = ellipse[2];
		ellipse[2] = tmp;

		return ellipse;
	}

	/*
	 * 2D direct ellipse fitting.<br> (Java port of Chernov's MATLAB implementation
	 * of the direct ellipse fit)
	 * 
	 * @param roi the component to fit
	 * 
	 * @param center (set to null if not wanted) the calculated ellipse center
	 * 
	 * @param radii (set to null if not wanted) the calculated ellipse radius in
	 * each eigen-direction
	 * 
	 * @param angle (set to null if not wanted) the calculated ellipse orientation
	 * 
	 * @param equation (set to null if not wanted) a 6-element array, {a b c d f g},
	 * which are the calculated algebraic parameters of the fitting ellipse:
	 * <i>ax</i><sup>2</sup> + 2 <i>bxy</i> + <i>cy</i><sup>2</sup> +2<i>dx</i> +
	 * 2<i>fy</i> + <i>g</i> = 0. The vector <b>A</b> represented in the array is
	 * normed, so that ||<b>A</b>||=1.
	 * 
	 * @throws RuntimeException if the ellipse calculation fails (e.g. if a singular
	 * matrix is detected)
	 * 
	 * @throws InterruptedException
	 */
	public static void fitEllipse(ROI2D roi, Point2d center, Point2d radii, Double angle, Vector2d[] eigenVectors,
			double[] equation) throws RuntimeException, InterruptedException {
		Point[] points = roi.getBooleanMask(true).getContourPoints();

		if (points.length < 4)
			return;

		final Point2D ccenter = computeMassCenter(roi).toPoint2D();
		final double cx = ccenter.getX();
		final double cy = ccenter.getY();

		final double[][] d1 = new double[points.length][3];
		final double[][] d2 = new double[points.length][3];

		for (int i = 0; i < d1.length; i++) {
			final double xixC = points[i].x - cx;
			final double yiyC = points[i].y - cy;

			d1[i][0] = xixC * xixC;
			d1[i][1] = xixC * yiyC;
			d1[i][2] = yiyC * yiyC;

			d2[i][0] = xixC;
			d2[i][1] = yiyC;
			d2[i][2] = 1;
		}

		final Matrix D1 = new Matrix(d1);
		final Matrix D2 = new Matrix(d2);

		final Matrix S1 = D1.transpose().times(D1);
		final Matrix S2 = D1.transpose().times(D2);
		final Matrix S3 = D2.transpose().times(D2);

		final Matrix T = (S3.inverse().times(-1)).times(S2.transpose());
		final Matrix M = S1.plus(S2.times(T));

		final double[][] m = M.getArray();
		final double[][] n = { { m[2][0] / 2, m[2][1] / 2, m[2][2] / 2 }, { -m[1][0], -m[1][1], -m[1][2] },
				{ m[0][0] / 2, m[0][1] / 2, m[0][2] / 2 } };

		final Matrix N = new Matrix(n);

		final EigenvalueDecomposition E = N.eig();
		final Matrix eVec = E.getV();

		final Matrix R1 = eVec.getMatrix(0, 0, 0, 2);
		final Matrix R2 = eVec.getMatrix(1, 1, 0, 2);
		final Matrix R3 = eVec.getMatrix(2, 2, 0, 2);

		final Matrix cond = (R1.times(4)).arrayTimes(R3).minus(R2.arrayTimes(R2));

		int _f = 0;
		for (int i = 0; i < 3; i++) {
			if (cond.get(0, i) > 0) {
				_f = i;
				break;
			}
		}

		Matrix A1 = eVec.getMatrix(0, 2, _f, _f);
		Matrix A = new Matrix(6, 1);

		A.setMatrix(0, 2, 0, 0, A1);
		A.setMatrix(3, 5, 0, 0, T.times(A1));

		double[] ell = A.getColumnPackedCopy();

		final double a4 = ell[3] - 2 * ell[0] * cx - ell[1] * cy;
		final double a5 = ell[4] - 2 * ell[2] * cy - ell[1] * cx;
		final double a6 = ell[5] + ell[0] * cx * cx + ell[2] * cy * cy + ell[1] * cx * cy - ell[3] * cx - ell[4] * cy;

		A.set(3, 0, a4);
		A.set(4, 0, a5);
		A.set(5, 0, a6);

		A = A.times(1 / A.normF());
		ell = A.getColumnPackedCopy();

		if (equation != null && equation.length != 6)
			System.arraycopy(ell, 0, equation, 0, 6);

		// Convert the general ellipse equation ax2 + bxy + cy2 + dx + fy + g = 0
		// into geometric parameters: center, radii and orientation.
		final double a = ell[0];
		final double b = ell[1] / 2;
		final double c = ell[2];
		final double d = ell[3] / 2;
		final double f = ell[4] / 2;
		final double g = ell[5];

		// centre
		final double cX = (c * d - b * f) / (b * b - a * c);
		final double cY = (a * f - b * d) / (b * b - a * c);

		// semi-axis length
		final double af = 2 * (a * f * f + c * d * d + g * b * b - 2 * b * d * f - a * c * g);
		final double aL = Math.sqrt((af) / ((b * b - a * c) * (Math.sqrt((a - c) * (a - c) + 4 * b * b) - (a + c))));
		final double bL = Math.sqrt((af) / ((b * b - a * c) * (-Math.sqrt((a - c) * (a - c) + 4 * b * b) - (a + c))));
		double phi = 0;

		if (b == 0) {
			if (Math.abs(a) <= Math.abs(c))
				phi = 0;
			else
				phi = Math.PI / 2;
		} else {
			if (Math.abs(a) <= Math.abs(c))
				phi = Math.atan(2 * b / (a - c)) / 2;
			else
				phi = Math.atan(2 * b / (a - c)) / 2 + Math.PI / 2;
		}

		if (center != null)
			center.set(cX, cY);
		if (radii != null)
			radii.set(aL, bL);
		if (angle != null)
			angle = Double.valueOf(phi);

		if (eigenVectors != null) {
			eigenVectors[0] = new Vector2d(Math.cos(phi), Math.sin(phi));
			eigenVectors[1] = new Vector2d(Math.cos(phi + Math.PI / 2), Math.sin(phi + Math.PI / 2));
		}
	}

	/**
	 * Compute and returns the mass center of specified ROI.
	 */
	public static Point5D computeMassCenter(ROI roi) throws InterruptedException {
		final Rectangle5D bounds = roi.getBounds5D();

		// special case of empty bounds ? --> return position
		if (bounds.isEmpty())
			return bounds.getPosition();
		// special case of single point ? --> return position
		if ((roi instanceof ROI2DPoint) || (roi instanceof ROI3DPoint))
			return bounds.getPosition();

		final ROIIterator it = new ROIIterator(roi, true);
		double x, y, z, t, c;
		long numPts;

		x = 0d;
		y = 0d;
		z = 0d;
		t = 0d;
		c = 0d;
		numPts = 0;
		while (!it.done()) {
			// check for interruption sometime
			if (((numPts & 0xFFFF) == 0) && Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}

			x += it.getX();
			y += it.getY();
			z += it.getZ();
			t += it.getT();
			c += it.getC();

			it.next();
			numPts++;
		}

		if (numPts == 0)
			return new Point5D.Double();

		return new Point5D.Double(x / numPts, y / numPts, z / numPts, t / numPts, c / numPts);
	}

	public static ROI2DPolygon getContourOfDetectedSpot(IcyBufferedImage workImage, Spot spot,
			BuildSeriesOptions options) {
		BooleanMask2D mask2d = getMaskOfThresholdedImage(workImage, spot, options);
		List<Point> points = getLargestContourFromThresholdedImage(mask2d);
		if (points != null) {
			List<Point2D> points2s = points.stream().map(point -> new Point2D.Double(point.getX(), point.getY()))
					.collect(Collectors.toList());
			ROI2DPolygon roi = new ROI2DPolygon(points2s);
			return roi;
		} 
		return null;
	}

	private static BooleanMask2D getMaskOfThresholdedImage(IcyBufferedImage workImage, Spot spot,
			BuildSeriesOptions options) {
		boolean spotThresholdUp = options.spotThresholdUp;
		int spotThreshold = options.spotThreshold;
		Rectangle rectSpot = spot.mask2DSpot.bounds;
		IcyBufferedImage subWorkImage = IcyBufferedImageUtil.getSubImage(workImage, rectSpot);
		boolean[] mask = spot.mask2DSpot.mask;
		int[] workData = (int[]) ArrayUtil.arrayToIntArray(subWorkImage.getDataXY(0), workImage.isSignedDataType());

		if (spotThresholdUp) {
			for (int offset = 0; offset < workData.length; offset++) {
				if (mask[offset])
					mask[offset] = (workData[offset] < spotThreshold);
			}
		} else {
			for (int offset = 0; offset < workData.length; offset++) {
				if (mask[offset])
					mask[offset] = (workData[offset] > spotThreshold);
			}
		}

		return new BooleanMask2D(rectSpot, mask);
	}

	private static List<Point> getLargestContourFromThresholdedImage(BooleanMask2D mask2d) {
		List<Point> points = null;
		BooleanMask2D[] components = null;
		int maxPoints = 0;
		
		try {
			components = mask2d.getComponents();
			int itemMax = 0;
			if (components.length > 0) {
				for (int i = 0; i < components.length; i++) {
					BooleanMask2D comp = components[i];
					if (comp.getNumberOfPoints() > maxPoints) {
						itemMax = i;
						maxPoints = comp.getNumberOfPoints();
					}
				}
			}
			if (maxPoints > 0)
				points = components[itemMax].getConnectedContourPoints();
			else
				System.out.println("unsuccessful detection of spot limits");
		} catch (InterruptedException e) {
//				 TODO Auto-generated catch block
			e.printStackTrace();
		}
		return points;
	}

}
