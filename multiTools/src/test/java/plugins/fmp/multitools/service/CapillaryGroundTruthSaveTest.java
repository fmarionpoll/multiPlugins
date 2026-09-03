package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CapillaryGroundTruthSaveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test public void exportsAdjustedEndpointsWithoutChangingNormalFiles() throws Exception {
        Capillaries caps = new Capillaries();
        Capillary cap = new Capillary();
        cap.setKymographName("line01");
        cap.setRoi(new ROI2DLine(new Line2D.Double(10, 0, 10, 130)));
        cap.getProperties().setMeasuredEndpoints(new Point2D.Double(10, 10), new Point2D.Double(10, 120));
        caps.addCapillary(cap);
        Path directory = temporary.newFolder().toPath();
        assertTrue(caps.getPersistence().saveDescriptions(caps, directory.toString()));
        Map<Path, byte[]> original = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            for (Path path : (Iterable<Path>) files::iterator)
                original.put(path, Files.readAllBytes(path));
        }
        cap.getProperties().setMeasuredEndpoints(new Point2D.Double(10, 5.5), new Point2D.Double(10, 125.5));
        assertTrue(caps.getPersistence().saveGroundTruthDescriptions(caps, directory.toString()));
        for (Map.Entry<Path, byte[]> entry : original.entrySet())
            assertArrayEquals(entry.getKey().toString(), entry.getValue(), Files.readAllBytes(entry.getKey()));
        Path truth = directory.resolve("CapillariesDescription-groundtruth.csv");
        String content = new String(Files.readAllBytes(truth), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("cap_length_x1;cap_length_y1;cap_length_x2;cap_length_y2"));
        assertTrue(content.contains(";10.0;5.5;10.0;125.5\n"));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(original.size() + 1, files.count());
        }
        assertTrue(caps.getPersistence().saveDescriptions(caps, directory.toString()));
        assertEquals(content, new String(Files.readAllBytes(truth), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test public void rejectsMissingDirectory() {
        assertFalse(new CapillariesPersistence().saveGroundTruthDescriptions(new Capillaries(), null));
    }

    @Test public void comparisonPrefersNewNameAndStillAcceptsLegacyNames() throws Exception {
        String property = "capillary.benchmark.groundTruthName";
        String previous = System.getProperty(property);
        try {
            System.clearProperty(property);
            String[] legacyNames = { "CapillariesDescription - Copy.csv",
                    "CapillariesDescription_groundtruth.csv", "CapillariesDescription_ground_truth.csv" };
            for (String legacyName : legacyNames) {
                Path directory = temporary.newFolder().toPath();
                Path legacy = Files.createFile(directory.resolve(legacyName));
                assertEquals(legacy.toFile(), CapillaryLengthRealDataBenchmarkTest.groundTruthFile(directory.toFile()));
                Path preferred = Files.createFile(directory.resolve("CapillariesDescription-groundtruth.csv"));
                assertEquals(preferred.toFile(), CapillaryLengthRealDataBenchmarkTest.groundTruthFile(directory.toFile()));
                assertTrue(Files.exists(legacy));
            }
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
