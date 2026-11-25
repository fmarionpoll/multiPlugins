package plugins.fmp.multitools.experiment.spot.fraction;

import java.util.*;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Input;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionCalculator.Row;
import plugins.fmp.multitools.experiment.spot.fraction.SpotFractionPlan.*;

/** Aggregates calculated fractions with fixed membership and exact native-time alignment. */
public final class SpotFractionAggregation {
    public enum Measure { SUM_CONSUMED, SUM_REMAINING, MEAN_CONSUMED, MEAN_REMAINING }
    public static final class Group {
        public final int cage, expectedSpots;
        public final String stimulus, concentration;
        private final double[] times, remaining, consumed;
        private final int[] available;
        private final boolean[] estimated;
        Group(int cage, String stimulus, String concentration, int expectedSpots, double[] times,
                double[] remaining, double[] consumed, int[] available, boolean[] estimated) {
            this.cage = cage; this.stimulus = stimulus; this.concentration = concentration; this.expectedSpots = expectedSpots;
            this.times = times.clone(); this.remaining = remaining.clone(); this.consumed = consumed.clone();
            this.available = available.clone(); this.estimated = estimated.clone();
        }
        public double[] timesSeconds() { return times.clone(); }
        public int[] availableSpots() { return available.clone(); }
        public boolean[] estimatedSamples() { return estimated.clone(); }
        public double[] values(Measure measure) {
            double[] values = (measure == Measure.SUM_CONSUMED || measure == Measure.MEAN_CONSUMED ? consumed : remaining).clone();
            if (measure == Measure.MEAN_CONSUMED || measure == Measure.MEAN_REMAINING)
                for (int i = 0; i < values.length; i++) values[i] /= expectedSpots;
            return values;
        }
        @Override public String toString() { return "Cage " + cage + " — " + stimulus + " / " + concentration + " (" + expectedSpots + " spots)"; }
    }
    private SpotFractionAggregation() { }

    public static List<Group> build(List<Input> inputs, List<Row> rows, Key selection, SpotFractionPlan plan) {
        Map<List<Object>, List<Input>> members = new LinkedHashMap<>();
        Map<String, Row> byName = new HashMap<>(); for (Row row : rows) byName.put(row.input.name, row);
        for (Input in : inputs) {
            Reference ref = plan.references.get(selection.forSpot(in.name));
            if (in.cage < 0 || (ref != null && ref.kind == ReferenceKind.FAILED_DEPOSIT)) continue;
            members.computeIfAbsent(Arrays.asList(in.cage, in.stimulus, in.concentration), key -> new ArrayList<>()).add(in);
        }
        List<Group> groups = new ArrayList<>();
        for (List<Input> group : members.values()) {
            TreeSet<Double> union = new TreeSet<>();
            for (Input in : group) if (in.result != null) for (double t : in.result.getTimesSeconds()) union.add(t);
            double[] times = new double[union.size()]; int index = 0; for (double t : union) times[index++] = t;
            double[] remaining = new double[times.length], consumed = new double[times.length];
            Arrays.fill(remaining, Double.NaN); Arrays.fill(consumed, Double.NaN);
            int[] available = new int[times.length]; boolean[] estimated = new boolean[times.length];
            // Snapshot series once; missing or provisional members remain in the denominator.
            Map<String, double[]> clocks = new HashMap<>(), values = new HashMap<>();
            Map<String, boolean[]> estimates = new HashMap<>();
            for (Input in : group) {
                Row row = byName.get(in.name);
                if (row == null || row.fractions == null || row.fractions.reviewRequired || row.excluded) continue;
                clocks.put(in.name, in.result.getTimesSeconds()); values.put(in.name, row.remaining());
                boolean[] flags = row.fractions.getEstimatedSamples();
                if (row.reference.kind == ReferenceKind.BORROWED || row.reference.kind == ReferenceKind.PRECONSUMED) Arrays.fill(flags, true);
                estimates.put(in.name, flags);
            }
            for (int t = 0; t < times.length; t++) {
                double sum = 0;
                for (Input in : group) {
                    double[] clock = clocks.get(in.name); if (clock == null) continue;
                    int sample = Arrays.binarySearch(clock, times[t]);
                    if (sample < 0 || !Double.isFinite(values.get(in.name)[sample])) continue;
                    sum += values.get(in.name)[sample]; available[t]++; estimated[t] |= estimates.get(in.name)[sample];
                }
                if (available[t] == group.size()) { remaining[t] = sum; consumed[t] = group.size() - sum; }
            }
            Input first = group.get(0); groups.add(new Group(first.cage, first.stimulus, first.concentration, group.size(), times,
                    remaining, consumed, available, estimated));
        }
        groups.sort(Comparator.comparingInt((Group group) -> group.cage).thenComparing(g -> String.valueOf(g.stimulus))
                .thenComparing(g -> String.valueOf(g.concentration)));
        return Collections.unmodifiableList(groups);
    }
}
