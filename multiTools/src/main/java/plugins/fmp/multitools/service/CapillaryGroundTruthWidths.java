package plugins.fmp.multitools.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import plugins.fmp.multitools.service.CapillaryGroundTruthLoader.CsvLayout;

/** Adds missing image-zero widths to an existing reference without replacing its manual endpoints. */
public final class CapillaryGroundTruthWidths {
    private CapillaryGroundTruthWidths() { }

    /** Never creates a ground truth file, and never replaces a valid saved width. */
    public static int fillMissing(String resultsDirectory, CapillaryLengthResult result) throws IOException {
        if (resultsDirectory == null || result == null || result.hasError())
            return 0;
        File reference;
        try {
            reference = CapillaryGroundTruthLoader.findFile(new File(resultsDirectory));
        } catch (IOException absent) {
            return 0;
        }
        Map<String, Double> detected = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (CapillaryLengthResult.Measure measure : result.getMeasures()) {
            if (measure.getFrameIndex() != 0 || measure.getCapillary() == null
                    || !measure.getStatus().isUsable() || !measure.hasDetectedEndpoints()
                    || !Double.isFinite(measure.getWidthPixels()) || measure.getWidthPixels() <= 0.)
                continue;
            String id = measure.getCapillary().getKymographPrefix();
            if (id == null || id.trim().isEmpty())
                continue;
            if (detected.put(id, measure.getWidthPixels()) != null)
                ambiguous.add(id);
        }
        for (String id : ambiguous)
            detected.remove(id);
        if (detected.isEmpty())
            return 0;

        Path target = reference.toPath();
        byte[] original = Files.readAllBytes(target);
        // Match the existing FileWriter/FileReader CSV encoding and retain every line ending.
        Charset encoding = Charset.defaultCharset();
        String contents = new String(original, encoding);
        if (!Arrays.equals(original, contents.getBytes(encoding)))
            throw new IOException("Ground truth encoding cannot be preserved: " + target);
        List<String> lines = new ArrayList<>(Arrays.asList(contents.split("(?<=\n)", -1)));
        boolean section = false;
        int header = -1, updated = 0;
        CsvLayout layout = null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i), row = body(raw);
            if (!section) {
                section = row.startsWith("#;CAPILLARIES;");
                continue;
            }
            if (row.startsWith("#"))
                break;
            if (row.trim().isEmpty())
                continue;
            if (layout == null) {
                header = i;
                layout = new CsvLayout(row);
                continue;
            }
            String[] fields = row.split(";", -1);
            String id = fields[0].trim();
            if (!seen.add(id))
                throw new IOException("Duplicate ground truth identifier: " + id);
            try {
                int widthIndex = layout.fieldIndex(layout.widthColumn >= 0
                        ? layout.widthColumn : layout.firstEndpointsColumn, fields);
                double existing = layout.width(fields);
                Double width = detected.get(id);
                if (width != null && !Double.isFinite(existing)) {
                    try {
                        layout.endpoints(fields);
                    } catch (IllegalArgumentException invalidEndpoints) {
                        width = null;
                    }
                } else
                    width = null;
                if (layout.widthColumn < 0) {
                    lines.set(i, insert(fields, widthIndex, width == null ? "" : Double.toString(width))
                            + raw.substring(row.length()));
                } else if (width != null) {
                    fields[widthIndex] = Double.toString(width);
                    lines.set(i, String.join(";", fields) + raw.substring(row.length()));
                }
                if (width != null)
                    updated++;
            } catch (IllegalArgumentException invalidRow) {
                throw new IOException("Cannot safely add widths to ground truth row " + id, invalidRow);
            }
        }
        if (layout == null)
            throw new IOException("No ground truth capillary table in " + target);
        if (updated == 0)
            return 0;
        if (layout.widthColumn < 0) {
            String raw = lines.get(header), row = body(raw);
            lines.set(header, insert(row.split(";", -1), layout.firstEndpointsColumn, CsvLayout.WIDTH_COLUMN)
                    + raw.substring(row.length()));
        }
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "capillary-width-", ".tmp");
        try {
            Files.write(temporary, String.join("", lines).getBytes(encoding));
            if (!Arrays.equals(original, Files.readAllBytes(target)))
                throw new IOException("Ground truth changed during width update: " + target);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return updated;
    }

    private static String body(String line) {
        return line.endsWith("\r\n") ? line.substring(0, line.length() - 2)
                : line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }

    private static String insert(String[] fields, int index, String value) {
        List<String> row = new ArrayList<>(Arrays.asList(fields));
        row.add(index, value);
        return String.join(";", row);
    }
}
