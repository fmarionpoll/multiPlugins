package plugins.fmp.multitools.tools.imageTransform.transforms;

import java.awt.geom.Rectangle2D;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.IcyBufferedImageUtil;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.FlyPosition;
import plugins.fmp.multitools.experiment.cages.Cages;

/**
 * Heals fly-contaminated pixels in a previous frame using a clean Detect2-style
 * referenceImage, so t - clean(t-1) keeps stationary flies visible without
 * absolute-difference ghosts.
 */
public final class PatchPreviousFromReference {

	private PatchPreviousFromReference() {
	}

	/**
	 * Returns a copy of {@code previous} where dark fly regions (seeded vs
	 * {@code reference}, grown to fill internal gaps) are replaced with reference
	 * pixels. Incomplete mid-body heals otherwise leave a weak difference band and
	 * split one fly into two detections.
	 */
	public static IcyBufferedImage patchDarkFliesFromReference(IcyBufferedImage previous, IcyBufferedImage reference,
			int threshold, int delta, int jitter) {
		if (previous == null) {
			return null;
		}
		if (reference == null || previous.getSizeX() != reference.getSizeX()
				|| previous.getSizeY() != reference.getSizeY()) {
			return previous;
		}
		int j = Math.max(2, jitter);
		int thresh = Math.max(0, Math.min(255, threshold));
		int minDelta = Math.max(0, delta);

		int width = previous.getSizeX();
		int height = previous.getSizeY();
		int planes = previous.getSizeC();
		int refPlanes = Math.min(planes, reference.getSizeC());

		IcyBufferedImageCursor prevCursor = new IcyBufferedImageCursor(previous);
		IcyBufferedImageCursor refCursor = new IcyBufferedImageCursor(reference);

		boolean[] seed = new boolean[width * height];
		try {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					boolean darker = false;
					for (int c = 0; c < refPlanes; c++) {
						double prevVal = prevCursor.get(x, y, c);
						double refVal = refCursor.get(x, y, c);
						// Seed where previous is darker than the clean floor (fly-like).
						if (prevVal < thresh && (refVal - prevVal) > minDelta) {
							darker = true;
							break;
						}
					}
					seed[x + y * width] = darker;
				}
			}
		} finally {
			// read-only cursors
		}

		// Grow seeds so thin gaps inside a fly silhouette are included in the heal.
		boolean[] heal = dilate(seed, width, height, j);
		heal = fillComponentBoundingBoxes(heal, width, height);

		IcyBufferedImage cleaned = IcyBufferedImageUtil.getCopy(previous);
		IcyBufferedImageCursor outCursor = new IcyBufferedImageCursor(cleaned);
		try {
			for (int i = 0; i < heal.length; i++) {
				if (!heal[i]) {
					continue;
				}
				int x = i % width;
				int y = i / width;
				for (int cc = 0; cc < refPlanes; cc++) {
					outCursor.set(x, y, cc, refCursor.get(x, y, cc));
				}
			}
		} finally {
			outCursor.commitChanges();
		}
		return cleaned;
	}

	/**
	 * Force-replaces pixels under previous-frame fly rectangles (expanded) with
	 * reference values — covers flies that were only partially darker than
	 * {@code threshold}.
	 */
	public static IcyBufferedImage patchKnownFlyRects(IcyBufferedImage previousOrCleaned, IcyBufferedImage reference,
			Cages cages, int tPrev, int expandPx) {
		if (previousOrCleaned == null || reference == null || cages == null || cages.cagesList == null || tPrev < 0) {
			return previousOrCleaned;
		}
		if (previousOrCleaned.getSizeX() != reference.getSizeX()
				|| previousOrCleaned.getSizeY() != reference.getSizeY()) {
			return previousOrCleaned;
		}
		int expand = Math.max(0, expandPx);
		int width = previousOrCleaned.getSizeX();
		int height = previousOrCleaned.getSizeY();
		int refPlanes = Math.min(previousOrCleaned.getSizeC(), reference.getSizeC());

		IcyBufferedImage cleaned = previousOrCleaned;
		IcyBufferedImageCursor refCursor = new IcyBufferedImageCursor(reference);
		IcyBufferedImageCursor outCursor = new IcyBufferedImageCursor(cleaned);
		try {
			for (Cage cage : cages.cagesList) {
				if (cage == null || cage.flyPositions == null || cage.flyPositions.flyPositionList == null) {
					continue;
				}
				for (FlyPosition fp : cage.flyPositions.flyPositionList) {
					if (fp == null || fp.flyIndexT != tPrev || Cage.isEmptyFlyPositionSlot(fp)) {
						continue;
					}
					Rectangle2D r = fp.rectPosition;
					int x0 = (int) Math.floor(r.getX()) - expand;
					int y0 = (int) Math.floor(r.getY()) - expand;
					int x1 = (int) Math.ceil(r.getX() + r.getWidth()) + expand;
					int y1 = (int) Math.ceil(r.getY() + r.getHeight()) + expand;
					if (x0 < 0) {
						x0 = 0;
					}
					if (y0 < 0) {
						y0 = 0;
					}
					if (x1 > width) {
						x1 = width;
					}
					if (y1 > height) {
						y1 = height;
					}
					for (int y = y0; y < y1; y++) {
						for (int x = x0; x < x1; x++) {
							for (int cc = 0; cc < refPlanes; cc++) {
								outCursor.set(x, y, cc, refCursor.get(x, y, cc));
							}
						}
					}
				}
			}
		} finally {
			outCursor.commitChanges();
		}
		return cleaned;
	}

	/** Dilate {@code radius} times with a 3x3 structuring element. */
	private static boolean[] dilate(boolean[] mask, int w, int h, int radius) {
		boolean[] work = mask;
		int r = Math.max(1, radius);
		for (int i = 0; i < r; i++) {
			boolean[] out = new boolean[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (!work[x + y * w]) {
						continue;
					}
					for (int dy = -1; dy <= 1; dy++) {
						int ny = y + dy;
						if (ny < 0 || ny >= h) {
							continue;
						}
						for (int dx = -1; dx <= 1; dx++) {
							int nx = x + dx;
							if (nx >= 0 && nx < w) {
								out[nx + ny * w] = true;
							}
						}
					}
				}
			}
			work = out;
		}
		return work;
	}

	/**
	 * For each connected component, mark the full axis-aligned bounding box. Fills
	 * internal holes in dark fly silhouettes so mid-body is healed, not only the
	 * darkest seed pixels.
	 */
	private static boolean[] fillComponentBoundingBoxes(boolean[] mask, int w, int h) {
		boolean[] out = mask.clone();
		boolean[] visited = new boolean[w * h];
		int[] qx = new int[w * h];
		int[] qy = new int[w * h];
		for (int i = 0; i < mask.length; i++) {
			if (!mask[i] || visited[i]) {
				continue;
			}
			int qh = 0;
			int qt = 0;
			int sx = i % w;
			int sy = i / w;
			qx[qt] = sx;
			qy[qt] = sy;
			qt++;
			visited[i] = true;
			int minX = sx;
			int maxX = sx;
			int minY = sy;
			int maxY = sy;
			while (qh < qt) {
				int x = qx[qh];
				int y = qy[qh];
				qh++;
				if (x < minX) {
					minX = x;
				}
				if (x > maxX) {
					maxX = x;
				}
				if (y < minY) {
					minY = y;
				}
				if (y > maxY) {
					maxY = y;
				}
				for (int dy = -1; dy <= 1; dy++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (dx == 0 && dy == 0) {
							continue;
						}
						int nx = x + dx;
						int ny = y + dy;
						if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
							continue;
						}
						int ni = nx + ny * w;
						if (!mask[ni] || visited[ni]) {
							continue;
						}
						visited[ni] = true;
						qx[qt] = nx;
						qy[qt] = ny;
						qt++;
					}
				}
			}
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					out[x + y * w] = true;
				}
			}
		}
		return out;
	}
}