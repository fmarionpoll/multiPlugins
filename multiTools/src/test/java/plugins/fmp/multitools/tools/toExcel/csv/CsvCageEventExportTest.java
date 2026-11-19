package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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

    @Test public void cageFilesFollowLrSelectionAndMissingCorrectionStaysBlank() throws Exception {
        for (boolean selected : new boolean[] {false, true}) {
            Path folder = output.newFolder().toPath();
            export(experiment(), folder, selected);
            assertTrue(Files.exists(folder.resolve("intakeevents.csv")));
            assertTrue(Files.exists(folder.resolve("gulpevents.csv")));
            assertFalse(Files.exists(folder.resolve("cage_gulpenvents.csv")));
            for (String file : new String[] {"cage_intakeevents.csv", "cage_gulpevents.csv"}) {
                assertEquals(selected, Files.exists(folder.resolve(file)));
                if (selected) try (CSVParser parser = read(folder.resolve(file))) {
                    for (CSVRecord row : parser) {
                        assertTrue(row.isConsistent());
                        assertEquals("", row.get(6));
                        assertEquals("", row.get(7));
                    }
                }
            }
        }
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
