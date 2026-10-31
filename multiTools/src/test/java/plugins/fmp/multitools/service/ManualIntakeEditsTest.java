package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import org.junit.Test;

public class ManualIntakeEditsTest {
    @Test public void regionReplacesAutomaticBinsWithoutDoubleCounting() {
        ManualIntakeEdits edits = new ManualIntakeEdits();
        edits.replace(2, 4, 7, 30);
        double[] amounts = {0, 3, 2, 5, 1, 6}, bottoms = new double[6];
        edits.apply(amounts, bottoms);
        assertArrayEquals(new double[]{0, 3, 0, 0, 7, 6}, amounts, 0);
        assertEquals(30, bottoms[4], 0);
    }

    @Test public void metadataRestoresGeometryAndLaterRemoval() {
        ManualIntakeEdits edits = new ManualIntakeEdits();
        edits.replace(2, 4, 7, 30);
        edits.replace(4, 4, 0, 30);
        edits.replace(6, 6, 2.5, 32.5);
        ManualIntakeEdits restored = ManualIntakeEdits.fromOptions("v6,persistent=true" + edits.options() + ",scale=1", 8);
        double[] amounts = new double[8], bottoms = new double[8];
        restored.apply(amounts, bottoms);
        assertEquals(0, amounts[4], 0);
        assertEquals(2.5, amounts[6], 0);
        assertEquals(32.5, bottoms[6], 0);
        assertEquals(edits.options(), restored.options());
    }

    @Test public void invalidAndOutOfRangeMetadataIsIgnored() {
        ManualIntakeEdits edits = ManualIntakeEdits.fromOptions(",manual_intakes=0:2:4:20|2:99:4:20|2:3:NaN:20|bad", 8);
        assertTrue(edits.isEmpty());
    }
}
