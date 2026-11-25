package plugins.fmp.multitools.experiment.spot.fraction;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;
import plugins.fmp.multitools.experiment.spot.measurement.SpotMeasurementMethod;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.GapPolicy;
import plugins.fmp.multitools.experiment.spot.measurement.SpotProcessingSettings.TailPolicy;

/** Independent experiment-local recipe, reference and review-list persistence. Raw data and derived values are not rewritten. */
public final class SpotFractionStore {
    public static final String FILENAME = "SpotFractionSettings.xml";
    private SpotFractionStore() { }

    public static SpotFractionPlan read(Path directory) throws IOException {
        SpotFractionPlan plan = new SpotFractionPlan(); Path file = directory.resolve(FILENAME);
        if (!Files.exists(file)) return plan;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.loadFromXML(in); }
        try {
            if (!"1".equals(required(p, "version"))) throw new IllegalArgumentException("Unsupported version");
            for (int i = 0, n = count(p, "settings"); i < n; i++) {
                String prefix = "setting." + i + "."; Key key = key(p, prefix);
                Settings s = new Settings(GapPolicy.valueOf(required(p, prefix + "similar")),
                        GapPolicy.valueOf(required(p, prefix + "changing")), TailPolicy.valueOf(required(p, prefix + "tail")),
                        number(p, prefix + "maxSeconds"), Tolerance.valueOf(required(p, prefix + "toleranceMode")),
                        number(p, prefix + "tolerance"), Integer.parseInt(required(p, prefix + "medianRadius")),
                        Illumination.valueOf(required(p, prefix + "illumination")));
                if (plan.settings.put(key, s) != null) throw new IllegalArgumentException("Duplicate settings");
            }
            for (int i = 0, n = count(p, "references"); i < n; i++) {
                String prefix = "reference." + i + "."; Key key = key(p, prefix);
                List<String> donors = new ArrayList<>();
                for (int j = 0, size = count(p, prefix + "donors"); j < size; j++) donors.add(required(p, prefix + "donor." + j));
                String approved = required(p, prefix + "approved");
                if (!approved.equals("true") && !approved.equals("false")) throw new IllegalArgumentException("Invalid approval");
                Reference ref = new Reference(ReferenceKind.valueOf(required(p, prefix + "kind")), number(p, prefix + "value"),
                        number(p, prefix + "timeSeconds"), required(p, prefix + "source"), Boolean.parseBoolean(approved),
                        required(p, prefix + "fingerprint"), donors);
                if (key.spot.isEmpty() || plan.references.put(key, ref) != null) throw new IllegalArgumentException("Invalid reference key");
            }
            for (int i = 0, n = count(p, "reviews"); i < n; i++) {
                String prefix = "review." + i + "."; plan.review.add(new Review(key(p, prefix), required(p, prefix + "reason")));
            }
            return plan;
        } catch (RuntimeException e) { throw new IOException("Invalid fraction settings: " + file + ": " + e.getMessage(), e); }
    }

    public static void write(Path directory, SpotFractionPlan plan) throws IOException {
        // Refuse to overwrite an unreadable or newer file, including when a preview was created earlier.
        read(directory);
        Properties p = new Properties(); p.setProperty("version", "1"); p.setProperty("conversion", "linear_remaining_signal_over_initial_reference");
        p.setProperty("settings", Integer.toString(plan.settings.size())); int i = 0;
        for (Map.Entry<Key, Settings> entry : plan.settings.entrySet()) {
            String prefix = "setting." + i++ + "."; putKey(p, prefix, entry.getKey()); Settings s = entry.getValue();
            put(p, prefix + "similar", s.similarGap); put(p, prefix + "changing", s.changingGap); put(p, prefix + "tail", s.tail);
            put(p, prefix + "maxSeconds", s.maximumGapSeconds); put(p, prefix + "toleranceMode", s.toleranceMode);
            put(p, prefix + "tolerance", s.tolerance); put(p, prefix + "medianRadius", s.medianRadius); put(p, prefix + "illumination", s.illumination);
        }
        p.setProperty("references", Integer.toString(plan.references.size())); i = 0;
        for (Map.Entry<Key, Reference> entry : plan.references.entrySet()) {
            String prefix = "reference." + i++ + "."; putKey(p, prefix, entry.getKey()); Reference r = entry.getValue();
            put(p, prefix + "kind", r.kind); put(p, prefix + "value", r.value); put(p, prefix + "timeSeconds", r.timeSeconds);
            put(p, prefix + "source", r.source); put(p, prefix + "approved", r.approved); put(p, prefix + "fingerprint", r.fingerprint);
            put(p, prefix + "donors", r.donors.size()); for (int j = 0; j < r.donors.size(); j++) put(p, prefix + "donor." + j, r.donors.get(j));
        }
        p.setProperty("reviews", Integer.toString(plan.review.size())); i = 0;
        for (Review review : plan.review) { String prefix = "review." + i++ + "."; putKey(p, prefix, review.key); put(p, prefix + "reason", review.reason); }
        Path target = directory.resolve(FILENAME).toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), ".spot-fraction-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { p.storeToXML(out, "Spot fraction calculation decisions; raw measurements remain unchanged", "UTF-8"); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static Key key(Properties p, String prefix) {
        return new Key(SpotMeasurementMethod.valueOf(required(p, prefix + "method")), required(p, prefix + "quantity"), required(p, prefix + "spot"));
    }
    private static void putKey(Properties p, String prefix, Key key) {
        put(p, prefix + "method", key.method); put(p, prefix + "quantity", key.quantity); put(p, prefix + "spot", key.spot);
    }
    private static void put(Properties p, String key, Object value) { p.setProperty(key, String.valueOf(value)); }
    private static double number(Properties p, String key) { return Double.parseDouble(required(p, key)); }
    private static int count(Properties p, String key) {
        int value = Integer.parseInt(required(p, key)); if (value < 0 || value > 1000000) throw new IllegalArgumentException("Invalid count"); return value;
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key); if (value == null) throw new IllegalArgumentException("Missing " + key); return value;
    }
}
