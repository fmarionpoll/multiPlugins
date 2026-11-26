package plugins.fmp.multitools.experiment;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasuredQuantity;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;

public class GeometryMeasurementResetTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @BeforeClass public static void preferences() { icy.preferences.IcyPreferences.init(); }

    private Path file(Path dir, String name) throws Exception {
        Files.createDirectories(dir);
        return Files.write(dir.resolve(name), name.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void archivesAllBinsWithoutTouchingImagesOrPreviousBackups() throws Exception {
        Path root = folder.newFolder("results").toPath();
        Path bin = root.resolve("bin_60"), other = root.resolve("bin_20");
        file(root, "SpotsPrimaryMeasurements.xml");
        file(bin, "SpotsPrimaryMeasurementsTrial.xml");
        file(other, "SpotsArrayMeasures.csv");
        file(bin, "kymocage_1.tiff");
        file(bin, "spot_000_001.tiff");
        file(bin, "CageKymographStripLayout.csv");
        file(other, "spot_001_002.tif");
        Path positions = file(bin, "CagesMeasures.csv");
        Path image = file(root, "Frame.jpg");
        Path description = file(root, "SpotsDescription.csv");
        Path previous = file(root.resolve("geometry-backups/previous"), "SpotsPrimaryMeasurements.xml");
        Path backup = GeometryMeasurementReset.archive(root, bin, false);
        assertFalse(Files.exists(root.resolve("SpotsPrimaryMeasurements.xml")));
        assertFalse(Files.exists(bin.resolve("SpotsPrimaryMeasurementsTrial.xml")));
        assertFalse(Files.exists(other.resolve("SpotsArrayMeasures.csv")));
        assertFalse(Files.exists(bin.resolve("kymocage_1.tiff")));
        assertFalse(Files.exists(bin.resolve("spot_000_001.tiff")));
        assertFalse(Files.exists(bin.resolve("CageKymographStripLayout.csv")));
        assertFalse(Files.exists(other.resolve("spot_001_002.tif")));
        assertTrue(Files.exists(positions));
        assertTrue(Files.exists(image));
        assertTrue(Files.exists(description));
        assertTrue(Files.exists(previous));
        assertEquals("SpotsPrimaryMeasurements.xml", new String(Files.readAllBytes(
                backup.resolve("results/SpotsPrimaryMeasurements.xml")), StandardCharsets.UTF_8));
        GeometryMeasurementReset.archive(root, bin, true);
        assertFalse(Files.exists(positions));
        assertTrue(Files.exists(backup.resolve("results/SpotsPrimaryMeasurements.xml")));
    }

    @Test(timeout = 20000) public void recreatedGeometryCannotReloadOrResaveOldMeasurements() throws Exception {
        Path root = folder.newFolder("experiment").toPath();
        Experiment exp = new Experiment();
        exp.setResultsDirectory(root.toString());
        Spot spot = new Spot();
        spot.getProperties().setName("spot_000_000");
        SpotMeasuredQuantity q = new SpotMeasuredQuantity("signal", "intensity",
                new double[] { 42 }, new int[] { 0 });
        spot.getMethodMeasurements().put(new SpotMethodResult(SpotMeasurementMethod.FILTER,
                new double[] { 0 }, new double[] { 0 }, "fraction",
                Collections.emptyMap(), Collections.singletonList(q)));
        exp.getSpots().addSpot(spot);
        Cage cage = new Cage();
        exp.getCages().getCageList().add(cage);
        assertTrue(exp.getSpots().getPersistence().saveDescriptions(exp.getSpots(), root.toString()));
        assertTrue(exp.getSpots().getPersistence().saveMeasures(exp.getSpots(), root.toString()));
        assertTrue(GeometryMeasurementReset.beforeRecreation(exp, true));
        assertTrue(exp.getSpots().getSpotList().isEmpty());
        assertTrue(cage.getSpotIDs().isEmpty());
        assertTrue(exp.getSpots().getPersistence().saveMeasures(exp.getSpots(), root.toString()));
        assertFalse(Files.exists(root.resolve("SpotsPrimaryMeasurements.xml")));
        exp.load_spots_description_and_measures();
        assertTrue(exp.getSpots().getSpotList().isEmpty());
        Spot replacement = new Spot();
        replacement.getProperties().setName(spot.getName());
        exp.getSpots().addSpot(replacement);
        exp.getSpots().getPersistence().loadMeasures(exp.getSpots(), root.toString());
        assertTrue(replacement.getMethodMeasurements().snapshot().isEmpty());
    }

    @Test(timeout = 20000) public void failedBackupLeavesGeometryAndMeasurementsIntact() throws Exception {
        Path root = folder.newFolder("blocked").toPath();
        file(root, "geometry-backups"); // A file prevents creating the backup directory.
        Path measurements = file(root, "SpotsPrimaryMeasurements.xml");
        Experiment exp = new Experiment();
        exp.setResultsDirectory(root.toString());
        Spot original = new Spot();
        exp.getSpots().addSpot(original);
        assertFalse(GeometryMeasurementReset.beforeRecreation(exp, true));
        assertSame(original, exp.getSpots().getSpotList().get(0));
        assertTrue(Files.exists(measurements));
    }
}
