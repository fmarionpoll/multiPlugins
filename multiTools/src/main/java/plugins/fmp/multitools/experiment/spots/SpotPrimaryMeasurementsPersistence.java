package plugins.fmp.multitools.experiment.spots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementCsv;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMethodResult;
import plugins.fmp.multitools.tools.Logger;

/** One primary CSV per acquisition method, beside the unchanged legacy compatibility files. */
final class SpotPrimaryMeasurementsPersistence {
    static final EnumSet<SpotMeasurementMethod> KYMOGRAPH = EnumSet.of(SpotMeasurementMethod.VERTICAL_LINE);
    static final EnumSet<SpotMeasurementMethod> IMAGE = EnumSet.complementOf(KYMOGRAPH);
    static final EnumSet<SpotMeasurementMethod> ALL = EnumSet.allOf(SpotMeasurementMethod.class);
    private SpotPrimaryMeasurementsPersistence() { }

    static boolean save(Spots spots, String directory, EnumSet<SpotMeasurementMethod> scope) {
        if (directory == null || spots == null || scope == null) { return false; }
        boolean saved = true;
        for (SpotMeasurementMethod method : scope) {
            if (!saveMethod(spots, directory, method)) { saved = false; }
        }
        return saved;
    }

    /** Reads and writes only this method's file. Other primary files are not opened. */
    private static boolean saveMethod(Spots spots, String directory, SpotMeasurementMethod method) {
        Path path = Paths.get(directory, method.fileName());
        try {
            Map<String, SpotMethodResult> updates = new LinkedHashMap<String, SpotMethodResult>();
            Set<String> names = new HashSet<String>();
            for (Spot spot : spots.getSpotList()) {
                if (spot == null) { continue; }
                SpotMethodResult result = spot.getMethodMeasurements().get(method);
                if (result == null) { continue; }
                String name = spot.getName();
                if (!names.add(name)) { throw new IOException("Duplicate spot name: " + name); }
                updates.put(name, result);
            }
            // Legacy-only saves neither create an empty file nor destroy an existing one.
            if (updates.isEmpty()) { return true; }
            Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> stored = SpotMeasurementCsv.read(path);
            for (Map.Entry<String, SpotMethodResult> update : updates.entrySet()) {
                stored.computeIfAbsent(update.getKey(), key -> new EnumMap<>(SpotMeasurementMethod.class))
                        .put(method, update.getValue());
            }
            SpotMeasurementCsv.write(path, stored);
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.error("Cannot save primary spot measurements: " + path, e);
            return false;
        }
    }

    static boolean load(Spots spots, String directory, EnumSet<SpotMeasurementMethod> scope) {
        if (directory == null || spots == null || scope == null) { return false; }
        boolean loaded = true;
        Map<SpotMeasurementMethod, Map<String, Map<SpotMeasurementMethod, SpotMethodResult>>> parsed =
                new EnumMap<>(SpotMeasurementMethod.class);
        for (SpotMeasurementMethod method : scope) {
            Path path = Paths.get(directory, method.fileName());
            if (!Files.isRegularFile(path)) {
                parsed.put(method, Collections.<String, Map<SpotMeasurementMethod, SpotMethodResult>>emptyMap());
                continue;
            }
            try {
                parsed.put(method, SpotMeasurementCsv.read(path));
            } catch (IOException | RuntimeException e) {
                Logger.error("Cannot load primary spot measurements: " + path, e);
                loaded = false;
            }
        }
        for (Map.Entry<SpotMeasurementMethod, Map<String, Map<SpotMeasurementMethod, SpotMethodResult>>> entry
                : parsed.entrySet()) {
            apply(spots, entry.getKey(), entry.getValue());
        }
        return loaded;
    }

    /** Removes one method from its own file. An empty file is deleted. Other method files stay. */
    static boolean delete(String directory, EnumSet<SpotMeasurementMethod> methods) {
        if (directory == null || methods == null || methods.isEmpty()) { return false; }
        boolean deleted = true;
        for (SpotMeasurementMethod method : methods) {
            if (!deleteMethod(directory, method)) { deleted = false; }
        }
        return deleted;
    }

    private static boolean deleteMethod(String directory, SpotMeasurementMethod method) {
        Path path = Paths.get(directory, method.fileName());
        if (!Files.isRegularFile(path)) { return true; }
        try {
            Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> stored = SpotMeasurementCsv.read(path);
            boolean changed = false;
            Iterator<Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>>> spots = stored.entrySet().iterator();
            while (spots.hasNext()) {
                Map<SpotMeasurementMethod, SpotMethodResult> methods = spots.next().getValue();
                if (methods.remove(method) != null) { changed = true; }
                if (methods.isEmpty()) {
                    spots.remove();
                    changed = true;
                }
            }
            if (!changed) { return true; }
            if (stored.isEmpty()) {
                Files.deleteIfExists(path);
                return true;
            }
            SpotMeasurementCsv.write(path, stored);
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.error("Cannot delete primary spot measurements: " + path, e);
            return false;
        }
    }

    private static void apply(Spots spots, SpotMeasurementMethod method,
            Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> stored) {
        for (Spot spot : spots.getSpotList()) {
            if (spot != null) { spot.getMethodMeasurements().remove(method); }
        }
        for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> entry : stored.entrySet()) {
            SpotMethodResult result = entry.getValue().get(method);
            if (result == null) { continue; }
            Spot spot = spots.findSpotByName(entry.getKey());
            if (spot == null) {
                // Measurements must never resurrect geometry removed from a description.
                if (spots.hasLoadedDescriptions()) { continue; }
                spot = new Spot();
                spot.getProperties().setName(entry.getKey());
                spots.addSpot(spot);
            }
            spot.getMethodMeasurements().put(result);
        }
    }
}
