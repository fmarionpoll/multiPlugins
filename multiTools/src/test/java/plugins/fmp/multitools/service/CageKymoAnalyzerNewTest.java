package plugins.fmp.multitools.service;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import icy.image.IcyBufferedImage;
import icy.type.DataType;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.*;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class CageKymoAnalyzerNewTest {
    @Test public void trialPersistsOriginalIntegralAndFlyEvidenceWithoutReplacingStandardData() throws Exception {
        IcyBufferedImage image = image();
        for (int x = 0; x < 6; x++) for (int y = 3; y < 7; y++) pixel(image, x, y, 0, 50, 100);
        pixel(image, 1, 4, 0, 0, 0);
        Params p = params();
        p.insectMetricGateEnabled = true;
        p.insectMetricThreshold = 20;
        p.insectMetricThresholdUp = false;
        p.rowwiseOcclusionFill = true;
        Settings settings = new Settings(0, 5);
        Signal signal = analyze(image, p, settings);
        assertTrue(Double.isNaN(signal.observed[1]));
        assertEquals(600, signal.primary.getQuantity("color_integral").getRawValues()[1], 0);
        assertEquals(0.1, signal.primary.getFlyEvidence()[1], 0);
        assertEquals(plugins.fmp.multitools.experiment.spot.measurement.SpotSampleFlags.OCCLUDED,
                signal.primary.getQuantity("color_integral").getEffectiveFlags()[1]);
        plugins.fmp.multitools.experiment.spot.Spot spot = new plugins.fmp.multitools.experiment.spot.Spot();
        spot.getProperties().setName("trialSpot");
        Result result = new Result(new double[] { 0, 1, 2, 3, 4, 5 },
                Collections.singletonList(new Row(1, spot, signal, new double[6])), settings);
        Path directory = temporary.newFolder("primary-trial").toPath();
        KymoAnalysisCsvExport_new.write(directory, result, p);
        plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult restored =
                plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementCsv
                    .read(directory.resolve("SpotsPrimaryMeasurementsTrial.csv")).get("trialSpot")
                    .get(plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod.VERTICAL_LINE);
        assertEquals(600, restored.getQuantity("color_integral").getRawValues()[1], 0);
        assertFalse(Files.exists(directory.resolve("SpotsPrimaryMeasurements.csv")));
        assertFalse(Files.exists(directory.resolve(
                plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod.VERTICAL_LINE.fileName())));
        assertTrue(spot.getMethodMeasurements().snapshot().isEmpty());
    }
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule memory =
            new plugins.fmp.multitools.test.InMemoryImageRule();
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Params params() {
        return new Params(ImageTransformEnums.RGB_DIFFS, 35, 0, 1, false, 2, false, false,
                false, ImageTransformEnums.B_RGB, 50, false);
    }

    private IcyBufferedImage image() {
        IcyBufferedImage image = new IcyBufferedImage(6, 10, 3, DataType.UBYTE);
        for (int c = 0; c < 3; c++) {
            byte[] values = new byte[60];
            Arrays.fill(values, (byte) 100);
            image.setDataXY(c, values);
        }
        return image;
    }

    private void pixel(IcyBufferedImage image, int x, int y, int r, int g, int b) {
        int[] rgb = {r, g, b};
        for (int c = 0; c < 3; c++) ((byte[]) image.getDataXY(c))[y * 6 + x] = (byte) rgb[c];
    }

    private Signal analyze(IcyBufferedImage image, Params p, Settings baseline) {
        image.dataChanged();
        double[] insect = p.insectMetricGateEnabled ? KymoImageTransforms.channel0AsDouble(
                KymoImageTransforms.applyMetricTransform(image, p.insectMetricTransform, false)) : null;
        return CageKymoAnalyzer_new.compute(image, new CageKymographSpotBands(null, 0, 10, false),
                CageKymoAnalyzer_new.metric(image, p), insect, p, new double[] {0, 1, 2, 3, 4, 5}, baseline);
    }

    @Test public void redistributedBorderColorDoesNotBecomeConsumption() {
        IcyBufferedImage image = image();
        for (int x = 0; x < 6; x++) {
            for (int y = 3; y < 7; y++) pixel(image, x, y, 0, 50, x < 3 ? 100 : 75);
            if (x >= 3) { pixel(image, x, 2, 0, 25, 50); pixel(image, x, 7, 0, 25, 50); }
        }
        Signal signal = analyze(image, params(), new Settings(0, 2));
        assertEquals(800, signal.baseline, 0);
        assertArrayEquals(new double[] {1, 1, 1, 1, 1, 1}, signal.ratio, 0);
    }

    @Test public void realSignalLossIsRetainedAndFullRangeIsNotClipped() {
        IcyBufferedImage image = image();
        for (int x = 0; x < 6; x++) for (int y = 3; y < 7; y++)
            pixel(image, x, y, 0, 50, x < 3 ? 200 : 100);
        Signal signal = analyze(image, params(), new Settings(0, 2));
        assertEquals(1600, signal.baseline, 0); // S(diffRGB)=400, not 255, per colored pixel.
        assertEquals(0.5, signal.ratio[4], 0);
    }

    @Test public void grayIlluminationChangeDoesNotChangeColorIntegral() {
        IcyBufferedImage image = image();
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 10; y++) pixel(image, x, y, 100 + x, 100 + x, 100 + x);
            for (int y = 3; y < 7; y++) pixel(image, x, y, x, 50 + x, 100 + x);
        }
        assertArrayEquals(new double[] {1, 1, 1, 1, 1, 1}, analyze(image, params(), new Settings(0, 2)).ratio, 0);
    }

    @Test public void flyOcclusionIsMissingRatherThanAnObservedLoss() {
        IcyBufferedImage image = image();
        for (int x = 0; x < 6; x++) for (int y = 3; y < 7; y++) pixel(image, x, y, 0, 50, 100);
        pixel(image, 4, 4, 0, 0, 0);
        Params p = params(); p.insectMetricGateEnabled = true;
        Signal signal = analyze(image, p, new Settings(0, 2));
        assertTrue(Double.isNaN(signal.observed[4]));
        assertTrue(Double.isNaN(signal.ratio[4]));
        assertEquals(1, signal.ratio[3], 0);
    }

    @Test public void onlyBoundedShortGapsAreFilledUsingActualTime() {
        double n = Double.NaN;
        Signal signal = CageKymoAnalyzer_new.normalize(new double[] {n, 100, n, 50, 50, 50, n},
                new double[] {0, 1, 2, 5, 6, 7, 8}, new Settings(5, 7), 1);
        assertTrue(Double.isNaN(signal.integrated[0]));
        assertEquals(87.5, signal.integrated[2], 0);
        assertTrue(Double.isNaN(signal.observed[2]));
        assertTrue(Double.isNaN(signal.integrated[6]));
        assertEquals(3, signal.baselineSamples);
        Signal longGap = CageKymoAnalyzer_new.normalize(new double[] {100, n, n, 100, 100, 100},
                new double[] {0, 1, 2, 3, 4, 5}, new Settings(3, 5), 1);
        assertTrue(Double.isNaN(longGap.ratio[1]));
    }

    @Test public void robustBaselineDoesNotClampIncreasesOrUseThePeak() {
        Signal signal = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 1000, 120, 50},
                new double[] {0, 1, 2, 3, 4}, new Settings(0, 2), 0);
        assertEquals(100, signal.baseline, 0);
        assertEquals(1.2, signal.ratio[3], 0);
        assertEquals(0.5, signal.ratio[4], 0);
    }

    @Test public void absentOrZeroBaselineStaysMissing() {
        for (double[] input : new double[][] {{0, 0, 0}, {1, Double.NaN, 1}}) {
            Signal signal = CageKymoAnalyzer_new.normalize(input, new double[] {0, 1, 2}, new Settings(0, 2), 2);
            assertTrue(Double.isNaN(signal.baseline));
            for (double v : signal.ratio) assertTrue(Double.isNaN(v));
        }
    }

    @Test(expected = IllegalArgumentException.class) public void invalidClockCannotDefineABaseline() {
        CageKymoAnalyzer_new.normalize(new double[] {1, 1, 1}, new double[] {0, 1, 1}, new Settings(0, 2), 0);
    }

    @Test public void disappearingObservationDoesNotLowerTheCageTotal() {
        Settings settings = new Settings(0, 2);
        double[] time = {0, 1, 2, 3};
        Signal one = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, 50}, time, settings, 0);
        Signal two = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, Double.NaN}, time, settings, 0);
        Result result = new Result(time, Arrays.asList(new Row(1, null, one, new double[] {1, 1, 1, .5}),
                new Row(1, null, two, new double[] {1, 1, 1, 1})), settings);
        Group group = result.groups.get(0);
        assertTrue(Double.isNaN(group.newLoss[3]));
        assertEquals(.5, group.newObservedLoss[3], 0);
        assertEquals(1, group.newMissing[3]);
        assertTrue(group.newMembershipChanged[3]);
        assertEquals(1, group.newValid[3]);
        assertEquals(.5, group.oldLoss[3], 0);
    }

    @Test public void trialExportIncludesMissingnessAndBaselineWithoutTouchingOldMeasures() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Path old = directory.resolve("SpotsMeasures.csv");
        Files.write(old, Collections.singletonList("original"), StandardCharsets.UTF_8);
        double[] time = {0, 1, 2, 3};
        Settings settings = new Settings(0, 2);
        Signal signal = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, Double.NaN}, time, settings, 0);
        Result result = new Result(time, Collections.singletonList(new Row(1, null, signal, new double[] {1, 1, 1, .5})), settings);
        KymoAnalysisCsvExport_new.write(directory, result, params());
        assertEquals("original", Files.readAllLines(old).get(0));
        String cages = new String(Files.readAllBytes(directory.resolve("kymo_analysis_new_cages.csv")), StandardCharsets.UTF_8);
        assertTrue(cages.contains("n_valid_new"));
        assertTrue(cages.contains("signal_loss_new_observed_subtotal"));
        assertTrue(cages.contains("n_missing_new"));
        assertTrue(cages.contains("3.0,0.5,0.5,,1,0,1,1"));
        assertTrue(Files.isRegularFile(directory.resolve("kymo_analysis_new.properties")));
    }

    @Test public void invalidBaselineIsExcludedFromAFixedCohortRatherThanEveryTimeBin() {
        Settings settings = new Settings(0, 2);
        double[] time = {0, 1, 2, 3};
        Signal one = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, 50}, time, settings, 0);
        Signal two = CageKymoAnalyzer_new.normalize(new double[] {0, 0, 0, 1}, time, settings, 0);
        Result result = new Result(time, Arrays.asList(new Row(1, null, one, new double[] {1, 1, 1, .5}),
                new Row(1, null, two, new double[] {1, 1, 1, 1})), settings);
        Group group = result.groups.get(0);
        assertEquals(2, group.expectedSpots);
        assertEquals(1, group.baselineSpots);
        assertEquals(.5, group.newLoss[3], 0);
        assertEquals(.5, group.oldMatchedLoss[3], 0);
    }

    @Test public void observedSubtotalTracksMissingMembersIncludingEqualCountSwaps() {
        Settings settings = new Settings(0, 2);
        double n = Double.NaN;
        double[] time = {0, 1, 2, 3, 4, 5, 6};
        Signal a = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, 50, n, n, 120}, time, settings, 0);
        Signal b = CageKymoAnalyzer_new.normalize(new double[] {100, 100, 100, n, 100, n, 100}, time, settings, 0);
        double[] old = {1, 1, 1, .5, .5, .5, .5};
        Group group = new Result(time, Arrays.asList(new Row(1, null, a, old), new Row(1, null, b, old)), settings).groups.get(0);
        assertEquals(.5, group.newObservedLoss[3], 0);
        assertEquals(0, group.newObservedLoss[4], 0);
        assertEquals(1, group.newValid[3]);
        assertEquals(1, group.newValid[4]);
        assertTrue(group.newMembershipChanged[4]);
        assertTrue(Double.isNaN(group.newObservedLoss[5]));
        assertEquals(2, group.newMissing[5]);
        assertEquals(-.2, group.newObservedLoss[6], 1e-12);
        assertEquals(-.2, group.newLoss[6], 1e-12);
    }
}
