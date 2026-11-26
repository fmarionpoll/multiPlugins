package plugins.fmp.multitools.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.series.BuildSeries;
import plugins.fmp.multitools.tools.Logger;

/** Explicit reset for the Create cages / Create spots commands, never for ordinary saves. */
public final class GeometryMeasurementReset {
    private GeometryMeasurementReset() { }

    public static boolean beforeRecreation(Experiment exp, boolean recreateCages) {
        if (exp == null || exp.getResultsDirectory() == null) return false;
        if (BuildSeries.isBackgroundRunActive() || exp.isLoading() || exp.isSaving()) {
            Logger.warn("Cannot recreate geometry while measurements or experiment I/O are running.");
            return false;
        }
        try {
            exp.releaseKymographSequence();
            Path results = Paths.get(exp.getResultsDirectory()).toAbsolutePath().normalize();
            Path active = exp.getKymosBinFullDirectory() == null ? null
                    : Paths.get(exp.getKymosBinFullDirectory()).toAbsolutePath().normalize();
            Path backup = archive(results, active, recreateCages);
            // Remove objects as well as their files: a later close/save must not
            // publish the old primary results again under reused spot names.
            exp.getSpots().clearSpotList();
            for (Cage cage : exp.getCages().getCageList()) {
                cage.getSpotIDs().clear();
                if (recreateCages) cage.clearMeasures();
                else cage.getSpotAggregates().clear();
            }
            exp.getCages().clearSpotAggregatesCache();
            if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
                exp.getSeqCamData().removeROIsContainingString("spot");
            // Write an empty current-format description immediately, preventing
            // legacy description fallback even before the new spots are saved.
            if (!exp.getSpots().getPersistence().saveDescriptions(exp.getSpots(), results.toString())
                    || !exp.getCages().getPersistence().saveDescriptions(exp.getCages(), results.toString()))
                throw new IOException("Cannot persist reset geometry descriptions");
            exp.setLegacyExperimentFormat(false);
            Logger.info("Geometry reset: previous measurements archived in " + backup);
            return true;
        } catch (IOException | RuntimeException e) {
            Logger.error("Cannot reset measurements before geometry recreation: " + exp.getResultsDirectory(), e);
            return false;
        }
    }

    /** Only immediate result/bin directories are inspected; backups are never scanned as active data. */
    static Path archive(Path results, Path active, boolean recreateCages) throws IOException {
        Files.createDirectories(results);
        List<Path> directories = new ArrayList<>();
        directories.add(results);
        try (Stream<Path> children = Files.list(results)) {
            directories.addAll(children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("bin"))
                    .collect(Collectors.toList()));
        }
        if (active != null && Files.isDirectory(active) && !directories.contains(active)) directories.add(active);
        Path backupRoot = results.resolve("geometry-backups");
        Files.createDirectories(backupRoot);
        Path backup = Files.createTempDirectory(backupRoot, "reset-");
        List<Path[]> moved = new ArrayList<>();
        try {
            for (Path directory : directories) {
                Path destination = directory.equals(results) ? backup.resolve("results")
                        : backup.resolve("bins").resolve(Integer.toString(directories.indexOf(directory)));
                Files.createDirectories(destination);
                Files.write(destination.resolve("original-directory.txt"),
                        directory.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                List<Path> files;
                try (Stream<Path> stream = Files.list(directory)) {
                    files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
                }
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    if (name.equals("SpotsDescription.csv") || name.equals("CagesDescription.csv"))
                        Files.copy(file, destination.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
                    else if (invalidatedFile(name, recreateCages)) {
                        Path archived = destination.resolve(name);
                        Files.move(file, archived);
                        moved.add(new Path[] { file, archived });
                    }
                }
            }
        } catch (IOException e) {
            // Do not leave a partially archived active experiment on a failed move.
            for (int i = moved.size() - 1; i >= 0; i--) {
                Path[] pair = moved.get(i);
                try { Files.move(pair[1], pair[0]); }
                catch (IOException rollback) { e.addSuppressed(rollback); }
            }
            throw e;
        }
        return backup;
    }

    private static boolean invalidatedFile(String name, boolean cages) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("spotsmeasures.csv") || n.equals("spotsarraymeasures.csv")
                || n.equals("spotsarray.csv") || n.equals("spotfractionsettings.xml")
                || n.equals("xlinebackground.csv") || n.equals("cagekymographcolumntimes.csv")
                || n.startsWith("spotsprimarymeasurements") && n.endsWith(".xml")
                || n.startsWith("spotdetection-") && n.endsWith(".properties")
                || n.startsWith("kymocage_") && (n.endsWith(".tif") || n.endsWith(".tiff"))) return true;
        // Combined legacy files can restore measurements even when current CSVs are absent.
        if (n.equals("ms96_cages.xml")) return true;
        return cages && (n.equals("cagesmeasures.csv") || n.equals("cagesarraymeasures.csv")
                || n.equals("cagesarray.csv") || n.equals("mcdrosotrack.xml"));
    }
}
