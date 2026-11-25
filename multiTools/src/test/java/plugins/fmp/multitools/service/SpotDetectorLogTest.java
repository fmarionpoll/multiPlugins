package plugins.fmp.multitools.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.MeasurementProgress;

public class SpotDetectorLogTest {
	@org.junit.BeforeClass
	public static void initializeIcyPreferences() {
		icy.preferences.IcyPreferences.init();
	}

	@Test
	public void maskFailureNamesTheListIndexAndImageDirectory() {
		Experiment exp = new Experiment();
		exp.setImagesDirectory("C:/data/exp042");
		String detail = "Processing step 'buildMask2D' failed: Input ROI is not set";
		try (MeasurementProgress.Scope scope = MeasurementProgress.install((phase, unit, completed, total) -> {
		}, new MeasurementProgress.ListPosition(17, 462))) {
			String message = SpotDetectorLog.maskFailure("SpotLevelDetectorFromCam", exp, "spot_005_033", detail);
			assertTrue(message.contains("spot_005_033"));
			assertTrue(message.contains("experiment 17/462"));
			assertTrue(message.contains("directory exp042 (C:/data/exp042)"));
			assertTrue(message.endsWith(detail));
		}
		assertNull(MeasurementProgress.listPosition());
		String withoutIndex = SpotDetectorLog.maskFailure("SpotLevelDetectorFromCam", exp, "spot_005_033", detail);
		assertFalse(withoutIndex.contains("experiment 17"));
		assertTrue(withoutIndex.contains("directory exp042 (C:/data/exp042)"));
	}

	@Test
	public void resultsDirectorySuppliesTheExperimentFolderWhenImagesAreMissing() {
		Experiment exp = new Experiment();
		exp.setResultsDirectory("C:/data/exp042/results");
		String stored = exp.getResultsDirectory();
		String parent = new File(stored).getParentFile().getPath();
		assertEquals(new File(parent).getName() + " (" + parent + ")", SpotDetectorLog.directoryLabel(exp));
	}

	@Test
	public void missingDirectoriesStayExplicit() {
		assertEquals("unknown", SpotDetectorLog.directoryLabel(null));
		assertTrue(SpotDetectorLog.maskFailure("SpotLevelDetectorFromCam", new Experiment(), "spot_005_033", "detail")
				.contains("directory unknown"));
	}
}
