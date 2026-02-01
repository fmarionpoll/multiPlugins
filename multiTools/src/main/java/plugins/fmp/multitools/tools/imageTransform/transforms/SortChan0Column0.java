package plugins.fmp.multitools.tools.imageTransform.transforms;

import java.util.Arrays;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.tools.imageTransform.CanvasImageTransformOptions;

public class SortChan0Column0 extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, CanvasImageTransformOptions options) {
		IcyBufferedImage destinationImage = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		IcyBufferedImageCursor sourceCursor = new IcyBufferedImageCursor(sourceImage);
		IcyBufferedImageCursor destinationCursor = new IcyBufferedImageCursor(destinationImage);
		int columnIndex = 0; // column 0
		int channel = 0; // RED
		int[][] sorted = getSortOrderForColumn(sourceImage, columnIndex, channel);
		try {
			for (int y = 0; y < sourceImage.getHeight(); y++) {
				int ySourceIndex = sorted[y][0];
				for (int x = 0; x < sourceImage.getWidth(); x++)
					for (int chan = 0; chan < 3; chan++)
						destinationCursor.set(x, y, chan, sourceCursor.get(x, ySourceIndex, chan));
			}
		} finally {
			sourceCursor.commitChanges();
			destinationCursor.commitChanges();
		}
		return destinationImage;
	}

	private int[][] getSortOrderForColumn(IcyBufferedImage sourceImage, int columnIndex, int channel) {
		int[][] sorted = getImageColumnValues(sourceImage, columnIndex, channel);
		Arrays.sort(sorted, (a, b) -> b[1] - a[1]);
		return sorted;
	}

	private int[][] getImageColumnValues(IcyBufferedImage sourceImage, int columnIndex, int channel) {
		int[][] sorted = new int[sourceImage.getHeight()][2];
		int x = columnIndex;
		IcyBufferedImageCursor cursorSource = new IcyBufferedImageCursor(sourceImage);
		try {
			for (int y = 0; y < sourceImage.getHeight(); y++) {
				sorted[y][1] = (int) cursorSource.get(x, y, channel);
				sorted[y][0] = y;
			}
		} finally {
			cursorSource.commitChanges();
		}
		return sorted;
	}

}
