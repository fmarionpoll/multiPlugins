package plugins.fmp.multitools.experiment.capillary.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Line2D;

import org.junit.BeforeClass;
import org.junit.Test;

import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale.MeasurePixelSource;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CapillaryPixelScaleTest {

	@BeforeClass
	public static void initializeIcy() {
		icy.preferences.IcyPreferences.init();
	}

	@Test
	public void greenSourceFollowsBlueLengthAtT() {
		Capillary cap = capillaryWithTwoBlues();
		assertEquals(40, CapillaryPixelScale.pixelsFor32mm(cap, 0, MeasurePixelSource.GREEN_KYMO), 1e-9);
		assertEquals(60, CapillaryPixelScale.pixelsFor32mm(cap, 50, MeasurePixelSource.GREEN_KYMO), 1e-9);
		assertEquals(40, CapillaryPixelScale.blueLengthPx(cap, 0), 1e-9);
	}

	@Test
	public void blueNormedSourceStaysAtMaxBlue() {
		Capillary cap = capillaryWithTwoBlues();
		assertEquals(60, CapillaryPixelScale.maxBlueLengthPx(cap), 1e-9);
		assertEquals(60, CapillaryPixelScale.pixelsFor32mm(cap, 0, MeasurePixelSource.BLUE_NORMED_KYMO), 1e-9);
		assertEquals(60, CapillaryPixelScale.pixelsFor32mm(cap, 50, MeasurePixelSource.BLUE_NORMED_KYMO), 1e-9);
	}

	@Test
	public void fallsBackToGetPixelsWhenGeometryMissing() {
		Capillary cap = new Capillary();
		ROI2DLine roi = new ROI2DLine(new Line2D.Double(10, 0, 10, 80));
		roi.setName("line01");
		cap.setRoi(roi);
		cap.setPixels(90);
		assertEquals(90, CapillaryPixelScale.blueLengthPx(cap, 0), 1e-9);
		assertEquals(90, CapillaryPixelScale.maxBlueLengthPx(cap), 1e-9);
	}

	@Test
	public void ulPerRowUsesExpansionAndPerCapHeight() {
		Capillary cap = capillaryWithTwoBlues();
		cap.setVolume(2.0);
		double e = 0.10;
		int h = NormedBlueKymoGeometry.kymoHeight(cap, e);
		assertEquals(66, h);
		double per = CapillaryPixelScale.ulPerNativePixel(cap, 0, MeasurePixelSource.BLUE_NORMED_KYMO, e);
		assertEquals(2.0 * 1.10 / 66.0, per, 1e-12);
		double ul = CapillaryPixelScale.toUl(h / 2.0, cap, 0, MeasurePixelSource.BLUE_NORMED_KYMO, e, false);
		assertEquals((h / 2.0) * per, ul, 1e-12);
	}

	@Test
	public void twoCapillariesHaveDifferentMaxBlue() {
		Capillary center = capillaryWithTwoBlues();
		Capillary edge = new Capillary();
		ROI2DLine roi = new ROI2DLine(new Line2D.Double(80, 0, 80, 50));
		roi.setName("line02");
		edge.setRoi(roi);
		edge.getPhaseGeometry().initialize(0, new Line2D.Double(80, 0, 80, 50), new Line2D.Double(80, 5, 80, 35));
		assertTrue(CapillaryPixelScale.maxBlueLengthPx(center) > CapillaryPixelScale.maxBlueLengthPx(edge));
		assertEquals(30, CapillaryPixelScale.maxBlueLengthPx(edge), 1e-9);
		assertEquals(33, NormedBlueKymoGeometry.kymoHeight(edge, 0.10));
		assertEquals(66, NormedBlueKymoGeometry.kymoHeight(center, 0.10));
	}

	@Test
	public void absoluteOffsetIsHalfExpansionOnBlueNormed() {
		Capillary cap = capillaryWithTwoBlues();
		double e = 0.10;
		int h = NormedBlueKymoGeometry.kymoHeight(cap, e);
		double offset = CapillaryPixelScale.absoluteRowOffset(cap, MeasurePixelSource.BLUE_NORMED_KYMO, e);
		assertEquals(h * e / (2.0 * (1.0 + e)), offset, 1e-12);
		assertEquals(0, CapillaryPixelScale.absoluteRowOffset(cap, MeasurePixelSource.GREEN_KYMO, e), 0);
	}

	private static Capillary capillaryWithTwoBlues() {
		Capillary cap = new Capillary();
		ROI2DLine roi = new ROI2DLine(new Line2D.Double(10, 0, 10, 80));
		roi.setName("line01");
		cap.setRoi(roi);
		cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 80), new Line2D.Double(10, 10, 10, 50));
		cap.getPhaseGeometry().putBlue(50, new Line2D.Double(10, 10, 10, 70));
		cap.setPixels(40);
		return cap;
	}
}
