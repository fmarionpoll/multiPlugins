package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Line2D;
import java.util.Collections;

import org.junit.BeforeClass;
import org.junit.Test;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.NormedBlueKymoGeometry;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class NormedBlueKymographBuilderTest {

	@BeforeClass
	public static void initializeIcy() {
		icy.preferences.IcyPreferences.init();
	}

	@Test
	public void twoBlueLengthsShareHeightAndTipRow() {
		Capillary cap = new Capillary();
		ROI2DLine roi = new ROI2DLine(new Line2D.Double(10, 0, 10, 80));
		roi.setName("line01");
		cap.setRoi(roi);
		cap.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 80), new Line2D.Double(10, 10, 10, 50));
		cap.getPhaseGeometry().putBlue(1, new Line2D.Double(10, 10, 10, 70));
		cap.setKymographBuild(true);

		BuildSeriesOptions options = new BuildSeriesOptions();
		options.diskRadius = 0;
		options.kymoBlueExpansionRatio = 0;

		int height = NormedBlueKymoGeometry.maxExpandedLengthPx(Collections.singletonList(cap), 0);
		assertEquals(60, height);

		IcyBufferedImage source = new IcyBufferedImage(20, 80, 3, DataType.UBYTE);
		byte[] red = (byte[]) source.getDataXY(0);
		for (int y = 0; y < 80; y++)
			red[y * 20 + 10] = (byte) y;
		source.setDataXY(0, red);

		NormedBlueKymographBuilder builder = new NormedBlueKymographBuilder();
		builder.allocateKymoBuffers(Collections.singletonList(cap), 2, height, 3);
		builder.analyzeImageUnderCapillary(source, cap, 0, 0, height, 20, 80, options);
		builder.analyzeImageUnderCapillary(source, cap, 1, 1, height, 20, 80, options);
		builder.commitBuffersToCapImages();

		IcyBufferedImage kymo = cap.getCap_Image();
		assertEquals(2, kymo.getWidth());
		assertEquals(height, kymo.getHeight());
		byte[] out = (byte[]) kymo.getDataXY(0);
		int row0col0 = out[0] & 0xFF;
		int row0col1 = out[1] & 0xFF;
		int lastCol0 = out[(height - 1) * 2] & 0xFF;
		int lastCol1 = out[(height - 1) * 2 + 1] & 0xFF;
		assertEquals(10, row0col0);
		assertEquals(10, row0col1);
		assertTrue("shorter blue must keep tip at row 0 and far end near y=50, was " + lastCol0, Math.abs(lastCol0 - 50) <= 1);
		assertTrue("longer blue far end near y=70, was " + lastCol1, Math.abs(lastCol1 - 70) <= 1);
	}

	@Test
	public void perCapillaryHeightsFollowEachMaxBlue() {
		Capillary center = new Capillary();
		ROI2DLine roiC = new ROI2DLine(new Line2D.Double(10, 0, 10, 80));
		roiC.setName("line01");
		center.setRoi(roiC);
		center.getPhaseGeometry().initialize(0, new Line2D.Double(10, 0, 10, 80), new Line2D.Double(10, 10, 10, 70));
		center.setKymographBuild(true);
		Capillary edge = new Capillary();
		ROI2DLine roiE = new ROI2DLine(new Line2D.Double(18, 0, 18, 50));
		roiE.setName("line02");
		edge.setRoi(roiE);
		edge.getPhaseGeometry().initialize(0, new Line2D.Double(18, 0, 18, 50), new Line2D.Double(18, 5, 18, 35));
		edge.setKymographBuild(true);
		NormedBlueKymographBuilder builder = new NormedBlueKymographBuilder();
		java.util.List<Capillary> caps = new java.util.ArrayList<Capillary>();
		caps.add(center);
		caps.add(edge);
		builder.allocateKymoBuffers(caps, 1, 3, 0.10);
		assertEquals(66, center.getCap_Image().getHeight());
		assertEquals(33, edge.getCap_Image().getHeight());
	}
}
