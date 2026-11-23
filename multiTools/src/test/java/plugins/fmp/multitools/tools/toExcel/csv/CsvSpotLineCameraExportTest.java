package plugins.fmp.multitools.tools.toExcel.csv;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.junit.ClassRule;
import org.junit.Test;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;

public class CsvSpotLineCameraExportTest {
    @ClassRule public static final plugins.fmp.multitools.test.InMemoryImageRule imageMemory =
            new plugins.fmp.multitools.test.InMemoryImageRule();

    @Test public void cameraFileContainsLinesAtCameraTimesAndLegacyRunsKeepTheirGrid() throws Exception {
        Experiment exp = new Experiment();
        exp.setKymoFirst_ms(40000);
        exp.setKymoBin_ms(60000);
        Spot spot = new Spot();
        exp.getSpots().getSpotList().add(spot);
        List<EnumResults> types = Arrays.asList(EnumResults.KYMO_CROSS_RATIO, EnumResults.KYMO_RIM_RATIO);
        for (EnumResults type : types) {
            spot.getMeasurements(type).setValues(new double[] { 1, 0.8, 0.5 });
            spot.getMeasurements(type).setCameraSampled(true);
            assertFalse(CsvNormalizedSpotExport.isKymoSeries(type));
        }
        long[] cameraTimes = { 0, 20000, 45000 };
        Path folder = Files.createTempDirectory("camera-line-export");
        List<String> columns = Arrays.asList("x_line_ratio", "rim_line_ratio");
        try (CsvNormalizedSpotExportSupport csv = new CsvNormalizedSpotExportSupport(folder, columns,
                Collections.emptyList(), Collections.emptyList(), false, 60000, false)) {
            CsvNormalizedSpotExport.writeSpotFamily(exp, new ResultsOptions(), csv, "expA", new Cage(), 1,
                    spot, "spotA", types, false, false, cameraTimes,
                    new CsvNormalizedSpotExport.KymoFrameIndexes(cameraTimes, 0, 0, 1));
        }
        List<String> lines = Files.readAllLines(folder.resolve("measure_spot_raw.csv"));
        assertEquals("experiment_id,cage_id,spot_id,time_min,frame_index,x_line_ratio,rim_line_ratio", lines.get(0));
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).contains(",0.0,0,1.0,1.0"));
        assertTrue(lines.get(3).contains(",0.75,2,0.5,0.5"));
        assertFalse(Files.exists(folder.resolve("measure_spot_kymo_raw.csv")));
        for (EnumResults type : types) {
            assertArrayEquals(cameraTimes, CsvNormalizedSpotExport.rawTimes(exp, new ResultsOptions(), type, 3, cameraTimes));
            spot.getMeasurements(type).setCameraSampled(false);
            assertArrayEquals(cameraTimes, CsvNormalizedSpotExport.rawTimes(exp, new ResultsOptions(), type, 3, cameraTimes));
            long[] unmatched = { 0, 20000, 45000, 70000, 90000, 120000 };
            assertArrayEquals(new long[] { 40000, 100000, 160000 },
                    CsvNormalizedSpotExport.rawTimes(exp, new ResultsOptions(), type, 3, unmatched));
        }
    }
}
