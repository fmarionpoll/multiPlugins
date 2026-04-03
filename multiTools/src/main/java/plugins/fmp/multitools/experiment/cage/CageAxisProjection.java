package plugins.fmp.multitools.experiment.cage;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import icy.roi.ROI2D;
import icy.type.geom.Polygon2D;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.kernel.roi.roi2d.ROI2DRectangle;

/**
 * Distance along the cage long axis (mm). Legacy modes use image Y and the ROI bounding box only.
 * Vertex modes use the long axis through midpoints of the two short sides of a quad, with
 * &quot;first&quot; = short side that contains the first stored vertex (index 0).
 */
public final class CageAxisProjection {

	/** Excel / chart: primary series vs opposite end ({@code L - s}). */
	public enum Anchor {
		TOP,
		BOTTOM
	}

	private static final double EPS = 1e-9;
	private static final double LEGACY_EPS = 1e-6;

	private final boolean legacy;
	private final double ux;
	private final double uy;
	private final double originMidPxX;
	private final double originMidPxY;
	private final double lengthMm;

	private CageAxisProjection(boolean legacy, double ux, double uy, double originMidPxX, double originMidPxY,
			double lengthMm) {
		this.legacy = legacy;
		this.ux = ux;
		this.uy = uy;
		this.originMidPxX = originMidPxX;
		this.originMidPxY = originMidPxY;
		this.lengthMm = lengthMm;
	}

	public static CageAxisProjection fromRoi(ROI2D roi, double mmPerPixelX, double mmPerPixelY,
			FlyPositionAxisReference ref) {
		if (roi == null || mmPerPixelX <= 0 || mmPerPixelY <= 0) {
			return invalid();
		}
		if (ref == null) {
			ref = FlyPositionAxisReference.LEGACY_IMAGE_TOP;
		}
		double sx = mmPerPixelX;
		double sy = mmPerPixelY;

		if (ref.isLegacyAabb()) {
			Rectangle r = roi.getBounds();
			boolean originAtBottom = ref == FlyPositionAxisReference.LEGACY_IMAGE_BOTTOM;
			return legacyFromBounds(r, sy, originAtBottom);
		}

		double[] vx;
		double[] vy;
		int n;
		if (roi instanceof ROI2DPolygon) {
			Polygon2D poly = ((ROI2DPolygon) roi).getPolygon2D();
			if (poly == null || poly.npoints < 3) {
				return legacyFromBounds(roi.getBounds(), sy, false);
			}
			n = poly.npoints;
			vx = new double[n];
			vy = new double[n];
			for (int i = 0; i < n; i++) {
				vx[i] = poly.xpoints[i];
				vy[i] = poly.ypoints[i];
			}
		} else if (roi instanceof ROI2DRectangle) {
			Rectangle r = roi.getBounds();
			n = 4;
			vx = new double[] { r.x, r.x + r.width, r.x + r.width, r.x };
			vy = new double[] { r.y, r.y, r.y + r.height, r.y + r.height };
		} else {
			return legacyFromBounds(roi.getBounds(), sy, false);
		}

		if (n == 4) {
			ShortSideMids mids = shortSideMidsThroughFirstVertex(vx, vy);
			if (mids != null) {
				CageAxisProjection q = fromQuadFirstSecond(mids.firstX, mids.firstY, mids.secondX, mids.secondY, vx, vy,
						sx, sy, ref);
				if (q != null && q.lengthMm > EPS) {
					return q;
				}
			}
		}
		return fromPcaCapsByImageY(vx, vy, n, sx, sy, ref);
	}

	private static final class ShortSideMids {
		final double firstX;
		final double firstY;
		final double secondX;
		final double secondY;

		ShortSideMids(double firstX, double firstY, double secondX, double secondY) {
			this.firstX = firstX;
			this.firstY = firstY;
			this.secondX = secondX;
			this.secondY = secondY;
		}
	}

	private static ShortSideMids shortSideMidsThroughFirstVertex(double[] vx, double[] vy) {
		double l01 = dist(vx[0], vy[0], vx[1], vy[1]);
		double l30 = dist(vx[3], vy[3], vx[0], vy[0]);
		if (Math.abs(l01 - l30) < LEGACY_EPS * Math.max(l01, l30)) {
			return null;
		}
		if (l01 < l30) {
			return new ShortSideMids((vx[0] + vx[1]) * 0.5, (vy[0] + vy[1]) * 0.5, (vx[2] + vx[3]) * 0.5,
					(vy[2] + vy[3]) * 0.5);
		}
		return new ShortSideMids((vx[3] + vx[0]) * 0.5, (vy[3] + vy[0]) * 0.5, (vx[1] + vx[2]) * 0.5,
				(vy[1] + vy[2]) * 0.5);
	}

	private static CageAxisProjection fromQuadFirstSecond(double fmx, double fmy, double smx, double smy, double[] vx,
			double[] vy, double sx, double sy, FlyPositionAxisReference ref) {
		double fmmx = fmx * sx;
		double fmmy = fmy * sy;
		double smmx = smx * sx;
		double smmy = smy * sy;
		double ox;
		double oy;
		double ux;
		double uy;
		if (ref == FlyPositionAxisReference.FIRST_SHORT_SIDE_AT_FIRST_VERTEX) {
			ox = fmx;
			oy = fmy;
			double ddx = smmx - fmmx;
			double ddy = smmy - fmmy;
			double norm = Math.hypot(ddx, ddy);
			if (norm < EPS) {
				return null;
			}
			ux = ddx / norm;
			uy = ddy / norm;
		} else {
			ox = smx;
			oy = smy;
			double ddx = fmmx - smmx;
			double ddy = fmmy - smmy;
			double norm = Math.hypot(ddx, ddy);
			if (norm < EPS) {
				return null;
			}
			ux = ddx / norm;
			uy = ddy / norm;
		}
		double ommx = ox * sx;
		double ommy = oy * sy;
		double lengthMm = 0;
		for (int i = 0; i < 4; i++) {
			double px = vx[i] * sx;
			double py = vy[i] * sy;
			double proj = (px - ommx) * ux + (py - ommy) * uy;
			lengthMm = Math.max(lengthMm, proj);
		}
		if (lengthMm < EPS) {
			return null;
		}
		return new CageAxisProjection(false, ux, uy, ox, oy, lengthMm);
	}

	private static CageAxisProjection fromPcaCapsByImageY(double[] vx, double[] vy, int n, double sx, double sy,
			FlyPositionAxisReference ref) {
		if (n < 3) {
			return null;
		}
		double mx = 0;
		double my = 0;
		for (int i = 0; i < n; i++) {
			mx += vx[i];
			my += vy[i];
		}
		mx /= n;
		my /= n;

		double cxx = 0;
		double cyy = 0;
		double cxy = 0;
		for (int i = 0; i < n; i++) {
			double dx = vx[i] - mx;
			double dy = vy[i] - my;
			cxx += dx * dx;
			cyy += dy * dy;
			cxy += dx * dy;
		}
		cxx /= n;
		cyy /= n;
		cxy /= n;

		double pux;
		double puy;
		if (Math.abs(cxy) < EPS * Math.max(cxx, cyy)) {
			if (cxx >= cyy) {
				pux = 1;
				puy = 0;
			} else {
				pux = 0;
				puy = 1;
			}
		} else {
			double theta = 0.5 * Math.atan2(2 * cxy, cxx - cyy);
			pux = Math.cos(theta);
			puy = Math.sin(theta);
		}

		double[] proj = new double[n];
		double minP = Double.POSITIVE_INFINITY;
		double maxP = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			double px = vx[i] * sx;
			double py = vy[i] * sy;
			double mxmm = mx * sx;
			double mymm = my * sy;
			proj[i] = (px - mxmm) * pux + (py - mymm) * puy;
			minP = Math.min(minP, proj[i]);
			maxP = Math.max(maxP, proj[i]);
		}

		double lowSumX = 0;
		double lowSumY = 0;
		int nLow = 0;
		double highSumX = 0;
		double highSumY = 0;
		int nHigh = 0;
		double tol = (maxP - minP) * 1e-6 + EPS;
		for (int i = 0; i < n; i++) {
			if (proj[i] <= minP + tol) {
				lowSumX += vx[i];
				lowSumY += vy[i];
				nLow++;
			}
			if (proj[i] >= maxP - tol) {
				highSumX += vx[i];
				highSumY += vy[i];
				nHigh++;
			}
		}
		if (nLow < 1 || nHigh < 1) {
			return null;
		}
		double lowAvX = lowSumX / nLow;
		double lowAvY = lowSumY / nLow;
		double highAvX = highSumX / nHigh;
		double highAvY = highSumY / nHigh;

		double capMinYX;
		double capMinYY;
		double capMaxYX;
		double capMaxYY;
		if (lowAvY <= highAvY) {
			capMinYX = lowAvX;
			capMinYY = lowAvY;
			capMaxYX = highAvX;
			capMaxYY = highAvY;
		} else {
			capMinYX = highAvX;
			capMinYY = highAvY;
			capMaxYX = lowAvX;
			capMaxYY = lowAvY;
		}

		double fmmx = capMinYX * sx;
		double fmmy = capMinYY * sy;
		double smmx = capMaxYX * sx;
		double smmy = capMaxYY * sy;

		double ox;
		double oy;
		double ux;
		double uy;
		if (ref == FlyPositionAxisReference.FIRST_SHORT_SIDE_AT_FIRST_VERTEX) {
			ox = capMinYX;
			oy = capMinYY;
			double ddx = smmx - fmmx;
			double ddy = smmy - fmmy;
			double norm = Math.hypot(ddx, ddy);
			if (norm < EPS) {
				return null;
			}
			ux = ddx / norm;
			uy = ddy / norm;
		} else {
			ox = capMaxYX;
			oy = capMaxYY;
			double ddx = fmmx - smmx;
			double ddy = fmmy - smmy;
			double norm = Math.hypot(ddx, ddy);
			if (norm < EPS) {
				return null;
			}
			ux = ddx / norm;
			uy = ddy / norm;
		}

		double ommx = ox * sx;
		double ommy = oy * sy;
		double lengthMm = 0;
		for (int i = 0; i < n; i++) {
			double px = vx[i] * sx;
			double py = vy[i] * sy;
			double p = (px - ommx) * ux + (py - ommy) * uy;
			lengthMm = Math.max(lengthMm, p);
		}
		if (lengthMm < EPS) {
			return null;
		}
		return new CageAxisProjection(false, ux, uy, ox, oy, lengthMm);
	}

	private static CageAxisProjection invalid() {
		return new CageAxisProjection(true, 0, 1, 0, 0, 0);
	}

	private static CageAxisProjection legacyFromBounds(Rectangle r, double sy, boolean originAtBottom) {
		if (r == null || r.height <= 0 || sy <= 0) {
			return invalid();
		}
		double lengthMm = r.height * sy;
		if (!originAtBottom) {
			return new CageAxisProjection(true, 0, 1, 0, r.y, lengthMm);
		}
		double yBottom = r.y + r.height;
		return new CageAxisProjection(true, 0, -1, 0, yBottom, lengthMm);
	}

	private static double dist(double x0, double y0, double x1, double y1) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		return Math.hypot(dx, dy);
	}

	public double positionMm(Point2D flyCenterPx, double mmPerPixelX, double mmPerPixelY, Anchor anchor, boolean clamp) {
		if (flyCenterPx == null || Double.isNaN(flyCenterPx.getX()) || Double.isNaN(flyCenterPx.getY())) {
			return Double.NaN;
		}
		if (legacy) {
			double flyMmY = flyCenterPx.getY() * mmPerPixelY;
			double originMmY = originMidPxY * mmPerPixelY;
			double s = (flyMmY - originMmY) * uy;
			double L = lengthMm;
			if (L <= EPS) {
				return Double.NaN;
			}
			if (clamp) {
				s = Math.min(Math.max(s, 0), L);
			}
			return anchor == Anchor.BOTTOM ? L - s : s;
		}
		double flyMmX = flyCenterPx.getX() * mmPerPixelX;
		double flyMmY = flyCenterPx.getY() * mmPerPixelY;
		double ommx = originMidPxX * mmPerPixelX;
		double ommy = originMidPxY * mmPerPixelY;
		double s = (flyMmX - ommx) * ux + (flyMmY - ommy) * uy;
		if (clamp) {
			s = Math.min(Math.max(s, 0), lengthMm);
		}
		return anchor == Anchor.BOTTOM ? lengthMm - s : s;
	}

	public boolean isLegacy() {
		return legacy;
	}
}
