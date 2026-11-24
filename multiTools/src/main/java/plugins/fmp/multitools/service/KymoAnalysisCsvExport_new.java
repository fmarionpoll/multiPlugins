package plugins.fmp.multitools.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import plugins.fmp.multitools.service.CageKymoAnalyzer.Params;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Group;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Result;
import plugins.fmp.multitools.service.CageKymoAnalyzer_new.Row;

/** Explicitly named trial exports; no reuse of the old green-height persistence keys. */
public final class KymoAnalysisCsvExport_new {
    private KymoAnalysisCsvExport_new() { }

    public static void write(Path directory, Result result, Params params) throws IOException {
        Files.createDirectories(directory);
        try (CSVPrinter out = new CSVPrinter(Files.newBufferedWriter(directory.resolve("kymo_analysis_new_spots.csv"),
                StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            out.printRecord("cage_id", "spot_name", "stimulus", "concentration", "time_min", "observed_integral",
                    "integral_after_gap_fill", "baseline_median", "baseline_valid_samples", "signal_ratio_new",
                    "signal_loss_new", "height_ratio_old");
            for (Row row : result.rows) {
                String name = row.spot != null ? row.spot.getName() : "";
                String stimulus = row.spot != null ? row.spot.getProperties().getStimulus() : "";
                String concentration = row.spot != null ? row.spot.getProperties().getConcentration() : "";
                for (int x = 0; x < result.minutes.length; x++)
                    out.printRecord(row.cageId, name, stimulus, concentration, result.minutes[x],
                            number(row.signal.observed[x]), number(row.signal.integrated[x]), number(row.signal.baseline),
                            row.signal.baselineSamples, number(row.signal.ratio[x]), number(1 - row.signal.ratio[x]),
                            number(row.oldHeightRatio[x]));
            }
        }
        try (CSVPrinter out = new CSVPrinter(Files.newBufferedWriter(directory.resolve("kymo_analysis_new_cages.csv"),
                StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            out.printRecord("cage_id", "stimulus", "concentration", "time_min", "height_loss_old", "height_loss_old_same_cohort",
                    "signal_loss_new", "n_valid_old", "n_valid_new", "n_spots_total", "n_spots_baseline_cohort",
                    "signal_loss_new_observed_subtotal", "n_missing_new", "observed_membership_changed");
            for (Group group : result.groups) for (int x = 0; x < result.minutes.length; x++)
                out.printRecord(group.cageId, group.key.stimulus, group.key.concentration, result.minutes[x],
                        number(group.oldLoss[x]), number(group.oldMatchedLoss[x]), number(group.newLoss[x]),
                        group.oldValid[x], group.newValid[x], group.expectedSpots, group.baselineSpots,
                        number(group.newObservedLoss[x]), group.newMissing[x], group.newMembershipChanged[x]);
        }
        Properties provenance = new Properties();
        provenance.setProperty("method", "CageKymoAnalyzer_new_v2");
        provenance.setProperty("baseline_start_min", Double.toString(result.settings.baselineStartMinutes));
        provenance.setProperty("baseline_end_min", Double.toString(result.settings.baselineEndMinutes));
        provenance.setProperty("baseline", "median of at least 3 observed columns; no peak normalization or clipping");
        provenance.setProperty("metric", params.metricTransform.toString());
        provenance.setProperty("metric_precision", "S(diffRGB) evaluated without 8-bit saturation");
        provenance.setProperty("background", "per-column median of outer 20 percent rows at each edge");
        provenance.setProperty("new_spot_threshold", "none");
        provenance.setProperty("old_spot_threshold", Integer.toString(params.metricThreshold));
        provenance.setProperty("min_rgb_sum", Integer.toString(params.minSumRgbForValidPixel));
        provenance.setProperty("min_valid_rows", Integer.toString(params.minValidRowsPerColumn));
        provenance.setProperty("insect_gate", Boolean.toString(params.insectMetricGateEnabled));
        provenance.setProperty("insect_transform", params.insectMetricTransform.toString());
        provenance.setProperty("insect_threshold", Integer.toString(params.insectMetricThreshold));
        provenance.setProperty("insect_threshold_above", Boolean.toString(params.insectMetricThresholdUp));
        provenance.setProperty("gap_fill", Boolean.toString(params.rowwiseOcclusionFill));
        provenance.setProperty("max_gap_columns", Integer.toString(params.maxRowOcclusionGapColumns));
        provenance.setProperty("gap_rule", "time-weighted interpolation of bounded missing columns; no leading/trailing fill");
        provenance.setProperty("aggregate_rule", "fixed cohort of spots with valid baselines; new sum requires every cohort member at each column");
        provenance.setProperty("observed_subtotal_rule", "sum of available baseline members only; not a full-cage estimate; break chart lines when membership changes");
        try (BufferedWriter out = Files.newBufferedWriter(directory.resolve("kymo_analysis_new.properties"), StandardCharsets.UTF_8)) {
            provenance.store(out, "Experimental analysis; old SpotsMeasures.csv is unchanged");
        }
    }

    private static Object number(double value) {
        return Double.isFinite(value) ? value : "";
    }
}
