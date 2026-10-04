package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;

public class SpotRimAnalyzerTest {

	@Test
	public void ringInsideEllipseBecomesOutlineAtThatRadius() {
		int width = 220;
		int height = 220;
		int cx = 100;
		int cy = 100;
		int[][] red = new int[3][];
		int[][] green = new int[3][];
		int[][] blue = new int[3][];
		for (int t = 0; t < 3; t++) {
			int[] r = new int[width * height];
			int[] g = new int[width * height];
			int[] b = new int[width * height];
			fill(r, g, b, 80);
			paintRing(r, g, b, width, cx, cy, 23, 27);
			red[t] = r;
			green[t] = g;
			blue[t] = b;
		}
		EllipseGeom ellipse = new EllipseGeom(cx, cy, 40, 40);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, red, green, blue,
				new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		assertTrue(outline.x.length >= 18);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - cx, outline.y[i] - cy);
			assertTrue("radius " + radius, radius > 24 && radius < 30);
		}
		assertTrue("path " + outline.pathWidthPx, outline.pathWidthPx >= 2 && outline.pathWidthPx <= 8);
	}

	@Test
	public void inwardVertexIsDroppedByHull() {
		int n = 24;
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++) {
			double angle = 2.0 * Math.PI * i / n;
			double radius = i == 0 ? 5.0 : 20.0;
			xs[i] = radius * Math.cos(angle);
			ys[i] = radius * Math.sin(angle);
		}
		double[][] hull = SpotRimGeometry.convexHull(xs, ys);
		assertEquals(n - 1, hull[0].length);
		for (int i = 0; i < hull[0].length; i++) {
			assertTrue(Math.hypot(hull[0][i], hull[1][i]) > 15);
		}
		double[][] simple = SpotRimGeometry.simplifyOutline(xs, ys, 0, 0);
		assertNotNull(simple);
		assertTrue(simple[0].length <= 24);
		for (int i = 0; i < simple[0].length; i++) {
			assertTrue(Math.hypot(simple[0][i], simple[1][i]) > 15);
		}
	}

	@Test
	public void detectedOutlineFollowsAnOffsetStain() {
		int width = 220;
		int height = 220;
		int spotX = 100;
		int spotY = 100;
		int stainX = 112;
		int stainY = 96;
		int[] r = new int[width * height];
		int[] g = new int[width * height];
		int[] b = new int[width * height];
		fill(r, g, b, 80);
		paintRing(r, g, b, width, stainX, stainY, 12, 16);
		EllipseGeom ellipse = new EllipseGeom(spotX, spotY, 40, 30);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r },
				new int[][] { g }, new int[][] { b }, new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		assertFalse(outline.flyFallback);
		double cx = 0;
		double cy = 0;
		for (int i = 0; i < outline.x.length; i++) {
			cx += outline.x[i];
			cy += outline.y[i];
		}
		cx /= outline.x.length;
		cy /= outline.y.length;
		assertEquals(stainX, cx, 3.0);
		assertEquals(stainY, cy, 3.0);
		assertTrue(Math.hypot(cx - spotX, cy - spotY) > 8);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - stainX, outline.y[i] - stainY);
			assertTrue("radius " + radius, radius > 12 && radius < 20);
		}
	}

	@Test
	public void inwardBiteIsReplacedByTheHull() {
		double[] xs = new double[] { 0, 20, 20, 10, 0 };
		double[] ys = new double[] { 0, 0, 20, 6, 20 };
		double[][] filled = SpotRimGeometry.withoutInwardBite(xs, ys, SpotRimGeometry.INWARD_BITE_PX);
		assertNotNull(filled);
		assertEquals(4, filled[0].length);
		for (int i = 0; i < filled[0].length; i++) {
			assertTrue(Math.hypot(filled[0][i] - 10, filled[1][i] - 6) > 1);
		}
		double[] squareX = new double[] { 0, 10, 10, 0 };
		double[] squareY = new double[] { 0, 0, 10, 10 };
		assertNull(SpotRimGeometry.withoutInwardBite(squareX, squareY, SpotRimGeometry.INWARD_BITE_PX));
	}

	@Test
	public void outlineFollowsOuterEdgeOfTheStain() {
		int width = 220;
		int height = 220;
		int cx = 100;
		int cy = 100;
		int[] r = new int[width * height];
		int[] g = new int[width * height];
		int[] b = new int[width * height];
		fill(r, g, b, 80);
		paintBand(r, g, b, width, cx, cy, 8, 30, 50, 160, 160);
		paintBand(r, g, b, width, cx, cy, 22, 30, 20, 220, 220);
		EllipseGeom ellipse = new EllipseGeom(cx, cy, 40, 40);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r },
				new int[][] { g }, new int[][] { b }, new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - cx, outline.y[i] - cy);
			assertTrue("radius " + radius, radius > 27 && radius < 33);
		}
		assertTrue("path " + outline.pathWidthPx, outline.pathWidthPx >= 8);
	}

	@Test
	public void darkRingIsPreferredToPaleFringe() {
		int width = 220;
		int height = 220;
		int cx = 100;
		int cy = 100;
		int[] r = new int[width * height];
		int[] g = new int[width * height];
		int[] b = new int[width * height];
		fill(r, g, b, 80);
		paintBand(r, g, b, width, cx, cy, 30, 36, 70, 100, 100);
		paintBand(r, g, b, width, cx, cy, 18, 24, 20, 200, 200);
		EllipseGeom ellipse = new EllipseGeom(cx, cy, 40, 40);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r },
				new int[][] { g }, new int[][] { b }, new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - cx, outline.y[i] - cy);
			assertTrue("radius " + radius, radius > 20 && radius < 28);
		}
	}


	@Test
	public void blankEllipseHasNoOutline() {
		int width = 80;
		int height = 80;
		int[] r = new int[width * height];
		int[] g = new int[width * height];
		int[] b = new int[width * height];
		fill(r, g, b, 80);
		EllipseGeom ellipse = new EllipseGeom(40, 40, 20, 20);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r },
				new int[][] { g }, new int[][] { b }, new EllipseGeom[] { ellipse }, 5.0);
		assertNull(outline);
	}

	@Test
	public void flyOnEveryFrameUsesShrunkSpotRoi() {
		int width = 220;
		int height = 220;
		int cx = 100;
		int cy = 100;
		int[] r = new int[width * height];
		int[] g = new int[width * height];
		int[] b = new int[width * height];
		fill(r, g, b, 80);
		paintRing(r, g, b, width, cx, cy, 23, 27);
		boolean[] fly = new boolean[width * height];
		Arrays.fill(fly, true);
		EllipseGeom ellipse = new EllipseGeom(cx, cy, 40, 40);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r },
				new int[][] { g }, new int[][] { b }, new boolean[][] { fly }, new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		assertTrue(outline.flyFallback);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - cx, outline.y[i] - cy);
			assertEquals(28.0, radius, 0.05);
		}
	}

	@Test
	public void cleanFrameIsKeptWhenAnotherFrameHasAFly() {
		int width = 220;
		int height = 220;
		int cx = 100;
		int cy = 100;
		int[] coveredR = new int[width * height];
		int[] coveredG = new int[width * height];
		int[] coveredB = new int[width * height];
		fill(coveredR, coveredG, coveredB, 80);
		paintRing(coveredR, coveredG, coveredB, width, cx, cy, 30, 36);
		int[] cleanR = new int[width * height];
		int[] cleanG = new int[width * height];
		int[] cleanB = new int[width * height];
		fill(cleanR, cleanG, cleanB, 80);
		paintRing(cleanR, cleanG, cleanB, width, cx, cy, 14, 18);
		boolean[] fly = new boolean[width * height];
		Arrays.fill(fly, true);
		EllipseGeom ellipse = new EllipseGeom(cx, cy, 40, 40);
		SpotRimAnalyzer.Detection outline = SpotRimAnalyzer.detect(ellipse, width, height,
				new int[][] { coveredR, cleanR }, new int[][] { coveredG, cleanG }, new int[][] { coveredB, cleanB },
				new boolean[][] { fly, null }, new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		assertFalse(outline.flyFallback);
		for (int i = 0; i < outline.x.length; i++) {
			double radius = Math.hypot(outline.x[i] - cx, outline.y[i] - cy);
			assertTrue("radius " + radius, radius > 15 && radius < 22);
		}
	}

	@Test
	public void offsetStepsAwayFromCentroid() {
		int n = 36;
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++) {
			double angle = 2.0 * Math.PI * i / n;
			xs[i] = 10.0 * Math.cos(angle);
			ys[i] = 10.0 * Math.sin(angle);
		}
		double[][] outer = SpotRimGeometry.offsetOutward(xs, ys, 4);
		for (int i = 0; i < n; i++) {
			double radius = Math.hypot(outer[0][i], outer[1][i]);
			assertEquals(14.0, radius, 0.05);
		}
	}

	@Test
	public void translateMovesStoredOutline() {
		SpotRimGeometry rim = new SpotRimGeometry();
		rim.setOutline(new double[] { 1, 2, 3 }, new double[] { 4, 5, 6 });
		rim.translate(10, -2);
		assertEquals(11, rim.outlineX()[0], 1e-9);
		assertEquals(2, rim.outlineY()[0], 1e-9);
		SpotRimGeometry copy = rim.copy();
		rim.translate(5, 5);
		assertEquals(11, copy.outlineX()[0], 1e-9);
	}

	@Test
	public void neighborEllipseIsDroppedFromFloor() {
		int n = 32;
		double[] xs = new double[n];
		double[] ys = new double[n];
		for (int i = 0; i < n; i++) {
			double angle = 2.0 * Math.PI * i / n;
			xs[i] = 50 + 20 * Math.cos(angle);
			ys[i] = 50 + 20 * Math.sin(angle);
		}
		EllipseGeom neighbor = new EllipseGeom(92, 50, 22, 22);
		int width = 200;
		int height = 120;
		int[] floor = SpotRimAnalyzer.floorPixels(xs, ys, 2, 16, width, height, new EllipseGeom[] { neighbor });
		assertTrue(floor.length > 0);
		boolean anyLeft = false;
		for (int pix : floor) {
			int x = pix % width;
			int y = pix / width;
			assertTrue(!neighbor.contains(x, y));
			if (x < 40) {
				anyLeft = true;
			}
		}
		assertTrue(anyLeft);
		int[] raw = SpotRimAnalyzer.floorPixels(xs, ys, 2, 16, width, height, new EllipseGeom[0]);
		boolean neighborHit = false;
		for (int pix : raw) {
			int x = pix % width;
			int y = pix / width;
			if (neighbor.contains(x, y)) {
				neighborHit = true;
				break;
			}
		}
		assertTrue(neighborHit);
	}

	private static void fill(int[] r, int[] g, int[] b, int level) {
		Arrays.fill(r, level);
		Arrays.fill(g, level);
		Arrays.fill(b, level);
	}

	private static void paintRing(int[] r, int[] g, int[] b, int width, int cx, int cy, double inner, double outer) {
		int height = r.length / width;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				double d = Math.hypot(x - cx, y - cy);
				if (d >= inner && d <= outer) {
					int pix = y * width + x;
					r[pix] = 20;
					g[pix] = 200;
					b[pix] = 200;
				}
			}
		}
	}
	private static void paintBand(int[] r, int[] g, int[] b, int width, int cx, int cy, double inner, double outer,
			int rv, int gv, int bv) {
		int height = r.length / width;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				double d = Math.hypot(x - cx, y - cy);
				if (d >= inner && d <= outer) {
					int pix = y * width + x;
					r[pix] = rv;
					g[pix] = gv;
					b[pix] = bv;
				}
			}
		}
	}

}
