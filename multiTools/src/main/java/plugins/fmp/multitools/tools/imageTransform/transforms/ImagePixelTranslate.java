package plugins.fmp.multitools.tools.imageTransform.transforms;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;

/**
 * Integer pixel translation (sample with zero fill at borders).
 */
public final class ImagePixelTranslate {

	private ImagePixelTranslate() {
	}

	public static IcyBufferedImage translate(IcyBufferedImage src, int dx, int dy) {
		if (src == null) {
			return null;
		}
		IcyBufferedImage out = new IcyBufferedImage(src.getWidth(), src.getHeight(), src.getSizeC(),
				src.getDataType_());
		out.beginUpdate();
		try {
			for (int ch = 0; ch < src.getSizeC(); ch++) {
				double[] srcData = Array1DUtil.arrayToDoubleArray(src.getDataXY(ch), src.isSignedDataType());
				double[] dstData = Array1DUtil.arrayToDoubleArray(out.getDataXY(ch), out.isSignedDataType());
				int w = src.getWidth();
				int h = src.getHeight();
				for (int y = 0; y < h; y++) {
					int ys = y - dy;
					if (ys < 0 || ys >= h) {
						continue;
					}
					int rowDst = y * w;
					int rowSrc = ys * w;
					for (int x = 0; x < w; x++) {
						int xs = x - dx;
						if (xs < 0 || xs >= w) {
							continue;
						}
						dstData[rowDst + x] = srcData[rowSrc + xs];
					}
				}
				Array1DUtil.doubleArrayToArray(dstData, out.getDataXY(ch));
			}
		} finally {
			out.endUpdate();
		}
		return out;
	}
}
