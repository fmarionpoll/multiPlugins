package plugins.fmp.multitools.experiment.cages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class EvaporationAverageNaNTest {

	@Test
	public void oneInvalidSampleDoesNotDropTheOtherEmptyCapillaries() {
		List<Capillary> caps = Arrays.asList(
				capillary(new double[] { 0, 10, Double.NaN, 30 }),
				capillary(new double[] { 0, 12, 22, 32 }),
				capillary(new double[] { 0, 8, 18, 28 }),
				capillary(new double[] { 0, 10, 20, 30 }));

		Level2D avg = CagesCapillariesComputation.computeAverageMeasure(caps);

		assertEquals(4, avg.npoints);
		assertEquals(0.0, avg.ypoints[0], 1e-9);
		assertEquals((10 + 12 + 8 + 10) / 4.0, avg.ypoints[1], 1e-9);
		assertEquals((22 + 18 + 20) / 3.0, avg.ypoints[2], 1e-9);
		assertEquals((30 + 32 + 28 + 30) / 4.0, avg.ypoints[3], 1e-9);
	}

	@Test
	public void timeWithNoFiniteSampleStaysMissing() {
		List<Capillary> caps = Arrays.asList(
				capillary(new double[] { 0, Double.NaN, 20 }),
				capillary(new double[] { 2, Double.NaN, 22 }));

		Level2D avg = CagesCapillariesComputation.computeAverageMeasure(caps);

		assertEquals(1.0, avg.ypoints[0], 1e-9);
		assertTrue(Double.isNaN(avg.ypoints[1]));
		assertEquals(21.0, avg.ypoints[2], 1e-9);
	}

	private static Capillary capillary(double[] y) {
		Capillary cap = new Capillary();
		double[] x = new double[y.length];
		for (int i = 0; i < y.length; i++)
			x[i] = i;
		cap.getTopRaw().polylineLevel = new Level2D(x, y, y.length);
		return cap;
	}
}
