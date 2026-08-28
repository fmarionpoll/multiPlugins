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

	/** Second synthetic scene, closer to a real recording: see buildRackImage(). */
	private static final int RACK_IMAGE_HEIGHT = 640;
	private static final int TUBE_TOP = 40;
	private static final double NOMINAL_RACK_LENGTH = 520.;
	private static final int EMPTY_TOP_LENGTH = 90;
	private static final int BAR_TOP = 430;
	private static final int BAR_HEIGHT = 30;
	private static final double BAR_LEVEL = 60.;
	private static final double WALL_DARKENING = 14.;

	/**
	 * Ground truth mimics what the webcam does: capillaries near the edges of the
	 * image occupy fewer pixels than those at the centre (radial term), with a
	 * left-to-right gradient on top of it (camera not perfectly perpendicular).
	 */
	private static double trueLength(int index) {
		double u = normalizedPosition(index);
		return NOMINAL_LENGTH * (1. - 0.030 * u * u + 0.012 * u);
	}

	private static double rackTubeLength(int index) {
		double u = normalizedPosition(index);
		return NOMINAL_RACK_LENGTH * (1. - 0.030 * u * u + 0.012 * u);
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
	 * Reproduces what a recording actually looks like: the rack holding the tubes
	 * hides every capillary behind a dark horizontal bar, and the upper part of a
	 * tube that is not filled to the brim shows only its two glass walls. The
	 * measure must span the whole tube regardless.
	 */
	@Test
	public void measuresAcrossTheDarkBarAndOverTheEmptyTop() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();

		for (int i = 0; i < N_CAPILLARIES; i++) {
			ArrayList<int[]> axis = verticalAxis(capillaryX(i), 0, RACK_IMAGE_HEIGHT - 1);
			AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary " + i + " should be located", located.found);
			assertEquals("capillary " + i + " must start at the glass top, not at the liquid meniscus", TUBE_TOP,
					located.startFrac, 3.);
			assertEquals("capillary " + i + " must reach its tip below the rack bar", TUBE_TOP + rackTubeLength(i),
					located.endFrac, 3.);
			assertTrue("the bar interrupting capillary " + i + " must be reported as crossed",
					located.bridgedGap >= BAR_HEIGHT - 4);
		}
	}

	/**
	 * Without the empty upper section being recognised, the measure would start at
	 * the meniscus and lose the air column entirely.
	 */
	@Test
	public void countsTheAirColumnAsPartOfTheCapillary() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		ArrayList<int[]> axis = verticalAxis(capillaryX(9), 0, RACK_IMAGE_HEIGHT - 1);

		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		double length = located.endFrac - located.startFrac;

		assertEquals(rackTubeLength(9), length, 3.);
		assertTrue("the air column must not be dropped, " + length + " px measured for a tube whose liquid alone is "
				+ (rackTubeLength(9) - EMPTY_TOP_LENGTH) + " px", length > rackTubeLength(9) - EMPTY_TOP_LENGTH + 20.);
	}

	/**
	 * A ROI stopping short of both tips must still find them, since the search runs
	 * a little past each end of the ROI.
	 */
	@Test
	public void findsTipsFallingJustOutsideTheRoi() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();

		int index = 5;
		int tubeEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> shortRoi = verticalAxis(capillaryX(index), TUBE_TOP + 5, tubeEnd - 5);
		AxisMeasure withoutExtension = CapillaryLengthDetector.locateAlongAxis(shortRoi, image, options);
		assertTrue("a ROI shorter than the tube fills up to its own ends", withoutExtension.touchesBorder);

		ArrayList<int[]> extended = CapillaryLengthDetector.extendAxis(shortRoi, options.axisExtensionPixels);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(extended, image, options);
		assertTrue("both tips must now be inside the searched corridor", !located.touchesBorder);

		// extendAxis prepends axisExtensionPixels samples, so a position along the
		// extended axis is that much ahead of the same position along the ROI.
		double offset = TUBE_TOP + 5 - options.axisExtensionPixels;
		assertEquals(TUBE_TOP, offset + located.startFrac, 3.);
		assertEquals(tubeEnd, offset + located.endFrac, 3.);
	}

	/**
	 * Runs on the recording frame used to design the detector, when it is available
	 * on this machine. That frame is cropped below the rack, so the lower tips are
	 * outside the picture and the lengths themselves cannot be checked; what it
	 * does show is the dark bar of the rack cutting across every tube, and the two
	 * things asserted here are that the tube tops are found where they really are
	 * and that the measure carries on below the bar instead of stopping at it.
	 */
	@Test
	public void followsCapillariesThroughTheRackBarOnARealFrame() throws Exception {
		File file = realFrameFile();
		Assume.assumeTrue("real frame not available on this machine", file != null && file.isFile());

		ImageData image = readImage(file);
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		List<Integer> columns = findCapillaryColumns(image);
		Assume.assumeTrue("could not isolate capillary columns on this frame", columns.size() >= 10);
		int barRow = findDarkestRow(image, image.height / 2, image.height - 1);

		// Corridors start a little above the tubes, the way a user draws a ROI, and
		// run to the bottom edge where this frame cuts the tubes off.
		int corridorTop = 20;
		List<Double> tops = new ArrayList<Double>();
		List<Double> bottoms = new ArrayList<Double>();
		int crossedBar = 0;
		for (Integer x : columns) {
			ArrayList<int[]> axis = verticalAxis(x.intValue(), corridorTop, image.height - 1);
			AxisMeasure measured = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary at x=" + x + " should be located", measured.found);
			double top = corridorTop + measured.startFrac;
			double bottom = corridorTop + measured.endFrac;
			tops.add(Double.valueOf(top));
			bottoms.add(Double.valueOf(bottom));
			if (bottom > barRow + 10)
				crossedBar++;
			System.out.println(String.format("x=%4d top=%7.2f bottom=%7.2f bridged=%3d", x.intValue(), top, bottom,
					measured.bridgedGap));
		}
		System.out.println(String.format("rack bar at y=%d, %d of %d tubes measured past it, tops between "
				+ "%.1f and %.1f px", barRow, crossedBar, columns.size(), min(tops), max(tops)));

		// The corridors here are strictly vertical while the real tubes lean with the
		// perspective, so the lower half of a corridor can drift off its tube; the
		// typical tube is what tells whether the bar is crossed.
		assertTrue("the typical tube must be measured below the rack bar, median bottom was " + median(bottoms),
				median(bottoms) > barRow + 10);
		assertTrue("the rack bar must not cut the measure, only " + crossedBar + " of " + columns.size()
				+ " tubes reached below it", 2 * crossedBar >= columns.size());

		double referenceTop = median(tops);
		int consistent = 0;
		for (Double top : tops) {
			if (Math.abs(top.doubleValue() - referenceTop) <= 15.)
				consistent++;
		}
		assertTrue("tube tops should agree with each other, only " + consistent + " of " + tops.size()
				+ " sit within 15 px of the median (" + referenceTop + ")", consistent >= 0.8 * tops.size());
	}

	/** Row where the image is darkest, i.e. the bar of the rack holding the tubes. */
	private static int findDarkestRow(ImageData image, int from, int to) {
		int darkest = from;
		double lowest = Double.POSITIVE_INFINITY;
		for (int y = from; y <= to; y++) {
			double sum = 0.;
			for (int x = 0; x < image.width; x++)
				sum += image.channels[0][x + y * image.width];
			if (sum < lowest) {
				lowest = sum;
				darkest = y;
			}
		}
		return darkest;
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

	/**
	 * The three features of a real recording that a plain contrast search gets
	 * wrong: an air column at the top of the tube where only the two glass walls
	 * show, the dark bar of the rack hiding every tube part way down, and the
	 * coloured liquid running all the way to the tip below it.
	 */
	private static ImageData buildRackImage() {
		double[][] channels = new double[3][IMAGE_WIDTH * RACK_IMAGE_HEIGHT];
		for (int y = 0; y < RACK_IMAGE_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++) {
				double shade = BACKGROUND_LEVEL - 15. * (x / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) RACK_IMAGE_HEIGHT);
				setPixel(channels, x, y, shade, shade, shade);
			}
		}

		for (int i = 0; i < N_CAPILLARIES; i++) {
			int cx = capillaryX(i);
			int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
			int yLiquid = TUBE_TOP + EMPTY_TOP_LENGTH;
			for (int y = TUBE_TOP; y < yEnd && y < RACK_IMAGE_HEIGHT; y++) {
				double shade = BACKGROUND_LEVEL - 15. * (cx / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) RACK_IMAGE_HEIGHT);
				if (y >= yLiquid) {
					for (int dx = -TUBE_HALF_WIDTH + 1; dx <= TUBE_HALF_WIDTH - 1; dx++)
						setPixel(channels, cx + dx, y, TUBE_R_LEVEL, TUBE_G_LEVEL, TUBE_B_LEVEL);
				}
				double wall = shade - WALL_DARKENING;
				setPixel(channels, cx - TUBE_HALF_WIDTH, y, wall, wall, wall);
				setPixel(channels, cx + TUBE_HALF_WIDTH, y, wall, wall, wall);
			}
		}

		for (int y = BAR_TOP; y < BAR_TOP + BAR_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++)
				setPixel(channels, x, y, BAR_LEVEL, BAR_LEVEL, BAR_LEVEL);
		}
		return new ImageData(IMAGE_WIDTH, RACK_IMAGE_HEIGHT, channels);
	}

	private static void setPixel(double[][] channels, int x, int y, double r, double g, double b) {
		if (x < 0 || x >= IMAGE_WIDTH || y < 0 || y >= RACK_IMAGE_HEIGHT)
			return;
		int index = x + y * IMAGE_WIDTH;
		channels[0][index] = r;
		channels[1][index] = g;
		channels[2][index] = b;
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
