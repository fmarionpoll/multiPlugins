package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Assume;
import org.junit.Test;

import plugins.fmp.multitools.service.CapillaryLengthDetector.AxisMeasure;
import plugins.fmp.multitools.service.CapillaryLengthDetector.Geometry;
import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;
import plugins.fmp.multitools.service.CapillaryLengthDetector.TipFind;

/**
 * Checks that walking inward from each ROI end recovers the glass tips: empty
 * tops count, the rack bar in the middle is ignored, a fly in the overhang is
 * not a tip, and peripheral tubes stay shorter than central ones without a
 * length prior.
 */
public class CapillaryLengthDetectorTest {

	private static final int IMAGE_WIDTH = 1024;
	private static final int IMAGE_HEIGHT = 520;
	private static final int N_CAPILLARIES = 20;
	private static final int CAPILLARY_TOP = 40;
	private static final double NOMINAL_LENGTH = 400.;
	private static final int TUBE_HALF_WIDTH = 4;
	private static final int OVERHANG = 8;

	private static final double BACKGROUND_LEVEL = 200.;
	private static final double TUBE_R_LEVEL = 110.;
	private static final double TUBE_G_LEVEL = 215.;
	private static final double TUBE_B_LEVEL = 220.;

	private static final int RACK_IMAGE_HEIGHT = 640;
	private static final int TUBE_TOP = 40;
	private static final double NOMINAL_RACK_LENGTH = 520.;
	private static final int EMPTY_TOP_LENGTH = 90;
	private static final int BAR_TOP = 430;
	private static final int BAR_HEIGHT = 30;
	private static final double BAR_LEVEL = 60.;
	private static final double WALL_DARKENING = 14.;
	private static final double FLY_LEVEL = 45.;

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
		CapillaryLengthDetectorOptions options = syntheticOptions();

		List<Double> measured = new ArrayList<Double>();
		double worstError = 0.;
		for (int i = 0; i < N_CAPILLARIES; i++) {
			double length = i == brokenIndex ? trueLength(i) - 120. : trueLength(i);
			ArrayList<int[]> axis = overhangingAxis(capillaryX(i), CAPILLARY_TOP, length);
			AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary " + i + " should be located", located.found);
			double detected = located.endFrac - located.startFrac;
			measured.add(Double.valueOf(detected));
			if (i != brokenIndex)
				worstError = Math.max(worstError, Math.abs(detected - trueLength(i)));
		}

		assertTrue("recovered lengths should be within 3 px of the truth, worst was " + worstError, worstError <= 3.);

		double centre = measured.get(N_CAPILLARIES / 2).doubleValue();
		double edge = measured.get(0).doubleValue();
		assertTrue("the capillary at the edge must be shorter than the one at the centre", edge < centre);

		double brokenLength = measured.get(brokenIndex).doubleValue();
		assertTrue("the truncated capillary must stand out", Math.abs(brokenLength - trueLength(brokenIndex)) > 20.);
	}

	@Test
	public void everyUsableLengthIsReplacedByTheTrend() {
		CapillaryLengthResult result = new CapillaryLengthResult();
		for (int i = 0; i < N_CAPILLARIES; i++) {
			double jitter = (i % 2 == 0) ? 3. : -2.;
			CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null, "line" + i, 400);
			measure.setCentroidX(capillaryX(i));
			measure.setRoiPixels(500.);
			measure.setDetectedPixels(trueLength(i) + jitter);
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setSelected(true);
			result.addMeasure(measure);
		}

		CapillaryLengthDetector.validate(result, IMAGE_WIDTH, new CapillaryLengthDetectorOptions());

		for (int i = 0; i < N_CAPILLARIES; i++) {
			CapillaryLengthResult.Measure m = result.getMeasures().get(i);
			assertTrue(m.isSelected());
			assertEquals("measured px must match trend px for capillary " + i, m.getFittedPixels(),
					m.getDetectedPixels(), 0.01);
			assertEquals(trueLength(i), m.getDetectedPixels(), 3.);
		}
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
		assertEquals(CapillaryLengthResult.Status.CORRECTED, broken.getStatus());
		assertTrue("a corrected outlier must be applied by default", broken.isSelected());
		assertEquals("the short tube must be replaced by the trend", broken.getFittedPixels(), broken.getDetectedPixels(),
				1.);
		assertTrue("the comment must record the original length, was: " + broken.getMessage(),
				broken.getMessage().contains("replaced") && broken.getMessage().contains("trend"));
		assertTrue("all capillaries must stay selected after the snap, only " + result.countSelected() + " were",
				result.countSelected() == N_CAPILLARIES);

		double expectedSpread = 100. * (trueLength(10) - trueLength(0)) / trueLength(10);
		assertEquals(expectedSpread, result.getSpreadPercent(), 1.);
	}

	@Test
	public void aLongOutlierIsReplacedByTheTrend() {
		int longIndex = 4;
		CapillaryLengthResult result = new CapillaryLengthResult();
		for (int i = 0; i < N_CAPILLARIES; i++) {
			CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null, "line" + i, 400);
			measure.setCentroidX(capillaryX(i));
			measure.setRoiPixels(500.);
			measure.setDetectedPixels(i == longIndex ? trueLength(i) + 80. : trueLength(i));
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setSelected(true);
			result.addMeasure(measure);
		}

		CapillaryLengthDetector.validate(result, IMAGE_WIDTH, new CapillaryLengthDetectorOptions());

		CapillaryLengthResult.Measure longOne = result.getMeasures().get(longIndex);
		assertEquals(CapillaryLengthResult.Status.CORRECTED, longOne.getStatus());
		assertTrue(longOne.isSelected());
		assertEquals(longOne.getFittedPixels(), longOne.getDetectedPixels(), 1.);
		assertTrue(longOne.getMessage().contains("replaced"));
		assertEquals(trueLength(longIndex), longOne.getDetectedPixels(), 5.);
	}

	@Test
	public void snappingTheOverlayKeepsTheConfidentTip() {
		ArrayList<int[]> axis = verticalAxis(10, 0, 400);
		CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null, "line", 400);
		measure.setDetectedEndpoints(new Point2D.Double(10, 120), new Point2D.Double(10, 400));
		CapillaryLengthDetector.applyEndpointYs(measure, 40., 400., axis);
		assertEquals(40., measure.getDetectedStart().getY(), 2.);
		assertEquals(400., measure.getDetectedEnd().getY(), 2.);
		assertEquals(360., measure.getDetectedPixels(), 2.);
	}

	@Test
	public void anOutlierTopIsMovedOntoTheTrendOfTheOtherTops() {
		CapillaryLengthResult result = new CapillaryLengthResult();
		int broken = 3;
		for (int i = 0; i < N_CAPILLARIES; i++) {
			double len = trueLength(i);
			double top = 40.;
			double bot = top + len;
			if (i == broken)
				top = 140.;
			CapillaryLengthResult.Measure measure = new CapillaryLengthResult.Measure(null, "line" + i, 400);
			measure.setCentroidX(capillaryX(i));
			measure.setRoiPixels(500.);
			measure.setDetectedEndpoints(new Point2D.Double(capillaryX(i), top),
					new Point2D.Double(capillaryX(i), bot));
			measure.setDetectedPixels(bot - top);
			measure.setStatus(CapillaryLengthResult.Status.OK);
			measure.setSelected(true);
			result.addMeasure(measure);
		}

		CapillaryLengthDetector.validate(result, IMAGE_WIDTH, new CapillaryLengthDetectorOptions());

		CapillaryLengthResult.Measure m = result.getMeasures().get(broken);
		assertEquals(CapillaryLengthResult.Status.CORRECTED, m.getStatus());
		assertEquals(40., m.getDetectedStart().getY(), 5.);
		assertEquals(40. + trueLength(broken), m.getDetectedEnd().getY(), 5.);
		assertEquals(m.getFittedPixels(), m.getDetectedPixels(), 1.);
		assertTrue(m.getMessage().contains("replaced") || m.getMessage().contains("trend"));
	}

	@Test
	public void detectsEndpointsInsideAnOverhangingRoi() {
		ImageData image = buildSyntheticImage(-1);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		ArrayList<int[]> axis = overhangingAxis(capillaryX(5), CAPILLARY_TOP, trueLength(5));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);

		assertTrue(located.found);
		assertTrue("endpoints must be found inside the ROI, not at its ends", !located.touchesBorder);
		assertEquals(OVERHANG, located.startFrac, 2.);
		assertEquals(trueLength(5), located.endFrac - located.startFrac, 2.);
	}

	@Test
	public void measuresTheEmptyTopAndIgnoresTheMidRoiBar() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();

		for (int i = 0; i < N_CAPILLARIES; i++) {
			ArrayList<int[]> axis = overhangingAxis(capillaryX(i), TUBE_TOP, rackTubeLength(i));
			AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary " + i + " should be located", located.found);
			assertEquals("capillary " + i + " must start at the glass top, not at the liquid meniscus", OVERHANG,
					located.startFrac, 3.);
			assertEquals("capillary " + i + " must reach its tip", OVERHANG + rackTubeLength(i), located.endFrac, 3.);
		}
	}

	@Test
	public void countsTheAirColumnAsPartOfTheCapillary() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();
		ArrayList<int[]> axis = overhangingAxis(capillaryX(9), TUBE_TOP, rackTubeLength(9));

		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		double length = located.endFrac - located.startFrac;

		assertEquals(rackTubeLength(9), length, 3.);
		assertTrue("the air column must not be dropped, " + length + " px measured for a tube whose liquid alone is "
				+ (rackTubeLength(9) - EMPTY_TOP_LENGTH) + " px", length > rackTubeLength(9) - EMPTY_TOP_LENGTH + 20.);
	}

	@Test
	public void distortionIsKeptWithoutALengthPrior() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();

		double worstError = 0.;
		double edge = Double.NaN;
		double centre = Double.NaN;
		for (int i = 0; i < N_CAPILLARIES; i++) {
			ArrayList<int[]> axis = overhangingAxis(capillaryX(i), TUBE_TOP, rackTubeLength(i));
			AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
			assertTrue("capillary " + i + " should be located", located.found);
			double length = located.endFrac - located.startFrac;
			worstError = Math.max(worstError, Math.abs(length - rackTubeLength(i)));
			if (i == 0)
				edge = length;
			if (i == N_CAPILLARIES / 2)
				centre = length;
		}
		assertTrue("lengths should stay within 5 px of the truth, worst was " + worstError, worstError <= 5.);
		assertTrue("edge capillaries must stay shorter than centre ones", edge < centre);
	}

	@Test
	public void aFlyInTheOverhangDoesNotBecomeTheTip() {
		ImageData image = buildRackImage(true);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 5;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));

		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue(located.found);
		assertEquals(OVERHANG, located.startFrac, 3.);
		assertEquals("the measure must stop at the glass, not at the fly", OVERHANG + rackTubeLength(index),
				located.endFrac, 3.);
	}

	@Test
	public void aRoiStartingOnTheGlassIsFlaggedBorderNotFailed() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 3;
		int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> axis = verticalAxis(capillaryX(index), TUBE_TOP, yEnd + OVERHANG);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue("a ROI that starts on the glass must still be measured, " + located.failure, located.found);
		assertTrue("the top tip sitting on the ROI end must be flagged", located.touchesBorder);
		assertEquals(0., located.startFrac, 2.);
		assertEquals(rackTubeLength(index), located.endFrac - located.startFrac, 4.);
	}

	@Test
	public void aRoiShortOfTheTipIsNotSilentlyExtended() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();

		int index = 5;
		int tubeEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> shortRoi = verticalAxis(capillaryX(index), TUBE_TOP + 5, tubeEnd - 5);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(shortRoi, image, options);
		assertTrue("a ROI that does not overhang the glass must fail or be flagged at the border",
				!located.found || located.touchesBorder);
	}

	@Test
	public void startsAtTheGlassWhenTheMeniscusIsCloseToTheTop() {
		ImageData image = buildRackImage(false, 16, false);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 8;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue(located.found);
		assertEquals("must start at the glass, not at the meniscus 16 px below", OVERHANG, located.startFrac, 3.);
		assertEquals(OVERHANG + rackTubeLength(index), located.endFrac, 3.);
	}

	@Test
	public void findsTheTipWhenTheRoiOverhangsFarIntoTheCage() {
		ImageData image = buildRackImage();
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 4;
		int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> axis = verticalAxis(capillaryX(index), TUBE_TOP - OVERHANG, yEnd + 70);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue("a long bottom overhang must still find the glass, " + located.failure, located.found);
		assertEquals(OVERHANG, located.startFrac, 3.);
		assertEquals("the extra cage pixels must not be counted", OVERHANG + rackTubeLength(index), located.endFrac,
				4.);
	}

	@Test
	public void findsTheBottomTipInsideADarkCage() {
		ImageData image = buildRackImage(false, EMPTY_TOP_LENGTH, true);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 6;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue(located.found);
		assertEquals(OVERHANG, located.startFrac, 3.);
		assertEquals("the glass tip in the dark slot must not be cut short", OVERHANG + rackTubeLength(index),
				located.endFrac, 4.);
	}

	@Test
	public void aWideCageSlotBelowTheTipIsNotTheGlass() {
		ImageData image = buildRackImage(false, EMPTY_TOP_LENGTH, false, true);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 7;
		int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> axis = verticalAxis(capillaryX(index), TUBE_TOP - OVERHANG, yEnd + 40);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue("capillary should still be located, " + located.failure, located.found);
		assertEquals(OVERHANG, located.startFrac, 3.);
		assertEquals("the cage slot past the glass must not lengthen the measure", OVERHANG + rackTubeLength(index),
				located.endFrac, 4.);
	}

	@Test
	public void aShortWallStubInTheOverhangIsNotTheTip() {
		ImageData image = buildRackImage();
		paintOverhangWallStub(image);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 5;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue(located.found);
		assertEquals("a few pixels of wall-like noise above the glass must not become the top", OVERHANG,
				located.startFrac, 3.);
		assertEquals(OVERHANG + rackTubeLength(index), located.endFrac, 3.);
	}

	@Test
	public void aHorizontalLidLineIsNotTheTopTip() {
		ImageData image = buildRackImage();
		paintTopLidLine(image);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 4;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue(located.found);
		assertEquals("a lid edge in the overhang must not become the top", OVERHANG, located.startFrac, 3.);
		assertEquals(OVERHANG + rackTubeLength(index), located.endFrac, 3.);
	}

	@Test
	public void findsTheBottomTipWhenTheTubeIsBrighterThanTheCage() {
		ImageData image = buildRackImage();
		paintInvertedDarkSlot(image);
		CapillaryLengthDetectorOptions options = syntheticOptions();
		int index = 1;
		int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(index));
		ArrayList<int[]> axis = verticalAxis(capillaryX(index), TUBE_TOP - OVERHANG, yEnd + 40);
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, image, options);
		assertTrue("glass in a dark slot must still be found, " + located.failure, located.found);
		assertEquals(OVERHANG, located.startFrac, 3.);
		assertEquals("the bottom tip must stay in the slot, not jump up to the slot opening",
				OVERHANG + rackTubeLength(index), located.endFrac, 5.);
	}

	@Test
	public void medianOfFramesDropsATransientFly() {
		ImageData clean = buildRackImage();
		ImageData withFly = buildRackImage(true);
		List<ImageData> frames = new ArrayList<ImageData>();
		frames.add(clean);
		frames.add(clean);
		frames.add(withFly);
		frames.add(clean);
		frames.add(withFly);
		ImageData combined = CapillaryLengthDetector.combineFrames(frames);
		int index = 5;
		ArrayList<int[]> axis = overhangingAxis(capillaryX(index), TUBE_TOP, rackTubeLength(index));
		AxisMeasure located = CapillaryLengthDetector.locateAlongAxis(axis, combined, syntheticOptions());
		assertTrue(located.found);
		assertEquals("a fly present in a minority of frames must not become the tip",
				OVERHANG + rackTubeLength(index), located.endFrac, 3.);
	}

	@Test
	public void findsTubeTopsOnARealFrame() throws Exception {
		File file = realFrameFile();
		Assume.assumeTrue("real frame not available on this machine", file != null && file.isFile());

		ImageData image = readImage(file);
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		List<Integer> columns = findCapillaryColumns(image);
		Assume.assumeTrue("could not isolate capillary columns on this frame", columns.size() >= 10);

		int corridorTop = 20;
		List<Double> tops = new ArrayList<Double>();
		for (Integer x : columns) {
			ArrayList<int[]> axis = verticalAxis(x.intValue(), corridorTop, image.height - 1);
			Geometry geometry = CapillaryLengthDetector.estimateGeometry(axis, image, options);
			TipFind top = CapillaryLengthDetector.findTip(axis, image, geometry, 0, +1, options);
			if (top.found)
				tops.add(Double.valueOf(corridorTop + top.axisFrac));
		}
		Assume.assumeTrue("too few tube tops found on this frame", tops.size() >= 8);

		double referenceTop = median(tops);
		int consistent = 0;
		for (Double top : tops) {
			if (Math.abs(top.doubleValue() - referenceTop) <= 15.)
				consistent++;
		}
		assertTrue("tube tops should agree with each other, only " + consistent + " of " + tops.size()
				+ " sit within 15 px of the median (" + referenceTop + ")", consistent >= 0.8 * tops.size());
	}

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

	private static CapillaryLengthDetectorOptions syntheticOptions() {
		CapillaryLengthDetectorOptions options = new CapillaryLengthDetectorOptions();
		// Synthetic tubes have perfectly sharp square ends and therefore no optical
		// side-wall shoulder to compensate.
		options.tipInsetHalfWidthScale = 0.;
		return options;
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

	private static ArrayList<int[]> overhangingAxis(int x, int tubeTop, double tubeLength) {
		int yFrom = tubeTop - OVERHANG;
		int yTo = (int) Math.round(tubeTop + tubeLength) + OVERHANG;
		return verticalAxis(x, yFrom, yTo);
	}

	private static ArrayList<int[]> verticalAxis(int x, int yFrom, int yTo) {
		ArrayList<int[]> axis = new ArrayList<int[]>();
		for (int y = yFrom; y <= yTo; y++)
			axis.add(new int[] { x, y });
		return axis;
	}

	private static void paintOverhangWallStub(ImageData image) {
		for (int i = 0; i < N_CAPILLARIES; i++) {
			int cx = capillaryX(i);
			for (int y = TUBE_TOP - 6; y < TUBE_TOP - 2; y++) {
				double shade = BACKGROUND_LEVEL - 15. * (cx / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) RACK_IMAGE_HEIGHT);
				double wall = shade - WALL_DARKENING;
				setPixel(image.channels, cx - TUBE_HALF_WIDTH, y, wall, wall, wall, RACK_IMAGE_HEIGHT);
				setPixel(image.channels, cx + TUBE_HALF_WIDTH, y, wall, wall, wall, RACK_IMAGE_HEIGHT);
			}
		}
	}

	private static void paintTopLidLine(ImageData image) {
		for (int y = TUBE_TOP - 5; y < TUBE_TOP - 2; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++)
				setPixel(image.channels, x, y, 80., 80., 80., RACK_IMAGE_HEIGHT);
		}
	}

	private static void paintInvertedDarkSlot(ImageData image) {
		for (int i = 0; i < N_CAPILLARIES; i++) {
			int cx = capillaryX(i);
			int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
			for (int y = yEnd - 80; y < yEnd + 40 && y < RACK_IMAGE_HEIGHT; y++) {
				for (int dx = -20; dx <= 20; dx++) {
					boolean onTube = y >= TUBE_TOP && y < yEnd && Math.abs(dx) <= TUBE_HALF_WIDTH;
					if (!onTube)
						setPixel(image.channels, cx + dx, y, 40., 40., 40., RACK_IMAGE_HEIGHT);
				}
			}
		}
	}

	private static ImageData buildSyntheticImage(int brokenIndex) {
		double[][] channels = new double[3][IMAGE_WIDTH * IMAGE_HEIGHT];
		for (int y = 0; y < IMAGE_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++) {
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
				double shade = BACKGROUND_LEVEL - 15. * (cx / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) IMAGE_HEIGHT);
				for (int dx = -TUBE_HALF_WIDTH + 1; dx <= TUBE_HALF_WIDTH - 1; dx++) {
					int x = cx + dx;
					if (x < 0 || x >= IMAGE_WIDTH)
						continue;
					int index = x + y * IMAGE_WIDTH;
					channels[0][index] = TUBE_R_LEVEL;
					channels[1][index] = TUBE_G_LEVEL;
					channels[2][index] = TUBE_B_LEVEL;
				}
				double wall = shade - WALL_DARKENING;
				setPixel(channels, cx - TUBE_HALF_WIDTH, y, wall, wall, wall, IMAGE_HEIGHT);
				setPixel(channels, cx + TUBE_HALF_WIDTH, y, wall, wall, wall, IMAGE_HEIGHT);
			}
		}
		return new ImageData(IMAGE_WIDTH, IMAGE_HEIGHT, channels);
	}

	private static ImageData buildRackImage() {
		return buildRackImage(false, EMPTY_TOP_LENGTH, false, false);
	}

	private static ImageData buildRackImage(boolean withFly) {
		return buildRackImage(withFly, EMPTY_TOP_LENGTH, false, false);
	}

	private static ImageData buildRackImage(boolean withFly, int emptyTopLength, boolean darkCage) {
		return buildRackImage(withFly, emptyTopLength, darkCage, false);
	}

	private static ImageData buildRackImage(boolean withFly, int emptyTopLength, boolean darkCage, boolean wideSlot) {
		double[][] channels = new double[3][IMAGE_WIDTH * RACK_IMAGE_HEIGHT];
		for (int y = 0; y < RACK_IMAGE_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++) {
				double shade = BACKGROUND_LEVEL - 15. * (x / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) RACK_IMAGE_HEIGHT);
				setPixel(channels, x, y, shade, shade, shade, RACK_IMAGE_HEIGHT);
			}
		}

		for (int i = 0; i < N_CAPILLARIES; i++) {
			int cx = capillaryX(i);
			int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
			int yLiquid = TUBE_TOP + emptyTopLength;
			for (int y = TUBE_TOP; y < yEnd && y < RACK_IMAGE_HEIGHT; y++) {
				double shade = BACKGROUND_LEVEL - 15. * (cx / (double) IMAGE_WIDTH)
						+ 10. * (y / (double) RACK_IMAGE_HEIGHT);
				if (y >= yLiquid) {
					for (int dx = -TUBE_HALF_WIDTH + 1; dx <= TUBE_HALF_WIDTH - 1; dx++)
						setPixel(channels, cx + dx, y, TUBE_R_LEVEL, TUBE_G_LEVEL, TUBE_B_LEVEL, RACK_IMAGE_HEIGHT);
				}
				double wall = shade - WALL_DARKENING;
				setPixel(channels, cx - TUBE_HALF_WIDTH, y, wall, wall, wall, RACK_IMAGE_HEIGHT);
				setPixel(channels, cx + TUBE_HALF_WIDTH, y, wall, wall, wall, RACK_IMAGE_HEIGHT);
			}
		}

		for (int y = BAR_TOP; y < BAR_TOP + BAR_HEIGHT; y++) {
			for (int x = 0; x < IMAGE_WIDTH; x++)
				setPixel(channels, x, y, BAR_LEVEL, BAR_LEVEL, BAR_LEVEL, RACK_IMAGE_HEIGHT);
		}

		if (darkCage) {
			for (int i = 0; i < N_CAPILLARIES; i++) {
				int cx = capillaryX(i);
				int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
				for (int y = yEnd - 18; y < yEnd + 25 && y < RACK_IMAGE_HEIGHT; y++) {
					for (int dx = -18; dx <= 18; dx++) {
						int x = cx + dx;
						boolean onTube = y >= TUBE_TOP && y < yEnd && Math.abs(dx) <= TUBE_HALF_WIDTH;
						if (!onTube)
							setPixel(channels, x, y, FLY_LEVEL, FLY_LEVEL, FLY_LEVEL, RACK_IMAGE_HEIGHT);
					}
				}
			}
		}

		if (withFly) {
			int i = 5;
			int cx = capillaryX(i);
			int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
			for (int y = yEnd + 2; y < yEnd + OVERHANG && y < RACK_IMAGE_HEIGHT; y++) {
				for (int dx = -6; dx <= 6; dx++)
					setPixel(channels, cx + dx, y, FLY_LEVEL, FLY_LEVEL, FLY_LEVEL, RACK_IMAGE_HEIGHT);
			}
		}
		if (wideSlot) {
			for (int i = 0; i < N_CAPILLARIES; i++) {
				int cx = capillaryX(i);
				int yEnd = (int) Math.round(TUBE_TOP + rackTubeLength(i));
				for (int y = yEnd + 2; y < yEnd + 50 && y < RACK_IMAGE_HEIGHT; y++) {
					for (int dx = -14; dx <= 14; dx++)
						setPixel(channels, cx + dx, y, 70., 70., 70., RACK_IMAGE_HEIGHT);
				}
			}
		}
		return new ImageData(IMAGE_WIDTH, RACK_IMAGE_HEIGHT, channels);
	}

	private static void setPixel(double[][] channels, int x, int y, double r, double g, double b, int height) {
		if (x < 0 || x >= IMAGE_WIDTH || y < 0 || y >= height)
			return;
		int index = x + y * IMAGE_WIDTH;
		channels[0][index] = r;
		channels[1][index] = g;
		channels[2][index] = b;
	}

	private static double median(List<Double> values) {
		List<Double> sorted = new ArrayList<Double>(values);
		java.util.Collections.sort(sorted);
		return sorted.get(sorted.size() / 2).doubleValue();
	}
}
