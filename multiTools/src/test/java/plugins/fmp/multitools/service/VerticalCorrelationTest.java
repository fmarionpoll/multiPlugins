package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import javax.imageio.ImageIO;
import org.junit.Assume;
import org.junit.Test;

public class VerticalCorrelationTest {
    private static BufferedImage movie(double[] center) {
        BufferedImage image = new BufferedImage(center.length, 90, BufferedImage.TYPE_INT_RGB);
        for (int t = 0; t < center.length; t++) for (int y = 0; y < 90; y++) {
            double fraction = 1 / (1 + Math.exp(-(y - center[t]) / 1.4));
            int r = (int) Math.round(150 - 65 * fraction);
            int g = (int) Math.round(155 - 5 * fraction);
            int b = (int) Math.round(145 - 5 * fraction);
            image.setRGB(t, y, (r << 16) | (g << 8) | b);
        }
        return image;
    }

    @Test public void signedFractionalShiftsAndSharpStepArePreserved() {
        double[] center = new double[40];
        for (int i = 0; i < center.length; i++) center[i] = 40 + i * .15 + (i >= 20 ? 3 : 0);
        double[] raw = Arrays.stream(center).map(Math::rint).toArray();
        VerticalCorrelation.Result r = VerticalCorrelation.analyze(movie(center), raw, 10);
        assertFalse(r.reliable[0]);
        assertTrue(Double.isNaN(r.shift[0]));
        assertTrue(r.reliable[20]);
        assertEquals(3.15, r.shift[20], .15);
        assertEquals(3.15, r.refined[20] - r.refined[19], .20);
        double[] decreasing = {45, 43.6, 42.2, 40.8};
        VerticalCorrelation.Result down = VerticalCorrelation.analyze(movie(decreasing), decreasing, 10);
        assertTrue(down.reliable[1]);
        assertEquals(-1.4, down.shift[1], .15);
    }

    @Test public void largeStepNeedsLargerMaxShift() {
        double[] center = {40, 40, 49, 49};
        VerticalCorrelation.Result limited = VerticalCorrelation.analyze(movie(center), center, 10);
        assertFalse(limited.reliable[2]);
        assertEquals("Search limit", limited.reason[2]);
        VerticalCorrelation.Result wide = VerticalCorrelation.analyze(movie(center), center, 10, 12);
        assertTrue(wide.reliable[2]);
        assertEquals(9, wide.shift[2], .15);
    }

    @Test public void adaptiveSearchRecoversBoundaryStepAndKeepsOrdinaryShifts() {
        double[] center = {40, 40.3, 48.2, 48.4};
        VerticalCorrelation.Result fixed = VerticalCorrelation.analyze(movie(center), center, 6, 7);
        VerticalCorrelation.Result adaptive = VerticalCorrelation.analyze(movie(center), center, 6, 7, true);
        assertFalse(fixed.reliable[2]);
        assertTrue(adaptive.reliable[2]);
        assertEquals(7.9, adaptive.shift[2], .15);
        assertEquals(10.5, adaptive.searchRange[2], 1e-9);
        assertEquals(fixed.shift[1], adaptive.shift[1], 1e-9);
        assertEquals(7, adaptive.searchRange[1], 1e-9);
        assertFalse(VerticalCorrelation.analyze(movie(new double[]{30,65}), new double[]{30,65},6,7,true).reliable[1]);
    }

    @Test public void flatAndBorderProfilesAreRejected() {
        VerticalCorrelation.Result flat = VerticalCorrelation.analyze(new BufferedImage(4, 60, BufferedImage.TYPE_INT_RGB),
                new double[] {30, 30, 30, 30}, 10);
        assertFalse(flat.reliable[1]);
        assertTrue(Double.isNaN(flat.shift[1]));
        VerticalCorrelation.Result border = VerticalCorrelation.analyze(movie(new double[] {8, 9}), new double[] {8, 9}, 10);
        assertFalse(border.reliable[1]);
    }

    @Test public void widerSearchPreservesValidMotionNearBothImageBorders() {
        for (double[] center : new double[][] {{30,31.4,32.8,34.2}, {62,60.6,59.2,57.8}}) {
            VerticalCorrelation.Result narrow = VerticalCorrelation.analyze(movie(center), center, 12, 10);
            VerticalCorrelation.Result wide = VerticalCorrelation.analyze(movie(center), center, 12, 26);
            for (int i = 1; i < center.length; i++) {
                assertTrue("narrow comparison " + i, narrow.reliable[i]);
                assertTrue("wide comparison " + i + ": " + wide.reason[i], wide.reliable[i]);
                assertEquals(narrow.shift[i], wide.shift[i], .051);
            }
        }
    }

    @Test public void trulyTruncatedDisplacementIsFlaggedAsImageBorder() {
        // The target boundary lies above the valid profile region. A larger
        // requested search cannot recover a full profile for that boundary.
        VerticalCorrelation.Result result = VerticalCorrelation.analyze(movie(new double[] {30,0}), new double[] {30,30}, 12, 40);
        assertFalse(result.reliable[1]);
    }

    @Test public void missingFramesAreNotBridged() {
        double[] raw = {40, 40, Double.NaN, 42, 42};
        VerticalCorrelation.Result r = VerticalCorrelation.analyze(movie(new double[] {40,40,41,42,42}), raw, 10);
        assertFalse(r.reliable[2]); assertFalse(r.reliable[3]);
        assertTrue(Double.isNaN(r.refined[2]));
        assertEquals(42, r.refined[3], .05);
    }

    @Test public void noAcceptedMotionRetainsRaw() {
        double[] raw = {10, 12, 13, 16};
        assertArrayEquals(raw, VerticalCorrelation.refine(raw, new double[4], new boolean[4], .05), 1e-10);
    }

    @Test public void noiseFromQuietPeriodsRejectsBackgroundAndAcceptsSteps() {
        double[] residual = new double[100], controls = new double[100];
        boolean[] valid = new boolean[100];
        Arrays.fill(valid, true);
        for (int i = 0; i < 100; i++) residual[i] = controls[i] = .08 * Math.sin(i);
        residual[30] = 2; residual[70] = 3;
        VerticalCorrelation.Noise noise = VerticalCorrelation.estimateNoise(residual, valid, controls);
        assertTrue(noise.quietSamples > 50);
        assertTrue(noise.automatic > .1 && noise.automatic < .5);
        boolean[] events = VerticalCorrelation.candidates(residual, valid, noise.automatic);
        assertTrue(events[30]); assertTrue(events[70]); assertFalse(events[31]);
        valid[30] = false;
        assertFalse(VerticalCorrelation.candidates(residual, valid, noise.automatic)[30]);
    }

    @Test public void continuousActivityFallsBackToControls() {
        double[] residual = new double[80], controls = new double[80];
        boolean[] valid = new boolean[80];
        Arrays.fill(residual, 2); Arrays.fill(valid, true);
        for (int i = 0; i < controls.length; i++) controls[i] = .1 * Math.sin(i);
        VerticalCorrelation.Noise noise = VerticalCorrelation.estimateNoise(residual, valid, controls);
        assertEquals(0, noise.quietSamples);
        assertTrue(noise.source.contains("special case"));
        assertTrue(noise.automatic < .5);
    }

    @Test public void insufficientEvidenceDoesNotInventAutomaticThreshold() {
        VerticalCorrelation.Noise noise = VerticalCorrelation.estimateNoise(new double[10], new boolean[10], new double[0]);
        assertTrue(Double.isNaN(noise.automatic));
    }

    @Test public void optionalRealKymographMatchesExploratoryDisplacements() throws Exception {
        String directory = System.getProperty("correlation.fixture");
        Assume.assumeNotNull(directory);
        File root = new File(directory, "results");
        double[] raw = null;
        boolean top = false;
        for (String line : Files.readAllLines(new File(root, "CapillariesMeasures.csv").toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) top = line.startsWith("#;TOPRAW;");
            else if (top && line.startsWith("1L;")) {
                String[] row = line.split(";");
                raw = new double[Integer.parseInt(row[2])];
                for (int i = 0; i < raw.length; i++) raw[i] = Double.parseDouble(row[i + 3]);
            }
        }
        assertNotNull(raw);
        BufferedImage image = ImageIO.read(new File(root, "bin_60/line11.tiff"));
        assertNotNull(image);
        VerticalCorrelation.Result r = VerticalCorrelation.analyze(image, raw, 10);
        assertEquals(1.4, r.shift[57], .051);
        assertEquals(.2, r.shift[28], .051);
        assertEquals(5.25, r.shift[34], .051);
        int reliable = 0;
        for (boolean b : r.reliable) if (b) reliable++;
        assertEquals(139, reliable);
    }

    @Test public void optionalReal2LEarlyBinsSurviveWideningSearchTo26Pixels() throws Exception {
        String directory = System.getProperty("correlation.fixture");
        Assume.assumeNotNull(directory);
        File root = new File(directory, "results");
        String first = Files.readAllLines(new File(root, "CapillariesMeasures.csv").toPath(), StandardCharsets.UTF_8)
                .stream().filter(line -> line.startsWith("2L;")).findFirst().get();
        String[] row = first.split(";");
        double[] raw = new double[Integer.parseInt(row[2])];
        for (int i = 0; i < raw.length; i++) raw[i] = Double.parseDouble(row[i + 3]);
        BufferedImage image = ImageIO.read(new File(root, "bin_60/line21.tiff"));
        VerticalCorrelation.Result narrow = VerticalCorrelation.analyze(image, raw, 12, 10);
        VerticalCorrelation.Result wide = VerticalCorrelation.analyze(image, raw, 12, 26);
        for (int i = 1; i <= 4; i++) {
            assertTrue("narrow bin " + i + ": " + narrow.reason[i], narrow.reliable[i]);
            assertTrue("wide bin " + i + ": " + wide.reason[i], wide.reliable[i]);
            assertEquals("bin " + i, narrow.shift[i], wide.shift[i], .051);
        }
    }
}
