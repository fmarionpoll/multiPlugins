package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer.Params;
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
}
