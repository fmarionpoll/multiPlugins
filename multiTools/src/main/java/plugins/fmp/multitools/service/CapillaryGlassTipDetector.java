package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.util.Arrays;

import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;

/**
 * CODEX Local physical-wall termination and liquid-boundary measurements are
 * independent.
 */
final class CapillaryGlassTipDetector {
	static final class Evidence {
		Point2D glassTip;
		Point2D liquidTop;
	}

	static Evidence find(ImageData image, Point2D top, Point2D bottom, double halfWidth) {
		Evidence result = new Evidence();
		double length = top.distance(bottom);
		if (length < 60. || halfWidth < 2. || halfWidth > 8.)
			return result;
		double dx = (bottom.getX() - top.getX()) / length, dy = (bottom.getY() - top.getY()) / length;
		boolean greenLiquid = isGreenLiquid(image, top, bottom, halfWidth);
		int radius = 24, count = 65;
		double[] walls = new double[count], corners = new double[count], blue = new double[count];
		boolean[] valid = new boolean[count];
		for (int i = 0; i < count; i++) {
			double x = top.getX() + (i - radius) * dx, y = top.getY() + (i - radius) * dy;
			// A truncated search window does not invalidate a visible rim. Each
			// candidate still needs a complete outside/inside evidence window.
			double reach = halfWidth + 3.5;
			if (!canSample(image, x - dy * reach, y + dx * reach)
					|| !canSample(image, x + dy * reach, y - dx * reach)) continue;
			valid[i] = true;
			walls[i] = Math.min(wall(image, x, y, -dy, dx, -halfWidth), wall(image, x, y, -dy, dx, halfWidth));
			corners[i] = Math.min(cornerWall(image,x,y,-dy,dx,-halfWidth),cornerWall(image,x,y,-dy,dx,halfWidth));
			if (image.nChannels >= 3) {
				int p = (int) Math.round(x) + (int) Math.round(y) * image.width;
				blue[i] = CapillaryLengthDetector.liquidColorContrast(image.channels[0][p],image.channels[1][p],
						image.channels[2][p],greenLiquid);
			}
		}
		// Both narrow wall ridges must appear together and continue below the rim.
		// No core colour or mean tube/background brightness enters this decision.
		double best = 0.;
		// This pass extends through empty glass above the existing proposal;
		// it must not jump far down to a stronger liquid boundary.
		for (int i = 5; i <= radius + 2; i++) {
			if (!validWindow(valid, i - 5, i + 12)) continue;
			double outside = median(walls, i - 5, i - 1), inside = median(walls, i + 1, i + 8);
			if (inside < 1.5 || outside > .45 * inside || inside - outside < 1.)
				continue;
			int sustained = 0;
			for (int j = i + 1; j <= i + 12; j++)
				if (walls[j] > .5 * inside)
					sustained++;
			if (sustained < 10)
				continue;
			double quality = (inside - outside) / (1. + inside) * 1. / (1. + .03 * Math.abs(i - radius));
			if (quality > best) {
				best = quality;
				result.glassTip = new Point2D.Double(top.getX() + (i - radius) * dx, top.getY() + (i - radius) * dy);
			}
		}
		// A short empty neck has visible corner ridges, but they can disappear
		// once filled glass becomes a broad intensity step. Requiring twelve
		// narrow-ridge rows then mistakes the meniscus for the physical rim.
		{
			double bestCorner = 0.; Point2D cornerTip = null;
			for (int i = 5; i <= radius + 2; i++) {
				if (!validWindow(valid, i - 5, i + 8)) continue;
				double outside = median(corners, i - 5, i - 1), inside = median(corners, i, i + 2);
				if (inside < 2. || outside > .35 * inside || inside - outside < 1.5) continue;
				int sustained = 0;
				for (int j = i; j <= i + 3; j++) if (corners[j] > .5 * inside) sustained++;
				if (sustained < 2) continue;
				if (image.nChannels < 3) continue;
				double cap = 0.;
				for (int j = i - 1; j <= i + 1; j++)
					cap = Math.max(cap, rimCrossbar(image, top.getX() + (j-radius)*dx,
							top.getY() + (j-radius)*dy, dx, dy, halfWidth));
				boolean shortNeck = cap >= 1. && median(blue, i + 4, i + 8) >= 10.;
				boolean emptyNeck = false;
				if (inside >= 2.5 && validWindow(valid,i,i+24) && median(blue,i+12,i+24) >= 10.) {
					int continued = 0;
					for (int j=i;j<=i+7;j++) if (corners[j] > .5*inside) continued++;
					emptyNeck = continued >= 6;
				}
				if (!shortNeck && !emptyNeck) continue;
				double quality = (inside - outside) / (1. + inside) / (1. + .03 * Math.abs(i - radius));
				if (quality > bestCorner) {
					bestCorner = quality;
					cornerTip = new Point2D.Double(top.getX() + (i - radius) * dx, top.getY() + (i - radius) * dy);
				}
			}
			if (cornerTip != null && (result.glassTip == null || cornerTip.distance(bottom) > result.glassTip.distance(bottom) + 1.))
				result.glassTip = cornerTip;
		}
		// A chromatic transition is a separate diagnostic, never a glass-tip fallback.
		if (image.nChannels >= 3)
			for (int i = 5; i < count - 9; i++) {
				if (!validWindow(valid, i - 5, i + 8)) continue;
				double before = median(blue, i - 5, i - 1), after = median(blue, i + 1, i + 8);
				if (after > 10. && after - before > 8.) {
					result.liquidTop = new Point2D.Double(top.getX() + (i - radius) * dx,
							top.getY() + (i - radius) * dy);
					break;
				}
			}
		return result;
	}

	private static boolean isGreenLiquid(ImageData image, Point2D top, Point2D bottom, double halfWidth) {
		if(image.nChannels<3) return false;
		double length=top.distance(bottom),nx=(top.getY()-bottom.getY())/length,ny=(bottom.getX()-top.getX())/length;
		double[] strength=new double[2];
		for(int row=0;row<9;row++) {
			double t=.35+.3*row/8.;
			double x=top.getX()+t*(bottom.getX()-top.getX()),y=top.getY()+t*(bottom.getY()-top.getY());
			for(int signal=0;signal<2;signal++) {
				double contrast=0.;
				for(int side=-1;side<=1;side++) {
					double px=x+side*(halfWidth+3.)*nx,py=y+side*(halfWidth+3.)*ny;
					int ix=(int)Math.round(px),iy=(int)Math.round(py);
					if(ix<0||iy<0||ix>=image.width||iy>=image.height) continue;
					int p=ix+iy*image.width;
					contrast+=(side==0?1.:-.5)*CapillaryLengthDetector.liquidColorContrast(image.channels[0][p],
							image.channels[1][p],image.channels[2][p],signal==1);
				}
				strength[signal]+=Math.max(0.,contrast);
			}
		}
		return strength[1]>1.25*strength[0];
	}

	private static boolean canSample(ImageData image, double x, double y) {
		return x >= 0. && y >= 0. && x < image.width - 1. && y < image.height - 1.;
	}

	/** A transverse rim must be confined to the tube, rather than a rack/background line. */
	private static double rimCrossbar(ImageData image, double x, double y, double dx, double dy, double halfWidth) {
		double core = 0., above = 0., below = 0.; int count = 0;
		for (double u = -halfWidth + 1.; u <= halfWidth - 1.; u += .5) {
			double px = x - u*dy, py = y + u*dx;
			if (!canSample(image, px-2.*dx, py-2.*dy) || !canSample(image, px+2.*dx, py+2.*dy)) return 0.;
			core += sample(image,px,py);
			above += sample(image,px-2.*dx,py-2.*dy);
			below += sample(image,px+2.*dx,py+2.*dy); count++;
		}
		// A bright stripe between a background line and the liquid can mimic
		// a cap. Require the localized dark transverse cut edge for short necks;
		// bright side-wall evidence is handled independently above.
		double response = Math.min(above-core,below-core)/count;
		double outside = 0.;
		for (int side : new int[] {-1,1}) {
			double px = x - side*(halfWidth+3.)*dy, py = y + side*(halfWidth+3.)*dx;
			if (!canSample(image,px-2.*dx,py-2.*dy) || !canSample(image,px+2.*dx,py+2.*dy)) return 0.;
			double v = sample(image,px,py), a = sample(image,px-2.*dx,py-2.*dy), b = sample(image,px+2.*dx,py+2.*dy);
			outside += Math.max(0.,Math.max(Math.min(a-v,b-v),Math.min(v-a,v-b))) / 2.;
		}
		return Math.max(0.,response-outside);
	}

	private static boolean validWindow(boolean[] valid, int from, int to) {
		for (int i = from; i <= to; i++) if (!valid[i]) return false;
		return true;
	}

	private static double wall(ImageData image, double x, double y, double nx, double ny, double offset) {
		return ridge(image,x,y,nx,ny,offset,1.,false);
	}

	private static double cornerWall(ImageData image, double x, double y, double nx, double ny, double offset) {
		return ridge(image,x,y,nx,ny,offset,2.,true);
	}

	private static double ridge(ImageData image, double x, double y, double nx, double ny, double offset,
			double search, boolean bright) {
		double best = 0.;
		for (double d = -search; d <= search; d += .5) {
			double u = offset + d;
			double centre = sample(image, x + u * nx, y + u * ny);
			double a = sample(image, x + (u - 1.5) * nx, y + (u - 1.5) * ny);
			double b = sample(image, x + (u + 1.5) * nx, y + (u + 1.5) * ny);
			best = Math.max(best, Math.min(a - centre, b - centre));
			if (bright) best = Math.max(best, Math.min(centre - a, centre - b));
		}
		return best;
	}

	private static double sample(ImageData image, double x, double y) {
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		double fx = x - ix, fy = y - iy, value = 0.;
		for (int c = 0; c < image.nChannels; c++) {
			double[] p = image.channels[c];
			int k = ix + iy * image.width;
			value += (1 - fy) * ((1 - fx) * p[k] + fx * p[k + 1])
					+ fy * ((1 - fx) * p[k + image.width] + fx * p[k + image.width + 1]);
		}
		return value / image.nChannels;
	}

	private static double median(double[] values, int from, int to) {
		double[] copy = Arrays.copyOfRange(values, from, to + 1);
		Arrays.sort(copy);
		return .5 * (copy[(copy.length - 1) / 2] + copy[copy.length / 2]);
	}
}
