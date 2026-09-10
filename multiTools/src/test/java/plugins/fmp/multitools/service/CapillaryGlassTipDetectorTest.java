package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.geom.Point2D;

import org.junit.Test;

import plugins.fmp.multitools.service.CapillaryLengthDetector.ImageData;

/*
 * CODEX
 */
public class CapillaryGlassTipDetectorTest {
	@Test
	public void glassRimIsIndependentOfFillingLevel() {
		for (int liquid : new int[] { 40, 52, 60 }) {
			CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(tube(liquid, true),
					new Point2D.Double(40, 48), new Point2D.Double(40, 135), 4.);
			assertNotNull(e.glassTip);
			assertEquals(40., e.glassTip.getY(), 2.);
			assertNotNull(e.liquidTop);
			assertEquals(liquid, e.liquidTop.getY(), 5.);
		}
	}

	@Test
	public void colourBoundaryWithoutWallsIsNotAGlassTip() {
		CapillaryGlassTipDetector.Evidence e = CapillaryGlassTipDetector.find(tube(52, false),
				new Point2D.Double(40, 48), new Point2D.Double(40, 135), 4.);
		assertNull(e.glassTip);
		assertNotNull(e.liquidTop);
	}

	@Test
	public void imageBorderIsUncertainRatherThanClamped() {
		assertNull(CapillaryGlassTipDetector.find(tube(52, true), new Point2D.Double(2, 48), new Point2D.Double(2, 135),
				4.).glassTip);
	}

	private ImageData tube(int liquid, boolean walls) {
		int width = 80, height = 180;
		double[][] pixels = new double[3][width * height];
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				for (int c = 0; c < 3; c++) {
					double v = 180.;
					if (y >= liquid && y < 140 && x > 36 && x < 44)
						v = c == 0 ? 70. : 150.;
					if (walls && y >= 40 && y < 140 && (x == 36 || x == 44))
						v = 120.;
					pixels[c][x + y * width] = v;
				}
		return new ImageData(width, height, pixels);
	}
}
