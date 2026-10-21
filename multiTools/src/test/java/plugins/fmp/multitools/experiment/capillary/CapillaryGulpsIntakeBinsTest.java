package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CapillaryGulpsIntakeBinsTest {

	private static CapillaryGulps gulps(double... heights) {
		CapillaryGulps g = new CapillaryGulps();
		g.ensureSize(heights.length);
		for (int i = 0; i < heights.length; i++)
			g.setAmplitudeAt(i, heights[i]);
		return g;
	}

	@Test
	public void rawKeepsSubPixelAmplitudesAndDropsNonEvents() {
		CapillaryGulps g = gulps(0, 2.5, -1, Double.NaN, 0.4);
		assertArrayEquals(new double[] { 0, 2.5, 0, 0, 0.4 }, g.getIntakeBins(5, null), 1e-12);
	}

	@Test
	public void correctedSubtractsEvaporationOverTheSameInterval() {
		CapillaryGulps g = gulps(0, 3, 0, 2, 0.1);
		double[] evaporation = { 0, 0.2, 0.5, 0.9, 1.4 };
		double[] corr = g.getIntakeBins(5, evaporation);
		assertArrayEquals(new double[] { 0, 2.8, 0, 1.6, -0.4 }, corr, 1e-12);
		assertArrayEquals(new double[] { 0, 2.8, 2.8, 4.4, 4.0 }, CapillaryGulps.cumulate(corr), 1e-12);
	}

	@Test
	public void missingEvaporationIncrementGivesNaNAndIsSkippedInTotal() {
		CapillaryGulps g = gulps(0, 1, 1, 1);
		double[] corr = g.getIntakeBins(4, new double[] { 0, 0.1, Double.NaN });
		assertEquals(0.9, corr[1], 1e-12);
		assertTrue(Double.isNaN(corr[2]));
		assertTrue(Double.isNaN(corr[3]));
		assertEquals(0.9, CapillaryGulps.cumulate(corr)[3], 1e-12);
	}
}