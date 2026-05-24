package plugins.fmp.multitools.service;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;

/**
 * Samples RGB colors from the camera frame at pixels outside all spot ROIs, optionally
 * compresses them to a small prototype set for use as {@code spotColorExcludeList}.
 */
public final class SpotColorBackgroundSampler {

	private SpotColorBackgroundSampler() {
	}

	/**
	 * @param rgbImage    RGB image (at least 3 channels or {@link IcyBufferedImage#getRGB} works)
	 * @param spots       spot list (ROIs may be ellipses or other {@link ROI2D})
	 * @param maxColors   maximum distinct colors to collect before k-means reduction
	 * @param maxAttempts random draw attempts (upper bound on work)
	 */
	public static ArrayList<Color> sampleOutsideSpotRois(IcyBufferedImage rgbImage, Spots spots, int maxColors,
			int maxAttempts) {
		ArrayList<Color> out = new ArrayList<>();
		if (rgbImage == null || spots == null || maxColors <= 0) {
			return out;
		}
		int w = rgbImage.getSizeX();
		int h = rgbImage.getSizeY();
		if (w < 1 || h < 1) {
			return out;
		}
		List<Spot> list = spots.getSpotList();
		if (list == null || list.isEmpty()) {
			return out;
		}
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		LinkedHashSet<Integer> packedRgb = new LinkedHashSet<>();
		int attempts = 0;
		while (packedRgb.size() < maxColors && attempts < maxAttempts) {
			attempts++;
			int x = rng.nextInt(w);
			int y = rng.nextInt(h);
			if (insideAnySpotRoi(x, y, list)) {
				continue;
			}
			int argb = rgbImage.getRGB(x, y);
			packedRgb.add(argb & 0xFFFFFF);
		}
		for (int p : packedRgb) {
			out.add(new Color((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
		}
		return reduceKMeansPrototypes(out, Math.min(8, Math.max(1, out.size())));
	}

	private static boolean insideAnySpotRoi(int x, int y, List<Spot> spots) {
		for (Spot spot : spots) {
			if (spot == null) {
				continue;
			}
			ROI2D roi = spot.getRoi();
			if (roi == null) {
				continue;
			}
			if (roi.getBounds().contains(x, y)) {
				return true;
			}
		}
		return false;
	}

	/** Lloyd k-means on RGB integers; {@code k} capped by input size. */
	static ArrayList<Color> reduceKMeansPrototypes(ArrayList<Color> samples, int k) {
		if (samples == null || samples.isEmpty()) {
			return new ArrayList<>();
		}
		if (samples.size() <= k) {
			return new ArrayList<>(samples);
		}
		int n = samples.size();
		int kk = Math.min(k, n);
		double[][] cent = new double[kk][3];
		for (int j = 0; j < kk; j++) {
			Color c = samples.get(j * n / kk);
			cent[j][0] = c.getRed();
			cent[j][1] = c.getGreen();
			cent[j][2] = c.getBlue();
		}
		int[] assign = new int[n];
		for (int iter = 0; iter < 4; iter++) {
			for (int i = 0; i < n; i++) {
				Color c = samples.get(i);
				double r = c.getRed();
				double g = c.getGreen();
				double b = c.getBlue();
				int best = 0;
				double bestD = Double.POSITIVE_INFINITY;
				for (int j = 0; j < kk; j++) {
					double dr = r - cent[j][0];
					double dg = g - cent[j][1];
					double db = b - cent[j][2];
					double d = dr * dr + dg * dg + db * db;
					if (d < bestD) {
						bestD = d;
						best = j;
					}
				}
				assign[i] = best;
			}
			int[] count = new int[kk];
			double[][] sum = new double[kk][3];
			for (int i = 0; i < n; i++) {
				int j = assign[i];
				Color c = samples.get(i);
				count[j]++;
				sum[j][0] += c.getRed();
				sum[j][1] += c.getGreen();
				sum[j][2] += c.getBlue();
			}
			for (int j = 0; j < kk; j++) {
				if (count[j] > 0) {
					cent[j][0] = sum[j][0] / count[j];
					cent[j][1] = sum[j][1] / count[j];
					cent[j][2] = sum[j][2] / count[j];
				}
			}
		}
		ArrayList<Color> proto = new ArrayList<>(kk);
		for (int j = 0; j < kk; j++) {
			proto.add(new Color(clamp255((int) Math.round(cent[j][0])), clamp255((int) Math.round(cent[j][1])),
					clamp255((int) Math.round(cent[j][2]))));
		}
		return proto;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
