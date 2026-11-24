package plugins.fmp.multitools.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.cage.CageSpotStimulusAggregation.StimulusConcKey;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;

/**
 * Experimental strip integral, kept separate from the stored height measurements.
 * No spot threshold, peak normalization, cage-wide subtraction, or RGB row lift
 * is applied to this signal. Insect-contaminated columns are missing observations.
 */
public final class CageKymoAnalyzer_new {
    private CageKymoAnalyzer_new() { }

    public static final class Settings {
        public final double baselineStartMinutes;
        public final double baselineEndMinutes;

        public Settings(double start, double end) {
            if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start)
                throw new IllegalArgumentException("Baseline end must be later than its nonnegative start.");
            baselineStartMinutes = start;
            baselineEndMinutes = end;
        }
    }

    public static final class Signal {
        /** Observed integral before any temporal interpolation. */
        public final double[] observed;
        public final double[] integrated;
        public final double[] ratio;
        public final double baseline;
        public final int baselineSamples;

        Signal(double[] observed, double[] integrated, double[] ratio, double baseline, int samples) {
            this.observed = observed;
            this.integrated = integrated;
            this.ratio = ratio;
            this.baseline = baseline;
            baselineSamples = samples;
        }
    }

    public static final class Row {
        public final int cageId;
        public final Spot spot;
        public final Signal signal;
        public final double[] oldHeightRatio;

        public Row(int cageId, Spot spot, Signal signal, double[] oldHeightRatio) {
            this.cageId = cageId;
            this.spot = spot;
            this.signal = signal;
            this.oldHeightRatio = oldHeightRatio;
        }
    }

    public static final class Group {
        public final int cageId;
        public final StimulusConcKey key;
        public final int expectedSpots;
        public final int baselineSpots;
        public final double[] oldLoss;
        public final double[] oldMatchedLoss;
        public final double[] newLoss;
        /** Subtotal over available members; not an estimate of the missing members. */
        public final double[] newObservedLoss;
        public final int[] newMissing;
        public final boolean[] newMembershipChanged;
        public final int[] oldValid;
        public final int[] newValid;

        Group(int cageId, StimulusConcKey key, List<Row> rows, int width) {
            this.cageId = cageId;
            this.key = key;
            expectedSpots = rows.size();
            baselineSpots = (int) rows.stream().filter(row -> Double.isFinite(row.signal.baseline)).count();
            oldLoss = missing(width);
            oldMatchedLoss = missing(width);
            newLoss = missing(width);
            newObservedLoss = missing(width);
            newMissing = new int[width];
            newMembershipChanged = new boolean[width];
            oldValid = new int[width];
            newValid = new int[width];
            boolean[] previousAvailable = new boolean[rows.size()];
            for (int x = 0; x < width; x++) {
                double oldSum = 0, oldMatchedSum = 0, newSum = 0;
                int oldMatchedValid = 0;
                for (int i = 0; i < rows.size(); i++) {
                    Row row = rows.get(i);
                    if (x < row.oldHeightRatio.length && Double.isFinite(row.oldHeightRatio[x])) {
                        oldSum += 1 - row.oldHeightRatio[x];
                        oldValid[x]++;
                        if (Double.isFinite(row.signal.baseline)) {
                            oldMatchedSum += 1 - row.oldHeightRatio[x];
                            oldMatchedValid++;
                        }
                    }
                    boolean available = Double.isFinite(row.signal.baseline)
                            && x < row.signal.ratio.length && Double.isFinite(row.signal.ratio[x]);
                    if (x > 0 && available != previousAvailable[i]) newMembershipChanged[x] = true;
                    previousAvailable[i] = available;
                    if (available) {
                        newSum += 1 - row.signal.ratio[x];
                        newValid[x]++;
                    }
                }
                if (oldValid[x] > 0) oldLoss[x] = oldSum;
                if (oldMatchedValid == baselineSpots && baselineSpots > 0) oldMatchedLoss[x] = oldMatchedSum;
                newMissing[x] = baselineSpots - newValid[x];
                if (newValid[x] > 0) newObservedLoss[x] = newSum;
                // Freeze eligibility at baseline; never drop an intermittently missing member from the sum.
                if (newValid[x] == baselineSpots && baselineSpots > 0) newLoss[x] = newSum;
            }
        }

        @Override public String toString() {
            return "Cage " + cageId + " / " + key.stimulus + " / " + key.concentration;
        }
    }

    public static final class Result {
        public final double[] minutes;
        public final List<Row> rows;
        public final List<Group> groups;
        public final Settings settings;

        public Result(double[] minutes, List<Row> rows, Settings settings) {
            this.minutes = minutes;
            this.rows = rows;
            this.settings = settings;
            groups = new ArrayList<>();
            Map<Integer, Map<StimulusConcKey, List<Row>>> grouped = new LinkedHashMap<>();
            for (Row row : rows) {
                String stimulus = row.spot != null ? row.spot.getProperties().getStimulus() : "";
                String concentration = row.spot != null ? row.spot.getProperties().getConcentration() : "";
                grouped.computeIfAbsent(row.cageId, k -> new LinkedHashMap<>())
                        .computeIfAbsent(new StimulusConcKey(stimulus, concentration), k -> new ArrayList<>()).add(row);
            }
            grouped.forEach((id, map) -> map.forEach((key, groupRows) ->
                    groups.add(new Group(id, key, groupRows, minutes.length))));
        }
    }

    public static Result analyze(Experiment exp, Params params, Settings settings) {
        if (exp == null || params == null || settings == null)
            throw new IllegalArgumentException("An experiment, metric settings and baseline are required.");
        SequenceKymos sequence = exp.getSeqKymos();
        if (sequence == null || sequence.getSequence() == null)
            throw new IllegalStateException("Load cage kymographs before running Analyze new.");
        int[] ref = CageKymographStripLayoutCsv.readRefCameraDimensionsOrNull(exp.getKymosBinFullDirectory());
        if (ref == null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null)
            ref = new int[] { exp.getSeqCamData().getSequence().getSizeX(), exp.getSeqCamData().getSequence().getSizeY() };
        if (ref == null) throw new IllegalStateException("Open the camera reference or rebuild the strip layout.");
        List<Row> rows = new ArrayList<>();
        double[] minutes = null;
        for (int t = 0; t < sequence.getImageLoader().getNTotalFrames(); t++) {
            Cage cage = KymocageCageResolver.resolveCageFromKymographPath(sequence.getFileNameFromImageList(t), exp.getCages());
            if (cage == null) continue;
            IcyBufferedImage image = sequence.getSeqImage(t, 0);
            if (image == null) continue;
            int width = image.getWidth();
            if (minutes == null) minutes = CageKymographTimeline.minutes(exp, width);
            if (width != minutes.length) {
                // Each trace needs the exact acquisition clock, never a shortened substitute.
                throw new IllegalStateException("Cage kymograph widths differ; rebuild them together.");
            }
            List<CageKymographSpotBands> bands = CageKymographPickSupport.stackedSpotBands(exp, cage,
                    exp.getSpots(), ref[0], ref[1], width);
            int cageId = cage.getProperties().getCageID();
            rows.addAll(analyzeImage(cageId, image, bands, params, minutes, settings));
        }
        return new Result(minutes != null ? minutes : new double[0], rows, settings);
    }

    /** Shared image entry point for the application, synthetic tests, and recorded-data replay. */
    public static List<Row> analyzeImage(int cageId, IcyBufferedImage image, List<CageKymographSpotBands> bands,
            Params params, double[] minutes, Settings settings) {
        double[] metric = metric(image, params);
        double[] insect = params.insectMetricGateEnabled ? KymoImageTransforms.channel0AsDouble(
                KymoImageTransforms.applyMetricTransform(image, params.insectMetricTransform, false)) : null;
        if (params.insectMetricGateEnabled && insect == null)
            throw new IllegalStateException("The insect transform could not be evaluated.");
        List<CageKymoAnalyzer.ColumnMetrics> old = CageKymoAnalyzer_old.analyzeImage(image, bands, params);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            CageKymographSpotBands band = bands.get(i);
            rows.add(new Row(cageId, band.spot, compute(image, band, metric, insect, params, minutes, settings),
                    old.get(i).greenHeightRatio));
        }
        return rows;
    }

    /** Full precision for S(diffRGB); the ordinary display transform saturates at 255 on 8-bit TIFFs. */
    static double[] metric(IcyBufferedImage image, Params params) {
        if (params.metricTransform != ImageTransformEnums.RGB_DIFFS)
            return KymoImageTransforms.channel0AsDouble(KymoImageTransforms.applyMetricTransform(image, params.metricTransform, false));
        if (image.getSizeC() < 3) throw new IllegalArgumentException("The new color integral requires RGB kymographs.");
        double[] r = channel(image, 0), g = channel(image, 1), b = channel(image, 2);
        double[] metric = new double[r.length];
        for (int i = 0; i < metric.length; i++)
            metric[i] = Math.abs(r[i] - b[i]) + Math.abs(r[i] - g[i]) + Math.abs(b[i] - g[i]);
        return metric;
    }

    static Signal compute(IcyBufferedImage image, CageKymographSpotBands band, double[] metric,
            double[] insect, Params params, double[] minutes, Settings settings) {
        int w = image.getWidth(), y0 = Math.max(0, band.y0), y1 = Math.min(image.getHeight(), band.y1Exclusive);
        double[] observed = missing(w);
        if (!band.geometryMissing && metric != null && y1 > y0) {
            double[] r = channel(image, 0), g = channel(image, Math.min(1, image.getSizeC() - 1)),
                    b = channel(image, Math.min(2, image.getSizeC() - 1));
            int edge = Math.max(1, (y1 - y0) / 5);
            for (int x = 0; x < w; x++) {
                double[] floor = new double[2 * edge];
                int floorCount = 0, valid = 0;
                boolean contaminated = false;
                for (int y = y0; y < y1; y++) {
                    int i = y * w + x;
                    if (!Double.isFinite(metric[i]) || r[i] + g[i] + b[i] < params.minSumRgbForValidPixel) continue;
                    valid++;
                    if (insect != null && KymoMetricGate.directedFinite(insect[i], params.insectMetricThreshold,
                            params.insectMetricThresholdUp)) contaminated = true;
                    if (y < y0 + edge || y >= y1 - edge) floor[floorCount++] = metric[i];
                }
                // Partial observations would systematically underestimate the strip integral.
                if (contaminated || valid != y1 - y0 || valid < params.minValidRowsPerColumn || floorCount == 0) continue;
                double background = median(floor, floorCount), sum = 0;
                for (int y = y0; y < y1; y++) sum += Math.max(0, metric[y * w + x] - background);
                observed[x] = sum;
            }
        }
        return normalize(observed, minutes, settings,
                params.rowwiseOcclusionFill ? params.maxRowOcclusionGapColumns : 0);
    }

    /** Baseline uses observations only (at least three), never interpolated or peak values. */
    public static Signal normalize(double[] observed, double[] minutes, Settings settings, int maxGap) {
        if (observed.length != minutes.length) throw new IllegalArgumentException("Signal and clock lengths differ.");
        double[] baselineValues = new double[observed.length];
        int count = 0;
        for (int x = 0; x < observed.length; x++) {
            if (!Double.isFinite(minutes[x]) || (x > 0 && minutes[x] <= minutes[x - 1]))
                throw new IllegalArgumentException("Kymograph acquisition times must be finite and increasing.");
            if (minutes[x] >= settings.baselineStartMinutes && minutes[x] <= settings.baselineEndMinutes
                    && Double.isFinite(observed[x])) baselineValues[count++] = observed[x];
        }
        double baseline = count >= 3 ? median(baselineValues, count) : Double.NaN;
        if (!(baseline > 0)) baseline = Double.NaN;
        double[] filled = observed.clone();
        for (int x = 0; x < filled.length;) {
            if (Double.isFinite(filled[x])) { x++; continue; }
            int first = x;
            while (x < filled.length && !Double.isFinite(filled[x])) x++;
            if (first == 0 || x == filled.length || x - first > maxGap) continue;
            int left = first - 1;
            for (int j = first; j < x; j++) {
                double fraction = (minutes[j] - minutes[left]) / (minutes[x] - minutes[left]);
                filled[j] = filled[left] + fraction * (filled[x] - filled[left]);
            }
        }
        double[] ratio = missing(observed.length);
        if (Double.isFinite(baseline)) for (int x = 0; x < ratio.length; x++) ratio[x] = filled[x] / baseline;
        return new Signal(observed.clone(), filled, ratio, baseline, count);
    }

    private static double[] channel(IcyBufferedImage image, int c) {
        return Array1DUtil.arrayToDoubleArray(image.getDataXY(c), image.isSignedDataType());
    }

    private static double median(double[] values, int count) {
        Arrays.sort(values, 0, count);
        return count % 2 == 0 ? (values[count / 2 - 1] + values[count / 2]) / 2 : values[count / 2];
    }

    private static double[] missing(int width) {
        double[] values = new double[width];
        Arrays.fill(values, Double.NaN);
        return values;
    }
}
