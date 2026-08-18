package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;
import plugins.fmp.multitools.series.options.GulpThresholdMethod;

public class GulpDetectorFromToprawTest {

	@Test
	public void firstBinGulpIsAccepted() {
		double[] y = { 10, 50, 50, 50 };
		double[] gulps = new double[y.length];
		GulpDetectorFromTopraw.detectGulpsFromLevel(y, gulps, 5.0, 1, y.length);
		assertEquals(0.0, gulps[0], 1e-9);
		assertEquals(40.0, gulps[1], 1e-9);
		assertEquals(0.0, gulps[2], 1e-9);
		assertEquals(0.0, gulps[3], 1e-9);
	}

	@Test
	public void emptyCageScaleNoiseIsRejected() {
		List<Double> emptyAbsDy = new ArrayList<Double>();
		double[] emptyY = { 10, 11, 10.5, 11.2, 10.8 };
		GulpDetectorFromTopraw.collectAbsDeltaY(emptyY, emptyAbsDy);
		double theta = GulpDetectorFromTopraw.robustScale(emptyAbsDy, GulpThresholdMethod.MEAN_PLUS_SD, 3.0);
		assertTrue(theta > 0);

		double[] flyY = { 10, 10.4, 10.2, 50 };
		double[] gulps = new double[flyY.length];
		GulpDetectorFromTopraw.detectGulpsFromLevel(flyY, gulps, theta, 1, flyY.length);
		assertEquals(0.0, gulps[1], 1e-9);
		assertEquals(0.0, gulps[2], 1e-9);
		assertTrue(gulps[3] > theta);
		assertEquals(flyY[3] - flyY[2], gulps[3], 1e-9);
	}

	@Test
	public void acceptDoesNotRequireXDiffn() {
		double[] y = { 0, 20 };
		double[] gulps = new double[2];
		GulpDetectorFromTopraw.detectGulpsFromLevel(y, gulps, 1.0, 1, 2);
		assertEquals(20.0, gulps[1], 1e-9);
	}

	@Test
	public void missingXmlMethodDefaultsToToprawDy() {
		assertEquals(GulpDetectionMethod.TOPRAW_DY, GulpDetectionMethod.fromXml(null));
		assertEquals(GulpDetectionMethod.TOPRAW_DY, GulpDetectionMethod.fromXml(""));
		assertEquals(GulpDetectionMethod.XDIFFN_REF, GulpDetectionMethod.fromXml("XDIFFN_REF"));
	}
}
