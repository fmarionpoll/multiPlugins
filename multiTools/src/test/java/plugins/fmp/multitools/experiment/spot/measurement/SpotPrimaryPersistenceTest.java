package plugins.fmp.multitools.experiment.spot.measurement;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.tools.results.EnumResults;

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

    @Test
    public void clearingFilterRemovesOnlyThatMethodFromCsvAndPrimaryFile() throws Exception {
        String directory = folder.getRoot().getAbsolutePath();
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        spot.getMeasurements(EnumResults.FILTER_GREY).setValues(new double[] { 12 });
        spot.getMeasurements(EnumResults.FLY_PIXEL_COUNT).setIsPresent(new int[] { 1 });
        spot.getMeasurements(EnumResults.COLOR_GREY).setValues(new double[] { 3 });
        spot.getMeasurements(EnumResults.X_LINE_RATIO).setValues(new double[] { 0.5 });
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 12));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.COLOR, 3));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.X_LINES, 5));
        assertTrue(spots.getPersistence().saveMeasures(spots, directory, true));
        Files.write(folder.getRoot().toPath().resolve("SpotDetection-image_filter.properties"),
                "method=filter".getBytes(StandardCharsets.UTF_8));
        Files.write(folder.getRoot().toPath().resolve("SpotDetection-color_distance.properties"),
                "method=color".getBytes(StandardCharsets.UTF_8));

        assertTrue(spots.getPersistence().deleteStoredMeasures(directory, EnumSet.of(SpotMeasurementMethod.FILTER)));
        spots.getPersistence().clearMeasuresInMemory(spots, EnumSet.of(SpotMeasurementMethod.FILTER));

        assertNull(spot.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        assertNotNull(spot.getMethodMeasurements().get(SpotMeasurementMethod.COLOR));
        assertEquals(0, spot.getMeasurements(EnumResults.FILTER_GREY).getCount());
        assertEquals(0, spot.getMeasurements(EnumResults.FLY_PIXEL_COUNT).getCount());
        assertEquals(1, spot.getMeasurements(EnumResults.COLOR_GREY).getCount());
        String csv = new String(Files.readAllBytes(folder.getRoot().toPath().resolve("SpotsMeasures.csv")), StandardCharsets.UTF_8);
        assertFalse(csv.contains("AREA_SUM"));
        assertFalse(csv.contains("AREA_FLYPRESENT"));
        assertTrue(csv.contains("GREY_SUM_V6"));
        assertTrue(csv.contains("KYMO_LINE_RATIO"));
        String xml = new String(Files.readAllBytes(folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME)), StandardCharsets.UTF_8);
        assertFalse(xml.contains("id=\"FILTER\""));
        assertTrue(xml.contains("id=\"COLOR\""));
        assertTrue(xml.contains("id=\"X_LINES\""));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("SpotDetection-image_filter.properties")));
        assertTrue(Files.exists(folder.getRoot().toPath().resolve("SpotDetection-color_distance.properties")));

        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        Spot restored = loaded.findSpotByName("spotA");
        assertNotNull(restored);
        assertNull(restored.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
        assertEquals(3, restored.getMethodMeasurements().get(SpotMeasurementMethod.COLOR).getQuantity("signal").getRawValues()[0], 0);
        assertEquals(5, restored.getMethodMeasurements().get(SpotMeasurementMethod.X_LINES).getQuantity("signal").getRawValues()[0], 0);
        assertEquals(0, restored.getMeasurements(EnumResults.FILTER_GREY).getCount());
        assertArrayEquals(new double[] { 3 }, restored.getMeasurements(EnumResults.COLOR_GREY).getValues(), 0.001);
        assertArrayEquals(new double[] { 0.5 }, restored.getMeasurements(EnumResults.X_LINE_RATIO).getValues(), 0.001);
    }

    @Test
    public void clearingXLineKeepsVerticalRimAndCameraData() throws Exception {
        String directory = folder.getRoot().getAbsolutePath();
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        spot.getMeasurements(EnumResults.FILTER_GREY).setValues(new double[] { 8 });
        spot.getMeasurements(EnumResults.KYMO_ROW_FRACTION).setValues(new double[] { 0.2 });
        spot.getMeasurements(EnumResults.X_LINE_FRACTION).setValues(new double[] { 0.4 });
        spot.getMeasurements(EnumResults.X_LINE_RATIO).setValues(new double[] { 0.6 });
        spot.getMeasurements(EnumResults.RIM_LINE_RATIO).setValues(new double[] { 0.7 });
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 8));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.VERTICAL_LINE, 2));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.X_LINES, 4));
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.RIM_LINES, 7));
        assertTrue(spots.getPersistence().saveMeasures(spots, directory, true));
        Files.write(folder.getRoot().toPath().resolve("XLineBackground.csv"), "x".getBytes(StandardCharsets.UTF_8));

        assertTrue(spots.getPersistence().deleteStoredMeasures(directory, EnumSet.of(SpotMeasurementMethod.X_LINES)));

        String csv = new String(Files.readAllBytes(folder.getRoot().toPath().resolve("SpotsMeasures.csv")), StandardCharsets.UTF_8);
        assertFalse(csv.contains("X_LINE_FRACTION"));
        assertFalse(csv.contains("KYMO_LINE_RATIO"));
        assertTrue(csv.contains("KYMO_CHROMA_FRACT"));
        assertTrue(csv.contains("KYMO_RIM_RATIO"));
        assertTrue(csv.contains("AREA_SUM"));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("XLineBackground.csv")));
        Map<SpotMeasurementMethod, SpotMethodResult> saved = SpotMeasurementXml.read(
                folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME)).get("spotA");
        assertNull(saved.get(SpotMeasurementMethod.X_LINES));
        assertEquals(2, saved.get(SpotMeasurementMethod.VERTICAL_LINE).getQuantity("signal").getRawValues()[0], 0);
        assertEquals(7, saved.get(SpotMeasurementMethod.RIM_LINES).getQuantity("signal").getRawValues()[0], 0);
        assertEquals(8, saved.get(SpotMeasurementMethod.FILTER).getQuantity("signal").getRawValues()[0], 0);

        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        Spot restored = loaded.findSpotByName("spotA");
        assertNull(restored.getMethodMeasurements().get(SpotMeasurementMethod.X_LINES));
        assertNotNull(restored.getMethodMeasurements().get(SpotMeasurementMethod.VERTICAL_LINE));
        assertNotNull(restored.getMethodMeasurements().get(SpotMeasurementMethod.RIM_LINES));
        assertEquals(0, restored.getMeasurements(EnumResults.X_LINE_RATIO).getCount());
        assertArrayEquals(new double[] { 0.2 }, restored.getMeasurements(EnumResults.KYMO_ROW_FRACTION).getValues(), 0.001);
        assertArrayEquals(new double[] { 0.7 }, restored.getMeasurements(EnumResults.RIM_LINE_RATIO).getValues(), 0.001);
        assertArrayEquals(new double[] { 8 }, restored.getMeasurements(EnumResults.FILTER_GREY).getValues(), 0.001);
    }

    @Test
    public void clearingTheLastMethodRemovesTheSidecarAndDoesNotResurrectIt() throws Exception {
        String directory = folder.getRoot().getAbsolutePath();
        Spots spots = new Spots();
        Spot spot = spot(spots, "spotA");
        spot.getMeasurements(EnumResults.FILTER_GREY).setValues(new double[] { 11 });
        spot.getMethodMeasurements().put(result(SpotMeasurementMethod.FILTER, 11));
        assertTrue(spots.getPersistence().saveMeasures(spots, directory, true));

        assertTrue(spots.getPersistence().deleteStoredMeasures(directory, EnumSet.of(SpotMeasurementMethod.FILTER)));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME)));
        String csv = new String(Files.readAllBytes(folder.getRoot().toPath().resolve("SpotsMeasures.csv")), StandardCharsets.UTF_8);
        assertTrue(csv.contains("version"));
        assertFalse(csv.contains("AREA_SUM"));

        Spots loaded = new Spots();
        assertTrue(loaded.getPersistence().loadMeasures(loaded, directory));
        Spot restored = loaded.findSpotByName("spotA");
        if (restored != null) {
            assertNull(restored.getMethodMeasurements().get(SpotMeasurementMethod.FILTER));
            assertEquals(0, restored.getMeasurements(EnumResults.FILTER_GREY).getCount());
        }
    }

    @Test
    public void clearingKymographImagesLeavesMeasureFiles() throws Exception {
        Path bin = folder.getRoot().toPath();
        Files.write(bin.resolve("kymocage_1.tiff"), new byte[] { 1 });
        Files.write(bin.resolve("CageKymographStripLayout.csv"), "layout".getBytes(StandardCharsets.UTF_8));
        Files.write(bin.resolve("CageKymographColumnTimes.csv"), "times".getBytes(StandardCharsets.UTF_8));
        Files.write(bin.resolve("SpotsMeasures.csv"), "keep".getBytes(StandardCharsets.UTF_8));
        assertTrue(new Spots().getPersistence().deleteCageKymographImages(bin.toString()));
        assertFalse(Files.exists(bin.resolve("kymocage_1.tiff")));
        assertFalse(Files.exists(bin.resolve("CageKymographStripLayout.csv")));
        assertFalse(Files.exists(bin.resolve("CageKymographColumnTimes.csv")));
        assertTrue(Files.exists(bin.resolve("SpotsMeasures.csv")));
    }

    @Test(expected = IOException.class)
    public void truncatedFileIsRejected() throws Exception {
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementXml.FILENAME);
        Files.write(path, "<spot-measurements version=\"1\"><spot name=\"A\">".getBytes(StandardCharsets.UTF_8));
        SpotMeasurementXml.read(path);
    }
}
