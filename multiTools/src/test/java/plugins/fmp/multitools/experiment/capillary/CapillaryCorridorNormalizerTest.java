package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.Arrays;

import org.junit.Test;

public class CapillaryCorridorNormalizerTest {
	@Test
	public void bestFitLinePreservesLongitudinalExtent() {
		CapillaryCorridorNormalizer.Fit fit = CapillaryCorridorNormalizer.fitPoints(Arrays.asList(
				new Point2D.Double(20, 10), new Point2D.Double(21, 60), new Point2D.Double(19, 110)));
		assertEquals(100.0, fit.line.getP1().distance(fit.line.getP2()), 0.1);
		assertTrue(fit.maxDeviation < 2.0);
	}
}
