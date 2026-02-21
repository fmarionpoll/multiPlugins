package plugins.fmp.multitools.service;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities;
import plugins.fmp.multitools.tools.polyline.Bresenham;

/**
 * Extracts a 1D intensity profile along a capillary ROI from a cam image (one
 * value per position along the line). Uses the same sampling as
 * KymographBuilder (Bresenham + disk radius) so the profile matches one column
 * of a kymograph.
 */
public final class CapillaryProfileExtractor {

	private CapillaryProfileExtractor() {
	}

	/**
	 * Builds the list of pixel masks along the ROI line (one mask per position).
	 */
	public static List<ArrayList<int[]>> buildMasksAlongRoi(ROI2D roi, int imageWidth, int imageHeight, int diskRadius) {
		List<ArrayList<int[]>> masks = new ArrayList<>();
		ArrayList<Point2D> points = ROI2DUtilities.getCapillaryPoints(roi);
		if (points == null || points.isEmpty())
			return masks;
		ArrayList<int[]> pixels = Bresenham.getPixelsAlongLineFromROI2D(points);
		int r = Math.max(0, diskRadius);
		for (int[] pixel : pixels)
			masks.add(getPixelsInDisk(pixel, r, imageWidth, imageHeight));
		return masks;
	}

	/**
	 * Builds the list of pixel masks along the ROI using a segment perpendicular to
	 * the capillary axis at each point. Same list shape as buildMasksAlongRoi so
	 * extractProfileFromMasks can be reused. For direct-from-cam level detection.
	 */
	public static List<ArrayList<int[]>> buildMasksAlongRoiPerpendicular(ROI2D roi, int imageWidth, int imageHeight,
			int halfLength) {
		List<ArrayList<int[]>> masks = new ArrayList<>();
		ArrayList<Point2D> points = ROI2DUtilities.getCapillaryPoints(roi);
		if (points == null || points.isEmpty())
			return masks;
		ArrayList<int[]> pixels = Bresenham.getPixelsAlongLineFromROI2D(points);
		int n = pixels.size();
		if (n == 0)
			return masks;
		int len = Math.max(0, halfLength);
		for (int i = 0; i < n; i++) {
			double tx, ty;
			if (n == 1) {
				tx = 1;
				ty = 0;
			} else if (i == 0) {
				tx = pixels.get(1)[0] - pixels.get(0)[0];
				ty = pixels.get(1)[1] - pixels.get(0)[1];
			} else if (i == n - 1) {
				tx = pixels.get(n - 1)[0] - pixels.get(n - 2)[0];
				ty = pixels.get(n - 1)[1] - pixels.get(n - 2)[1];
			} else {
				tx = pixels.get(i + 1)[0] - pixels.get(i - 1)[0];
				ty = pixels.get(i + 1)[1] - pixels.get(i - 1)[1];
			}
			double plen = Math.hypot(tx, ty);
			if (plen < 1e-6) {
				tx = 1;
				ty = 0;
				plen = 1;
			}
			double px = -ty / plen;
			double py = tx / plen;
			int[] c = pixels.get(i);
			int x1 = clip((int) Math.round(c[0] - len * px), 0, imageWidth - 1);
			int y1 = clip((int) Math.round(c[1] - len * py), 0, imageHeight - 1);
			int x2 = clip((int) Math.round(c[0] + len * px), 0, imageWidth - 1);
			int y2 = clip((int) Math.round(c[1] + len * py), 0, imageHeight - 1);
			ArrayList<int[]> seg = Bresenham.getPixelsBetween2Points(x1, y1, x2, y2);
			masks.add(seg);
		}
		return masks;
	}

	private static ArrayList<int[]> getPixelsInDisk(int[] center, int diskRadius, int sizex, int sizey) {
		ArrayList<int[]> list = new ArrayList<>();
		double r2 = (double) diskRadius * diskRadius;
		int minX = clip(center[0] - diskRadius, 0, sizex - 1);
		int maxX = clip(center[0] + diskRadius, minX, sizex - 1);
		int y = center[1];
		if (y < 0 || y >= sizey)
			return list;
		for (int x = minX; x <= maxX; x++) {
			double dx = x - center[0];
			if (dx * dx <= r2)
				list.add(new int[] { x, y });
		}
		return list;
	}

	private static int clip(int v, int lo, int hi) {
		if (v < lo) return lo;
		if (v > hi) return hi;
		return v;
	}

	/**
	 * Extracts a 1D profile (one value per mask) by averaging pixel values in each
	 * mask. Uses channel 0 of the source image.
	 *
	 * @return array of length masks.size(), or empty array if masks empty or image null
	 */
	public static int[] extractProfileFromMasks(IcyBufferedImage sourceImage, List<ArrayList<int[]>> masks) {
		if (sourceImage == null || masks == null || masks.isEmpty())
			return new int[0];
		int w = sourceImage.getSizeX();
		int h = sourceImage.getSizeY();
		Object data = sourceImage.getDataXY(0);
		int[] channel = Array1DUtil.arrayToIntArray(data, sourceImage.isSignedDataType());
		int[] profile = new int[masks.size()];
		for (int i = 0; i < masks.size(); i++) {
			ArrayList<int[]> mask = masks.get(i);
			long sum = 0;
			int n = 0;
			for (int[] p : mask) {
				int x = p[0];
				int y = p[1];
				if (x >= 0 && x < w && y >= 0 && y < h) {
					sum += channel[x + y * w];
					n++;
				}
			}
			profile[i] = n > 0 ? (int) (sum / n) : 0;
		}
		return profile;
	}

	/**
	 * Extracts R, G, B profiles (one value per mask per channel) by averaging pixel
	 * values in each mask. Use for direct-from-cam detection so color transforms
	 * (e.g. 2G-(R+B)) get real R,G,B and do not zero out.
	 *
	 * @return new int[3][masks.size()] with {R, G, B} profiles, or null if masks empty or image null
	 */
	public static int[][] extractRgbProfileFromMasks(IcyBufferedImage sourceImage, List<ArrayList<int[]>> masks) {
		if (sourceImage == null || masks == null || masks.isEmpty())
			return null;
		int w = sourceImage.getSizeX();
		int h = sourceImage.getSizeY();
		int nCh = Math.min(3, sourceImage.getSizeC());
		int[][] channels = new int[nCh][];
		for (int c = 0; c < nCh; c++) {
			Object data = sourceImage.getDataXY(c);
			channels[c] = Array1DUtil.arrayToIntArray(data, sourceImage.isSignedDataType());
		}
		int n = masks.size();
		int[][] rgb = new int[3][n];
		for (int i = 0; i < n; i++) {
			ArrayList<int[]> mask = masks.get(i);
			long[] sum = new long[3];
			int count = 0;
			for (int[] p : mask) {
				int x = p[0];
				int y = p[1];
				if (x >= 0 && x < w && y >= 0 && y < h) {
					int idx = x + y * w;
					for (int c = 0; c < nCh; c++)
						sum[c] += channels[c][idx];
					count++;
				}
			}
			for (int c = 0; c < nCh; c++)
				rgb[c][i] = count > 0 ? (int) (sum[c] / count) : 0;
		}
		if (nCh < 3) {
			for (int i = 0; i < n; i++) {
				if (nCh == 1)
					rgb[1][i] = rgb[2][i] = rgb[0][i];
				else
					rgb[2][i] = rgb[1][i];
			}
		}
		return rgb;
	}
}
