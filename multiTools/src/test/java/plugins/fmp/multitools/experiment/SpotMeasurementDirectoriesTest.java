package plugins.fmp.multitools.experiment;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance;
import plugins.fmp.multitools.experiment.spot.SpotDetectionProvenance.Family;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.tools.results.EnumResults;

public class SpotMeasurementDirectoriesTest {
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule memory =
            new plugins.fmp.multitools.test.InMemoryImageRule();
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test(timeout = 10000) public void imageSaveProvenanceAndDeletionIgnoreKymographBin() throws Exception {
        Path root = folder.getRoot().toPath();
        Path bin20 = Files.createDirectory(root.resolve("bin_20"));
        Path bin60 = Files.createDirectory(root.resolve("bin_60"));
        Experiment exp = new Experiment(root.toString());
        exp.setBinSubDirectory("bin_20");
        Spot spot = new Spot(); spot.getProperties().setName("A"); exp.getSpots().addSpot(spot);
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values())
            spot.getMethodMeasurements().put(new SpotMethodResult(method, new double[] { 0 }, new double[] { 0 }, "fraction",
                    Collections.emptyMap(), Collections.singletonList(new SpotMeasuredQuantity("signal", "pixels", new double[] { 10 }, new int[1]))));
        spot.getMeasurements(EnumResults.X_LINE_RATIO).setValues(new double[] { .8 });
        spot.getMeasurements(EnumResults.RIM_LINE_RATIO).setValues(new double[] { .9 });
        assertTrue(exp.save_image_spot_measures());
        assertFalse(Files.exists(root.resolve(SpotMeasurementMethod.VERTICAL_LINE.fileName())));
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values()) {
            assertFalse(Files.exists(bin20.resolve(method.fileName())));
            assertFalse(Files.exists(bin60.resolve(method.fileName())));
            if (!method.isKymographDerived()) {
                Map<SpotMeasurementMethod, SpotMethodResult> saved = SpotMeasurementCsv.read(root.resolve(method.fileName())).get("A");
                assertEquals(Collections.singleton(method), saved.keySet());
            }
        }
        SpotDetectionProvenance.recordCameraLine(exp, Family.KYMO_CROSS, "test-X", "threshold", 5);
        SpotDetectionProvenance.recordCameraLine(exp, Family.KYMO_RIM, "test-rim", "threshold", 6);
        Files.write(root.resolve("XLineBackground.csv"), new byte[] { 1 });
        exp.setBinSubDirectory("bin_60");
        assertEquals("test-X", SpotDetectionProvenance.load(exp, Family.KYMO_CROSS).getProperty("method"));
        assertEquals("test-rim", SpotDetectionProvenance.load(exp, Family.KYMO_RIM).getProperty("method"));
        assertEquals(root.toString(), exp.getSpotMeasurementsDirectory(SpotMeasurementMethod.X_LINES));
        assertEquals(root.toString(), exp.getSpotMeasurementsDirectory(SpotMeasurementMethod.RIM_LINES));
        assertEquals(bin60.toString(), exp.getSpotMeasurementsDirectory(SpotMeasurementMethod.VERTICAL_LINE));
        byte[] rim = Files.readAllBytes(root.resolve(SpotMeasurementMethod.RIM_LINES.fileName()));
        byte[] filter = Files.readAllBytes(root.resolve(SpotMeasurementMethod.FILTER.fileName()));
        assertTrue(exp.clearSpotMeasurements(EnumSet.of(SpotMeasurementMethod.X_LINES)));
        assertFalse(Files.exists(root.resolve("XLineBackground.csv")));
        assertFalse(Files.exists(root.resolve(SpotMeasurementMethod.X_LINES.fileName())));
        assertTrue(SpotDetectionProvenance.load(exp, Family.KYMO_CROSS).isEmpty());
        assertFalse(SpotDetectionProvenance.load(exp, Family.KYMO_RIM).isEmpty());
        assertArrayEquals(rim, Files.readAllBytes(root.resolve(SpotMeasurementMethod.RIM_LINES.fileName())));
        assertArrayEquals(filter, Files.readAllBytes(root.resolve(SpotMeasurementMethod.FILTER.fileName())));
    }
}
