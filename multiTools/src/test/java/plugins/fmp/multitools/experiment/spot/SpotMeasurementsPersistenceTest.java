package plugins.fmp.multitools.experiment.spot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.EnumResults.PersistenceDomain;

public class SpotMeasurementsPersistenceTest {

	@ClassRule
	public static final InMemoryImageRule imageMemory = new InMemoryImageRule();



	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void spotMeasurementsRoundTripThroughSharedResultsDirectory() {
		Experiment experiment = new Experiment();
		experiment.setResultsDirectory(folder.getRoot().getAbsolutePath());
		Spots spots = experiment.getSpots();
		Spot spot = new Spot();
		spot.getProperties().setName("spotA");
		spots.addSpot(spot);
		spot.getFlyCoveredPixelCount().setIsPresent(new int[] { 0, 1, 0 });

		for (EnumResults result : EnumResults.values()) {
			if (result.isPersistedIn(PersistenceDomain.SPOT) && result != EnumResults.FLY_PIXEL_COUNT) {
				spot.getMeasurements(result).setValues(new double[] { result.ordinal(), Double.NaN, 0.5 });
			}
		}
		assertTrue(experiment.save_spots_description_and_measures());
		assertTrue(experiment.save_kymo_spot_measures());

		Spots reloaded = new Spots();
		assertTrue(reloaded.getPersistence().loadMeasures(reloaded, folder.getRoot().getAbsolutePath()));
		Spot loaded = reloaded.findSpotByName("spotA");
		assertArrayEquals(spot.getFlyCoveredPixelCount().getIsPresent(), loaded.getFlyCoveredPixelCount().getIsPresent());
		for (EnumResults result : EnumResults.values()) {
			if (result.isPersistedIn(PersistenceDomain.SPOT) && result != EnumResults.FLY_PIXEL_COUNT) {
				assertArrayEquals(result.name(), spot.getMeasurements(result).getValues(),
						loaded.getMeasurements(result).getValues(), 0.001);
			}
		}
	}

	@Test
	public void loadsColorSectionWrittenWithFormerDisplayName() throws Exception {
		Files.write(folder.getRoot().toPath().resolve("SpotsMeasures.csv"), Arrays.asList(
				"#;version;2.0", "#;#", "#;GREY_SUM_COLOR_PREFLY;v0", "name;index;npts;yi", "spotA;0;2;0.5;42"),
				StandardCharsets.UTF_8);

		Spots spots = new Spots();
		assertTrue(spots.getPersistence().loadMeasures(spots, folder.getRoot().getAbsolutePath()));
		assertArrayEquals(new double[] { 0.5, 42 },
				spots.findSpotByName("spotA").getColorDistanceGreySignalBeforeOccupancyCheck().getValues(), 0.001);
	}
}
