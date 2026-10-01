package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Layout;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Params;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotCross;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.SpotGeom;

public class SpotLineDeficitAnalyzerTest {

	@Test
	public void unchangedDipsStayNearOneAndRemovedPaleDipFalls() {
		int width = 200;
		int height = 40;
		int y = 20;
		int nFrames = 8;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			if (t < 4) {
				addDip(r, width, y, 35, 45, 30);
			}
			addDip(r, width, y, 115, 125, 60);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(40, y, 15), new SpotGeom(120, y, 15));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 4, 40));

		assertEquals(1.0, ratio[0][0], 1e-6);
		assertEquals(1.0, ratio[1][0], 1e-6);
		assertEquals(1.0, ratio[1][7], 1e-6);
		assertEquals(0.0, ratio[0][7], 1e-6);
		assertTrue(ratio[0][3] > 0.95);
		assertTrue(ratio[0][4] < 0.05);
	}

	@Test
	public void nearbyDipsStayOnSeparateSpots() {
		int width = 180;
		int height = 30;
		int y = 15;
		int nFrames = 6;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			if (t < 3) {
				addDip(r, width, y, 46, 54, 25);
			}
			addDip(r, width, y, 74, 82, 50);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, y, 20), new SpotGeom(78, y, 20));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 3, 40));

		assertEquals(1.0, ratio[0][0], 1e-6);
		assertEquals(1.0, ratio[1][0], 1e-6);
		assertEquals(0.0, ratio[0][5], 1e-6);
		assertEquals(1.0, ratio[1][5], 1e-6);
	}

	@Test
	public void shrinkingDiskFallsWhileSlightFadeStaysHigh() {
		int width = 220;
		int height = 80;
		int nFrames = 6;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			int shrinkRadius = t < 3 ? 8 : 3;
			addDisk(r, width, 50, 40, shrinkRadius, 40);
			int fadeDepth = t < 3 ? 30 : 27;
			addDisk(r, width, 150, 40, 8, fadeDepth);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, 40, 15), new SpotGeom(150, 40, 15));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 3, 40));

		assertEquals(1.0, ratio[0][0], 1e-6);
		assertEquals(1.0, ratio[1][0], 1e-6);
		assertTrue(ratio[0][5] < 0.55);
		assertTrue(ratio[0][5] > 0.2);
		assertTrue(ratio[1][5] > 0.85);
		assertTrue(ratio[1][5] < 1.05);
	}

	@Test
	public void faintStainFallsBelowHalfTheInitialExcess() {
		int width = 220;
		int height = 80;
		int nFrames = 6;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			int depth = t < 3 ? 40 : 8;
			addDisk(r, width, 50, 40, 12, depth);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, 40, 8));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 3, 40));
		assertEquals(1.0, ratio[0][0], 1e-6);
		assertTrue(ratio[0][5] < 0.2);
	}

	@Test
	public void collapsedRingFallsWhileRemainingRingStays() {
		int width = 220;
		int height = 80;
		int nFrames = 6;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			int eaten = t < 3 ? 50 : 8;
			addRing(r, width, 50, 40, 6, 11, eaten);
			addRing(r, width, 150, 40, 6, 11, 50);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, 40, 15), new SpotGeom(150, 40, 15));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 3, 40));
		assertEquals(1.0, ratio[0][0], 1e-6);
		assertEquals(1.0, ratio[1][0], 1e-6);
		assertTrue(ratio[0][5] < 0.25);
		assertTrue(ratio[1][5] > 0.85);
	}

	@Test
	public void darkerDiskDoesNotRaiseTheRatio() {
		int width = 220;
		int height = 80;
		int nFrames = 6;
		int[][] red = new int[nFrames][];
		int[][] green = new int[nFrames][];
		int[][] blue = new int[nFrames][];
		for (int t = 0; t < nFrames; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fillFloor(r, g, b, width, height);
			int depth = t < 3 ? 40 : 80;
			addDisk(r, width, 50, 40, 8, depth);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, 40, 15));
		double[][] ratio = SpotLineDeficitAnalyzer.analyze(spots, width, height, red, green, blue,
				new Params(5.0, 3, 40));
		assertEquals(1.0, ratio[0][0], 1e-6);
		assertEquals(1.0, ratio[0][5], 1e-6);
	}

	@Test
	public void movingMedianRemovesASingleBinSpike() {
		double[][] fraction = new double[][] { { 0.5, 0.5, 0.5, 1.0, 0.5, 0.5, 0.5 } };
		double[][] ratio = SpotLineDeficitAnalyzer.ratiosFromIntegrals(fraction, 3, 5);
		assertEquals(1.0, ratio[0][3], 1e-6);
		assertTrue(ratio[0][3] < 1.05);
	}

	@Test
	public void flyPixelIsLeftOutOfTheFraction() {
		int[] red = new int[] { 0, 100, 60 };
		int[] green = new int[] { 100, 100, 100 };
		int[] blue = new int[] { 100, 100, 100 };
		Layout layout = new Layout(3, 1, new int[][] { new int[0] }, new int[][] { new int[] { 0, 1, 2 } });
		double[][] integral = new double[1][2];
		SpotLineDeficitAnalyzer.integrate(layout, red, green, blue, new double[] { 0.0 }, 0.0, integral, 0, null);
		assertEquals(2.0 / 3.0, integral[0][0], 1e-6);
		boolean[] insect = new boolean[] { true, false, false };
		SpotLineDeficitAnalyzer.integrate(layout, red, green, blue, new double[] { 0.0 }, 0.0, integral, 1, insect);
		assertTrue(Double.isNaN(integral[0][1]));

		int n = 20;
		int[] redN = new int[n];
		int[] greenN = new int[n];
		int[] blueN = new int[n];
		int[] pix = new int[n];
		boolean[] mask = new boolean[n];
		Arrays.fill(greenN, 100);
		Arrays.fill(blueN, 100);
		for (int i = 0; i < n; i++) {
			pix[i] = i;
			redN[i] = i < 10 ? 0 : 100;
		}
		mask[0] = true;
		Layout wide = new Layout(n, 1, new int[][] { new int[0] }, new int[][] { pix });
		double[][] wideIntegral = new double[1][1];
		SpotLineDeficitAnalyzer.integrate(wide, redN, greenN, blueN, new double[] { 0.0 }, 0.0, wideIntegral, 0, mask);
		assertEquals(9.0 / 19.0, wideIntegral[0][0], 1e-6);
	}

	@Test
	public void crossTipsStartOutsideTheCircle() {
		List<SpotGeom> spots = Arrays.asList(new SpotGeom(50, 40, 10));
		List<SpotCross> crosses = SpotLineDeficitAnalyzer.crosses(spots, 120, 90, 5);
		assertEquals(1, crosses.size());
		SpotCross cross = crosses.get(0);
		assertEquals(40, cross.x0);
		assertEquals(30, cross.y0);
		assertEquals(60, cross.x1);
		assertEquals(50, cross.y1);
		assertEquals(40, cross.u0);
		assertEquals(50, cross.v0);
		assertEquals(60, cross.u1);
		assertEquals(30, cross.v1);
		assertEquals(4, cross.tipX0.length);
		long dx = (long) cross.tipX0[0] - 50;
		long dy = (long) cross.tipY0[0] - 40;
		assertTrue(dx * dx + dy * dy > 100);
	}

	private static void fillFloor(int[] r, int[] g, int[] b, int width, int height) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int v = 80 + x;
				int pix = y * width + x;
				r[pix] = v;
				g[pix] = v;
				b[pix] = v;
			}
		}
	}

	private static void addDip(int[] red, int width, int y, int x0, int x1, int depth) {
		for (int x = x0; x <= x1; x++) {
			int pix = y * width + x;
			red[pix] = Math.max(0, red[pix] - depth);
		}
	}

	private static void addRing(int[] red, int width, int cx, int cy, int inner, int outer, int depth) {
		int height = red.length / width;
		long inner2 = (long) inner * inner;
		long outer2 = (long) outer * outer;
		for (int y = Math.max(0, cy - outer); y <= Math.min(height - 1, cy + outer); y++) {
			for (int x = Math.max(0, cx - outer); x <= Math.min(width - 1, cx + outer); x++) {
				long dx = (long) x - cx;
				long dy = (long) y - cy;
				long d2 = dx * dx + dy * dy;
				if (d2 > inner2 && d2 <= outer2) {
					int pix = y * width + x;
					red[pix] = Math.max(0, red[pix] - depth);
				}
			}
		}
	}
	private static void addDisk(int[] red, int width, int cx, int cy, int radius, int depth) {
		int height = red.length / width;
		long r2 = (long) radius * radius;
		for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y++) {
			for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
				long dx = (long) x - cx;
				long dy = (long) y - cy;
				if (dx * dx + dy * dy <= r2) {
					int pix = y * width + x;
					red[pix] = Math.max(0, red[pix] - depth);
				}
			}
		}
	}
}
