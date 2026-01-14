package plugins.fmp.multiSPOTS96.series;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageCursor;
import icy.image.ImageUtil;
import plugins.fmp.multiSPOTS96.tools.imageTransform.ImageTransformOptions;

/**
 * Safe implementation of ImageProcessor with proper error handling. Replaces
 * scattered image I/O operations with centralized, safe operations.
 */
public class SafeImageProcessor implements ImageProcessor {

	@Override
	public ProcessingResult<IcyBufferedImage> loadImage(String filename) {
		if (filename == null || filename.trim().isEmpty()) {
			return ProcessingResult.failure("Filename cannot be null or empty");
		}

		try {
			File file = new File(filename);
			if (!file.exists()) {
				return ProcessingResult.failure("File does not exist: %s", filename);
			}

			if (!file.canRead()) {
				return ProcessingResult.failure("Cannot read file: %s", filename);
			}

			BufferedImage bufferedImage = ImageIO.read(file);
			if (bufferedImage == null) {
				return ProcessingResult.failure("Failed to read image (unsupported format?): %s", filename);
			}

			IcyBufferedImage icyImage = IcyBufferedImage.createFrom(bufferedImage);
			return ProcessingResult.success(icyImage);

		} catch (IOException e) {
			return ProcessingResult.failure("I/O error loading image: %s", filename); // .getCause().orElse(e);
		} catch (OutOfMemoryError e) {
			return ProcessingResult.failure("Out of memory loading image: %s", filename); // .getCause().orElse(e);
		} catch (Exception e) {
			return ProcessingResult.failure("Unexpected error loading image: %s", filename); // .getCause().orElse(e);
		}
	}

	@Override
	public ProcessingResult<Void> saveImage(IcyBufferedImage image, String filename) {
		if (image == null) {
			return ProcessingResult.failure("Image cannot be null");
		}

		if (filename == null || filename.trim().isEmpty()) {
			return ProcessingResult.failure("Filename cannot be null or empty");
		}

		try {
			File file = new File(filename);
			File parentDir = file.getParentFile();

			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					return ProcessingResult.failure("Failed to create directory: %s", parentDir.getPath());
				}
			}

			// Convert IcyBufferedImage to BufferedImage for saving
			BufferedImage bufferedImage = ImageUtil.toBufferedImage(image);

			String extension = getFileExtension(filename);
			if (extension.isEmpty()) {
				return ProcessingResult.failure("No file extension found in filename: %s", filename);
			}

			boolean success = ImageIO.write(bufferedImage, extension, file);
			if (!success) {
				return ProcessingResult.failure("Failed to save image (unsupported format?): %s", filename);
			}

			return ProcessingResult.success();

		} catch (IOException e) {
			return ProcessingResult.failure("I/O error saving image: %s", filename);
		} catch (Exception e) {
			return ProcessingResult.failure("Unexpected error saving image: %s", filename);
		}
	}

	@Override
	public ProcessingResult<IcyBufferedImage> transformImage(IcyBufferedImage sourceImage,
			ImageTransformOptions options) {
		if (sourceImage == null) {
			return ProcessingResult.failure("Source image cannot be null");
		}

		if (options == null) {
			return ProcessingResult.failure("Transform options cannot be null");
		}

		try {
			// This would typically delegate to the existing transformation system
			// For now, return a copy as a placeholder
			IcyBufferedImage transformedImage = IcyBufferedImage.createFrom(sourceImage);
			return ProcessingResult.success(transformedImage);

		} catch (Exception e) {
			return ProcessingResult.failure("Image transformation failed");
		}
	}

	@Override
	public ProcessingResult<BackgroundTransformResult> transformBackground(IcyBufferedImage sourceImage,
			IcyBufferedImage backgroundImage, ImageTransformOptions options) {

		if (sourceImage == null || backgroundImage == null) {
			return ProcessingResult.failure("Source and background images cannot be null");
		}

		if (options == null) {
			return ProcessingResult.failure("Transform options cannot be null");
		}

		try {
			int pixelsChanged = performBackgroundTransformation(sourceImage, backgroundImage, options);
			BackgroundTransformResult result = new BackgroundTransformResult(pixelsChanged, backgroundImage);
			return ProcessingResult.success(result);

		} catch (Exception e) {
			return ProcessingResult.failure("Background transformation failed");
		}
	}

	@Override
	public ProcessingResult<boolean[]> createBinaryMask(IcyBufferedImage image, int threshold, boolean trackWhite,
			int videoChannel) {
		if (image == null) {
			return ProcessingResult.failure("Image cannot be null");
		}

		if (threshold < 0 || threshold > 255) {
			return ProcessingResult.failure("Threshold must be between 0 and 255, got: %d", threshold);
		}

		if (videoChannel < 0 || videoChannel >= image.getSizeC()) {
			return ProcessingResult.failure("Invalid video channel: %d", videoChannel);
		}

		try {
			boolean[] mask = new boolean[image.getSizeX() * image.getSizeY()];

			if (trackWhite) {
				createWhiteTrackingMask(image, mask, threshold);
			} else {
				createChannelMask(image, mask, threshold, videoChannel);
			}

			return ProcessingResult.success(mask);

		} catch (Exception e) {
			return ProcessingResult.failure("Failed to create binary mask");
		}
	}

	// Helper methods
	private String getFileExtension(String filename) {
		int lastDotIndex = filename.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
			return "";
		}
		return filename.substring(lastDotIndex + 1).toLowerCase();
	}

	private int performBackgroundTransformation(IcyBufferedImage sourceImage, IcyBufferedImage backgroundImage,
			ImageTransformOptions options) {
		// Refactored version of the transformBackground method from BuildBackground
		int width = sourceImage.getSizeX();
		int height = sourceImage.getSizeY();
		int planes = sourceImage.getSizeC();
		int changed = 0;

		IcyBufferedImageCursor sourceCursor = new IcyBufferedImageCursor(sourceImage);
		IcyBufferedImageCursor backgroundCursor = new IcyBufferedImageCursor(backgroundImage);

		double smallThreshold = options.background_delta;

		try {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					for (int c = 0; c < planes; c++) {
						double backgroundValue = backgroundCursor.get(x, y, c);
						double sourceValue = sourceCursor.get(x, y, c);

						if (sourceValue < options.simplethreshold) {
							continue;
						}

						double differenceValue = sourceValue - backgroundValue;
						if (backgroundValue < options.simplethreshold && differenceValue > smallThreshold) {
							changed++;
							updateBackgroundNeighborhood(backgroundCursor, sourceCursor, x, y, width, height, planes,
									options.background_jitter);
						}
					}
				}
			}
		} finally {
			backgroundCursor.commitChanges();
		}

		return changed;
	}

	private void updateBackgroundNeighborhood(IcyBufferedImageCursor backgroundCursor,
			IcyBufferedImageCursor sourceCursor, int x, int y, int width, int height, int planes, int jitter) {
		for (int yy = y - jitter; yy < y + jitter; yy++) {
			if (yy < 0 || yy >= height)
				continue;
			for (int xx = x - jitter; xx < x + jitter; xx++) {
				if (xx < 0 || xx >= width)
					continue;
				for (int cc = 0; cc < planes; cc++) {
					backgroundCursor.set(xx, yy, cc, sourceCursor.get(xx, yy, cc));
				}
			}
		}
	}

	private void createWhiteTrackingMask(IcyBufferedImage image, boolean[] mask, int threshold) {
		byte[] arrayRed = image.getDataXYAsByte(0);
		byte[] arrayGreen = image.getDataXYAsByte(1);
		byte[] arrayBlue = image.getDataXYAsByte(2);

		for (int i = 0; i < arrayRed.length; i++) {
			float r = (arrayRed[i] & 0xFF);
			float g = (arrayGreen[i] & 0xFF);
			float b = (arrayBlue[i] & 0xFF);
			float intensity = (r + g + b) / 3f;
			mask[i] = intensity > threshold;
		}
	}

	private void createChannelMask(IcyBufferedImage image, boolean[] mask, int threshold, int videoChannel) {
		byte[] arrayChan = image.getDataXYAsByte(videoChannel);
		for (int i = 0; i < arrayChan.length; i++) {
			mask[i] = (((int) arrayChan[i]) & 0xFF) < threshold;
		}
	}
}