package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.IcyBufferedImageUtil;

/**
 * Heals fly-contaminated pixels in a previous frame using a clean Detect2-style
 * referenceImage, so t - clean(t-1) keeps stationary flies visible without
 * absolute-difference ghosts.
 */
public final class PatchPreviousFromReference {

	private PatchPreviousFromReference() {
	}

	/**
	 * Returns a copy of previous where dark fly holes are replaced with brighter
	 * empty-floor pixels from reference (same idea as Detect2 background heal,
	 * applied to t-1).
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
		int j = Math.max(1, jitter);
		int thresh = Math.max(0, Math.min(255, threshold));
		int minDelta = Math.max(0, delta);

		IcyBufferedImage cleaned = IcyBufferedImageUtil.getCopy(previous);
		IcyBufferedImageCursor prevCursor = new IcyBufferedImageCursor(previous);
		IcyBufferedImageCursor refCursor = new IcyBufferedImageCursor(reference);
		IcyBufferedImageCursor outCursor = new IcyBufferedImageCursor(cleaned);

		int width = previous.getSizeX();
		int height = previous.getSizeY();
		int planes = previous.getSizeC();
		int refPlanes = Math.min(planes, reference.getSizeC());

		try {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					boolean heal = false;
					for (int c = 0; c < refPlanes; c++) {
						double prevVal = prevCursor.get(x, y, c);
						double refVal = refCursor.get(x, y, c);
						if (prevVal < thresh && refVal >= thresh && (refVal - prevVal) > minDelta) {
							heal = true;
							break;
						}
					}
					if (!heal) {
						continue;
					}
					for (int yy = y - j; yy <= y + j; yy++) {
						if (yy < 0 || yy >= height) {
							continue;
						}
						for (int xx = x - j; xx <= x + j; xx++) {
							if (xx < 0 || xx >= width) {
								continue;
							}
							for (int cc = 0; cc < refPlanes; cc++) {
								outCursor.set(xx, yy, cc, refCursor.get(xx, yy, cc));
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
}