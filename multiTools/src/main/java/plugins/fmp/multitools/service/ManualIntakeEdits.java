package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.List;

/** Explicit user geometry, applied after automatic detection without quality gates. */
public final class ManualIntakeEdits {
    private static final String KEY = ",manual_intakes=";
    private final List<double[]> regions = new ArrayList<>();

    /** Replace the selected bins by one unresolved event at the last bin.
     * A zero amplitude explicitly removes intake in that region.
     */
    public void replace(int first, int last, double amplitude, double bottom) {
        if (first < 1 || last < first || !Double.isFinite(amplitude) || amplitude < 0
                || !Double.isFinite(bottom)) throw new IllegalArgumentException("Invalid manual intake geometry");
        regions.add(new double[] { first, last, amplitude, bottom });
    }

    public void apply(double[] amplitudes, double[] bottoms) {
        for (double[] region : regions) {
            int first = (int) region[0], last = (int) region[1];
            if (last >= amplitudes.length) continue;
            for (int i = first; i <= last; i++) amplitudes[i] = 0;
            amplitudes[last] = region[2];
            if (bottoms != null) bottoms[last] = region[3];
        }
    }

    public boolean isEmpty() { return regions.isEmpty(); }

    public boolean covers(int frame) {
        for (double[] region : regions)
            if (frame >= region[0] && frame <= region[1]) return true;
        return false;
    }

    /** Numeric-only metadata travels with the existing correlation measures. */
    public String options() {
        if (regions.isEmpty()) return "";
        StringBuilder text = new StringBuilder(KEY);
        for (double[] r : regions) {
            if (text.length() > KEY.length()) text.append('|');
            text.append((int) r[0]).append(':').append((int) r[1]).append(':')
                    .append(r[2]).append(':').append(r[3]);
        }
        return text.toString();
    }

    public static ManualIntakeEdits fromOptions(String options, int samples) {
        ManualIntakeEdits edits = new ManualIntakeEdits();
        int start = options.indexOf(KEY);
        if (start < 0) return edits;
        String encoded = options.substring(start + KEY.length()).split(",", 2)[0];
        for (String region : encoded.split("\\|")) {
            String[] fields = region.split(":");
            if (fields.length != 4) continue;
            try {
                int first = Integer.parseInt(fields[0]), last = Integer.parseInt(fields[1]);
                if (last < samples) edits.replace(first, last, Double.parseDouble(fields[2]), Double.parseDouble(fields[3]));
            } catch (IllegalArgumentException ignored) { /* Ignore malformed legacy metadata. */ }
        }
        return edits;
    }
}
