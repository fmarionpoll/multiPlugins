package plugins.fmp.multitools.experiment.capillary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class ReachableEndTest {

	@Test
	public void flatBottomStaysUnset() {
		assertEquals(ReachableEnd.UNSET, ReachableEnd.find(constant(40, 100), constant(40, 20), 40));
	}

	@Test
	public void sustainedRiseTowardTopReturnsFirstFrameOfRun() {
		double[] bottom = constant(40, 100);
		Arrays.fill(bottom, 20, 40, 80);
		assertEquals(20, ReachableEnd.find(bottom, constant(40, 20), 40));
	}

	@Test
	public void singleFrameSpikeDoesNotEndTheSeries() {
		double[] bottom = constant(40, 100);
		bottom[20] = 80;
		assertEquals(ReachableEnd.UNSET, ReachableEnd.find(bottom, constant(40, 20), 40));
	}

	@Test
	public void applyIfUnsetKeepsAnExistingCutoff() {
		double[] bottom = constant(40, 100);
		Arrays.fill(bottom, 20, 40, 80);
		Capillary cap = capillary(bottom, constant(40, 20));
		assertTrue(ReachableEnd.applyIfUnset(cap));
		assertEquals(20, cap.getNValid());
		cap.setNValid(7);
		assertFalse(ReachableEnd.applyIfUnset(cap));
		assertEquals(7, cap.getNValid());
	}

	@Test
	public void descriptionCsvRoundTripsNValidAndLegacyRowsStayUnset() {
		BuildSeriesOptions options = new BuildSeriesOptions();
		List<String> row = new ArrayList<>(Arrays.asList("0L", "0", "line0L", "line0L.tif", "1", "1", "0.0", "0",
				"stim", "conc", "L", "line0L", "LINE", "2", "10", "20", "30", "40"));
		DetectionProvenanceSupport.appendCapillaryProvenanceColumns(row, options);
		row.add("NaN");
		row.add("NaN");
		row.add("NaN");
		row.add("false");
		row.add(DetectionProvenanceSupport.unifiedLevelRecipe(options));
		row.add("");
		row.add("");
		row.add("");
		row.add("");
		row.add("42");

		Capillary loaded = new Capillary();
		CapillaryPersistence.csvImportCapillaryDescription(loaded, row.toArray(new String[0]));
		assertEquals(42, loaded.getNValid());

		Capillary legacy = new Capillary();
		legacy.setNValid(7);
		CapillaryPersistence.csvImportCapillaryDescription(legacy,
				row.subList(0, row.size() - 1).toArray(new String[0]));
		assertEquals(ReachableEnd.UNSET, legacy.getNValid());

		String header = CapillaryPersistence.csvExportCapillarySubSectionHeader(";");
		assertTrue(header.contains("cap_measured_y2;n_valid;cap_length_x1"));

		Capillary written = new Capillary();
		written.setNValid(42);
		assertTrue(CapillaryPersistence.csvExportCapillaryDescription(written, ";").trim().endsWith(";42"));
		written.setNValid(ReachableEnd.UNSET);
		assertTrue(CapillaryPersistence.csvExportCapillaryDescription(written, ";").trim().endsWith(";"));
	}

	private static Capillary capillary(double[] bottom, double[] top) {
		Capillary cap = new Capillary();
		cap.getBottomRaw().polylineLevel = level(bottom);
		cap.getTopRaw().polylineLevel = level(top);
		return cap;
	}

	private static Level2D level(double[] y) {
		Level2D level = new Level2D(y.length);
		System.arraycopy(y, 0, level.ypoints, 0, y.length);
		return level;
	}

	private static double[] constant(int n, double y) {
		double[] values = new double[n];
		Arrays.fill(values, y);
		return values;
	}
}
