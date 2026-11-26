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
        if (!method.isKymographDerived()) measurementsDirectory = descriptionsDirectory;
        spots.getPersistence().loadDescriptions(spots, descriptionsDirectory.toString());
        Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> primary = SpotMeasurementCsv.read(
                measurementsDirectory.resolve(method.fileName()));
        // Trial captures stay in their own file. They fill a missing vertical color integral in memory only.
        if (method == SpotMeasurementMethod.VERTICAL_LINE && "color_integral".equals(quantity)) {
            boolean needsTrial = primary.isEmpty() || primary.values().stream().anyMatch(methods ->
                    !methods.containsKey(method) || !methods.get(method).getQuantities().containsKey(quantity));
            if (needsTrial) {
                Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> trial = SpotMeasurementCsv.read(
                        measurementsDirectory.resolve("SpotsPrimaryMeasurementsTrial.csv"));
                for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> entry : trial.entrySet()) {
                    Map<SpotMeasurementMethod, SpotMethodResult> methods = primary.computeIfAbsent(entry.getKey(),
                            key -> new EnumMap<>(SpotMeasurementMethod.class));
                    SpotMethodResult result = methods.get(method), fallback = entry.getValue().get(method);
                    if ((result == null || !result.getQuantities().containsKey(quantity)) && fallback != null)
                        methods.put(method, fallback);
                }
            }
        }
        for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> entry : primary.entrySet()) {
            SpotMethodResult result = entry.getValue().get(method); if (result == null) continue;
            Spot spot = spots.findSpotByName(entry.getKey());
            if (spot == null) {
                if (spots.hasLoadedDescriptions()) continue;
                spot = new Spot(); spot.getProperties().setName(entry.getKey()); spots.addSpot(spot);
            }
            spot.getMethodMeasurements().put(result);
        }
        return fromSpots(spots.getSpotList(), method);
    }
    public static List<SpotFractionCalculator.Input> fromSpots(List<Spot> spots, SpotMeasurementMethod method) {
        List<SpotFractionCalculator.Input> inputs = new ArrayList<>();
        for (Spot spot : spots) inputs.add(new SpotFractionCalculator.Input(spot, method)); return inputs;
    }
}
