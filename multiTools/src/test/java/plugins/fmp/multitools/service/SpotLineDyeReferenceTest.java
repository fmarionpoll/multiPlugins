package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;

public class SpotLineDyeReferenceTest {

	private static final int SIZE = 100;
	private final Layout layout = SpotLineDeficitAnalyzer.layout(Arrays.asList(new SpotGeom(50, 50, 30)), SIZE, SIZE, 10, 3);
	private final int[] other = filled(150);

	private static int[] filled(int value) {
		int[] data = new int[SIZE * SIZE];
		Arrays.fill(data, value);
		return data;
	}

	private static int[] disk(int cx, int cy, int radius, int deficit) {
		int[] red = filled(150);
		for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
			if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) red[y * SIZE + x] -= deficit;
		}
		return red;
	}

	@Test
	public void smallDyeInsideOversizedRoiIsCalibratedFromBlueSamples() {
		int[] red = disk(50, 50, 4, 40);
		// More than three quarters of this ROI's X is empty.
		assertEquals(0, SpotLineDeficitAnalyzer.medianSignalExcess(layout, 0, red, other, other, 0, null), 0);
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 5);
		reference.observe(0, 0, red, other, other, 0, 1, null);
		assertTrue(reference.dyeLevel(0) > 20);
		assertTrue(reference.footprintStations(0) > 0);
		assertTrue(reference.footprintStations(0) < layout.spotRun[0].length / 2);
		assertTrue(reference.measure(0, red, other, other, 0, 1, null) > 0);
	}

	@Test
	public void legInEmptyMarginDoesNotRejectOrRescaleTheDye() {
		int[] red = disk(50, 50, 6, 40);
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 5);
		reference.observe(0, 0, red, other, other, 0, 1, null);
		boolean[] leg = new boolean[other.length];
		for (int y = 29; y < 37; y++) for (int x = 29; x < 37; x++) leg[y * SIZE + x] = true;
		double[][] old = new double[1][1];
		SpotLineDeficitAnalyzer.integrate(layout, red, other, other, new double[] { 0 }, 1, old, 0, leg);
		assertTrue(Double.isNaN(old[0][0]));
		assertEquals(reference.measure(0, red, other, other, 0, 1, null),
				reference.measure(0, red, other, other, 0, 1, leg), 0);
		reference.observe(0, 1, red, other, other, 0, 1, leg);
		assertEquals(2, reference.acceptedFrames(0));
	}

	@Test
	public void trueDyeOcclusionRemainsUnknownAndCannotCalibrate() {
		int[] red = disk(50, 50, 6, 40);
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 5);
		reference.observe(0, 0, red, other, other, 0, 1, null);
		boolean[] fly = new boolean[other.length];
		for (int y = 45; y <= 55; y++) for (int x = 45; x <= 55; x++) fly[y * SIZE + x] = true;
		assertTrue(Double.isNaN(reference.measure(0, red, other, other, 0, 1, fly)));
		reference.observe(0, 1, red, other, other, 0, 1, fly);
		assertEquals(1, reference.acceptedFrames(0));
	}

	@Test
	public void oneEarlyBlueFrameSurvivesFourEmptyCalibrationFramesAndSmoothing() {
		int[] blueSpot = disk(50, 50, 6, 40), empty = filled(150);
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 5);
		for (int t = 0; t < 5; t++) reference.observe(0, t, t == 0 ? blueSpot : empty, other, other, 0, 1, null);
		assertTrue(reference.complete(0));
		assertEquals(40, reference.dyeLevel(0), 0);
		assertEquals(0, reference.firstDyeFrame(0));
		double[][] raw = { new double[8] };
		for (int t = 0; t < raw[0].length; t++) raw[0][t] = reference.measure(0, t == 0 ? blueSpot : empty, other, other, 0, 1, null);
		double[] ratio = SpotLineDeficitAnalyzer.ratiosFromDyeIntegrals(raw, 5, 9)[0];
		SpotLineDeficitAnalyzer.finalizeDyeRatio(ratio, reference.dyeLevel(0), 1, false);
		assertEquals(1, ratio[0], 0);
		assertEquals(0, ratio[1], 0);
	}

	@Test
	public void initialOcclusionsRemainMissingWhenFirstClearViewIsEmpty() {
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 2);
		int[] empty = filled(150);
		boolean[] fly = new boolean[other.length];
		Arrays.fill(fly, true);
		for (int t = 0; t < 3; t++) reference.observe(0, t, empty, other, other, 0, 1, fly);
		assertTrue(Double.isNaN(reference.dyeLevel(0)));
		reference.observe(0, 3, empty, other, other, 0, 1, null);
		reference.observe(0, 4, empty, other, other, 0, 1, null);
		assertEquals(3, reference.firstClearFrame(0));
		assertEquals(-1, reference.firstDyeFrame(0));
		double[][] raw = { new double[5] };
		for (int t = 0; t < 5; t++) raw[0][t] = reference.measure(0, empty, other, other, 0, 1, t < 3 ? fly : null);
		double[] ratio = SpotLineDeficitAnalyzer.ratiosFromDyeIntegrals(raw, 2, 9)[0];
		SpotLineDeficitAnalyzer.finalizeDyeRatio(ratio, reference.dyeLevel(0), 1, false);
		assertTrue(Double.isNaN(ratio[0]));
		assertTrue(Double.isNaN(ratio[2]));
		assertEquals(0, ratio[3], 0);
	}

	@Test
	public void missingBackgroundAndIsolatedBlueEvidenceCannotEstablishAnEmptyReference() {
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 5);
		reference.observe(0, 0, other, other, other, Double.NaN, 1, null);
		assertEquals(0, reference.acceptedFrames(0));
		reference.observe(0, 0, new int[0], new int[0], new int[0], 0, 1, null);
		assertEquals(0, reference.acceptedFrames(0));
		int[] noise = filled(150);
		noise[50 * SIZE + 50] = 0;
		reference.observe(0, 1, noise, other, other, 0, 1, null);
		assertEquals(0, reference.acceptedFrames(0));
		assertTrue(Double.isNaN(reference.dyeLevel(0)));
	}

	@Test
	public void missingImageCannotBeMeasuredAsEmptyAfterCalibration() {
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 1);
		reference.observe(0, 0, other, other, other, 0, 1, null);
		assertEquals(0, reference.dyeLevel(0), 0);
		assertTrue(Double.isNaN(reference.measure(0, new int[0], new int[0], new int[0], 0, 1, null)));
		assertTrue(Double.isNaN(reference.measure(0, null, null, null, 0, 1, null)));
	}

	@Test
	public void movementMarginKeepsSmallTranslationsMeasurable() {
		SpotLineDyeReference reference = new SpotLineDyeReference(layout, 1);
		int[] red = disk(50, 50, 6, 40);
		reference.observe(0, 0, red, other, other, 0, 1, null);
		double start = reference.measure(0, red, other, other, 0, 1, null);
		double moved = reference.measure(0, disk(51, 50, 6, 40), other, other, 0, 1, null);
		assertTrue(moved / start > 0.8);
	}

	@Test
	public void smoothingDoesNotBridgeOcclusionsOrEraseFirstObservation() {
		double[][] raw = { { Double.NaN, 1, Double.NaN, 0, 0, 0 } };
		double[] ratio = SpotLineDeficitAnalyzer.ratiosFromDyeIntegrals(raw, 5, 9)[0];
		assertEquals(1, ratio[1], 0);
		assertTrue(Double.isNaN(ratio[2]));
		assertEquals(0, ratio[3], 0);
	}

	@Test
	public void legacyRimTemporalHelpersRetainTheirPreviousBehavior() {
		double[][] raw = { { 1, 0, 0, 0, 0, 0 } };
		assertEquals(0, SpotLineDeficitAnalyzer.ratiosFromIntegrals(raw, 5, 9)[0][0], 0);
		assertEquals(1, SpotLineDeficitAnalyzer.ratiosFromDyeIntegrals(raw, 5, 9)[0][0], 0);
		double[] legacy = { Double.NaN, 0 };
		SpotLineDeficitAnalyzer.finalizeRatio(legacy, 0, 1, false);
		assertEquals(0, legacy[0], 0);
		double[] current = { Double.NaN, 0 };
		SpotLineDeficitAnalyzer.finalizeDyeRatio(current, 0, 1, false);
		assertTrue(Double.isNaN(current[0]));
	}

	@Test
	public void singleLowObservationCannotPermanentlySuppressLaterDye() {
		double[] values = { 1, 0, Double.NaN, 0.9, 0.8 };
		SpotLineDeficitAnalyzer.suppressDyeRebound(values);
		assertEquals(0.9, values[3], 0);
		values = new double[] { 1, 0, 0.8, 0.7 };
		SpotLineDeficitAnalyzer.suppressDyeRebound(values);
		assertEquals(0.8, values[2], 0);
	}
}

