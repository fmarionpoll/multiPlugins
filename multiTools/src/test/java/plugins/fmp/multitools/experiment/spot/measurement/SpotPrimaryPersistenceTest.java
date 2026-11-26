package plugins.fmp.multitools.experiment.spot.measurement;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;

public class SpotPrimaryPersistenceTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Spot spot(Spots spots, String name) {
        Spot spot = new Spot();
        spot.getProperties().setName(name);
        spots.addSpot(spot);
        return spot;
    }

    private SpotMethodResult result(SpotMeasurementMethod method, double value) {
        SpotMeasuredQuantity quantity = new SpotMeasuredQuantity("signal", "intensity", new double[] { value, Double.NaN, 0 },
                new int[] { 0, SpotSampleFlags.OCCLUDED, 0 }).withManualRejection(2, true);
        return new SpotMethodResult(method, new double[] { 0, 1.25, 9.123456789123 },
                new double[] { 0, 0.75, Double.NaN }, "fraction", Collections.singletonMap("source", "A<&é\"B"),
                Collections.singletonList(quantity));
    }

    @Test
    public void allMethodsRoundTripWithIndependentRawDataParametersAndManualDecisions() throws Exception {
        Spots original = new Spots();
        Spot spot = spot(original, "spot<&é\"A");
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values()) {
            spot.getMethodMeasurements().put(result(method, 123.456789012345 + method.ordinal()));
        }
        spot.getRoiNormalizedGreySignal().setValues(new double[] { 7, 8, 9 });
        assertTrue(original.getPersistence().saveMeasures(original, folder.getRoot().getAbsolutePath(), true));
        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadMeasures(loaded, folder.getRoot().getAbsolutePath()));
        Spot restored = loaded.findSpotByName(spot.getName());
        assertNotNull(restored);
        assertEquals(5, restored.getMethodMeasurements().snapshot().size());
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values()) {
            SpotMethodResult expected = spot.getMethodMeasurements().get(method), actual = restored.getMethodMeasurements().get(method);
            assertArrayEquals(expected.getTimesSeconds(), actual.getTimesSeconds(), 0);
            assertArrayEquals(expected.getFlyEvidence(), actual.getFlyEvidence(), 0);
            assertEquals(expected.getParameters(), actual.getParameters());
            assertArrayEquals(expected.getQuantity("signal").getRawValues(), actual.getQuantity("signal").getRawValues(), 0);
            assertArrayEquals(expected.getQuantity("signal").getEffectiveFlags(), actual.getQuantity("signal").getEffectiveFlags());
            assertArrayEquals(expected.getQuantity("signal").getManualRejections(), actual.getQuantity("signal").getManualRejections());
        }
        assertArrayEquals(new double[] { 7, 8, 9 }, restored.getRoiNormalizedGreySignal().getValues(), 0.001);
    }

    @Test
    public void descriptionsPreventOldCsvAndPrimaryMeasurementsFromResurrectingRemovedSpots() throws Exception {
        String directory = folder.getRoot().getAbsolutePath();
        Spots original = new Spots();
        Spot retained = spot(original, "spot_000_000");
        retained.getProperties().setCageID(0);
        retained.getRoiNormalizedGreySignal().setValues(new double[] { 12 });
        retained.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 12));
        Spot removed = spot(original, "spot_000_001");
        removed.getRoiNormalizedGreySignal().setValues(new double[] { 99 });
        removed.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 99));
        removed.getMethodMeasurements().put(result(SpotMeasurementMethod.X_LINES, 99));
        assertTrue(original.getPersistence().saveMeasures(original, directory, true));
        original.removeSpot(removed);
        assertTrue(original.getPersistence().saveDescriptions(original, directory));

        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadDescriptions(loaded, directory));
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        assertTrue(loaded.getPersistence().loadMeasuresMergeKymo(loaded, directory));
        assertEquals(1, loaded.getSpotListCount());
        assertNull(loaded.findSpotByName(removed.getName()));
        assertEquals(12, loaded.findSpotByName(retained.getName()).getMethodMeasurements()
                .get(SpotMeasurementMethod.FILTER).getQuantity("signal").getRawValues()[0], 0);

        // An explicitly empty description must also block all saved spot resurrection.
        original.clearSpotList();
        assertTrue(original.getPersistence().saveDescriptions(original, directory));
        loaded.clearSpotList();
        assertTrue(loaded.getPersistence().loadDescriptions(loaded, directory));
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        assertTrue(loaded.getPersistence().loadMeasuresMergeKymo(loaded, directory));
        assertTrue(loaded.isSpotListEmpty());
    }

    @Test
    public void partialCameraSaveRetainsOtherMethodsAlreadyOnDisk() throws Exception {
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.RIM_LINES, 4));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.COLOR, 3));
        assertTrue(spots.getPersistence().saveMeasures(spots, folder.getRoot().getAbsolutePath(), true));
        Spots cameraOnly = new Spots();
        spot(cameraOnly, "spotA").getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 9));
        assertTrue(cameraOnly.getPersistence().saveMeasures(cameraOnly, folder.getRoot().getAbsolutePath()));
        Map<SpotMeasurementMethod, SpotMethodResult> saved = SpotMeasurementXml.read(
                folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME)).get("spotA");
        assertEquals(3, saved.size());
        assertEquals(4, saved.get(SpotMeasurementMethod.RIM_LINES).getQuantity("signal").getRawValues()[0], 0);
        assertEquals(9, saved.get(SpotMeasurementMethod.FILTER).getQuantity("signal").getRawValues()[0], 0);
    }

    @Test
    public void kymographMergePreservesCameraDataAndLoadsPrimaryOnlyFiles() throws Exception {
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME);
        SpotMeasurementXml.write(path, Collections.singletonMap("spotA",
                Collections.singletonMap(SpotMeasurementMethod.X_LINES, result(SpotMeasurementMethod.X_LINES, 4))));
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        SpotMethodResult camera = result(SpotMeasurementMethod.FILTER, 8);
        spot.getMethodMeasurements().put(camera);
        assertTrue(spots.getPersistence().loadMeasuresMergeKymo(spots, folder.getRoot().getAbsolutePath()));
        assertSame(camera, spot.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        assertNotNull(spot.getMethodMeasurements().get(SpotMeasurementMethod.X_LINES));
    }

    @Test
    public void oldFilesDoNotInventPrimaryMeasurementsOrRetainPreviousExperimentRawData() throws Exception {
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        spot.getRoiNormalizedGreySignal().setValues(new double[] { 100 });
        assertTrue(spots.getPersistence().saveMeasures(spots, folder.getRoot().getAbsolutePath()));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME)));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 999));
        assertTrue(spots.getPersistence().loadMeasures(spots, folder.getRoot().getAbsolutePath()));
        assertTrue(spot.getMethodMeasurements().snapshot().isEmpty());
        assertArrayEquals(new double[] { 100 }, spot.getRoiNormalizedGreySignal().getValues(), 0.001);
    }

    @Test
    public void malformedSidecarCannotOverwriteRawFileOrReplaceInMemoryPrimaryData() throws Exception {
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        SpotMethodResult original = result(SpotMeasurementMethod.COLOR, 7);
        spot.getMethodMeasurements().put(original);
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME);
        byte[] corrupt = "<spot-measurements version=\"999\"/>".getBytes(StandardCharsets.UTF_8);
        Files.write(path, corrupt);
        assertFalse(spots.getPersistence().saveMeasures(spots, folder.getRoot().getAbsolutePath()));
        assertArrayEquals(corrupt, Files.readAllBytes(path));
        assertFalse(spots.getPersistence().loadMeasures(spots, folder.getRoot().getAbsolutePath()));
        assertSame(original, spot.getMethodMeasurements().get(SpotMeasurementMethod.COLOR));
    }

    @Test(expected = IOException.class)
    public void truncatedFileIsRejected() throws Exception {
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME);
        Files.write(path, "<spot-measurements version=\"1\"><spot name=\"A\">".getBytes(StandardCharsets.UTF_8));
        SpotMeasurementXml.read(path);
    }
}
