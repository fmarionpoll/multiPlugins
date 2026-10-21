package plugins.fmp.multitools.experiment.inspection;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/** Separate UTF-8 review file. Upsert preserves other reviews and replaces a reclassified point. */
public final class EventReviewCsv {
    private EventReviewCsv() { }

    public static List<EventReview> read(Path path) throws IOException {
        List<EventReview> reviews = new ArrayList<>();
        if (!Files.exists(path)) return reviews;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                CSVParser csv = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            if (!new ArrayList<>(csv.getHeaderMap().keySet()).equals(Arrays.asList(EventReview.HEADER)))
                throw new IOException("Unrecognized EventInspector review CSV header; file left unchanged.");
            for (CSVRecord row : csv) {
                if (row.size() != EventReview.HEADER.length) throw new IOException("Invalid review CSV row");
                String[] values = new String[EventReview.HEADER.length];
                for (int i = 0; i < values.length; i++) values[i] = row.get(i);
                reviews.add(new EventReview(values));
            }
        }
        return reviews;
    }

    public static synchronized void save(Path path, EventReview review) throws IOException {
        Map<List<String>, EventReview> rows = new LinkedHashMap<>();
        for (EventReview previous : read(path)) rows.put(previous.key(), previous);
        rows.put(review.key(), review);
        Path target = path.toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), "event-review-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
                    CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(EventReview.HEADER))) {
                for (EventReview row : rows.values()) csv.printRecord((Object[]) row.fields());
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
