package plugins.fmp.multitools.experiment.inspection;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EventReviewCsvTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder(new java.io.File("target"));

    private EventReview review(String column, String comment) {
        String[] fields = new String[EventReview.HEADER.length];
        Arrays.fill(fields, "");
        fields[0] = "P06/experiment";
        fields[1] = "3";
        fields[2] = "line03L";
        fields[3] = "results/bin_60/line03L.tif";
        fields[4] = column;
        fields[11] = "Uncertain";
        fields[12] = comment;
        return new EventReview(fields);
    }

    @Test public void preservesCommentsAndUpsertsOnlySameSourcePoint() throws Exception {
        Path path = folder.getRoot().toPath().resolve("reviews.csv");
        String comment = "Ménisque, moved \"back\"\ncheck next image";
        EventReviewCsv.save(path, review("12", comment));
        EventReviewCsv.save(path, review("13", "another point"));
        List<EventReview> read = EventReviewCsv.read(path);
        assertEquals(2, read.size());
        assertEquals(comment, read.get(0).get(12));
        EventReviewCsv.save(path, review("12", "reclassified"));
        read = EventReviewCsv.read(path);
        assertEquals(2, read.size());
        assertEquals("reclassified", read.get(0).get(12));
        assertEquals("another point", read.get(1).get(12));
        String[] otherSource = review("12", "different source").fields();
        otherSource[3] = "results/bin_other/line03L.tif";
        EventReviewCsv.save(path, new EventReview(otherSource));
        assertEquals(3, EventReviewCsv.read(path).size());
    }

    @Test public void refusesToOverwriteUnrelatedCsv() throws Exception {
        Path path = folder.newFile("measurements.csv").toPath();
        byte[] original = "capillary,value\nline01,24\n".getBytes(StandardCharsets.UTF_8);
        Files.write(path, original);
        try {
            EventReviewCsv.save(path, review("12", "review"));
            fail("Should reject canonical measurements CSV");
        } catch (IOException expected) {
            assertArrayEquals(original, Files.readAllBytes(path));
        }
    }
}
