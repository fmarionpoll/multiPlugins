package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryPersistence;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CapillaryGroundTruthWidthsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    private Capillary cap(String id) {
        Capillary cap = new Capillary();
        cap.setKymographPrefix(id);
        cap.setKymographName("line" + id);
        cap.setRoi(new ROI2DLine(new Line2D.Double(10, 0, 10, 130)));
        cap.getProperties().setMeasuredEndpoints(new java.awt.geom.Point2D.Double(11, 5.5),
                new java.awt.geom.Point2D.Double(12, 125.5));
        return cap;
    }

    private CapillaryLengthResult detection(int frame, Capillary... caps) {
        CapillaryLengthResult result = new CapillaryLengthResult();
        for (int i = 0; i < caps.length; i++) {
            CapillaryLengthResult.Measure m = new CapillaryLengthResult.Measure(caps[i], "detected", 100);
            // Deliberately different from the manual reference; only width may migrate.
            m.setDetectedEndpoints(new java.awt.geom.Point2D.Double(20, 30), new java.awt.geom.Point2D.Double(20, 140));
            m.setWidthPixels(8.5 + i);
            m.setStatus(CapillaryLengthResult.Status.OK);
            m.setFrameIndex(frame);
            result.addMeasure(m);
        }
        return result;
    }

    private Path legacy(File dir, Capillary... caps) throws Exception {
        StringBuilder csv = new StringBuilder("#;version;2.1\r\n");
        csv.append(CapillaryPersistence.csvExportCapillarySubSectionHeader(";").replace("\n", "\r\n"));
        for (Capillary cap : caps)
            csv.append(CapillaryPersistence.csvExportCapillaryDescription(cap, ";").replace("\n", "\r\n"));
        csv.append("#;#\r\n#;ALONGT;untouched\r\nmanual metadata\r\n");
        Path path = dir.toPath().resolve(CapillariesPersistence.GROUND_TRUTH_CSV);
        Files.write(path, csv.toString().getBytes(Charset.defaultCharset()));
        return path;
    }

    @Test public void oldReferenceReceivesOnlyMissingWidthsAndRetainsAllOtherFields() throws Exception {
        File dir = temporary.newFolder();
        Capillary matched = cap("0L"), unmatched = cap("1L");
        Path truth = legacy(dir, matched, unmatched);
        byte[] before = Files.readAllBytes(truth);
        Path normal = dir.toPath().resolve("CapillariesDescription.csv");
        Files.write(normal, new byte[] { 1, 2, 3 });
        assertEquals(1, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, matched)));
        String after = new String(Files.readAllBytes(truth), Charset.defaultCharset());
        String restored = after.replace("cap_width_px;", "").replace(";8.5;11.0;5.5;12.0;125.5",
                ";11.0;5.5;12.0;125.5").replace(";;11.0;5.5;12.0;125.5", ";11.0;5.5;12.0;125.5");
        assertEquals(new String(before, Charset.defaultCharset()), restored);
        assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(normal));
        byte[] migrated = Files.readAllBytes(truth);
        assertEquals(0, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, matched)));
        assertArrayEquals(migrated, Files.readAllBytes(truth));
        Capillaries target = new Capillaries(); target.addCapillary(matched); target.addCapillary(unmatched);
        CapillaryGroundTruthLoader.read(truth.toFile(), target).apply();
        assertEquals(8.5, matched.getPhaseGeometry().getWidthAt(0), 0);
        assertEquals(11., matched.getPhaseGeometry().getBlueAt(0).getX1(), 0);
        assertEquals(5.5, matched.getPhaseGeometry().getBlueAt(0).getY1(), 0);
        assertTrue(Double.isNaN(unmatched.getPhaseGeometry().getWidthAt(0)));
    }

    @Test public void preservesExistingWidthsWhileFillingBlankCells() throws Exception {
        File dir = temporary.newFolder();
        Capillary existing = cap("0L"), missing = cap("1L");
        existing.getPhaseGeometry().initialize(0, ((ROI2DLine)existing.getRoi()).getLine(),
                new Line2D.Double(11, 5.5, 12, 125.5), 6.25);
        Capillaries caps = new Capillaries(); caps.addCapillary(existing); caps.addCapillary(missing);
        assertTrue(caps.getPersistence().saveGroundTruthDescriptions(caps, dir.toString()));
        Path truth = dir.toPath().resolve(CapillariesPersistence.GROUND_TRUTH_CSV);
        assertEquals(1, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, existing, missing)));
        CapillaryGroundTruthLoader.read(truth.toFile(), caps).apply();
        assertEquals(6.25, existing.getPhaseGeometry().getWidthAt(0), 0);
        assertEquals(9.5, missing.getPhaseGeometry().getWidthAt(0), 0);
    }

    @Test public void missingReferenceLaterFramesAndUnmatchedDetectionsDoNotWrite() throws Exception {
        File dir = temporary.newFolder();
        Capillary cap = cap("0L");
        assertEquals(0, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, cap)));
        assertEquals(0, dir.list().length);
        Path truth = legacy(dir, cap);
        byte[] before = Files.readAllBytes(truth);
        assertEquals(0, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(50, cap)));
        assertEquals(0, CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, cap("2L"))));
        assertArrayEquals(before, Files.readAllBytes(truth));
    }

    @Test public void duplicateReferenceIdentifiersAbortWithoutChangingTheFile() throws Exception {
        File dir = temporary.newFolder();
        Capillary cap = cap("0L");
        Path truth = legacy(dir, cap, cap);
        byte[] before = Files.readAllBytes(truth);
        try {
            CapillaryGroundTruthWidths.fillMissing(dir.toString(), detection(0, cap));
            fail("ambiguous reference must not be rewritten");
        } catch (java.io.IOException expected) { }
        assertArrayEquals(before, Files.readAllBytes(truth));
    }

    @Test public void legacyCoordinateAliasesLoadAndRetainKnownOperationalWidth() throws Exception {
        File dir = temporary.newFolder();
        Capillary cap = cap("0L");
        Path truth = legacy(dir, cap);
        cap.getPhaseGeometry().initialize(0, ((ROI2DLine)cap.getRoi()).getLine(),
                new Line2D.Double(20, 30, 20, 140), 7.);
        Capillaries caps = new Capillaries(); caps.addCapillary(cap);
        CapillaryGroundTruthLoader.read(truth.toFile(), caps).apply();
        assertEquals(7., cap.getPhaseGeometry().getWidthAt(0), 0);
        assertEquals(11., cap.getPhaseGeometry().getBlueAt(0).getX1(), 0);
    }
}
