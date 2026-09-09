package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import plugins.fmp.multitools.service.FrameSupportBarDetector;

/**
 * CODEX Image-zero anchored, translation-only registration of rack junctions.
 */
public final class RackTranslationTracker {
	public static final class Estimate {
		public final double dx, dy, confidence;
		public final boolean reliable;
		public final String reason;

		Estimate(double dx, double dy, double confidence, boolean reliable, String reason) {
			this.dx = dx;
			this.dy = dy;
			this.confidence = confidence;
			this.reliable = reliable;
			this.reason = reason;
		}

		public static Estimate uncertain(String reason) {
			return new Estimate(Double.NaN, Double.NaN, 0, false, reason);
		}
	}

	private final double[] reference;
	private final int width, height, radius, half;
	private final List<int[]> junctions = new ArrayList<int[]>();

	public RackTranslationTracker(double[] reference, int width, int height, FrameSupportBarDetector.Result rack) {
		if (reference == null || reference.length != width * height || rack == null || !rack.found()
				|| rack.lowerY <= rack.upperY || rack.dividers.size() != 9)
			throw new IllegalArgumentException("Image 0 needs a complete rack detection");
		this.reference = reference.clone();
		this.width = width;
		this.height = height;
		double pitch = (rack.dividers.get(8).getX1() - rack.dividers.get(0).getX1()) / 8;
		radius = Math.max(2, (int) Math.min(width * .03, pitch * .3));
		half = Math.max(6, (int) Math.round(width * .012));
		for (Line2D divider : rack.dividers) {
			int x = (int) Math.round(divider.getX1()), y = rack.lowerY;
			if (x - half - radius >= 0 && x + half + radius < width && y - half - radius >= 0
					&& y + half + radius < height)
				junctions.add(new int[] { x, y });
		}
		if (junctions.size() < 6)
			throw new IllegalArgumentException("Too few visible rack junctions");
	}

	public Estimate estimate(double[] current, int w, int h) {
		if (current == null || w != width || h != height || current.length != reference.length)
			return Estimate.uncertain("missing image or changed dimensions");
		List<double[]> matches = new ArrayList<double[]>();
		for (int[] p : junctions) {
			double best = -2;
			int bx = 0, by = 0;
			for (int dy = -radius; dy <= radius; dy++)
				for (int dx = -radius; dx <= radius; dx++) {
					double score = correlation(current, p[0], p[1], dx, dy);
					if (score > best) {
						best = score;
						bx = dx;
						by = dy;
					}
				}
			if (best >= .8 && Math.abs(bx) < radius && Math.abs(by) < radius)
				matches.add(new double[] { bx, by, best });
		}
		if (matches.size() < 6)
			return Estimate.uncertain("insufficient rack contrast or search limit");
		double dx = median(matches, 0), dy = median(matches, 1);
		int inliers = 0;
		double score = 0;
		for (double[] m : matches)
			if (Math.hypot(m[0] - dx, m[1] - dy) <= 1.5) {
				inliers++;
				score += m[2];
			}
		double confidence = (double) inliers / junctions.size();
		if (inliers < 6 || confidence < .7)
			return Estimate.uncertain("rack junctions disagree with a shared translation");
		return new Estimate(dx, dy, confidence * score / inliers, true, "rack consensus");
	}

	private double correlation(double[] image, int x, int y, int dx, int dy) {
		double a = 0, b = 0, aa = 0, bb = 0, ab = 0;
		int n = 0;
		for (int v = -half; v <= half; v++)
			for (int u = -half; u <= half; u++) {
				double r = reference[(y + v) * width + x + u];
				double c = image[(y + v + dy) * width + x + u + dx];
				a += r;
				b += c;
				aa += r * r;
				bb += c * c;
				ab += r * c;
				n++;
			}
		double variance = (aa - a * a / n) * (bb - b * b / n);
		return variance > 1e-6 ? (ab - a * b / n) / Math.sqrt(variance) : -1;
	}

	private static double median(List<double[]> values, int axis) {
		List<Double> sorted = new ArrayList<Double>();
		for (double[] v : values)
			sorted.add(v[axis]);
		Collections.sort(sorted);
		return (sorted.get((sorted.size() - 1) / 2) + sorted.get(sorted.size() / 2)) / 2;
	}

	public static Line2D translate(Line2D reference, double dx, double dy) {
		return new Line2D.Double(reference.getX1() + dx, reference.getY1() + dy, reference.getX2() + dx,
				reference.getY2() + dy);
	}
}
