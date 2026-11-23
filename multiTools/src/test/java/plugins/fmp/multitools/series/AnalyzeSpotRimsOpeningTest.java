package plugins.fmp.multitools.series;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.SpotRimGeometry;
import plugins.fmp.multitools.series.AnalyzeSpotRims.CageRims;
import plugins.fmp.multitools.series.AnalyzeSpotRims.FramePixels;
import plugins.fmp.multitools.service.SpotLineDeficitAnalyzer;
import plugins.fmp.multitools.service.SpotRimAnalyzer;
import plugins.fmp.multitools.service.SpotRimAnalyzer.EllipseGeom;

public class AnalyzeSpotRimsOpeningTest {

    private static final int SIZE = 100;

    private CageRims cage(int nFrames) {
        Spot spot = new Spot();
        double[][] outline = SpotRimGeometry.ellipseOutline(50, 50, 16, 16);
        spot.getRimGeometry().setOutline(outline[0], outline[1]);
        List<EllipseGeom> ellipses = Arrays.asList(new EllipseGeom(50, 50, 40, 40));
        CageRims cage = new CageRims(null, Arrays.asList(spot), ellipses);
        cage.layout = SpotRimAnalyzer.layoutFor(SIZE, SIZE, cage.spots, ellipses, ellipses);
        cage.flanks = new double[nFrames][];
        cage.integral = new double[1][nFrames];
        Arrays.fill(cage.integral[0], Double.NaN);
        cage.openingExcess = new double[][] { { Double.NaN, Double.NaN } };
        cage.openingCount = new int[1];
        cage.refFloor = new double[] { Double.NaN };
        return cage;
    }

    private FramePixels frame(boolean occupied, boolean dye) {
        int[] red = new int[SIZE * SIZE];
        int[] green = new int[red.length];
        int[] blue = new int[red.length];
        boolean[] insect = new boolean[red.length];
        Arrays.fill(red, 80);
        Arrays.fill(green, 80);
        Arrays.fill(blue, 80);
        Arrays.fill(insect, occupied);
        if (dye) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double radius = Math.hypot(x - 50, y - 50);
                    if (radius >= 12 && radius <= 16) {
                        int i = y * SIZE + x;
                        red[i] = 20;
                        green[i] = 200;
                        blue[i] = 200;
                    }
                }
            }
        }
        return new FramePixels(red, green, blue, insect);
    }

    @Test
    public void calibrationRecoversAfterFlyStaysBeyondInitialWindow() {
        CageRims cage = cage(7);
        List<CageRims> cages = Arrays.asList(cage);
        FramePixels covered = frame(true, true);
        FramePixels clear = frame(false, true);
        for (int t = 0; t < 3; t++) {
            AnalyzeSpotRims.collectOpening(cages, covered, t);
        }
        assertEquals(0, cage.openingCount[0]);
        assertFalse(AnalyzeSpotRims.openingComplete(cages, 2));
        AnalyzeSpotRims.collectOpening(cages, clear, 3);
        AnalyzeSpotRims.collectOpening(cages, clear, 4);
        assertTrue(AnalyzeSpotRims.openingComplete(cages, 2));
        assertEquals(0.0, cage.refFloor[0], 1e-9);
        double dye = SpotLineDeficitAnalyzer.referenceExcess(cage.openingExcess[0]);
        assertTrue(dye > 0);

        for (int t = 0; t < 7; t++) {
            FramePixels f = t < 3 ? covered : t < 6 ? clear : frame(false, false);
            double[] flanks = SpotLineDeficitAnalyzer.sampleFlanks(cage.layout, f.red, f.green, f.blue, f.insect);
            double[] floors = SpotLineDeficitAnalyzer.floorsWithReference(cage.layout, flanks, cage.refFloor);
            SpotLineDeficitAnalyzer.integrate(cage.layout, f.red, f.green, f.blue, floors, 0.0,
                    cage.integral, t, f.insect, new double[] { dye });
        }
        double[] ratios = SpotLineDeficitAnalyzer.ratiosFromIntegrals(cage.integral, 2, 1)[0];
        SpotLineDeficitAnalyzer.finalizeRatio(ratios, dye, 0.0, false);
        assertTrue(Double.isNaN(ratios[0]));
        assertEquals(1.0, ratios[3], 1e-9);
        assertEquals(0.0, ratios[6], 1e-9);
    }

    @Test
    public void neverUncoveredSpotRemainsUnknown() {
        CageRims cage = cage(3);
        for (int t = 0; t < 3; t++) {
            AnalyzeSpotRims.collectOpening(Arrays.asList(cage), frame(true, true), t);
        }
        double dye = SpotLineDeficitAnalyzer.referenceExcess(cage.openingExcess[0]);
        assertTrue(Double.isNaN(dye));
        double[] ratios = { 0, 0, 0 };
        SpotLineDeficitAnalyzer.finalizeRatio(ratios, dye, 0.0, false);
        for (double value : ratios) {
            assertTrue(Double.isNaN(value));
        }
    }

    @Test
    public void provisionalOutlineIsReplacedWhenFlyLeaves() {
        CageRims cage = cage(1);
        double[][] fallback = SpotRimGeometry.ellipseOutline(50, 50, 28, 28);
        cage.spots.get(0).getRimGeometry().setOutline(fallback[0], fallback[1]);
        cage.retryOutline[0] = true;
        List<CageRims> cages = Arrays.asList(cage);
        AnalyzeSpotRims.retryOutlines(cages, frame(true, true), SIZE, SIZE, 5.0, new ArrayList<>());
        assertTrue(cage.retryOutline[0]);
        AnalyzeSpotRims.retryOutlines(cages, frame(false, true), SIZE, SIZE, 5.0, new ArrayList<>());
        assertFalse(cage.retryOutline[0]);
        double[] xs = cage.spots.get(0).getRimGeometry().outlineX();
        double[] ys = cage.spots.get(0).getRimGeometry().outlineY();
        for (int i = 0; i < xs.length; i++) {
            assertTrue(Math.hypot(xs[i] - 50, ys[i] - 50) < 20);
        }
    }

    @Test
    public void savedOutlineIsPreservedDuringRetries() {
        CageRims cage = cage(1);
        double[] original = cage.spots.get(0).getRimGeometry().outlineX();
        AnalyzeSpotRims.retryOutlines(Arrays.asList(cage), frame(false, true), SIZE, SIZE, 5.0, new ArrayList<>());
        assertArrayEquals(original, cage.spots.get(0).getRimGeometry().outlineX(), 0.0);
    }

    @Test
    public void preConsumedSpotDoesNotWaitForClearCalibration() {
        CageRims cage = cage(3);
        cage.spots.get(0).setConsumedBeforeRecording(true);
        AnalyzeSpotRims.collectOpening(Arrays.asList(cage), frame(true, true), 0);
        assertTrue(AnalyzeSpotRims.openingComplete(Arrays.asList(cage), 2));
        double[] ratios = { Double.NaN, Double.NaN, Double.NaN };
        SpotLineDeficitAnalyzer.finalizeRatio(ratios, Double.NaN, 0.0,
                cage.spots.get(0).isConsumedBeforeRecording());
        assertArrayEquals(new double[] { 1, 0, 0 }, ratios, 0.0);
    }
}
