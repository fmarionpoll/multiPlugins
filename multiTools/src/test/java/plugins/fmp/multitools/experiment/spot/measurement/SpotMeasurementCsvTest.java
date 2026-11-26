package plugins.fmp.multitools.experiment.spot.measurement;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SpotMeasurementCsvTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void longRecordingPreservesPrecisionAndQuotedMetadataAcrossMultipleQuantities() throws Exception {
        int n = 50000;
        double[] times = new double[n], fly = new double[n], raw = new double[n];
        int[] flags = new int[n];
        boolean[] manual = new boolean[n];
        for (int i = 0; i < n; i++) {
            times[i] = i * 1.23456789012345;
            fly[i] = i % 7 == 0 ? Double.NaN : .125;
            raw[i] = i % 13 == 0 ? Double.NaN : -0.123456789012345 * i;
            flags[i] = i % 13 == 0 ? SpotSampleFlags.OCCLUDED : 0;
            manual[i] = i % 17 == 0;
        }
        String spot = "spot,\"é\"\nA", unit = "intensity,\"raw\"\r\nunits";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("threshold,\"key\"", "line1\nline2\r\n<&é>");
        parameters.put("empty", "");
        SpotMethodResult original = new SpotMethodResult(SpotMeasurementMethod.COLOR, times, fly, unit, parameters,
                Arrays.asList(new SpotMeasuredQuantity("signal", unit, raw, flags, manual),
                        new SpotMeasuredQuantity("area", "pixels", fly, flags)));
        Path file = folder.getRoot().toPath().resolve(SpotMeasurementCsv.FILENAME);
        SpotMeasurementCsv.write(file, Collections.singletonMap(spot, Collections.singletonMap(original.getMethod(), original)));
        SpotMethodResult restored = SpotMeasurementCsv.read(file).get(spot).get(original.getMethod());
        assertArrayEquals(times, restored.getTimesSeconds(), 0);
        assertArrayEquals(fly, restored.getFlyEvidence(), 0);
        assertEquals(unit, restored.getFlyEvidenceUnit());
        assertEquals(parameters, restored.getParameters());
        assertEquals(unit, restored.getQuantity("signal").getUnit());
        assertArrayEquals(raw, restored.getQuantity("signal").getRawValues(), 0);
        assertArrayEquals(flags, restored.getQuantity("signal").getAcquisitionFlags());
        assertArrayEquals(manual, restored.getQuantity("signal").getManualRejections());
        assertArrayEquals(fly, restored.getQuantity("area").getRawValues(), 0);
    }

    @Test public void emptyFileAndEmptyTimelineAreDistinct() throws Exception {
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementCsv.FILENAME);
        SpotMeasurementCsv.write(path, Collections.emptyMap());
        assertTrue(SpotMeasurementCsv.read(path).isEmpty());
        SpotMethodResult result = new SpotMethodResult(SpotMeasurementMethod.FILTER,
                new double[0], new double[0], "pixels", Collections.emptyMap(),
                Collections.singletonList(new SpotMeasuredQuantity("signal", "intensity", new double[0], new int[0])));
        SpotMeasurementCsv.write(path, Collections.singletonMap("A", Collections.singletonMap(result.getMethod(), result)));
        assertEquals(0, SpotMeasurementCsv.read(path).get("A").get(result.getMethod()).getTimesSeconds().length);
    }

    @Test public void invalidRecordsCannotBeOverwritten() throws Exception {
        String valid = "SPOT_PRIMARY_MEASUREMENTS,1\nMETHOD,A,FILTER,pixels\nTIMES,0,1\nFLY,0,0\n"
                + "QUANTITY,signal,intensity\nRAW,1,NaN\nFLAGS,0,1\nMANUAL,0,1\nEND_METHOD\nEND_FILE\n";
        String method = valid.substring(valid.indexOf("METHOD,"), valid.indexOf("END_FILE"));
        String[] invalid = {
            "", valid.replace(",1\nMETHOD", ",99\nMETHOD"),
            valid.replace("RAW,1,NaN", "RAW,1"),
            valid.replace("MANUAL,0,1", "MANUAL,0,2"),
            valid.replace("TIMES,0,1", "TIMES,1,0"),
            valid.replace("END_METHOD\n", ""), valid.replace("END_FILE", method + "END_FILE"),
            valid.replace("END_FILE\n", ""),
            valid.replace("TIMES,0,1", "PARAMETER,a,1\nPARAMETER,a,2\nTIMES,0,1"),
            valid + "UNKNOWN\n"
        };
        Path path = folder.getRoot().toPath().resolve(SpotMeasurementCsv.FILENAME);
        for (String text : invalid) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);
            try {
                SpotMeasurementCsv.write(path, Collections.emptyMap());
                fail("Invalid existing file was accepted: " + text);
            } catch (IOException expected) {
                assertArrayEquals(bytes, Files.readAllBytes(path));
            }
        }
    }
}
