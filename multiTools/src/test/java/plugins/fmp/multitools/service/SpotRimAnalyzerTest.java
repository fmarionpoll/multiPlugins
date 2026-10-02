package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
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
		double[][] outline = SpotRimAnalyzer.detect(ellipse, width, height, red, green, blue,
				new EllipseGeom[] { ellipse }, 5.0);
		assertNotNull(outline);
		assertTrue(outline[0].length >= 18);
		for (int i = 0; i < outline[0].length; i++) {
			double radius = Math.hypot(outline[0][i] - cx, outline[1][i] - cy);
			assertTrue("radius " + radius, radius > 22 && radius < 28);
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
		double[][] outline = SpotRimAnalyzer.detect(ellipse, width, height, new int[][] { r }, new int[][] { g },
				new int[][] { b }, new EllipseGeom[] { ellipse }, 5.0);
		assertNull(outline);
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
}
