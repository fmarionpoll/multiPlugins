package plugins.fmp.multitools.fmp_tools.imageTransform.transforms;

import java.util.Arrays;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformFunctionAbstract;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformInterface;
import plugins.fmp.multitools.fmp_tools.imageTransform.ImageTransformOptions;

public class SortChan0Columns extends ImageTransformFunctionAbstract implements ImageTransformInterface {
	@Override
	public IcyBufferedImage getTransformedImage(IcyBufferedImage sourceImage, ImageTransformOptions options) {
		IcyBufferedImage destinationImage = new IcyBufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), 3,
				sourceImage.getDataType_());
		IcyBufferedImageCursor sourceCursor = new IcyBufferedImageCursor(sourceImage);
		IcyBufferedImageCursor destinationCursor = new IcyBufferedImageCursor(destinationImage);

		int channel = 0; // RED
		int[][] sorted = new int[sourceImage.getHeight()][2];
		try {
			for (int x = 0; x < sourceImage.getWidth(); x++) {
				getSortOrderForColumn(sourceImage, x, channel, sorted);
				for (int y = 0; y < sourceImage.getHeight(); y++) {
					int ySourceIndex = sorted[y][0];
					for (int chan = 0; chan < 3; chan++)
						destinationCursor.set(x, y, chan, sourceCursor.get(x, ySourceIndex, chan));
				}
			}
		} finally {
			sourceCursor.commitChanges();
			destinationCursor.commitChanges();
		}
		return destinationImage;
	}

	private void getSortOrderForColumn(IcyBufferedImage sourceImage, int columnIndex, int channel, int[][] sorted) {
		getImageColumnValues(sourceImage, columnIndex, channel, sorted);
		Arrays.sort(sorted, (a, b) -> Integer.compare(b[1], a[1]));
	}

	private void getImageColumnValues(IcyBufferedImage sourceImage, int columnIndex, int channel, int[][] sorted) {
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
	}

}
