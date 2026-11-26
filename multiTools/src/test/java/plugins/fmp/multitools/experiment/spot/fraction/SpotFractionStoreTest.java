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
    @Test public void baselineRecipeSurvivesReloadAndVersionOneKeepsItsInitialReferencePolicy() throws Exception {
        Path directory = folder.getRoot().toPath();
        SpotFractionPlan plan = new SpotFractionPlan();
        Settings old = Settings.DEFAULT;
        plan.settings.put(KEY, new Settings(old.similarGap, old.changingGap, old.tail, old.maximumGapSeconds,
                old.toleranceMode, old.tolerance, old.medianRadius, old.illumination,
                ReferenceEstimation.BASELINE_MEDIAN, 300, 900));
        SpotFractionStore.write(directory, plan);
        Settings restored = SpotFractionStore.read(directory).settingsFor(KEY);
        assertEquals(ReferenceEstimation.BASELINE_MEDIAN, restored.referenceEstimation);
        assertEquals(300, restored.baselineStartSeconds, 0);
        assertEquals(900, restored.baselineEndSeconds, 0);
        Path file = directory.resolve(SpotFractionStore.FILENAME);
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.loadFromXML(in); }
        p.setProperty("version", "1");
        p.remove("setting.0.referenceEstimation"); p.remove("setting.0.baselineStartSeconds"); p.remove("setting.0.baselineEndSeconds");
        try (OutputStream out = Files.newOutputStream(file)) { p.storeToXML(out, "Previous format"); }
        assertEquals(ReferenceEstimation.INITIAL_OBSERVATION, SpotFractionStore.read(directory).settingsFor(KEY).referenceEstimation);
    }
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
        SpotMeasurementCsv.write(directory.resolve(SpotMeasurementCsv.FILENAME), Collections.singletonMap("A", Collections.singletonMap(KEY.method, primary)));
        List<SpotFractionCalculator.Input> inputs = SpotFractionData.read(directory, directory, KEY.method);
        assertEquals(1, inputs.size());
        assertArrayEquals(new double[] {0, .5}, SpotFractionCalculator.calculate(inputs, KEY, new SpotFractionPlan()).get(0).consumed(), 0);
    }
    @Test public void integralLoadPrefersStandardCaptureAndFallsBackToOlderTrialData() throws Exception {
        Path directory = folder.getRoot().toPath();
        SpotMeasurementMethod method = SpotMeasurementMethod.VERTICAL_LINE;
        SpotMethodResult current = new SpotMethodResult(method, new double[] {0, 1}, new double[] {0, 0}, "fraction",
                Collections.emptyMap(), Collections.singletonList(new SpotMeasuredQuantity("color_integral", "metric_pixels",
                        new double[] {100, 50}, new int[2])));
        SpotMethodResult trial = new SpotMethodResult(method, new double[] {0, 1}, new double[] {0, 0}, "fraction",
                Collections.emptyMap(), Collections.singletonList(new SpotMeasuredQuantity("color_integral", "metric_pixels",
                        new double[] {200, 80}, new int[2])));
        Path standard = directory.resolve(SpotMeasurementCsv.FILENAME);
        SpotMeasurementCsv.write(standard, Collections.singletonMap("A", Collections.singletonMap(method, current)));
        SpotMeasurementCsv.write(directory.resolve("SpotsPrimaryMeasurementsTrial.csv"),
                Collections.singletonMap("A", Collections.singletonMap(method, trial)));
        byte[] saved = Files.readAllBytes(standard);
        assertEquals(100, SpotFractionData.read(directory, directory, method, "color_integral")
                .get(0).result.getQuantity("color_integral").getRawValues()[0], 0);
        assertArrayEquals(saved, Files.readAllBytes(standard));
        SpotMeasurementCsv.write(standard, Collections.emptyMap());
        assertEquals(200, SpotFractionData.read(directory, directory, method, "color_integral")
                .get(0).result.getQuantity("color_integral").getRawValues()[0], 0);
    }

    @Test public void imageFractionInputAlwaysComesFromResultsDespiteSelectedBin() throws Exception {
        Path root = folder.getRoot().toPath(), bin20 = folder.newFolder("bin_20").toPath(), bin60 = folder.newFolder("bin_60").toPath();
        for (SpotMeasurementMethod method : SpotMeasurementMethod.values()) {
            if (method.isKymographDerived()) continue;
            SpotMethodResult observed = new SpotMethodResult(method, new double[] { 0, 2 }, new double[] { 0, 0 },
                    "fraction", Collections.emptyMap(), Collections.singletonList(
                            new SpotMeasuredQuantity("signal", "intensity", new double[] { 100, 50 }, new int[2])));
            SpotMeasurementCsv.write(root.resolve(SpotMeasurementCsv.FILENAME), Collections.singletonMap("A", Collections.singletonMap(method, observed)));
            // A leftover file in a bin must neither override image inputs nor become a fallback.
            SpotMethodResult decoy = observed.withQuantity(new SpotMeasuredQuantity("signal", "intensity", new double[] { 900, 800 }, new int[2]));
            SpotMeasurementCsv.write(bin20.resolve(SpotMeasurementCsv.FILENAME), Collections.singletonMap("A", Collections.singletonMap(method, decoy)));
            for (Path bin : new Path[] { bin20, bin60 })
                assertEquals(100, SpotFractionData.read(root, bin, method).get(0).result.getQuantity("signal").getRawValues()[0], 0);
            Files.delete(root.resolve(SpotMeasurementCsv.FILENAME));
            assertTrue(SpotFractionData.read(root, bin20, method).isEmpty());
        }
    }
}
