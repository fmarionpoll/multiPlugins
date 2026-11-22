package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;

import java.awt.geom.Ellipse2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotLineSampling;
import plugins.fmp.multitools.experiment.spot.SpotMeasure;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class SpotCameraFrameSamplingTest {

	@ClassRule public static final InMemoryImageRule imageMemory = new InMemoryImageRule();
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test(timeout = 30000)
	public void basicUsesAll369FramesInsteadOf364NominalIntervals() throws Exception {
		checkDetector(new SpotLevelDetectorFromCamBasic(), false);
	}

	@Test(timeout = 30000)
	public void pipelinedUsesAll369FramesInsteadOf364NominalIntervals() throws Exception {
		checkDetector(new SpotLevelDetectorFromCam(), false);
	}

	@Test(timeout = 30000)
	public void basicIgnoresClippedAndDownsampledKymographGrid() throws Exception {
		checkDetector(new SpotLevelDetectorFromCamBasic(), true);
	}

	@Test(timeout = 30000)
	public void pipelinedIgnoresClippedAndDownsampledKymographGrid() throws Exception {
		checkDetector(new SpotLevelDetectorFromCam(), true);
	}

	@Test(timeout = 30000)
	public void colorUsesAllCameraFrames() throws Exception {
		checkDetector(new SpotLevelDetectorFromCamColor(), false);
	}

	@Test(timeout = 30000)
	public void colorIgnoresClippedKymographGrid() throws Exception {
		checkDetector(new SpotLevelDetectorFromCamColor(), true);
	}

	private void checkDetector(SpotLevelDetectionRunner detector, boolean clippedKymo) throws Exception {
		Experiment exp = new Experiment() {
			@Override public String getDirectoryToSaveResults() { return null; }
			@Override public int findNearestIntervalWithBinarySearch(long time, int low, int high) {
				throw new AssertionError("Direct spot measurements must not resample camera frames");
			}
		};
		// Three different images expose skipped, duplicated, or reordered frames.
		List<String> images = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) image.setRGB(x, y, (40 + 60 * i) << 16);
			}
			File file = folder.newFile("frame" + i + ".png");
			ImageIO.write(image, "png", file);
			images.add(file.getAbsolutePath());
		}
		List<String> stack = new ArrayList<>();
		stack.add(images.get(2)); // Excluded opening frame, like the experiment's dark frame.
		for (int i = 0; i < 369; i++) stack.add(images.get(i % 3));
		stack.add(images.get(1)); // Excluded trailing frame.
		exp.getSeqCamData().setImagesList(stack);
		exp.getSeqCamData().getImageLoader().setAbsoluteIndexFirstImage(1);
		exp.getSeqCamData().getImageLoader().setFixedNumberOfImages(370);
		long[] times = new long[369];
		for (int i = 0; i < times.length; i++) times[i] = Math.round(i * 7642000.0 / 368);
		exp.setCamImages_ms(times);
		exp.getSeqCamData().getTimeManager().setCamImagesTime_Ms(times);
		exp.setCamImageFirst_ms(1000000);
		exp.setCamImageLast_ms(8642000);
		exp.setCamImageBin_ms(21000);
		exp.setKymoFirst_ms(clippedKymo ? 60000 : 0);
		exp.setKymoLast_ms(clippedKymo ? 120000 : 0);
		exp.setKymoBin_ms(60000);
		assertEquals(364, 7642000 / 21000 + 1);

		Spot spot = new Spot();
		spot.setRoi(new ROI2DEllipse(new Ellipse2D.Double(2, 2, 4, 4)));
		spot.setReadyForAnalysis(true);
		exp.getSpots().getSpotList().add(spot);
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.transform01 = ImageTransformEnums.R_RGB;
		options.transform02 = ImageTransformEnums.B_RGB;
		options.spotThreshold = 0;
		options.flyThreshold = 255;
		options.useGpuTransforms = false;
		options.kymoDownsampleFactor = 3;
		options.spotColorReferenceList.add(new Color(40, 0, 0));
		options.spotColorReferenceList.add(new Color(100, 0, 0));
		options.spotColorReferenceList.add(new Color(160, 0, 0));
		detector.detectSpots(exp, options);

		SpotMeasure signal = detector instanceof SpotLevelDetectorFromCamColor ? spot.getColorDistanceGreySignal()
				: spot.getRoiNormalizedGreySignal();
		assertEquals(SpotLineSampling.cameraFrames(exp).size(), signal.getCount());
		assertEquals(369, spot.getFlyCoveredPixelCount().getCount());
		for (int i = 0; i < 369; i++) {
			double expected = 40 + 60 * (i % 3);
			if (detector instanceof SpotLevelDetectorFromCamColor) expected = (int) expected / 3;
			assertEquals("camera frame " + i, expected, signal.getValueAt(i), 1e-9);
		}
	}
}
