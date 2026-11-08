package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class MeniscusReferenceTrackingTest {
    private BufferedImage image(int n, int missing) {
        BufferedImage image = new BufferedImage(n, 120, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < n; x++) for (int y = 0; y < 120; y++) {
            double line = x == missing ? 0 : 85*Math.exp(-.5*Math.pow((y-(70+.05*x))/1.4, 2));
            int value = (int)Math.round(180-line);
            image.setRGB(x, y, (value<<16)|(value<<8)|value);
        }
        return image;
    }

    private Map<Integer, Double> seed(int x) {
        Map<Integer, Double> anchors = new LinkedHashMap<>(); anchors.put(x, 70+.05*x); return anchors;
    }

    @Test public void removesJitterAndPreservesGradualSlopeUsingFeatureOutsideStrip() {
        int n = 81; double[] raw = new double[n];
        for (int x = 0; x < n; x++) raw[x] = 20+.12*x+.8*Math.sin(x*2.1);
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(n, -1), raw, seed(40), 12, null);
        double before = 0, after = 0;
        for (int x = 0; x < n; x++) {
            assertTrue(r.reliable[x]);
            assertEquals(70+.05*x, r.tracked[x], .2);
            before += Math.pow(raw[x]-(20+.12*x), 2);
            after += Math.pow(r.refined[x]-(20+.12*x), 2);
            assertEquals(r.target[x], r.tracked[x]-r.refined[x], 1e-9);
        }
        assertTrue("Alignment should remove most of the jitter", after < before*.12);
        assertEquals(.12, (r.refined[70]-r.refined[10])/60, .01);
    }

    @Test public void manualBlocksStayExactAndTheirStepsAreNotSpreadIntoNeighbours() {
        int n = 81; double[] raw = new double[n]; boolean[] fixed = new boolean[n];
        for (int x = 0; x < n; x++) { fixed[x] = x >= 40; raw[x] = 20+.12*x+(fixed[x]?5:0); }
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(n, -1), raw, seed(40), 12, fixed);
        for (int x = 40; x < n; x++) assertEquals(raw[x], r.refined[x], 0);
        assertEquals(raw[39], r.refined[39], .15);
        MeniscusReferenceTracking.Result again = MeniscusReferenceTracking.analyze(image(n, -1), r.refined, seed(40), 12, fixed);
        for (int x = 40; x < n; x++) assertEquals(raw[x], again.refined[x], 0);
    }

    @Test public void missingFeatureStopsTrackingAndExtraAnchorRestartsIt() {
        int n = 81; double[] raw = new double[n]; java.util.Arrays.fill(raw, 25);
        MeniscusReferenceTracking.Result first = MeniscusReferenceTracking.analyze(image(n, 30), raw, seed(10), 12, null);
        assertFalse(first.reliable[30]); assertFalse(first.reliable[60]);
        assertEquals(raw[60], first.refined[60], 0);
        Map<Integer, Double> anchors = seed(10); anchors.put(60, 73.0);
        MeniscusReferenceTracking.Result corrected = MeniscusReferenceTracking.analyze(image(n, 30), raw, anchors, 12, null);
        assertFalse(corrected.reliable[30]); assertTrue(corrected.reliable[60]); assertTrue(corrected.reliable[80]);
    }

    @Test public void missingToprawIsNotBridged() {
        double[] raw = new double[50]; java.util.Arrays.fill(raw, 25); raw[25] = Double.NaN;
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(50, -1), raw, seed(10), 12, null);
        assertFalse(r.reliable[25]); assertFalse(r.reliable[26]); assertTrue(Double.isNaN(r.refined[25]));
    }

    @Test(expected=IllegalArgumentException.class) public void rejectsBlankSeed() {
        MeniscusReferenceTracking.analyze(image(50, 10), new double[50], seed(10), 12, null);
    }

    @Test public void exponentialTargetRemovesLocalWigglesAndKeepsThePlateauShape() {
        int n = 101; double[] raw = new double[n];
        for (int x = 0; x < n; x++) raw[x] = 20+.05*x+15*(1-Math.exp(-x/18.0))+.8*Math.sin(x*2.1);
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(n,-1),raw,seed(40),9,null,
                MeniscusReferenceTracking.Model.EXPONENTIAL);
        double error = 0;
        for (int x = 0; x < n; x++) {
            assertTrue(r.reliable[x]);
            error += Math.pow(r.target[x]-(50-15*(1-Math.exp(-x/18.0))),2);
            if (x > 1) assertTrue("Target must remain smooth",Math.abs(r.target[x]-2*r.target[x-1]+r.target[x-2]) < .06);
        }
        assertTrue("Model should recover the smooth target",Math.sqrt(error/n)<.15);
        assertEquals(0,r.linearFallbackSegments);
    }

    @Test public void exponentialStillPreservesManualBlocksExactly() {
        double[] raw = new double[81]; boolean[] fixed = new boolean[81];
        for (int i = 0; i < raw.length; i++) { raw[i] = 25+.1*i+(i>=40?4:0); fixed[i] = i>=40; }
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(81,-1),raw,seed(40),9,fixed,
                MeniscusReferenceTracking.Model.EXPONENTIAL);
        for (int i = 40; i < raw.length; i++) {
            assertEquals(raw[i],r.refined[i],0);
            assertEquals(r.tracked[i]-raw[i],r.target[i],0);
        }
        assertEquals(41,r.fixedColumns);
    }

    @Test public void boundedExponentialMatchesAnIndependentFitOfTheSelectedColumns() {
        int n = 101, first = 25, last = 75, anchor = 40;
        BufferedImage image = image(n, -1);
        double[] raw = new double[n];
        for (int i = 0; i < n; i++)
            raw[i] = 20+.05*i+15*(1-Math.exp(-i/18.0))+.8*Math.sin(i*2.1)
                    + (i < first || i > last ? 10 : 0);
        Map<Integer, Double> anchors = seed(anchor);
        anchors.put(10, 0.0);
        MeniscusReferenceTracking.Result bounded = MeniscusReferenceTracking.analyze(image, raw, anchors, 9,
                null, MeniscusReferenceTracking.Model.EXPONENTIAL, first, last);
        int width = last - first + 1;
        Map<Integer, Double> croppedAnchors = new LinkedHashMap<>();
        croppedAnchors.put(anchor - first, anchors.get(anchor));
        MeniscusReferenceTracking.Result cropped = MeniscusReferenceTracking.analyze(
                image.getSubimage(first, 0, width, image.getHeight()), Arrays.copyOfRange(raw, first, last + 1),
                croppedAnchors, 9, null, MeniscusReferenceTracking.Model.EXPONENTIAL);
        assertEquals(0, bounded.linearFallbackSegments);
        for (int i = 0; i < n; i++) {
            if (i >= first && i <= last) {
                assertTrue(bounded.reliable[i]);
                assertEquals(cropped.refined[i - first], bounded.refined[i], 0);
            } else {
                assertFalse(bounded.reliable[i]);
                assertTrue(Double.isNaN(bounded.tracked[i]));
                assertTrue(Double.isNaN(bounded.target[i]));
                assertEquals(raw[i], bounded.refined[i], 0);
            }
        }
    }

    @Test(expected=IllegalArgumentException.class) public void boundedTrackingRequiresAnAnchorWithinTheRange() {
        MeniscusReferenceTracking.analyze(image(50, -1), new double[50], seed(10), 12, null,
                MeniscusReferenceTracking.Model.EXPONENTIAL, 15, 30);
    }

    @Test public void shortExponentialSegmentFallsBackToOneStraightLine() {
        double[] raw = {25,26,24,25,27,26};
        MeniscusReferenceTracking.Result r = MeniscusReferenceTracking.analyze(image(6,-1),raw,seed(3),1,null,
                MeniscusReferenceTracking.Model.EXPONENTIAL);
        assertEquals(1,r.linearFallbackSegments);
        for (int i = 2; i < raw.length; i++) assertEquals(0,r.target[i]-2*r.target[i-1]+r.target[i-2],1e-9);
    }
}
