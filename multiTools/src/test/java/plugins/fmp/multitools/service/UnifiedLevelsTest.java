package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryPersistence;
import plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.LevelDetectV2Options;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class UnifiedLevelsTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Test public void oldXmlKeepsIndependentDetectionAndNoSmoothing() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        org.w3c.dom.Element root = doc.createElement("recipe");
        doc.appendChild(root);
        root.appendChild(doc.createElement("LimitsOptions"));
        BuildSeriesOptions restored = new BuildSeriesOptions();
        restored.loadFromXML(root);
        assertFalse(restored.levelTracking);
        assertFalse(restored.levelSmoothing);
        assertTrue(restored.detectBottom);
    }

    @Test public void xmlRestoresTrackingSmoothingAndBottomChoice() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        org.w3c.dom.Element root = doc.createElement("recipe");
        doc.appendChild(root);
        BuildSeriesOptions source = recipe();
        source.saveToXML(root);
        BuildSeriesOptions restored = new BuildSeriesOptions();
        restored.loadFromXML(root);
        assertRecipe(restored);
    }

    @Test public void csvRoundTripKeepsRecipeAndOldRowsStillLoad() {
        Capillary cap = new Capillary();
        cap.setKymographName("line01");
        ROI2DLine roi = new ROI2DLine(new Line2D.Double(10, 0, 10, 130));
        roi.setName("line01");
        cap.setRoi(roi);
        DetectionProvenanceSupport.copyLevelRecipeTo(cap.getProperties().getLimitsOptions(), recipe());
        String[] row = CapillaryPersistence.csvExportCapillaryDescription(cap, ";").trim().split(";", -1);
        Capillary restored = new Capillary();
        CapillaryPersistence.csvImportCapillaryDescription(restored, row);
        assertRecipe(restored.getProperties().getLimitsOptions());
        Capillary old = new Capillary();
        java.util.List<String> oldRow = new java.util.ArrayList<>(Arrays.asList(row));
        oldRow.removeIf(value -> value.startsWith("levels:"));
        CapillaryPersistence.csvImportCapillaryDescription(old, oldRow.toArray(new String[0]));
        assertFalse(old.getProperties().getLimitsOptions().levelTracking);
        assertFalse(old.getProperties().getLimitsOptions().levelSmoothing);
    }

    @Test public void trackingRestrictsJumpsWhileOriginalScansEachColumn() {
        int width = 3, height = 40;
        int[] pixels = new int[width * height];
        int[] boundary = {10, 30, 10};
        for (int x = 0; x < width; x++)
            for (int y = boundary[x]; y < height; y++) pixels[x + y * width] = 100;
        int[] independent = new int[width];
        new LevelDetectorFromKymo().computeTopThresholds(pixels, width, height,
                new Rectangle(0, 0, width, height), true, 35, 0, width - 1, independent);
        assertArrayEquals(boundary, independent);
        LevelDetectV2Options v2 = new LevelDetectV2Options();
        v2.edgePeak = false;
        v2.trackDown = 5;
        int[] tracked = new int[width];
        new LevelDetectorFromKymoV2().detectTopSeries(pixels, width, height,
                new Rectangle(0, 0, width, height), 0, width - 1, v2, tracked);
        assertArrayEquals(new int[] {10, 15, 12}, tracked);
    }

    @Test public void smoothingCanBeDisabledWithoutChangingShortEvents() {
        int[] raw = {10, 10, 20, 10, 10};
        int[] unchanged = raw.clone();
        LevelSeriesSmoother.smooth(unchanged, 1, 0);
        assertArrayEquals(raw, unchanged);
        LevelSeriesSmoother.smooth(raw, 5, 4);
        assertArrayEquals(new int[] {10, 10, 10, 10, 10}, raw);
    }

    private static BuildSeriesOptions recipe() {
        BuildSeriesOptions o = new BuildSeriesOptions();
        o.levelTracking = true;
        o.levelSmoothing = true;
        o.detectBottom = false;
        o.levelV2.tapePrepass = true;
        o.levelV2.removeHorizontalAverage = true;
        o.levelV2.runBackwards = true;
        o.levelV2.trackDown = 37;
        o.levelV2.medianWindow = 7;
        return o;
    }

    private static void assertRecipe(BuildSeriesOptions o) {
        assertTrue(o.levelTracking);
        assertTrue(o.levelSmoothing);
        assertFalse(o.detectBottom);
        assertTrue(o.levelV2.tapePrepass);
        assertTrue(o.levelV2.removeHorizontalAverage);
        assertTrue(o.levelV2.runBackwards);
        assertEquals(37, o.levelV2.trackDown);
        assertEquals(7, o.levelV2.medianWindow);
    }
}
