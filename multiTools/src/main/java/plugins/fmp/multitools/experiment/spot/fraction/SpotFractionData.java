package plugins.fmp.multitools.experiment.spot.fraction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spots.Spots;
import plugins.fmp.multitools.experiment.spot.measurement.*;

/** Lightweight batch input: descriptions and saved primary series only, no camera or kymograph images. */
public final class SpotFractionData {
    private SpotFractionData() { }
    public static List<SpotFractionCalculator.Input> read(Path descriptionsDirectory, Path measurementsDirectory,
            SpotMeasurementMethod method) throws IOException {
        return read(descriptionsDirectory, measurementsDirectory, method, "");
    }
    public static List<SpotFractionCalculator.Input> read(Path descriptionsDirectory, Path measurementsDirectory,
            SpotMeasurementMethod method, String quantity) throws IOException {
        Spots spots = new Spots();
        spots.getPersistence().loadDescriptions(spots, descriptionsDirectory.toString());
        String filename = method == SpotMeasurementMethod.VERTICAL_LINE && "color_integral".equals(quantity)
                ? "SpotsPrimaryMeasurementsTrial.xml" : SpotMeasurementXml.FILENAME;
        Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> primary = SpotMeasurementXml.read(measurementsDirectory.resolve(filename));
        for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> entry : primary.entrySet()) {
            SpotMethodResult result = entry.getValue().get(method); if (result == null) continue;
            Spot spot = spots.findSpotByName(entry.getKey());
            if (spot == null) { spot = new Spot(); spot.getProperties().setName(entry.getKey()); spots.addSpot(spot); }
            spot.getMethodMeasurements().put(result);
        }
        return fromSpots(spots.getSpotList(), method);
    }
    public static List<SpotFractionCalculator.Input> fromSpots(List<Spot> spots, SpotMeasurementMethod method) {
        List<SpotFractionCalculator.Input> inputs = new ArrayList<>();
        for (Spot spot : spots) inputs.add(new SpotFractionCalculator.Input(spot, method)); return inputs;
    }
}
