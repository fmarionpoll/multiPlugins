package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillary.measurefilter.CapillaryMeasureFilter;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterRule;
import plugins.fmp.multitools.experiment.capillary.measurefilter.MeasureFilterSource;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class BottomBaselineEstimatorTest {

	@Test
	public void noiseChecksUseCurrentMeasuresWithoutASavedReference() {
		Capillary stable = new Capillary();
		stable.getBottomRaw().polylineLevel = level(100, 100, 100, 100, 100, 100, 100);
		Capillary noisy = new Capillary();
		noisy.getBottomRaw().polylineLevel = level(100, 101, 102, 103, 104, 105, 140);
		noisy.setBottomBaselineY(999);
		noisy.setBottomBaselineMad(999);
		noisy.setBottomBaselineOutlierFrac(1);

		assertEquals(0, BottomBaselineEstimator.noiseScore(stable), 0);
		assertEquals(1.5 + 100.0 / 7, BottomBaselineEstimator.noiseScore(noisy), 1e-12);
		assertEquals(Arrays.asList(noisy, stable),
				BottomBaselineEstimator.rankByBottomNoise(Arrays.asList(stable, noisy)));
		MeasureFilterRule rule = new MeasureFilterRule();
		rule.source = MeasureFilterSource.BOTTOM_BASELINE_MAD;
		assertEquals(1.5, CapillaryMeasureFilter.compute(noisy, rule), 0);
		noisy.getBottomRaw().polylineLevel = level(100, 100, 100, 100, 100, 100, 100);
		assertEquals(0, CapillaryMeasureFilter.compute(noisy, rule), 0);
		assertEquals(0, BottomBaselineEstimator.noiseScore(noisy), 0);
	}

	private static Level2D level(double... y) {
		return new Level2D(new double[y.length], y, y.length);
	}
}
