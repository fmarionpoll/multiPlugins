package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import icy.image.IcyBufferedImage;
import icy.type.DataType;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.fraction.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

public class KymoRawMeasurementTest {
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule memory =
            new plugins.fmp.multitools.test.InMemoryImageRule();
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rawCaptureSurvivesSaveAndFractionCalculationWithoutFillingOrNormalizingIt() throws Exception {
        IcyBufferedImage image = new IcyBufferedImage(6, 10, 3, DataType.UBYTE);
        byte[][] rgb = { new byte[60], new byte[60], new byte[60] };
        for (byte[] channel : rgb) Arrays.fill(channel, (byte) 100);
        for (int x = 0; x < 6; x++) for (int y = 3; y < (x == 4 ? 5 : 7); y++) {
            rgb[0][y * 6 + x] = 0; rgb[1][y * 6 + x] = 50;
        }
        for (byte[] channel : rgb) channel[4 * 6 + 1] = 0; // Fly in one column.
        for (int c = 0; c < 3; c++) image.setDataXY(c, rgb[c]);
        image.dataChanged();
        CageKymoAnalyzer.Params params = new CageKymoAnalyzer.Params(ImageTransformEnums.RGB_DIFFS, 35, 0, 1,
                false, 15, true, true, true, ImageTransformEnums.B_RGB, 20, false);
        Spot spot = new Spot(); spot.getProperties().setName("A"); spot.getProperties().setCageID(1);
        SpotMethodResult raw = SpotVerticalMeasurementCapture.capture(image,
                new CageKymographSpotBands(spot, 0, 10, false), params, new double[] {0, 60, 120, 180, 240, 300});
        assertEquals(800, raw.getQuantity("color_integral").getRawValues()[0], 0);
        assertEquals(600, raw.getQuantity("color_integral").getRawValues()[1], 0);
        assertEquals(400, raw.getQuantity("color_integral").getRawValues()[4], 0);
        assertEquals(SpotSampleFlags.OCCLUDED, raw.getQuantity("color_integral").getEffectiveFlags()[1]);
        assertEquals(4, raw.getQuantity("green_height").getRawValues()[0], 0);
        assertFalse(raw.getQuantities().containsKey("signal_ratio_new"));
        assertFalse(raw.getParameters().containsKey("baseline_start_min"));
        spot.getGreenMaskHeightToPeakRatio().setValues(new double[] {1, .5});
        KymoAnalysisResult result = new KymoAnalysisResult(Collections.singletonMap(1, Collections.singletonList(
                new KymoAnalysisResult.SpotKymoSeries(spot, 0, null, null, null, null, raw))), new double[6], 6);
        KymoMeasuresCommit.apply(result, null);
        assertEquals(0, spot.getGreenMaskHeightToPeakRatio().getValues().length);
        Path directory = temporary.getRoot().toPath();
        Path file = directory.resolve(SpotMeasurementXml.FILENAME);
        plugins.fmp.multitools.experiment.spots.Spots spots = new plugins.fmp.multitools.experiment.spots.Spots();
        spots.addSpot(spot);
        assertTrue(spots.getPersistence().saveKymoMeasures(spots, directory.toString()));
        byte[] saved = Files.readAllBytes(file);
        SpotFractionPlan plan = new SpotFractionPlan();
        Settings defaults = Settings.DEFAULT;
        Key key = new Key(SpotMeasurementMethod.VERTICAL_LINE, "color_integral", "");
        plan.settings.put(key, new Settings(defaults.similarGap, defaults.changingGap, defaults.tail, defaults.maximumGapSeconds,
                defaults.toleranceMode, defaults.tolerance, defaults.medianRadius, defaults.illumination,
                ReferenceEstimation.BASELINE_MEDIAN, 0, 180));
        List<SpotFractionCalculator.Input> inputs = SpotFractionData.read(directory, directory, key.method, key.quantity);
        SpotFractionCalculator.Row fractions = SpotFractionCalculator.calculate(inputs, key, plan).get(0);
        assertEquals(800, fractions.reference.value, 0);
        assertEquals(.5, fractions.remaining()[4], 0);
        assertTrue(fractions.fractions.getEstimatedSamples()[1]);
        SpotFractionStore.write(directory, plan);
        assertArrayEquals(saved, Files.readAllBytes(file));
        assertEquals(600, inputs.get(0).result.getQuantity("color_integral").getRawValues()[1], 0);
    }

    @Test public void integralRetainsFullPrecisionBeyondEightBitDisplayRange() {
        IcyBufferedImage image = new IcyBufferedImage(1, 1, 3, DataType.UBYTE);
        image.setDataXY(0, new byte[] {0}); image.setDataXY(1, new byte[] {0}); image.setDataXY(2, new byte[] {(byte) 255});
        CageKymoAnalyzer.Params params = new CageKymoAnalyzer.Params(ImageTransformEnums.RGB_DIFFS, 35, 0, 1,
                false, 0, false, false, false, ImageTransformEnums.B_RGB, 20, false);
        assertEquals(510, SpotVerticalMeasurementCapture.integralMetric(image, params)[0], 0);
    }
}
