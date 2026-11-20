package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import icy.image.IcyBufferedImage;
import icy.roi.ROI2D;
import icy.type.DataType;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryPersistence;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale.MeasurePixelSource;
import plugins.fmp.multitools.experiment.capillary.geometry.NormedBlueKymoGeometry;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.series.options.BuildSeriesOptions.DirectSampling;
import plugins.fmp.multitools.tools.ROI2D.AlongT;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class DirectCapillarySamplingTest {
    @org.junit.ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
            new plugins.fmp.multitools.test.InMemoryImageRule();
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    private Capillary capillary() {
        ROI2DLine green = new ROI2DLine(new Line2D.Double(20, 0, 20, 120));
        green.setName("line01");
        Capillary cap = new Capillary(green);
        cap.setKymographName("line01");
        cap.getAlongTList().clear();
        cap.addAlongTFromImport(new AlongT(0, green));
        cap.addAlongTFromImport(new AlongT(5, new ROI2DLine(new Line2D.Double(60, 0, 60, 120))));
        cap.getPhaseGeometry().initialize(0, new Line2D.Double(20, 0, 20, 120),
                new Line2D.Double(20, 10, 20, 110));
        java.util.Map<Long, Line2D> blues = new java.util.TreeMap<>();
        blues.put(0L, new Line2D.Double(20, 10, 20, 110));
        blues.put(5L, new Line2D.Double(60, 10, 60, 110));
        cap.getPhaseGeometry().replaceBlueKeyframes(blues);
        return cap;
    }

    @Test public void selectsFrameGeometryAndSplitsExpansionBetweenEnds() {
        Capillary cap = capillary();
        BuildSeriesOptions options = new BuildSeriesOptions();
        assertEquals(20, line(cap, 4, options).getX1(), 0);
        assertEquals(60, line(cap, 5, options).getX1(), 0);
        options.directSampling = DirectSampling.BLUE;
        assertEquals(10, line(cap, 0, options).getY1(), 0);
        assertEquals(60, line(cap, 6, options).getX1(), 0);
        options.directSampling = DirectSampling.EXPANDED_BLUE;
        assertEquals(5, line(cap, 0, options).getY1(), 1e-9);
        assertEquals(115, line(cap, 0, options).getY2(), 1e-9);
    }

    private Line2D line(Capillary cap, long frame, BuildSeriesOptions options) {
        return NormedBlueKymoGeometry.lineFromRoi(DirectCapillarySampling.roiAt(cap, frame, options));
    }

    @Test public void profilesFollowMovingBlueAndExpansionKeepsCalibratedPosition() {
        Capillary cap = capillary();
        cap.setVolume(10);
        IcyBufferedImage image = new IcyBufferedImage(100, 130, 3, DataType.UBYTE);
        for (int c = 0; c < 3; c++) {
            byte[] values = new byte[100 * 130];
            for (int y = 0; y < 130; y++) {
                values[20 + y * 100] = (byte) (y >= 40 ? 200 : 0);
                values[60 + y * 100] = (byte) (y >= 70 ? 200 : 0);
            }
            image.setDataXY(c, values);
        }
        for (DirectSampling mode : new DirectSampling[] { DirectSampling.BLUE, DirectSampling.EXPANDED_BLUE }) {
            BuildSeriesOptions options = new BuildSeriesOptions();
            options.directSampling = mode;
            for (int frame : new int[] { 0, 5 }) {
                ROI2D roi = DirectCapillarySampling.roiAt(cap, frame, options);
                List<ArrayList<int[]>> masks = CapillaryProfileExtractor.buildMasksAlongRoi(roi, 100, 130, 0);
                int[] profile = CapillaryProfileExtractor.extractRgbProfileFromMasks(image, masks)[0];
                int index = 0;
                while (profile[index] < 100) index++;
                double nativePosition = index * DirectCapillarySampling.pixelStep(roi, profile.length, options)
                        - DirectCapillarySampling.originOffset(roi, options);
                assertEquals(frame == 0 ? 30 : 60, nativePosition, 1e-9);
                assertEquals(frame == 0 ? 3 : 6, CapillaryPixelScale.toUl(nativePosition, cap, frame,
                        MeasurePixelSource.CAM_DIRECT, 0.10, true), 1e-9);
            }
        }
    }

    @Test public void diagonalProfileUsesPhysicalLength() {
        BuildSeriesOptions options = new BuildSeriesOptions();
        options.directSampling = DirectSampling.BLUE;
        ROI2DLine diagonal = new ROI2DLine(new Line2D.Double(0, 0, 30, 40));
        int samples = CapillaryProfileExtractor.buildMasksAlongRoi(diagonal, 100, 100, 0).size();
        assertEquals(50, (samples - 1) * DirectCapillarySampling.pixelStep(diagonal, samples, options), 1e-9);
    }

    @Test public void samplingSettingsSurviveXmlAndCapillaryCsv() throws Exception {
        Capillary cap = capillary();
        BuildSeriesOptions options = cap.getProperties().getLimitsOptions();
        options.directSampling = DirectSampling.EXPANDED_BLUE;
        options.directBlueExpansionRatio = 0.24;
        options.profilePerpendicular = false;
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        org.w3c.dom.Element root = doc.createElement("recipe");
        doc.appendChild(root);
        options.saveToXML(root);
        BuildSeriesOptions restored = new BuildSeriesOptions();
        restored.loadFromXML(root);
        assertSettings(restored);
        Capillary reloaded = new Capillary();
        CapillaryPersistence.csvImportCapillaryDescription(reloaded,
                CapillaryPersistence.csvExportCapillaryDescription(cap, ";").trim().split(";", -1));
        assertSettings(reloaded.getProperties().getLimitsOptions());
    }

    private void assertSettings(BuildSeriesOptions options) {
        assertEquals(DirectSampling.EXPANDED_BLUE, options.directSampling);
        assertEquals(0.24, options.directBlueExpansionRatio, 1e-9);
        assertFalse(options.profilePerpendicular);
    }

    @Test(expected = IllegalArgumentException.class) public void blueModeRejectsMissingGeometry() {
        BuildSeriesOptions options = new BuildSeriesOptions();
        options.directSampling = DirectSampling.BLUE;
        DirectCapillarySampling.roiAt(new Capillary(), 0, options);
    }

    @Test public void legacyRecipeKeepsGreenSampling() {
        BuildSeriesOptions options = new BuildSeriesOptions();
        plugins.fmp.multitools.experiment.capillaries.DetectionProvenanceSupport.importUnifiedLevelRecipe(options,
                "levels:false|false|true|false|false|false|true|3|25|5|4");
        assertEquals(DirectSampling.GREEN, options.directSampling);
        assertEquals(0.10, options.directBlueExpansionRatio, 0);
    }
}
