package plugins.fmp.multitools.experiment.capillary.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import org.junit.BeforeClass;
import org.junit.Test;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class NormedBlueKymoGeometryTest {

	@BeforeClass
	public static void initializeIcy() {
		icy.preferences.IcyPreferences.init();
	}

	@Test
	public void expandBothEndsGrowsEachEndByHalfTheRatio() {
		Line2D blue = new Line2D.Double(10, 10, 10, 110);
		Line2D expanded = NormedBlueKymoGeometry.expandBothEnds(blue, 0.10);
		assertEquals(110, expanded.getP1().distance(expanded.getP2()), 1e-9);
		assertEquals(10, expanded.getX1(), 1e-9);
		assertEquals(5, expanded.getY1(), 1e-9);
		assertEquals(10, expanded.getX2(), 1e-9);
		assertEquals(115, expanded.getY2(), 1e-9);
	}

	@Test
	public void expandBothEndsZeroLeavesTheLineUnchanged() {
		Line2D blue = new Line2D.Double(3, 4, 9, 12);
		Line2D expanded = NormedBlueKymoGeometry.expandBothEnds(blue, 0);
		assertEquals(blue.getP1(), expanded.getP1());
		assertEquals(blue.getP2(), expanded.getP2());
	}

	@Test
	public void orientLikeReversesWhenBluePointsOppositeGreen() {
		Line2D green = new Line2D.Double(10, 0, 10, 100);
		Line2D reversed = NormedBlueKymoGeometry.orientLike(new Line2D.Double(10, 80, 10, 20), green);
		assertEquals(20, reversed.getY1(), 1e-9);
		assertEquals(80, reversed.getY2(), 1e-9);
		Line2D same = NormedBlueKymoGeometry.orientLike(new Line2D.Double(10, 20, 10, 80), green);
		assertEquals(20, same.getY1(), 1e-9);
		assertEquals(80, same.getY2(), 1e-9);
	}

	@Test
	public void maxExpandedLengthUsesTheLongestBlueKeyframe() {
		Capillary cap = capillary(new Line2D.Double(10, 0, 10, 130));
		cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 130), new Line2D.Double(10, 10, 10, 50));
		cap.getPhaseGeometry().putBlue(40, new Line2D.Double(10, 10, 10, 70));
		Capillaries caps = new Capillaries();
		caps.addCapillary(cap);
		assertEquals(66, NormedBlueKymoGeometry.maxExpandedLengthPx(caps.getList(), 0.10));
		assertEquals(66, NormedBlueKymoGeometry.kymoHeight(caps.getList(), 0.10));
	}

	@Test
	public void maxExpandedLengthIgnoresCapillariesWithoutBlueGeometry() {
		Capillary missing = capillary(new Line2D.Double(20, 0, 20, 100));
		Capillaries caps = new Capillaries();
		caps.addCapillary(missing);
		assertEquals(0, NormedBlueKymoGeometry.maxExpandedLengthPx(caps.getList(), 0.10));
		assertNull(NormedBlueKymoGeometry.expandedSamplingLine(missing, 0, 0.10));
	}

	@Test
	public void sampleArcKeepsHeightAndEndpointsOnAShorterLine() {
		Line2D shortBlue = new Line2D.Double(10, 10, 10, 50);
		Point2D[] points = NormedBlueKymoGeometry.sampleArc(shortBlue, 60);
		assertEquals(60, points.length);
		assertEquals(10, points[0].getX(), 1e-9);
		assertEquals(10, points[0].getY(), 1e-9);
		assertEquals(10, points[59].getX(), 1e-9);
		assertEquals(50, points[59].getY(), 1e-9);
		assertEquals(10 + 30 / 59.0 * 40, points[30].getY(), 1e-9);
	}

	@Test
	public void expandedSamplingLineOrientsToGreenAndAppliesExpansion() {
		Capillary cap = capillary(new Line2D.Double(10, 0, 10, 130));
		cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 130), new Line2D.Double(10, 80, 10, 20));
		Line2D sampling = NormedBlueKymoGeometry.expandedSamplingLine(cap, 0, 0.10);
		assertTrue(sampling.getY2() > sampling.getY1());
		assertEquals(66, sampling.getP1().distance(sampling.getP2()), 1e-9);
	}

	private static Capillary capillary(Line2D green) {
		Capillary cap = new Capillary();
		ROI2DLine roi = new ROI2DLine(green);
		roi.setName("line01");
		cap.setRoi(roi);
		return cap;
	}
}
