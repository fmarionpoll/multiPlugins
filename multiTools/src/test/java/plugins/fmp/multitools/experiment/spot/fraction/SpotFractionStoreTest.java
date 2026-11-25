package plugins.fmp.multitools.experiment.spot.fraction;

import static org.junit.Assert.*;
import static plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculatorTest.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.measurement.*;

public class SpotFractionStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void choicesReferencesAndReviewQueueSurviveReloadWithoutChangingOtherExperiments() throws Exception {
        Path first = folder.newFolder("first").toPath(), second = folder.newFolder("second").toPath();
        SpotFractionPlan plan = new SpotFractionPlan();
        SpotFractionCalculator.calculate(inputs(spot("A<&é", 1, false, new double[] {999, 80, 40}, 1, 0, 0)), KEY, plan);
        plan.settings.put(KEY, Settings.DEFAULT); SpotFractionStore.write(first, plan);
        SpotFractionPlan loaded = SpotFractionStore.read(first);
        assertEquals(8, loaded.settingsFor(KEY).tolerance, 0); assertEquals(600, loaded.settingsFor(KEY).maximumGapSeconds, 0);
        assertFalse(loaded.references.get(KEY.forSpot("A<&é")).approved); assertEquals(1, loaded.review.size());
        assertEquals(plan.references.get(KEY.forSpot("A<&é")).fingerprint, loaded.references.get(KEY.forSpot("A<&é")).fingerprint);
        assertTrue(SpotFractionStore.read(second).references.isEmpty()); assertFalse(Files.exists(second.resolve(SpotFractionStore.FILENAME)));
        loaded.references.clear(); SpotFractionStore.write(first, loaded); assertTrue(SpotFractionStore.read(first).references.isEmpty());
    }
    @Test public void newerOrMalformedFilesAreNotOverwritten() throws Exception {
        Path directory = folder.getRoot().toPath(), file = directory.resolve(SpotFractionStore.FILENAME);
        Properties p = new Properties(); p.setProperty("version", "999");
        try (OutputStream out = Files.newOutputStream(file)) { p.storeToXML(out, "Future file"); }
        byte[] original = Files.readAllBytes(file);
        try { SpotFractionStore.write(directory, new SpotFractionPlan()); fail("Must reject future versions"); } catch (IOException expected) { }
        assertArrayEquals(original, Files.readAllBytes(file));
    }
    @Test public void primaryOnlyBatchLoadDoesNotRequireImages() throws Exception {
        Path directory = folder.getRoot().toPath();
        SpotMethodResult primary = spot("A", 1, false, new double[] {100, 50}).getMethodMeasurements().get(KEY.method);
        SpotMeasurementXml.write(directory.resolve(SpotMeasurementXml.FILENAME), Collections.singletonMap("A", Collections.singletonMap(KEY.method, primary)));
        List<SpotFractionCalculator.Input> inputs = SpotFractionData.read(directory, directory, KEY.method);
        assertEquals(1, inputs.size());
        assertArrayEquals(new double[] {0, .5}, SpotFractionCalculator.calculate(inputs, KEY, new SpotFractionPlan()).get(0).consumed(), 0);
    }
}
