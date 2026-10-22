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

    @Test public void fittedPositionsSurviveFlatCsvAndInvalidateOnAmplitudeEdit() {
        CapillaryGulps g = gulps(0, 2.25, 0, 1.5);
        g.setBottomPositions(new double[]{40, 42.25, 42.5, 44});
        StringBuffer row=new StringBuffer(); g.csvExportDataFlatToRow(row,";");
        CapillaryGulps loaded=new CapillaryGulps(); loaded.csvImportDataFromRow(row.toString().split(";"),0);
        assertEquals(44,loaded.bottomAt(3,999),1e-9);
        assertEquals(1.5,loaded.getHeightSeries().ypoints[3],1e-9);
        loaded.setAmplitudeAt(3,2);
        assertEquals(999,loaded.bottomAt(3,999),0);
    }

    @Test public void fittedCsvDoesNotRoundAmplitudesIntoOverlaps() {
        double a=2.2983376070307457, b=1.96020494631955;
        CapillaryGulps g=gulps(0,a,b);
        double[] bottom={40,40+a,40+a+b};
        g.setBottomPositions(bottom);
        StringBuffer row=new StringBuffer(); g.csvExportDataFlatToRow(row,";");
        CapillaryGulps loaded=new CapillaryGulps(); loaded.csvImportDataFromRow(row.toString().split(";"),0);
        assertEquals(a,loaded.getHeightSeries().ypoints[1],0);
        assertEquals(b,loaded.getHeightSeries().ypoints[2],0);
        assertTrue(loaded.bottomAt(2,0)-loaded.getHeightSeries().ypoints[2]+1e-12>=loaded.bottomAt(1,0));
    }
}
