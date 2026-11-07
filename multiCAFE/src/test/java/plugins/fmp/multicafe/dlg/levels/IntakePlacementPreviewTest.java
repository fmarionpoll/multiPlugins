package plugins.fmp.multicafe.dlg.levels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

import org.junit.Test;

public class IntakePlacementPreviewTest {
    private final double[] raw = { 20, 20, 20, 24, 24, 24 };

    @Test public void bothFlanksSupportTheSameTranslation() {
        assertEquals(2, IntakePlacementPreview.offset(step(2, 2), raw, 3, 20, 24), .25);
        assertEquals(-2, IntakePlacementPreview.offset(step(-2, -2), raw, 3, 20, 24), .25);
    }

    @Test public void insufficientOrConflictingEvidenceLeavesPlacementUndecided() {
        assertTrue(Double.isNaN(IntakePlacementPreview.offset(step(-2, 2), raw, 3, 20, 24)));
        assertTrue(Double.isNaN(IntakePlacementPreview.offset(step(2, 2), raw, 1, 20, 24)));
        BufferedImage image = step(2, 2);
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < image.getHeight(); y++)
                image.setRGB(x, y, 0xaaaaaa);
        assertTrue(Double.isNaN(IntakePlacementPreview.offset(image, raw, 3, 20, 24)));
    }

    private BufferedImage step(int before, int after) {
        BufferedImage image = new BufferedImage(raw.length, 45, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < raw.length; x++) {
            double edge = raw[x] + (x < 3 ? before : after);
            for (int y = 0; y < image.getHeight(); y++)
                image.setRGB(x, y, y < edge ? 0xaaaaaa : y == edge ? 0x90aaa5 : 0x76aaa0);
        }
        return image;
    }
}
