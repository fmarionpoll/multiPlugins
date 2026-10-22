package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import plugins.fmp.multitools.series.options.BuildSeriesOptions.GulpDetectionMethod;

public class CapillaryCorrelationGulpsTest {

	@Test
	public void csvRowRoundTripKeepsEventsAndSettings() {
		CapillaryCorrelationGulps source = new CapillaryCorrelationGulps();
		source.set(new double[] { 0, 2.5, 0, 0, 1.25 }, 12, 15, 0.75, true);
		source.setMethodOptions("v1,adaptive=true,persistent=true,local=false,scale=1.0");
		double[] positions = {40, 42.5, 42.5, 43, 44.25};
		source.setBottomPositions(positions);
		positions[1] = 999; // stored geometry must be independent of caller edits

		StringBuffer sbf = new StringBuffer("0L;3;");
		source.csvExportToRow(sbf, ";");
		CapillaryCorrelationGulps loaded = new CapillaryCorrelationGulps();
		loaded.csvImportFromRow(sbf.toString().split(";"), 2, true);

		assertEquals(12, loaded.getWindowRadius());
		assertEquals(15, loaded.getMaxShiftPx(), 1e-9);
		assertEquals(0.75, loaded.getThresholdPxPerMin(), 1e-9);
		assertTrue(loaded.isManualThreshold());
		assertEquals(source.getMethodOptions(), loaded.getMethodOptions());
		assertEquals(42.5, loaded.bottomAt(1, 999), 1e-9);
		CapillaryCorrelationGulps copied = new CapillaryCorrelationGulps();
		copied.copy(loaded);
		assertEquals(loaded.getMethodOptions(), copied.getMethodOptions());
		assertEquals(44.25, copied.bottomAt(4, 999), 1e-9);
		copied.clear();
		assertEquals(999, copied.bottomAt(4, 999), 0);
		assertEquals(2, loaded.countEvents());
		assertEquals(5, loaded.getEvents().getHeightSeries().npoints);
		assertEquals(2.5, loaded.getEvents().getHeightSeries().ypoints[1], 1e-9);
		assertEquals(1.25, loaded.getEvents().getHeightSeries().ypoints[4], 1e-9);
	}

	@Test
	public void rowWithoutMaxShiftUsesDefault() {
		CapillaryCorrelationGulps loaded = new CapillaryCorrelationGulps();
		loaded.csvImportFromRow("0L;3;10;0.5;false;3;0;2.0;0;".split(";"), 2, false);

		assertEquals(10, loaded.getWindowRadius());
		assertEquals(CapillaryCorrelationGulps.DEFAULT_MAX_SHIFT_PX, loaded.getMaxShiftPx(), 1e-9);
		assertEquals(0.5, loaded.getThresholdPxPerMin(), 1e-9);
		assertEquals(1, loaded.countEvents());
	}

	@Test
	public void copyToGulpsReplacesGenuineGulpsAndRecordsMethod() {
		Capillary cap = new Capillary();
		assertFalse(cap.copyCorrelationGulpsToGulps());

		cap.getCorrelationGulps().set(new double[] { 0, 3, 0 }, 10, 6, 1.0, false);
		cap.getCorrelationGulps().setBottomPositions(new double[] {40, 43.25, 43.25});
		assertTrue(cap.copyCorrelationGulpsToGulps());

		assertEquals(3.0, cap.getGulps().getHeightSeries().ypoints[1], 1e-9);
		assertEquals(GulpDetectionMethod.VERTICAL_CORRELATION, cap.getGulpsOptions().gulpDetectionMethod);
		cap.getCorrelationGulps().clear();
		assertEquals(3.0, cap.getGulps().getHeightSeries().ypoints[1], 1e-9);
		assertEquals(43.25, cap.getGulps().bottomAt(1, 999), 1e-9);
	}
}
