package plugins.fmp.multitools.service;

import static org.junit.Assert.*;
import org.junit.Test;

public class ManualIntakeEditsTest {
    @Test public void measuresEveryBinAndSubtractsEvaporationOnlyOnce() {
        double[] raw = {10, 12, 15, 14, 14.2};
        double[] evaporation = {0, .2, .5, .7, 1};
        ManualIntakeEdits.BinMeasures measured = ManualIntakeEdits.measureBins(raw, evaporation, 1, 4);
        assertEquals(2, measured.amplitudes[1], 0);
        assertEquals(3, measured.amplitudes[2], 0);
        assertEquals(0, measured.amplitudes[3], 0);
        assertEquals(0, measured.amplitudes[4], 0);
        assertEquals(1.8, measured.amplitudes[1] - (evaporation[1] - evaporation[0]), 1e-9);
        assertEquals(2.7, measured.amplitudes[2] - (evaporation[2] - evaporation[1]), 1e-9);
        ManualIntakeEdits edits = new ManualIntakeEdits();
        for (int i = 1; i <= 4; i++) edits.replace(i, i, measured.amplitudes[i], measured.bottoms[i]);
        ManualIntakeEdits restored = ManualIntakeEdits.fromOptions(edits.options(), raw.length);
        double[] amounts = new double[raw.length], bottoms = new double[raw.length];
        restored.apply(amounts, bottoms);
        assertArrayEquals(new double[] {0, 2, 3, 0, 0}, amounts, 0);
        assertEquals(12, bottoms[1], 0); assertEquals(15, bottoms[2], 0);
    }

    @Test public void missingLevelsAreNotSilentlyInvented() {
        ManualIntakeEdits.BinMeasures measured = ManualIntakeEdits.measureBins(
                new double[]{10, Double.NaN, 14, 16}, new double[]{0, .1, .2, .3}, 1, 3);
        assertTrue(Double.isNaN(measured.amplitudes[1]));
        assertTrue(Double.isNaN(measured.amplitudes[2]));
        assertEquals(2, measured.amplitudes[3], 0);
    }
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

    @Test public void sharedOffsetSurvivesReloadWithoutChangingAmountsOrManualPositions() {
        ManualIntakeEdits edits = new ManualIntakeEdits();
        edits.replace(2, 2, 3, 24);
        edits.shiftPositions(1.5, false);
        edits = ManualIntakeEdits.fromOptions(edits.options(), 4);
        double[] amounts = {0, 2, 5, 0};
        double[] bottoms = {18, 20, 21, 22};
        edits.apply(amounts, bottoms);
        assertArrayEquals(new double[] {0, 2, 3, 0}, amounts, 0);
        assertArrayEquals(new double[] {18, 21.5, 24, 22}, bottoms, 0);
        assertFalse(edits.covers(1));

        edits.shiftPositions(-.5, true);
        edits = ManualIntakeEdits.fromOptions(edits.options(), 4);
        amounts = new double[] {0, 2, 5, 0};
        bottoms = new double[] {18, 20, 21, 22};
        edits.apply(amounts, bottoms);
        assertArrayEquals(new double[] {0, 2, 3, 0}, amounts, 0);
        assertArrayEquals(new double[] {18, 21, 23.5, 22}, bottoms, 0);
    }
}
