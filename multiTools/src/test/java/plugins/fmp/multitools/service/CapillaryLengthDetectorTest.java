package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Test;

import plugins.fmp.multitools.service.CapillaryLengthDetector.AxisMeasure;
import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;

/**
 * Checks that the capillary length detector recovers position-dependent lengths
 * (the effect of lens distortion and camera tilt) and rejects capillaries whose
 * measure departs from the pattern the other capillaries describe.
 */
public class CapillaryLengthDetectorTest {

	private static final int IMAGE_WIDTH = 1024;
	private static final int IMAGE_HEIGHT = 520;
	private static final int N_CAPILLARIES = 20;
	private static final int CAPILLARY_TOP = 40;
	private static final double NOMINAL_LENGTH = 400.;
	private static final int TUBE_HALF_WIDTH = 4;

	private static final double BACKGROUND_LEVEL = 200.;
	private static final double TUBE_R_LEVEL = 110.;
	private static final double TUBE_G_LEVEL = 215.;
	private static final double TUBE_B_LEVEL = 220.;

	/**
	 * Ground truth mimics what the webcam does: capillaries near the edges of the
	 * image occupy fewer pixels than those at the centre (radial term), with a
	 * left-to-right gradient on top of it (camera not perfectly perpendicular).
	 */
	private static double trueLength(int index) {
		double u = normalizedPosition(index);
		return NOMINAL_LENGTH * (1. - 0.030 * u * u + 0.012 * u);
	}

	private static double normalizedPosition(int index) {
		return (capillaryX(index) - IMAGE_WIDTH / 2.) / (IMAGE_WIDTH / 2.);
	}

	private static int capillaryX(int index) {
		int spacing = IMAGE_WIDTH / (N_CAPILLARIES + 1);
		return spacing * (index + 1);
	}

	@Test
	public void recoversPositionDependentLengths() {
		int brokenIndex = 7;
		ImageData image = buildSyntheticImage(brokenIndex);
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();

		List<Double> measured = new ArrayList<Double>();
		double worstError = 0.;
		for (int i = 0; i < N_CAPILLARIES; i++) {
			ArrayList<int[]> axis = verticalAxis(capillaryX(i), 10, IMAGE_HEIGHT - 10);
			AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary " + i + " should be located", located.found);
			double length = located.endFrac - located.startFrac;
			measured.add(Double.valueOf(length));
			if (i != brokenIndex)
				worstError = Math.max(worstError, Math.abs(length - trueLength(i)));
		}

		assertTrue("recovered lengths should be within 2 px of the truth, worst was " + worstError, worstError <= 2.);

		double centre = measured.get(N_CAPILLARIES / 2).doubleValue();
		double edge = measured.get(0).doubleValue();
		assertTrue("the capillary at the edge must be shorter than the one at the centre", edge < centre);

		double brokenLength = measured.get(brokenIndex).doubleValue();
		assertTrue("the truncated capillary must stand out", Math.abs(brokenLength - trueLength(brokenIndex)) > 20.);
	}

	@Test
	public void flagsTheCapillaryThatDoesNotFollowTheTrend() {
		int brokenIndex = 7;
		CapillaryLengthResult result = new CapillaryLengthResult();
		for (int i = 0; i < N_CAPILLARIES; i++) {
			CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null, "line" + i, 400);
			measure.setCentroidX(capillaryX(i));
			measure.setRoiPixels(500.);
			measure.setDetectedPixels(i == brokenIndex ? trueLength(i) - 120. : trueLength(i));
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setSelected(true);
			result.addMeasure(measure);
		}

		CapillaryLengthDetector.validate(result, IMAGE_WIDTH, new CapillaryLengthDetectorOptions());

		CapillaryLengthResult.Measure broken = result.getMeasures().get(brokenIndex);
		assertEquals(CapillaryLengthResult.Status.OUTLIER, broken.getStatus());
		assertTrue("an outlier must not be applied", !broken.isSelected());
		assertTrue("the other capillaries must be kept, only " + result.countSelected() + " were",
				result.countSelected() >= N_CAPILLARIES - 3);

		// The reported spread must describe the distortion, not the failed capillary.
		double expectedSpread = 100. * (trueLength(10) - trueLength(0)) / trueLength(10);
		assertEquals(expectedSpread, result.getSpreadPercent(), 1.);
	}

	@Test
	public void detectsEndpointsInsideAnOversizedRoi() {
		ImageData image = buildSyntheticImage(-1);
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();

		// The ROI spans the whole image height while the capillary occupies only its
		// middle part, which is how users draw them.
		ArrayList<int[]> axis = verticalAxis(capillaryX(5), 0, IMAGE_HEIGHT - 1);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);

		assertTrue(located.found);
		assertTrue("endpoints must be found inside the ROI, not at its ends", !located.touchesBorder);
		assertEquals(CAPILLARY_TOP, located.startFrac, 2.);
		assertEquals(trueLength(5), located.endFrac - located.startFrac, 2.);
	}

	/**
	 * Runs on the recording frame used to design the detector, when it is available
	 * on this machine. Endpoints are located inside full-height search corridors,
	 * then the cross-capillary validation is applied to check that the measured
	 * lengths follow a smooth spatial pattern rather than scattering at random.
	 */
	@Test
	public void locatesCapillariesOnARealFrame() throws Exception {
		File file = realFrameFile();
		Assume.assumeTrue("real frame not available on this machine", file != null && file.isFile());

		ImageData image = readImage(file);
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		List<Integer> columns = findCapillaryColumns(image);
		Assume.assumeTrue("could not isolate capillary columns on this frame", columns.size() >= 10);

		List<AxisMeasure> located = new ArrayList<AxisMeasure>();
		for (Integer x : columns) {
			ArrayList<int[]> axis = verticalAxis(x.intValue(), 0, image.height - 1);
			AxisMeasure measured = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary at x=" + x + " should be located", measured.found);
			located.add(measured);
			System.out.println(String.format("x=%4d top=%7.2f bottom=%7.2f span=%7.2f", x.intValue(),
					measured.startFrac, measured.endFrac, measured.endFrac - measured.startFrac));
		}

		// The column finder also catches the shadows between the tubes; a real tube
		// starts near the top of the rack, which all the others agree on.
		double referenceTop = median(startFractions(located));
		List<Double> tops = new ArrayList<Double>();
		CapillaryLengthResult result = new CapillaryLengthResult();
		for (int i = 0; i < columns.size(); i++) {
			AxisMeasure measured = located.get(i);
			double span = measured.endFrac - measured.startFrac;
			if (Math.abs(measured.startFrac - referenceTop) > 25. || span <= 0.5 * image.height)
				continue;
			tops.add(Double.valueOf(measured.startFrac));
			CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null,
					"x" + columns.get(i), 0);
			measure.setCentroidX(columns.get(i).intValue());
			measure.setRoiPixels(image.height);
			measure.setDetectedPixels(span);
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setSelected(true);
			result.addMeasure(measure);
		}
		int nTubes = result.getMeasures().size();
		assertTrue("most columns should be capillary tubes, only " + nTubes + " of " + columns.size() + " were",
				nTubes >= 0.7 * columns.size());

		CapillaryLengthDetector.validate(result, image.width, options);
		System.out.println(String.format("%d tubes measured, %d follow the trend, upper endpoints spread over "
				+ "%.1f px, lengths %.1f to %.1f px (%.1f%% variation)", nTubes, result.countSelected(),
				max(tops) - min(tops), result.getMinPixels(), result.getMaxPixels(), result.getSpreadPercent()));
		reportCentreVersusEdge(result, image.width);

		assertTrue("the measured lengths should follow a smooth spatial pattern, only " + result.countSelected()
				+ " of " + nTubes + " fit it", result.countSelected() >= 0.8 * nTubes);
		assertTrue("a real frame must show some geometric distortion", result.getSpreadPercent() > 1.);
	}

	/**
	 * Prints the length the fitted model predicts at the centre and at both edges,
	 * which is the number the per-capillary calibration corrects for.
	 */
	private static void reportCentreVersusEdge(CapillaryLengthResult result, int imageWidth) {
		CapillaryLengthResult.Measure left = null;
		CapillaryLengthResult.Measure right = null;
		CapillaryLengthResult.Measure centre = null;
		for (CapillaryLengthResult.Measure m : result.getMeasures()) {
			if (!m.isSelected())
				continue;
			if (left == null || m.getCentroidX() < left.getCentroidX())
				left = m;
			if (right == null || m.getCentroidX() > right.getCentroidX())
				right = m;
			double distance = Math.abs(m.getCentroidX() - imageWidth / 2.);
			if (centre == null || distance < Math.abs(centre.getCentroidX() - imageWidth / 2.))
				centre = m;
		}
		if (left == null || right == null || centre == null)
			return;
		System.out.println(String.format("left x=%.0f %.1f px | centre x=%.0f %.1f px | right x=%.0f %.1f px",
				left.getCentroidX(), left.getDetectedPixels(), centre.getCentroidX(), centre.getDetectedPixels(),
				right.getCentroidX(), right.getDetectedPixels()));
	}

	// === helpers ===

	private static File realFrameFile() {
		String override = System.getProperty("capillary.test.frame");
		if (override != null && !override.isEmpty())
			return new File(override);
		String home = System.getProperty("user.home");
		if (home == null)
			return null;
		return new File(home + "/.cursor/projects/c-Users-fred-git-multiPlugins/assets/"
				+ "c__Users_fred_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_"
				+ "image-8c44b292-f3d4-49d4-81e6-ff24915527f9.png");
	}

	private static ImageData readImage(File file) throws Exception {
		BufferedImage source = ImageIO.read(file);
		int width = source.getWidth();
		int height = source.getHeight();
		double[][] channels = new double[3][width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int rgb = source.getRGB(x, y);
				int index = x + y * width;
				channels[0][index] = (rgb >> 16) & 0xFF;
				channels[1][index] = (rgb >> 8) & 0xFF;
				channels[2][index] = rgb & 0xFF;
			}
		}
		return new ImageData(width, height, channels);
	}

	/** Columns where the red channel is locally darkest, i.e. the cyan tubes. */
	private static List<Integer> findCapillaryColumns(ImageData image) {
		int from = image.height / 4;
		int to = image.height / 2;
		double[] darkness = new double[image.width];
		for (int x = 0; x < image.width; x++) {
			double sum = 0.;
			for (int y = from; y < to; y++)
				sum += image.channels[0][x + y * image.width];
			darkness[x] = -sum / (to - from);
		}
		double[] sorted = darkness.clone();
		java.util.Arrays.sort(sorted);
		double cut = sorted[(int) (0.85 * (sorted.length - 1))];

		List<Integer> columns = new ArrayList<Integer>();
		int runStart = -1;
		for (int x = 0; x <= image.width; x++) {
			boolean dark = x < image.width && darkness[x] >= cut;
			if (dark && runStart < 0) {
				runStart = x;
			} else if (!dark && runStart >= 0) {
				if (x - runStart >= 3)
					columns.add(Integer.valueOf((runStart + x - 1) / 2));
				runStart = -1;
			}
		}
		return columns;
	}

	private static ArrayList<int[]> verticalAxis(int x, int yFrom, int yTo) {
		ArrayList<int[]> axis = new ArrayList<int[]>();
		for (int y = yFrom; y <= yTo; y++)
			axis.add(new int[] { x, y });
		return axis;
	}

	/**
	 * @param brokenIndex capillary drawn much shorter than it should be, to check
	 *                    that the detector does not silently accept it; -1 for none
	 */
	private static ImageData buildSyntheticImage(int brokenIndex) {
		double[][] channels = new double[3][IMAGE_WIDTH * IMAGE_HEIGHT];
		for (int y = 0; y < IMAGE_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++) {
				// Slowly varying background, as on the real inhomogeneous grey plate.
				double shade = BACKGROUND_LEVEL - 15. * (x / (double) IMAGE_WIDTH) + 10. * (y / (double) IMAGE_HEIGHT);
				int index = x + y * IMAGE_WIDTH;
				channels[0][index] = shade;
				channels[1][index] = shade;
				channels[2][index] = shade;
			}
		}

		for (int i = 0; i < N_CAPILLARIES; i++) {
			int cx = capillaryX(i);
			double length = i == brokenIndex ? trueLength(i) - 120. : trueLength(i);
			int yEnd = (int) Math.round(CAPILLARY_TOP + length);
			for (int y = CAPILLARY_TOP; y < yEnd && y < IMAGE_HEIGHT; y++) {
				for (int dx = -TUBE_HALF_WIDTH; dx <= TUBE_HALF_WIDTH; dx++) {
					int x = cx + dx;
					if (x < 0 || x >= IMAGE_WIDTH)
						continue;
					int index = x + y * IMAGE_WIDTH;
					channels[0][index] = TUBE_R_LEVEL;
					channels[1][index] = TUBE_G_LEVEL;
					channels[2][index] = TUBE_B_LEVEL;
				}
			}
		}
		return new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, channels);
	}

	private static List<Double> startFractions(List<AxisMeasure> measures) {
		List<Double> values = new ArrayList<Double>();
		for (AxisMeasure m : measures)
			values.add(Double.valueOf(m.startFrac));
		return values;
	}

	private static double median(List<Double> values) {
		List<Double> sorted = new ArrayList<Double>(values);
		java.util.Collections.sort(sorted);
		return sorted.get(sorted.size() / 2).doubleValue();
	}

	private static double min(List<Double> values) {
		double result = Double.POSITIVE_INFINITY;
		for (Double v : values)
			result = Math.min(result, v.doubleValue());
		return result;
	}

	private static double max(List<Double> values) {
		double result = Double.NEGATIVE_INFINITY;
		for (Double v : values)
			result = Math.max(result, v.doubleValue());
		return result;
	}
}
