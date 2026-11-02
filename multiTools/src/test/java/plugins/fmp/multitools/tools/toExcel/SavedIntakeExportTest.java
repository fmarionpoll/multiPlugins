package plugins.fmp.multitools.tools.toExcel;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.CapillaryMeasure;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.toExcel.csv.CsvNormalizedExportSupport;

public class SavedIntakeExportTest {
    @org.junit.BeforeClass public static void initializeIcy() { icy.preferences.IcyPreferences.init(); }
    private Capillary capillary() {
        Capillary cap = new Capillary();
        cap.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3}, new double[] {10, 11, 12, 13}, 4);
        cap.getCorrelationGulps().set(new double[] {0, 2.25, 0, 0.75}, 3, 6, 0.5, true);
        cap.getCorrelationGulps().setMethodOptions("version=test,manual=1;edited");
        return cap;
    }

    @Test public void savedIntakeWorksWithoutOrdinaryGulpsAndPreservesFractions() {
        Capillary cap = capillary();
        Experiment exp = new Experiment();
        ResultsOptions options = new ResultsOptions();
        assertEquals(2.25, cap.getMeasurements(EnumResults.SAVED_INTAKE_RAW, exp, options).getValueAt(1), 1e-12);
        assertEquals(3, cap.getMeasurements(EnumResults.SUM_SAVED_INTAKE_RAW, exp, options).getValueAt(3), 1e-12);
        assertNull(cap.getMeasurements(EnumResults.INTAKE_BIN_RAW, exp, options));
        cap.getGulps().ensureSize(4);
        cap.getGulps().setAmplitudeAt(1, 9);
        assertEquals(9, cap.getMeasurements(EnumResults.INTAKE_BIN_RAW, exp, options).getValueAt(1), 1e-12);
        assertEquals(2.25, cap.getMeasurements(EnumResults.SAVED_INTAKE_RAW, exp, options).getValueAt(1), 1e-12);
        assertEquals(9, cap.getGulps().getHeightSeries().ypoints[1], 0);
    }

    @Test public void correctionUsesSameIntervalAndMissingIntakeStaysMissing() {
        Capillary cap = capillary();
        Experiment exp = new Experiment();
        ResultsOptions options = new ResultsOptions();
        assertNull(cap.getMeasurements(EnumResults.SAVED_INTAKE_CORR, exp, options));
        CapillaryMeasure evaporation = new CapillaryMeasure("evaporation");
        evaporation.polylineLevel = new Level2D(new double[] {0, 1, 2, 3}, new double[] {0, 0.25, 0.5, 1}, 4);
        exp.getCapillaries().getReferenceMeasures().setEvaporation(evaporation);
        assertEquals(2, cap.getMeasurements(EnumResults.SAVED_INTAKE_CORR, exp, options).getValueAt(1), 1e-12);
        assertEquals(2.25, cap.getMeasurements(EnumResults.SUM_SAVED_INTAKE_CORR, exp, options).getValueAt(3), 1e-12);
        cap.getCorrelationGulps().clear();
        assertNull(cap.getMeasurements(EnumResults.SAVED_INTAKE_RAW, exp, options));
    }

    @Test public void csvKeepsIndependentFilesAndProvenance() throws Exception {
        Path folder = Files.createTempDirectory("saved-intake-export");
        try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, Collections.emptyList(), 60000, false)) {
            csv.writeGulpEvent("expA", 2, "L", 1.5, 9);
            csv.writeSavedIntake("expA", 2, "L", 0, 1.5, 2.25, Double.NaN, 3, 2);
            csv.writeSavedIntakeMetadata(SavedIntakeMetadata.row("expA", 2, "L", capillary()));
        }
        String saved = new String(Files.readAllBytes(folder.resolve("intakeevents.csv")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains("saved_intake_raw_uL"));
        assertTrue(saved.contains("2.25"));
        assertTrue(saved.contains("interval_start_min,interval_end_min"));
        assertFalse(saved.contains("NaN"));
        String metadata = new String(Files.readAllBytes(folder.resolve("saved_intake_metadata.csv")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(metadata.contains("method_options"));
        assertTrue(metadata.contains("version=test,manual=1;edited"));
    }

    @Test public void gulpIntervalsUsePreviousImageEvenAcrossMissingImages() throws Exception {
        org.jfree.data.xy.XYSeries series = new org.jfree.data.xy.XYSeries("cap", false);
        series.add(0, 0);
        series.add(1, 2);
        series.add(4, 3); // images at 2 and 3 minutes are missing
        series.add(5, 0);
        series.add(6, 1); // preceding image, not preceding event, starts the interval
        Path folder = Files.createTempDirectory("gulp-interval-export");
        try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, Collections.emptyList(), 60000, false)) {
            java.lang.reflect.Method method = plugins.fmp.multitools.tools.toExcel.csv.CsvNormalizedExport.class
                .getDeclaredMethod("writeGulpEventsNative", CsvNormalizedExportSupport.class, String.class,
                    int.class, String.class, org.jfree.data.xy.XYSeries.class);
            method.setAccessible(true);
            method.invoke(null, csv, "expA", 2, "L", series);
        }
        String events = new String(Files.readAllBytes(folder.resolve("gulpevents.csv")), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(events.contains("interval_start_min,interval_end_min"));
        assertTrue(events.contains("expA,2,L,4.0,3.0,1.0,4.0"));
        assertTrue(events.contains("expA,2,L,6.0,1.0,5.0,6.0"));
    }

    @Test public void copiedOptionsRetainIndependentSelections() {
        ResultsOptions options = new ResultsOptions();
        options.savedIntake = true;
        ResultsOptions copy = new ResultsOptions();
        copy.copy(options);
        assertTrue(copy.savedIntake);
        assertFalse(copy.intakeBins);
    }

    @Test public void intakeCsvAndExcelPreserveAcquisitionGapsAtNativeResolution() throws Exception {
        Experiment exp = new Experiment();
        exp.setFrameTimeScale(plugins.fmp.multitools.experiment.timebase.FrameTimeScale.fromArrays(0,
            new long[] {0, 60000, 240000, 300000}, new String[] {"a", "b", "e", "f"}, 0, 4));
        plugins.kernel.roi.roi2d.ROI2DLine roi = new plugins.kernel.roi.roi2d.ROI2DLine(0, 0, 0, 100);
        roi.setName("line2L");
        Capillary cap = new Capillary(roi);
        cap.setKymographIndex(0);
        cap.setCageID(2);
        cap.setPixels(100);
        cap.setVolume(1);
        cap.getTopRaw().polylineLevel = new Level2D(new double[] {0, 1, 2, 3}, new double[] {10, 11, 13, 14}, 4);
        cap.getCorrelationGulps().set(new double[] {0, 0, 2.25, 0}, 3, 6, 0.5, true);
        exp.getCapillaries().getList().add(cap);
        plugins.fmp.multitools.experiment.cage.Cage cage = new plugins.fmp.multitools.experiment.cage.Cage();
        cage.getProperties().setCageID(2);
        cage.addCapillaryIfUnique(cap);
        exp.getCages().getCageList().add(cage);
        ResultsOptions options = new ResultsOptions();
        options.savedIntake = true;
        options.buildExcelStepMs = 600000; // summary bins must not replace native event boundaries
        Path folder = Files.createTempDirectory("intake-native-gap");
        try (CsvNormalizedExportSupport csv = new CsvNormalizedExportSupport(folder, Collections.emptyList(), 600000, false)) {
            java.lang.reflect.Method method = plugins.fmp.multitools.tools.toExcel.csv.CsvNormalizedExport.class
                .getDeclaredMethod("exportIntakeBins", Experiment.class, ResultsOptions.class, String.class,
                    CsvNormalizedExportSupport.class, plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder.class,
                    int.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, exp, options, "A", csv,
                new plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder(), 60000, true);
        }
        java.util.List<String> lines = Files.readAllLines(folder.resolve("intakeevents.csv"));
        assertEquals(2, lines.size());
        String[] fields = lines.get(1).split(",");
        assertEquals(4, Double.parseDouble(fields[3]), 0);
        assertEquals(0.0225, Double.parseDouble(fields[4]), 1e-12);
        assertEquals(1, Double.parseDouble(fields[8]), 0);
        assertEquals(4, Double.parseDouble(fields[9]), 0);
        XLSExportMeasuresFromGulp exporter = new XLSExportMeasuresFromGulp();
        exporter.options = options;
        try (plugins.fmp.multitools.tools.toExcel.utils.ExcelResourceManager manager =
                new plugins.fmp.multitools.tools.toExcel.utils.ExcelResourceManager(folder.resolve("test.xlsx").toString())) {
            exporter.resourceManager = manager;
            java.lang.reflect.Method method = XLSExportMeasuresFromGulp.class.getDeclaredMethod("exportSavedIntakeDetails", Experiment.class, String.class);
            method.setAccessible(true);
            method.invoke(exporter, exp, "A");
            org.apache.poi.xssf.streaming.SXSSFSheet sheet = manager.getWorkbook().getSheet("saved_intake_intervals");
            assertEquals(1, sheet.getRow(3).getCell(3).getNumericCellValue(), 0);
            assertEquals(4, sheet.getRow(3).getCell(4).getNumericCellValue(), 0);
            assertEquals(0.0225, sheet.getRow(3).getCell(5).getNumericCellValue(), 1e-12);
        }
    }
}
