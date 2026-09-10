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
		int radius = 24, count = 65;
		double[] walls = new double[count], blue = new double[count];
		for (int i = 0; i < count; i++) {
			double x = top.getX() + (i - radius) * dx, y = top.getY() + (i - radius) * dy;
			if (x < 12 || y < 12 || x >= image.width - 12 || y >= image.height - 12)
				return result;
			walls[i] = Math.min(wall(image, x, y, -dy, dx, -halfWidth), wall(image, x, y, -dy, dx, halfWidth));
			if (image.nChannels >= 3) {
				int p = (int) Math.round(x) + (int) Math.round(y) * image.width;
				blue[i] = .5 * (image.channels[1][p] + image.channels[2][p]) - image.channels[0][p];
			}
		}
		// Both narrow wall ridges must appear together and continue below the rim.
		// No core colour or mean tube/background brightness enters this decision.
		double best = 0.;
		// This pass extends through empty glass above the existing proposal;
		// it must not jump far down to a stronger liquid boundary.
		for (int i = 5; i <= radius + 2; i++) {
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
		// A chromatic transition is a separate diagnostic, never a glass-tip fallback.
		if (image.nChannels >= 3)
			for (int i = 5; i < count - 9; i++) {
				double before = median(blue, i - 5, i - 1), after = median(blue, i + 1, i + 8);
				if (after > 10. && after - before > 8.) {
					result.liquidTop = new Point2D.Double(top.getX() + (i - radius) * dx,
							top.getY() + (i - radius) * dy);
					break;
				}
			}
		return result;
	}

	private static double wall(ImageData image, double x, double y, double nx, double ny, double offset) {
		double best = 0.;
		for (double d = -1.; d <= 1.; d += .5) {
			double u = offset + d;
			double centre = sample(image, x + u * nx, y + u * ny);
			double a = sample(image, x + (u - 1.5) * nx, y + (u - 1.5) * ny);
			double b = sample(image, x + (u + 1.5) * nx, y + (u + 1.5) * ny);
			best = Math.max(best, Math.min(a - centre, b - centre));
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
