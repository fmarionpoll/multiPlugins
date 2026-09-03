package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Files;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CapillaryGroundTruthLoadTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @BeforeClass public static void init() { icy.preferences.IcyPreferences.init(); }
    private Capillary cap(String id) {
        Capillary c = new Capillary();
        c.setKymographPrefix(id);
        c.setKymographName("line" + id);
        c.setRoi(new ROI2DLine(new Line2D.Double(10, 0, 10, 130)));
        return c;
    }
    @Test public void roundTripMatchesIdentifiersAndPreservesOtherData() throws Exception {
        Capillaries source = new Capillaries();
        Capillary reference = cap("0L");
        reference.getProperties().setMeasuredEndpoints(new Point2D.Double(10, 5.5), new Point2D.Double(10, 125.5));
        source.addCapillary(reference);
        File dir = temp.newFolder();
        assertTrue(source.getPersistence().saveGroundTruthDescriptions(source, dir.toString()));
        File csv = CapillaryGroundTruthLoader.findFile(dir);
        byte[] before = Files.readAllBytes(csv.toPath());
        Capillaries target = new Capillaries();
        Capillary missing = cap("1L"), current = cap("0L");
        target.addCapillary(missing); target.addCapillary(current);
        current.setPixels(100);
        current.getPhaseGeometry().initialize(0, ((ROI2DLine)current.getRoi()).getLine(), new Line2D.Double(10, 15, 10, 115));
        current.getPhaseGeometry().putBlue(50, new Line2D.Double(20, 20, 20, 120));
        double volume = current.getVolume();
        CapillaryGroundTruthLoader.Preview preview = CapillaryGroundTruthLoader.read(csv, target);
        assertEquals(1, preview.count());
        assertTrue(preview.summary().contains("1L"));
        assertEquals(100, current.getPixels()); // preview is read-only
        preview.apply();
        assertEquals(120, current.getPixels());
        assertEquals(5.5, current.getProperties().getMeasuredStart().getY(), 0);
        assertEquals(5.5, current.getPhaseGeometry().getBlueAt(0).getY1(), 0);
        assertEquals(20, current.getPhaseGeometry().getBlueAt(50).getY1(), 0);
        assertEquals(0, ((ROI2DLine)current.getRoi()).getLine().getY1(), 0);
        assertEquals(volume, current.getVolume(), 0);
        assertFalse(missing.getProperties().hasMeasuredEndpoints());
        assertArrayEquals(before, Files.readAllBytes(csv.toPath()));
        assertEquals(1, dir.list().length);
    }

    @Test public void findsLegacyButPrefersNewAndNeverNormal() throws Exception {
        File dir = temp.newFolder();
        Files.createFile(new File(dir, "CapillariesDescription.csv").toPath());
        try { CapillaryGroundTruthLoader.findFile(dir); fail("must not use operational CSV"); }
        catch (java.io.IOException expected) { }
        File copy = new File(dir, "CapillariesDescription - Copy.csv");
        Files.createFile(copy.toPath());
        assertEquals(copy, CapillaryGroundTruthLoader.findFile(dir));
        File preferred = new File(dir, "CapillariesDescription-groundtruth.csv");
        Files.createFile(preferred.toPath());
        assertEquals(preferred, CapillaryGroundTruthLoader.findFile(dir));
    }
}
