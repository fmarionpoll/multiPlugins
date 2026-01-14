package plugins.fmp.multitools.fmp_series;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.fmp_experiment.sequence.SequenceCamData;

/**
 * Streaming image processor to avoid loading entire stack.
 * 
 * This class provides efficient image loading and processing by loading images
 * on-demand rather than loading the entire stack into memory.
 */
public class StreamingImageProcessor {
	private final ArrayList<String> imageFiles;

	public StreamingImageProcessor() {
		this.imageFiles = new ArrayList<>();
	}

	public void start(SequenceCamData seqCamData, int startFrame, int endFrame) {
		// Initialize image file list
		for (int i = startFrame; i < endFrame; i++) {
			String fileName = seqCamData.getFileNameFromImageList(i);
			if (fileName != null) {
				imageFiles.add(fileName);
			}
		}
	}

	public void stop() {
		clearAllImages();
	}

	public void clearAllImages() {
		// No buffer to clear - images are loaded on demand and immediately discarded
//		System.out.println("No image buffer to clear - using on-demand loading");
	}

	public IcyBufferedImage getImage(int frameIndex) {
		// Simple direct image loading like the original
		String fileName = imageFiles.get(frameIndex);
		if (fileName == null) {
			System.err.println("No filename found for frame " + frameIndex);
			return null;
		}

		// Use the same imageIORead method as the original
		return imageIORead(fileName);
	}

	// Use the same imageIORead method as BuildSeries
	private IcyBufferedImage imageIORead(String fileName) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new java.io.File(fileName));
		} catch (IOException e) {
			System.err.println("Error loading image: " + fileName + " - " + e.getMessage());
			return null;
		}
		return IcyBufferedImage.createFrom(image);
	}
}