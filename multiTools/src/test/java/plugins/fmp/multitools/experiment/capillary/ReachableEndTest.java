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
	public void normalizedReviewUsesTheSameFractionAcrossResolutionsAndOrigins() {
		for (int scale : new int[] { 1, 2 }) {
			double[] bottom = constant(40, 437 * scale + 100);
			Arrays.fill(bottom, 20, 40, 427 * scale + 100);
			Capillary cap = capillary(bottom, constant(40, 100));
			cap.setPixels(430 * scale);
			assertEquals(20, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
			assertEquals(10.0 / 430.0, ReachableEnd.riseFraction(cap, null, 20), 1e-12);
			assertEquals(0, ReachableEnd.riseFraction(cap, null, 0), 1e-12);
		}
	}

	@Test
	public void normalizedReviewRejectsSmallerRisesAndMissingCalibration() {
		double[] bottom = constant(40, 437);
		Arrays.fill(bottom, 20, 40, 428);
		Capillary cap = capillary(bottom, constant(40, 20));
		cap.setPixels(430);
		assertEquals(ReachableEnd.UNSET, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
		cap.setPixels(0);
		assertEquals(ReachableEnd.UNSET, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
		assertTrue(Double.isNaN(ReachableEnd.riseFraction(cap, null, 20)));
	}

	@Test
	public void normalizedReviewStillRequiresSustainedDepartureAndHonorsDecisions() {
		double[] bottom = constant(60, 437);
		Arrays.fill(bottom, 15, 30, 427);
		Capillary cap = capillary(bottom, constant(60, 20));
		cap.setPixels(430);
		assertEquals(ReachableEnd.UNSET, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
		Arrays.fill(bottom, 40, 60, 427);
		cap.getBottomRaw().polylineLevel = level(bottom);
		assertEquals(40, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
		cap.setDriftDismissed(true);
		assertEquals(ReachableEnd.UNSET, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
		cap.setDriftDismissed(false);
		assertEquals(40, ReachableEnd.find(cap, null, 10.0 / 430.0));
		cap.setNValid(40);
		assertEquals(40, ReachableEnd.find(cap, null, 10.0 / 430.0));
		assertEquals(40, cap.getNValid());
		assertEquals(ReachableEnd.UNSET, ReachableEnd.findReviewFrame(cap, null, 10.0 / 430.0));
	}

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
	public void temporaryRiseFollowedByRecoveryIsNotACutoff() {
		double[] bottom = constant(60, 100);
		Arrays.fill(bottom, 15, 30, 80);
		assertEquals(ReachableEnd.UNSET, ReachableEnd.find(bottom, constant(60, 20), 60));
	}

	@Test
	public void temporaryRiseDoesNotMoveCutoffBeforeFinalRise() {
		double[] bottom = constant(70, 100);
		Arrays.fill(bottom, 15, 30, 80);
		Arrays.fill(bottom, 40, 70, 80);
		bottom[50] = Double.NaN;
		bottom[55] = 100;
		assertEquals(40, ReachableEnd.find(bottom, constant(70, 20), 70));
	}

	@Test
	public void finalRiseCanBeDetectedWithOnlyAnEarlyTopTrace() {
		double[] bottom = constant(120, 100);
		Arrays.fill(bottom, 14, 120, 80);
		assertEquals(14, ReachableEnd.find(bottom, constant(12, 20), 120));
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
		assertTrue(header.contains("cap_measured_y2;n_valid;drift_dismissed;cap_length_x1"));
		assertFalse(loaded.isDriftDismissed());

		row.add("true");
		Capillary dismissed = new Capillary();
		CapillaryPersistence.csvImportCapillaryDescription(dismissed, row.toArray(new String[0]));
		assertEquals(42, dismissed.getNValid());
		assertTrue(dismissed.isDriftDismissed());

		Capillary missingFlag = new Capillary();
		missingFlag.setDriftDismissed(true);
		CapillaryPersistence.csvImportCapillaryDescription(missingFlag,
				row.subList(0, row.size() - 1).toArray(new String[0]));
		assertFalse(missingFlag.isDriftDismissed());

		Capillary written = new Capillary();
		written.setNValid(42);
		written.setDriftDismissed(true);
		assertTrue(CapillaryPersistence.csvExportCapillaryDescription(written, ";").trim().endsWith(";42;true"));
		written.setNValid(ReachableEnd.UNSET);
		written.setDriftDismissed(false);
		assertTrue(CapillaryPersistence.csvExportCapillaryDescription(written, ";").trim().endsWith(";false"));
	}

	@Test
	public void validatedOrDismissedCapillaryIsNotACandidate() {
		double[] bottom = constant(40, 100);
		Arrays.fill(bottom, 20, 40, 80);
		Capillary cap = capillary(bottom, constant(40, 20));
		assertTrue(ReachableEnd.isReviewCandidate(cap));
		cap.setNValid(20);
		assertFalse(ReachableEnd.isReviewCandidate(cap));
		cap.setNValid(ReachableEnd.UNSET);
		cap.setDriftDismissed(true);
		assertFalse(ReachableEnd.isReviewCandidate(cap));
		assertFalse(ReachableEnd.applyIfUnset(cap));
		assertEquals(ReachableEnd.UNSET, cap.getNValid());
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
