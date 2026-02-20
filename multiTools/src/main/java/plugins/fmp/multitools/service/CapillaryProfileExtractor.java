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
}
