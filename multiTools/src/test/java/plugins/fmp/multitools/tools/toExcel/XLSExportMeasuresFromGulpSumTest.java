package plugins.fmp.multitools.tools.toExcel;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XLSExportMeasuresFromGulpSumTest {

	@Test
	public void consecutiveBinsPartitionTheEvents() {
		double[] t = { 0, 60000, 120000, 180000, 240000 };
		double[] v = { 0, 1.5, Double.NaN, 2, 0.5 };
		assertEquals(0, XLSExportMeasuresFromGulp.sumSince(t, v, -120000, 0), 1e-12);
		assertEquals(1.5, XLSExportMeasuresFromGulp.sumSince(t, v, 0, 120000), 1e-12);
		assertEquals(2.5, XLSExportMeasuresFromGulp.sumSince(t, v, 120000, 240000), 1e-12);
	}
}