package plugins.fmp.multitools.experiment.spots;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementXml;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.tools.Logger;

/** Adds primary-data persistence alongside the unchanged legacy CSV compatibility files. */
final class SpotPrimaryMeasurementsPersistence {
    static final EnumSet<SpotMeasurementMethod> CAMERA = EnumSet.of(SpotMeasurementMethod.FILTER, SpotMeasurementMethod.COLOR);
    static final EnumSet<SpotMeasurementMethod> LINES = EnumSet.complementOf(CAMERA);
    static final EnumSet<SpotMeasurementMethod> ALL = EnumSet.allOf(SpotMeasurementMethod.class);
    private SpotPrimaryMeasurementsPersistence() { }

    static boolean save(Spots spots, String directory, EnumSet<SpotMeasurementMethod> scope) {
        if (directory == null || spots == null) { return false; }
        Path path = Paths.get(directory, SpotMeasurementXml.FILENAME);
        try {
            Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> saved = SpotMeasurementXml.read(path);
            boolean changed = false;
            Set<String> names = new HashSet<>();
            for (Spot spot : spots.getSpotList()) {
                Map<SpotMeasurementMethod, SpotMethodResult> current = spot.getMethodMeasurements().snapshot();
                if (current.keySet().stream().noneMatch(scope::contains)) { continue; }
                String name = spot.getName();
                if (!names.add(name)) { throw new IOException("Duplicate spot name: " + name); }
                for (SpotMethodResult result : current.values()) {
                    if (scope.contains(result.getMethod())) {
                        saved.computeIfAbsent(name, key -> new EnumMap<>(SpotMeasurementMethod.class))
                                .put(result.getMethod(), result);
                        changed = true;
                    }
                }
            }
            // Legacy-only saves neither create an empty sidecar nor destroy existing raw measurements.
            if (changed) { SpotMeasurementXml.write(path, saved); }
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.error("Cannot save primary spot measurements: " + path, e);
            return false;
        }
    }

    static boolean load(Spots spots, String directory, EnumSet<SpotMeasurementMethod> scope) {
        if (directory == null || spots == null) { return false; }
        Path path = Paths.get(directory, SpotMeasurementXml.FILENAME);
        try {
            // Parse and validate the complete file before replacing any in-memory primary results.
            Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> saved = SpotMeasurementXml.read(path);
            for (Spot spot : spots.getSpotList()) {
                for (SpotMeasurementMethod method : scope) { spot.getMethodMeasurements().remove(method); }
            }
            for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> entry : saved.entrySet()) {
                if (entry.getValue().keySet().stream().noneMatch(scope::contains)) { continue; }
                Spot spot = spots.findSpotByName(entry.getKey());
                if (spot == null) {
                    // Measurements must never resurrect geometry removed from a description.
                    if (spots.hasLoadedDescriptions()) { continue; }
                    spot = new Spot();
                    spot.getProperties().setName(entry.getKey());
                    spots.addSpot(spot);
                }
                for (SpotMethodResult result : entry.getValue().values()) {
                    if (scope.contains(result.getMethod())) { spot.getMethodMeasurements().put(result); }
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.error("Cannot load primary spot measurements: " + path, e);
            return false;
        }
    }
}
