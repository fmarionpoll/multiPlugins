package plugins.fmp.multitools.service.tracking;

import static org.junit.Assert.assertEquals;

import java.awt.geom.Line2D;

import org.junit.Test;

public class DirectionalCapillaryTrackerTest {
	@Test
	public void tracksTranslatedShaftAndEnds() {
		int w = 180, h = 180;
		double[] ref = render(w, h, 70, 35, 70, 140);
		double[] cur = render(w, h, 75, 32, 75, 137);
		Line2D tracked = new DirectionalCapillaryTracker().track(new Line2D.Double(70, 35, 70, 140), ref, cur, w, h);
		assertEquals(75, tracked.getX1(), 0.01);
		assertEquals(32, tracked.getY1(), 0.01);
		assertEquals(75, tracked.getX2(), 0.01);
		assertEquals(137, tracked.getY2(), 0.01);
	}

	private static double[] render(int w, int h, int x1, int y1, int x2, int y2) {
		double[] a = new double[w * h];
		java.util.Arrays.fill(a, 200);
		for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
			for (int x = x1 - 2; x <= x1 + 2; x++) a[y * w + x] = 40;
		for (int x = x1 - 8; x <= x1 + 8; x++) {
			a[y1 * w + x] = 10;
			a[y2 * w + x] = 10;
		}
		return a;
	}
}
