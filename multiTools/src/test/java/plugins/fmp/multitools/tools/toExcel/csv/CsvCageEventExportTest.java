package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import icy.preferences.IcyPreferences;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.cages.EvaporationCorrectionMethod;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.test.InMemoryImageRule;
import plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CsvCageEventExportTest {
    @ClassRule public static final InMemoryImageRule imageMemory = new InMemoryImageRule();
    @Rule public TemporaryFolder output = new TemporaryFolder();
    @BeforeClass public static void initializeIcy() { IcyPreferences.init(); }

    @Test public void cumulativeSideTotalsPreserveFractionsCorrectionsAndNativeGaps() throws Exception {
        Experiment exp = experiment();
        Capillary reference = exp.getCapillaries().getList().get(0);
        reference.getProperties().setNFlies(0);
        reference.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3},
                new double[] {10, 10.25, 10.5, 10.75}, 4);
        Path folder = output.newFolder().toPath();
        export(exp, folder, true);
        for (String event : new String[] {"intake", "gulp"}) {
            Path file = folder.resolve(event.equals("intake") ? "cage_intakeevents.csv" : "cage_gulpevents.csv");
            try (CSVParser parser = read(file)) {
                List<CSVRecord> rows = parser.getRecords();
                assertEquals(4, rows.size());
                assertEquals(8, parser.getHeaderMap().size());
                for (CSVRecord row : rows) assertTrue(row.isConsistent());
                assertEquals("0.0", rows.get(0).get("sum_" + event + "_raw_uL"));
                assertEquals("", rows.get(0).get("pi_" + event + "_raw"));
                CSVRecord row = rows.get(2);
                assertEquals(4, Double.parseDouble(row.get("time_min")), 0);
                assertEquals("2", row.get("frame_index"));
                // Two L capillaries contribute 2.25 each; R contributes 1.5.
                double factor = event.equals("intake") ? 1 : 2;
                assertEquals(.06 * factor, Double.parseDouble(row.get("sum_" + event + "_raw_uL")), 1e-12);
                assertEquals(.5, Double.parseDouble(row.get("pi_" + event + "_raw")), 1e-12);
                double left = (4.5 * factor - .5) / 100;
                double right = (1.5 * factor - .25) / 100;
                assertEquals(left + right, Double.parseDouble(row.get("sum_" + event + "_corr_uL")), 1e-12);
                assertEquals((left - right) / (left + right),
                        Double.parseDouble(row.get("pi_" + event + "_corr")), 1e-12);
            }
        }
    }

    @Test public void cageFilesAccompanyEventsRegardlessOfLrSelectionAndMissingCorrectionStaysBlank() throws Exception {
        for (boolean selected : new boolean[] {false, true}) {
            Path folder = output.newFolder().toPath();
            export(experiment(), folder, selected);
            assertTrue(Files.exists(folder.resolve("intakeevents.csv")));
            assertTrue(Files.exists(folder.resolve("gulpevents.csv")));
            assertFalse(Files.exists(folder.resolve("cage_gulpenvents.csv")));
            for (String file : new String[] {"cage_intakeevents.csv", "cage_gulpevents.csv"}) {
                assertTrue(Files.exists(folder.resolve(file)));
                try (CSVParser parser = read(folder.resolve(file))) {
                    for (CSVRecord row : parser) {
                        assertTrue(row.isConsistent());
                        assertEquals("", row.get(6));
                        assertEquals("", row.get(7));
                    }
                }
            }
        }
    }

    @Test public void emptyEventDatasetsExportZeroOnEveryObservedFrameUntilTheCageCutoff() throws Exception {
        for (int cutoff : new int[] {-1, 2, 0}) {
            Experiment exp = experiment();
            for (Capillary cap : exp.getCapillaries().getList()) {
                cap.getGulps().clear();
                cap.getIntakes().clear();
            }
            Capillary reference = exp.getCapillaries().getList().get(0);
            reference.getProperties().setNFlies(0);
            reference.setNValid(cutoff);
            Path folder = output.newFolder().toPath();
            export(exp, folder, false);
            for (String event : new String[] {"intake", "gulp"}) {
                Path file = folder.resolve("cage_" + event + "events.csv");
                if (cutoff == 0) {
                    assertFalse(Files.exists(file));
                    continue;
                }
                try (CSVParser parser = read(file)) {
                    List<CSVRecord> rows = parser.getRecords();
                    assertEquals(cutoff < 0 ? 4 : cutoff, rows.size());
                    for (int i = 0; i < rows.size(); i++) {
                        CSVRecord row = rows.get(i);
                        assertTrue(row.isConsistent());
                        assertEquals(Integer.toString(i), row.get("frame_index"));
                        assertEquals("0.0", row.get("sum_" + event + "_raw_uL"));
                        assertEquals("0.0", row.get("sum_" + event + "_corr_uL"));
                        assertEquals("", row.get("pi_" + event + "_raw"));
                        assertEquals("", row.get("pi_" + event + "_corr"));
                    }
                }
            }
        }
    }

    @Test public void emptySideContributesZeroAndShortObservationsDoNotCreateTrailingRows() throws Exception {
        Experiment exp = experiment();
        Capillary right = exp.getCapillaries().getList().get(2);
        right.getGulps().clear();
        right.getIntakes().clear();
        right.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1}, new double[] {10, 11}, 2);
        Path folder = output.newFolder().toPath();
        export(exp, folder, false);
        for (String event : new String[] {"intake", "gulp"}) {
            try (CSVParser parser = read(folder.resolve("cage_" + event + "events.csv"))) {
                List<CSVRecord> rows = parser.getRecords();
                assertEquals(2, rows.size());
                assertEquals("0.0", rows.get(0).get("sum_" + event + "_raw_uL"));
                double expected = event.equals("intake") ? .045 : .09;
                assertEquals(expected, Double.parseDouble(rows.get(1).get("sum_" + event + "_raw_uL")), 1e-12);
                assertEquals("1.0", rows.get(1).get("pi_" + event + "_raw"));
                assertEquals("", rows.get(1).get("sum_" + event + "_corr_uL"));
            }
        }
    }

    @Test public void levelsAndEventsShareInclusiveCageLimitsAndPreserveIndependentCapillaryLimits() throws Exception {
        for (int cutoff : new int[] {-1, 1, 2, 3, 0}) {
            Experiment exp = experiment();
            exp.getCapillaries().getList().remove(1); // one L and one R, with distinct export IDs
            exp.getCapillaries().getList().get(0).setNValid(cutoff);
            String firstCapId = plugins.fmp.multitools.tools.toExcel.NormalizedExportSupport
                    .buildCapId(exp.getCapillaries().getList().get(0));
            Path levels = exportMode(exp, CsvNormalizedExport.Mode.LEVELS);
            Path events = exportMode(exp, CsvNormalizedExport.Mode.GULPS);
            int expected = cutoff < 0 ? 4 : cutoff;
            Set<String> levelFrames = frameKeys(levels.resolve("measure_cage_raw.csv"));
            assertEquals(expected, levelFrames.size());
            assertEquals(levelFrames, frameKeys(events.resolve("cage_gulpevents.csv")));
            assertEquals(levelFrames, frameKeys(events.resolve("cage_intakeevents.csv")));
            if (cutoff != 0) {
                try (CSVParser parser = read(levels.resolve("measure_cage_raw.csv"))) {
                    CSVRecord first = parser.getRecords().get(0);
                    assertEquals("0.0", first.get("sum_topraw"));
                    assertEquals("", first.get("pi_topraw"));
                }
            }
            for (String file : new String[] {"measure_cap_raw.csv", "measure_cap_bin60.csv"}) {
                try (CSVParser parser = read(levels.resolve(file))) {
                    int firstCapRows = 0;
                    int otherCapRows = 0;
                    for (CSVRecord row : parser) {
                        if (row.get("capillary_id").equals(firstCapId)) firstCapRows++;
                        else otherCapRows++;
                    }
                    int expectedBins = expected == 4 ? 6 : expected == 3 ? 5 : expected;
                    assertEquals(file.contains("bin") ? expectedBins : expected, firstCapRows);
                    assertEquals(file.contains("bin") ? 6 : 4, otherCapRows);
                }
            }
            for (String file : new String[] {"gulpevents.csv", "intakeevents.csv"}) {
                int expectedEvents = cutoff < 0 ? 3 : Math.max(0, cutoff - 1);
                if (expectedEvents == 0) {
                    assertFalse(Files.exists(events.resolve(file)));
                    continue;
                }
                try (CSVParser parser = read(events.resolve(file))) {
                    List<CSVRecord> rows = parser.getRecords();
                    assertEquals(expectedEvents, rows.size());
                    for (CSVRecord row : rows) {
                        if (cutoff >= 0)
                            assertTrue(Integer.parseInt(row.get("frame_index")) < cutoff);
                        assertTrue(levelFrames.contains(row.get("experiment_id") + "/" + row.get("cage_id")
                                + "/" + row.get("frame_index")));
                    }
                }
            }
            if (expected > 0) {
                try (CSVParser parser = read(levels.resolve("measure_cage_bin60.csv"))) {
                    double lastTime = expected == 4 ? 5 : expected == 3 ? 4 : expected - 1;
                    for (CSVRecord row : parser)
                        assertTrue(Double.parseDouble(row.get("time_min")) <= lastTime);
                }
            }
        }
    }

    @Test public void singleSidedNoFlyControlsAndSubsampledCutoffsHaveMatchingCageCoverage() throws Exception {
        Experiment exp = experiment();
        Capillary cap = exp.getCapillaries().getList().get(0);
        exp.getCapillaries().getList().clear();
        exp.getCapillaries().getList().add(cap);
        cap.getProperties().setNFlies(0);
        cap.getGulps().clear();
        cap.getIntakes().clear();
        cap.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3}, new double[] {10, 10, 10, 10}, 4);
        exp.setKymoSubsampleFactor(2);
        exp.setFrameTimeScale(FrameTimeScale.fromArrays(0,
                new long[] {0, 60000, 120000, 180000, 240000, 300000, 360000, 420000},
                new String[] {"a", "b", "c", "d", "e", "f", "g", "h"}, 0, 8));
        cap.setNValid(3); // camera frames 0 and 2 are valid; frame 4 is not.
        Path levels = exportMode(exp, CsvNormalizedExport.Mode.LEVELS);
        Path events = exportMode(exp, CsvNormalizedExport.Mode.GULPS);
        Set<String> keys = frameKeys(levels.resolve("measure_cage_raw.csv"));
        assertEquals(2, keys.size());
        assertEquals(keys, frameKeys(events.resolve("cage_gulpevents.csv")));
        assertEquals(keys, frameKeys(events.resolve("cage_intakeevents.csv")));
    }

    private Set<String> frameKeys(Path file) throws Exception {
        Set<String> keys = new HashSet<>();
        if (Files.exists(file)) try (CSVParser parser = read(file)) {
            for (CSVRecord row : parser) {
                assertTrue(row.isConsistent());
                assertTrue(keys.add(row.get("experiment_id") + "/" + row.get("cage_id") + "/" + row.get("frame_index")));
            }
        }
        return keys;
    }

    private Path exportMode(Experiment exp, CsvNormalizedExport.Mode mode) throws Exception {
        Path folder = output.newFolder().toPath();
        ResultsOptions options = new ResultsOptions();
        options.intake = true;
        options.forceCsvBinGrid = true;
        options.evaporationCorrectionMethod = EvaporationCorrectionMethod.AVERAGE;
        List<String> columns = mode == CsvNormalizedExport.Mode.LEVELS ? Arrays.asList(
                "consumption_raw_uL", "consumption_corrected_uL", "bottom_level_uL") : Collections.emptyList();
        Method method = CsvNormalizedExport.class.getDeclaredMethod("exportOneExperiment", Experiment.class,
                ResultsOptions.class, String.class, CsvNormalizedExport.Mode.class, CsvNormalizedExportSupport.class,
                List.class, boolean.class, boolean.class, boolean.class, long.class);
        method.setAccessible(true);
        try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, columns, 60000, true, mode == CsvNormalizedExport.Mode.GULPS)) {
            method.invoke(null, exp, options, "A", mode, csv, columns, mode == CsvNormalizedExport.Mode.LEVELS,
                    mode == CsvNormalizedExport.Mode.GULPS, mode == CsvNormalizedExport.Mode.GULPS, 60000L);
        }
        return folder;
    }

    private Experiment experiment() {
        Experiment exp = new Experiment();
        exp.setFrameTimeScale(FrameTimeScale.fromArrays(0, new long[] {0, 60000, 240000, 300000},
                new String[] {"a", "b", "e", "f"}, 0, 4));
        Cage cage = new Cage();
        cage.getProperties().setCageID(2);
        exp.getCages().getCageList().add(cage);
        for (int i = 0; i < 3; i++) {
            String side = i < 2 ? "L" : "R";
            ROI2DLine roi = new ROI2DLine(0, 0, 0, 100);
            roi.setName("line2" + side);
            Capillary cap = new Capillary(roi);
            cap.setKymographIndex(i);
            cap.setCageID(2);
            cap.setPixels(100);
            cap.setVolume(1);
            cap.getProperties().setNFlies(1);
            cap.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3},
                    new double[] {10, 11, 12, 13}, 4);
            double[] events = i < 2 ? new double[] {0, 2.25, 0, .75} : new double[] {0, 0, 1.5, 0};
            cap.getIntakes().set(events, 3, 6, .5, true);
            cap.getGulps().ensureSize(4);
            for (int j = 0; j < events.length; j++) cap.getGulps().setAmplitudeAt(j, 2 * events[j]);
            exp.getCapillaries().getList().add(cap);
            cage.addCapillaryIfUnique(cap);
        }
        return exp;
    }

    private void export(Experiment exp, Path folder, boolean selected) throws Exception {
        ResultsOptions options = new ResultsOptions();
        options.lrPI = selected;
        options.evaporationCorrectionMethod = EvaporationCorrectionMethod.AVERAGE;
        Method method = CsvNormalizedExport.class.getDeclaredMethod("exportEventBins", Experiment.class,
                ResultsOptions.class, String.class, CsvNormalizedExportSupport.class,
                CageCapillarySeriesBuilder.class, int.class, boolean.class);
        method.setAccessible(true);
        try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, Collections.emptyList(), 60000, false)) {
            for (boolean intake : new boolean[] {false, true})
                method.invoke(null, exp, options, "A", csv, new CageCapillarySeriesBuilder(), 60000, intake);
        }
    }

    private CSVParser read(Path file) throws Exception {
        return CSVParser.parse(Files.newBufferedReader(file, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build());
    }
}
