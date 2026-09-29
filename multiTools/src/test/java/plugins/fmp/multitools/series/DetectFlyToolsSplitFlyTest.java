package plugins.fmp.multitools.series;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import icy.roi.BooleanMask2D;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

public class DetectFlyToolsSplitFlyTest {

	@Test
	public void joinsStubbyPiecesCutAlongTheBody() throws InterruptedException {
		BooleanMask2D head = rect(10, 20, 22, 28);
		BooleanMask2D abdomen = rect(30, 20, 42, 28);
		List<BooleanMask2D> joined = DetectFlyTools.mergeSplitFlyBodies(list(head, abdomen), options());
		assertEquals(1, joined.size());
		assertEquals(1, joined.get(0).getComponents().length);
		assertTrue(joined.get(0).contains(16, 24));
		assertTrue(joined.get(0).contains(36, 24));
		assertTrue(joined.get(0).contains(26, 24));
	}

	@Test
	public void leavesSideBySideFliesApart() throws InterruptedException {
		BooleanMask2D upper = rect(10, 10, 30, 16);
		BooleanMask2D lower = rect(10, 24, 30, 30);
		List<BooleanMask2D> joined = DetectFlyTools.mergeSplitFlyBodies(list(upper, lower), options());
		assertEquals(2, joined.size());
	}

	@Test
	public void leavesTwoFullFliesInARowApart() throws InterruptedException {
		BooleanMask2D first = rect(0, 0, 27, 7);
		BooleanMask2D second = rect(32, 0, 59, 7);
		List<BooleanMask2D> joined = DetectFlyTools.mergeSplitFlyBodies(list(first, second), options());
		assertEquals(2, joined.size());
	}

	@Test
	public void leavesDistantFragmentsApart() throws InterruptedException {
		BooleanMask2D left = rect(0, 0, 12, 8);
		BooleanMask2D right = rect(40, 0, 52, 8);
		List<BooleanMask2D> joined = DetectFlyTools.mergeSplitFlyBodies(list(left, right), options());
		assertEquals(2, joined.size());
	}

	@Test
	public void joinsADiagonalCut() throws InterruptedException {
		BooleanMask2D a = stroke(8, 8, 0, 5, 4);
		BooleanMask2D b = stroke(8, 8, 11, 16, 4);
		List<BooleanMask2D> joined = DetectFlyTools.mergeSplitFlyBodies(list(a, b), options());
		assertEquals(1, joined.size());
		assertEquals(1, joined.get(0).getComponents().length);
	}

	private static BuildSeriesOptions options() {
		BuildSeriesOptions options = new BuildSeriesOptions();
		options.blimitUp = true;
		options.blimitRatio = true;
		options.limitUp = 500;
		options.limitRatio = 4;
		return options;
	}

	private static List<BooleanMask2D> list(BooleanMask2D... masks) {
		List<BooleanMask2D> out = new ArrayList<>();
		for (BooleanMask2D mask : masks)
			out.add(mask);
		return out;
	}

	/** Thick diagonal segment along y = x, from step {@code t0} through {@code t1}. */
	private static BooleanMask2D stroke(int x0, int y0, int t0, int t1, int halfWidth) {
		List<Point> pts = new ArrayList<>();
		for (int t = t0; t <= t1; t++) {
			for (int w = -halfWidth; w <= halfWidth; w++)
				pts.add(new Point(x0 + t, y0 + t + w));
		}
		return new BooleanMask2D(pts.toArray(new Point[0]));
	}

	private static BooleanMask2D rect(int x0, int y0, int x1, int y1) {
		List<Point> pts = new ArrayList<>();
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++)
				pts.add(new Point(x, y));
		}
		return new BooleanMask2D(pts.toArray(new Point[0]));
	}
}
