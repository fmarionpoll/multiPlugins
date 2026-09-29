package plugins.fmp.multitools.series;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class DetectFlyToolsBackgroundGateTest {

	@Test
	public void keepsWholeBlobWhenADarkBandMatchesTheBackground() {
		int width = 20;
		Point[] blob = rect(2, 5, 14, 10);
		int[] current = fill(width, 12, 40);
		int[] comparison = fill(width, 12, 180);
		for (int y = 5; y <= 10; y++) {
			for (int x = 7; x <= 10; x++)
				comparison[x + y * width] = 40;
		}
		assertTrue(DetectFlyTools.passesBackgroundComparison(blob, current, comparison, width, 0, 0, 20, false));
		assertEquals(40, current[8 + 6 * width]);
		assertEquals(blob.length, rect(2, 5, 14, 10).length);
	}

	@Test
	public void dropsBlobThatMatchesTheBackground() {
		int width = 20;
		Point[] blob = rect(2, 5, 14, 10);
		int[] current = fill(width, 12, 40);
		int[] comparison = fill(width, 12, 40);
		assertFalse(DetectFlyTools.passesBackgroundComparison(blob, current, comparison, width, 0, 0, 20, false));
	}

	@Test
	public void dropsBlobWhenTooFewPixelsDiffer() {
		int width = 20;
		Point[] blob = rect(2, 5, 14, 10);
		int[] current = fill(width, 12, 40);
		int[] comparison = fill(width, 12, 40);
		for (int i = 0; i < 10; i++) {
			Point p = blob[i];
			comparison[p.x + p.y * width] = 180;
		}
		assertFalse(DetectFlyTools.passesBackgroundComparison(blob, current, comparison, width, 0, 0, 20, false));
	}

	private static int[] fill(int width, int height, int value) {
		int[] samples = new int[width * height];
		for (int i = 0; i < samples.length; i++)
			samples[i] = value;
		return samples;
	}

	private static Point[] rect(int x0, int y0, int x1, int y1) {
		List<Point> pts = new ArrayList<>();
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++)
				pts.add(new Point(x, y));
		}
		return pts.toArray(new Point[0]);
	}
}
