package plugins.fmp.multitools.experiment.cage;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

/**
 * Distance from fly center (px) to food side of a quadrilateral cage, in mm.
 */
public final class CageFoodDistanceMm {

	public static final double AMBIGUOUS_EDGE_LENGTH_RATIO = 0.90;

	private CageFoodDistanceMm() {
	}

	public static boolean isAmbiguousQuad(ROI2D roi) {
		double[] vx = new double[4];
		double[] vy = new double[4];
		if (!fillQuadVertices(roi, vx, vy)) {
			return true;
		}
		double e0 = edgeLen(vx, vy, 0, 1);
		double e1 = edgeLen(vx, vy, 1, 2);
		double e2 = edgeLen(vx, vy, 2, 3);
		double e3 = edgeLen(vx, vy, 3, 0);
		double pairA = (e0 + e2) * 0.5;
		double pairB = (e1 + e3) * 0.5;
		double lo = Math.min(pairA, pairB);
		double hi = Math.max(pairA, pairB);
		if (hi <= 1e-9) {
			return true;
		}
		return lo / hi >= AMBIGUOUS_EDGE_LENGTH_RATIO;
	}

	private static boolean fillQuadVertices(ROI2D roi, double[] vx, double[] vy) {
		if (roi == null || vx == null || vy == null || vx.length < 4 || vy.length < 4) {
			return false;
		}
		if (roi instanceof ROI2DPolygon) {
			Polygon2D poly = ((ROI2DPolygon) roi).getPolygon2D();
			if (poly == null || poly.npoints != 4) {
				return false;
			}
			for (int i = 0; i < 4; i++) {
				vx[i] = poly.xpoints[i];
				vy[i] = poly.ypoints[i];
			}
			return true;
		}
		if (roi instanceof ROI2DRectangle) {
			Rectangle r = roi.getBounds();
			vx[0] = r.x;
			vy[0] = r.y;
			vx[1] = r.x + r.width;
			vy[1] = r.y;
			vx[2] = r.x + r.width;
			vy[2] = r.y + r.height;
			vx[3] = r.x;
			vy[3] = r.y + r.height;
			return true;
		}
		return false;
	}

	private static double edgeLen(double[] vx, double[] vy, int a, int b) {
		double dx = vx[b] - vx[a];
		double dy = vy[b] - vy[a];
		return Math.hypot(dx, dy);
	}

	/**
	 * @param flyCenterPx center of fly in image pixels (same space as ROI)
	 */
	public static double distanceFromFoodMm(ROI2D roi, Point2D flyCenterPx, double mmPerPixelX, double mmPerPixelY,
			FoodSide foodSide) {
		if (roi == null || flyCenterPx == null || mmPerPixelX <= 0 || mmPerPixelY <= 0) {
			return Double.NaN;
		}
		double[] vx = new double[4];
		double[] vy = new double[4];
		if (!fillQuadVertices(roi, vx, vy)) {
			return legacyAabbDistanceMm(roi, flyCenterPx, mmPerPixelX, mmPerPixelY, foodSide);
		}

		// Determine which edges are "horizontal-ish" vs "vertical-ish" using the edge direction
		// in physical units (mm). This is robust for wide vs tall cages and for rotated quads.
		double[] midX = new double[4];
		double[] midY = new double[4];
		double[] horizScore = new double[4];
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			midX[i] = (vx[i] + vx[j]) * 0.5;
			midY[i] = (vy[i] + vy[j]) * 0.5;
			double dxMm = (vx[j] - vx[i]) * mmPerPixelX;
			double dyMm = (vy[j] - vy[i]) * mmPerPixelY;
			horizScore[i] = Math.abs(dxMm) - Math.abs(dyMm); // >0 means more horizontal than vertical
		}

		// Pick the 2 most horizontal edges, the remaining 2 are vertical.
		int h0 = 0, h1 = 1, h2 = 2, h3 = 3;
		// Sort indices by horizScore descending (manual 4-item sort to avoid allocations).
		int[] idx = new int[] { h0, h1, h2, h3 };
		for (int a = 0; a < idx.length - 1; a++) {
			for (int b = a + 1; b < idx.length; b++) {
				if (horizScore[idx[b]] > horizScore[idx[a]]) {
					int tmp = idx[a];
					idx[a] = idx[b];
					idx[b] = tmp;
				}
			}
		}
		int heA = idx[0];
		int heB = idx[1];
		int veA = idx[2];
		int veB = idx[3];

		int topEdge = midY[heA] <= midY[heB] ? heA : heB;
		int bottomEdge = topEdge == heA ? heB : heA;
		int leftEdge = midX[veA] <= midX[veB] ? veA : veB;
		int rightEdge = leftEdge == veA ? veB : veA;

		int foodEdge = foodSide == FoodSide.TOP ? topEdge
				: foodSide == FoodSide.BOTTOM ? bottomEdge
						: foodSide == FoodSide.LEFT ? leftEdge : rightEdge;

		double cx = (vx[0] + vx[1] + vx[2] + vx[3]) * 0.25;
		double cy = (vy[0] + vy[1] + vy[2] + vy[3]) * 0.25;
		int a = foodEdge;
		int b = (foodEdge + 1) % 4;
		double mx = (vx[a] + vx[b]) * 0.5;
		double my = (vy[a] + vy[b]) * 0.5;
		double dxMm = (cx - mx) * mmPerPixelX;
		double dyMm = (cy - my) * mmPerPixelY;
		double nrm = Math.hypot(dxMm, dyMm);
		if (nrm < 1e-12) {
			return Double.NaN;
		}
		double ux = dxMm / nrm;
		double uy = dyMm / nrm;
		double fxMm = flyCenterPx.getX() * mmPerPixelX;
		double fyMm = flyCenterPx.getY() * mmPerPixelY;
		double mxMm = mx * mmPerPixelX;
		double myMm = my * mmPerPixelY;
		return (fxMm - mxMm) * ux + (fyMm - myMm) * uy;
	}

	private static double legacyAabbDistanceMm(ROI2D roi, Point2D flyCenterPx, double mmPerPixelX, double mmPerPixelY,
			FoodSide foodSide) {
		Rectangle r = roi.getBounds();
		double fx = flyCenterPx.getX() * mmPerPixelX;
		double fy = flyCenterPx.getY() * mmPerPixelY;
		double x0 = r.x * mmPerPixelX;
		double y0 = r.y * mmPerPixelY;
		double x1 = (r.x + r.width) * mmPerPixelX;
		double y1 = (r.y + r.height) * mmPerPixelY;
		switch (foodSide) {
		case TOP:
			return fy - y0;
		case BOTTOM:
			return y1 - fy;
		case LEFT:
			return fx - x0;
		case RIGHT:
			return x1 - fx;
		default:
			return Double.NaN;
		}
	}
}
