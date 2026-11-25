package plugins.fmp.multitools.experiment.spot.fraction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.experiment.spot.measurement.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;

/** Shared calculation used by the common panel and available to future exports. Does not read images or edit primary measurements. */
public final class SpotFractionCalculator {
    private SpotFractionCalculator() { }

    public static final class Input {
        public final String name, stimulus, concentration;
        public final int cage;
        public final boolean preconsumed;
        public final SpotMethodResult result;
        public Input(Spot spot, SpotMeasurementMethod method) {
            name = spot.getName(); cage = spot.getProperties().getCageID();
            stimulus = spot.getProperties().getStimulus(); concentration = spot.getProperties().getConcentration();
            preconsumed = spot.isConsumedBeforeRecording(); result = spot.getMethodMeasurements().get(method);
        }
    }

    public static final class Row {
        public final Input input;
        public final Key key;
        public final Reference reference;
        public final SpotDerivedSeries fractions;
        private final double[] corrected, factors;
        private final int[] support;
        public final List<String> issues;
        public final boolean excluded;
        public final int markedChanges;
        Row(Input input, Key key, Reference reference, SpotDerivedSeries fractions, double[] corrected,
                double[] factors, int[] support, List<String> issues, boolean excluded, int markedChanges) {
            this.input = input; this.key = key; this.reference = reference; this.fractions = fractions;
            this.corrected = corrected.clone(); this.factors = factors.clone(); this.support = support.clone();
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues)); this.excluded = excluded; this.markedChanges = markedChanges;
        }
        public double[] corrected() { return corrected.clone(); }
        public double[] factors() { return factors.clone(); }
        public int[] support() { return support.clone(); }
        public double[] remaining() {
            if (fractions != null && !excluded) return fractions.getValues();
            double[] a = new double[corrected.length]; Arrays.fill(a, Double.NaN); return a;
        }
        public double[] consumed() {
            double[] a = remaining(); for (int i = 0; i < a.length; i++) a[i] = 1 - a[i]; return a;
        }
    }

    /** Replaces only this method/quantity's review items and records newly established initial references. */
    public static List<Row> calculate(List<Input> inputs, Key selection, SpotFractionPlan plan) {
        plan.review.removeIf(r -> r.key.method == selection.method && r.key.quantity.equals(selection.quantity));
        List<Input> available = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Input in : inputs) {
            if (!names.add(in.name)) throw new IllegalArgumentException("Duplicate spot name: " + in.name);
            if (in.result != null && in.result.getQuantities().containsKey(selection.quantity)) available.add(in);
            else {
                Reference ref = plan.references.get(selection.forSpot(in.name));
                if (ref == null || ref.kind != ReferenceKind.FAILED_DEPOSIT)
                    plan.review.add(new Review(selection.forSpot(in.name), "Primary measurements unavailable; collect measurements again"));
            }
        }
        if (inputs.isEmpty()) plan.review.add(new Review(selection.forSpot(""), "No spots or primary measurements available"));
        Map<String, Input> byName = new LinkedHashMap<>(); for (Input in : available) byName.put(in.name, in);
        // Establish visible initial references first so donor selection does not depend on spot order.
        for (Input in : available) {
            Key key = selection.forSpot(in.name);
            if (!plan.references.containsKey(key) && !in.preconsumed) {
                double[] raw = in.result.getQuantity(selection.quantity).getRawValues();
                int[] flags = in.result.getQuantity(selection.quantity).getEffectiveFlags();
                int first = firstPositive(raw, flags);
                if (first >= 0) {
                    plan.references.put(key, reference(in, selection.quantity, first == 0 ? ReferenceKind.INITIAL : ReferenceKind.DELAYED,
                            raw[first], in.result.getTimesSeconds()[first], first == 0 ? "Initial valid observation" : "First valid observation (delayed)",
                            first == 0, Collections.emptyList(), byName));
                }
            }
        }
        for (Input in : available) {
            Key key = selection.forSpot(in.name);
            if (!plan.references.containsKey(key) && in.preconsumed) {
                List<String> donors = new ArrayList<>();
                for (Input donor : available) {
                    Reference r = plan.references.get(selection.forSpot(donor.name));
                    if (in.cage >= 0 && !donor.name.equals(in.name) && !donor.preconsumed && donor.cage == in.cage
                            && Objects.equals(donor.stimulus, in.stimulus) && Objects.equals(donor.concentration, in.concentration)
                            && r != null && r.kind == ReferenceKind.INITIAL && r.approved && matches(r, donor, selection.quantity, byName)) donors.add(donor.name);
                }
                if (!donors.isEmpty()) plan.references.put(key, borrow(in, selection, donors, plan, byName, true));
            }
        }
        List<Row> rows = new ArrayList<>();
        Map<String, Boolean> unchanged = new HashMap<>();
        for (Input in : available) {
            Reference ref = plan.references.get(selection.forSpot(in.name));
            unchanged.put(in.name, ref != null && matches(ref, in, selection.quantity, byName));
        }
        Map<DriftKey, Drift> driftCache = new HashMap<>();
        for (Input in : available) {
            Key key = selection.forSpot(in.name);
            Settings settings = plan.settingsFor(key);
            Reference ref = plan.references.get(key);
            List<String> issues = new ArrayList<>();
            boolean excluded = ref != null && ref.kind == ReferenceKind.FAILED_DEPOSIT;
            boolean matched = Boolean.TRUE.equals(unchanged.get(in.name));
            if (!excluded) {
                if (ref == null) issues.add(in.preconsumed ? "Pre-consumed spot needs a comparable initial reference" : "Initial spot absent or unmeasurable: identify failed deposit, pre-consumption or another reference");
                else if (!matched) issues.add("Primary measurements or donor measurements changed; reapprove the initial reference");
                else if (!ref.approved) issues.add("Initial reference is delayed or provisional; review required");
            }
            double[] values = in.result.getQuantity(selection.quantity).getRawValues();
            int[] flags = in.result.getQuantity(selection.quantity).getEffectiveFlags();
            double[] factors = new double[values.length]; Arrays.fill(factors, 1);
            int[] support = new int[values.length];
            if (settings.illumination != Illumination.NONE && !excluded) {
                DriftKey driftKey = new DriftKey(settings.illumination, settings.illumination == Illumination.CAGE ? in.cage : -1,
                        in.result.getTimesSeconds());
                Drift drift = driftCache.get(driftKey);
                if (drift == null) { drift = drift(in, available, selection, plan, unchanged, settings.illumination); driftCache.put(driftKey, drift); }
                factors = drift.factors; support = drift.support;
                boolean warned = false;
                for (int t = 0; t < values.length; t++) {
                    if (!Double.isFinite(factors[t])) {
                        values[t] = Double.NaN; flags[t] |= SpotSampleFlags.INVALID_BACKGROUND;
                        if (!warned) { issues.add("Illumination correction lacks sufficient stable, aligned spots from sample " + t); warned = true; }
                    } else values[t] /= factors[t];
                }
            }
            double tolerance = 0;
            if (settings.toleranceMode == Tolerance.ABSOLUTE) tolerance = settings.tolerance;
            else if (settings.toleranceMode == Tolerance.PERCENT_REFERENCE && ref != null) tolerance = ref.value * settings.tolerance / 100;
            else if (settings.toleranceMode == Tolerance.ROBUST_NOISE) {
                tolerance = noise(values, flags);
                if (!Double.isFinite(tolerance)) { tolerance = 0; issues.add("Too few valid adjacent measurements to estimate noise"); }
            }
            SpotDerivedSeries derived = null;
            int changes = 0;
            if (ref != null && !excluded) {
                double normalizedReference = ref.value;
                if (settings.illumination != Illumination.NONE) {
                    double[] clock = in.result.getTimesSeconds();
                    int referenceSample = Arrays.binarySearch(clock, ref.timeSeconds);
                    if (referenceSample >= 0) {
                        if (Double.isFinite(factors[referenceSample])) normalizedReference /= factors[referenceSample];
                        else { normalizedReference = Double.NaN; issues.add("Illumination correction is unavailable at the reference time"); }
                    } else if (clock.length > 0 && ref.timeSeconds >= clock[0] && ref.timeSeconds <= clock[clock.length - 1]) {
                        normalizedReference = Double.NaN; issues.add("Select a sampled reference time before applying illumination correction");
                    }
                }
                if (Double.isFinite(normalizedReference) && normalizedReference > 0) {
                    SpotMethodResult corrected = new SpotMethodResult(in.result.getMethod(), in.result.getTimesSeconds(),
                            in.result.getFlyEvidence(), in.result.getFlyEvidenceUnit(), in.result.getParameters(),
                            Collections.singletonList(new SpotMeasuredQuantity(selection.quantity,
                                    in.result.getQuantity(selection.quantity).getUnit(), values, flags)));
                    SpotProcessingSettings processing = new SpotProcessingSettings(settings.changingGap, settings.similarGap, settings.tail,
                            settings.maximumGapSeconds, settings.toleranceMode == Tolerance.PERCENT_REFERENCE
                                    ? normalizedReference * settings.tolerance / 100 : tolerance, settings.medianRadius, SpotProcessingSettings.BaselinePolicy.EXPLICIT,
                            normalizedReference, ref.timeSeconds, ref.source, ref.approved && matched);
                    derived = SpotSeriesProcessor.process(corrected, selection.quantity, processing);
                    if (derived.reviewRequired && ref.approved && matched && issues.isEmpty()) issues.add("No usable observations remain for this reference");
                    for (SpotDerivedSeries.Gap gap : derived.getGaps()) {
                        if (gap.kind == SpotDerivedSeries.GapKind.INTERNAL && !gap.connectableForDisplay) changes++;
                        if (gap.kind == SpotDerivedSeries.GapKind.TRAILING && settings.tail == SpotProcessingSettings.TailPolicy.REVIEW)
                            issues.add("Final occlusion: choose missing values or an explicit held estimate");
                    }
                }
            }
            for (String issue : issues) plan.review.add(new Review(key, issue));
            rows.add(new Row(in, key, ref, derived, values, factors, support, issues, excluded, changes));
        }
        return rows;
    }

    public static Reference reference(Input input, String quantity, ReferenceKind kind, double value, double time,
            String source, boolean approved, List<String> donors, Map<String, Input> inputs) {
        return new Reference(kind, value, time, source, approved, fingerprint(input, quantity, donors, inputs), donors);
    }

    public static Reference borrow(Input target, Key selection, List<String> donors, SpotFractionPlan plan,
            Map<String, Input> inputs, boolean preconsumed) {
        if (donors.isEmpty() || donors.contains(target.name)) throw new IllegalArgumentException("Choose other comparable donor spots");
        if (target.cage < 0) throw new IllegalArgumentException("Assign a cage before borrowing a reference");
        double[] values = new double[donors.size()];
        for (int i = 0; i < values.length; i++) {
            Reference ref = plan.references.get(selection.forSpot(donors.get(i)));
            Input donor = inputs.get(donors.get(i));
            if (donor == null || donor.cage != target.cage || ref == null || !ref.approved
                    || ref.kind != ReferenceKind.INITIAL || !matches(ref, donor, selection.quantity, inputs))
                throw new IllegalArgumentException("Donors need approved, unchanged initial observations in the same cage");
            values[i] = ref.value;
        }
        return reference(target, selection.quantity, preconsumed ? ReferenceKind.PRECONSUMED : ReferenceKind.BORROWED,
                median(values), target.result.getTimesSeconds().length == 0 ? 0 : target.result.getTimesSeconds()[0],
                "Median initial reference from " + String.join(", ", donors), true, donors, inputs);
    }

    private static int firstPositive(double[] values, int[] flags) {
        // The first clear view may show an empty spot. A later deposit must not become its initial reference.
        for (int i = 0; i < values.length; i++) if (flags[i] == 0 && Double.isFinite(values[i])) return values[i] > 0 ? i : -1;
        return -1;
    }

    private static final class Drift {
        final double[] factors; final int[] support;
        Drift(int n) { factors = new double[n]; support = new int[n]; Arrays.fill(factors, Double.NaN); if (n > 0) factors[0] = 1; }
    }
    private static final class DriftKey {
        final Illumination scope; final int cage; final double[] times;
        DriftKey(Illumination scope, int cage, double[] times) { this.scope = scope; this.cage = cage; this.times = times; }
        @Override public boolean equals(Object o) { if (!(o instanceof DriftKey)) return false; DriftKey k = (DriftKey) o;
            return scope == k.scope && cage == k.cage && Arrays.equals(times, k.times); }
        @Override public int hashCode() { return Objects.hash(scope, cage, Arrays.hashCode(times)); }
    }

    private static Drift drift(Input target, List<Input> all, Key selection, SpotFractionPlan plan,
            Map<String, Boolean> unchanged, Illumination scope) {
        double[] clock = target.result.getTimesSeconds();
        Drift out = new Drift(clock.length);
        List<double[]> signals = new ArrayList<>(); List<int[]> flags = new ArrayList<>();
        Set<Integer> cages = new HashSet<>();
        for (Input in : all) {
            if (scope == Illumination.CAGE && (target.cage < 0 || in.cage != target.cage)) continue;
            Reference ref = plan.references.get(selection.forSpot(in.name));
            if (in.cage < 0 || in.preconsumed || ref == null || !ref.approved || ref.kind == ReferenceKind.PRECONSUMED
                    || ref.kind == ReferenceKind.FAILED_DEPOSIT || !Boolean.TRUE.equals(unchanged.get(in.name))
                    || !Arrays.equals(clock, in.result.getTimesSeconds())) continue;
            double[] raw = in.result.getQuantity(selection.quantity).getRawValues();
            for (int i = 0; i < raw.length; i++) raw[i] /= ref.value;
            signals.add(raw); flags.add(in.result.getQuantity(selection.quantity).getEffectiveFlags()); cages.add(in.cage);
        }
        int minimum = 4 * Math.max(1, cages.size()) + 1; // Strict majority even if two spots per cage change.
        for (int t = 1; t < clock.length; t++) {
            double[] ratios = new double[signals.size()]; int n = 0;
            for (int s = 0; s < signals.size(); s++) {
                double[] raw = signals.get(s); int[] f = flags.get(s);
                if (f[t] == 0 && f[t - 1] == 0 && Double.isFinite(raw[t]) && Double.isFinite(raw[t - 1])
                        && raw[t - 1] > .05 && raw[t] > 0) ratios[n++] = raw[t] / raw[t - 1];
            }
            out.support[t] = n;
            if (n >= minimum && Double.isFinite(out.factors[t - 1])) out.factors[t] = out.factors[t - 1] * median(Arrays.copyOf(ratios, n));
        }
        return out;
    }

    private static double noise(double[] values, int[] flags) {
        double[] diff = new double[values.length]; int n = 0;
        for (int i = 1; i < values.length; i++) if (flags[i] == 0 && flags[i - 1] == 0
                && Double.isFinite(values[i]) && Double.isFinite(values[i - 1])) diff[n++] = values[i] - values[i - 1];
        if (n < 5) return Double.NaN;
        diff = Arrays.copyOf(diff, n); double middle = median(diff);
        for (int i = 0; i < n; i++) diff[i] = Math.abs(diff[i] - middle);
        return 3 * 1.4826 * median(diff) / Math.sqrt(2);
    }
    static double median(double[] values) {
        double[] sorted = values.clone(); Arrays.sort(sorted); int m = sorted.length / 2;
        return sorted.length % 2 == 0 ? sorted[m - 1] / 2 + sorted[m] / 2 : sorted[m];
    }
    private static boolean matches(Reference ref, Input in, String quantity, Map<String, Input> all) {
        if (ref.kind == ReferenceKind.INITIAL || ref.kind == ReferenceKind.DELAYED) {
            int sample = Arrays.binarySearch(in.result.getTimesSeconds(), ref.timeSeconds);
            if (sample < 0 || in.result.getQuantity(quantity).getEffectiveFlags()[sample] != 0
                    || in.result.getQuantity(quantity).getRawValues()[sample] != ref.value
                    || (ref.kind == ReferenceKind.INITIAL && sample != 0)) return false;
        }
        return ref.fingerprint.equals(fingerprint(in, quantity, ref.donors, all));
    }
    private static String fingerprint(Input input, String quantity, List<String> donors, Map<String, Input> all) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            update(hash, input, quantity);
            for (String name : donors) {
                hash.update(name.getBytes(StandardCharsets.UTF_8)); Input donor = all.get(name);
                if (donor == null) hash.update((byte) 0); else update(hash, donor, quantity);
            }
            StringBuilder text = new StringBuilder(); for (byte b : hash.digest()) text.append(String.format(Locale.ROOT, "%02x", b & 255)); return text.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void update(MessageDigest hash, Input in, String quantity) {
        hash.update((in.name + "\n" + in.cage + "\n" + in.preconsumed + "\n" + in.stimulus + "\n" + in.concentration
                + "\n" + in.result.getMethod() + "\n" + quantity + "\n"
                + in.result.getQuantity(quantity).getUnit() + new TreeMap<>(in.result.getParameters())).getBytes(StandardCharsets.UTF_8));
        ByteBuffer bytes = ByteBuffer.allocate(8);
        for (double[] array : new double[][] { in.result.getTimesSeconds(), in.result.getQuantity(quantity).getRawValues() })
            for (double v : array) { bytes.clear(); bytes.putDouble(v); hash.update(bytes.array()); }
        for (int flag : in.result.getQuantity(quantity).getEffectiveFlags()) { bytes.clear(); bytes.putLong(flag); hash.update(bytes.array()); }
    }
}
