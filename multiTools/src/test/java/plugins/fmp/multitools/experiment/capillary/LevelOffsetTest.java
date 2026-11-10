package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import plugins.fmp.multitools.tools.polyline.Level2D;

public class LevelOffsetTest {

	@Test
	public void offsetMovesBothRawCurvesAndKeepsRelativeMeasures() {
		Capillary cap = new Capillary();
		cap.getTopRaw().polylineLevel = level(30, 31, 32);
		cap.getTopRaw().setRefinementQuality(new boolean[] { false, true, false });
		cap.getTopRaw().polyline_old = level(1, 2, 3);
		cap.getBottomRaw().polylineLevel = new Level2D(new double[] { 0, 1, 2 }, new double[] { 4, Double.NaN, 6 }, 3);
		cap.getDerivative().polylineLevel = level(1, -2, 0);
		CapillaryMeasure corrected = new CapillaryMeasure("toplevel_corrected");
		corrected.polylineLevel = level(10, 11, 12);
		cap.setTopCorrected(corrected);
		cap.getGulps().ensureSize(3);
		cap.getGulps().setAmplitudeAt(1, 4);
		cap.getGulps().setBottomPositions(new double[] { Double.NaN, 20, 21 });
		cap.getIntakes().set(new double[] { 0, 3, 0 }, 1, 6, 1, false);
		cap.getIntakes().setBottomPositions(new double[] { 8, 9, 10 });
		cap.setT00YPixels(7);

		assertFalse(cap.addConstantLevelOffset(0));
		assertTrue(cap.addConstantLevelOffset(5));

		assertArrayEquals(new double[] { 35, 36, 37 }, cap.getTopRaw().polylineLevel.ypoints, 0);
		assertArrayEquals(new boolean[] { false, true, false }, cap.getTopRaw().getRefinementQuality());
		assertArrayEquals(new double[] { 6, 7, 8 }, cap.getTopRaw().polyline_old.ypoints, 0);
		assertEquals(9, cap.getBottomRaw().polylineLevel.ypoints[0], 0);
		assertTrue(Double.isNaN(cap.getBottomRaw().polylineLevel.ypoints[1]));
		assertEquals(11, cap.getBottomRaw().polylineLevel.ypoints[2], 0);
		assertArrayEquals(new double[] { 1, -2, 0 }, cap.getDerivative().polylineLevel.ypoints, 0);
		assertArrayEquals(new double[] { 15, 16, 17 }, cap.getTopCorrected().polylineLevel.ypoints, 0);
		assertEquals(4, cap.getGulps().getHeightSeries().ypoints[1], 0);
		assertEquals(25, cap.getGulps().bottomAt(1, -1), 0);
		assertEquals(-1, cap.getGulps().bottomAt(0, -1), 0);
		assertEquals(3, cap.getIntakes().getEvents().getHeightSeries().ypoints[1], 0);
		assertEquals(14, cap.getIntakes().bottomAt(1, -1), 0);
		assertEquals(12, cap.getT00YPixels(), 0);
	}

	@Test
	public void emptyCurvesAreLeftAlone() {
		Capillary cap = new Capillary();
		assertFalse(cap.addConstantLevelOffset(5));
		assertEquals(0, cap.getTopRaw().getNPoints());
		assertEquals(0, cap.getBottomRaw().getNPoints());
	}

	private static Level2D level(double... y) {
		double[] x = new double[y.length];
		for (int i = 0; i < y.length; i++)
			x[i] = i;
		return new Level2D(x, y, y.length);
	}
}
