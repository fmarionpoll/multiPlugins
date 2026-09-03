package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillaries.CapillariesPersistence;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** Read-only file import followed by an explicitly requested, image-0-only apply. */
public final class CapillaryGroundTruthLoader {
    private CapillaryGroundTruthLoader() { }

    public static File findFile(File directory) throws IOException {
        String[] names = { CapillariesPersistence.GROUND_TRUTH_CSV,
                "CapillariesDescription_groundtruth.csv", "CapillariesDescription_ground_truth.csv",
                "CapillariesDescription - Copy.csv" };
        for (String name : names) {
            File file = new File(directory, name);
            if (file.isFile()) return file;
        }
        throw new IOException("No ground truth CSV found in " + directory);
    }

    public static final class Preview {
        private final Map<Capillary, Line2D> matches = new LinkedHashMap<>();
        private final List<String> warnings = new ArrayList<>();
        public int count() { return matches.size(); }
        public String summary() {
            return count() + " capillary measurements matched."
                    + (warnings.isEmpty() ? "" : "\n" + String.join("\n", warnings));
        }
        /** No files, green ROIs, biological descriptors or later blue keyframes are changed. */
        public void apply() {
            for (Map.Entry<Capillary, Line2D> entry : matches.entrySet()) {
                Capillary cap = entry.getKey();
                Line2D blue = entry.getValue();
                if (cap.getPhaseGeometry().isInitialized()) cap.getPhaseGeometry().putBlue(0, blue);
                else cap.getPhaseGeometry().initialize(0, ((ROI2DLine) cap.getRoi()).getLine(), blue);
                cap.getProperties().setMeasuredEndpoints(blue.getP1(), blue.getP2());
                cap.setPixels((int) Math.round(blue.getP1().distance(blue.getP2())));
                cap.getProperties().setPixelsAutoMeasured(true);
            }
        }
    }

    public static Preview read(File file, Capillaries caps) throws IOException {
        Preview preview = new Preview();
        Map<String, Line2D> tips = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        boolean section = false, headerRead = false;
        int headerLength = 0, aliasColumns = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String row;
            while ((row = reader.readLine()) != null) {
                if (!section) {
                    if (row.startsWith("#;CAPILLARIES;")) section = true;
                    continue;
                }
                if (row.startsWith("#")) break;
                if (row.trim().isEmpty()) continue;
                if (!headerRead) {
                    if (!row.startsWith("cap_prefix;") || !(row.endsWith("cap_length_y2")
                            || row.endsWith("cap_measured_y2")))
                        throw new IOException("Unsupported ground truth coordinate header in " + file);
                    headerRead = true;
                    headerLength = row.split(";", -1).length;
                    aliasColumns = row.contains("cap_measured_x1;") && row.contains("cap_length_x1;") ? 4 : 0;
                    continue;
                }
                String[] fields = row.split(";", -1);
                String id = fields[0].trim();
                if (!seen.add(id)) throw new IOException("Duplicate capillary identifier: " + id);
                try {
                    // The native format embeds variable-length green coordinates after npoints;
                    // blue coordinates are the final four fields (including older alias headers).
                    if (fields.length < 18 || id.isEmpty()) throw new IllegalArgumentException();
                    int points = Integer.parseInt(fields[13]);
                    if (points < 0 || points > 10000
                            || fields.length != headerLength + 2 * points - aliasColumns)
                        throw new IllegalArgumentException();
                    int start = fields.length - 4;
                    double[] xy = new double[4];
                    for (int i = 0; i < 4; i++) {
                        xy[i] = Double.parseDouble(fields[start + i].trim());
                        if (!Double.isFinite(xy[i])) throw new IllegalArgumentException();
                    }
                    Line2D line = new Line2D.Double(xy[0], xy[1], xy[2], xy[3]);
                    double length = line.getP1().distance(line.getP2());
                    if (!Double.isFinite(length) || length < 0.5 || length > Integer.MAX_VALUE)
                        throw new IllegalArgumentException();
                    tips.put(id, line);
                } catch (IllegalArgumentException ex) {
                    preview.warnings.add("Invalid or missing endpoints: " + id);
                }
            }
        }
        if (!headerRead) throw new IOException("No capillary measurement section in " + file);
        Map<String, Integer> counts = new HashMap<>();
        for (Capillary cap : caps.getList()) counts.merge(cap.getKymographPrefix(), 1, Integer::sum);
        for (Capillary cap : caps.getList()) {
            String id = cap.getKymographPrefix();
            Line2D line = tips.remove(id);
            if (id == null || counts.get(id) > 1) preview.warnings.add("Ambiguous current identifier: " + id);
            else if (line == null) preview.warnings.add("No valid ground truth for: " + id);
            else if (!(cap.getRoi() instanceof ROI2DLine)) preview.warnings.add("Unsupported green ROI: " + id);
            else preview.matches.put(cap, line);
        }
        for (String id : tips.keySet()) preview.warnings.add("Reference capillary not in this experiment: " + id);
        return preview;
    }
}
