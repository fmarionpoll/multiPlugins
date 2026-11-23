package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.SpotBackgroundEstimator.Sample;
import plugins.fmp.multitools.service.SpotBackgroundEstimator.Source;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;

public class SpotBackgroundEstimatorTest {

	private static final int WIDTH = 260;
	private static final int HEIGHT = 260;

	private static List<SpotGeom> grid() {
		List<SpotGeom> spots = new ArrayList<>();
		for (int y = 20; y <= 220; y += 40)
			for (int x = 20; x <= 220; x += 40) spots.add(new SpotGeom(x, y, 8));
		return spots;
	}

	private static int[] channel(int level) {
		int[] result = new int[WIDTH * HEIGHT];
		Arrays.fill(result, level);
		return result;
	}

	@Test
	public void cleanBranchesKeepTheirOriginalFloor() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30, 3);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		int[] r = channel(150), other = channel(160);
		double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(layout, r, other, other);
		Sample sample = estimator.sample(r, other, other, null, flanks);
		assertTrue(estimator.patchCount() > 20);
		for (int s = 0; s < spots.size(); s++) {
			assertEquals(Source.BRANCHES, sample.sources[s]);
			assertEquals(10, sample.floors[s], 0);
		}
	}

	@Test
	public void obscuredBranchesUseCleanPatchesWithoutChangingSignalSampling() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30, 3);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		int[] r = channel(150), other = channel(160);
		boolean[] mask = new boolean[r.length];
		int spot = 14;
		for (int p : layout.flankPix[spot]) mask[p] = true;
		for (int p : layout.spotPix[spot]) r[p] = 100;
		double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(layout, r, other, other, mask);
		assertTrue(Double.isNaN(SpotLineDeficitAnalyzer.spotFloors(layout, flanks)[spot]));
		Sample sample = estimator.sample(r, other, other, mask, flanks);
		assertEquals(Source.PATCHES, sample.sources[spot]);
		assertEquals(10, sample.floors[spot], 0);
		double[][] integral = new double[spots.size()][1];
		SpotLineDeficitAnalyzer.integrate(layout, r, other, other, sample.floors, 1, integral, 0, mask);
		assertEquals(1, integral[spot][0], 0);
	}

	@Test
	public void evenOneLegPixelRejectsAPatchAndSearchMovesFartherAway() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		int[] r = channel(150), other = channel(160);
		double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(layout, r, other, other);
		Arrays.fill(flanks, Double.NaN);
		Sample before = estimator.sample(r, other, other, null, flanks);
		boolean[] mask = new boolean[r.length];
		int blocked = before.selectedPatches[14][0];
		Point center = estimator.patchCenter(blocked);
		mask[center.y * WIDTH + center.x + 2] = true;
		Sample after = estimator.sample(r, other, other, mask, flanks);
		assertEquals(before.cleanPatches - 1, after.cleanPatches);
		assertEquals(Source.PATCHES, after.sources[14]);
		assertEquals(10, after.floors[14], 0);
		for (int p : after.selectedPatches[14]) assertNotEquals(blocked, p);
	}

	@Test
	public void partiallyContaminatedBranchesPreferPatchesAndMedianToleratesOneOutlier() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		int[] r = channel(150), other = channel(160);
		double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(layout, r, other, other);
		int count = layout.flankRun[0].length;
		Arrays.fill(flanks, 0, count / 4, Double.NaN);
		assertEquals(10, SpotLineDeficitAnalyzer.spotFloors(layout, flanks)[0], 0);
		Sample before = estimator.sample(r, other, other, null, flanks);
		assertEquals(Source.PATCHES, before.sources[0]);
		Point center = estimator.patchCenter(before.selectedPatches[0][0]);
		for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++)
			r[(center.y + dy) * WIDTH + center.x + dx] = 0;
		Sample after = estimator.sample(r, other, other, null, flanks);
		assertEquals(10, after.floors[0], 0);
	}

	@Test
	public void allBackgroundObscuredUsesReferenceOrRemainsUnknown() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		int[] r = channel(150), other = channel(160);
		boolean[] mask = new boolean[r.length];
		Arrays.fill(mask, true);
		double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(layout, r, other, other, mask);
		Sample sample = estimator.sample(r, other, other, mask, flanks);
		assertEquals(Source.UNKNOWN, sample.sources[0]);
		assertTrue(Double.isNaN(sample.withReference(null)[0]));
		assertEquals(Source.UNKNOWN, sample.sourceWithReference(0, new double[] { Double.NaN }));
		assertEquals(7, sample.withReference(new double[] { 7 })[0], 0);
		assertEquals(Source.REFERENCE, sample.sourceWithReference(0, new double[] { 7 }));
		assertTrue(Double.isNaN(sample.floors[0]));
	}

	@Test
	public void patchesStayInsideCageAndClearOfEveryEnlargedRoi() {
		List<SpotGeom> spots = grid();
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 30);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, (x, y) -> x < 120);
		assertTrue(estimator.patchCount() > 0);
		for (int i = 0; i < estimator.patchCount(); i++) {
			Point center = estimator.patchCenter(i);
			for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
				int x = center.x + dx, y = center.y + dy;
				assertTrue(x < 120 && x >= 20 && y >= 20 && y < 220);
				for (SpotGeom spot : spots)
					assertTrue(Point.distance(x, y, spot.centerX, spot.centerY) > spot.radius + SpotBackgroundEstimator.ROI_CLEARANCE);
			}
		}
	}

	@Test
	public void tooFewPatchesAndCollinearArraysDoNotInventBackground() {
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(20, 20, 8), new SpotGeom(60, 20, 8));
		Layout layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 0);
		SpotBackgroundEstimator estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, null);
		assertEquals(0, estimator.patchCount());
		Sample sample = estimator.sample(channel(150), channel(160), channel(160), null, new double[0]);
		assertEquals(Source.UNKNOWN, sample.sources[0]);
		assertTrue(Double.isNaN(sample.floors[0]));

		spots = grid();
		layout = SpotLineDeficitAnalyzer.layout(spots, WIDTH, HEIGHT, 0);
		estimator = new SpotBackgroundEstimator(layout, spots, HEIGHT, (x, y) -> x <= 25 && y <= 35);
		assertTrue(estimator.patchCount() < SpotBackgroundEstimator.MIN_PATCHES);
		sample = estimator.sample(channel(150), channel(160), channel(160), null, new double[0]);
		assertEquals(Source.UNKNOWN, sample.sources[0]);
	}
}
