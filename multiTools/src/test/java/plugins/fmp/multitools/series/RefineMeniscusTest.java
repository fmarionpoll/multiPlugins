package plugins.fmp.multitools.series;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import icy.image.IcyBufferedImage;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.tools.polyline.Level2D;

public class RefineMeniscusTest {
    @BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static final double[] RAW = {30, 30, 30, 30, 30, 30};

    private static class TestExperiment extends Experiment {
        boolean closed;
        String resultsDirectory;
        @Override public String getResultsDirectory() { return resultsDirectory; }
        @Override public void closeSequences() { closed = true; }
    }

    private static TestExperiment experiment() {
        BufferedImage image = new BufferedImage(RAW.length, 80, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < RAW.length; x++)
            for (int y = 0; y < image.getHeight(); y++) {
                int v = y >= 32 + x / 2 ? 210 : 40;
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        TestExperiment exp = new TestExperiment();
        exp.setSeqKymos(new SequenceKymos("test", IcyBufferedImage.createFrom(image)));
        plugins.kernel.roi.roi2d.ROI2DLine roi = new plugins.kernel.roi.roi2d.ROI2DLine(
                new java.awt.geom.Line2D.Double(0, 0, 0, 79));
        roi.setName("line01");
        Capillary cap = new Capillary(roi);
        cap.setKymographName("line01");
        cap.setKymographIndex(0);
        cap.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3, 4, 5}, RAW.clone(), RAW.length);
        exp.getCapillaries().getList().add(cap);
        return exp;
    }

    @Test(timeout = 10000) public void writesValidatedToprawAndQualityBeforeClosing() throws Exception {
        TestExperiment exp = experiment();
        exp.resultsDirectory = temporary.newFolder("results").getAbsolutePath();
        Capillary cap = exp.getCapillaries().getList().get(0);
        MeniscusRefinement.Proposal expected = MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,
                MeniscusRefinement.image(exp.getSeqKymos().getSequence(), cap, RAW.length), 10, 3, true);
        RefineMeniscus worker = new RefineMeniscus(10, 3, true) {
            @Override protected void save(Experiment e) {
                assertFalse(exp.closed);
                assertArrayEquals(expected.refined(), cap.getTopRaw().polylineLevel.ypoints, 0);
                super.save(e);
            }
        };
        worker.analyzeExperiment(exp);
        assertTrue(exp.closed);
        assertEquals(1, worker.getSavedExperiments());
        // Round-trip through the same files used by the normal Levels save.
        cap.getTopRaw().clear();
        assertTrue(exp.getCapillaries().getPersistence().loadMeasures(exp.getCapillaries(), exp.getResultsDirectory()));
        assertArrayEquals(expected.refined(), cap.getTopRaw().polylineLevel.ypoints, 0.001);
        assertArrayEquals(expected.quality(), cap.getTopRaw().getRefinementQuality());
    }

    @Test(timeout = 10000) public void failedSaveRestoresToprawAndDoesNotCloseOrAdvance() {
        TestExperiment exp = experiment();
        RefineMeniscus worker = new RefineMeniscus(10, 3, true) {
            @Override protected void save(Experiment e) { throw new IllegalStateException("disk full"); }
        };
        try {
            worker.analyzeExperiment(exp);
            fail("Save failure must stop the series");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("disk full"));
        }
        assertFalse(exp.closed);
        assertEquals(0, worker.getSavedExperiments());
        assertArrayEquals(RAW, exp.getCapillaries().getList().get(0).getTopRaw().polylineLevel.ypoints, 0);
    }

    @Test(timeout = 10000) public void usesNamedNativeFileDespitePaddedDisplayAndStaleIndex() throws Exception {
        TestExperiment exp = experiment();
        Capillary cap = exp.getCapillaries().getList().get(0);
        BufferedImage nativeImage = MeniscusRefinement.image(exp.getSeqKymos().getSequence(), cap, RAW.length);
        java.io.File file = new java.io.File(temporary.newFolder("native"), "line01.png");
        assertTrue(javax.imageio.ImageIO.write(nativeImage, "png", file));
        // Reproduce a display frame padded to another kymograph's wider time axis.
        exp.getSeqKymos().getSequence().setImage(0, 0, IcyBufferedImage.createFrom(
                new BufferedImage(RAW.length + 4, 80, BufferedImage.TYPE_INT_RGB)));
        exp.getSeqKymos().getImageLoader().setImagesList(java.util.Collections.singletonList(file.getAbsolutePath()));
        cap.setKymographIndex(82);
        MeniscusRefinement.Proposal expected = MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,
                nativeImage, 10, 3, true);
        RefineMeniscus worker = new RefineMeniscus(10, 3, true) {
            @Override protected void save(Experiment e) {
                assertArrayEquals(expected.refined(), cap.getTopRaw().polylineLevel.ypoints, 0);
            }
        };
        worker.analyzeExperiment(exp);
        assertEquals(1, worker.getSavedExperiments());
        assertTrue(exp.closed);
    }

    @Test(timeout = 10000) public void diagnosesInvalidCoordinatesSeparatelyFromMissingRgbChannels() {
        TestExperiment exp = experiment();
        Capillary cap = exp.getCapillaries().getList().get(0);
        cap.getTopRaw().polylineLevel.xpoints[RAW.length - 1] = RAW.length;
        try {
            MeniscusRefinement.image(exp.getSeqKymos().getSequence(), cap, RAW.length);
            fail("Out-of-image coordinates must not be silently resized");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("x=6.0, image=6 columns, topraw=6 points"));
        }
        cap.getTopRaw().polylineLevel.xpoints[RAW.length - 1] = RAW.length - 1;
        try {
            MeniscusRefinement.image(new IcyBufferedImage(RAW.length, 80, 1, icy.type.DataType.UBYTE),
                    cap, RAW.length);
            fail("Grayscale has no RGB color differences to correlate");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("channels=1"));
        }
    }

    @Test(timeout = 10000) public void saves135PointsAtTheirOriginalColumnsIn142ColumnNativeImage() throws Exception {
        TestExperiment exp = experiment();
        exp.resultsDirectory = temporary.newFolder("partial-results").getAbsolutePath();
        Capillary cap = exp.getCapillaries().getList().get(0);
        cap.setKymographName("line82");
        double[] x = new double[135], levels = new double[135];
        for (int i = 0; i < x.length; i++) { x[i] = i + 7; levels[i] = 32; }
        cap.getTopRaw().polylineLevel = new Level2D(x.clone(), levels, levels.length);
        BufferedImage original = new BufferedImage(142, 80, BufferedImage.TYPE_INT_RGB);
        for (int column = 0; column < original.getWidth(); column++)
            for (int y = 0; y < original.getHeight(); y++)
                original.setRGB(column, y, (column << 16) | ((y + column) << 8) | y);
        java.io.File file = new java.io.File(temporary.newFolder("partial-native"), "line82.png");
        assertTrue(javax.imageio.ImageIO.write(original, "png", file));
        exp.getSeqKymos().getImageLoader().setImagesList(java.util.Collections.singletonList(file.getAbsolutePath()));
        BufferedImage sampled = MeniscusRefinement.image(IcyBufferedImage.createFrom(original), cap, levels.length);
        assertEquals(135, sampled.getWidth());
        for (int i = 0; i < sampled.getWidth(); i++)
            for (int y = 0; y < sampled.getHeight(); y++)
                assertEquals(original.getRGB(i + 7, y), sampled.getRGB(i, y));
        MeniscusRefinement.Proposal expected = MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,
                sampled, 10, 3, true);
        RefineMeniscus worker = new RefineMeniscus(10, 3, true);
        worker.analyzeExperiment(exp);
        assertTrue(exp.closed);
        assertEquals(1, worker.getSavedExperiments());
        cap.getTopRaw().clear();
        assertTrue(exp.getCapillaries().getPersistence().loadMeasures(exp.getCapillaries(), exp.resultsDirectory));
        assertEquals(135, cap.getTopRaw().polylineLevel.npoints);
        assertArrayEquals(x, cap.getTopRaw().polylineLevel.xpoints, 0);
        assertArrayEquals(expected.refined(), cap.getTopRaw().polylineLevel.ypoints, 0.001);
        assertArrayEquals(expected.quality(), cap.getTopRaw().getRefinementQuality());
    }

    @Test(timeout = 10000) public void samplesSkippedAndFractionalColumnsWithoutMovingSavedCoordinates() {
        TestExperiment exp = experiment();
        Capillary cap = exp.getCapillaries().getList().get(0);
        double[] x = {1, 3.5, 7};
        cap.getTopRaw().polylineLevel = new Level2D(x.clone(), new double[] {30, 31, 32}, 3);
        BufferedImage original = new BufferedImage(8, 80, BufferedImage.TYPE_INT_RGB);
        for (int column = 0; column < 8; column++)
            for (int y = 0; y < 80; y++) original.setRGB(column, y, column * 20 << 16);
        BufferedImage sampled = MeniscusRefinement.image(IcyBufferedImage.createFrom(original), cap, 3);
        assertEquals(20, (sampled.getRGB(0, 0) >> 16) & 255);
        assertEquals(70, (sampled.getRGB(1, 0) >> 16) & 255);
        assertEquals(140, (sampled.getRGB(2, 0) >> 16) & 255);
        assertArrayEquals(x, cap.getTopRaw().polylineLevel.xpoints, 0);
    }

    @Test(timeout = 10000) public void mixedTrajectoriesKeepTheirCoordinatesAndQualityThroughSave() throws Exception {
        TestExperiment exp = experiment();
        exp.resultsDirectory = temporary.newFolder("mixed-results").getAbsolutePath();
        Capillary first = exp.getCapillaries().getList().get(0);
        Capillary second = experiment().getCapillaries().getList().get(0);
        second.getRoi().setName("line02");
        second.setKymographName("line02");
        second.setKymographPrefix("02");
        second.setKymographIndex(1);
        exp.getCapillaries().getList().add(second);
        double[] x = {1, 3.5, 5};
        double[] y = {30.125, 31.25, 32.375};
        boolean[] quality = {true, false, true};
        second.getTopRaw().polylineLevel = new Level2D(x.clone(), y.clone(), 3);
        second.getTopRaw().setRefinementQuality(quality);
        new RefineMeniscus(10, 3, true).save(exp);
        first.getTopRaw().clear();
        second.getTopRaw().clear();
        assertTrue(exp.getCapillaries().getPersistence().loadMeasures(exp.getCapillaries(), exp.resultsDirectory));
        assertArrayEquals(new double[] {0, 1, 2, 3, 4, 5}, first.getTopRaw().polylineLevel.xpoints, 0);
        assertArrayEquals(RAW, first.getTopRaw().polylineLevel.ypoints, 0);
        assertArrayEquals(x, second.getTopRaw().polylineLevel.xpoints, 0);
        assertArrayEquals(y, second.getTopRaw().polylineLevel.ypoints, 0);
        assertArrayEquals(quality, second.getTopRaw().getRefinementQuality());
    }

    @Test(timeout = 10000) public void missingToprawDoesNotPartiallyValidateExperiment() {
        TestExperiment exp = experiment();
        exp.getCapillaries().getList().add(new Capillary());
        RefineMeniscus worker = new RefineMeniscus(10, 3, true);
        try {
            worker.analyzeExperiment(exp);
            fail("Missing measurements must stop the series");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Detect topraw first"));
        }
        assertArrayEquals(RAW, exp.getCapillaries().getList().get(0).getTopRaw().polylineLevel.ypoints, 0);
        assertFalse(exp.closed);
        assertEquals(0, worker.getSavedExperiments());
    }

    @Test(timeout = 10000) public void stopDoesNotValidateOrSaveAnIncompleteExperiment() {
        TestExperiment exp = experiment();
        RefineMeniscus worker = new RefineMeniscus(10, 3, true) {
            @Override protected void save(Experiment e) { fail("Stopped experiment must not be saved"); }
        };
        worker.requestStop();
        worker.analyzeExperiment(exp);
        assertTrue(worker.stopFlag);
        assertEquals(0, worker.getSavedExperiments());
        assertTrue(Arrays.equals(RAW, exp.getCapillaries().getList().get(0).getTopRaw().polylineLevel.ypoints));
    }
}
