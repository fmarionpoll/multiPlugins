package plugins.fmp.multitools.experiment.spot.measurement;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/** Lossless primary measurements. Series occupy separate CSV rows on native timelines. Callers choose one file per method. */
public final class SpotMeasurementCsv {
    private SpotMeasurementCsv() { }

    public static Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> read(Path path) throws IOException {
        Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> results = new LinkedHashMap<>();
        if (!Files.exists(path)) return results;
        try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.parse(input)) {
            Iterator<CSVRecord> records = parser.iterator();
            CSVRecord header = next(records, "SPOT_PRIMARY_MEASUREMENTS", 2);
            if (!"1".equals(header.get(1))) throw new IOException("Unsupported primary measurement version");
            while (true) {
                CSVRecord methodRow = next(records);
                if ("END_FILE".equals(methodRow.get(0))) {
                    require(methodRow, "END_FILE", 1);
                    if (records.hasNext()) throw new IOException("Unexpected records after END_FILE");
                    break;
                }
                require(methodRow, "METHOD", 4);
                String spot = methodRow.get(1);
                SpotMeasurementMethod method = SpotMeasurementMethod.valueOf(methodRow.get(2));
                Map<String, String> parameters = new LinkedHashMap<>();
                CSVRecord row = next(records);
                while ("PARAMETER".equals(row.get(0))) {
                    require(row, "PARAMETER", 3);
                    if (parameters.put(row.get(1), row.get(2)) != null)
                        throw new IOException("Duplicate acquisition parameter");
                    row = next(records);
                }
                require(row, "TIMES", -1);
                double[] times = doubles(row);
                double[] fly = doubles(next(records, "FLY", times.length + 1));
                List<SpotMeasuredQuantity> quantities = new ArrayList<>();
                row = next(records);
                while ("QUANTITY".equals(row.get(0))) {
                    require(row, "QUANTITY", 3);
                    String id = row.get(1), unit = row.get(2);
                    double[] raw = doubles(next(records, "RAW", times.length + 1));
                    CSVRecord flagsRow = next(records, "FLAGS", times.length + 1);
                    CSVRecord manualRow = next(records, "MANUAL", times.length + 1);
                    int[] flags = new int[times.length];
                    boolean[] manual = new boolean[times.length];
                    for (int i = 0; i < times.length; i++) {
                        flags[i] = Integer.parseInt(flagsRow.get(i + 1));
                        String decision = manualRow.get(i + 1);
                        if (!"0".equals(decision) && !"1".equals(decision))
                            throw new IOException("Invalid manual rejection decision");
                        manual[i] = "1".equals(decision);
                    }
                    quantities.add(new SpotMeasuredQuantity(id, unit, raw, flags, manual));
                    row = next(records);
                }
                require(row, "END_METHOD", 1);
                SpotMethodResult result = new SpotMethodResult(method, times, fly, methodRow.get(3), parameters, quantities);
                Map<SpotMeasurementMethod, SpotMethodResult> methods = results.computeIfAbsent(spot,
                        key -> new EnumMap<>(SpotMeasurementMethod.class));
                if (methods.put(method, result) != null) throw new IOException("Duplicate method for " + spot);
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid primary measurement file: " + path, e);
        }
        return results;
    }

    /** Write to a temporary sibling, then replace; never overwrite an unreadable existing CSV. */
    public static void write(Path path, Map<String, Map<SpotMeasurementMethod, SpotMethodResult>> results)
            throws IOException {
        read(path);
        Path target = path.toAbsolutePath();
        Path temporary = Files.createTempFile(target.getParent(), ".spot-primary-", ".csv.tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);
                    CSVPrinter csv = new CSVPrinter(output, CSVFormat.DEFAULT)) {
                csv.printRecord("SPOT_PRIMARY_MEASUREMENTS", "1");
                for (Map.Entry<String, Map<SpotMeasurementMethod, SpotMethodResult>> spot : results.entrySet()) {
                    for (SpotMethodResult result : spot.getValue().values()) {
                        csv.printRecord("METHOD", spot.getKey(), result.getMethod().name(), result.getFlyEvidenceUnit());
                        for (Map.Entry<String, String> parameter : result.getParameters().entrySet())
                            csv.printRecord("PARAMETER", parameter.getKey(), parameter.getValue());
                        series(csv, "TIMES", result.getTimesSeconds());
                        series(csv, "FLY", result.getFlyEvidence());
                        for (SpotMeasuredQuantity quantity : result.getQuantities().values()) {
                            csv.printRecord("QUANTITY", quantity.getId(), quantity.getUnit());
                            series(csv, "RAW", quantity.getRawValues());
                            csv.print("FLAGS");
                            for (int flag : quantity.getAcquisitionFlags()) csv.print(flag);
                            csv.println();
                            csv.print("MANUAL");
                            for (boolean rejected : quantity.getManualRejections()) csv.print(rejected ? 1 : 0);
                            csv.println();
                        }
                        csv.printRecord("END_METHOD");
                    }
                }
                csv.printRecord("END_FILE");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void series(CSVPrinter csv, String name, double[] values) throws IOException {
        csv.print(name);
        for (double value : values) csv.print(Double.toString(value));
        csv.println();
    }

    private static double[] doubles(CSVRecord row) {
        double[] values = new double[row.size() - 1];
        for (int i = 0; i < values.length; i++) values[i] = Double.parseDouble(row.get(i + 1));
        return values;
    }

    private static CSVRecord next(Iterator<CSVRecord> records) throws IOException {
        if (!records.hasNext()) throw new IOException("Incomplete primary measurement file");
        return records.next();
    }

    private static CSVRecord next(Iterator<CSVRecord> records, String type, int size) throws IOException {
        CSVRecord row = next(records);
        require(row, type, size);
        return row;
    }

    private static void require(CSVRecord row, String type, int size) throws IOException {
        if (!type.equals(row.get(0)) || size >= 0 && row.size() != size)
            throw new IOException("Expected " + type + " record at CSV record " + row.getRecordNumber());
    }
}
